"""판결문 가공 스크립트 테스트. API는 호출하지 않는다.

실행: tools/ai-judgment/case-extractor에서 `python3 -m unittest discover tests`
"""

import copy
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

EXTRACTOR_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(EXTRACTOR_DIR))

from deidentify import date_in_text, extract_source_info, premask, residual_check  # noqa: E402
from extract_case import (  # noqa: E402
    ExtractError, ModelUnavailableError, call_llm, model_chain, parse_response_text, process_output, read_judgment, run,
    run_with_fallback, schema_instruction, split_model,
)
import llm  # noqa: E402 (extract_case를 불러와야 상위 폴더가 import 경로에 들어간다. 위로 올리면 단독 실행에서 import 실패)
from schema import OUTPUT_SCHEMA, validate_against_schema  # noqa: E402

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
    "shortIntro": "빌린 돈 문제로 다투던 지인을 집에서 살해한 사건이다.",
    "keywords": ["금전 갈등", "자백", "우발적 범행"],
    "difficulty": "MID",
    "estimatedMinutes": 10,
    "eligibility": {"eligible": True, "reasons": []},
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
    "incidentDate": "2099-01-10",  # FAKE_JUDGMENT 범죄사실의 범행일
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

    def test_processOutput_penaltyRules_areSortedHeaviestFirst(self):
        # 모델이 징역부터 줘도 표시 순서는 사형 → 무기 → 징역 → 벌금 (ERD v1.9 penalty_rule.display_order)
        reversed_rules = list(reversed(FAKE_OUTPUT["penaltyRules"]))

        case_input, _, errors, _ = process_output(output_with(penaltyRules=reversed_rules))

        self.assertEqual(errors, [])
        self.assertEqual([r["penaltyType"] for r in case_input["penaltyRules"]], ["DEATH", "LIFE", "PRISON"])

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

    def test_processOutput_validOutput_hasNoCourtEvaluationWarning(self):
        _, _, _, warnings = process_output(copy.deepcopy(FAKE_OUTPUT))

        self.assertFalse(any("재판부 평가" in w for w in warnings))

    def test_processOutput_courtEvaluation_isWarning(self):
        # 판결문 양형 이유의 재판부 평가 · 결론을 옮긴 문장 (가상 문구)
        for text in ("죄책이 무거워 처벌이 필요하다.", "엄중한 처벌이 필요하다.", "어떠한 경우에도 용서될 수 없다.",
                     "그 행위는 정당화될 수 없다.", "죄질이 좋지 않다.", "참작할 만한 사정이다.",
                     "반인륜적 범죄다.", "보통 동기 살인으로 봄이 상당하다.",
                     "재판부는 우발적 범행으로 봤다.", "재판부는 유리한 정상으로 봤다."):
            with self.subTest(text=text):
                _, _, errors, warnings = process_output(output_with(facts=text))

                self.assertEqual(errors, [])  # 사실과 섞일 수 있어 오류가 아니라 경고다
                self.assertTrue(any("sections[0](FACTS)" in w and "재판부 평가" in w for w in warnings))

    def test_processOutput_partySectionCommonExpressions_haveSourceWarningOnly(self):
        # 각 측 주장에 흔한 표현("엄중한 처벌이 필요하다", "유리한 정상")은 재판부 평가가 아니라 양형 이유 표현일 수 있다는 안내만 낸다
        for where, field, text in (("sections[4](PROSECUTOR)", "prosecutor", "엄중한 처벌이 필요하다."),
                                   ("sections[4](PROSECUTOR)", "prosecutor", "죄책이 무거워 처벌이 필요하다."),
                                   ("sections[5](DEFENSE)", "defense", "피고인에게 유리한 정상으로는 반성이 있다고 주장한다.")):
            with self.subTest(text=text):
                _, _, errors, warnings = process_output(output_with(**{field: text}))

                self.assertEqual(errors, [])
                self.assertTrue(any(where in w and "양형 이유의 표현일 수 있음" in w for w in warnings))
                self.assertFalse(any("재판부 평가" in w for w in warnings))

    def test_processOutput_partySectionNamingCourt_isCourtEvaluation(self):
        # 재판부 · 원심을 명시한 문장은 각 측 주장이라도 재판부 평가로 본다
        for field, text in (("prosecutor", "재판부는 엄중한 처벌이 필요하다고 봤다."),
                            ("defense", "원심은 유리한 정상으로 참작하였다.")):
            with self.subTest(text=text):
                _, _, _, warnings = process_output(output_with(**{field: text}))

                self.assertTrue(any("재판부 평가" in w for w in warnings))

    def test_processOutput_factsAndLawTerms_areNotCourtEvaluation(self):
        # 사실 · 피해자 의사 · 변호인 주장 · 법률 용어 설명은 걸리지 않는다
        output = output_with(
            settlement="유족은 엄벌을 원한다.",
            defense="상당한 정신적 고통을 받아 왔고, 동기를 참작해야 한다고 주장한다.",
            lawTerms=[{"term": "작량감경", "desc": "참작할 만한 사정이 있을 때 판사가 형을 줄이는 것"}])

        _, _, _, warnings = process_output(output)

        self.assertFalse(any("재판부 평가" in w for w in warnings))

    def test_processOutput_unrelatedNumbers_areNotSentenceLeak(self):
        # 법정형 범위 · 범행 기간 · 전과처럼 형량 용어와 붙지 않은 숫자는 보존한다
        for text in ("법정형은 징역 10년 이상이다.", "두 사람은 10년 동안 알고 지냈다.", "징역 110년은 없다.",
                     "과거 징역 3년형을 산 적이 있다."):
            with self.subTest(text=text):
                _, _, errors, _ = process_output(output_with(overview=FAKE_OUTPUT["overview"] + " " + text))

                self.assertFalse(any("실제 선고 형량" in e for e in errors))

    def test_processOutput_largerAmountOrPeriod_isNotSentenceLeak(self):
        # 실제 형량의 뒷부분이 더 큰 금액 · 기간의 일부로 나오는 전과 설명은 보존한다
        for court, text in (
            ({"penaltyType": "FINE", "reducedTo": None, "prisonMonths": None, "fineAmount": 20_000_000,
              "suspensionMonths": None}, "과거 1억 2천만원의 벌금을 낸 적이 있다."),
            ({"penaltyType": "PRISON", "reducedTo": None, "prisonMonths": 6, "fineAmount": None,
              "suspensionMonths": None}, "과거 2년 6개월의 징역을 받았다."),
        ):
            with self.subTest(text=text):
                _, _, errors, _ = process_output(output_with(
                    overview=FAKE_OUTPUT["overview"] + " " + text, courtJudgment=court))

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
                         ["sample-case.case.json", "sample-case.court_judgment_internal.json", "sample-case.report.json",
                          "sample-case.source_internal.json"])
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertEqual(report["status"], "NEEDS_REVIEW")
        # compare.py --court가 결과 묶음과 대조할 사건 제목이 판결 파일에 들어 있다
        case = json.loads(paths[0].read_text(encoding="utf-8"))
        court = json.loads(paths[1].read_text(encoding="utf-8"))
        self.assertEqual(court["caseTitle"], case["title"])
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
        output = output_with(reviewNotes=["원문에는 010-1234-5678과 2099고합123, 계좌 123456-12-123456이 있었다"])
        output["penaltyRules"][0]["allowedBasis"] = "가상지방법원 2099. 1. 10. 기준"

        with self.assertRaises(ExtractError):
            run(self.input, "leaky-case", out_dir=self.dir / "out", call=fake_call(output))

        raw = (self.dir / "out" / "leaky-case.report.json").read_text(encoding="utf-8")
        report = json.loads(raw)
        for value in ("010-1234-5678", "2099고합123", "123456-12-123456", "가상지방법원", "2099. 1. 10."):
            self.assertNotIn(value, raw)
        self.assertTrue(any("reviewNotes[0]" in e for e in report["errors"]))
        self.assertTrue(any("penaltyRuleBasis[" in e for e in report["errors"]))

    def test_run_checkErrors_removesStaleDryRunRequest(self):
        out = self.dir / "out"
        [request] = run(self.input, "same-name", out_dir=out, dry_run=True)
        self.assertTrue(request.exists())

        with self.assertRaises(ExtractError):
            run(self.input, "same-name", out_dir=out,
                call=fake_call(output_with(overview="2099. 1. 10. 가상지방법원 사건이다.")))

        self.assertFalse(request.exists())

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


class ListingEligibilitySourceTest(unittest.TestCase):
    """BE-31: 목록 카드 칸 · 선정 조건 판정 · 원본 판결문 정보"""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_listing_inCaseInput_notInPrompt(self):
        case_input, _, errors, _ = process_output(copy.deepcopy(FAKE_OUTPUT))
        self.assertEqual(errors, [])
        self.assertEqual(case_input["listing"]["difficulty"], "MID")
        from build_prompt import build_prompt
        prompt = build_prompt(case_input)
        self.assertNotIn(FAKE_OUTPUT["shortIntro"], prompt["user"])

    def test_listing_shortIntroTooLong_isError_andPiiChecked(self):
        _, _, errors, _ = process_output(output_with(shortIntro="가" * 201))
        self.assertTrue(any("shortIntro" in e for e in errors))
        _, _, errors, _ = process_output(output_with(keywords=["2099. 1. 10. 사건", "자백"]))
        self.assertTrue(any("listing.keywords[0]" in e for e in errors))

    def test_eligibility_false_isWarning_andInReport(self):
        output = output_with(eligibility={"eligible": False, "reasons": ["경합범이다"]})
        _, _, errors, warnings = process_output(copy.deepcopy(output))
        self.assertEqual(errors, [])
        self.assertTrue(any("서비스 대상이 아닌" in w for w in warnings))
        path = self.dir / "j.txt"
        path.write_text(FAKE_JUDGMENT, encoding="utf-8")
        paths = run(path, "sample-case", out_dir=self.dir / "out", call=fake_call(output))
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertEqual(report["eligibility"], {"eligible": False, "reasons": ["경합범이다"]})

    def test_extractSourceInfo_header(self):
        info = extract_source_info(FAKE_JUDGMENT)
        self.assertEqual(info, {"caseNumber": "2099고합123", "courtName": "서울가상지방법원",
                                "decidedAt": "2099-05-01", "courtLevel": "FIRST"})
        appeal = extract_source_info("가상고등법원\n사건 2099노45 살인\n판결선고 2099. 9. 3.")
        self.assertEqual((appeal["caseNumber"], appeal["courtLevel"], appeal["decidedAt"]),
                         ("2099노45", "APPEAL", "2099-09-03"))
        self.assertEqual(extract_source_info("머리 정보 없음")["caseNumber"], None)

    def test_extractSourceInfo_courtLevelBySign(self):
        # 사건 부호 → 심급 (1심 · 항소심 · 상고심, 군사법원 감 · 전자 부호 포함)
        expected = {"2099고합1": "FIRST", "2099고단5": "FIRST", "2099노45": "APPEAL", "2099감노2": "APPEAL",
                    "2099도1": "SUPREME", "2099감도3": "SUPREME"}
        for number, level in expected.items():
            info = extract_source_info(f"사건 {number} 살인\n판결선고 2099. 1. 1.")
            self.assertEqual((info["caseNumber"], info["courtLevel"]), (number, level), number)

    def test_run_multipleJudgments_sourceKeptLocal(self):
        first, appeal = self.dir / "first.txt", self.dir / "appeal.txt"
        first.write_text(FAKE_JUDGMENT, encoding="utf-8")
        appeal.write_text("가상고등법원\n사건 2099노45 살인\n판결선고 2099. 9. 3.\n" + "항소를 기각한다. " * 30,
                          encoding="utf-8")
        paths = run([first, appeal], "sample-case", out_dir=self.dir / "out", call=fake_call(FAKE_OUTPUT))
        source = json.loads(paths[3].read_text(encoding="utf-8"))["sources"]
        self.assertEqual([s["caseNumber"] for s in source], ["2099고합123", "2099노45"])
        self.assertIn("990101-1234567", source[0]["originalText"])  # 원문은 그대로 (내부 전용)
        # 모델에는 두 판결문을 머리표로 나눠 보내고, 사건번호 등은 가려서 보낸다
        self.assertIn("===== 판결문 2 =====", fake_call.last_user)
        self.assertNotIn("2099노45", fake_call.last_user)
        self.assertNotIn("2099고합123", fake_call.last_user)


class IncidentDateTest(unittest.TestCase):
    """BE-38: 사건 발생일은 원문 · 선고일과 대조해 확인한 값만 내부 파일에 둔다"""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.input = self.dir / "judgment.txt"
        self.input.write_text(FAKE_JUDGMENT, encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def run_with(self, **changes):
        paths = run(self.input, "sample-case", out_dir=self.dir / "out", call=fake_call(output_with(**changes)))
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        source = json.loads(paths[3].read_text(encoding="utf-8"))
        case = json.loads(paths[0].read_text(encoding="utf-8"))
        return case, report, source

    def test_dateInText_forms(self):
        text = "범행 2024. 1. 10. 20:00경, 다른 날 2024.02.03, 또 2024년 3월 4일"
        for iso in ("2024-01-10", "2024-02-03", "2024-03-04"):
            self.assertTrue(date_in_text(iso, text), iso)
        for iso in ("2024-01-01", "2024-1-1x", None, "2024-11-10"):
            self.assertFalse(date_in_text(iso, text), iso)
        self.assertFalse(date_in_text("2099-01-10", "기록번호 12099. 1. 10. 이다"))  # 앞에 숫자가 붙으면 다른 값

    def test_validDate_storedInternalOnly(self):
        case, report, source = self.run_with()
        self.assertEqual(source["incidentDate"], "2099-01-10")
        self.assertNotIn("2099-01-10", json.dumps(case, ensure_ascii=False))  # 사용자 · AI 입력에는 없다
        self.assertNotIn("incidentDate", case)
        self.assertNotIn("2099-01-10", json.dumps(report, ensure_ascii=False))  # 보고서에도 날짜 값을 남기지 않는다

    def test_invalidDates_areDropped_withWarning(self):
        for value, part in ((None, "찾지 못했습니다"), ("2099/01/10", "형식"), ("2099-02-28", "원문에서 확인되지 않아"),
                            ("2099-13-01", "형식")):
            _, report, source = self.run_with(incidentDate=value)
            self.assertIsNone(source["incidentDate"], value)
            self.assertTrue(any(part in w for w in report["warnings"]), (value, report["warnings"]))

    def test_dateAfterDecision_isDropped(self):
        judgment = FAKE_JUDGMENT.replace("판결선고 2099. 5. 1.", "판결선고 2099. 5. 1.\n별건 2099. 6. 1. 기재")
        self.input.write_text(judgment, encoding="utf-8")
        _, report, source = self.run_with(incidentDate="2099-06-01")
        self.assertIsNone(source["incidentDate"])
        self.assertTrue(any("선고일보다 늦어" in w for w in report["warnings"]))


class OtherProviderTest(unittest.TestCase):
    """BE-35: Claude 외 공급자(OpenAI · Gemini)로 비식별화"""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.input = self.dir / "judgment.txt"
        self.input.write_text(FAKE_JUDGMENT, encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    @staticmethod
    def fake_llm(texts, stop_reason="stop"):
        """llm.call 흉내: 호출마다 texts에서 하나씩 돌려주고 받은 요청을 기록한다."""
        queue, calls = list(texts), []

        def caller(spec, system, user, **kwargs):
            calls.append({"spec": spec, "system": system, "user": user, **kwargs})
            return SimpleNamespace(text=queue.pop(0), served_model=spec.split(":")[1] + "-served", stop_reason=stop_reason)

        return caller, calls

    def test_validateAgainstSchema(self):
        self.assertEqual(validate_against_schema(copy.deepcopy(FAKE_OUTPUT), OUTPUT_SCHEMA), [])
        errors = validate_against_schema(output_with(keywords="가", difficulty="EASY", extra=1, estimatedMinutes=True,
                                                     recommended={"minMonths": 1}), OUTPUT_SCHEMA)
        text = " | ".join(errors)
        for part in ("$.keywords", "$.difficulty", "$: 스키마에 없는 항목이 1개", "$.estimatedMinutes", "$.recommended"):
            self.assertIn(part, text)

    def test_validateAgainstSchema_errorsHaveNoValues(self):
        # 모델이 판결문 속 실명을 엉뚱한 칸 · 항목 이름에 넣어도 오류 메시지에는 남지 않는다
        output = output_with(difficulty="홍길동", **{"홍길동씨": "x"})
        output["factors"] = [dict(FAKE_OUTPUT["factors"][0], revealStage="김철수")]
        text = " | ".join(validate_against_schema(output, OUTPUT_SCHEMA))
        self.assertTrue(text)
        for name in ("홍길동", "김철수"):
            self.assertNotIn(name, text)
        missing = {k: v for k, v in FAKE_OUTPUT.items() if k != "title"}
        self.assertIn("$.title: 필수 항목이 없습니다", validate_against_schema(missing, OUTPUT_SCHEMA))

    def test_splitModel_andProvider(self):
        self.assertEqual(split_model("claude-opus-5-5"), ("claude", "claude-opus-5-5"))
        self.assertEqual(split_model("anthropic:claude-opus-5-5"), ("claude", "claude-opus-5-5"))
        self.assertEqual(split_model("openai:gpt-x"), ("openai", "gpt-x"))
        from llm import model_provider
        self.assertEqual(model_provider("gemini:gem-x"), "gemini")
        self.assertEqual(model_provider("claude-opus-5-5"), "anthropic")
        self.assertEqual(model_provider("anthropic:claude-x"), "anthropic")
        for bad in ("manual:x", "unknown:x"):
            with self.assertRaises(ExtractError):
                split_model(bad)

    def test_callLlm_fencedJson_isAccepted(self):
        caller, calls = self.fake_llm(["```json\n" + json.dumps(FAKE_OUTPUT, ensure_ascii=False) + "\n```"])
        output, served = call_llm("SYS", "USER", "openai:gpt-x", "high", caller=caller)
        self.assertEqual(output["title"], FAKE_OUTPUT["title"])
        self.assertEqual(served, "gpt-x-served")
        self.assertEqual((len(calls), calls[0]["json_output"]), (1, True))

    def test_callLlm_retriesOnBadFormat_thenSucceeds(self):
        bad_type = json.dumps(output_with(keywords="가"), ensure_ascii=False)
        caller, calls = self.fake_llm(["설명만 있음", bad_type, json.dumps(FAKE_OUTPUT, ensure_ascii=False)])
        output, _ = call_llm("S", "U", "gemini:gem-x", "high", caller=caller)
        self.assertEqual(len(calls), 3)
        self.assertEqual(output["keywords"], FAKE_OUTPUT["keywords"])
        self.assertIn("형식 오류", calls[1]["user"])  # 이전 오류를 알려 준다
        self.assertIn("$.keywords", calls[2]["user"])

    def test_callLlm_failsAfterRetries_andTruncated(self):
        caller, calls = self.fake_llm(["x", "y", "z"])
        with self.assertRaises(ExtractError) as ctx:
            call_llm("S", "U", "openai:gpt-x", "high", caller=caller)
        self.assertIn("3번 모두", str(ctx.exception))
        caller, _ = self.fake_llm(["{"], stop_reason="length")
        with self.assertRaises(ExtractError) as ctx:
            call_llm("S", "U", "openai:gpt-x", "high", max_tokens=1234, caller=caller)
        self.assertIn("1234", str(ctx.exception))

    def test_callLlm_blockedStop_notRetried(self):
        for reason in ("SAFETY", "RECITATION", "content_filter"):
            caller, calls = self.fake_llm(["", "", ""], stop_reason=reason)
            with self.assertRaises(ExtractError) as ctx:
                call_llm("S", "U", "gemini:gem-x", "high", caller=caller)
            self.assertEqual(len(calls), 1, reason)  # 같은 판결문을 다시 보내지 않는다
            self.assertIn(reason, str(ctx.exception))
        for reason in ("stop", "STOP", "end_turn", None):
            caller, _ = self.fake_llm([json.dumps(FAKE_OUTPUT, ensure_ascii=False)], stop_reason=reason)
            self.assertEqual(call_llm("S", "U", "openai:gpt-x", "high", caller=caller)[0]["title"], FAKE_OUTPUT["title"])

    def test_cli_maxTokens_mustBePositive(self):
        from extract_case import main as extract_main
        for bad in ("0", "-5", "abc"):
            with mock.patch.object(sys, "argv", ["extract_case.py", str(self.input), "--name", "x", "--max-tokens", bad]), \
                    mock.patch("sys.stderr"):
                with self.assertRaises(SystemExit):
                    extract_main()

    def test_dryRun_nonClaude_includesSchema(self):
        paths = run(self.input, "sample-case", out_dir=self.dir / "out", model="openai:gpt-x", dry_run=True)
        request = paths[0].read_text(encoding="utf-8")
        self.assertIn("<json_schema>", request)
        self.assertNotIn("990101-1234567", request)  # 마스킹 후 내용
        claude = run(self.input, "claude-case", out_dir=self.dir / "out", dry_run=True)
        self.assertNotIn("<json_schema>", claude[0].read_text(encoding="utf-8"))

    def test_run_openai_usesLlm_andRecordsModel(self):
        sent = {}

        def fake_call_llm(system, user, model, effort, max_tokens=None):
            sent.update(user=user, model=model, max_tokens=max_tokens)
            return copy.deepcopy(FAKE_OUTPUT), "gpt-x-2026"

        with mock.patch.dict(os.environ, {"OPENAI_API_KEY": "k"}), mock.patch("extract_case.call_llm", fake_call_llm):
            paths = run(self.input, "sample-case", out_dir=self.dir / "out", model="openai:gpt-x", max_tokens=777)
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertEqual((report["model"], report["requestedModel"]), ("gpt-x-2026", "openai:gpt-x"))
        self.assertEqual((sent["model"], sent["max_tokens"]), ("openai:gpt-x", 777))
        self.assertIn("<json_schema>", sent["user"])
        self.assertNotIn("990101-1234567", sent["user"])  # API로 가는 내용에 마스킹 전 값이 없다

    # ---- 모델 대체 체인 (BE-45)

    def run_chain(self, models, first_error=None, free_models=(), **patches):
        """call_claude · call_llm을 가짜로 바꿔 run을 돌린다. (paths, 호출 기록, 안내 로그)"""
        calls, logs = [], []

        def fake(kind):
            def inner(system, user, model, effort, max_tokens=None):
                calls.append((kind, model, user))
                if first_error is not None and len(calls) == 1:
                    raise first_error
                return copy.deepcopy(FAKE_OUTPUT), f"{model}-served"
            return inner

        env = {"OPENAI_API_KEY": "k", "GEMINI_API_KEY": "k"}
        with mock.patch.dict(os.environ, env), mock.patch("extract_case.call_llm", fake("llm")), \
                mock.patch("extract_case.call_claude", fake("claude")):
            paths = run(self.input, "sample-case", out_dir=self.dir / "out", model=models, log=logs.append,
                        free_models=free_models)
        return paths, calls, logs

    def test_run_chain_overloaded_fallsBackFromScratch(self):
        error = ModelUnavailableError("서비스 과부하 (503)", "overloaded")
        paths, calls, logs = self.run_chain(["claude-x", "openai:gpt-x"], first_error=error)
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertEqual([(kind, model) for kind, model, _ in calls], [("claude", "claude-x"), ("llm", "openai:gpt-x")])
        self.assertEqual((report["requestedModel"], report["model"]), ("openai:gpt-x", "openai:gpt-x-served"))
        self.assertEqual(report["modelChain"], ["claude-x", "openai:gpt-x"])
        self.assertEqual([(f["model"], f["kind"]) for f in report["fallbacks"]], [("claude-x", "overloaded")])
        # 다음 모델은 같은 판결문으로 처음부터 요청한다. 스키마를 강제할 수 없는 공급자(openai)만 스키마 안내를 붙인다
        self.assertNotIn("<json_schema>", calls[0][2])
        self.assertIn("<json_schema>", calls[1][2])
        self.assertEqual(calls[0][2], calls[1][2].split("\n\n---\n")[0])
        self.assertTrue(any("다음 모델 openai:gpt-x" in line and "과부하" in line for line in logs))

    def test_run_chain_freeTierBackup_warnsAndRecords(self):
        error = ModelUnavailableError("서비스 과부하 (503)", "overloaded")
        paths, calls, logs = self.run_chain(["claude-x", "gemini:gem-x"], first_error=error, free_models={"gemini:gem-x"})
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertTrue(report["usedFreeTier"])
        self.assertEqual(report["requestedModel"], "gemini:gem-x")
        warnings = [line for line in logs if "무료 등급 모델입니다" in line]
        self.assertEqual(len(warnings), 1)
        self.assertIn("gemini:gem-x", warnings[0])  # 넘어가는 순간 어떤 모델인지 함께 경고

    def test_run_chain_paidAnswers_freeBackupUnused(self):
        paths, calls, logs = self.run_chain(["claude-x", "gemini:gem-x"], free_models={"gemini:gem-x"})
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertIs(report["usedFreeTier"], False)  # 백업을 허용했지만 쓰지 않았다
        self.assertEqual(len(calls), 1)
        self.assertFalse(any("무료 등급" in line for line in logs))

    def test_run_noFreeModels_reportHasNoUsedFreeTier(self):
        paths, _, _ = self.run_chain(["openai:gpt-x"])
        self.assertNotIn("usedFreeTier", json.loads(paths[2].read_text(encoding="utf-8")))

    def test_run_chain_firstModelAnswers_noFallbackRecorded(self):
        paths, calls, _ = self.run_chain(["openai:gpt-x", "gemini:gem-x"])
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertEqual(len(calls), 1)
        self.assertEqual((report["requestedModel"], report["fallbacks"]), ("openai:gpt-x", []))

    def test_run_singleModel_reportUnchanged(self):
        paths, _, _ = self.run_chain("openai:gpt-x")
        report = json.loads(paths[2].read_text(encoding="utf-8"))
        self.assertEqual(report["requestedModel"], "openai:gpt-x")
        self.assertNotIn("modelChain", report)
        self.assertNotIn("fallbacks", report)

    def test_run_chain_qualityFailure_doesNotFallBack(self):
        with self.assertRaises(ExtractError) as ctx:
            self.run_chain(["openai:gpt-x", "gemini:gem-x"], first_error=ExtractError("응답 형식이 맞지 않습니다"))
        self.assertNotIsInstance(ctx.exception, ModelUnavailableError)
        self.assertIn("형식", str(ctx.exception))

    def test_run_chain_allUnavailable(self):
        calls = []

        def busy(system, user, model, effort, max_tokens=None):
            calls.append(model)
            raise ModelUnavailableError(f"{model} 일 한도\n→ 내일 다시", "daily_quota")

        with mock.patch.dict(os.environ, {"OPENAI_API_KEY": "k", "GEMINI_API_KEY": "k"}), \
                mock.patch("extract_case.call_llm", busy):
            with self.assertRaises(ModelUnavailableError) as ctx:
                run(self.input, "sample-case", out_dir=self.dir / "out", model=["openai:gpt-x", "gemini:gem-x"],
                    log=lambda *_: None)
        self.assertEqual(calls, ["openai:gpt-x", "gemini:gem-x"])
        self.assertIn("모든 모델을 쓸 수 없었습니다", str(ctx.exception))
        self.assertEqual([f["model"] for f in ctx.exception.skipped], ["openai:gpt-x", "gemini:gem-x"])

    def test_runWithFallback_attachesModelAndSkipped(self):
        unavailable = ModelUnavailableError("과부하", "overloaded")

        def attempt(spec):
            if spec == "a":
                raise unavailable
            raise ExtractError("형식 오류")  # 품질 문제

        with self.assertRaises(ExtractError) as ctx:
            run_with_fallback(["a", "b"], attempt, log=lambda *_: None)
        self.assertNotIsInstance(ctx.exception, ModelUnavailableError)
        self.assertEqual(ctx.exception.model, "b")  # 실제로 실패한 모델
        self.assertEqual([(f["model"], f["kind"]) for f in ctx.exception.skipped], [("a", "overloaded")])

    def test_runWithFallback_rateLimitLabel_doesNotClaimPerMinute(self):
        logs = []

        def attempt(spec):
            if spec == "a":
                raise ModelUnavailableError("429 rate_limit_error", "rate_limit")
            return "ok"

        result, used, skipped = run_with_fallback(["a", "b"], attempt, log=logs.append)
        self.assertEqual((result, used), ("ok", "b"))
        self.assertIn("요청 한도(429)", logs[0])  # Claude 429는 분당 한도 외에 사용량 · 지출 한도일 수 있다
        self.assertNotIn("분당", logs[0])

    def test_runWithFallback_singleModel_keepsOriginalError(self):
        cause = RuntimeError("원래 원인")
        error = ModelUnavailableError("한도", "rate_limit")
        error.__cause__ = cause

        def attempt(spec):
            raise error

        with self.assertRaises(ModelUnavailableError) as ctx:
            run_with_fallback(["a"], attempt)
        self.assertIs(ctx.exception, error)
        self.assertIs(ctx.exception.__cause__, cause)  # 원인 체인이 지워지지 않는다
        self.assertEqual((ctx.exception.model, len(ctx.exception.skipped)), ("a", 1))

    def test_run_chain_missingFallbackKey_stopsBeforeAnyCall(self):
        with mock.patch.dict(os.environ, {"OPENAI_API_KEY": "k"}, clear=True), \
                mock.patch("extract_case.call_llm") as called:
            with self.assertRaises(ExtractError) as ctx:
                run(self.input, "sample-case", out_dir=self.dir / "out", model=["openai:gpt-x", "gemini:gem-x"])
        self.assertIn("GEMINI_API_KEY", str(ctx.exception))
        called.assert_not_called()

    def test_modelChain_validation(self):
        self.assertEqual(model_chain("openai:a"), ["openai:a"])
        self.assertEqual(model_chain([" openai:a ", "gemini:b"]), ["openai:a", "gemini:b"])
        for bad in ([], "", ["openai:a", "openai:a"], ["openai:a", 3], None, 5):
            with self.assertRaises(ExtractError, msg=repr(bad)):
                model_chain(bad)
        with self.assertRaises(ExtractError):
            run(self.dir / "missing.txt", "sample-case", out_dir=self.dir / "out", model=["openai:a", "manual:b"])

    def test_callLlm_failureKind_becomesModelUnavailable(self):
        def blocked(kind_error):
            def caller(*args, **kwargs):
                raise kind_error
            return caller

        for error, kind in ((llm.LLMOverloadedError("busy"), "overloaded"), (llm.LLMRateLimitError("rate"), "rate_limit"),
                            (llm.LLMQuotaExhaustedError("quota"), "daily_quota")):
            with self.assertRaises(ModelUnavailableError) as ctx:
                call_llm("S", "U", "gemini:gem-x", "high", caller=blocked(error))
            self.assertEqual(ctx.exception.kind, kind)
        with self.assertRaises(ExtractError) as ctx:  # 원인 구분이 없는 오류(잘못된 요청 등)는 대체하지 않는다
            call_llm("S", "U", "gemini:gem-x", "high", caller=blocked(llm.LLMError("API 오류 (400): bad")))
        self.assertNotIsInstance(ctx.exception, ModelUnavailableError)

    def test_run_missingKey_stopsBeforeCall(self):
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("extract_case.call_llm") as called:
            with self.assertRaises(ExtractError) as ctx:
                run(self.input, "sample-case", out_dir=self.dir / "out", model="gemini:gem-x")
        self.assertIn("GEMINI_API_KEY", str(ctx.exception))
        called.assert_not_called()

    def test_run_badModelSpec_failsBeforeReading(self):
        with self.assertRaises(ExtractError):
            run(self.dir / "missing.txt", "sample-case", out_dir=self.dir / "out", model="manual:x")

    def test_schemaInstruction_containsAllFields(self):
        text = schema_instruction()
        for key in OUTPUT_SCHEMA["properties"]:
            self.assertIn(f'"{key}"', text)


if __name__ == "__main__":
    unittest.main()
