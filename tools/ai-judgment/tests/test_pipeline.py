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
from rag_store import case_number_hash  # noqa: E402
from to_seed_sql import build_sql  # noqa: E402

EXAMPLES = TOOL_DIR / "examples"
KEYS = {"OPENAI_API_KEY": "k1", "GEMINI_API_KEY": "k2"}


def listing_case():
    case = load_json(EXAMPLES / "case_input.json")
    case["listing"] = {"shortIntro": "빌린 돈 문제로 다투던 지인을 살해한 사건이다.", "keywords": ["금전 갈등", "자백"],
                       "difficulty": "MID", "estimatedMinutes": 10}
    return case


def report_for(case):
    return {"factorExtras": [{"factorId": f["factorId"], "preLabel": None, "summaryTag": "분류",
                              "valueAxis": "FAULT_STANDARD" if f["factorId"] == 1 else None} for f in case["factors"]],
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
        self.assertEqual(sql.count("summary_tag, value_axis, value_axis_votes,"), len(case["factors"]))
        self.assertNotIn("::jsonb, 1);", sql)  # 투표 기록이 없는 보고서는 value_axis_votes가 NULL
        self.assertNotIn("summary_tag, value_axis, value_axis_votes, value_axis_status", sql)  # INSERT는 후검수 상태를 DB 기본값 AUTO로 둔다
        inserted = sql.split("RETURNING id INTO v_case_id")[1]  # 새 사건 INSERT 부분 (앞은 이미 적재된 사건의 축 갱신)
        self.assertEqual(inserted.count("'FAULT_STANDARD'"), 1)  # 첫 요소만 축이 있고 나머지는 NULL (어느 축에도 맞지 않는 요소)
        self.assertEqual(sql.count("INSERT INTO case_source"), 2)
        self.assertIn("'2099-05-01'::date", sql)
        self.assertIn("'법원 공개 판결문'", sql)

    def test_build_case_sql_invalidValueAxis_isError(self):
        case = listing_case()
        report = report_for(case)
        report["factorExtras"][0]["valueAxis"] = "EMBEDDING"
        with self.assertRaises(CaseSeedError) as ctx:
            build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertIn("valueAxis", str(ctx.exception))

    def test_build_case_sql_valueAxisVotes_insertsJsonb(self):
        # 축 분류 투표 기록(BE-49)은 value_axis_votes에 jsonb로 넣는다 (BE-48)
        case = listing_case()
        report = report_for(case)
        report["factorExtras"][0]["valueAxisVotes"] = {
            "runs": 5, "counts": {"FAULT_STANDARD": 3, "NONE": 2}, "needsReview": False}
        report["factorExtras"][1]["valueAxisVotes"] = {
            "runs": 4, "counts": {"NONE": 2, "ORDER_OPPORTUNITY": 2}, "needsReview": True}  # 동률, 축은 NULL을 고름
        sql = build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertIn("""'FAULT_STANDARD', '{"runs": 5, "counts": {"FAULT_STANDARD": 3, "NONE": 2}, "needsReview": false}'::jsonb, NULL, 1);""", sql)
        self.assertIn(""", NULL, '{"runs": 4, "counts": {"NONE": 2, "ORDER_OPPORTUNITY": 2}, "needsReview": true}'::jsonb, NULL, 2);""", sql)

    def test_build_case_sql_summaryTagCandidates_insertsJsonb(self):
        # 요약어 후보(BE-53)는 summary_tag_candidates에 jsonb로 넣는다 (BE-80). 후보가 없는 요소는 NULL
        case = listing_case()
        report = report_for(case)
        report["factorExtras"][0]["summaryTagCandidates"] = {
            "candidates": [{"tag": "흉기 사용", "score": 0.9312}, {"tag": "범행 동기", "score": 0.8123}],
            "inVocabulary": False, "ambiguous": False, "suggested": "흉기 사용"}
        sql = build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertEqual(sql.count("summary_tag_candidates, display_order)"), len(case["factors"]))
        self.assertIn("""'{"candidates": [{"tag": "흉기 사용", "score": 0.9312}, {"tag": "범행 동기", "score": 0.8123}], """
                      """"inVocabulary": false, "ambiguous": false, "suggested": "흉기 사용"}'::jsonb, 1);""", sql)
        self.assertIn("NULL, NULL, NULL, 2);", sql)  # 두 번째 요소: 축 · 투표 기록 · 후보 모두 없음
        existing = sql.split("RETURNING id INTO v_case_id")[0]
        self.assertNotIn("summary_tag_candidates", existing)  # 이미 적재된 사건은 후보를 바꾸지 않는다(축만 맞춤)

    def test_build_case_sql_invalidSummaryTagCandidates_isError(self):
        ok = {"tag": "흉기 사용", "score": 0.93}
        cases = [
            ("객체", "candidates"),
            ("배열", {"candidates": "흉기 사용", "inVocabulary": False, "ambiguous": False, "suggested": None}),
            ("tag는", {"candidates": [{"tag": "", "score": 0.9}], "inVocabulary": False, "ambiguous": False, "suggested": None}),
            ("tag는", {"candidates": [{"tag": "가" * 21, "score": 0.9}], "inVocabulary": False, "ambiguous": False, "suggested": None}),
            ("score는", {"candidates": [{"tag": "흉기 사용", "score": 1.5}], "inVocabulary": False, "ambiguous": False, "suggested": None}),
            ("score는", {"candidates": [{"tag": "흉기 사용", "score": True}], "inVocabulary": False, "ambiguous": False, "suggested": None}),
            ("inVocabulary", {"candidates": [ok], "inVocabulary": "no", "ambiguous": False, "suggested": None}),
            ("ambiguous", {"candidates": [ok], "inVocabulary": False, "suggested": None}),
            ("tag 중 하나", {"candidates": [ok], "inVocabulary": False, "ambiguous": False, "suggested": "반성"}),
            ("false일 때만", {"candidates": [ok], "inVocabulary": False, "ambiguous": True, "suggested": "흉기 사용"}),
            ("모르는 키", {"candidates": [ok], "inVocabulary": False, "ambiguous": False, "suggested": None, "futureKey": 1}),
            ("모르는 키", {"candidates": [{**ok, "rank": 1}], "inVocabulary": False, "ambiguous": False, "suggested": None}),
        ]
        for message, candidates in cases:
            with self.subTest(message):
                case = listing_case()
                report = report_for(case)
                report["factorExtras"][0]["summaryTagCandidates"] = candidates
                with self.assertRaises(CaseSeedError) as ctx:
                    build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
                self.assertIn(message, str(ctx.exception))

    def test_build_case_sql_emptySummaryTagCandidates_passes(self):
        # 요약어가 비어 후보 계산에서 빠진 요소(summary_tags._empty_result)도 형식은 맞다
        case = listing_case()
        report = report_for(case)
        report["factorExtras"][0]["summaryTagCandidates"] = {
            "candidates": [], "inVocabulary": False, "ambiguous": False, "suggested": None}
        self.assertIn("""'{"candidates": [], "inVocabulary": false, "ambiguous": false, "suggested": null}'::jsonb""",
                      build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}])))

    def test_build_case_sql_existingDraft_updatesAutoAxesOnly(self):
        # 이미 적재된 DRAFT 사건이면 사건은 건너뛰고, 확정 전(AUTO) 요소의 축 · 투표 기록만 맞춘다 (리뷰 반영)
        case = listing_case()
        report = report_for(case)
        report["factorExtras"][0]["valueAxisVotes"] = {
            "runs": 5, "counts": {"FAULT_STANDARD": 3, "NONE": 2}, "needsReview": False}
        sql = build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        skip = sql.split("사건 적재 건너뜀")[1].split("RETURN;")[0]
        self.assertIn("UPDATE factor f", skip)
        self.assertIn("f.value_axis_status = 'AUTO'", skip)
        self.assertIn("v.value_axis_votes IS NOT NULL", skip)  # 투표 없이 정한 값으로 투표 결과를 덮지 않는다 (셀프 리뷰 반영)
        self.assertIn("AND f.label = v.label", skip)  # 다시 추출해 요소가 달라졌으면 바꾸지 않는다
        first = case["factors"][0]["label"].replace("'", "''")
        self.assertIn(f"""(1, '{first}', 'FAULT_STANDARD'::varchar, '{{"runs": 5, "counts": {{"FAULT_STANDARD": 3, "NONE": 2}}, "needsReview": false}}'::jsonb)""", skip)
        self.assertIn("(2, ", skip)
        self.assertIn("NULL::varchar, NULL::jsonb)", skip)  # 축 없음 · 투표 기록 없음
        self.assertIn("RAISE WARNING '가치관 축: 번호 · 라벨이 DB와 다른 요소", skip)
        self.assertEqual(skip.count(f"(1, '{first}'"), 1)  # 요소 목록은 CTE 한 곳에만 둔다 (리뷰 반영)
        unmatched = skip.split("SELECT (SELECT count(*) FROM updated)")[1]
        self.assertIn("v.value_axis_votes IS NOT NULL", unmatched)  # 불일치 경고는 갱신 대상(투표 기록 있음)만 센다

    def test_build_case_sql_valueAxisVotes_needsReviewConsistent_passes(self):
        # requestedRuns가 있으면 그 기준으로, 없으면 runs 기준으로 needsReview를 확인한다
        for votes in ({"runs": 2, "counts": {"FAULT_STANDARD": 2}, "needsReview": True, "requestedRuns": 5},  # 2개만 유효
                      {"runs": 3, "counts": {"FAULT_STANDARD": 3}, "needsReview": False, "requestedRuns": 5},
                      {"runs": 3, "counts": {"FAULT_STANDARD": 3}, "needsReview": True}):  # 예전 기록: true는 막지 않음
            with self.subTest(votes):
                case = listing_case()
                report = report_for(case)
                report["factorExtras"][0]["valueAxisVotes"] = votes
                build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))

    def test_build_case_sql_invalidValueAxisVotes_isError(self):
        cases = [
            ("객체", "votes"),
            ("runs", {"runs": 0, "counts": {"FAULT_STANDARD": 1}, "needsReview": False}),
            ("needsReview", {"runs": 1, "counts": {"FAULT_STANDARD": 1}, "needsReview": "no"}),
            ("counts는 비어", {"runs": 1, "counts": {}, "needsReview": False}),
            ("키는", {"runs": 1, "counts": {"EMBEDDING": 1}, "needsReview": False}),
            ("합계", {"runs": 5, "counts": {"FAULT_STANDARD": 3}, "needsReview": False}),
            ("최다표", {"runs": 5, "counts": {"FAULT_STANDARD": 2, "NONE": 3}, "needsReview": False}),
            # needsReview가 표 수와 맞지 않음 (리뷰 반영): 동률인데 false / 요청 횟수 기준 과반이 아닌데 false / 과반인데 true
            ("과반이 아닙니다", {"runs": 4, "counts": {"FAULT_STANDARD": 2, "NONE": 2}, "needsReview": False}),
            ("맞지 않습니다", {"runs": 2, "counts": {"FAULT_STANDARD": 2}, "needsReview": False, "requestedRuns": 5}),
            ("맞지 않습니다", {"runs": 5, "counts": {"FAULT_STANDARD": 4, "NONE": 1}, "needsReview": True, "requestedRuns": 5}),
            ("requestedRuns", {"runs": 5, "counts": {"FAULT_STANDARD": 5}, "needsReview": False, "requestedRuns": 3}),
        ]
        for message, votes in cases:
            with self.subTest(message):
                case = listing_case()
                report = report_for(case)
                report["factorExtras"][0]["valueAxisVotes"] = votes  # 첫 요소의 축은 FAULT_STANDARD
                with self.assertRaises(CaseSeedError) as ctx:
                    build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
                self.assertIn(message, str(ctx.exception))

    def test_build_case_sql_malformedValueAxisWithVotes_isCaseSeedError(self):
        # 축 값 형식이 틀리면 투표 기록 검사로 넘어가지 않고 CaseSeedError로 알린다 (TypeError 아님, CodeRabbit 리뷰 반영)
        for bad in ([], ["FAULT_STANDARD"], {"x": 1}):  # 비어 있지 않은 목록 · 객체는 예전에 TypeError였다
            with self.subTest(bad):
                case = listing_case()
                report = report_for(case)
                report["factorExtras"][0]["valueAxis"] = bad
                report["factorExtras"][0]["valueAxisVotes"] = {"runs": 1, "counts": {"FAULT_STANDARD": 1},
                                                              "needsReview": False}
                with self.assertRaises(CaseSeedError) as ctx:
                    build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
                self.assertIn("valueAxis", str(ctx.exception))

    def test_build_case_sql_noFactors_isError(self):
        # 판단 요소가 없으면 적재 SQL을 만들지 않는다 (이미 적재된 사건의 축 갱신 SQL이 빈 VALUES가 되지 않게)
        case = listing_case()
        case["factors"] = []
        with self.assertRaises(CaseSeedError) as ctx:
            build_case_sql(case, report_for(case), resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertIn("판단 요소(factors)가 없습니다", str(ctx.exception))

    def test_build_case_sql_reportWithoutValueAxis_insertsNull(self):
        # extract-v5 이전 보고서(valueAxis 키 없음)도 적재된다
        case = listing_case()
        report = report_for(case)
        for extra in report["factorExtras"]:
            del extra["valueAxis"]
        sql = build_case_sql(case, report, resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}]))
        self.assertNotIn("FAULT_STANDARD", sql)

    def test_build_case_sql_incident_date(self):
        case = listing_case()
        sources = resolve_sources(SOURCES, [{}, {"caseNumber": "2099노2"}])
        # 양형기준 버전은 기준일(공소제기일 → 없으면 발생일)로 DB 함수가 고른다 (BE-54)
        self.assertIn("sentencing_guideline_at('살인범죄', '2099-01-10'::date), '2099-01-10'::date,",
                      build_case_sql(case, report_for(case), sources, incident_date="2099-01-10"))
        self.assertIn("sentencing_guideline_at('살인범죄', '2099-02-01'::date), '2099-01-10'::date,",
                      build_case_sql(case, report_for(case), sources, incident_date="2099-01-10",
                                     indictment_date="2099-02-01"))
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

    def config(self, _top=None, **stage_changes):
        raw = copy.deepcopy(self.raw_config)
        raw.update(_top or {})
        for stage, changes in stage_changes.items():
            raw["stages"].setdefault(stage, {}).update(changes)
        path = self.dir / "config.json"
        write_json(path, raw)
        return pipeline.load_config(path)

    def run_with(self, config, answers, applied=None, court_answers=None, caller=None, axis_answers=None,
                script_answers=None, classify_answers=None, **kwargs):
        caller = caller or fake_caller(answers)
        functions = dict(pipeline.STAGE_FUNCTIONS)
        court_queue = list(court_answers or [])
        self.court_requests = getattr(self, "court_requests", [])
        axis_queue = None if axis_answers is None else list(axis_answers)
        self.axis_requests = getattr(self, "axis_requests", [])
        script_queue = None if script_answers is None else list(script_answers)
        self.script_requests = getattr(self, "script_requests", [])
        classify_queue = list(classify_answers or [])
        self.classify_requests = getattr(self, "classify_requests", [])

        def classify_caller(spec, system, user, **kw):
            # 사건 분류 (BE-123). 응답을 순서대로 낸다(dict는 JSON으로)
            self.classify_requests.append((spec, user))
            item = classify_queue.pop(0)
            if isinstance(item, Exception):
                raise item
            text = item if isinstance(item, str) else json.dumps(item, ensure_ascii=False)
            return llm.LLMResult(text, *llm.parse_model_spec(spec), usage={"inputTokens": 100, "outputTokens": 20,
                                                                            "totalTokens": 120})

        def script_caller(spec, system, user, **kw):
            self.script_requests.append((spec, system, user))
            if script_queue is None:
                return llm.LLMResult("기본 증거 대사입니다.", *llm.parse_model_spec(spec))
            item = script_queue.pop(0)
            if isinstance(item, Exception):
                raise item
            return llm.LLMResult(item, *llm.parse_model_spec(spec))

        def axis_caller_for(c, s):
            def axis_caller(spec, system, user, **kw):
                # 가치관 축 분류 (BE-49). 응답을 주지 않으면 보고서(추출기)의 축을 그대로 답한다
                self.axis_requests.append((spec, user))
                if axis_queue is None:
                    extras = load_json(pipeline.input_file(c, s, "report"))["factorExtras"]
                    item = {"factors": [{"factorId": e["factorId"], "valueAxis": e.get("valueAxis")} for e in extras]}
                else:
                    item = axis_queue.pop(0)
                if isinstance(item, Exception):
                    raise item
                text = item if isinstance(item, str) else json.dumps(item, ensure_ascii=False)
                return llm.LLMResult(text, *llm.parse_model_spec(spec), usage={"totalTokens": 5}, latency_seconds=0.1)
            return axis_caller

        def court_caller(spec, system, user, **kw):
            self.court_requests.append(user)
            item = court_queue.pop(0)
            return SimpleNamespace(text=json.dumps(item, ensure_ascii=False), served_model="court-served", stop_reason="stop")

        functions["court"] = lambda c, s, log: pipeline.stage_court(c, s, log, caller=court_caller)
        functions["contamination"] = lambda c, s, log: pipeline.stage_contamination(c, s, log, caller=caller)
        # 참고 자료 RAG (BE-54): 실제 임베딩 · DB 대신 가짜. rag_db를 주지 않으면 DB 접속 실패로 대체 동작
        functions["rag"] = lambda c, s, log: pipeline.stage_rag(
            c, s, log, embedder=lambda texts, task_type: [[0.001] * 3072 for _ in texts],
            runner=getattr(self, "rag_db", None) or self.rag_unavailable)
        functions["generate"] = lambda c, s, log: pipeline.stage_generate(c, s, log, caller=caller)
        functions["axis"] = lambda c, s, log: pipeline.stage_axis(c, s, log, caller=axis_caller_for(c, s))
        functions["classify"] = lambda c, s, log: pipeline.stage_classify(c, s, log, caller=classify_caller)
        functions["script"] = lambda c, s, log: pipeline.stage_script(c, s, log, caller=script_caller)
        functions["load"] = lambda c, s, log: pipeline.stage_load(
            c, s, log, apply=lambda path, db: (applied.append((path, db)) if applied is not None else None) or [])
        return pipeline.run_pipeline(config, functions=functions, log=self.logs.append, **kwargs)

    @staticmethod
    def rag_unavailable(sql):
        raise pipeline.RagUnavailable("connection refused")

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
        self.assertEqual(pipeline.plan(config, state), ["select", "axis", "load"])  # extract · contamination 꺼짐, generate 끝남
        self.assertEqual(pipeline.plan(config, state, start="generate"), ["generate", "select", "axis", "load"])
        self.assertEqual(pipeline.plan(config, state, rerun=True, until="select"), ["generate", "select"])
        self.assertEqual(pipeline.plan(config, state, skip=["load"]), ["select", "axis"])
        # 축만 다시 투표: 생성(비싼 단계)은 다시 돌지 않는다 (BE-49)
        self.assertEqual(pipeline.plan(config, state, start="axis"), ["axis", "load"])

    def test_plan_resume_after_failure_runs_later_stages(self):
        # 중간 단계가 실패했으면 이어서 돌릴 때 뒤 단계도 다시 돈다 (낡은 결과로 끝내지 않게)
        config = self.config(extract={"enabled": True})
        state = {"stages": {"extract": {"status": "done"}, "generate": {"status": "failed"},
                            "select": {"status": "done"}, "load": {"status": "done"}}}
        self.assertEqual(pipeline.plan(config, state), ["generate", "select", "axis", "load"])

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

    def test_rag_references_go_into_prompt_and_report(self):
        # BE-54: rag 단계가 찾은 참고 자료로 생성 프롬프트를 만들고, 생성 정보에는 id · 점수 · 버전 이름만 남긴다
        applied, queries = [], []
        result = {"guideline": {"id": 7, "versionName": "2차 수정 2013", "effectiveDate": "2013-05-15"},
                  "ranges": [{"id": 1, "subject": "제2유형", "typeNo": 2, "content": "기본 10 ~ 16년"}],
                  "factors": [],
                  "precedents": [{"id": 31, "caseGroup": "g31", "score": 0.81, "guidelineId": 7, "chargeName": "살인",
                                  "courtLevel": "FIRST", "penaltyType": "PRISON", "prisonMonths": 144,
                                  "suspensionMonths": None, "fineAmount": None, "sentencingFactors": None,
                                  "factsSummary": "검색된 판례 사실관계", "sentencingReason": "검색된 판례 양형 이유"}]}
        self.rag_db = lambda sql: queries.append(sql) or json.dumps(result, ensure_ascii=False)
        config = self.config({"incidentDate": "2014-03-01"}, rag={"enabled": True},
                             generate={"models": ["openai:a"], "runs": 1, "maxRetries": 0})
        state = self.run_with(config, {"openai:a": [self.output]}, applied)
        rag = state["stages"]["rag"]["outputs"]
        self.assertEqual(rag["report"]["status"], "OK")
        self.assertIn(case_number_hash("2099노2"), queries[0])  # 설정 sources의 사건번호도 자기 사건 제외에 쓴다
        self.assertNotIn("2099노2", queries[0])
        prompt = (Path(state["stages"]["generate"]["outputs"]["batchDir"]) / "prompt.md").read_text(encoding="utf-8")
        self.assertIn("검색된 판례 사실관계", prompt)
        self.assertIn("2차 수정 2013", prompt)
        self.assertNotIn("유사 판례 A (예시)", prompt)
        self.assertEqual(load_json(pipeline.input_file(config, state, "case"))["references"], self.case["references"])
        sql = Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8")
        self.assertIn("검색된 판례 사실관계", sql)  # ai_generation 입력 프롬프트도 실제로 보낸 것
        report = json.loads(sql.split("'PENDING', NULL, NULL, '")[1].split("'::jsonb")[0].replace("''", "'"))
        self.assertEqual(report["rag"]["precedents"], [{"id": 31, "score": 0.81, "sameGuideline": True}])
        self.assertEqual(report["rag"]["guideline"], "2차 수정 2013")
        self.assertNotIn("검색된 판례", json.dumps(report, ensure_ascii=False))

        # 생성 뒤에 참고 자료가 바뀌면 실제로 보낸 것과 다른 프롬프트가 기록되므로 적재를 멈춘다 (#143 리뷰)
        references_file = Path(state["stages"]["rag"]["outputs"]["referencesFile"])
        saved = load_json(references_file)
        saved["references"]["similarCases"][0]["summary"] = "생성 뒤에 바뀐 사실관계"
        references_file.write_text(json.dumps(saved, ensure_ascii=False), encoding="utf-8")
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"openai:a": [self.output]}, applied, start="load")
        self.assertIn("--from generate", str(ctx.exception))

        # 고른 회차의 묶음 정보(batch.json)가 없으면 확인할 수 없으므로 멈춘다 (#143 리뷰)
        saved["references"]["similarCases"][0]["summary"] = "검색된 판례 사실관계"
        references_file.write_text(json.dumps(saved, ensure_ascii=False), encoding="utf-8")
        batch_file = Path(state["stages"]["generate"]["outputs"]["batchDir"]) / "batch.json"
        batch_file.rename(batch_file.with_name("batch.moved.json"))
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"openai:a": [self.output]}, applied, start="load")
        self.assertIn("묶음 정보가 없어", str(ctx.exception))
        batch_file.with_name("batch.moved.json").rename(batch_file)

        # 참고 자료를 만든 뒤 기준일 · 사건 입력이 바뀌면 --from generate만으로는 예전 참고 자료를 쓰므로 멈춘다 (#143 리뷰)
        changed = {**config, "indictmentDate": "2014-04-01"}
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(changed, {"openai:a": [self.output]}, applied, start="generate")
        self.assertIn("--from rag", str(ctx.exception))
        state = self.run_with(changed, {"openai:a": [self.output]}, applied, start="rag")  # rag부터 다시 돌리면 통과
        self.assertEqual(state["stages"]["rag"]["outputs"]["report"]["guidelineBasis"], "INDICTMENT_DATE")
        self.assertNotIn("2014-04-01", json.dumps(state["stages"]["rag"]["outputs"], ensure_ascii=False))  # 지문에 날짜를 그대로 남기지 않는다

    def test_rag_fallback_and_disabled(self):
        config = self.config(rag={"enabled": True}, generate={"models": ["openai:a"], "runs": 1, "maxRetries": 0})
        state = self.run_with(config, {"openai:a": [self.output]})  # DB 접속 실패 → 사건 입력의 참고 자료로 진행
        self.assertEqual(state["stages"]["rag"]["outputs"]["report"]["status"], "FALLBACK")
        prompt = (Path(state["stages"]["generate"]["outputs"]["batchDir"]) / "prompt.md").read_text(encoding="utf-8")
        self.assertIn("유사 판례 A (예시)", prompt)
        self.assertTrue(any("RAG 대체" in line for line in self.logs))

        required = self.config(rag={"enabled": True, "required": True},
                               generate={"models": ["openai:a"], "runs": 1, "maxRetries": 0})
        with self.assertRaises(pipeline.PipelineError):
            self.run_with(required, {"openai:a": [self.output]}, start="rag")

        off = self.config(generate={"models": ["openai:a"], "runs": 1, "maxRetries": 0})  # 기본은 꺼짐
        self.assertNotIn("rag", pipeline.plan(off, {"stages": {}}))
        state = self.run_with(off, {"openai:a": [self.output]}, start="generate")
        sql = Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8")
        report = json.loads(sql.split("'PENDING', NULL, NULL, '")[1].split("'::jsonb")[0].replace("''", "'"))
        self.assertEqual(report["rag"], {"status": "SKIPPED"})

    def test_rag_config_validation(self):
        bad = copy.deepcopy(self.raw_config)
        bad["indictmentDate"] = "2014/03/01"
        bad["stages"]["rag"] = {"precedent": {"topK": 0}}
        write_json(self.dir / "bad.json", bad)
        with self.assertRaises(pipeline.PipelineError) as ctx:
            pipeline.load_config(self.dir / "bad.json")
        self.assertIn("indictmentDate", str(ctx.exception))
        self.assertIn("stages.rag.precedent.topK", str(ctx.exception))

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

    def _tied_candidate(self, run_index, reasoning):
        """요소 방향은 self.output과 똑같아(구조 점수 동점) reasoning만 다른 후보 (BE-55 테스트용)."""
        return {"modelSpec": "openai:a", "runIndex": run_index,
                "output": {**self.output, "reasoning": reasoning},
                "validation": {"ok": True, "warnings": []}}

    def test_select_consensus_semantic_weight_breaks_tie(self):
        """구조(요소 방향) 점수가 완전히 동점일 때, semanticWeight > 0이면 reasoning이 서로 더 비슷한
        후보 쪽으로 선택이 바뀐다(동점이면 항상 1번 회차를 고르던 것과 달라진다)."""
        groups = {"openai:a": [self._tied_candidate(1, "이유A"), self._tied_candidate(2, "이유B"),
                                self._tied_candidate(3, "이유C")]}
        vectors = {"이유A": [1.0, 0.0], "이유B": [0.0, 1.0], "이유C": [0.0, 0.9]}
        embed_fn = lambda texts: [vectors[t] for t in texts]  # noqa: E731

        without, reason_without, _ = pipeline.select_run(self.case, groups, ["openai:a"], "consensus")
        self.assertEqual(without["runIndex"], 1)  # 동점이면 앞선 회차(semanticWeight 기본 0, 기존 동작)
        self.assertNotIn("유사도", reason_without)

        with_weight, reason_with, score = pipeline.select_run(
            self.case, groups, ["openai:a"], "consensus", semantic_weight=0.3, embed_fn=embed_fn)
        self.assertEqual(with_weight["runIndex"], 2)  # B · C가 서로 비슷해 A 대신 선택됨(동률이면 앞선 B)
        self.assertIn("유사도", reason_with)
        self.assertIsInstance(score, float)

    def test_select_consensus_semantic_weight_falls_back_on_embed_failure(self):
        """임베딩 호출이 실패하면(한도 초과 등) 가중치 없이 기존 방식(구조 점수)으로 조용히 선택한다."""
        groups = {"openai:a": [self._tied_candidate(1, "이유A"), self._tied_candidate(2, "이유B")]}

        def failing_embed(texts):
            raise llm.LLMQuotaExhaustedError("일 한도 초과")

        logs = []
        record, reason, score = pipeline.select_run(
            self.case, groups, ["openai:a"], "consensus", semantic_weight=0.3,
            embed_fn=failing_embed, log=logs.append)
        self.assertEqual(record["runIndex"], 1)  # 구조 점수 동점 → 앞선 회차로 fallback
        self.assertNotIn("유사도", reason)
        self.assertTrue(any("임베딩 실패" in line for line in logs))

    def test_select_consensus_semantic_weight_logs_when_not_comparable(self):
        """후보가 1개뿐이면 reasoning_similarity_scores가 None을 돌려주는데(예외가 아니다), 이때도
        가중치가 적용되지 않았음을 로그로 남긴다."""
        groups = {"openai:a": [self._tied_candidate(1, "이유A")]}
        logs = []
        record, reason, score = pipeline.select_run(
            self.case, groups, ["openai:a"], "consensus", semantic_weight=0.3, log=logs.append)
        self.assertEqual(record["runIndex"], 1)
        self.assertNotIn("유사도", reason)
        self.assertTrue(any("유사도 비교 불가" in line for line in logs))

    def test_validate_config_rejects_semantic_weight_on_non_consensus_strategy(self):
        with self.assertRaises(pipeline.PipelineError):
            self.config(select={"strategy": "first-valid", "semanticWeight": 0.3})

    def test_validate_config_invalid_semantic_weight_type_does_not_crash(self):
        """semanticWeight가 숫자가 아니면(타입 오류) strategy 비교에서 TypeError가 나지 않고
        option_errors의 타입 오류만 보고한다."""
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.config(select={"strategy": "first-valid", "semanticWeight": "0.3"})
        self.assertIn("semanticWeight", str(ctx.exception))

    def test_select_semantic_weight_external_send_notice(self):
        """semanticWeight > 0이면 판결 이유가 gemini(임베딩)로 전송된다는 안내가 나온다 (0이면 안 나온다)."""
        def stop(*args, **kwargs):
            raise pipeline.PipelineError("테스트 중단")

        functions = {stage: stop for stage in pipeline.STAGES}

        config = self.config(select={"semanticWeight": 0.3})
        with self.assertRaises(pipeline.PipelineError):
            pipeline.run_pipeline(config, functions=functions, until="select", log=self.logs.append)
        self.assertTrue(any(line.startswith("외부 전송") and "판결 이유" in line for line in self.logs))

        self.logs.clear()
        config = self.config(select={"semanticWeight": 0})
        with self.assertRaises(pipeline.PipelineError):
            pipeline.run_pipeline(config, functions=functions, until="select", log=self.logs.append)
        self.assertFalse(any("판결 이유" in line for line in self.logs if line.startswith("외부 전송")))

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

    # ---- 가치관 축 분류 투표 (BE-49)

    def axis_answer(self, overrides=None):
        """요소 11개 응답. 기본은 보고서(1번만 FAULT_STANDARD, 나머지 null)와 같고 overrides로 일부 요소를 바꾼다."""
        axes = {f["factorId"]: ("FAULT_STANDARD" if f["factorId"] == 1 else None) for f in self.case["factors"]}
        axes.update(overrides or {})
        return {"factors": [{"factorId": i, "valueAxis": a} for i, a in axes.items()]}

    def test_axis_votes_flagSplitFactorsAndLoad(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 5})
        split = {2: "APOLOGY_SINCERITY"}
        answers = [self.axis_answer({**split, 3: "ORDER_OPPORTUNITY"}), self.axis_answer({**split, 3: "ORDER_OPPORTUNITY"}),
                   self.axis_answer({**split, 3: "ORDER_OPPORTUNITY"}), self.axis_answer({3: "PRINCIPLE_RELATION"}),
                   self.axis_answer({3: "PRINCIPLE_RELATION"})]
        state = self.run_with(config, {"openai:a": [self.output]}, axis_answers=answers)
        axis = state["stages"]["axis"]["outputs"]
        self.assertEqual((axis["model"], axis["runs"], axis["requestedRuns"]), ("openai:a", 5, 5))  # 생성 첫 모델
        self.assertEqual(axis["needsReview"], [])  # 2번 3 : 2, 3번 3 : 2 → 모두 과반
        self.assertEqual(axis["changedFromExtract"], [2, 3])
        self.assertEqual({spec for spec, _ in self.axis_requests}, {"openai:a"})  # 같은 모델로만 묻는다
        votes = load_json(axis["votesFile"])
        self.assertEqual(votes["promptVersion"], "axis-v1")
        self.assertEqual(votes["factors"][1]["valueAxisVotes"],
                         {"runs": 5, "counts": {"APOLOGY_SINCERITY": 3, "NONE": 2}, "needsReview": False, "requestedRuns": 5})
        # 적재 SQL은 투표 결과 축과 투표 기록(value_axis_votes, BE-48)을 쓴다 (추출기 값 위에 덮어씀). 추출기 보고서 파일은 바뀌지 않는다
        sql = Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8")
        self.assertIn("""'분류', 'APOLOGY_SINCERITY', '{"runs": 5, "counts": {"APOLOGY_SINCERITY": 3, "NONE": 2}, "needsReview": false, "requestedRuns": 5}'::jsonb, NULL, 2);""", sql)
        self.assertIn("""'분류', 'ORDER_OPPORTUNITY', '{"runs": 5, "counts": {"PRINCIPLE_RELATION": 2, "ORDER_OPPORTUNITY": 3}, "needsReview": false, "requestedRuns": 5}'::jsonb, NULL, 3);""", sql)
        self.assertIsNone(load_json(self.raw_config["inputs"]["report"])["factorExtras"][1]["valueAxis"])
        self.assertTrue(any("가치관 축 분류 단계" in line and "openai" in line for line in self.logs))  # 외부 전송 안내

    def test_axis_votes_tie_needsReview(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 4, "model": "gemini:b"})
        answers = [self.axis_answer({4: "ORDER_OPPORTUNITY"}), self.axis_answer({4: "ORDER_OPPORTUNITY"}),
                   self.axis_answer({4: "APOLOGY_SINCERITY"}), self.axis_answer()]
        state = self.run_with(config, {"openai:a": [self.output]}, axis_answers=answers)
        axis = state["stages"]["axis"]["outputs"]
        self.assertEqual(axis["model"], "gemini:b")  # 설정한 모델
        self.assertEqual(axis["needsReview"], [4])  # 2 : 1 : 1 → 과반 아님
        self.assertTrue(any("요소 4" in line and "관리자 확인 필요" in line for line in self.logs))
        pipeline.print_status(config, log=self.logs.append)
        self.assertIn("가치관 축: gemini:b 4/4회 투표 · 관리자 확인 필요 요소 4", self.logs)

    def test_axis_invalid_answer_retriedThenExcluded(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 3, "maxRetries": 1})
        bad = {"factors": [{"factorId": 1, "valueAxis": "EMBEDDING"}]}
        answers = ["읽을 수 없음", self.axis_answer(),          # 1회차: 형식 오류 → 다시 물어 통과
                   bad, llm.LLMError("서버 오류"),               # 2회차: 두 번 다 실패 → 집계에서 뺌
                   self.axis_answer({5: "APOLOGY_SINCERITY"})]  # 3회차
        state = self.run_with(config, {"openai:a": [self.output]}, axis_answers=answers)
        axis = state["stages"]["axis"]["outputs"]
        self.assertEqual((axis["runs"], axis["attempts"], axis["failures"]), (2, 5, 3))
        self.assertEqual(axis["needsReview"], [5])  # 유효 2개 중 1 : 1
        self.assertTrue(any("유효 응답 2/3개" in line for line in self.logs))

    def test_axis_quota_exhausted_stopsRemainingRuns(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 5})
        answers = [self.axis_answer(), self.axis_answer(), llm.LLMQuotaExhaustedError("일 한도")]
        state = self.run_with(config, {"openai:a": [self.output]}, axis_answers=answers)
        axis = state["stages"]["axis"]["outputs"]
        self.assertEqual((axis["runs"], axis["attempts"]), (2, 3))  # 한도 소진 뒤에는 호출하지 않는다
        # 5회 중 2개만 유효 → 표가 모여도 요청 횟수의 과반이 아니라 모두 확인 필요 (리뷰 반영)
        self.assertEqual(axis["needsReview"], [f["factorId"] for f in self.case["factors"]])

    def test_axis_no_valid_answer_stops_and_skip_usesExtractor(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 2, "maxRetries": 0})
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"openai:a": [self.output]}, axis_answers=["x", "y"])
        self.assertIn("--skip axis", str(ctx.exception))
        # 축 단계를 건너뛰면 추출기 값 그대로 적재한다
        state = self.run_with(config, {}, skip=["axis"])
        sql = Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8")
        self.assertIn("'분류', 'FAULT_STANDARD', NULL, NULL, 1);", sql)  # 투표 기록 · 요약어 후보 없음 (NULL)
        self.assertIn("'분류', NULL, NULL, NULL, 2);", sql)
        self.assertEqual(state["stages"]["axis"]["status"], "failed")  # 실패한 투표 결과는 쓰지 않는다

    def test_axis_disabled_ignoresPreviousVotes_skipKeepsThem(self):
        # 투표를 마친 뒤: --skip axis는 끝난 투표 결과를 그대로 쓰고(다른 단계와 같은 뜻), enabled: false는 추출기 값으로 적재한다 (CodeRabbit 리뷰 반영)
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 1})
        self.run_with(config, {"openai:a": [self.output]}, axis_answers=[self.axis_answer({2: "APOLOGY_SINCERITY"})])
        state = self.run_with(config, {}, start="load", skip=["axis"])
        sql = Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8")
        self.assertIn("'분류', 'APOLOGY_SINCERITY', ", sql.split("RETURNING id INTO v_case_id")[1])
        disabled = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 1, "enabled": False})
        state = self.run_with(disabled, {}, start="load")
        inserted = Path(state["stages"]["load"]["outputs"]["sqlFile"]).read_text(encoding="utf-8").split("RETURNING id INTO v_case_id")[1]
        self.assertNotIn("APOLOGY_SINCERITY", inserted)
        self.assertIn("'분류', NULL, NULL, NULL, 2);", inserted)  # 추출기 값(null) · 투표 기록 · 요약어 후보 없음
        self.assertFalse(any("투표 결과 사용" in line for line in self.logs[-5:]))

    def test_axis_rerun_from_axis_keepsGeneration(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, axis={"runs": 1})
        self.run_with(config, {"openai:a": [self.output]})
        state = self.run_with(config, {}, axis_answers=[self.axis_answer({6: "PRINCIPLE_RELATION"})], start="axis")
        self.assertEqual(state["stages"]["axis"]["outputs"]["changedFromExtract"], [6])
        self.assertEqual(state["stages"]["generate"]["status"], "done")  # 생성은 다시 돌지 않았다 (응답 목록이 비어 있음)

    def test_axis_external_send_notice_withFreeModelsAndContamination(self):
        # 무료 모델(free:)만 쓰고 점검 단계도 돌면 시작 시점에 축 모델을 모른다. 그래도 생성 공급자로 안내한다 (리뷰 반영)
        config = self.config(generate={"models": ["free:gemini"]}, contamination={"enabled": True})
        lines = []
        with mock.patch.object(pipeline, "STAGES", ("contamination", "axis")):
            pipeline.run_pipeline(config, functions={"contamination": lambda c, s, log: {}, "axis": lambda c, s, log: {}},
                                  log=lines.append)
        self.assertTrue(any(line.startswith("외부 전송: 가치관 축 분류 단계") and "gemini" in line for line in lines), lines)

    def test_axis_config_validation(self):
        for changes, message in (({"model": "free:gemini"}, "stages.axis.model"), ({"model": "manual:x"}, "manual"),
                                 ({"runs": 0}, "stages.axis.runs"), ({"maxRetries": -1}, "stages.axis.maxRetries"),
                                 ({"delay": "1"}, "stages.axis.delay"), ({"delay": -1}, "stages.axis.delay"),
                                 ({"temperature": "0.2"}, "stages.axis.temperature"),
                                 ({"temperature": True}, "stages.axis.temperature"),
                                 ({"delay": float("nan")}, "stages.axis.delay"),
                                 ({"delay": float("inf")}, "stages.axis.delay"),
                                 ({"temperature": float("nan")}, "stages.axis.temperature"),
                                 ({"temperature": float("-inf")}, "stages.axis.temperature")):
            with self.subTest(changes):
                with self.assertRaises(pipeline.PipelineError) as ctx:
                    self.config(axis=changes)
                self.assertIn(message, str(ctx.exception))
        with self.assertRaises(pipeline.PipelineError) as ctx:  # 축 모델도 생성 모델도 없음
            self.config(generate={"enabled": False, "models": []})
        self.assertIn("stages.axis.model", str(ctx.exception))
        self.config(generate={"enabled": False, "models": []}, axis={"enabled": False})  # 축을 끄면 된다

    def test_script_config_validation(self):
        valid_cast = {"prosecutor": "naero", "defense": "nambul"}
        self.config(script={"enabled": False})  # 꺼져 있으면 model · cast가 없어도 통과
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.config(script={"enabled": True, "cast": valid_cast})  # model 없음
        self.assertIn("stages.script.model", str(ctx.exception))
        for cast, message in (({"prosecutor": "naero"}, "defense"),
                              ({"prosecutor": "naero", "defense": "naero"}, "같은 캐릭터"),
                              ({"prosecutor": "naero", "defense": "law"}, "defense"),
                              (None, "cast")):
            with self.subTest(cast):
                with self.assertRaises(pipeline.PipelineError) as ctx:
                    self.config(script={"enabled": True, "model": "openai:a", "cast": cast})
                self.assertIn(message, str(ctx.exception))
        for changes, message in (({"model": "manual:x"}, "manual"), ({"maxRetries": -1}, "stages.script.maxRetries"),
                                 ({"maxTokens": 0}, "stages.script.maxTokens"), ({"timeout": 0}, "stages.script.timeout"),
                                 ({"delay": -1}, "stages.script.delay"), ({"temperature": "0.2"}, "stages.script.temperature")):
            with self.subTest(changes):
                with self.assertRaises(pipeline.PipelineError) as ctx:
                    self.config(script={"enabled": True, "cast": valid_cast, **changes})
                self.assertIn(message, str(ctx.exception))
        self.config(script={"enabled": True, "model": "openai:a", "cast": valid_cast})  # 정상 설정은 통과

    def test_script_stage_generates_evidence_lines_from_selected_judgment(self):
        """BE-107: 판단 요소마다 증거조사 대사 한 줄, 검사(UP) 먼저 변호인(DOWN) 다음, 배역은 설정값 그대로 쓴다."""
        config = self.config(generate={"models": ["openai:a"], "runs": 1},
                             script={"enabled": True, "model": "openai:a",
                                     "cast": {"prosecutor": "naero", "defense": "nambul"}})
        script_lines = ["검사 측 증거1 대사입니다.", "검사 측 증거2 대사입니다.", "검사 측 증거3 대사입니다.",
                        "변호인 측 증거1 대사입니다.", "변호인 측 증거2 대사입니다.", "변호인 측 증거3 대사입니다."]
        state = self.run_with(config, {"openai:a": [self.output]}, until="script", script_answers=list(script_lines))
        outputs = state["stages"]["script"]["outputs"]
        self.assertEqual(outputs["count"], 6)
        self.assertEqual(outputs["failed"], 0)
        self.assertFalse(outputs["stopped"])
        self.assertTrue(outputs["publishable"])
        evidence_data = load_json(outputs["evidenceFile"])
        self.assertTrue(evidence_data["publishable"])
        evidence = evidence_data["evidence"]
        self.assertEqual([e["factorId"] for e in evidence], [2, 3, 5, 7, 8, 9])
        self.assertEqual([e["role"] for e in evidence], ["prosecutor"] * 3 + ["defense"] * 3)
        self.assertEqual([e["character"] for e in evidence], ["naero"] * 3 + ["nambul"] * 3)
        self.assertEqual([e["text"] for e in evidence], script_lines)
        # 대사 재료는 select 출력의 reason이 아니라 case.json의 label이다 (peanutboi710님 리뷰 1번, PR #277)
        case_labels = {f["factorId"]: f["label"] for f in self.case["factors"]}
        for i, e in enumerate(evidence):
            self.assertIn(case_labels[e["factorId"]], self.script_requests[i][2])
        # system 프롬프트에 배역별(검사 · 변호인) 블록이 실제로 들어갔는지 확인
        self.assertIn("검사", self.script_requests[0][1])
        self.assertIn("변호인", self.script_requests[3][1])

    def test_script_stage_full_writes_script_beside_evidence(self):
        """BE-121: full이면 U0 · U2 · U3 · U4를 만들어 script.json을 쓴다. evidence.json(BE-108 적재 계약)은 그대로 둔다.
        기사(inputs.paper)가 없으면 대본 단계가 기사를 먼저 만들고 1막 해설까지 만든다."""
        classification = {"mood": "impulsive", "issue": "admitted", "victim": "punish", "thirdParty": False,
                          "sensitiveFlags": ["MENTAL_HEALTH"]}
        config = self.config(generate={"models": ["openai:a"], "runs": 1},
                             script={"enabled": True, "model": "openai:a", "full": True, "classification": classification,
                                     "cast": {"prosecutor": "nambul", "defense": "naero"}})
        design = {"plea": "인정", "issues": ["경위", "행동", "회복"],
                  "questions": [{"q": "질문 1입니까?", "a": "아니요"}, {"q": "질문 2입니까?", "a": "예"},
                                {"q": "공소사실을 인정합니까?", "a": "예"}]}
        opening = {"indict": ["공소사실을 말씀드리겠습니다.", "적용 법조입니다."], "plea": ["인정합니다.", "살펴 주십시오."],
                   "issues_narr": ["쟁점이에요.", "눈여겨보세요."],
                   "questions": [{"q": "질문 1입니까?", "a": "아니요"}, {"q": "질문 2입니까?", "a": "네"}],
                   "cards": {"indict": {"ul": ["요약"], "law": "법조"}, "plea": {"b": "인정", "ul": ["주장"]},
                             "issues": {"p": "정리", "ul": ["쟁점"]}}}
        closing = {"prosecutor": "검사 측 의견입니다.", "defense": "변호인 측 최종변론입니다."}
        # 숫자를 넣지 않는다 — 입력에 없는 숫자는 검수 오류다(BE-122)
        evidence_lines = [f"증거 대사 {name}입니다." for name in ("가", "나", "다", "라", "마", "바")]
        paper = {"headline": "제목", "deck": "부제", "caption": "설명", "article": ["문단1", "문단2"]}
        intro = {"lines": ["하나예요.", "둘이에요.", "셋이에요.", "넷이에요.", "다섯이에요."]}
        answers = [json.dumps(v, ensure_ascii=False) for v in (paper, design, intro, opening)] + evidence_lines \
            + [json.dumps(closing, ensure_ascii=False)]
        state = self.run_with(config, {"openai:a": [self.output]}, until="script", script_answers=answers)
        outputs = state["stages"]["script"]["outputs"]
        self.assertTrue(outputs["publishable"])  # 증거조사는 다 만들었다
        self.assertEqual(outputs["pending"], [])
        self.assertTrue(outputs["scriptPublishable"])
        self.assertEqual(outputs["reviewErrors"], 0)
        evidence_data = load_json(outputs["evidenceFile"])
        self.assertEqual([e["text"] for e in evidence_data["evidence"]], evidence_lines)
        script = load_json(outputs["scriptFile"])
        self.assertEqual(sorted(script["acts"]), ["1a", "2", "3"])
        self.assertEqual(script["paper"]["headline"], "제목")
        self.assertEqual(script["sensitiveNotice"], "이 사건에는 정신건강과 관련된 내용이 나와요.")
        self.assertEqual(script["acts"]["2"]["look"], {"nambul": "role:prosecutor", "naero": "role:defense"})
        # 증거조사 프롬프트에도 민감 가드가 들어갔다
        self.assertIn("정신", self.script_requests[4][1])

    def test_script_stage_repairs_review_errors(self):
        """BE-122 2차: 검수 오류가 난 줄만 고쳐 쓰고 다시 검사한다. 고친 기록(repairs)은 script.json에 남는다.
        repairRetries가 0이면 고치지 않는다."""
        classification = {"mood": "impulsive", "issue": "admitted", "victim": "punish", "thirdParty": False,
                          "sensitiveFlags": []}
        design = {"plea": "인정", "issues": ["경위", "행동", "회복"],
                  "questions": [{"q": "질문 가입니까?", "a": "아니요"}, {"q": "질문 나입니까?", "a": "예"},
                                {"q": "공소사실을 인정합니까?", "a": "예"}]}
        opening = {"indict": ["공소사실을 말씀드리겠습니다.", "적용 법조입니다."], "plea": ["인정합니다.", "살펴 주십시오."],
                   "issues_narr": ["쟁점이에요.", "눈여겨보세요."],
                   "questions": [{"q": "질문 가입니까?", "a": "아니요"}, {"q": "질문 나입니까?", "a": "네"}],
                   "cards": {"indict": {"ul": ["요약"], "law": "법조"}, "plea": {"b": "인정", "ul": ["주장"]},
                             "issues": {"p": "정리", "ul": ["쟁점"]}}}
        bad_closing = {"prosecutor": "검사 측 의견입니다.", "defense": "변호인 측 최종변론입니다. 피고인은 77번 사과했습니다."}
        paper = {"headline": "제목", "deck": "부제", "caption": "설명", "article": ["문단1", "문단2"]}
        intro = {"lines": ["하나예요.", "둘이에요.", "셋이에요.", "넷이에요.", "다섯이에요."]}
        evidence_lines = [f"증거 대사 {name}입니다." for name in ("가", "나", "다", "라", "마", "바")]
        answers = [json.dumps(v, ensure_ascii=False) for v in (paper, design, intro, opening)] + evidence_lines \
            + [json.dumps(bad_closing, ensure_ascii=False)]
        fixed_text = "변호인 측 최종변론입니다. 피고인은 여러 차례 사과했습니다."
        for retries, extra, publishable in ((2, [json.dumps({"text": fixed_text}, ensure_ascii=False)], True), (0, [], False)):
            with self.subTest(repairRetries=retries):
                # 실행 이름을 나눠야 앞 실행의 단계 결과(state.json)를 이어 쓰지 않는다
                config = self.config({"name": f"repair-{retries}"}, generate={"models": ["openai:a"], "runs": 1},
                                     script={"enabled": True, "model": "openai:a", "full": True,
                                             "classification": classification, "repairRetries": retries,
                                             "cast": {"prosecutor": "naero", "defense": "nambul"}})
                state = self.run_with(config, {"openai:a": [self.output]}, until="script", script_answers=answers + extra)
                outputs = state["stages"]["script"]["outputs"]
                script = load_json(outputs["scriptFile"])
                self.assertEqual(outputs["scriptPublishable"], publishable)
                self.assertEqual(outputs["reviewErrors"], 0 if publishable else 1)
                self.assertEqual(len(script["repairs"]), 1 if retries else 0)
                if retries:
                    self.assertEqual(script["acts"]["3"]["steps"][1]["text"], fixed_text)

    def test_load_paper_validates_configured_article(self):
        """inputs.paper를 설정했는데 파일이 없거나 기사 형식이 아니면 멈춘다 — 조용히 무시하면 새 기사가 생성돼 1막 해설이
        넘긴 기사와 다른 기사를 따라간다(PR #331 리뷰). 설정하지 않았으면 None."""
        classification = {"mood": "tragic", "issue": "admitted", "victim": "punish"}
        config = self.config(script={"enabled": True, "model": "openai:a", "full": True, "classification": classification,
                                     "cast": {"prosecutor": "naero", "defense": "nambul"}})
        self.assertIsNone(pipeline.load_paper(config))
        path = self.dir / "paper.json"
        config["inputs"]["paper"] = str(path)
        with self.assertRaises(pipeline.PipelineError) as ctx:
            pipeline.load_paper(config)
        self.assertIn("파일이 없습니다", str(ctx.exception))
        for bad in ("[1, 2]", '"문자열"', '{"headline": "", "article": ["하나"]}', "{깨진 JSON"):
            with self.subTest(bad):
                path.write_text(bad, encoding="utf-8")
                with self.assertRaises(pipeline.PipelineError):
                    pipeline.load_paper(config)
        write_json(path, {"headline": " 제목 ", "deck": "", "caption": "설명", "article": [" 문단1 ", "문단2"]})
        self.assertEqual(pipeline.load_paper(config), {"desk": "사회부", "headline": "제목", "deck": None,
                                                       "caption": "설명", "article": ["문단1", "문단2"]})

    def test_bad_paper_input_keeps_previous_script_output(self):
        """inputs.paper 검증은 이전 결과를 지우기 전에 한다 — 입력 오류가 지난 evidence.json을 날리면 안 된다(PR #329 CodeRabbit 리뷰)."""
        cast = {"prosecutor": "naero", "defense": "nambul"}
        config = self.config(generate={"models": ["openai:a"], "runs": 1},
                             script={"enabled": True, "model": "openai:a", "cast": cast})
        state = self.run_with(config, {"openai:a": [self.output]}, until="script",
                              script_answers=[f"증거 대사 {i}입니다." for i in range(6)])
        evidence_file = Path(state["stages"]["script"]["outputs"]["evidenceFile"])
        self.assertTrue(evidence_file.exists())
        classification = {"mood": "tragic", "issue": "admitted", "victim": "punish"}
        full = self.config(generate={"models": ["openai:a"], "runs": 1},
                           script={"enabled": True, "model": "openai:a", "full": True, "classification": classification, "cast": cast})
        full["inputs"]["paper"] = str(self.dir / "missing-paper.json")
        with self.assertRaises(pipeline.PipelineError):
            pipeline.stage_script(full, pipeline.load_state(full), self.logs.append)
        self.assertTrue(evidence_file.exists())  # 지난 결과가 남아 있다

    def test_script_full_config_requires_classification(self):
        base = {"enabled": True, "model": "openai:a", "cast": {"prosecutor": "naero", "defense": "nambul"}}
        for changes, message in (({"full": True}, "classification"), ({"full": "yes"}, "stages.script.full"),
                                 ({"full": True, "classification": {"mood": "x", "issue": "admitted", "victim": "punish"}},
                                  "classification.mood"),
                                 ({"unitTimeout": 0}, "stages.script.unitTimeout"),
                                 ({"repairRetries": -1}, "stages.script.repairRetries")):
            with self.subTest(changes):
                with self.assertRaises(pipeline.PipelineError) as ctx:
                    self.config(script={**base, **changes})
                self.assertIn(message, str(ctx.exception))
        self.config(script={**base, "full": True, "classification": {"mood": "tragic", "issue": "admitted", "victim": "punish"}})

    CLASSIFY_ANSWER = {"mood": "impulsive", "issues": ["admitted"], "victimWill": "punish", "recovery": "partial",
                       "thirdParty": False, "sensitiveFlags": ["PROFANITY_QUOTED"]}

    def test_classify_stage_votes_and_counts_tokens(self):
        """BE-123: runs회 묻고 표를 센다. 형식이 틀린 회차는 다시 묻고, 토큰은 응답마다 더한다. 판결 정보는 보내지 않는다."""
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, classify={"enabled": True, "runs": 3})
        answers = [self.CLASSIFY_ANSWER, "JSON 아님", {**self.CLASSIFY_ANSWER, "mood": "cold"}, self.CLASSIFY_ANSWER]
        state = self.run_with(config, {"openai:a": [self.output]}, until="classify", classify_answers=answers)
        outputs = state["stages"]["classify"]["outputs"]
        self.assertEqual(outputs["classification"]["mood"], "impulsive")
        self.assertEqual(outputs["classification"]["issue"], "admitted")
        self.assertEqual(outputs["runs"], 3)
        self.assertEqual(outputs["attempts"], 4)
        self.assertEqual(outputs["failures"], 1)
        self.assertEqual(outputs["tokens"], {"inputTokens": 400, "outputTokens": 80, "totalTokens": 480})
        saved = load_json(outputs["classificationFile"])
        self.assertEqual(saved["votes"]["mood"]["counts"], {"impulsive": 2, "cold": 1})
        self.assertEqual(saved["recovery"], "partial")
        self.assertEqual(outputs["model"], "openai:a")  # model이 없으면 생성 모델(axis와 같은 규칙)
        sent = self.classify_requests[0][1]
        self.assertIn(self.case["overview"], sent)
        self.assertNotIn(self.output["reasoning"], sent)  # 판결 이유는 보내지 않는다
        self.assertTrue(any(line.startswith("외부 전송: 사건 분류 단계") for line in self.logs))

    def test_script_full_uses_classify_output_when_not_configured(self):
        """BE-123: stages.script.classification이 없으면 classify 결과를 쓴다(설정 값이 있으면 그쪽이 우선)."""
        config = self.config(generate={"models": ["openai:a"], "runs": 1}, classify={"enabled": True, "runs": 1},
                             script={"enabled": True, "model": "openai:a", "full": True,
                                     "cast": {"prosecutor": "naero", "defense": "nambul"}})
        state = self.run_with(config, {"openai:a": [self.output]}, until="classify",
                              classify_answers=[self.CLASSIFY_ANSWER])
        with mock.patch.object(pipeline, "generate_units", side_effect=lambda *a, **k: {
                "units": {}, "paper": None, "evidence": [], "stopped": True, "pending": ["U0"]}) as gen:
            pipeline.stage_script(config, state, self.logs.append)
        self.assertEqual(gen.call_args.args[3]["mood"], "impulsive")
        # 모델이 잡은 플래그 + 규칙(사건 법정형의 사형 · 무기)으로 잡은 플래그
        self.assertEqual(gen.call_args.args[3]["sensitiveFlags"], ["PROFANITY_QUOTED", "CAPITAL_PENALTY"])

    def test_script_full_without_any_classification_fails(self):
        base = {"enabled": True, "model": "openai:a", "full": True, "cast": {"prosecutor": "naero", "defense": "nambul"}}
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.config(script=base)  # 분류 설정도 classify 단계도 없음
        self.assertIn("classification", str(ctx.exception))
        self.config(script=base, classify={"enabled": True})  # classify가 켜져 있으면 통과
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.config(classify={"enabled": True, "runs": 0})
        self.assertIn("stages.classify.runs", str(ctx.exception))

    def test_script_stage_marks_not_publishable_when_quota_exhausted(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1},
                             script={"enabled": True, "model": "openai:a",
                                     "cast": {"prosecutor": "naero", "defense": "nambul"}})
        script_lines = ["검사 측 증거1 대사입니다.", llm.LLMQuotaExhaustedError("일 한도 초과")]
        state = self.run_with(config, {"openai:a": [self.output]}, until="script", script_answers=list(script_lines))
        outputs = state["stages"]["script"]["outputs"]
        self.assertTrue(outputs["stopped"])
        self.assertFalse(outputs["publishable"])
        self.assertFalse(load_json(outputs["evidenceFile"])["publishable"])

    def test_script_stage_rejects_case_changed_after_selection(self):
        """peanutboi710님 리뷰(PR #277, CodeRabbit): select 이후 case.json의 label이 바뀌면 그 사실로
        모델을 부르기 전에 멈춰야 한다 — stage_load와 같은 provenance 검사를 쓴다."""
        config = self.config(generate={"models": ["openai:a"], "runs": 1},
                             script={"enabled": True, "model": "openai:a",
                                     "cast": {"prosecutor": "naero", "defense": "nambul"}})
        state = self.run_with(config, {"openai:a": [self.output]}, until="select")

        changed_case = copy.deepcopy(self.case)
        changed_case["factors"][0]["label"] = "select 이후 바뀐 사실"
        write_json(self.raw_config["inputs"]["case"], changed_case)

        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"openai:a": [self.output]}, start="script", script_answers=[])
        self.assertIn("--from generate", str(ctx.exception))

    def test_script_stage_requires_select_stage(self):
        config = self.config(generate={"models": ["openai:a"], "runs": 1},
                             script={"enabled": True, "model": "openai:a",
                                     "cast": {"prosecutor": "naero", "defense": "nambul"}})
        state = pipeline.load_state(config)
        with self.assertRaises(pipeline.PipelineError) as ctx:
            pipeline.stage_script(config, state, self.logs.append)
        self.assertIn("select", str(ctx.exception))

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

    def test_apply_sql_rejects_service(self):
        # service 접속은 서비스 파일의 host를 써서 호스트 검사를 우회한다 (#143 리뷰)
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline.apply_sql(self.dir / "x.sql", {"mode": "psql", "url": "postgresql:///lawnambul?service=remote"})
        self.assertIn("service", str(ctx.exception))
        with mock.patch.dict(os.environ, {"PGSERVICE": "remote"}, clear=True):
            with self.assertRaises(pipeline.PipelineError):
                pipeline.apply_sql(self.dir / "x.sql", {"mode": "psql", "url": "postgresql:///lawnambul"})
            with self.assertRaises(pipeline.RagUnavailable):  # RAG 검색도 같은 검사
                pipeline.query_db("SELECT 1;", {"mode": "psql", "url": "postgresql://localhost/lawnambul"})

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

    def test_apply_sql_returnsNoticeAndWarning(self):
        # 적재 SQL의 WARNING(예: 번호 · 라벨이 달라 가치관 축을 반영하지 못한 요소)도 파이프라인 로그에 남긴다 (리뷰 반영)
        stderr = ("psql:<stdin>:10: NOTICE:  사건 적재 건너뜀: 같은 제목의 DRAFT 사건이 이미 있습니다\n"
                  "psql:<stdin>:10: WARNING:  가치관 축: 번호 · 라벨이 DB와 다른 요소 1개는 바꾸지 않았습니다\n"
                  "psql:<stdin>:10: NOTICE:  가치관 축: 0행 갱신\n"
                  "기타 출력\n").encode("utf-8")
        (self.dir / "x.sql").write_text("SELECT 1;", encoding="utf-8")
        done = SimpleNamespace(returncode=0, stderr=stderr)
        with mock.patch("shutil.which", return_value="/usr/bin/psql"), mock.patch("subprocess.run", return_value=done):
            messages = pipeline.apply_sql(self.dir / "x.sql", {"mode": "psql", "url": "postgresql://localhost:5432/x"})
        self.assertEqual(len(messages), 3)
        self.assertTrue(messages[1].startswith("⚠ ") and "WARNING" in messages[1])
        self.assertFalse(messages[0].startswith("⚠"))

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

    # ---- 모델 대체 체인 (BE-45)

    def test_model_chain_config_validation(self):
        for stage, value, part in (("extract", [], "stages.extract.model"),
                                   ("extract", ["openai:a", "openai:a"], "같은 모델"),
                                   ("extract", ["openai:a", ""], "stages.extract.model"),
                                   ("extract", ["openai:a", "manual:b"], "manual"),
                                   ("court", [], "stages.court.model"),
                                   ("court", ["gemini:g", "unknown:x"], "unknown")):
            bad = copy.deepcopy(self.raw_config)
            bad["stages"][stage] = {"model": value}
            write_json(self.dir / "bad-chain.json", bad)
            with self.assertRaises(pipeline.PipelineError, msg=f"{stage} {value}") as ctx:
                pipeline.load_config(self.dir / "bad-chain.json")
            self.assertIn(part, str(ctx.exception), msg=f"{stage} {value}")
        config = self.config(extract={"model": ["claude-opus-5-5", "gemini:g"]}, court={"model": ["openai:o", "gemini:g"]})
        self.assertEqual(pipeline.court_model(config), ["openai:o", "gemini:g"])
        self.assertEqual(self.config(extract={"model": ["gemini:g", "openai:o"]})["stages"]["extract"]["model"],
                         ["gemini:g", "openai:o"])

    def test_model_chain_transfer_notice_lists_every_provider(self):
        config = self.config(extract={"enabled": True, "model": ["claude-opus-5-5", "gemini:g"]},
                             court={"enabled": True, "model": ["openai:o", "gemini:g"]})
        self.assertEqual(pipeline.extract_provider(config), "anthropic, gemini")
        self.assertEqual(pipeline.providers_text(pipeline.court_model(config)), "gemini, openai")
        self.assertEqual(pipeline.extract_provider(self.config()), "anthropic")  # 문자열 하나면 그대로

        def stop(*args, **kwargs):
            raise pipeline.PipelineError("테스트 중단")

        functions = {stage: stop for stage in pipeline.STAGES}
        with self.assertRaises(pipeline.PipelineError):
            pipeline.run_pipeline(config, functions=functions, until="court", log=self.logs.append)
        notices = [line for line in self.logs if line.startswith("외부 전송")]
        self.assertTrue(any("비식별화" in line and "anthropic, gemini" in line for line in notices))
        self.assertTrue(any("재판부" in line and "gemini, openai" in line for line in notices))

    # ---- 무료 모델 자동 사용 (BE-45)

    FREE_POOL = ["gemini:g1", "gemini:g2", "gemini:g3", "gemini:g4"]
    CLEAN = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 60, "fineAmount": None, "suspensionMonths": None}

    def free_config(self, max_models=2, contamination=None, runs=2, **top):
        """generate.models = ["free:gemini"] (풀은 FREE_POOL로 고정). contamination을 주면 점검도 켠다."""
        patcher = mock.patch.object(pipeline, "free_tier_model_list", return_value=list(self.FREE_POOL))
        patcher.start()
        self.addCleanup(patcher.stop)
        stages = {"generate": {"models": ["free:gemini"], "runs": runs, "maxRetries": 0}}
        if contamination is not None:
            stages["contamination"] = {"enabled": True, "runs": runs, "criteria": {"minAnswered": 1}, **contamination}
        return self.config({"freeModels": {"max": max_models, **top}}, **stages)

    def test_free_models_config_validation(self):
        pool = mock.patch.object(pipeline, "free_tier_model_list", return_value=list(self.FREE_POOL))
        pool.start()
        self.addCleanup(pool.stop)
        for top, stages, part in (
                ({"freeModels": {"max": 0}}, {"generate": {"models": ["free:gemini"]}}, "freeModels.max"),
                ({"freeModels": {"max": True}}, {"generate": {"models": ["free:gemini"]}}, "freeModels.max"),
                ({"freeModels": {"include": "g1"}}, {"generate": {"models": ["free:gemini"]}}, "freeModels.include"),
                ({"freeModels": {"exclude": [1]}}, {"generate": {"models": ["free:gemini"]}}, "freeModels.exclude"),
                ({"freeModels": {"unknown": 1}}, {"generate": {"models": ["free:gemini"]}}, "freeModels는"),
                ({}, {"generate": {"models": ["free:nobody"]}}, "'nobody'"),
                ({"freeModels": {"exclude": ["g1", "g2", "g3", "g4"]}}, {"generate": {"models": ["free:gemini"]}}, "무료 모델이 없다"),
                ({"freeModels": {"include": ["zzz"]}}, {"generate": {"models": ["free:gemini"]}}, "무료 모델이 없다"),
                ({}, {"extract": {"model": ["claude-opus-5-5", "free:gemini"]}}, "stages.extract.model에는 free:"),
                ({}, {"court": {"model": "free:gemini"}}, "stages.court.model에는 free:")):
            raw = copy.deepcopy(self.raw_config)
            raw.update(top)
            for stage, changes in stages.items():
                raw["stages"].setdefault(stage, {}).update(changes)
            write_json(self.dir / "bad-free.json", raw)
            with self.assertRaises(pipeline.PipelineError, msg=f"{top} {stages}") as ctx:
                pipeline.load_config(self.dir / "bad-free.json")
            self.assertIn(part, str(ctx.exception), msg=f"{top} {stages}")
        config = self.free_config(include=["g3", "g1"], exclude=["g1"])
        self.assertEqual(pipeline.free_pool(config, {"gemini"}), ["gemini:g3"])

    def test_free_models_pool_order_include_priority(self):
        config = self.free_config(include=["g3", "g1", "g2"])
        self.assertEqual(pipeline.free_pool(config), ["gemini:g3", "gemini:g1", "gemini:g2"])

    def test_free_models_resolve_keeps_list_on_resume(self):
        config = self.free_config(max_models=2)
        state, logs = {"stages": {}}, []
        pipeline.resolve_free_models(config, state, logs.append)
        fm = state["freeModels"]
        self.assertEqual((fm["active"], fm["reserve"]), (["gemini:g1", "gemini:g2"], ["gemini:g3", "gemini:g4"]))
        self.assertTrue(any("gemini:g1, gemini:g2" in line and "대기 gemini:g3" in line for line in logs))
        fm["reserve"].pop(0)
        fm["active"][0] = "gemini:g3"  # 실행 중 자리를 채운 상태
        pipeline.resolve_free_models(config, state, logs.append)  # 설정이 같으면 이어서 같은 목록
        self.assertEqual(state["freeModels"]["active"], ["gemini:g3", "gemini:g2"])
        pipeline.resolve_free_models(config, state, logs.append, fresh=True)  # 처음부터 다시
        self.assertEqual(state["freeModels"]["active"], ["gemini:g1", "gemini:g2"])
        changed = self.free_config(max_models=3)  # 설정이 바뀌면 다시 펼친다
        pipeline.resolve_free_models(changed, state, logs.append)
        self.assertEqual(state["freeModels"]["active"], ["gemini:g1", "gemini:g2", "gemini:g3"])
        plain = self.config()  # free: 표시가 없으면 기록을 지운다
        pipeline.resolve_free_models(plain, state, logs.append)
        self.assertNotIn("freeModels", state)

    def test_expand_models_keeps_position_and_dedupes(self):
        state = {"freeModels": {"active": ["gemini:g1", "gemini:g2"]}}
        self.assertEqual(pipeline.expand_models(state, ["openai:a", "free:gemini", "gemini:g2", "openai:b"]),
                         ["openai:a", "gemini:g1", "gemini:g2", "openai:b"])

    def test_free_models_generate_uses_only_active_models(self):
        config = self.free_config(max_models=2)
        answers = {"gemini:g1": [self.output] * 2, "gemini:g2": [self.output] * 2}
        state = self.run_with(config, answers, caller=self.recording_caller(answers))
        self.assertEqual({spec for spec, _ in self.calls}, {"gemini:g1", "gemini:g2"})  # g3 · g4는 대기
        self.assertEqual(list(state["stages"]["generate"]["outputs"]["models"]), ["gemini:g1", "gemini:g2"])
        self.assertEqual(state["freeModels"]["reserve"], ["gemini:g3", "gemini:g4"])

    def test_free_models_contamination_replaces_exhausted_model(self):
        config = self.free_config(max_models=2, contamination={})
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"contamination:gemini:g1": [quota],  # 첫 호출에서 소진 → 대기 g3가 자리를 채운다
                   "contamination:gemini:g2": [self.CLEAN] * 2, "contamination:gemini:g3": [self.CLEAN] * 2,
                   "gemini:g2": [self.output] * 2, "gemini:g3": [self.output] * 2}
        state = self.run_with(config, answers, caller=self.recording_caller(answers))
        results = state["stages"]["contamination"]["outputs"]["results"]
        self.assertEqual(results["gemini:g1"]["replacedBy"], "gemini:g3")
        self.assertEqual(results["gemini:g3"]["answered"], 2)  # 새 모델은 자기 이름으로 처음부터 runs회
        self.assertNotIn("gemini:g1", state["stages"]["contamination"]["outputs"]["excluded"])  # 대체된 모델에는 제외 설정을 적용하지 않는다
        self.assertEqual(list(state["stages"]["generate"]["outputs"]["models"]), ["gemini:g3", "gemini:g2"])  # 자리는 그대로(g1 → g3)
        self.assertNotIn(("gemini:g1", True), self.calls)  # 소진된 모델은 생성에 쓰지 않는다
        self.assertEqual(state["freeModels"]["replaced"], [{"model": "gemini:g1", "by": "gemini:g3", "stage": "contamination"}])

    def test_free_models_generate_replacement_is_vetted_and_keeps_finished_runs(self):
        config = self.free_config(max_models=2, contamination={})
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"contamination:gemini:g1": [self.CLEAN] * 2, "contamination:gemini:g2": [self.CLEAN] * 2,
                   "contamination:gemini:g3": [self.CLEAN] * 2,  # 대신 들어오기 전에 점검을 받는다
                   "gemini:g1": [self.output, quota], "gemini:g2": [self.output] * 2, "gemini:g3": [self.output] * 2}
        state = self.run_with(config, answers, caller=self.recording_caller(answers))
        order = [(spec, generating) for spec, generating in self.calls]
        vet = [i for i, call in enumerate(order) if call == ("gemini:g3", False)]
        first_generation = order.index(("gemini:g3", True))
        self.assertEqual(len(vet), 2)
        self.assertTrue(all(i < first_generation for i in vet))  # 점검이 생성보다 먼저
        models = state["stages"]["generate"]["outputs"]["models"]
        self.assertEqual({spec: (m["runs"], m["valid"]) for spec, m in models.items()},
                         {"gemini:g1": (2, 1), "gemini:g2": (2, 2), "gemini:g3": (2, 2)})
        # 소진된 g1이 끝낸 회차는 g1 이름으로 남아 선택 후보에 들어간다 (g3가 g1의 남은 회차를 대신 만들지 않았다)
        self.assertEqual(state["freeModels"]["kept"], ["gemini:g1"])
        self.assertEqual(pipeline.active_models(config, state), ["gemini:g3", "gemini:g2", "gemini:g1"])
        self.assertEqual(pipeline.active_models(config, state, include_kept=False), ["gemini:g3", "gemini:g2"])
        results = state["stages"]["contamination"]["outputs"]["results"]
        self.assertEqual(results["gemini:g3"]["answered"], 2)  # 대체 모델의 점검 결과도 기록된다
        self.assertEqual(state["stages"]["generate"]["outputs"]["freeModels"]["replaced"],
                         [{"model": "gemini:g1", "by": "gemini:g3", "stage": "generate"}])

    def test_free_models_replacement_failing_vet_is_skipped(self):
        known = {"knowsCase": True, "note": "기사로 봤다"}
        config = self.free_config(max_models=2, contamination={"onContaminated": "exclude"})
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"contamination:gemini:g1": [self.CLEAN] * 2, "contamination:gemini:g2": [self.CLEAN] * 2,
                   "contamination:gemini:g3": [known] * 2,  # 사건을 아는 모델 → 제외하고 다음 대기 g4를 본다
                   "contamination:gemini:g4": [self.CLEAN] * 2,
                   "gemini:g1": [self.output, quota], "gemini:g2": [self.output] * 2, "gemini:g4": [self.output] * 2}
        state = self.run_with(config, answers, caller=self.recording_caller(answers))
        fm = state["freeModels"]
        self.assertEqual([(s["model"], s["reason"]) for s in fm["skipped"]], [("gemini:g3", "사전 학습 점검에서 제외")])
        self.assertEqual(fm["replaced"], [{"model": "gemini:g1", "by": "gemini:g4", "stage": "generate"}])
        self.assertNotIn(("gemini:g3", True), self.calls)  # 점검에서 걸린 모델은 생성하지 않는다
        self.assertIn("gemini:g3", state["stages"]["contamination"]["outputs"]["excluded"])

    def test_free_models_from_generate_keeps_list_and_never_revives_replaced_model(self):
        config = self.free_config(max_models=2, contamination={})
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"contamination:gemini:g1": [quota], "contamination:gemini:g2": [self.CLEAN] * 2,
                   "contamination:gemini:g3": [self.CLEAN] * 2,
                   "gemini:g2": [self.output] * 2, "gemini:g3": [self.output] * 2}
        state = self.run_with(config, answers)
        self.assertEqual(state["freeModels"]["active"], ["gemini:g3", "gemini:g2"])
        # --from generate: 점검을 다 받지 못한 g1이 되살아나면 안 된다 (목록 유지, 점검을 통과한 g3로 생성)
        again = {"gemini:g2": [self.output] * 2, "gemini:g3": [self.output] * 2}
        state = self.run_with(config, again, caller=self.recording_caller(again), start="generate")
        self.assertEqual(state["freeModels"]["active"], ["gemini:g3", "gemini:g2"])
        self.assertEqual({spec for spec, _ in self.calls}, {"gemini:g2", "gemini:g3"})
        # --from contamination: 처음부터 다시 고른다
        fresh = {"contamination:gemini:g1": [self.CLEAN] * 2, "contamination:gemini:g2": [self.CLEAN] * 2,
                 "gemini:g1": [self.output] * 2, "gemini:g2": [self.output] * 2}
        state = self.run_with(config, fresh, start="contamination")
        self.assertEqual(state["freeModels"]["active"], ["gemini:g1", "gemini:g2"])

    def test_free_models_active_models_drops_models_replaced_in_contamination(self):
        config = self.free_config(max_models=2)
        state = {"stages": {"contamination": {"status": "done", "outputs": {
            "excluded": [], "results": {"gemini:g1": {"verdict": "INSUFFICIENT", "replacedBy": "gemini:g3"}}}}},
            "freeModels": {"active": ["gemini:g1", "gemini:g2"], "reserve": [], "kept": [], "replaced": [], "skipped": []}}
        self.assertEqual(pipeline.active_models(config, state), ["gemini:g2"])  # 목록이 어긋나도 점검을 다 못 받은 모델은 뺀다

    def test_free_models_regeneration_replacement_runs_full_count(self):
        config = self.free_config(max_models=2, runs=2)
        config["stages"]["generate"]["maxRetries"] = 1
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"gemini:g1": ["x", "x", quota],  # 검증 통과 회차가 없어 재생성 → 재생성 중 소진
                   "gemini:g2": [self.output] * 2, "gemini:g3": [self.output] * 2}
        state = self.run_with(config, answers)
        models = state["stages"]["generate"]["outputs"]["models"]
        self.assertEqual((models["gemini:g3"]["runs"], models["gemini:g3"]["valid"]), (2, 2))  # 1회가 아니라 runs회

    def test_free_models_generate_failure_keeps_summary(self):
        config = self.free_config(max_models=2)
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"gemini:g1": [self.output, quota], "gemini:g2": [self.output] * 2}
        with mock.patch.object(pipeline, "vet_replacement", side_effect=pipeline.PipelineError("대체 모델 점검 오류")):
            with self.assertRaises(pipeline.PipelineError) as ctx:
                self.run_with(config, answers)
        self.assertIn("대체 모델 점검 오류", str(ctx.exception))
        entry = pipeline.load_state(config)["stages"]["generate"]
        self.assertEqual(entry["status"], "failed")
        self.assertEqual(set(entry["outputs"]["models"]), {"gemini:g1", "gemini:g2"})  # 그때까지의 요약이 남는다
        self.assertIn("batchDir", entry["outputs"])

    def test_free_models_vet_stop_setting_skips_candidate_instead_of_halting(self):
        known = {"knowsCase": True, "note": "기사로 봤다"}
        config = self.free_config(max_models=2, contamination={})  # onContaminated 기본값은 stop
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"contamination:gemini:g1": [self.CLEAN] * 2, "contamination:gemini:g2": [self.CLEAN] * 2,
                   "contamination:gemini:g3": [known] * 2, "contamination:gemini:g4": [self.CLEAN] * 2,
                   "gemini:g1": [self.output, quota], "gemini:g2": [self.output] * 2, "gemini:g4": [self.output] * 2}
        state = self.run_with(config, answers)  # 멈추지 않고 g3를 제외한 뒤 g4가 들어온다
        fm = state["freeModels"]
        self.assertEqual([s["model"] for s in fm["skipped"]], ["gemini:g3"])
        self.assertEqual(fm["replaced"], [{"model": "gemini:g1", "by": "gemini:g4", "stage": "generate"}])
        self.assertTrue(any("멈추지 않고 제외" in line for line in self.logs))

    def test_free_models_contamination_stage_replacement_stop_setting_excludes(self):
        known = {"knowsCase": True, "note": "기사로 봤다"}
        config = self.free_config(max_models=2, contamination={})
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"contamination:gemini:g1": [quota], "contamination:gemini:g2": [self.CLEAN] * 2,
                   "contamination:gemini:g3": [known] * 2,  # 자동으로 들어온 g3가 걸려도 파이프라인은 멈추지 않는다
                   "gemini:g2": [self.output] * 2}
        state = self.run_with(config, answers)
        outputs = state["stages"]["contamination"]["outputs"]
        self.assertIn("gemini:g3", outputs["excluded"])
        self.assertEqual(list(state["stages"]["generate"]["outputs"]["models"]), ["gemini:g2"])

    def test_free_models_no_reserve_continues_without_replacement(self):
        config = self.free_config(max_models=4)  # 풀 4개를 모두 쓰므로 대기가 없다
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"gemini:g1": [quota], "gemini:g2": [self.output] * 2, "gemini:g3": [self.output] * 2,
                   "gemini:g4": [self.output] * 2}
        state = self.run_with(config, answers)
        self.assertTrue(any("자리를 채울 무료 모델이 더 없습니다" in line for line in self.logs))
        self.assertEqual(state["freeModels"]["replaced"], [])
        self.assertEqual(state["stages"]["generate"]["outputs"]["models"]["gemini:g1"]["valid"], 0)

    def test_free_models_explicit_models_are_never_replaced(self):
        config = self.free_config(max_models=2)
        config["stages"]["generate"]["models"] = ["openai:a", "free:gemini"]
        quota = llm.LLMQuotaExhaustedError("일 한도")
        answers = {"openai:a": [quota], "gemini:g1": [self.output] * 2, "gemini:g2": [self.output] * 2}
        state = self.run_with(config, answers)
        self.assertEqual(state["freeModels"]["replaced"], [])  # free:로 쓴 모델이 아니면 대체하지 않는다
        self.assertEqual(state["freeModels"]["active"], ["gemini:g1", "gemini:g2"])

    # ---- 판결문 단계의 무료 등급 백업 (BE-45)

    def patch_free_pool(self):
        patcher = mock.patch.object(pipeline, "free_tier_model_list", return_value=list(self.FREE_POOL))
        patcher.start()
        self.addCleanup(patcher.stop)

    def judgment_config(self, extract_model=("claude-opus-5-5", "free:gemini"), court=None, allow=True, **top):
        self.patch_free_pool()
        stages = {"extract": {"enabled": True, "model": list(extract_model)}}
        if court is not None:
            stages["court"] = {"enabled": True, "model": court}
        return self.config({"allowFreeTierForJudgment": allow, "freeModels": {"max": 2}, **top}, **stages)

    def test_judgment_free_backup_config_validation(self):
        self.patch_free_pool()
        for top, extract, court, part in (
                ({}, ["claude-opus-5-5", "free:gemini"], None, "allowFreeTierForJudgment를 true로"),
                ({"allowFreeTierForJudgment": "yes"}, ["claude-opus-5-5"], None, "true · false다"),
                ({"allowFreeTierForJudgment": True}, ["free:gemini"], None, "유료 모델을 하나 이상 앞에"),
                ({"allowFreeTierForJudgment": True}, ["free:gemini", "claude-opus-5-5"], None, "맨 끝에"),
                ({"allowFreeTierForJudgment": True}, ["claude-opus-5-5", "free:gemini", "openai:o"], None, "맨 끝에"),
                ({"allowFreeTierForJudgment": True}, ["claude-opus-5-5", "free:nobody"], None, "'nobody'"),
                ({"allowFreeTierForJudgment": True, "freeModels": {"exclude": ["g1", "g2", "g3", "g4"]}},
                 ["claude-opus-5-5", "free:gemini"], None, "무료 모델이 없다"),
                ({"allowFreeTierForJudgment": True}, ["claude-opus-5-5"], ["free:gemini"], "stages.court.model: free:는 백업"),
                ({}, ["claude-opus-5-5"], ["openai:o", "free:gemini"], "stages.court.model에는 free:")):
            raw = copy.deepcopy(self.raw_config)
            raw.update(top)
            raw["stages"]["extract"] = {"model": extract}
            if court is not None:
                raw["stages"]["court"] = {"model": court}
            write_json(self.dir / "bad-judgment-free.json", raw)
            with self.assertRaises(pipeline.PipelineError, msg=f"{top} {extract} {court}") as ctx:
                pipeline.load_config(self.dir / "bad-judgment-free.json")
            self.assertIn(part, str(ctx.exception), msg=f"{top} {extract} {court}")
        self.judgment_config()  # 유료 모델 뒤에 free:를 두면 통과
        self.judgment_config(court=["openai:o", "free:gemini"])
        config = self.judgment_config()
        self.assertIs(config["allowFreeTierForJudgment"], True)
        self.assertIs(self.config()["allowFreeTierForJudgment"], False)  # 기본은 꺼짐

    def test_expand_judgment_models(self):
        config = self.judgment_config()
        models, free = pipeline.expand_judgment_models(config, ["claude-opus-5-5", "free:gemini"])
        self.assertEqual((models, free), (["claude-opus-5-5", "gemini:g1", "gemini:g2"], {"gemini:g1", "gemini:g2"}))  # max 2
        self.assertEqual(pipeline.expand_judgment_models(config, "claude-opus-5-5"), ("claude-opus-5-5", set()))  # 표시가 없으면 그대로
        models, free = pipeline.expand_judgment_models(config, ["gemini:g1", "free:gemini"])
        self.assertEqual((models, free), (["gemini:g1", "gemini:g2"], {"gemini:g2"}))  # 직접 적은 모델은 무료 백업이 아니다

    def test_expand_judgment_models_drops_backup_without_key(self):
        config = self.judgment_config()
        logs = []
        with mock.patch.dict(os.environ, {"OPENAI_API_KEY": "k"}, clear=True):
            models, free = pipeline.expand_judgment_models(config, ["openai:o", "free:gemini"], logs.append)
        self.assertEqual((models, free), (["openai:o"], set()))  # 백업이라 실행을 막지 않고 뺀다
        self.assertEqual(len([line for line in logs if "GEMINI_API_KEY" in line]), 2)

    def test_judgment_stages_pass_expanded_models_and_report_free_tier_use(self):
        config = self.judgment_config(court=None)
        report = report_for(self.case)
        report.update(requestedModel="gemini:g1", model="gemini-g1-2026", usedFreeTier=True,
                      fallbacks=[{"model": "claude-opus-5-5", "kind": "overloaded", "error": "과부하"}])
        write_json(self.dir / "r.json", report)
        files = [self.raw_config["inputs"][k] for k in ("case", "court", "report", "source")]
        extract_module = mock.MagicMock()
        extract_module.run.return_value = (files[0], files[1], str(self.dir / "r.json"), files[3])
        extract_module.ExtractError = RuntimeError
        court_module = mock.MagicMock()
        court_module.run.return_value = (str(self.dir / "d.json"), str(self.dir / "cr.json"))
        court_module.CourtDraftError = RuntimeError
        write_json(self.dir / "cr.json", {"model": "gemini-g2", "usedFreeTier": True, "fallbacks": [], "warnings": []})
        with mock.patch.dict(sys.modules, {"extract_case": extract_module, "court_draft": court_module}):
            outputs = pipeline.stage_extract(config, {"stages": {}}, self.logs.append)
            court_outputs = pipeline.stage_court(config, {"stages": {}}, self.logs.append)
        kwargs = extract_module.run.call_args.kwargs
        self.assertEqual(kwargs["model"], ["claude-opus-5-5", "gemini:g1", "gemini:g2"])
        self.assertEqual(kwargs["free_models"], {"gemini:g1", "gemini:g2"})
        self.assertIs(outputs["usedFreeTier"], True)
        # court 모델을 정하지 않으면 extract 목록(free: 포함)을 쓴다
        self.assertEqual(court_module.run.call_args.kwargs["model"], ["claude-opus-5-5", "gemini:g1", "gemini:g2"])
        self.assertEqual(court_module.run.call_args.kwargs["free_models"], {"gemini:g1", "gemini:g2"})
        self.assertIs(court_outputs["usedFreeTier"], True)
        self.assertEqual(len([line for line in self.logs if "무료 등급 백업 모델이 판결문 원문을 처리했습니다" in line]), 2)

    def test_judgment_stages_without_free_tier_use_have_no_flag(self):
        config = self.judgment_config()
        report = report_for(self.case)
        report.update(model="claude-x", usedFreeTier=False)
        write_json(self.dir / "r.json", report)
        files = [self.raw_config["inputs"][k] for k in ("case", "court", "report", "source")]
        extract_module = mock.MagicMock()
        extract_module.run.return_value = (files[0], files[1], str(self.dir / "r.json"), files[3])
        extract_module.ExtractError = RuntimeError
        with mock.patch.dict(sys.modules, {"extract_case": extract_module}):
            outputs = pipeline.stage_extract(config, {"stages": {}}, self.logs.append)
        self.assertNotIn("usedFreeTier", outputs)
        self.assertFalse(any("무료 등급 백업 모델이" in line for line in self.logs))

    def test_judgment_free_backup_notices(self):
        config = self.judgment_config(court=["openai:o", "free:gemini"])
        notices = self.run_notices(config, until="court")
        strong = [line for line in notices if line.startswith("⚠")]
        self.assertEqual(len(strong), 2)  # extract · court 각각
        self.assertTrue(any("비식별화" in line and "free:gemini" in line for line in strong))
        self.assertTrue(any("재판부" in line and "allowFreeTierForJudgment" in line and "공개된 판결문" in line for line in strong))
        transfers = [line for line in self.logs if line.startswith("외부 전송")]
        self.assertTrue(any("비식별화" in line and "anthropic, gemini" in line for line in transfers))
        self.assertTrue(any("재판부" in line and "gemini, openai" in line for line in transfers))

    # ---- 무료 등급 모델 안내 (BE-45)

    def run_notices(self, config, **kwargs):
        def stop(*args, **kw):
            raise pipeline.PipelineError("테스트 중단")

        with self.assertRaises(pipeline.PipelineError):
            pipeline.run_pipeline(config, functions={stage: stop for stage in pipeline.STAGES}, log=self.logs.append,
                                  **kwargs)
        return [line for line in self.logs if line.startswith(("주의", "무료 등급", "⚠"))]

    def test_free_tier_notice_for_judgment_stages_is_strong(self):
        config = self.config(extract={"enabled": True, "model": ["claude-opus-5-5", "gemini:gemini-3.5-flash"]},
                             court={"enabled": True, "model": "gemini:gemini-3.1-pro-preview"})
        notices = self.run_notices(config, until="court")
        self.assertEqual(len(notices), 1)  # court 모델은 무료 등급이 없는 모델이라 안내가 없다
        self.assertIn("비식별화", notices[0])
        self.assertIn("gemini:gemini-3.5-flash", notices[0])
        self.assertIn("유료 키", notices[0])
        self.assertNotIn("claude-opus-5-5", notices[0])

    def test_free_tier_notice_for_free_token(self):
        config = self.free_config(max_models=2)
        notices = self.run_notices(config)
        self.assertEqual(len(notices), 1)
        self.assertIn("free:gemini", notices[0])

    def test_free_tier_notice_for_generate_models(self):
        config = self.config(extract={"enabled": False}, generate={"models": ["gemini:gemini-3.5-flash", "openai:gpt-x"]})
        notices = self.run_notices(config)
        self.assertEqual(len(notices), 1)
        self.assertIn("gemini:gemini-3.5-flash", notices[0])
        self.assertNotIn("openai:gpt-x", notices[0])
        self.assertIn("delay", notices[0])

    def test_no_free_tier_notice_without_free_models(self):
        config = self.config(extract={"enabled": True, "model": "claude-opus-5-5"},
                             generate={"models": ["openai:gpt-x", "gemini:gemini-3.1-pro-preview"]})
        self.assertEqual(self.run_notices(config), [])

    def test_extract_stage_logs_chain_and_records_fallbacks(self):
        config = self.config(extract={"enabled": True, "model": ["claude-opus-5-5", "gemini:g"]})
        report = report_for(self.case)
        report["fallbacks"] = [{"model": "claude-opus-5-5", "kind": "overloaded", "error": "서비스 과부하"}]
        report["requestedModel"], report["model"] = "gemini:g", "gemini-g-2026"
        write_json(self.dir / "r.json", report)
        fake_module = mock.MagicMock()
        fake_module.run.return_value = (self.raw_config["inputs"]["case"], self.raw_config["inputs"]["court"],
                                        str(self.dir / "r.json"), self.raw_config["inputs"]["source"])
        fake_module.ExtractError = RuntimeError
        with mock.patch.dict(sys.modules, {"extract_case": fake_module}):
            outputs = pipeline.stage_extract(config, {"stages": {}}, self.logs.append)
        self.assertEqual(fake_module.run.call_args.kwargs["model"], ["claude-opus-5-5", "gemini:g"])
        self.assertTrue(any("claude-opus-5-5 → gemini:g" in line for line in self.logs))
        self.assertEqual(outputs["fallbacks"], [{"model": "claude-opus-5-5", "kind": "overloaded"}])
        self.assertEqual(outputs["model"], "gemini-g-2026")  # 실제로 응답한 모델 (fallbacks가 없어도 항상 들어간다)

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

    def test_extract_stops_when_crime_type_differs_from_request(self):
        """BE-138: 등록 요청의 범죄 유형(crimeType)과 판결문에서 읽은 유형이 다르면 비싼 단계 전에 멈춘다. 요청 값으로 덮어쓰지
        않는다. 같거나 요청에 유형이 없으면(로컬 실행) 그대로 진행하고, 읽은 유형을 출력에 남긴다."""
        write_json(self.dir / "r.json", report_for(self.case))
        fake_module = mock.MagicMock()
        fake_module.run.return_value = (self.raw_config["inputs"]["case"], self.raw_config["inputs"]["court"],
                                        str(self.dir / "r.json"), self.raw_config["inputs"]["source"])
        fake_module.ExtractError = RuntimeError
        extracted = self.case["crimeType"]
        other = next(t for t in pipeline.CRIME_TYPES if t != extracted)
        with mock.patch.dict(sys.modules, {"extract_case": fake_module}):
            with self.assertRaises(pipeline.PipelineError) as ctx:
                pipeline.stage_extract(self.config({"crimeType": other}, extract={"enabled": True}), {"stages": {}},
                                       self.logs.append)
            self.assertIn(f"등록 요청은 {other}, 판결문에서 읽은 유형은 {extracted}", str(ctx.exception))
            self.assertEqual(ctx.exception.args[1]["crimeType"], extracted)  # 멈춰도 읽은 유형은 출력에 남는다
            for config in (self.config({"crimeType": extracted}, extract={"enabled": True}),
                           self.config(extract={"enabled": True})):
                self.assertEqual(pipeline.stage_extract(config, {"stages": {}}, self.logs.append)["crimeType"], extracted)
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.config({"crimeType": "ARSON"})
        self.assertIn("crimeType", str(ctx.exception))

    def test_resume_without_extract_checks_crime_type(self):
        """BE-138 (PR #356 리뷰): extract를 돌리지 않는 실행(--from · 끝난 extract 건너뜀)도 뒤 단계 전에 유형을 비교한다."""
        extracted = self.case["crimeType"]
        other = next(t for t in pipeline.CRIME_TYPES if t != extracted)
        config = self.config({"crimeType": other}, generate={"models": ["openai:a"], "runs": 1, "maxRetries": 0})
        with self.assertRaises(pipeline.PipelineError) as ctx:
            self.run_with(config, {"openai:a": [self.output]}, start="generate")
        self.assertIn(f"등록 요청은 {other}, 판결문에서 읽은 유형은 {extracted}", str(ctx.exception))
        self.assertEqual(pipeline.load_state(config)["stages"], {})  # 판결 생성 · 적재는 시작하지 않는다
        config = self.config({"crimeType": extracted}, generate={"models": ["openai:a"], "runs": 1, "maxRetries": 0})
        state = self.run_with(config, {"openai:a": [self.output]}, start="generate")
        self.assertEqual(state["stages"]["load"]["status"], "done")

    def test_crime_types_match_extractor_and_service(self):
        """범죄 유형 목록은 파이프라인(common) · 추출기 스키마 · 서비스가 따로 갖는다 — 늘릴 때 한 곳만 고치면 이 테스트가 깨진다."""
        sys.path.insert(0, str(pipeline.EXTRACTOR_DIR))
        sys.path.insert(0, str(TOOL_DIR / "service"))
        import jobs  # noqa: E402
        import schema  # noqa: E402
        self.assertEqual(pipeline.CRIME_TYPES, schema.CRIME_TYPES)
        self.assertEqual(pipeline.CRIME_TYPES, jobs.CRIME_TYPES)


if __name__ == "__main__":
    unittest.main()
