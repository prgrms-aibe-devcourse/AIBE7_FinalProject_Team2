"""사건 대본 — 증거조사 대사 생성 (BE-107) 테스트. Gemini는 호출하지 않는다(모델 호출은 가짜 함수).

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import sys
import unittest
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402
import script_generation  # noqa: E402
from script_generation import (  # noqa: E402
    balance_warning, build_evidence_prompt, cast_errors, generate_evidence_lines,
    ordered_evidence_factors, validate_line,
)

CASE = {
    "title": "빌린 돈 문제로 찾아온 지인을 살해한 사건",
    "overview": "피고인이 빌린 돈을 갚지 못해 오래 다투던 지인이 집으로 찾아오자, 말다툼 끝에 흉기로 살해했다.",
    "factors": [
        {"factorId": 2, "label": "다투던 중 집에 있던 흉기를 집어 들었다", "revealStage": "OVERVIEW"},
        {"factorId": 3, "label": "범행 뒤 구호 조치 없이 현장을 떠났다", "revealStage": "OVERVIEW"},
        {"factorId": 7, "label": "수사 초기부터 범행을 인정하고 반성하고 있다", "revealStage": "DETAIL"},
        {"factorId": 9, "label": "피해 회복을 위해 5,000만 원을 공탁했다", "revealStage": "DETAIL"},
        {"factorId": 10, "label": "피고인은 우발적 범행이라고 주장한다", "revealStage": "ARGUMENT"},
    ],
}

JUDGMENT_OUTPUT = {
    "factors": [
        {"factorId": 2, "direction": "UP", "reason": "다투던 중 집에 있던 흉기를 집어 들어 피해자를 공격했다."},
        {"factorId": 3, "direction": "UP", "reason": "범행 뒤 구호 조치 없이 현장을 떠났다."},
        {"factorId": 7, "direction": "DOWN", "reason": "수사 초기부터 범행을 인정하고 반성하고 있다."},
        {"factorId": 9, "direction": "DOWN", "reason": "피해 회복을 위해 5,000만 원을 공탁했다."},
        {"factorId": 10, "direction": "DOWN", "reason": "우발적 범행이라 비난 가능성이 낮다."},
        {"factorId": 11, "direction": None, "reason": "선택하지 않음"},
    ]
}

CAST = {"prosecutor": "naero", "defense": "nambul"}


class FakeCaller:
    """항목마다 순서대로 응답을 내주는 가짜 모델 호출. 호출 인자도 기록한다."""

    def __init__(self, responses):
        self.responses, self.calls = list(responses), []

    def __call__(self, model, system, user, **kwargs):
        self.calls.append({"model": model, "system": system, "user": user, **kwargs})
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return llm.LLMResult(response, "gemini", model)


class OrderedEvidenceFactorsTest(unittest.TestCase):
    def test_orders_up_before_down_and_drops_none(self):
        ordered = ordered_evidence_factors(CASE, JUDGMENT_OUTPUT)
        self.assertEqual([f["factorId"] for f in ordered], [2, 3, 7, 9])

    def test_uses_case_label_not_judgment_reason(self):
        ordered = ordered_evidence_factors(CASE, JUDGMENT_OUTPUT)
        factor = next(f for f in ordered if f["factorId"] == 2)
        self.assertEqual(factor["label"], "다투던 중 집에 있던 흉기를 집어 들었다")
        self.assertNotIn("reason", factor)

    def test_drops_argument_stage_factor(self):
        ordered = ordered_evidence_factors(CASE, JUDGMENT_OUTPUT)
        self.assertNotIn(10, [f["factorId"] for f in ordered])

    def test_drops_factor_missing_from_case(self):
        ordered = ordered_evidence_factors(CASE, JUDGMENT_OUTPUT)
        self.assertNotIn(11, [f["factorId"] for f in ordered])

    def test_empty_factors(self):
        self.assertEqual(ordered_evidence_factors(CASE, {"factors": []}), [])


class CastErrorsTest(unittest.TestCase):
    def test_valid_cast(self):
        self.assertEqual(cast_errors({"prosecutor": "naero", "defense": "nambul"}), [])

    def test_not_a_dict(self):
        self.assertTrue(cast_errors(None))
        self.assertTrue(cast_errors("naero"))

    def test_missing_role(self):
        errors = cast_errors({"prosecutor": "naero"})
        self.assertTrue(any("defense" in e for e in errors))

    def test_invalid_character_name(self):
        errors = cast_errors({"prosecutor": "naero", "defense": "law"})
        self.assertTrue(any("defense" in e for e in errors))

    def test_same_character_for_both_roles(self):
        errors = cast_errors({"prosecutor": "naero", "defense": "naero"})
        self.assertTrue(any("같은 캐릭터" in e for e in errors))


class BuildEvidencePromptTest(unittest.TestCase):
    def test_up_direction_uses_prosecutor_role_and_cast_character(self):
        factor = {"factorId": 2, "direction": "UP", "label": "흉기를 집어 들었다"}
        system, user, role, character = build_evidence_prompt(CASE, factor, CAST, is_first_for_role=True)
        self.assertEqual(role, "prosecutor")
        self.assertEqual(character, "naero")
        self.assertIn("내로", system)
        self.assertIn("검사", system)
        self.assertIn(factor["label"], user)
        self.assertIn(CASE["title"], user)

    def test_down_direction_uses_defense_role_and_cast_character(self):
        factor = {"factorId": 7, "direction": "DOWN", "label": "범행을 인정하고 반성하고 있다"}
        system, user, role, character = build_evidence_prompt(CASE, factor, CAST, is_first_for_role=True)
        self.assertEqual(role, "defense")
        self.assertEqual(character, "nambul")
        self.assertIn("남불", system)
        self.assertIn("변호인", system)

    def test_first_for_role_announces_side(self):
        factor = {"factorId": 2, "direction": "UP", "label": "흉기를 집어 들었다"}
        _, user, _, _ = build_evidence_prompt(CASE, factor, CAST, is_first_for_role=True)
        self.assertIn("처음 말하는 차례", user)

    def test_not_first_for_role_carries_previous_text(self):
        factor = {"factorId": 3, "direction": "UP", "label": "구호 조치 없이 떠났다"}
        _, user, _, _ = build_evidence_prompt(
            CASE, factor, CAST, is_first_for_role=False, previous_text="검사 측 증거입니다. 흉기를 쓴 기록입니다.")
        self.assertIn("검사 측 증거입니다. 흉기를 쓴 기록입니다.", user)
        self.assertIn("다시 말하지 마세요", user)


class ValidateLineTest(unittest.TestCase):
    FACTOR = {"label": "피해 회복을 위해 5,000만 원을 공탁했다."}

    def test_valid_line_ok(self):
        result = validate_line("검사 측 증거입니다. 흉기를 사용한 기록입니다.", self.FACTOR)
        self.assertTrue(result["ok"])
        self.assertEqual(result["errors"], [])

    def test_blank_response_is_error(self):
        result = validate_line("   ", self.FACTOR)
        self.assertFalse(result["ok"])

    def test_too_many_lines_is_error(self):
        result = validate_line("한 줄\n두 줄\n세 줄", self.FACTOR)
        self.assertFalse(result["ok"])
        self.assertIn("2줄", result["errors"][0])

    def test_too_many_chars_is_error(self):
        result = validate_line("가" * 101, self.FACTOR)
        self.assertFalse(result["ok"])
        self.assertTrue(any("100자" in e for e in result["errors"]))

    def test_exactly_max_chars_is_ok(self):
        result = validate_line("가" * 100, self.FACTOR)
        self.assertTrue(result["ok"])

    def test_banned_word_is_error(self):
        result = validate_line("법의 정령이 이 증거를 소개합니다.", self.FACTOR)
        self.assertFalse(result["ok"])
        self.assertTrue(any("정령" in e for e in result["errors"]))

    def test_spouse_is_not_banned_word(self):
        factor = {"label": "피고인의 배우자가 증인으로 나섰다."}
        result = validate_line("피고인의 배우자가 증인으로 나선 기록입니다.", factor)
        self.assertTrue(result["ok"])

    def test_heavy_punishment_demand_is_not_banned_word(self):
        factor = {"label": "유족이 엄벌을 원한다."}
        result = validate_line("유족은 엄벌을 원한다는 진술입니다.", factor)
        self.assertTrue(result["ok"])

    def test_number_not_in_source_is_warning_not_error(self):
        result = validate_line("피해 회복을 위해 9,999만 원을 공탁했습니다.", self.FACTOR)
        self.assertTrue(result["ok"])
        self.assertTrue(any("9999" in w for w in result["warnings"]))

    def test_number_present_in_source_has_no_warning(self):
        result = validate_line("피해 회복을 위해 5,000만 원을 공탁했습니다.", self.FACTOR)
        self.assertEqual(result["warnings"], [])

    def test_number_token_comparison_not_substring(self):
        # 과거 버전은 "number not in source"로 부분 문자열 비교를 해서 "1"이 "5,000" 안에 있다고
        # 오인하지 않았다 — 오히려 반대로 "20"이 "2023" 안에 있다는 식의 오탐이 문제였다(리뷰 6번).
        # 전체 숫자 토큰으로 비교하면 "1"은 원문의 "5000"과 다른 숫자이므로 그대로 경고해야 한다.
        result = validate_line("피해 회복을 위해 1만 원을 공탁했습니다.", self.FACTOR)
        self.assertTrue(any("1" in w for w in result["warnings"]))


class GenerateEvidenceLinesTest(unittest.TestCase):
    def test_generates_one_line_per_factor_in_order(self):
        caller = FakeCaller(["검사 측 증거입니다. 흉기를 사용한 기록입니다.",
                             "범행 뒤 구호 조치 없이 떠난 기록입니다.",
                             "변호인 측 증거입니다. 수사 초기부터 인정하고 반성한 기록입니다.",
                             "5,000만 원을 공탁한 자료입니다."])
        lines, stopped = generate_evidence_lines(CASE, JUDGMENT_OUTPUT, CAST, model="gemini:gemini-3.7-flash",
                                                  caller=caller)
        self.assertFalse(stopped)
        self.assertEqual([line["factorId"] for line in lines], [2, 3, 7, 9])
        self.assertEqual([line["role"] for line in lines], ["prosecutor", "prosecutor", "defense", "defense"])
        self.assertTrue(all(line["validation"]["ok"] for line in lines))
        self.assertEqual(len(caller.calls), 4)
        self.assertFalse(caller.calls[0]["json_output"])

    def test_first_of_each_role_is_marked_first_rest_are_not(self):
        caller = FakeCaller(["검사 측 증거입니다. 흉기를 사용한 기록입니다.",
                             "구호 조치 없이 떠난 기록입니다.",
                             "변호인 측 증거입니다. 인정하고 반성한 기록입니다.",
                             "5,000만 원을 공탁한 자료입니다."])
        generate_evidence_lines(CASE, JUDGMENT_OUTPUT, CAST, model="gemini:gemini-3.7-flash", caller=caller)
        self.assertIn("처음 말하는 차례", caller.calls[0]["user"])
        self.assertIn("검사 측 증거입니다. 흉기를 사용한 기록입니다.", caller.calls[1]["user"])
        self.assertIn("처음 말하는 차례", caller.calls[2]["user"])
        self.assertIn("변호인 측 증거입니다. 인정하고 반성한 기록입니다.", caller.calls[3]["user"])

    def test_retries_on_invalid_response_then_succeeds(self):
        caller = FakeCaller(["세 줄짜리\n응답\n입니다", "다시 쓴 한 줄짜리 증거 대사입니다."])
        case = {"title": "t", "overview": "o", "factors": [{"factorId": 1, "label": "이유", "revealStage": "OVERVIEW"}]}
        lines, stopped = generate_evidence_lines(
            case, {"factors": [{"factorId": 1, "direction": "UP", "reason": "이유"}]},
            CAST, model="gemini:gemini-3.7-flash", caller=caller, max_retries=2)
        self.assertEqual(len(lines), 1)
        self.assertTrue(lines[0]["validation"]["ok"])
        self.assertEqual(lines[0]["attempts"], 2)

    def test_gives_up_after_max_retries(self):
        caller = FakeCaller(["너무\n긴\n응답", "또\n너무\n긴\n응답", "여전히\n긴\n응답"])
        case = {"title": "t", "overview": "o", "factors": [{"factorId": 1, "label": "이유", "revealStage": "OVERVIEW"}]}
        lines, stopped = generate_evidence_lines(
            case, {"factors": [{"factorId": 1, "direction": "UP", "reason": "이유"}]},
            CAST, model="gemini:gemini-3.7-flash", caller=caller, max_retries=2)
        self.assertFalse(stopped)
        self.assertEqual(len(lines), 1)
        self.assertFalse(lines[0]["validation"]["ok"])
        self.assertIsNone(lines[0]["text"])
        self.assertEqual(lines[0]["attempts"], 3)

    def test_stops_remaining_items_on_quota_exhausted(self):
        caller = FakeCaller(["첫 증거 대사입니다.", llm.LLMQuotaExhaustedError("일 한도 초과")])
        logs = []
        lines, stopped = generate_evidence_lines(CASE, JUDGMENT_OUTPUT, CAST, model="gemini:gemini-3.7-flash",
                                                  caller=caller, log=logs.append)
        self.assertTrue(stopped)
        self.assertEqual(len(lines), 1)
        self.assertTrue(any("한도 소진" in line for line in logs))

    def test_llm_error_counts_as_attempt_and_retries(self):
        caller = FakeCaller([llm.LLMError("일시적 오류"), "재시도 뒤 받은 증거 대사입니다."])
        case = {"title": "t", "overview": "o", "factors": [{"factorId": 1, "label": "이유", "revealStage": "OVERVIEW"}]}
        lines, stopped = generate_evidence_lines(
            case, {"factors": [{"factorId": 1, "direction": "UP", "reason": "이유"}]},
            CAST, model="gemini:gemini-3.7-flash", caller=caller, max_retries=2)
        self.assertTrue(lines[0]["validation"]["ok"])
        self.assertEqual(lines[0]["attempts"], 2)


class BalanceWarningTest(unittest.TestCase):
    def make_lines(self, prosecutor_texts, defense_texts):
        lines = [{"role": "prosecutor", "text": t} for t in prosecutor_texts]
        lines += [{"role": "defense", "text": t} for t in defense_texts]
        return lines

    def test_balanced_lengths_no_warning(self):
        lines = self.make_lines(["열 글자 정도의 대사", "열 글자 정도의 대사"],
                                ["열 글자 정도의 대사", "열 글자 정도의 대사"])
        self.assertIsNone(balance_warning(lines))

    def test_skewed_lengths_warn_longer_side(self):
        lines = self.make_lines(["이것은 아주 길게 쓴 검사 측 대사로 분량이 눈에 띄게 많습니다 정말 깁니다"],
                                ["짧음"])
        warning = balance_warning(lines)
        self.assertIsNotNone(warning)
        self.assertIn("검사", warning)

    def test_missing_one_side_returns_none(self):
        lines = self.make_lines(["대사"], [])
        self.assertIsNone(balance_warning(lines))

    def test_ignores_failed_lines_without_text(self):
        lines = [{"role": "prosecutor", "text": None}, {"role": "defense", "text": None}]
        self.assertIsNone(balance_warning(lines))


if __name__ == "__main__":
    unittest.main()
