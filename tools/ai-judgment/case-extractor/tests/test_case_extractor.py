"""판결문 가공 스크립트 테스트. API는 호출하지 않는다.

실행: tools/ai-judgment/case-extractor에서 `python3 -m unittest discover tests`
"""

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

EXTRACTOR_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(EXTRACTOR_DIR))

from deidentify import premask, residual_check  # noqa: E402
from extract_case import ExtractError, parse_response_text, process_output, read_judgment, run  # noqa: E402

# 설명용 가상 판결문 (tools/ai-judgment/examples의 가상 살인 사건). 개인정보 값은 모두 지어낸 것이다.
FAKE_JUDGMENT = """
서울가상지방법원
판 결
사건 2099고합123 살인
피고인 A (990101-1234567), 주거 가상시 가상구 가상로 12, 101동 202호
검사 홍길동(기소), 변호인 변호사 김변호
판결선고 2099. 5. 1.

범죄사실
피고인은 2099. 1. 10. 20:00경 위 주거지에서 돈을 빌린 피해자 B(30세)와 다투던 중 집에 있던 흉기로 피해자를 찔러 살해하였다.
피고인의 연락처는 010-1234-5678이고, 압수된 흉기(2099압제45호)는 몰수한다.
""" + "양형의 이유 " * 30

FAKE_OUTPUT = {
    "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",
    "crimeType": "MURDER",
    "chargeName": "살인",
    "appliedLaw": "형법 제250조 제1항 살인",
    "statutoryPenaltyText": "사형, 무기 또는 5년 이상의 징역",
    "overview": "A씨가 빌린 돈 문제로 오래 다투던 지인 B씨를 집에서 흉기로 살해한 혐의로 재판에 넘겨졌다. "
                "A씨는 말다툼 끝에 범행했고, 수사 초기부터 범행을 인정했다.",
    "facts": "사건 3개월 전부터 변제 문제로 여러 차례 다퉜다. 사건 당일 B씨가 A씨의 집으로 찾아왔고, 말다툼 끝에 "
             "A씨가 집에 있던 흉기로 B씨를 공격했다.",
    "damage": [{"label": "피해자 수", "value": "1명"}, {"label": "피해 결과", "value": "사망"}],
    "defendant": "30대이고 형사처벌 전력이 없다. 수사 초기부터 범행을 인정하고 반성하고 있다.",
    "settlement": "피해 회복을 위해 5,000만 원을 공탁했으나 합의에 이르지 못했다.",
    "prosecutor": "흉기로 피해자를 공격했고 구호 조치 없이 현장을 떠났다.",
    "defense": "말다툼 중에 벌어진 우발적 범행이고, 전력이 없다.",
    "lawTerms": [{"term": "작량감경", "desc": "참작할 사정이 있을 때 판사가 형을 줄이는 것"}],
    "penaltyRules": [
        {"penaltyType": "DEATH", "statutoryMin": None, "statutoryMax": None, "allowedMin": 240, "allowedMax": 600,
         "suspensionAllowed": False, "allowedBasis": "작량감경 시 무기 또는 징역 20년 ~ 50년"},
        {"penaltyType": "LIFE", "statutoryMin": None, "statutoryMax": None, "allowedMin": 120, "allowedMax": 600,
         "suspensionAllowed": False, "allowedBasis": "작량감경 시 징역 10년 ~ 50년"},
        {"penaltyType": "PRISON", "statutoryMin": 60, "statutoryMax": 360, "allowedMin": 30, "allowedMax": 360,
         "suspensionAllowed": True, "allowedBasis": "작량감경 시 하한 1/2"},
    ],
    "recommended": {"minMonths": 84, "maxMonths": 144, "basis": "제2유형 감경영역"},
    "sentencingGuideline": "살인범죄 양형기준 제2유형(보통 동기 살인)",
    "factors": [
        {"label": "빌린 돈을 갚지 못해 오래 다툼이 있었다", "preLabel": "돈 문제로 오래 다퉜다",
         "revealStage": "OVERVIEW", "summaryTag": "범행 동기"},
        {"label": "형사처벌 전력이 없다", "preLabel": None, "revealStage": "DETAIL", "summaryTag": "전력"},
        {"label": "피고인은 우발적 범행이라고 주장한다", "preLabel": None, "revealStage": "ARGUMENT",
         "summaryTag": "범행 동기"},
    ],
    "courtJudgment": {"penaltyType": "PRISON", "reducedTo": None, "prisonMonths": 120, "fineAmount": None,
                      "suspensionMonths": None},
    "deidentifiedItems": ["인명", "지명", "사건번호", "법원명", "날짜", "나이"],
    "reviewNotes": ["공탁 금액을 원 판결문과 대조할 것"],
}


def output_with(**changes):
    output = copy.deepcopy(FAKE_OUTPUT)
    output.update(changes)
    return output


def fake_call(output):
    def call(system, user, model, effort):
        fake_call.last_user = user
        return copy.deepcopy(output), model
    return call


class PremaskTest(unittest.TestCase):
    def test_premask_identifiers_areReplaced(self):
        masked, counts = premask(FAKE_JUDGMENT)

        for raw in ("990101-1234567", "010-1234-5678", "2099고합123", "서울가상지방법원", "2099압제45호",
                    "가상로 12", "101동 202호"):
            self.assertNotIn(raw, masked)
        for item in ("주민등록번호", "전화번호", "사건번호", "법원명", "압수번호", "주소"):
            self.assertIn(item, counts)

    def test_premask_sentencingCommittee_isKept(self):
        masked, _ = premask("대법원 양형위원회의 살인범죄 양형기준")

        self.assertIn("대법원 양형위원회", masked)

    def test_premask_hyphenDate_isNotAccount(self):
        masked, counts = premask("2099-01-10 범행, 계좌 110-123-456789, 12345-1234-12345678901")

        self.assertIn("2099-01-10", masked)
        self.assertNotIn("110-123-456789", masked)
        self.assertEqual(counts.get("계좌번호"), 1)
        self.assertIn("12345-1234-12345678901", masked)  # 더 긴 숫자열의 일부는 잡지 않는다

    def test_premask_judgmentDates_areKept(self):
        # 날짜는 모델이 "사건 3개월 전"처럼 바꾸는 데 필요해 미리 가리지 않는다
        masked, _ = premask("2099. 1. 10. 20:00경")

        self.assertIn("2099. 1. 10.", masked)


class ResidualCheckTest(unittest.TestCase):
    def test_residualCheck_dateAgeCourt_areErrors(self):
        errors, _ = residual_check([("overview", "2099. 1. 10. 가상지방법원에서 30세 피해자")])

        self.assertTrue(any("정확한 날짜" in e for e in errors))
        self.assertTrue(any("정확한 나이" in e for e in errors))
        self.assertTrue(any("법원명" in e for e in errors))

    def test_residualCheck_realNameAfterRole_isWarning(self):
        _, warnings = residual_check([("facts", "피고인 김철수는 범행을 인정했다. 피고인 측은 선처를 구했다.")])

        self.assertEqual(len(warnings), 1)
        self.assertIn("실명일 수 있음", warnings[0])
        self.assertNotIn("김철수", warnings[0])  # 보고서에 원본 이름을 남기지 않는다

    def test_residualCheck_anonymizedText_isClean(self):
        errors, warnings = residual_check([("overview", FAKE_OUTPUT["overview"]), ("facts", FAKE_OUTPUT["facts"])])

        self.assertEqual(errors, [])
        self.assertEqual(warnings, [])


class ProcessOutputTest(unittest.TestCase):
    def test_processOutput_validOutput_buildsCaseInput(self):
        case_input, court, errors, _ = process_output(copy.deepcopy(FAKE_OUTPUT))

        self.assertEqual(errors, [])
        self.assertEqual([s["sectionType"] for s in case_input["sections"]],
                         ["FACTS", "DAMAGE", "DEFENDANT", "SETTLEMENT", "PROSECUTOR", "DEFENSE", "LAW_TERM"])
        self.assertEqual([f["factorId"] for f in case_input["factors"]], [1, 2, 3])
        self.assertNotIn("courtJudgment", case_input)
        self.assertNotIn("allowedBasis", case_input["penaltyRules"][0])
        self.assertEqual(court["prisonMonths"], 120)

    def test_processOutput_sentenceInOverview_isError(self):
        output = output_with(overview=FAKE_OUTPUT["overview"] + " 재판부는 징역 10년을 선고했다.")

        _, _, errors, _ = process_output(output)

        self.assertTrue(any("실제 선고 형량" in e for e in errors))

    def test_processOutput_sentenceVariants_areErrors(self):
        court = {"penaltyType": "PRISON", "reducedTo": None, "prisonMonths": 120, "fineAmount": 20_000_000,
                 "suspensionMonths": 24}
        for text in ("재판부는 징역10년을 선고했다.", "10년의 징역에 처했다.", "징역 120개월이 나왔다.",
                     "120개월의 징역이다.", "벌금 2천만 원이 나왔다.", "2천만원의 벌금이다.",
                     "집행유예 2년이다.", "2년간 집행유예가 붙었다.", "징역형의 집행을 유예했다."):
            with self.subTest(text=text):
                _, _, errors, _ = process_output(output_with(
                    overview=FAKE_OUTPUT["overview"] + " " + text, courtJudgment=court))

                self.assertTrue(any("실제 선고 형량" in e for e in errors))

    def test_processOutput_unrelatedNumbers_areNotSentenceLeak(self):
        # 법정형 범위 · 범행 기간 · 전과처럼 형량 용어와 붙지 않은 숫자는 보존한다
        for text in ("법정형은 징역 10년 이상이다.", "두 사람은 10년 동안 알고 지냈다.", "징역 110년은 없다.",
                     "과거 징역 3년형을 산 적이 있다."):
            with self.subTest(text=text):
                _, _, errors, _ = process_output(output_with(overview=FAKE_OUTPUT["overview"] + " " + text))

                self.assertFalse(any("실제 선고 형량" in e for e in errors))

    def test_processOutput_noOverviewFactor_isError(self):
        factors = [f for f in FAKE_OUTPUT["factors"] if f["revealStage"] != "OVERVIEW"]

        _, _, errors, _ = process_output(output_with(factors=factors))

        self.assertTrue(any("OVERVIEW" in e for e in errors))

    def test_processOutput_deathWithStatutoryRange_isError(self):
        rules = copy.deepcopy(FAKE_OUTPUT["penaltyRules"])
        rules[0]["statutoryMax"] = 600

        _, _, errors, _ = process_output(output_with(penaltyRules=rules))

        self.assertTrue(any("DEATH" in e for e in errors))

    def test_processOutput_maskTokenLeft_isError(self):
        _, _, errors, _ = process_output(output_with(facts="[주소]에서 다퉜다."))

        self.assertTrue(any("마스킹 표시" in e for e in errors))


class ParseResponseTest(unittest.TestCase):
    def test_parseResponseText_afterFallback_usesLastModelText(self):
        content = [
            SimpleNamespace(type="text", text='{"broken": '),
            SimpleNamespace(type="fallback"),
            SimpleNamespace(type="text", text='{"ok": true}'),
        ]

        self.assertEqual(parse_response_text(content), {"ok": True})

    def test_parseResponseText_empty_raises(self):
        with self.assertRaises(ExtractError):
            parse_response_text([SimpleNamespace(type="thinking")])


class RunTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.input = self.dir / "judgment.txt"
        self.input.write_text(FAKE_JUDGMENT, encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def test_run_validOutput_writesThreeFiles(self):
        paths = run(self.input, "sample-case", out_dir=self.dir / "out", call=fake_call(FAKE_OUTPUT))

        self.assertEqual([p.name for p in paths],
                         ["sample-case.case.json", "sample-case.court_judgment_internal.json", "sample-case.report.json"])
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertEqual(report["status"], "NEEDS_REVIEW")
        self.assertIn("사건번호", report["premasked"])
        # 모델에 보낸 텍스트에는 미리 가린 값이 없다
        self.assertNotIn("990101-1234567", fake_call.last_user)

    def test_run_checkErrors_writesReportOnly(self):
        output = output_with(overview="2099. 1. 10. 가상지방법원 사건이다.")

        with self.assertRaises(ExtractError):
            run(self.input, "bad-case", out_dir=self.dir / "out", call=fake_call(output))

        self.assertTrue((self.dir / "out" / "bad-case.report.json").exists())
        self.assertFalse((self.dir / "out" / "bad-case.case.json").exists())

    def test_run_checkErrors_removesStaleOutputs(self):
        out = self.dir / "out"
        run(self.input, "same-name", out_dir=out, call=fake_call(FAKE_OUTPUT))
        self.assertTrue((out / "same-name.case.json").exists())

        with self.assertRaises(ExtractError):
            run(self.input, "same-name", out_dir=out,
                call=fake_call(output_with(overview="2099. 1. 10. 가상지방법원 사건이다.")))

        self.assertTrue((out / "same-name.report.json").exists())
        self.assertFalse((out / "same-name.case.json").exists())
        self.assertFalse((out / "same-name.court_judgment_internal.json").exists())

    def test_run_identifiersInReportFields_areErroredAndScrubbed(self):
        output = output_with(reviewNotes=["원문에는 010-1234-5678과 2099고합123이 있었다"])
        output["penaltyRules"][0]["allowedBasis"] = "가상지방법원 2099. 1. 10. 기준"

        with self.assertRaises(ExtractError):
            run(self.input, "leaky-case", out_dir=self.dir / "out", call=fake_call(output))

        raw = (self.dir / "out" / "leaky-case.report.json").read_text(encoding="utf-8")
        report = json.loads(raw)
        for value in ("010-1234-5678", "2099고합123", "가상지방법원", "2099. 1. 10."):
            self.assertNotIn(value, raw)
        self.assertTrue(any("reviewNotes[0]" in e for e in report["errors"]))
        self.assertTrue(any("penaltyRuleBasis[" in e for e in report["errors"]))

    def test_run_dryRun_writesMaskedRequestWithoutCalling(self):
        def must_not_call(*args):
            raise AssertionError("dry-run에서 API를 호출했다")

        [path] = run(self.input, "sample-case", out_dir=self.dir / "out", dry_run=True, call=must_not_call)

        request = path.read_text(encoding="utf-8")
        self.assertIn("[주민등록번호]", request)
        self.assertNotIn("010-1234-5678", request)

    def test_run_identifyingName_isRejected(self):
        with self.assertRaises(ExtractError):
            run(self.input, "2099고합123", out_dir=self.dir / "out", call=fake_call(FAKE_OUTPUT))

    def test_readJudgment_cp949Txt_isDecoded(self):
        path = self.dir / "cp949.txt"
        path.write_bytes(FAKE_JUDGMENT.encode("cp949"))

        self.assertIn("범죄사실", read_judgment(path))

    def test_readJudgment_otherExtension_raises(self):
        path = self.dir / "judgment.docx"
        path.write_text("x", encoding="utf-8")

        with self.assertRaises(ExtractError):
            read_judgment(path)


if __name__ == "__main__":
    unittest.main()
