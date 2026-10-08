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
from types import SimpleNamespace
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402
import pipeline  # noqa: E402
from build_prompt import build_prompt  # noqa: E402
from case_seed_sql import CaseSeedError, build_case_sql, resolve_sources  # noqa: E402
from common import load_json, write_json  # noqa: E402
from court_seed_sql import CourtSeedError, build_court_sql, check_court  # noqa: E402
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

    def test_build_case_sql_incident_date(self):
        case = listing_case()
        sources = resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}])
        self.assertIn("NULL, '2099-01-10'::date,", build_case_sql(case, report_for(case), sources, incident_date="2099-01-10"))
        self.assertIn("NULL, NULL,", build_case_sql(case, report_for(case), sources))
        with self.assertRaises(CaseSeedError):
            build_case_sql(case, report_for(case), sources, incident_date="2099/01/10")

    def test_build_case_sql_missing_values(self):
        case = listing_case()
        with self.assertRaises(CaseSeedError) as ctx:
            build_case_sql(case, report_for(case), resolve_sources(SOURCES))  # 항소심 사건번호 없음
        self.assertIn("sources[1].caseNumber", str(ctx.exception))
        no_listing = load_json(EXAMPLES / "case_input.json")
        with self.assertRaises(CaseSeedError) as ctx:
            build_case_sql(no_listing, report_for(no_listing), resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertIn("listing.shortIntro", str(ctx.exception))


class CourtSeedSqlTest(unittest.TestCase):
    def setUp(self):
        self.data = {"caseTitle": "사건",
                     "judgment": {"penaltyType": "PRISON", "reducedTo": None, "prisonMonths": 6, "fineAmount": None,
                                  "suspensionMonths": None, "extraDispositions": [], "summary": "요약 '인용'",
                                  "reasoning": "이유", "plainExplanation": "설명", "excerpt": "발췌"},
                     "judgmentFactors": [{"factorId": 1, "label": "요소", "direction": "UP", "evidence": "근거"}]}

    def test_sql_unpublished_and_idempotent(self):
        sql = build_court_sql(self.data, "사건")
        self.assertIn("false, now()", sql)
        self.assertNotIn("SET is_published", sql)
        self.assertIn("같은 내용의 재판부 판결이 이미 있습니다", sql)
        self.assertIn("요약 ''인용''", sql)
        self.assertIn("IS DISTINCT FROM v.label", sql)
        self.assertIn("reduced_to IS NOT DISTINCT FROM NULL", sql)  # 감경 여부까지 같아야 같은 판결로 본다
        reduced = dict(self.data, judgment=dict(self.data["judgment"], penaltyType="LIFE", reducedTo="PRISON",
                                                prisonMonths=180))
        self.assertIn("reduced_to IS NOT DISTINCT FROM 'PRISON'", build_court_sql(reduced, "사건"))

    def test_checks(self):
        j = self.data["judgment"]
        bad_cases = [
            (dict(j, prisonMonths=None), "prisonMonths가 필요"),
            (dict(j, penaltyType="LIFE", prisonMonths=None, suspensionMonths=12), "사형 · 무기"),
            (dict(j, summary="가" * 101), "100자"),
            (dict(j, excerpt=" "), "excerpt"),
            (dict(j, extraDispositions=[{"type": "FORFEIT", "value": "x"}]), "부가 처분"),
            (dict(j, reducedTo="LIFE"), "감경 조합"),
        ]
        for judgment, part in bad_cases:
            with self.assertRaises(CourtSeedError, msg=part) as ctx:
                check_court(dict(self.data, judgment=judgment))
            self.assertIn(part, str(ctx.exception))
        with self.assertRaises(CourtSeedError):
            check_court(dict(self.data, judgmentFactors=[]))
        # 오류 메시지에 입력 값(모델이 쓴 글 등)을 남기지 않는다
        leaky = dict(self.data, judgment=dict(j, penaltyType="홍길동", extraDispositions=[{"type": "X", "value": "김철수"}]),
                     judgmentFactors=[{"factorId": "이영희", "label": "l", "direction": "UP", "evidence": "e"}])
        with self.assertRaises(CourtSeedError) as ctx:
            check_court(leaky)
        for name in ("홍길동", "김철수", "이영희"):
            self.assertNotIn(name, str(ctx.exception))
        with self.assertRaises(CourtSeedError):
            build_court_sql(self.data, "다른 사건")  # caseTitle 불일치


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
        self.loads_before = set(pipeline.PRIVATE_SEED_DIR.joinpath("loads").glob("*")) if pipeline.PRIVATE_SEED_DIR.joinpath("loads").exists() else set()
        self.addCleanup(self.assert_no_real_loads)
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
            "stages": {"extract": {"enabled": False}, "court": {"enabled": False},
                       "generate": {"models": ["openai:a", "gemini:b"], "runs": 2, "maxRetries": 2},
                       "load": {"sqlDir": str(self.dir / "loads")}},
        }
        self.logs = []

    def tearDown(self):
        self.tmp.cleanup()

    def assert_no_real_loads(self):
        """테스트가 실제 비공개 저장소(private-seed/loads)에 SQL을 쓰지 않았는지 확인한다."""
        loads = pipeline.PRIVATE_SEED_DIR.joinpath("loads")
        after = set(loads.glob("*")) if loads.exists() else set()
        self.assertEqual(after - self.loads_before, set(), "테스트가 실제 private-seed/loads에 파일을 만들었다")

    def config(self, **stage_changes):
        raw = copy.deepcopy(self.raw_config)
        for stage, changes in stage_changes.items():
            raw["stages"].setdefault(stage, {}).update(changes)
        path = self.dir / "config.json"
        write_json(path, raw)
        return pipeline.load_config(path)

    def run_with(self, config, answers, applied=None, court_answers=None, caller=None, **kwargs):
        caller = caller or fake_caller(answers)
        functions = dict(pipeline.STAGE_FUNCTIONS)
        court_queue = list(court_answers or [])
        self.court_requests = getattr(self, "court_requests", [])

        def court_caller(spec, system, user, **kw):
            self.court_requests.append(user)
            item = court_queue.pop(0)
            return SimpleNamespace(text=json.dumps(item, ensure_ascii=False), served_model="court-served", stop_reason="stop")

        functions["court"] = lambda c, s, log: pipeline.stage_court(c, s, log, caller=court_caller)
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
        config = self.config(contamination={"enabled": True, "runs": 3, "criteria": {"minAnswered": 3}})
        clean = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None,
                 "suspensionMonths": None}
        state = self.run_with(config, {
            "contamination:openai:a": [clean] * 3,
            "contamination:gemini:b": [clean, "읽을 수 없음", clean],  # 읽을 수 없는 응답은 INVALID → SUSPECT → 기본은 진행
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
        # 생성 정보에는 실제 판결 · 예측 형량 값이 들어가지 않는다 (점검은 판정 · 근거 · 분류별 개수 · 기준만)
        report = json.loads(sql.split("'PENDING', NULL, NULL, '")[1].split("'::jsonb")[0].replace("''", "'"))
        self.assertEqual(report["contamination"]["verdict"], contamination[selection["modelSpec"]]["verdict"])
        self.assertNotIn("120", json.dumps(report["contamination"]))
        self.assertTrue(load["runKey"])

    def test_contamination_insufficient_and_criteria(self):
        clean = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None,
                 "suspensionMonths": None}
        # 응답이 최소 개수보다 적으면(호출 실패 · 한도) 판정을 확정하지 않고 기본은 멈춘다
        config = self.config(contamination={"enabled": True, "runs": 3, "models": ["openai:a"],
                                            "criteria": {"minAnswered": 3}},
                             generate={"models": ["openai:a"], "runs": 1})
        failing = [clean, llm.LLMError("API 오류 (429): quota"), llm.LLMError("API 오류 (429): quota")]
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"contamination:openai:a": failing})
        self.assertIn("INSUFFICIENT", str(ctx.exception))
        result = pipeline.load_state(config)["stages"]["contamination"]["outputs"]["results"]["openai:a"]
        self.assertEqual((result["answered"], result["counts"]), (1, {"FAR": 1}))
        # onInsufficient: continue면 진행한다
        config = self.config(contamination={"enabled": True, "runs": 3, "models": ["openai:a"], "onInsufficient": "continue",
                                            "criteria": {"minAnswered": 3}},
                             generate={"models": ["openai:a"], "runs": 1})
        state = self.run_with(config, {"contamination:openai:a": failing, "openai:a": [self.output]}, rerun=True)
        self.assertEqual(state["stages"]["generate"]["status"], "done")

    def test_contamination_all_calls_failed(self):
        clean = {"knowsCase": False, "penaltyType": "LIFE"}
        quota = [llm.LLMError("API 오류 (429): quota")] * 2
        # 호출이 모두 실패해도 INSUFFICIENT로 판정하고, 다른 모델까지 점검한 뒤 기본(stop)대로 멈춘다
        config = self.config(contamination={"enabled": True, "runs": 2, "criteria": {"minAnswered": 2}})
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"contamination:openai:a": quota, "contamination:gemini:b": [clean] * 2})
        self.assertIn("openai:a: INSUFFICIENT", str(ctx.exception))
        results = pipeline.load_state(config)["stages"]["contamination"]["outputs"]["results"]
        self.assertEqual((results["openai:a"]["verdict"], results["openai:a"]["answered"]), ("INSUFFICIENT", 0))
        self.assertIn("gemini:b", results)
        # onInsufficient: exclude면 그 모델만 빼고 진행한다
        config = self.config(contamination={"enabled": True, "runs": 2, "onInsufficient": "exclude",
                                            "criteria": {"minAnswered": 2}})
        state = self.run_with(config, {"contamination:openai:a": quota, "contamination:gemini:b": [clean] * 2,
                                       "gemini:b": [self.output, self.output]}, rerun=True)
        self.assertEqual(state["stages"]["contamination"]["outputs"]["excluded"], ["openai:a"])
        self.assertEqual(list(state["stages"]["generate"]["outputs"]["models"]), ["gemini:b"])

    def test_contamination_criteria_validation(self):
        for criteria, part in (({"minAnswered": 0}, "minAnswered"), ({"closeRatio": 1.5}, "closeRatio"),
                               ({"exactRatio": "0.5"}, "exactRatio"), ({"unknown": 1}, "알 수 없는"),
                               ({"closeMinMonths": 6, "closeMaxMonths": 3}, "closeMinMonths"),
                               ({"minAnswered": 11}, "runs보다")):
            bad = copy.deepcopy(self.raw_config)
            bad["stages"]["contamination"] = {"criteria": criteria}
            write_json(self.dir / "bad5.json", bad)
            with self.assertRaises(pipeline.PipelineError, msg=str(criteria)) as ctx:
                pipeline.load_config(self.dir / "bad5.json")
            self.assertIn(part, str(ctx.exception))
        bad = copy.deepcopy(self.raw_config)
        bad["stages"]["contamination"] = {"onInsufficient": "ignore"}
        write_json(self.dir / "bad5.json", bad)
        with self.assertRaises(pipeline.PipelineError):
            pipeline.load_config(self.dir / "bad5.json")

    def test_contamination_stop_and_exclude(self):
        known = {"knowsCase": True, "note": "기사로 봤다"}
        clean = {"knowsCase": False, "penaltyType": "LIFE"}
        config = self.config(contamination={"enabled": True, "runs": 1, "criteria": {"minAnswered": 1}})
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"contamination:openai:a": [known], "contamination:gemini:b": [clean]})
        self.assertIn("openai:a: CONTAMINATED", str(ctx.exception))
        state = pipeline.load_state(config)
        self.assertEqual(state["stages"]["contamination"]["status"], "failed")

        config = self.config(contamination={"enabled": True, "runs": 1, "onContaminated": "exclude",
                                            "criteria": {"minAnswered": 1}})
        state = self.run_with(config, {"contamination:openai:a": [known], "contamination:gemini:b": [clean],
                                       "gemini:b": [self.output, self.output]}, rerun=True)
        self.assertEqual(state["stages"]["contamination"]["outputs"]["excluded"], ["openai:a"])
        self.assertEqual(list(state["stages"]["generate"]["outputs"]["models"]), ["gemini:b"])
        self.assertEqual(state["stages"]["select"]["outputs"]["modelSpec"], "gemini:b")

    # ---- 재시도 설정 · 한도 소진 (BE-36)

    def test_retry_config(self):
        raw = copy.deepcopy(self.raw_config)
        raw["retry"] = {"maxAttempts": 8, "maxWait": 90}
        write_json(self.dir / "retry.json", raw)
        config = pipeline.load_config(self.dir / "retry.json")
        self.addCleanup(llm.reset_retry)
        self.run_with(config, {"openai:a": [self.output, self.output], "gemini:b": [self.output, self.output]})
        self.assertEqual(llm.configure_retry(), {"max_attempts": 8, "max_wait": 90.0})  # 실행할 때 적용된다
        default = self.config()
        self.assertEqual(default["retry"], {"maxAttempts": llm.DEFAULT_MAX_ATTEMPTS, "maxWait": llm.DEFAULT_MAX_WAIT})
        for bad in ({"maxAttempts": 0}, {"maxWait": -5}, {"maxAttempts": "3"}, {"unknown": 1}):
            raw["retry"] = bad
            write_json(self.dir / "bad-retry.json", raw)
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline.load_config(self.dir / "bad-retry.json")
            self.assertIn("retry", str(ctx.exception), msg=bad)

    def test_contamination_quota_exhausted_skips_remaining_runs(self):
        clean = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None,
                 "suspensionMonths": None}
        # 한도 소진으로 응답이 모자라면 INSUFFICIENT (BE-37) — 여기서는 회차를 건너뛰는지만 보려고 진행시킨다
        config = self.config(contamination={"enabled": True, "runs": 3, "onInsufficient": "continue",
                                            "criteria": {"minAnswered": 3}})
        quota = llm.LLMQuotaExhaustedError("일 한도")
        state = self.run_with(config, {
            "contamination:openai:a": [clean, quota],  # 3번째는 꺼내지 않는다 (꺼내면 IndexError)
            "contamination:gemini:b": [clean] * 3,
            "openai:a": [self.output, self.output],
            "gemini:b": [self.output, self.output],
        })
        result = state["stages"]["contamination"]["outputs"]["results"]["openai:a"]
        self.assertEqual((result["answered"], result["runs"], result["verdict"]), (1, 3, "INSUFFICIENT"))
        self.assertTrue(any("남은 1회" in line for line in self.logs))

    def test_generate_quota_exhausted_does_not_regenerate(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 3, "maxRetries": 2})
        with self.assertRaises(pipeline.PipelineError):
            self.run_with(config, {"openai:a": [llm.LLMQuotaExhaustedError("일 한도")]})  # 남은 회차 · 재생성 호출 없음
        summary = pipeline.load_state(config)["stages"]["generate"]["outputs"]["models"]["openai:a"]
        self.assertEqual((summary["runs"], summary["valid"], summary["retries"]), (1, 0, 0))

    # ---- 모델 교차 호출 (BE-44)

    def recording_caller(self, answers):
        """호출 순서를 기록하는 caller. (모델, 생성이면 True · 사전 학습 점검이면 False)"""
        inner, self.calls = fake_caller(answers), []

        def caller(spec, system, user, **kwargs):
            self.calls.append((spec, bool(system)))
            return inner(spec, system, user, **kwargs)

        return caller

    def test_contamination_and_generate_alternate_models(self):
        clean = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None,
                 "suspensionMonths": None}
        config = self.config(contamination={"enabled": True, "runs": 2, "criteria": {"minAnswered": 1}})
        answers = {"contamination:openai:a": [clean] * 2, "contamination:gemini:b": [clean] * 2,
                   "openai:a": [self.output] * 2, "gemini:b": [self.output] * 2}
        state = self.run_with(config, answers, caller=self.recording_caller(answers))
        self.assertEqual(self.calls, [("openai:a", False), ("gemini:b", False)] * 2
                         + [("openai:a", True), ("gemini:b", True)] * 2)
        # 모델별 기록 · 회차 번호는 모델마다 독립이다
        runs = state["stages"]["generate"]["outputs"]["models"]
        self.assertEqual([runs[m]["runs"] for m in ("openai:a", "gemini:b")], [2, 2])
        contamination = state["stages"]["contamination"]["outputs"]["results"]
        self.assertEqual([contamination[m]["answered"] for m in ("openai:a", "gemini:b")], [2, 2])

    def test_contamination_quota_exhausted_model_dropped_from_alternation(self):
        clean = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None,
                 "suspensionMonths": None}
        config = self.config(contamination={"enabled": True, "runs": 3, "criteria": {"minAnswered": 1}})
        answers = {"contamination:openai:a": [clean, llm.LLMQuotaExhaustedError("일 한도")],
                   "contamination:gemini:b": [clean] * 3,
                   "openai:a": [self.output] * 2, "gemini:b": [self.output] * 2}
        self.run_with(config, answers, caller=self.recording_caller(answers))
        points = [spec for spec, generating in self.calls if not generating]
        self.assertEqual(points, ["openai:a", "gemini:b", "openai:a", "gemini:b", "gemini:b"])

    def test_generate_regeneration_alternates_models(self):
        config = self.config(generate={"runs": 1, "maxRetries": 1})
        answers = {"openai:a": ["x", "x"], "gemini:b": ["x", "x"]}
        with self.assertRaises(pipeline.PipelineError):
            self.run_with(config, answers, caller=self.recording_caller(answers))
        self.assertEqual([spec for spec, _ in self.calls], ["openai:a", "gemini:b", "openai:a", "gemini:b"])
        summary = pipeline.load_state(config)["stages"]["generate"]["outputs"]["models"]
        self.assertEqual([(summary[m]["runs"], summary[m]["retries"]) for m in ("openai:a", "gemini:b")],
                         [(2, 1), (2, 1)])

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

    def test_load_incident_date_source_and_override(self):
        write_json(self.raw_config["inputs"]["source"], {"incidentDate": "2099-01-10", "sources": SOURCES})
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, load={"applyToDb": False})
        state = self.run_with(config, {"openai:a": [self.output]})
        self.assertIn("'2099-01-10'::date", Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8"))
        raw = copy.deepcopy(self.raw_config)
        raw["incidentDate"] = "2099-02-02"
        write_json(self.dir / "config.json", raw)
        config = pipeline.load_config(self.dir / "config.json")
        config["stages"]["generate"].update(models=["openai:a"], runs=1)
        config["stages"]["load"]["applyToDb"] = False
        state = self.run_with(config, {"openai:a": [self.output]}, start="load")
        self.assertIn("'2099-02-02'::date", Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8"))
        raw["incidentDate"] = "2099-2-2"
        write_json(self.dir / "config.json", raw)
        with self.assertRaises(pipeline.PipelineError):
            pipeline.load_config(self.dir / "config.json")

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

    def test_extract_model_spec_and_provider(self):
        self.assertEqual(pipeline.extract_provider(self.config()), "anthropic")  # 기본 Claude
        for spec, provider in (("gemini:gem-x", "gemini"), ("openai:gpt-x", "openai"), ("anthropic:claude-x", "anthropic")):
            self.assertEqual(pipeline.extract_provider(self.config(extract={"model": spec})), provider)
        bad = copy.deepcopy(self.raw_config)
        bad["stages"]["extract"] = {"model": "manual:x"}
        write_json(self.dir / "bad2.json", bad)
        with self.assertRaises(pipeline.PipelineError) as ctx:
            pipeline.load_config(self.dir / "bad2.json")
        self.assertIn("manual", str(ctx.exception))

    def test_extract_config_validation(self):
        for extract, part in (({"model": None}, "stages.extract.model"), ({"model": ""}, "stages.extract.model"),
                              ({"maxTokens": "32000"}, "maxTokens"), ({"maxTokens": 0}, "maxTokens"),
                              ({"maxTokens": -1}, "maxTokens"), ({"maxTokens": True}, "maxTokens")):
            bad = copy.deepcopy(self.raw_config)
            bad["stages"]["extract"] = extract
            write_json(self.dir / "bad3.json", bad)
            with self.assertRaises(pipeline.PipelineError, msg=str(extract)) as ctx:
                pipeline.load_config(self.dir / "bad3.json")
            self.assertIn(part, str(ctx.exception))
        self.config(extract={"maxTokens": 4000})  # 양의 정수는 통과

    def test_extract_passes_model_and_max_tokens(self):
        config = self.config(extract={"enabled": True, "model": "gemini:gem-x", "maxTokens": 4000})
        fake_module = mock.MagicMock()
        fake_module.run.return_value = (self.raw_config["inputs"]["case"], self.raw_config["inputs"]["court"],
                                        self.raw_config["inputs"]["report"], self.raw_config["inputs"]["source"])
        fake_module.ExtractError = RuntimeError
        with mock.patch.dict(sys.modules, {"extract_case": fake_module}):
            pipeline.stage_extract(config, {"stages": {}}, self.logs.append)
        kwargs = fake_module.run.call_args.kwargs
        self.assertEqual((kwargs["model"], kwargs["max_tokens"]), ("gemini:gem-x", 4000))
        self.assertTrue(any("gemini:gem-x" in line for line in self.logs))

    # ---- 재판부 판결 (BE-38)

    COURT_SOURCE = ("주 문\n피고인을 징역 10년에 처한다.\n양형의 이유\n피고인이 수사 초기부터 범행을 인정하고 반성하고 있는 점은 "
                    "유리한 정상이다. 그러나 다투던 중 집에 있던 흉기를 사용하여 피해자를 살해한 점은 불리한 정상이다.\n")
    COURT_DRAFT = {
        "summary": "흉기 사용을 무겁게 보되 자백과 반성을 참작한 판단",
        "reasoning": "재판부는 피고인이 범행을 인정하고 반성하는 점을 유리하게, 흉기를 사용한 점을 불리하게 보았다.",
        "plainExplanation": "잘못을 인정한 점은 고려했지만 흉기를 쓴 점을 무겁게 보았다.",
        "excerpt": "그러나 다투던 중 집에 있던 흉기를 사용하여 피해자를 살해한 점은 불리한 정상이다.",
        "extraDispositions": [],
        "factors": [
            {"factorId": 2, "direction": "UP", "evidence": "다투던 중 집에 있던 흉기를 사용하여 피해자를 살해한 점은 불리한 정상이다."},
            {"factorId": 7, "direction": "DOWN", "evidence": "피고인이 수사 초기부터 범행을 인정하고 반성하고 있는 점은 유리한 정상이다."},
        ],
        "notes": [],
    }

    def court_config(self, **changes):
        write_json(self.raw_config["inputs"]["source"],
                   {"sources": [dict(SOURCES[0], originalText=self.COURT_SOURCE), dict(SOURCES[1], caseNumber="2099노2")]})
        stages = {"court": {"enabled": True, "model": "openai:court-x"},
                  "generate": {"models": ["openai:a"], "runs": 1}, "load": {"applyToDb": False}}
        for stage, value in changes.items():
            stages.setdefault(stage, {}).update(value)
        return self.config(**stages)

    def test_court_stage_and_load_unpublished(self):
        config = self.court_config()
        state = self.run_with(config, {"openai:a": [self.output]}, court_answers=[self.COURT_DRAFT])
        court = state["stages"]["court"]["outputs"]
        self.assertEqual(court["model"], "court-served")
        draft = load_json(court["courtDraft"])
        self.assertEqual((draft["judgment"]["prisonMonths"], draft["judgment"]["isPublished"]), (120, False))
        self.assertEqual(draft["excludedFactors"]["factorIds"], [1, 3, 4, 5, 6, 8, 9, 10, 11])
        sql = Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8")
        self.assertTrue(state["stages"]["load"]["outputs"]["court"])
        self.assertIn("'COURT', 'FINAL'", sql)
        court_block = sql.split("-- 재판부 판결 적재")[1].split("-- AI 판결 적재")[0]
        self.assertIn("false, now()", court_block)  # 비공개
        self.assertNotIn("SET is_published", court_block)  # 기존 공개 재판부 판결을 건드리지 않는다
        self.assertTrue(any("재판부 판결 초안 단계" in line for line in self.logs))  # 외부 전송 안내

    def test_court_draft_isolated_from_ai_inputs(self):
        config = self.court_config(contamination={"enabled": True, "runs": 1, "criteria": {"minAnswered": 1}})
        clean = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None, "suspensionMonths": None}
        self.run_with(config, {"contamination:openai:a": [clean], "openai:a": [self.output]}, court_answers=[self.COURT_DRAFT])
        batch = pipeline.load_state(config)["stages"]["generate"]["outputs"]["batchDir"]
        prompt = (Path(batch) / "prompt.md").read_text(encoding="utf-8")
        # 예시 사건 프롬프트에는 선고 가능 범위로 "징역 10년" 같은 문구가 원래 있으므로 재판부 판결 고유 문구로 확인한다
        for text in (self.COURT_DRAFT["summary"], self.COURT_DRAFT["excerpt"], self.COURT_DRAFT["reasoning"]):
            self.assertNotIn(text, prompt)  # 재판부 판결은 AI 판결 프롬프트에 들어가지 않는다
        self.assertNotIn("judgment", load_json(self.raw_config["inputs"]["case"]))

    def test_court_human_written_input(self):
        human = {"caseTitle": self.case["title"],
                 "judgment": {"subjectType": "COURT", "timing": "FINAL", "penaltyType": "PRISON", "reducedTo": None,
                              "prisonMonths": 120, "fineAmount": None, "suspensionMonths": None, "extraDispositions": [],
                              "summary": "요약", "reasoning": "이유", "plainExplanation": "설명", "excerpt": "발췌"},
                 "judgmentFactors": [{"factorId": 2, "label": self.case["factors"][1]["label"], "direction": "UP",
                                      "evidence": "근거"}]}
        write_json(self.dir / "human.json", human)
        raw = copy.deepcopy(self.raw_config)
        raw["inputs"]["courtDraft"] = str(self.dir / "human.json")
        raw["stages"]["generate"] = {"models": ["openai:a"], "runs": 1}
        raw["stages"]["load"] = dict(raw["stages"]["load"], applyToDb=False)  # sqlDir(임시 폴더)는 유지한다
        write_json(self.dir / "config.json", raw)
        state = self.run_with(pipeline.load_config(self.dir / "config.json"), {"openai:a": [self.output]})
        self.assertIn("'COURT', 'FINAL'", Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8"))

    def test_court_missing_draft_loads_without_court(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, load={"applyToDb": False})
        state = self.run_with(config, {"openai:a": [self.output]})
        self.assertFalse(state["stages"]["load"]["outputs"]["court"])
        self.assertTrue(any("재판부 판결 초안이 없어" in line for line in self.logs))

    def test_court_config_validation(self):
        for court, part in (({"model": ""}, "stages.court.model"), ({"maxTokens": 0}, "stages.court.maxTokens")):
            bad = copy.deepcopy(self.raw_config)
            bad["stages"]["court"] = court
            write_json(self.dir / "bad4.json", bad)
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline.load_config(self.dir / "bad4.json")
            self.assertIn(part, str(ctx.exception))
        config = self.config(extract={"model": "gemini:g"})
        self.assertEqual(pipeline.court_model(config), "gemini:g")  # 지정 안 하면 extract 모델

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
