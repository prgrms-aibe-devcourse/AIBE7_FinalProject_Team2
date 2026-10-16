"""extract · court 퓨샷 예시 검사 (BE-53). API는 호출하지 않는다.

예시가 규칙을 어기면 모델이 그 잘못을 배운다. 그래서 예시 자체를 실제 검사 코드(스키마 · 원문 인용 · 남은 개인정보 ·
가치관 축)에 그대로 넣어 통과하는지 확인한다. 예시를 고치면 이 테스트가 함께 지킨다.

실행: tools/ai-judgment/case-extractor에서 `python3 -m unittest discover tests`
"""

import json
import re
import sys
import unittest
from pathlib import Path

EXTRACTOR_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(EXTRACTOR_DIR))

from court_draft import build_messages as court_messages, check_draft  # noqa: E402
from deidentify import residual_check  # noqa: E402
from extract_case import build_messages as extract_messages  # noqa: E402
from schema import (  # noqa: E402
    LABEL_MAX_LENGTH, OUTPUT_SCHEMA, SUMMARY_TAG_MAX_LENGTH, check_value_axis, validate_against_schema,
)

PROMPTS = EXTRACTOR_DIR / "prompts"


def read(name):
    return (PROMPTS / name).read_text(encoding="utf-8")


def example_source(text):
    """예시 입력의 판결문(<judgment> 안)."""
    match = re.search(r"<judgment>\n(.*?)\n</judgment>", text, re.S)
    assert match, "예시 판결문(<judgment>)이 없습니다"
    return match.group(1)


def example_output(text):
    """'## 예시 출력' 아래의 첫 JSON 객체."""
    start = text.index("## 예시 출력")
    brace = text.index("{", start)
    value, _ = json.JSONDecoder().raw_decode(text, brace)
    return value


def example_factor_list(text):
    """court 예시 입력의 판단 요소 목록 → [{factorId, label}]."""
    return [{"factorId": int(n), "label": label}
            for n, label in re.findall(r"^- (\d+)\. (.+)$", text, re.M)]


class ExtractFewshotTest(unittest.TestCase):
    def setUp(self):
        self.text = read("extract_fewshot.md")
        self.factors = example_output(self.text)["factors"]

    def test_factors_follow_output_schema(self):
        # 실제 응답 검사에 쓰는 스키마의 factors 항목 그대로
        item_schema = OUTPUT_SCHEMA["properties"]["factors"]["items"]
        for i, factor in enumerate(self.factors, 1):
            self.assertEqual(validate_against_schema(factor, item_schema, f"$.factors[{i}]"), [], factor["label"])

    def test_lengths_and_pre_label_rule(self):
        for factor in self.factors:
            self.assertLessEqual(len(factor["label"]), LABEL_MAX_LENGTH)
            self.assertLessEqual(len(factor["summaryTag"]), SUMMARY_TAG_MAX_LENGTH)
            # preLabel은 OVERVIEW 요소에만 (extract_system.md)
            if factor["revealStage"] == "OVERVIEW":
                self.assertTrue(factor["preLabel"])
                self.assertLessEqual(len(factor["preLabel"]), LABEL_MAX_LENGTH)
            else:
                self.assertIsNone(factor["preLabel"])
        overview = [f for f in self.factors if f["revealStage"] == "OVERVIEW"]
        self.assertTrue(2 <= len(overview) <= 4)  # OVERVIEW 2 ~ 4개 규칙

    def test_value_axis_matches_summary_tag(self):
        # 예시가 가치관 축 경고(요약어 키워드와 축이 어긋남)를 내면 안 된다
        for i, factor in enumerate(self.factors, 1):
            self.assertEqual(check_value_axis(i, factor), [])

    def test_no_masked_values_or_personal_info_in_output(self):
        for factor in self.factors:
            for key in ("label", "preLabel", "summaryTag"):
                value = factor[key] or ""
                self.assertNotRegex(value, r"\[[^\]]+\]")  # [주소] 같은 미리 가린 표시를 옮기지 않는다
                self.assertNotRegex(value, r"\d{4}\.\s*\d{1,2}\.|\d{1,2}:\d{2}")  # 정확한 날짜 · 시각을 쓰지 않는다
                self.assertNotIn("B", value)  # 판결문 가명 B를 그대로 옮기지 않는다 (피고인은 A씨)
        errors, _ = residual_check([(f"factors[{i}]", f["label"]) for i, f in enumerate(self.factors)])
        self.assertEqual(errors, [])

    def test_example_is_appended_to_system_prompt(self):
        system, user = extract_messages("판결문")
        self.assertTrue(system.startswith(read("extract_system.md").rstrip()))
        self.assertIn(self.text.strip(), system)
        self.assertNotIn("extract_fewshot", user)

    def test_example_input_looks_like_real_masked_text(self):
        # 실제 입력처럼: 가린 표시는 deidentify가 실제로 만드는 것만 쓰고, 날짜는 원래 모습으로 둔다(모델이 바꿔야 함)
        from court_draft import MASK_TOKENS
        source = example_source(self.text)
        for token in re.findall(r"\[[^\]]+\]", source):
            self.assertIn(token, MASK_TOKENS)
        self.assertRegex(source, r"\d{4}\. \d{1,2}\. \d{1,2}\.")


class CourtFewshotTest(unittest.TestCase):
    def setUp(self):
        self.text = read("court_draft_fewshot.md")
        self.source = example_source(self.text)
        self.draft = example_output(self.text)
        self.case = {"factors": example_factor_list(self.text)}

    def test_example_passes_real_draft_check(self):
        # 실제 초안 검사 그대로: 스키마 · 목록 번호 · 원문 인용(⟦ ⟧ 규칙) · 평가 표현 · 남은 개인정보
        errors, warnings = check_draft(self.draft, self.case, self.source)
        self.assertEqual(errors, [])
        self.assertEqual(warnings, [])

    def test_example_shows_replacement_mark(self):
        # 인용 규칙에서 가장 중요한 ⟦ ⟧ 사용을 예시가 실제로 보여 준다
        self.assertTrue(any("⟦" in f["evidence"] for f in self.draft["factors"]))

    def test_example_skips_factor_the_court_did_not_mention(self):
        # 재판부가 언급하지 않은 요소(7, 변호인 주장)는 고르지 않는다 (court_draft_system.md 원칙 2)
        listed = {f["factorId"] for f in self.case["factors"]}
        chosen = {f["factorId"] for f in self.draft["factors"]}
        self.assertEqual(listed - chosen, {7})

    def test_example_matches_extract_example_case(self):
        # 두 예시는 같은 가상 사건이다: court 요소 목록 = extract 예시의 label 순서
        extract_labels = [f["label"] for f in example_output(read("extract_fewshot.md"))["factors"]]
        court_labels = [f["label"] for f in self.case["factors"]]
        self.assertEqual(sorted(court_labels), sorted(extract_labels))
        self.assertEqual(example_source(read("extract_fewshot.md")), self.source)

    def test_example_is_appended_to_system_prompt(self):
        case = {"factors": [{"factorId": 1, "label": "요소"}]}
        system, _ = court_messages(case, {"penaltyType": "PRISON"}, "판결문")
        self.assertTrue(system.startswith(read("court_draft_system.md").rstrip()))
        self.assertIn(self.text.strip(), system)


if __name__ == "__main__":
    unittest.main()
