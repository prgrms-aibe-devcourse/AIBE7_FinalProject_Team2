"""재판부 판결 초안 생성 · 대조 검사 테스트 (BE-38). API는 호출하지 않는다.

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

import time  # noqa: E402

from court_draft import (  # noqa: E402
    CourtDraftError, build_court_draft, check_draft, check_sentence, display_text, effective_sentences, parse_sentences,
    quote_matches, run,
)
from common import write_json  # noqa: E402
import llm  # noqa: E402

# 설명용 가상 판결문. 개인정보 값은 모두 지어낸 것이다.
JUDGMENT = """가상지방법원
판 결
사건 2099고단77 사기
피고인 A
판결선고 2099. 5. 1.

주 문
피고인을 징역 6월에 처한다.

범죄사실
피고인은 2099. 1. 10. 20:00경 가상시 가상구 소재 별빛주점에서 술값을 지급할 의사나 능력이 없음에도 술과 안주를 주문하여 피해자로부터 합계 15,000원 상당을 제공받아 이를 편취하였다.

양형의 이유
피고인이 범행을 자백하고 반성하는 점, 피해 금액이 비교적 소액인 점은 유리한 정상이다.
그러나 피고인은 동종 범죄로 여러 차례 처벌받은 전력이 있고, 누범 기간 중에 이 사건 범행을 저질렀다는 점은 불리한 정상이다.
그 밖에 피고인의 나이, 성행, 환경 등 이 사건 변론에 나타난 모든 양형 조건을 종합하여 주문과 같이 형을 정한다.
""" + "기록 " * 20

CASE = {
    "title": "가상 무전취식 사건",
    "factors": [
        {"factorId": 1, "label": "범행을 자백하고 반성한다", "revealStage": "OVERVIEW"},
        {"factorId": 2, "label": "피해액이 소액이다", "revealStage": "OVERVIEW"},
        {"factorId": 3, "label": "동종 전과가 여러 차례 있다", "revealStage": "DETAIL"},
        {"factorId": 4, "label": "누범 기간 중 범행했다", "revealStage": "DETAIL"},
        {"factorId": 5, "label": "피해 회복이 확인되지 않는다", "revealStage": "DETAIL"},
    ],
}
COURT = {"caseTitle": "가상 무전취식 사건", "penaltyType": "PRISON", "reducedTo": None, "prisonMonths": 6,
         "fineAmount": None, "suspensionMonths": None}

DRAFT = {
    "summary": "동종 전과와 누범 기간 중 범행을 무겁게 보되 자백과 소액 피해를 참작한 판단",
    "reasoning": "재판부는 피고인이 범행을 자백하고 반성하며 피해 금액이 비교적 소액인 점을 유리하게 보았다. "
                 "그러나 동종 범죄 전력이 여러 차례 있고 누범 기간 중에 범행한 점을 불리하게 보았다.",
    "plainExplanation": "같은 잘못을 여러 번 저질렀고 처벌이 끝난 지 얼마 안 돼 다시 범행해 실형을 정했다.",
    "excerpt": "그러나 피고인은 동종 범죄로 여러 차례 처벌받은 전력이 있고, 누범 기간 중에 이 사건 범행을 저질렀다는 점은 불리한 정상이다.",
    "extraDispositions": [],
    "factors": [
        {"factorId": 1, "direction": "DOWN", "evidence": "피고인이 범행을 자백하고 반성하는 점"},
        {"factorId": 2, "direction": "DOWN", "evidence": "피해 금액이 비교적 소액인 점은 유리한 정상이다."},
        {"factorId": 3, "direction": "UP", "evidence": "피고인은 동종 범죄로 여러 차례 처벌받은 전력이 있고"},
        {"factorId": 4, "direction": "UP", "evidence": "누범 기간 중에 이 사건 범행을 저질렀다는 점은 불리한 정상이다."},
    ],
    "notes": ["피해 회복 여부는 판결문에 없다"],
}


def draft_with(**changes):
    draft = copy.deepcopy(DRAFT)
    draft.update(changes)
    return draft


class QuoteTest(unittest.TestCase):
    def test_exactQuote_andWhitespace(self):
        self.assertTrue(quote_matches("피고인을 징역 6월에 처한다.", JUDGMENT)[0])
        self.assertTrue(quote_matches("피고인을   징역 6월에\n처한다.", JUDGMENT)[0])  # 공백만 다르면 같다

    def test_replacedSpans_allowed(self):
        quote = "피고인은 ⟦사건 당일 저녁⟧ ⟦한 주점⟧에서 술값을 지급할 의사나 능력이 없음에도 술과 안주를 주문하여"
        self.assertTrue(quote_matches(quote, JUDGMENT)[0])
        self.assertEqual(display_text(quote), "피고인은 사건 당일 저녁 한 주점에서 술값을 지급할 의사나 능력이 없음에도 술과 안주를 주문하여")

    def test_paraphrase_rejected(self):
        ok, reason = quote_matches("피고인은 동종 범죄 전력이 많고 누범 기간에 범행했다.", JUDGMENT)
        self.assertFalse(ok)
        self.assertIn("원문에서 찾을 수 없습니다", reason)

    def test_mostlyReplaced_rejected(self):
        ok, reason = quote_matches("⟦피고인은 무거운 죄를 지었고⟧ 처한다 ⟦엄하게⟧", JUDGMENT)
        self.assertFalse(ok)
        self.assertIn("너무 적습니다", reason)

    def test_insertedWords_rejected(self):
        # 원문 사이에 없는 말을 끼워 넣는 것(0자 대체)은 원문 인용이 아니다
        src = "피고인은 피해자에게 사과하고 깊이 반성하고 있다."
        self.assertFalse(quote_matches("피고인은 피해자에게 사과하고 ⟦겉으로만⟧ 깊이 반성하고 있다.", src)[0])
        self.assertTrue(quote_matches("⟦A는⟧ 피해자에게 사과하고 깊이 반성하고 있다.", src)[0])  # 원문 1자 이상 대체는 허용

    def test_longReplacement_rejected(self):
        ok, reason = quote_matches("피고인은 ⟦" + "가" * 21 + "⟧에서 술값을 지급할 의사나 능력이 없음에도", JUDGMENT)
        self.assertFalse(ok)
        self.assertIn("20자", reason)

    def test_noCatastrophicBacktracking(self):
        src = "피고인 " * 3000
        quote = ("피고인 ⟦가⟧" * 5) + "원문에없는꼬리문장입니다"
        started = time.monotonic()
        self.assertFalse(quote_matches(quote, src)[0])
        self.assertLess(time.monotonic() - started, 2.0)

    def test_leadingAndTrailingReplacement(self):
        self.assertTrue(quote_matches("⟦사건 당일 저녁⟧ ⟦한 주점⟧에서 술값을 지급할 의사나 능력이 없음에도", JUDGMENT)[0])
        self.assertTrue(quote_matches("피고인이 범행을 자백하고 반성하는 점, ⟦일부⟧", JUDGMENT)[0])

    def test_unbalancedMarks_rejected(self):
        self.assertFalse(quote_matches("피고인을 징역 ⟦6월에 처한다.", JUDGMENT)[0])


class SentenceTest(unittest.TestCase):
    def test_parseSentences(self):
        self.assertEqual(parse_sentences("피고인을 징역 1년 6월에 처한다.")[0]["prisonMonths"], 18)
        self.assertEqual(parse_sentences("피고인을 징역 10년에 처한다.")[0]["prisonMonths"], 120)
        fine = parse_sentences("피고인을 벌금 3,000,000원에 처한다.")[0]
        self.assertEqual((fine["penalty"], fine["fineAmount"]), ("FINE", 3_000_000))
        suspended = parse_sentences("피고인을 징역 1년에 처한다. 다만 이 판결 확정일부터 2년간 위 형의 집행을 유예한다.")[0]
        self.assertEqual(suspended["suspensionMonths"], 24)
        self.assertEqual(parse_sentences("피고인을 무기징역에 처한다.")[0]["penalty"], "LIFE")
        self.assertEqual(parse_sentences("항소를 기각한다."), [])
        # "각 형의" · "N년 M월간"도 집행유예로 읽는다
        each = parse_sentences("피고인들을 각 징역 1년에 처한다. 다만 이 판결 확정일부터 2년간 각 형의 집행을 유예한다.")[0]
        self.assertEqual(each["suspensionMonths"], 24)
        months = parse_sentences("피고인을 징역 6월에 처한다. 다만 이 판결 확정일부터 1년 6월간 위 형의 집행을 유예한다.")[0]
        self.assertEqual(months["suspensionMonths"], 18)

    def test_checkSentence(self):
        self.assertEqual(check_sentence(COURT, [JUDGMENT]), ([], []))
        errors, _ = check_sentence(dict(COURT, prisonMonths=8), [JUDGMENT])
        self.assertTrue(errors)
        self.assertNotIn("8", errors[0])  # 메시지에 형량 값을 남기지 않는다
        # 항소 기각 판결만 있으면 주문에 형량이 없어도 1심 주문과 맞으면 통과
        self.assertEqual(check_sentence(COURT, ["항소를 기각한다.", JUDGMENT]), ([], []))
        _, warnings = check_sentence(COURT, ["주문 없음"])
        self.assertTrue(warnings)

    def test_checkSentence_usesFinalJudgmentOnly(self):
        first = "주문 피고인을 징역 2년에 처한다. 다만 이 판결 확정일부터 3년간 위 형의 집행을 유예한다."
        appeal = "주문 원심판결을 파기한다. 피고인을 징역 1년 6월에 처한다."
        lower = {"penaltyType": "PRISON", "prisonMonths": 24, "suspensionMonths": 36, "fineAmount": None}
        final = {"penaltyType": "PRISON", "prisonMonths": 18, "suspensionMonths": None, "fineAmount": None}
        levels = ["FIRST", "APPEAL"]
        self.assertTrue(check_sentence(lower, [first, appeal], levels)[0])  # 하급심 형량은 오류
        self.assertEqual(check_sentence(final, [first, appeal], levels), ([], []))
        # 항소 기각이면 1심 주문이 확정된 형이다
        dismissed = "주문 피고인의 항소를 기각한다."
        self.assertEqual(check_sentence(lower, [first, dismissed], levels), ([], []))
        # 최종 판결 번호를 직접 주면 그 판결을 쓴다
        self.assertEqual(effective_sentences([first, appeal], levels, final_index=0)[0]["prisonMonths"], 24)


class CheckDraftTest(unittest.TestCase):
    def test_validDraft(self):
        self.assertEqual(check_draft(copy.deepcopy(DRAFT), CASE, JUDGMENT)[0], [])

    def test_errors(self):
        cases = [
            (draft_with(excerpt="피고인은 전과가 많아 엄하게 처벌한다."), "excerpt: 원문 인용이 아닙니다"),
            (draft_with(factors=[{"factorId": 9, "direction": "UP", "evidence": "피고인을 징역 6월에 처한다."}]),
             "목록에 없는 판단 요소 번호 9"),
            (draft_with(factors=DRAFT["factors"] + [DRAFT["factors"][0]]), "두 번"),
            (draft_with(factors=[]), "고려한 판단 요소가 없습니다"),
            (draft_with(summary="가" * 101), "summary가 100자"),
            (draft_with(reasoning="재판부 판단이 정답이다."), "평가 표현"),
            (draft_with(plainExplanation="2099. 1. 10. 범행했다."), "정확한 날짜"),
            (draft_with(extraDispositions=[{"type": "FORFEIT", "value": "x"}]), "허용되는 값이 아닙니다"),
            (draft_with(notes=["사건 2099고단77 확인"]), "notes[0]"),
        ]
        for draft, part in cases:
            errors, _ = check_draft(draft, CASE, JUDGMENT)
            self.assertTrue(any(part in e for e in errors), (part, errors))

    def test_quoteWithDate_mustBeReplaced(self):
        # 원문 그대로 인용했더라도 날짜를 바꾸지 않으면 개인정보 검사에서 걸린다
        evidence = "피고인은 2099. 1. 10. 20:00경 가상시 가상구 소재 별빛주점에서 술값을 지급할 의사나 능력이 없음에도"
        draft = draft_with(factors=[{"factorId": 3, "direction": "UP", "evidence": evidence}])
        errors, _ = check_draft(draft, CASE, JUDGMENT)
        self.assertTrue(any("정확한 날짜" in e for e in errors))

    def test_buildCourtDraft_be14Format(self):
        result = build_court_draft(draft_with(excerpt="피고인은 ⟦사건 당일⟧ 술값을 지급할 의사나 능력이 없음에도"), CASE, COURT, "m")
        j = result["judgment"]
        self.assertEqual((j["subjectType"], j["timing"], j["penaltyType"], j["prisonMonths"], j["isPublished"]),
                         ("COURT", "FINAL", "PRISON", 6, False))
        self.assertNotIn("⟦", j["excerpt"])
        self.assertEqual([f["factorId"] for f in result["judgmentFactors"]], [1, 2, 3, 4])
        self.assertEqual(result["judgmentFactors"][0]["label"], "범행을 자백하고 반성한다")
        self.assertEqual(result["excludedFactors"]["factorIds"], [5])


class RunTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        write_json(self.dir / "c.case.json", CASE)
        write_json(self.dir / "c.court_judgment_internal.json", COURT)
        write_json(self.dir / "c.source_internal.json", {"sources": [{"file": "a.txt", "originalText": JUDGMENT}]})

    def tearDown(self):
        self.tmp.cleanup()

    @staticmethod
    def caller(texts, stop_reason="stop"):
        queue, calls = list(texts), []

        def call(spec, system, user, **kwargs):
            calls.append({"spec": spec, "system": system, "user": user})
            item = queue.pop(0)
            return SimpleNamespace(text=item if isinstance(item, str) else json.dumps(item, ensure_ascii=False),
                                   served_model="served-x", stop_reason=stop_reason)

        return call, calls

    def test_run_writesDraft_andMasksInput(self):
        call, calls = self.caller([DRAFT])
        draft_path, report_path = run("c", self.dir, "openai:gpt-x", caller=call)
        draft = json.loads(draft_path.read_text(encoding="utf-8"))
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual((report["status"], report["model"], report["attempts"]), ("NEEDS_REVIEW", "served-x", 1))
        self.assertEqual(draft["judgment"]["prisonMonths"], 6)
        self.assertNotIn("2099고단77", calls[0]["user"])  # 사건번호는 가려서 보낸다
        self.assertNotIn("가상지방법원", calls[0]["user"])
        self.assertIn("5. 피해 회복이 확인되지 않는다", calls[0]["user"])  # 요소 목록을 준다

    def test_run_retriesWithFeedback_thenFails(self):
        bad = draft_with(excerpt="지어낸 문장이다.")
        call, calls = self.caller([bad, DRAFT])
        run("c", self.dir, "openai:gpt-x", caller=call)
        self.assertEqual(len(calls), 2)
        self.assertIn("이전 응답의 오류", calls[1]["user"])
        call, calls = self.caller([bad, bad, bad])
        with self.assertRaises(CourtDraftError):
            run("c", self.dir, "openai:gpt-x", caller=call)
        self.assertFalse((self.dir / "c.court_draft.json").exists())  # 실패하면 이전 초안도 지운다
        report = json.loads((self.dir / "c.court_report.json").read_text(encoding="utf-8"))
        self.assertEqual(report["status"], "ERROR")

    def test_run_blockedStop_notRetried(self):
        call, calls = self.caller([""], stop_reason="SAFETY")
        with self.assertRaises(CourtDraftError) as ctx:
            run("c", self.dir, "gemini:g", caller=call)
        self.assertEqual(len(calls), 1)
        self.assertIn("SAFETY", str(ctx.exception))

    def test_run_sentenceMismatch_stopsBeforeCall(self):
        write_json(self.dir / "c.court_judgment_internal.json", dict(COURT, prisonMonths=8))
        call, calls = self.caller([DRAFT])
        with self.assertRaises(CourtDraftError):
            run("c", self.dir, "openai:gpt-x", caller=call)
        self.assertEqual(calls, [])

    def test_run_sourcesWithoutText_finalIndexRemapped(self):
        # 원문이 없는 항목이 섞여도 오류 없이, 최종 판결 번호는 전체 sources 기준으로 해석한다
        appeal = "가상고등법원\n주 문\n원심판결을 파기한다. 피고인을 징역 4월에 처한다.\n" + "기록 " * 20
        write_json(self.dir / "c.source_internal.json", {"sources": [
            {"file": "missing.txt"},  # 원문 없음 (읽기 실패 등)
            {"file": "a.txt", "originalText": JUDGMENT, "courtLevel": "FIRST"},
            {"file": "b.txt", "originalText": appeal, "courtLevel": "APPEAL"},
        ]})
        call, calls = self.caller([DRAFT])
        # 1심(전체 번호 1)을 최종으로 지정하면 1심 주문(6월)과 대조해 통과한다
        run("c", self.dir, "openai:gpt-x", caller=call, final_index=1)
        self.assertNotIn("missing.txt", calls[0]["user"])
        # 지정하지 않으면 심급이 가장 높은 항소심 주문(4월)과 대조해 실패한다
        call, calls = self.caller([DRAFT])
        with self.assertRaises(CourtDraftError):
            run("c", self.dir, "openai:gpt-x", caller=call)
        self.assertEqual(calls, [])

    # ---- 모델 대체 체인 (BE-45)

    @staticmethod
    def chain_caller(script):
        """모델별로 정해 둔 응답을 차례로 준다. 값이 Exception이면 던진다."""
        queues, calls = {spec: list(items) for spec, items in script.items()}, []

        def call(spec, system, user, **kwargs):
            calls.append({"spec": spec, "user": user})
            item = queues[spec].pop(0)
            if isinstance(item, Exception):
                raise item
            return SimpleNamespace(text=item if isinstance(item, str) else json.dumps(item, ensure_ascii=False),
                                   served_model=f"{spec}-served", stop_reason="stop")

        return call, calls

    def report(self):
        return json.loads((self.dir / "c.court_report.json").read_text(encoding="utf-8"))

    def test_run_chain_overloaded_fallsBack(self):
        call, calls = self.chain_caller({"openai:gpt-x": [llm.LLMOverloadedError("서비스 과부하 (503)")],
                                         "gemini:g": [DRAFT]})
        logs = []
        draft_path, _ = run("c", self.dir, ["openai:gpt-x", "gemini:g"], caller=call, log=logs.append)
        report = self.report()
        self.assertEqual([c["spec"] for c in calls], ["openai:gpt-x", "gemini:g"])
        self.assertTrue(draft_path.exists())
        self.assertEqual((report["requestedModel"], report["model"]), ("gemini:g", "gemini:g-served"))
        self.assertEqual(report["modelChain"], ["openai:gpt-x", "gemini:g"])
        self.assertEqual([(f["model"], f["kind"]) for f in report["fallbacks"]], [("openai:gpt-x", "overloaded")])
        self.assertTrue(any("다음 모델 gemini:g" in line for line in logs))

    def test_run_chain_freeTierBackup_warnsAndRecords(self):
        call, _ = self.chain_caller({"openai:gpt-x": [llm.LLMOverloadedError("서비스 과부하 (503)")], "gemini:g": [DRAFT]})
        logs = []
        run("c", self.dir, ["openai:gpt-x", "gemini:g"], caller=call, log=logs.append, free_models={"gemini:g"})
        report = self.report()
        self.assertIs(report["usedFreeTier"], True)
        self.assertEqual(len([line for line in logs if "무료 등급 모델입니다" in line and "gemini:g" in line]), 1)

    def test_run_chain_paidAnswers_freeBackupUnused(self):
        call, calls = self.chain_caller({"openai:gpt-x": [DRAFT], "gemini:g": [DRAFT]})
        logs = []
        run("c", self.dir, ["openai:gpt-x", "gemini:g"], caller=call, log=logs.append, free_models={"gemini:g"})
        self.assertIs(self.report()["usedFreeTier"], False)
        self.assertEqual([c["spec"] for c in calls], ["openai:gpt-x"])
        self.assertFalse(any("무료 등급" in line for line in logs))

    def test_run_noFreeModels_reportHasNoUsedFreeTier(self):
        call, _ = self.chain_caller({"openai:gpt-x": [DRAFT]})
        run("c", self.dir, "openai:gpt-x", caller=call, log=lambda *_: None)
        self.assertNotIn("usedFreeTier", self.report())

    def test_run_chain_midRetry_nextModelStartsFresh(self):
        bad = draft_with(excerpt="지어낸 문장이다.")
        call, calls = self.chain_caller({"openai:gpt-x": [bad, llm.LLMQuotaExhaustedError("일 한도")],
                                         "gemini:g": [DRAFT]})
        run("c", self.dir, ["openai:gpt-x", "gemini:g"], caller=call, log=lambda *_: None)
        self.assertEqual([c["spec"] for c in calls], ["openai:gpt-x", "openai:gpt-x", "gemini:g"])
        self.assertIn("이전 응답의 오류", calls[1]["user"])      # 앞 모델의 재요청에는 오류 안내가 붙었지만
        self.assertNotIn("이전 응답의 오류", calls[2]["user"])   # 대체 모델은 처음부터 (앞 모델의 응답 · 오류를 이어받지 않는다)
        self.assertEqual(self.report()["attempts"], 1)           # 재요청 횟수도 새로 센다

    def test_run_chain_qualityFailure_doesNotFallBack(self):
        bad = draft_with(excerpt="지어낸 문장이다.")
        call, calls = self.chain_caller({"openai:gpt-x": [bad, bad, bad], "gemini:g": [DRAFT]})
        with self.assertRaises(CourtDraftError):
            run("c", self.dir, ["openai:gpt-x", "gemini:g"], caller=call, log=lambda *_: None)
        self.assertEqual({c["spec"] for c in calls}, {"openai:gpt-x"})  # 검사 실패는 다른 모델로 덮지 않는다
        self.assertEqual(self.report()["status"], "ERROR")

    def test_run_chain_fallbackModelQualityFailure_reportsFailingModel(self):
        bad = draft_with(excerpt="지어낸 문장이다.")
        call, calls = self.chain_caller({"openai:gpt-x": [llm.LLMOverloadedError("서비스 과부하 (503)")],
                                         "gemini:g": [bad, bad, bad]})
        with self.assertRaises(CourtDraftError):
            run("c", self.dir, ["openai:gpt-x", "gemini:g"], caller=call, log=lambda *_: None)
        report = self.report()
        self.assertEqual(report["status"], "ERROR")
        self.assertEqual(report["requestedModel"], "gemini:g")  # 실제로 실패한 모델
        self.assertEqual([(f["model"], f["kind"]) for f in report["fallbacks"]], [("openai:gpt-x", "overloaded")])
        self.assertEqual([c["spec"] for c in calls], ["openai:gpt-x", "gemini:g", "gemini:g", "gemini:g"])

    def test_run_chain_allUnavailable_recordsFallbacks(self):
        call, _ = self.chain_caller({"openai:gpt-x": [llm.LLMRateLimitError("분당 한도")],
                                     "gemini:g": [llm.LLMQuotaExhaustedError("일 한도")]})
        with self.assertRaises(CourtDraftError) as ctx:
            run("c", self.dir, ["openai:gpt-x", "gemini:g"], caller=call, log=lambda *_: None)
        self.assertIn("모든 모델을 쓸 수 없었습니다", str(ctx.exception))
        report = self.report()
        self.assertEqual(report["status"], "ERROR")
        self.assertEqual([(f["model"], f["kind"]) for f in report["fallbacks"]],
                         [("openai:gpt-x", "rate_limit"), ("gemini:g", "daily_quota")])
        self.assertFalse((self.dir / "c.court_draft.json").exists())

    def test_run_chain_singleModelList_reportUnchanged(self):
        call, _ = self.chain_caller({"openai:gpt-x": [DRAFT]})
        run("c", self.dir, ["openai:gpt-x"], caller=call, log=lambda *_: None)
        report = self.report()
        self.assertEqual(report["requestedModel"], "openai:gpt-x")
        self.assertNotIn("fallbacks", report)

    def test_run_chain_missingFallbackKey(self):
        with mock.patch.dict(os.environ, {"OPENAI_API_KEY": "k"}, clear=True):
            with self.assertRaises(CourtDraftError) as ctx:
                run("c", self.dir, ["openai:gpt-x", "gemini:g"])
        self.assertIn("GEMINI_API_KEY", str(ctx.exception))

    def test_run_missingKey(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(CourtDraftError) as ctx:
                run("c", self.dir, "openai:gpt-x")
        self.assertIn("OPENAI_API_KEY", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
