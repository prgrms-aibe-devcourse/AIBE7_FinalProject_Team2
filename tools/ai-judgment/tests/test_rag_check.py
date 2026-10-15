"""RAG 확인 도구 (BE-54, rag_check.py) 테스트. 네트워크 · DB 대신 가짜 임베딩 · 가짜 DB를 쓴다."""

import io
import json
import sys
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import rag_check  # noqa: E402

VECTOR = [0.001] * 3072
RESULT = {"guideline": {"id": 7, "versionName": "2차 수정 2013", "effectiveDate": "2013-05-15"},
          "ranges": [{"id": 1, "subject": "제2유형", "typeNo": 2, "content": "기본 10 ~ 16년"}],
          "factors": [{"id": 9, "section": "양형인자", "subject": "행위", "content": "계획적", "score": 0.66}],
          "precedents": [{"id": 31, "caseGroup": "g31", "score": 0.97, "guidelineId": 7, "chargeName": "살인",
                          "courtLevel": "FIRST", "penaltyType": "PRISON", "prisonMonths": 144, "suspensionMonths": None,
                          "fineAmount": None, "sentencingFactors": None, "factsSummary": "사실", "sentencingReason": "이유"},
                         {"id": 32, "caseGroup": "g32", "score": 0.81, "guidelineId": None, "chargeName": "살인",
                          "courtLevel": "APPEAL", "penaltyType": "LIFE", "prisonMonths": None, "suspensionMonths": None,
                          "fineAmount": None, "sentencingFactors": None, "factsSummary": "사실2", "sentencingReason": "이유2"}]}


def fake_embed(texts, **kwargs):
    assert kwargs["task_type"] == "RETRIEVAL_QUERY"
    return [VECTOR for _ in texts]


class RagCheckTest(unittest.TestCase):
    def run_main(self, argv, db):
        out = io.StringIO()
        with mock.patch.object(rag_check, "embed", fake_embed), \
                mock.patch.object(rag_check.pipeline, "query_db", side_effect=lambda sql, cfg: db(sql)), redirect_stdout(out):
            code = rag_check.main(argv)
        return code, out.getvalue()

    def test_case_prints_scores_and_prompt_without_case_numbers(self):
        examples = str(rag_check.pipeline.TOOL_DIR / "examples" / "case_input.json")
        code, out = self.run_main(["case", examples, "--incident-date", "2014-03-01", "--prompt"],
                                  lambda sql: json.dumps(RESULT, ensure_ascii=False))
        self.assertEqual(code, 0)
        self.assertIn("양형기준 버전: 2차 수정 2013", out)
        self.assertIn("0.9700", out)
        self.assertIn("상한 이상(자기 사건 의심)", out)
        self.assertIn("=== 프롬프트 '유사 판례' ===", out)
        self.assertIn("사실2", out)  # 상한 이상 판례는 프롬프트에서 빠진다
        self.assertNotIn("### 유사 판례 2", out)

    def test_db_case_uses_case_source_hashes(self):
        sqls = []
        row = {"title": "가상 사건", "crimeType": "MURDER", "overview": "개요", "incidentDate": "2014-03-01",
               "sections": [], "sources": [{"caseNumber": "2099고합1"}]}

        def db(sql):
            sqls.append(sql)
            return json.dumps(row if len(sqls) == 1 else RESULT, ensure_ascii=False)

        code, out = self.run_main(["db-case", "5"], db)
        self.assertEqual(code, 0)
        self.assertIn("FROM legal_case c WHERE c.id = 5", sqls[0])
        self.assertNotIn("2099고합1", out + "".join(sqls[1:]))
        self.assertIn(rag_check.rag_context.own_case_hashes(row["sources"])[0], sqls[1])

    def test_sample_excludes_own_group(self):
        sqls = []

        def db(sql):
            sqls.append(sql)
            if len(sqls) == 1:
                return json.dumps([{"id": 1, "case_group": "G1", "facts_summary": "요약"}], ensure_ascii=False)
            return json.dumps([0.88, 0.8, 0.75])

        code, out = self.run_main(["sample", "--count", "1"], db)
        self.assertEqual(code, 0)
        self.assertIn("ARRAY['G1']::text[]", sqls[1])
        self.assertIn("1위 점수: 최소 0.8800", out)
        self.assertIn("5위 점수: 최소 0.7500", out)

    def test_db_failure_is_error(self):
        def db(sql):
            raise rag_check.rag_context.RagUnavailable("connection refused")

        err = io.StringIO()
        with mock.patch("sys.stderr", err):
            code, _ = self.run_main(["db-case", "1"], db)
        self.assertEqual(code, 1)
        self.assertIn("connection refused", err.getvalue())


if __name__ == "__main__":
    unittest.main()
