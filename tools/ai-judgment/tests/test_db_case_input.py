"""로컬 DB 사건 → 사건 입력 (BE-76, db_case_input.py) 테스트. DB 대신 가짜 실행기를 쓴다."""

import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import db_case_input  # noqa: E402
import pipeline  # noqa: E402
from build_prompt import build_prompt  # noqa: E402
from common import find_forbidden_keys  # noqa: E402

ROW = {
    "caseId": 7, "title": "가상 사건", "crimeType": "MURDER", "chargeName": "살인", "appliedLaw": "형법 제250조 제1항",
    "statutoryPenaltyText": "사형, 무기 또는 5년 이상의 징역", "overview": "가상 개요", "incidentDate": "2014-03-01",
    "recommended": {"minMonths": 84, "maxMonths": 144, "basis": "가상 근거"},
    "sections": [{"stage": "DETAIL", "sectionType": "FACTS", "title": "사실관계", "content": "가상 사실", "data": None},
                 {"stage": "DETAIL", "sectionType": "DAMAGE", "title": "피해", "content": None,
                  "data": [{"label": "피해자 수", "value": "1명"}]}],
    "penaltyRules": [{"penaltyType": "LIFE", "statutoryMin": None, "statutoryMax": None, "allowedMin": 120,
                      "allowedMax": 600, "suspensionAllowed": False},
                     {"penaltyType": "PRISON", "statutoryMin": 60, "statutoryMax": 360, "allowedMin": 30,
                      "allowedMax": 360, "suspensionAllowed": True}],
    "factors": [{"factorId": 1, "label": "가상 요소", "revealStage": "OVERVIEW"}],
    "sources": [{"caseNumber": "2099고합1", "courtName": "가상법원", "decidedAt": "2015-01-01",
                 "courtLevel": "FIRST", "isFinal": True}],
}


class DbCaseInputTest(unittest.TestCase):
    def test_build_makes_generation_input_without_secrets(self):
        case, source = db_case_input.build(ROW)
        self.assertEqual(case["references"], {})
        self.assertEqual(case["sections"][0], {"stage": "DETAIL", "sectionType": "FACTS", "title": "사실관계", "content": "가상 사실"})
        self.assertNotIn("content", case["sections"][1])
        self.assertEqual(find_forbidden_keys(case), [])
        self.assertNotIn("2099고합1", json.dumps(case, ensure_ascii=False))  # 사건번호는 원본 정보 파일에만
        self.assertNotIn("incidentDate", case)
        self.assertEqual(source["sources"][0]["caseNumber"], "2099고합1")
        self.assertEqual(source["incidentDate"], "2014-03-01")
        prompt = build_prompt(case)
        self.assertIn("가상 요소", prompt["user"])

    def test_build_rejects_unusable_rows(self):
        with self.assertRaises(db_case_input.CaseInputError):
            db_case_input.build(None)
        broken = {**ROW, "penaltyRules": [{**ROW["penaltyRules"][1], "allowedMin": None}]}
        with self.assertRaises(db_case_input.CaseInputError):
            db_case_input.build(broken)
        no_recommended, _ = db_case_input.build({**ROW, "recommended": None})
        self.assertNotIn("recommended", no_recommended)

    def test_sql_excludes_screen_only_sections_and_original_text(self):
        sql = db_case_input.case_sql(7)
        self.assertIn("NOT IN ('LAW_TERM', 'SUMMARY')", sql)
        self.assertIn("WHERE c.id = 7", sql)
        self.assertNotIn("original_text", sql)

    def test_main_writes_case_source_and_pipeline_configs(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(pipeline, "CASES_DIR", Path(tmp)), \
                mock.patch.object(db_case_input.rag_check, "json_row", return_value=ROW), \
                mock.patch.object(pipeline, "TOOL_DIR", Path(tmp)), redirect_stdout(io.StringIO()):
            code = db_case_input.main(["7", "--name", "test-case", "--pipeline", "--model", "gemini:x", "--runs", "2"])
            self.assertEqual(code, 0)
            files = sorted(p.name for p in Path(tmp).iterdir())
            self.assertEqual(files, ["test-case-rag-off.pipeline.json", "test-case-rag-on.pipeline.json",
                                     "test-case.case.json", "test-case.source_internal.json"])
            on = json.loads((Path(tmp) / "test-case-rag-on.pipeline.json").read_text(encoding="utf-8"))
            off = json.loads((Path(tmp) / "test-case-rag-off.pipeline.json").read_text(encoding="utf-8"))
            self.assertTrue(on["stages"]["rag"]["enabled"])
            self.assertFalse(off["stages"]["rag"]["enabled"])
            self.assertEqual(on["inputs"], {"case": "test-case.case.json", "source": "test-case.source_internal.json"})
            self.assertEqual(on["stages"]["generate"], {"models": ["gemini:x"], "runs": 2, "maxRetries": 0, "timeout": 600})
            for name in ("test-case-rag-on.pipeline.json", "test-case-rag-off.pipeline.json"):
                config = pipeline.load_config(Path(tmp) / name)  # 파이프라인 설정 검사를 통과한다
                self.assertFalse(config["stages"]["extract"]["enabled"])

    def test_main_rejects_bad_options(self):
        for argv in (["7", "--name", "Bad_Name"], ["7", "--name", "ok", "--pipeline"], ["7", "--name", "ok", "--runs", "0"]):
            err = io.StringIO()
            with redirect_stderr(err), mock.patch.object(db_case_input.rag_check, "json_row", return_value=ROW):
                self.assertEqual(db_case_input.main(argv), 1, argv)
            self.assertIn("[오류]", err.getvalue())


if __name__ == "__main__":
    unittest.main()
