"""AI 판결 파이프라인 · 사건 적재 SQL · 비공개(PENDING) 적재 테스트 (BE-31). 네트워크 · DB를 쓰지 않는다.

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import copy
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402
import pipeline  # noqa: E402
from build_prompt import build_prompt  # noqa: E402
from case_seed_sql import CaseSeedError, build_case_sql, resolve_sources  # noqa: E402
from common import load_json, write_json  # noqa: E402
from to_seed_sql import build_sql  # noqa: E402

EXAMPLES = TOOL_DIR / "examples"
KEYS = {"OPENAI_API_KEY": "k1", "GEMINI_API_KEY": "k2"}


def listing_case():
    case = load_json(EXAMPLES / "case_input.json")
    case["listing"] = {"shortIntro": "빌린 돈 문제로 다투던 지인을 살해한 사건이다.", "keywords": ["금전 갈등", "자백"],
                       "difficulty": "MID", "estimatedMinutes": 10}
    return case


def report_for(case):
    return {"factorExtras": [{"factorId": f["factorId"], "preLabel": None, "summaryTag": "분류"} for f in case["factors"]],
            "penaltyRuleBasis": {r["penaltyType"]: "근거" for r in case["penaltyRules"]},
            "deidentifiedItems": ["인명"], "eligibility": {"eligible": True, "reasons": []}, "warnings": []}


SOURCES = [
    {"file": "first.txt", "caseNumber": "2099고합1", "courtName": "가상지방법원", "decidedAt": "2099-05-01",
     "courtLevel": "FIRST", "originalText": "원문 '따옴표'"},
    {"file": "appeal.txt", "caseNumber": None, "courtName": None, "decidedAt": None, "courtLevel": "APPEAL",
     "originalText": "원문 2"},
]


class CaseSeedSqlTest(unittest.TestCase):
    def test_resolve_sources_overrides_and_final(self):
        sources = resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}])
        self.assertEqual(sources[1]["caseNumber"], "2099노2")
        self.assertEqual([s["isFinal"] for s in sources], [False, True])  # 심급이 가장 높은 항소심
        self.assertEqual([s["isFinal"] for s in resolve_sources(SOURCES, final_index=0)], [True, False])
        with self.assertRaises(CaseSeedError):
            resolve_sources(SOURCES, final_index=5)

    def test_build_case_sql_draft_and_guard(self):
        case = listing_case()
        sql = build_case_sql(case, report_for(case), resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertIn("'DRAFT', NULL", sql)
        self.assertNotIn("'PUBLISHED'", sql)
        self.assertIn("같은 제목의 DRAFT 사건이 이미 있습니다", sql)
        # 같은 제목의 공개 · 검토 중 사건이면 다른 사건일 수 있어 멈춘다
        self.assertIn("status <> 'DRAFT'", sql)
        self.assertIn("RAISE EXCEPTION '같은 제목의 공개 · 검토 중 사건", sql)
        self.assertIn("'원문 ''따옴표'''", sql)  # 작은따옴표 이스케이프
        self.assertEqual(sql.count("INSERT INTO factor"), len(case["factors"]))
        self.assertEqual(sql.count("INSERT INTO case_source"), 2)
        self.assertIn("'2099-05-01'::date", sql)
        self.assertIn("'법원 공개 판결문'", sql)

    def test_build_case_sql_missing_values(self):
        case = listing_case()
        with self.assertRaises(CaseSeedError) as ctx:
            build_case_sql(case, report_for(case), resolve_sources(SOURCES))  # 항소심 사건번호 없음
        self.assertIn("sources[1].caseNumber", str(ctx.exception))
        no_listing = load_json(EXAMPLES / "case_input.json")
        with self.assertRaises(CaseSeedError) as ctx:
            build_case_sql(no_listing, report_for(no_listing), resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertIn("listing.shortIntro", str(ctx.exception))


class PendingSqlTest(unittest.TestCase):
    def setUp(self):
        self.case = load_json(EXAMPLES / "case_input.json")
        self.output = load_json(EXAMPLES / "ai_output_sample.json")
        self.prompt = build_prompt(self.case)

    def test_pending_is_unpublished_and_idempotent(self):
        sql = build_sql(self.case, self.output, self.prompt, "model-x", None, flyway=True, pending=True,
                        generation_report={"runKey": "abc", "validationWarnings": []})
        self.assertNotIn("SET is_published = false", sql)  # 기존 공개 AI 판결을 건드리지 않는다
        self.assertIn("false, now()", sql)
        self.assertIn("'PENDING', NULL, NULL", sql)
        self.assertIn("generation_report ->> 'runKey' = 'abc'", sql)
        self.assertNotIn("BEGIN;", sql)

    def test_pending_requires_run_key(self):
        with self.assertRaises(ValueError):
            build_sql(self.case, self.output, self.prompt, "m", None, pending=True, generation_report={})

    def test_default_mode_unchanged(self):
        sql = build_sql(self.case, self.output, self.prompt, "m", "검수자")
        self.assertIn("SET is_published = false", sql)
        self.assertIn("'APPROVED', '검수자'", sql)
        self.assertNotIn("generation_report", sql)


def fake_caller(answers):
    """모델별 응답 목록. 사전 학습 점검(system 없음)은 contamination 목록에서 꺼낸다."""
    queues = {key: list(value) for key, value in answers.items()}

    def caller(spec, system, user, **kwargs):
        key = spec if system else f"contamination:{spec}"
        item = queues[key].pop(0)
        if isinstance(item, Exception):
            raise item
        provider, model = llm.parse_model_spec(spec)
        text = item if isinstance(item, str) else json.dumps(item, ensure_ascii=False)
        return llm.LLMResult(text, provider, model, usage={"totalTokens": 10}, latency_seconds=0.1)

    return caller


@mock.patch.dict(os.environ, KEYS)
class PipelineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        (self.dir / ".git").write_text("gitdir: x\n", encoding="utf-8")  # SQL 보관 폴더(loads)의 상위가 git 체크아웃인 것처럼
        patches = [mock.patch.object(pipeline, "PIPELINE_DIR", self.dir / "pipeline"),
                   mock.patch.object(pipeline, "RUNS_DIR", self.dir / "runs")]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)
        self.case = listing_case()
        self.output = load_json(EXAMPLES / "ai_output_sample.json")
        self.court = {**load_json(EXAMPLES / "court_judgment_internal.json"), "caseTitle": self.case["title"]}
        files = {}
        for key, value in (("case", self.case), ("report", report_for(self.case)),
                           ("source", {"sources": SOURCES}), ("court", self.court)):
            files[key] = str(self.dir / f"{key}.json")
            write_json(files[key], value)
        self.raw_config = {
            "name": "test-case",
            "sources": [{"path": "a.txt"}, {"path": "b.txt", "caseNumber": "2099노2"}],
            "inputs": files,
            "stages": {"extract": {"enabled": False},
                       "generate": {"models": ["openai:a", "gemini:b"], "runs": 2, "maxRetries": 2},
                       "load": {"sqlDir": str(self.dir / "loads")}},
        }
        self.logs = []

    def tearDown(self):
        self.tmp.cleanup()

    def config(self, **stage_changes):
        raw = copy.deepcopy(self.raw_config)
        for stage, changes in stage_changes.items():
            raw["stages"].setdefault(stage, {}).update(changes)
        path = self.dir / "config.json"
        write_json(path, raw)
        return pipeline.load_config(path)

    def run_with(self, config, answers, applied=None, **kwargs):
        caller = fake_caller(answers)
        functions = dict(pipeline.STAGE_FUNCTIONS)
        functions["contamination"] = lambda c, s, log: pipeline.stage_contamination(c, s, log, caller=caller)
        functions["generate"] = lambda c, s, log: pipeline.stage_generate(c, s, log, caller=caller)
        functions["load"] = lambda c, s, log: pipeline.stage_load(
            c, s, log, apply=lambda path, db: (applied.append((path, db)) if applied is not None else None) or [])
        return pipeline.run_pipeline(config, functions=functions, log=self.logs.append, **kwargs)

    def variant(self, **changes):
        return {**self.output, **changes}

    # ---- 설정 · 계획

    def test_config_validation(self):
        bad = copy.deepcopy(self.raw_config)
        bad["name"] = "사건"
        bad["stages"]["generate"]["models"] = ["manual:x"]
        bad["stages"]["select"] = {"strategy": "manual"}
        write_json(self.dir / "bad.json", bad)
        with self.assertRaises(pipeline.PipelineError) as ctx:
            pipeline.load_config(self.dir / "bad.json")
        message = str(ctx.exception)
        for part in ("name", "manual", "stages.select.run"):
            self.assertIn(part, message)
        config = self.config()
        self.assertEqual(config["stages"]["contamination"]["runs"], 10)  # 기본값과 합쳐진다
        self.assertFalse(config["stages"]["contamination"]["enabled"])

    def test_plan_resume_from_until_skip(self):
        config = self.config()
        state = {"stages": {"generate": {"status": "done"}}}
        self.assertEqual(pipeline.plan(config, state), ["select", "load"])  # extract · contamination 꺼짐, generate 끝남
        self.assertEqual(pipeline.plan(config, state, start="generate"), ["generate", "select", "load"])
        self.assertEqual(pipeline.plan(config, state, rerun=True, until="select"), ["generate", "select"])
        self.assertEqual(pipeline.plan(config, state, skip=["load"]), ["select"])

    def test_plan_resume_after_failure_runs_later_stages(self):
        # 중간 단계가 실패했으면 이어서 돌릴 때 뒤 단계도 다시 돈다 (낡은 결과로 끝내지 않게)
        config = self.config(extract={"enabled": True})
        state = {"stages": {"extract": {"status": "done"}, "generate": {"status": "failed"},
                            "select": {"status": "done"}, "load": {"status": "done"}}}
        self.assertEqual(pipeline.plan(config, state), ["generate", "select", "load"])

    def test_resume_after_generate_failure_loads(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1, "maxRetries": 0})
        self.run_with(config, {"openai:a": [self.output]})
        with self.assertRaises(pipeline.PipelineError):
            self.run_with(config, {"openai:a": ["x"]}, start="generate")
        state = self.run_with(config, {"openai:a": [self.output]})  # 옵션 없이 이어서
        self.assertEqual([state["stages"][s]["status"] for s in ("generate", "select", "load")], ["done"] * 3)

    # ---- 전체 흐름

    def test_full_run_selects_and_loads(self):
        applied = []
        config = self.config(contamination={"enabled": True, "runs": 3})
        clean = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None,
                 "suspensionMonths": None}
        state = self.run_with(config, {
            "contamination:openai:a": [clean] * 3,
            "contamination:gemini:b": [clean, "읽을 수 없음", clean],  # 읽을 수 없는 응답은 SUSPECT → 기본은 진행
            "openai:a": [self.output, self.variant(prisonMonths=132)],
            "gemini:b": [self.variant(prisonMonths=150), "[1]"],
        }, applied)
        contamination = state["stages"]["contamination"]["outputs"]["results"]
        self.assertEqual((contamination["openai:a"]["verdict"], contamination["gemini:b"]["verdict"]),
                         ("CLEAN", "SUSPECT"))
        selection = state["stages"]["select"]["outputs"]
        self.assertEqual(selection["strategy"], "consensus")
        self.assertIn(selection["modelSpec"], ("openai:a", "gemini:b"))
        load = state["stages"]["load"]["outputs"]
        sql = Path(load["sqlFile"]).read_text(encoding="utf-8")
        self.assertTrue(sql.startswith("-- AI 판결 파이프라인 적재"))
        self.assertEqual((sql.count("BEGIN;"), sql.count("COMMIT;")), (1, 1))
        self.assertIn("'DRAFT', NULL", sql)
        self.assertIn("'PENDING', NULL, NULL", sql)
        self.assertEqual(len(applied), 1)
        self.assertEqual(applied[0][1]["mode"], "docker")
        # 생성 정보에는 실제 판결 값이 들어가지 않는다 (점검은 판정 · 횟수만)
        report = json.loads(sql.split("'PENDING', NULL, NULL, '")[1].split("'::jsonb")[0].replace("''", "'"))
        self.assertEqual(report["contamination"]["verdict"], contamination[selection["modelSpec"]]["verdict"])
        self.assertNotIn("120", json.dumps(report["contamination"]))
        self.assertTrue(load["runKey"])

    def test_contamination_stop_and_exclude(self):
        known = {"knowsCase": True, "note": "기사로 봤다"}
        clean = {"knowsCase": False, "penaltyType": "LIFE"}
        config = self.config(contamination={"enabled": True, "runs": 1})
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"contamination:openai:a": [known], "contamination:gemini:b": [clean]})
        self.assertIn("openai:a: CONTAMINATED", str(ctx.exception))
        state = pipeline.load_state(config)
        self.assertEqual(state["stages"]["contamination"]["status"], "failed")

        config = self.config(contamination={"enabled": True, "runs": 1, "onContaminated": "exclude"})
        state = self.run_with(config, {"contamination:openai:a": [known], "contamination:gemini:b": [clean],
                                       "gemini:b": [self.output, self.output]}, rerun=True)
        self.assertEqual(state["stages"]["contamination"]["outputs"]["excluded"], ["openai:a"])
        self.assertEqual(list(state["stages"]["generate"]["outputs"]["models"]), ["gemini:b"])
        self.assertEqual(state["stages"]["select"]["outputs"]["modelSpec"], "gemini:b")

    def test_generate_retries_until_valid(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 2, "maxRetries": 2})
        state = self.run_with(config, {"openai:a": ["x", "y", "z", self.output]}, until="generate")
        summary = state["stages"]["generate"]["outputs"]["models"]["openai:a"]
        self.assertEqual(summary, {"runs": 4, "valid": 1, "retries": 2})

        config = self.config(generate={"models": ["openai:a"], "runs": 1, "maxRetries": 1})
        with self.assertRaises(pipeline.PipelineError):
            self.run_with(config, {"openai:a": ["x", "y"]}, start="generate", until="generate")

    def test_select_strategies(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 3})
        many_warnings = self.variant(prisonMonths=200)  # 권고 범위 밖 → 경고
        self.run_with(config, {"openai:a": [many_warnings, self.output, self.variant(factors=self.output["factors"][:2])]},
                      until="generate")
        state = pipeline.load_state(config)
        _, groups = pipeline.load_runs(state["stages"]["generate"]["outputs"]["batchDir"])
        case = self.case
        first, _, _ = pipeline.select_run(case, groups, ["openai:a"], "first-valid")
        self.assertEqual(first["runIndex"], 1)
        fewest, _, _ = pipeline.select_run(case, groups, ["openai:a"], "fewest-warnings")
        self.assertEqual(fewest["runIndex"], 2)
        consensus, reason, score = pipeline.select_run(case, groups, ["openai:a"], "consensus")
        self.assertIn(consensus["runIndex"], (1, 2))  # 요소가 같은 두 회차 중 경고 적은 쪽
        self.assertEqual(consensus["runIndex"], 2)
        manual, reason, _ = pipeline.select_run(case, groups, ["openai:a"], "manual", "openai__a/run-003")
        self.assertEqual((manual["runIndex"], reason), (3, "설정에서 직접 지정"))
        with self.assertRaises(pipeline.PipelineError):
            pipeline.select_run(case, groups, ["openai:a"], "manual", "openai__a/run-009")
        with self.assertRaises(pipeline.PipelineError):
            pipeline.select_run(case, groups, ["gemini:b"], "manual", "openai__a/run-001")
        self.assertEqual(state["stages"]["generate"]["status"], "done")

    def test_changed_case_uses_new_batch(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1})
        state = self.run_with(config, {"openai:a": [self.output]}, until="generate")
        first = state["stages"]["generate"]["outputs"]["batchDir"]
        self.assertTrue(Path(first).name.startswith("test-case-"))
        # 사건 내용이 바뀌면(다시 가공) 이전 묶음 때문에 막히지 않고 새 묶음에 생성한다
        changed = dict(self.case, overview=self.case["overview"] + " 추가 사실.")
        write_json(self.raw_config["inputs"]["case"], changed)
        state = self.run_with(config, {"openai:a": [self.output]}, start="generate", until="select")
        second = state["stages"]["generate"]["outputs"]["batchDir"]
        self.assertNotEqual(first, second)
        self.assertEqual(state["stages"]["select"]["outputs"]["runIndex"], 1)
        self.assertTrue(state["stages"]["select"]["outputs"]["runFile"].startswith(second))

    def test_rerun_earlier_stage_clears_later(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1})
        self.run_with(config, {"openai:a": [self.output]})
        self.assertEqual(pipeline.load_state(config)["stages"]["load"]["status"], "done")
        self.run_with(config, {"openai:a": [self.output]}, start="generate", until="generate")
        stages = pipeline.load_state(config)["stages"]
        self.assertNotIn("select", stages)
        self.assertNotIn("load", stages)

    def test_load_without_db_and_case(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, load={"applyToDb": False, "case": False})
        state = self.run_with(config, {"openai:a": [self.output]})
        load = state["stages"]["load"]["outputs"]
        self.assertFalse(load["applied"])
        sql = Path(load["sqlFile"]).read_text(encoding="utf-8")
        self.assertNotIn("INSERT INTO legal_case", sql)

    def test_load_sql_dir_parent_missing(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1},
                             load={"sqlDir": str(self.dir / "no" / "such" / "loads")})
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"openai:a": [self.output]})
        self.assertIn("private-seed", str(ctx.exception))

    def test_sql_dir_must_be_private_checkout(self):
        empty = self.dir / "empty-submodule"
        empty.mkdir()
        with self.assertRaises(pipeline.PipelineError) as ctx:  # 빈 서브모듈 폴더 (.git 없음)
            pipeline.check_sql_dir(empty / "loads")
        self.assertIn("git 저장소 체크아웃이 아닙니다", str(ctx.exception))
        with self.assertRaises(pipeline.PipelineError) as ctx:  # 공개 저장소 안 (private-seed 밖)
            pipeline.check_sql_dir(pipeline.PUBLIC_ROOT / "loads")
        self.assertIn("공개 저장소 안", str(ctx.exception))
        pipeline.check_sql_dir(self.dir / "loads")  # 별도 git 체크아웃은 허용

    def test_apply_sql_rejects_remote_db(self):
        with self.assertRaises(pipeline.PipelineError) as ctx:
            pipeline.apply_sql(self.dir / "x.sql", {"mode": "psql", "url": "jdbc:postgresql://prod.example.com:5432/db"})
        self.assertIn("로컬 DB에만", str(ctx.exception))
        # libpq가 URL 호스트보다 우선하는 쿼리 host · hostaddr로 우회하지 못한다
        for url in ("postgresql://localhost:5432/db?host=prod.example.com",
                    "postgresql://localhost/db?hostaddr=10.0.0.5",
                    "postgresql://localhost/db?host=localhost,prod.example.com"):
            with self.assertRaises(pipeline.PipelineError, msg=url):
                pipeline.apply_sql(self.dir / "x.sql", {"mode": "psql", "url": url})

    def test_apply_sql_unix_socket_and_pghost(self):
        socket_url = {"mode": "psql", "url": "postgresql:///lawnambul"}
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("shutil.which", return_value=None):
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline.apply_sql(self.dir / "x.sql", socket_url)
        self.assertIn("psql 명령이 없습니다", str(ctx.exception))  # 호스트 확인은 통과 (유닉스 소켓 = 로컬)
        with mock.patch.dict(os.environ, {"PGHOST": "prod.example.com"}, clear=True):
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline.apply_sql(self.dir / "x.sql", socket_url)
        self.assertIn("로컬 DB에만", str(ctx.exception))

    def test_docker_mode_rejects_remote_endpoint(self):
        with mock.patch.dict(os.environ, {"DOCKER_HOST": "tcp://prod.example.com:2376"}):
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline._local_docker_endpoint()
        self.assertIn("원격 docker", str(ctx.exception))
        for local in ("unix:///var/run/docker.sock", "tcp://127.0.0.1:2375"):
            with mock.patch.dict(os.environ, {"DOCKER_HOST": local}):
                self.assertEqual(pipeline._local_docker_endpoint(), local)
        context = mock.Mock(stdout=b"ssh://deploy@prod.example.com\n")
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("subprocess.run", return_value=context):
            with self.assertRaises(pipeline.PipelineError):
                pipeline._local_docker_endpoint()

    def test_main_completion_message_by_load(self):
        config_path = self.dir / "config.json"
        write_json(config_path, self.raw_config)
        for state, expected in (({"stages": {}}, "돌지 않았습니다"),
                                ({"stages": {"load": {"status": "done", "outputs": {"applied": False, "sqlFile": "a.sql"}}}},
                                 "SQL만 만들었습니다"),
                                ({"stages": {"load": {"status": "done", "outputs": {"applied": True, "sqlFile": "a.sql"}}}},
                                 "로컬 DB에 적재한")):
            out = io.StringIO()
            with mock.patch.object(sys, "argv", ["pipeline.py", "run", str(config_path), "--until", "select"]), \
                    mock.patch.object(pipeline, "run_pipeline", return_value=state), mock.patch("sys.stdout", out):
                self.assertEqual(pipeline.main(), 0)
            self.assertIn(expected, out.getvalue())

    def test_extract_ineligible_stops(self):
        config = self.config(extract={"enabled": True})
        report = report_for(self.case)
        report["eligibility"] = {"eligible": False, "reasons": ["경합범이다"]}
        write_json(self.dir / "r.json", report)
        fake_module = mock.MagicMock()
        fake_module.run.return_value = (self.raw_config["inputs"]["case"], self.raw_config["inputs"]["court"],
                                        str(self.dir / "r.json"), self.raw_config["inputs"]["source"])
        fake_module.ExtractError = RuntimeError
        with mock.patch.dict(sys.modules, {"extract_case": fake_module}):
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline.stage_extract(config, {"stages": {}}, self.logs.append)
            self.assertIn("경합범이다", str(ctx.exception))
            config["stages"]["extract"]["requireEligible"] = False
            outputs = pipeline.stage_extract(config, {"stages": {}}, self.logs.append)
        self.assertFalse(outputs["eligible"])
        sources = fake_module.run.call_args[0][0]
        self.assertEqual([p.name for p in sources], ["a.txt", "b.txt"])


if __name__ == "__main__":
    unittest.main()
