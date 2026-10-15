"""생성 참고 자료 RAG (BE-54, rag_context.py) 테스트. 네트워크 · DB를 쓰지 않는다 (가짜 임베딩 · 가짜 DB 실행기).

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import json
import sys
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import rag_context  # noqa: E402
from build_prompt import build_prompt  # noqa: E402
from common import find_forbidden_keys, load_json  # noqa: E402
from llm import LLMError  # noqa: E402
from rag_context import (RagContextError, RagUnavailable, base_date, build_query_sql, build_references,  # noqa: E402
                         option_errors, options_with, own_case_hashes, query_text)
from rag_store import case_number_hash  # noqa: E402

EXAMPLES = TOOL_DIR / "examples"
VECTOR = [0.001] * 3072
SOURCES = [{"caseNumber": "2099고합1", "courtLevel": "FIRST"}, {"caseNumber": "2099노2, 2099노3(병합)", "courtLevel": "APPEAL"}]


def precedent(pid, score, guideline_id=None, **changes):
    item = {"id": pid, "caseGroup": f"g{pid}", "score": score, "guidelineId": guideline_id, "chargeName": "살인",
            "courtLevel": "FIRST", "penaltyType": "PRISON", "prisonMonths": 150, "suspensionMonths": None,
            "fineAmount": None, "sentencingFactors": {"aggravating": ["계획적 범행"], "mitigating": []},
            "factsSummary": f"판례 {pid}의 비식별 사실관계", "sentencingReason": f"판례 {pid}의 양형 이유"}
    item.update(changes)
    return item


def db_result(guideline=True, precedents=None):
    return {
        "guideline": {"id": 7, "versionName": "2차 수정 2013", "effectiveDate": "2013-05-15"} if guideline else None,
        "ranges": [{"id": 1, "subject": "제2유형 보통 동기 살인", "typeNo": 2, "content": "감경 7 ~ 12년, 기본 10 ~ 16년"}]
        if guideline else [],
        "factors": [{"id": 9, "section": "양형인자", "subject": "특별양형인자 > 행위 > 공통", "content": "계획적 살인 범행",
                     "score": 0.66}] if guideline else [],
        "precedents": precedents if precedents is not None else [],
    }


class FakeDb:
    def __init__(self, result=None, error=None):
        self.result, self.error, self.sql = result, error, []

    def __call__(self, sql):
        self.sql.append(sql)
        if self.error:
            raise self.error
        return "\n" + json.dumps(self.result, ensure_ascii=False) + "\n"


def fake_embedder(texts, task_type):
    assert task_type == "RETRIEVAL_QUERY"
    return [VECTOR for _ in texts]


class RagContextTest(unittest.TestCase):
    def setUp(self):
        self.case = load_json(EXAMPLES / "case_input.json")
        self.logs = []

    def build(self, db, **kwargs):
        kwargs.setdefault("incident_date", "2014-03-01")
        return build_references(self.case, SOURCES, embedder=kwargs.pop("embedder", fake_embedder), runner=db,
                                log=self.logs.append, **kwargs)

    def test_base_date_prefers_indictment(self):
        self.assertEqual(base_date("2014-04-01", "2014-03-01"), ("2014-04-01", "INDICTMENT_DATE"))
        self.assertEqual(base_date(None, "2014-03-01"), ("2014-03-01", "INCIDENT_DATE"))
        self.assertEqual(base_date(None, None), (None, None))

    def test_own_case_hashes_per_number(self):
        hashes = own_case_hashes(SOURCES)
        self.assertEqual(len(hashes), 3)  # 1심 1개 + 항소심 병합 2개
        self.assertIn(case_number_hash("2099노3"), hashes)
        self.assertEqual(own_case_hashes([{"caseNumber": None}]), [])

    def test_query_sql_uses_hashes_not_case_numbers(self):
        db = FakeDb(db_result(precedents=[precedent(1, 0.8)]))
        self.build(db)
        sql = db.sql[0]
        self.assertIn(case_number_hash("2099고합1"), sql)
        self.assertNotIn("2099고합1", sql)
        self.assertIn("sentencing_guideline_at('살인범죄', '2014-03-01'::date)", sql)
        self.assertIn("rag_precedent_search(", sql)
        self.assertIn("'MURDER'", sql)
        self.assertIn("::vector(3072)", sql)
        # 판례 버전은 검색할 때 판례 발생일로 정한다 (시드 순서상 rag_precedent.guideline_id가 비어 있을 수 있다)
        self.assertIn("coalesce(p.guideline_id, CASE WHEN p.incident_date IS NULL THEN NULL "
                      "ELSE sentencing_guideline_at('살인범죄', p.incident_date) END)", sql)

    def test_query_sql_without_date_skips_guideline(self):
        sql = build_query_sql(VECTOR, "MURDER", "살인범죄", None, [], options_with(None))
        self.assertIn("SELECT NULL::bigint AS id", sql)
        self.assertIn("ARRAY[]::text[]", sql)
        sql = build_query_sql(VECTOR, "FRAUD", None, "2014-03-01", [], options_with(None))  # 양형기준 분류가 없는 유형
        self.assertIn("'guidelineId', p.guideline_id,", sql)

    def test_query_text_is_facts_only(self):
        text = query_text(self.case)
        self.assertTrue(text.startswith(self.case["overview"].strip()[:20]))
        self.assertNotIn(self.case["statutoryPenaltyText"], text)
        long_case = {**self.case, "overview": "가" * 10000}
        self.assertEqual(len(query_text(long_case)), rag_context.QUERY_MAX_CHARS)

    def test_builds_guideline_and_precedents(self):
        db = FakeDb(db_result(precedents=[precedent(1, 0.97), precedent(2, 0.82), precedent(3, 0.75, guideline_id=7),
                                          precedent(4, 0.7, penaltyType="LIFE", prisonMonths=None)]))
        references, report = self.build(db)
        self.assertEqual(report["status"], "OK")
        # 상한(0.88) 이상은 자기 사건 의심으로 빼고, 점수순으로 둔다. 같은 버전(7)은 작은 가산만 받아 더 비슷한 판례를 밀어내지 않는다
        self.assertEqual([p["id"] for p in report["precedents"]], [2, 3, 4])
        self.assertEqual(report["excludedSuspects"], 1)
        self.assertTrue(any("자기 사건 의심" in w for w in report["warnings"]))
        self.assertEqual(report["guideline"], "2차 수정 2013")
        self.assertEqual(report["guidelineBasis"], "INCIDENT_DATE")
        cases = references["similarCases"]
        self.assertEqual(cases[0]["title"], "유사 판례 1 (살인, 1심)")
        self.assertEqual(cases[0]["sentence"], "징역 12년 6개월")
        self.assertTrue(cases[1]["sameGuideline"])
        self.assertEqual(cases[2]["sentence"], "무기징역")
        self.assertIn("[형량범위]", references["sentencingGuideline"])
        self.assertIn("계획적 살인 범행", references["sentencingGuideline"])
        self.assertNotIn("2014-03-01", references["sentencingGuideline"])  # 기준일은 프롬프트에 넣지 않는다

    def test_same_guideline_bonus_breaks_close_scores_only(self):
        _, report = self.build(FakeDb(db_result(precedents=[precedent(1, 0.800), precedent(2, 0.795, guideline_id=7),
                                                            precedent(3, 0.760, guideline_id=7)])))
        self.assertEqual([p["id"] for p in report["precedents"]], [2, 1, 3])  # 0.005 차이는 같은 버전이 앞, 0.04 차이는 점수대로

    def test_chunk_line_drops_repeated_subject(self):
        self.assertEqual(rag_context.chunk_line("제2유형", "제2유형 권고 형량범위 — 기본 10 ~ 16년"), "제2유형: 권고 형량범위 — 기본 10 ~ 16년")
        self.assertEqual(rag_context.chunk_line("2. 피해자 유발", "2. 피해자 유발\n  - 귀책사유\n  - 과잉방위 제외"),
                         "2. 피해자 유발\n  - 귀책사유\n  - 과잉방위 제외")
        self.assertEqual(rag_context.chunk_line(None, "본문"), "본문")

    def test_references_pass_prompt_checks(self):
        references, report = self.build(FakeDb(db_result(precedents=[precedent(1, 0.8)])))
        case = {**self.case, "references": references}
        self.assertEqual(find_forbidden_keys(case), [])
        prompt = build_prompt(case)
        self.assertIn("판례 1의 비식별 사실관계", prompt["user"])
        self.assertIn("2차 수정 2013", prompt["user"])
        dumped = json.dumps([references, report], ensure_ascii=False)
        for secret in ("2099고합1", "2099노2", case_number_hash("2099고합1")):
            self.assertNotIn(secret, dumped)  # 사건번호 · 그 해시는 결과 · 보고에 남기지 않는다

    def test_no_guideline_keeps_case_value(self):
        references, report = self.build(FakeDb(db_result(guideline=False, precedents=[precedent(1, 0.8)])),
                                        incident_date=None)
        self.assertEqual(report["status"], "PARTIAL")
        self.assertEqual(references["sentencingGuideline"], self.case["references"]["sentencingGuideline"])
        self.assertTrue(any("버전을 고르지 못합니다" in w for w in report["warnings"]))
        self.assertEqual(len(references["similarCases"]), 1)

    def test_no_case_number_skips_precedents(self):
        # 사건번호가 없으면 자기 사건을 뺄 수 없어 상한만 남는다. 유사 판례를 붙이지 않는다 (#143 리뷰)
        db = FakeDb(db_result(precedents=[precedent(1, 0.8), precedent(2, 0.75)]))
        references, report = build_references(self.case, [], incident_date="2014-03-01", embedder=fake_embedder,
                                              runner=db, log=self.logs.append)
        self.assertEqual(report["status"], "PARTIAL")
        self.assertEqual(references["similarCases"], [])
        self.assertEqual(report["precedents"], [])
        self.assertTrue(any("사건번호가 없어" in r for r in report["fallbackReasons"]))
        self.assertEqual(report["guideline"], "2차 수정 2013")  # 양형기준은 그대로 쓴다
        with self.assertRaises(RagContextError):
            build_references(self.case, [], incident_date="2014-03-01", embedder=fake_embedder, runner=db,
                             options={"required": True}, log=self.logs.append)

    def test_empty_ranges_is_missing_guideline(self):
        # 버전 행만 있고 형량범위 조각이 없으면 제목뿐인 양형기준을 쓰지 않는다 (#143 리뷰)
        result = db_result(precedents=[precedent(1, 0.8)])
        result["ranges"] = []
        references, report = self.build(FakeDb(result))
        self.assertEqual(report["status"], "PARTIAL")
        self.assertIsNone(report["guideline"])
        self.assertEqual(references["sentencingGuideline"], self.case["references"]["sentencingGuideline"])
        self.assertTrue(any("형량범위 조각이 없습니다" in w for w in report["warnings"]))
        with self.assertRaises(RagContextError):
            self.build(FakeDb(result), options={"required": True})

    def test_input_key_changes_with_inputs(self):
        key = rag_context.input_key(self.case, SOURCES, incident_date="2014-03-01")
        self.assertEqual(key, rag_context.input_key(self.case, SOURCES, incident_date="2014-03-01"))
        self.assertNotEqual(key, rag_context.input_key(self.case, SOURCES, indictment_date="2014-04-01", incident_date="2014-03-01"))
        self.assertNotEqual(key, rag_context.input_key(self.case, SOURCES[:1], incident_date="2014-03-01"))
        self.assertNotEqual(key, rag_context.input_key({**self.case, "overview": "다른 개요"}, SOURCES, incident_date="2014-03-01"))
        self.assertNotEqual(key, rag_context.input_key(self.case, SOURCES, incident_date="2014-03-01",
                                                       options={"precedent": {"minScore": 0.72}}))
        self.assertNotIn("2099", key)

    def test_required_stops_on_partial(self):
        # required면 한쪽만 못 찾아도(PARTIAL) 멈춘다 (#143 리뷰)
        with self.assertRaises(RagContextError):
            self.build(FakeDb(db_result(precedents=[])), options={"required": True})
        with self.assertRaises(RagContextError):
            self.build(FakeDb(db_result(guideline=False, precedents=[precedent(1, 0.8)])), incident_date=None,
                       options={"required": True})
        _, report = self.build(FakeDb(db_result(precedents=[precedent(1, 0.8)])), options={"required": True})
        self.assertEqual(report["status"], "OK")

    def test_no_precedents_clears_examples(self):
        references, report = self.build(FakeDb(db_result(precedents=[])))
        self.assertEqual(report["status"], "PARTIAL")
        self.assertEqual(references["similarCases"], [])  # 예시 판례(사건 입력 값)를 남기지 않는다

    def test_nothing_found_is_fallback(self):
        _, report = self.build(FakeDb(db_result(guideline=False, precedents=[])))
        self.assertEqual(report["status"], "FALLBACK")
        with self.assertRaises(RagContextError):
            self.build(FakeDb(db_result(guideline=False, precedents=[])), options={"required": True})

    def test_embedding_or_db_failure_falls_back(self):
        def no_key(texts, task_type):
            raise LLMError("gemini API 키가 없습니다")

        references, report = self.build(FakeDb(db_result()), embedder=no_key)
        self.assertEqual(report["status"], "FALLBACK")
        self.assertEqual(references, self.case["references"])
        self.assertIn("임베딩 실패", report["fallbackReasons"][0])

        references, report = self.build(FakeDb(error=RagUnavailable("connection refused")))
        self.assertEqual(report["status"], "FALLBACK")
        self.assertEqual(references, self.case["references"])
        self.assertTrue(any("RAG 대체" in line for line in self.logs))

        with self.assertRaises(RagContextError):
            self.build(FakeDb(error=RagUnavailable("connection refused")), options={"required": True})

    def test_bad_embedding_dimension_falls_back(self):
        _, report = self.build(FakeDb(db_result()), embedder=lambda texts, task_type: [[0.1] * 10])
        self.assertEqual(report["status"], "FALLBACK")

    def test_multiline_json_from_psql(self):
        # 실제 psql 출력: json_agg가 원소 사이에 줄바꿈을 넣는다 (rag_check 실행에서 발견)
        text = json.dumps(db_result(precedents=[precedent(1, 0.8), precedent(2, 0.7)]), ensure_ascii=False)
        text = text.replace('}, {', '}, \n {')
        self.assertIn("\n", text)
        _, report = self.build(lambda sql: text + "\n")
        self.assertEqual([p["id"] for p in report["precedents"]], [1, 2])

    def test_unreadable_db_output_falls_back(self):
        _, report = self.build(lambda sql: "ERROR")
        self.assertEqual(report["status"], "FALLBACK")

    def test_option_errors(self):
        self.assertEqual(option_errors(options_with(None)), [])
        errors = option_errors(options_with({"required": "yes", "precedent": {"topK": 0, "minScore": 0.99}}))
        self.assertTrue(any("required" in e for e in errors))
        self.assertTrue(any("precedent.topK" in e for e in errors))
        errors = option_errors(options_with({"precedent": {"topK": 20}}))
        self.assertTrue(any("candidates" in e for e in errors))
        errors = option_errors(options_with({"precedent": {"minScore": 0.96}}))
        self.assertTrue(any("maxScore" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
