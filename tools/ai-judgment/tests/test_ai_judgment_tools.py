"""AI 판결 오프라인 도구 테스트. 실행: tools/ai-judgment에서 `python3 -m unittest discover tests`"""

import copy
import sys
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

from build_prompt import InputError, build_prompt  # noqa: E402
from check_contamination import build_contamination_prompt, judge, judge_one  # noqa: E402
from common import final_penalty, format_months, format_penalty_range, format_won, load_json  # noqa: E402
from to_seed_sql import build_sql  # noqa: E402
from validate_output import parse_output, validate  # noqa: E402

EXAMPLES = TOOL_DIR / "examples"

# 벌금 규칙 검사용. 살인은 법정형에 벌금이 없어 예시 사건에 FINE 행을 붙여 쓴다.
FINE_RULE = {"penaltyType": "FINE", "statutoryMin": None, "statutoryMax": 20_000_000,
             "allowedMin": 25_000, "allowedMax": 20_000_000, "suspensionAllowed": True}


class Fixtures(unittest.TestCase):
    def setUp(self):
        self.case = load_json(EXAMPLES / "case_input.json")
        self.output = load_json(EXAMPLES / "ai_output_sample.json")
        self.court = load_json(EXAMPLES / "court_judgment_internal.json")

    def with_output(self, **changes):
        output = copy.deepcopy(self.output)
        output.update(changes)
        return output

    def case_with_fine(self):
        case = copy.deepcopy(self.case)
        case["penaltyRules"].append(copy.deepcopy(FINE_RULE))
        return case

    def errors_contain(self, report, text):
        return any(text in e for e in report.errors)


class FormatTest(unittest.TestCase):
    def test_format_months(self):
        self.assertEqual(format_months(1), "1개월")
        self.assertEqual(format_months(30), "2년 6개월")
        self.assertEqual(format_months(600), "50년")

    def test_format_won(self):
        self.assertEqual(format_won(25_000), "2만 5천 원")
        self.assertEqual(format_won(20_000_000), "2천만 원")
        self.assertEqual(format_won(5_000_000), "500만 원")

    def test_format_penalty_range(self):
        self.assertEqual(format_penalty_range({"penaltyType": "PRISON", "allowedMin": 30, "allowedMax": 360}),
                         "징역 2년 6개월 ~ 30년")
        self.assertEqual(format_penalty_range({"penaltyType": "LIFE", "allowedMin": 120, "allowedMax": 600}),
                         "무기징역, 또는 감경 시 징역 10년 ~ 50년")
        self.assertEqual(format_penalty_range({"penaltyType": "DEATH", "allowedMin": 240, "allowedMax": 600}),
                         "사형, 또는 감경 시 무기징역이나 징역 20년 ~ 50년")

    def test_final_penalty(self):
        self.assertEqual(final_penalty({"penaltyType": "LIFE", "reducedTo": "PRISON"}), "PRISON")
        self.assertEqual(final_penalty({"penaltyType": "LIFE", "reducedTo": None}), "LIFE")
        self.assertEqual(final_penalty({"penaltyType": "PRISON"}), "PRISON")


class BuildPromptTest(Fixtures):
    def test_prompt_contains_case_and_fixed_values(self):
        prompt = build_prompt(self.case)
        self.assertIn("빌린 돈 문제로 찾아온 지인을 살해한 사건", prompt["user"])
        self.assertIn("형법 제250조 제1항 살인 (고정값)", prompt["user"])
        self.assertIn("징역 2년 6개월 ~ 30년", prompt["user"])
        self.assertIn("11. 피고인은 오랜 채무로 정신적으로 지쳐 있었다고 주장한다", prompt["user"])
        self.assertEqual(prompt["promptVersion"], "judgment-v2")

    def test_prompt_explains_death_and_life(self):
        prompt = build_prompt(self.case)
        self.assertIn("`LIFE` (무기징역): 무기징역, 또는 감경 시 징역 10년 ~ 50년, 집행유예 불가, 감경하면 `PRISON`(징역)로 선고 가능",
                      prompt["user"])
        self.assertIn("감경하면 `LIFE`(무기징역) · `PRISON`(징역)로 선고 가능", prompt["user"])
        self.assertIn("reducedTo", prompt["system"])

    def test_prompt_uses_only_whitelisted_fields(self):
        # 입력 파일에 정의되지 않은 항목을 추가해도 프롬프트에 들어가지 않는다 (화이트리스트)
        case = copy.deepcopy(self.case)
        case["internalMemo"] = "UNIQUE-MARKER-내부 메모"
        prompt = build_prompt(case)
        self.assertNotIn("UNIQUE-MARKER", prompt["user"])
        self.assertNotIn("UNIQUE-MARKER", prompt["system"])

    def test_rejects_forbidden_keys(self):
        case = copy.deepcopy(self.case)
        case["courtJudgment"] = self.court
        with self.assertRaises(InputError):
            build_prompt(case)

    def test_rejects_nested_forbidden_keys(self):
        case = copy.deepcopy(self.case)
        case["references"]["caseNumber"] = "2023고합0000"
        with self.assertRaises(InputError):
            build_prompt(case)

    def test_rejects_missing_allowed_range(self):
        case = copy.deepcopy(self.case)
        del case["penaltyRules"][0]["allowedMax"]
        with self.assertRaises(InputError):
            build_prompt(case)

    def test_rejects_suspension_on_life(self):
        case = copy.deepcopy(self.case)
        case["penaltyRules"][1]["suspensionAllowed"] = True
        with self.assertRaises(InputError):
            build_prompt(case)

    def test_renders_law_term_section(self):
        # 용어 설명(LAW_TERM) 섹션은 data가 term/desc다 (API 6 terms, case_section LAW_TERM)
        case = copy.deepcopy(self.case)
        case["sections"].append({"stage": "LAW", "sectionType": "LAW_TERM", "title": "용어 설명",
                                 "data": [{"term": "작량감경", "desc": "재판상 감경"}]})
        prompt = build_prompt(case)
        self.assertIn("- 작량감경: 재판상 감경", prompt["user"])

    def test_rejects_unknown_data_item(self):
        case = copy.deepcopy(self.case)
        case["sections"][1]["data"].append({"name": "x"})
        with self.assertRaises(InputError):
            build_prompt(case)

    def test_rejects_unknown_penalty_type(self):
        case = copy.deepcopy(self.case)
        case["penaltyRules"].append({"penaltyType": "NOT_GUILTY", "allowedMin": 0, "allowedMax": 0})
        with self.assertRaises(InputError):
            build_prompt(case)


class ValidatePrisonTest(Fixtures):
    def test_sample_output_passes(self):
        report = validate(self.case, self.output)
        self.assertEqual(report.errors, [])
        self.assertEqual(report.warnings, [])

    def test_out_of_allowed_range(self):
        report = validate(self.case, self.with_output(prisonMonths=372))
        self.assertTrue(self.errors_contain(report, "선고할 수 있는 범위"))

    def test_disallowed_penalty_type(self):
        report = validate(self.case, self.with_output(penaltyType="NOT_GUILTY"))
        self.assertFalse(report.ok)

    def test_penalty_type_not_in_case(self):
        report = validate(self.case, self.with_output(penaltyType="FINE", prisonMonths=None, fineAmount=5_000_000))
        self.assertTrue(self.errors_contain(report, "허용되지 않은 형벌"))

    def test_prison_cannot_be_reduced(self):
        report = validate(self.case, self.with_output(reducedTo="PRISON"))
        self.assertTrue(self.errors_contain(report, "감경할 수 없습니다"))

    def test_suspension_over_three_years_prison(self):
        report = validate(self.case, self.with_output(prisonMonths=48, suspensionMonths=36))
        self.assertTrue(self.errors_contain(report, "집행유예를 붙일 수 없습니다"))

    def test_suspension_period_range(self):
        report = validate(self.case, self.with_output(prisonMonths=36, suspensionMonths=6))
        self.assertTrue(self.errors_contain(report, "12 ~ 60개월"))

    def test_suspension_within_three_years_passes(self):
        report = validate(self.case, self.with_output(prisonMonths=36, suspensionMonths=48))
        self.assertTrue(report.ok, report.errors)

    def test_outside_recommended_is_warning_only(self):
        report = validate(self.case, self.with_output(prisonMonths=180))
        self.assertTrue(report.ok)
        self.assertTrue(any("권고 범위" in w for w in report.warnings))


class ValidateDeathLifeTest(Fixtures):
    def test_life_as_is(self):
        report = validate(self.case, self.with_output(penaltyType="LIFE", reducedTo=None, prisonMonths=None))
        self.assertTrue(report.ok, report.errors)
        self.assertTrue(any("무기징역으로 권고 범위" in w for w in report.warnings))

    def test_death_as_is(self):
        report = validate(self.case, self.with_output(penaltyType="DEATH", reducedTo=None, prisonMonths=None))
        self.assertTrue(report.ok, report.errors)

    def test_life_as_is_must_not_have_months(self):
        report = validate(self.case, self.with_output(penaltyType="LIFE", reducedTo=None, prisonMonths=180))
        self.assertTrue(self.errors_contain(report, "prisonMonths는 null"))

    def test_life_reduced_to_prison_uses_life_range(self):
        # 무기징역을 감경해 징역으로: LIFE 행 120 ~ 600으로 검사한다 (ERD 6장)
        ok = validate(self.case, self.with_output(penaltyType="LIFE", reducedTo="PRISON", prisonMonths=600))
        self.assertTrue(ok.ok, ok.errors)
        too_low = validate(self.case, self.with_output(penaltyType="LIFE", reducedTo="PRISON", prisonMonths=100))
        self.assertTrue(self.errors_contain(too_low, "LIFE의 선고할 수 있는 범위"))

    def test_life_reduced_to_prison_needs_months(self):
        report = validate(self.case, self.with_output(penaltyType="LIFE", reducedTo="PRISON", prisonMonths=None))
        self.assertTrue(self.errors_contain(report, "prisonMonths는 정수"))

    def test_death_reduced_to_life(self):
        report = validate(self.case, self.with_output(penaltyType="DEATH", reducedTo="LIFE", prisonMonths=None))
        self.assertTrue(report.ok, report.errors)

    def test_death_reduced_to_prison_uses_death_range(self):
        ok = validate(self.case, self.with_output(penaltyType="DEATH", reducedTo="PRISON", prisonMonths=240))
        self.assertTrue(ok.ok, ok.errors)
        too_low = validate(self.case, self.with_output(penaltyType="DEATH", reducedTo="PRISON", prisonMonths=200))
        self.assertTrue(self.errors_contain(too_low, "DEATH의 선고할 수 있는 범위"))

    def test_life_cannot_be_reduced_to_death(self):
        report = validate(self.case, self.with_output(penaltyType="LIFE", reducedTo="DEATH", prisonMonths=None))
        self.assertTrue(self.errors_contain(report, "감경할 수 없습니다"))

    def test_no_suspension_even_when_reduced(self):
        report = validate(self.case, self.with_output(penaltyType="LIFE", reducedTo="PRISON", prisonMonths=120,
                                                      suspensionMonths=36))
        self.assertTrue(self.errors_contain(report, "감경하더라도 집행유예"))


class ValidateFineTest(Fixtures):
    def test_fine_passes(self):
        output = self.with_output(penaltyType="FINE", prisonMonths=None, fineAmount=5_000_000, suspensionMonths=24)
        report = validate(self.case_with_fine(), output)
        self.assertTrue(report.ok, report.errors)

    def test_fine_needs_prison_null(self):
        output = self.with_output(penaltyType="FINE", fineAmount=5_000_000)
        report = validate(self.case_with_fine(), output)
        self.assertTrue(self.errors_contain(report, "prisonMonths는 null"))

    def test_fine_suspension_over_limit(self):
        output = self.with_output(penaltyType="FINE", prisonMonths=None, fineAmount=6_000_000, suspensionMonths=24)
        report = validate(self.case_with_fine(), output)
        self.assertTrue(self.errors_contain(report, "500만 원 이하만"))


class ValidateFactorsAndTextsTest(Fixtures):
    def test_unknown_factor(self):
        factors = self.output["factors"] + [{"factorId": 99, "direction": "UP", "reason": "x"}]
        report = validate(self.case, self.with_output(factors=factors))
        self.assertTrue(self.errors_contain(report, "목록에 없는 판단 요소"))

    def test_duplicate_factor_and_bad_direction(self):
        factors = [{"factorId": 2, "direction": "UP", "reason": "a"}, {"factorId": 2, "direction": "UPWARD", "reason": "b"}]
        report = validate(self.case, self.with_output(factors=factors))
        self.assertTrue(self.errors_contain(report, "중복"))
        self.assertTrue(self.errors_contain(report, "UP / DOWN"))

    def test_forbidden_expression(self):
        report = validate(self.case, self.with_output(summary="이것이 정답인 판단"))
        self.assertTrue(self.errors_contain(report, "평가 표현"))

    def test_summary_too_long(self):
        report = validate(self.case, self.with_output(summary="가" * 101))
        self.assertTrue(self.errors_contain(report, "100자"))

    def test_missing_reduced_to_key(self):
        output = copy.deepcopy(self.output)
        del output["reducedTo"]
        report = validate(self.case, output)
        self.assertTrue(self.errors_contain(report, "reducedTo"))

    def test_non_string_reason_is_error(self):
        factors = [{"factorId": 2, "direction": "UP", "reason": 123}]
        report = validate(self.case, self.with_output(factors=factors))
        self.assertTrue(self.errors_contain(report, "reason은 문자열"))

    def test_malformed_types_do_not_crash(self):
        # 형식이 틀려도 예외 없이 오류 목록으로 돌려준다
        report = validate(self.case, self.with_output(factors=None, reasoning=123))
        self.assertTrue(self.errors_contain(report, "factors는 배열"))
        self.assertTrue(self.errors_contain(report, "reasoning"))

    def test_empty_reference_tags_is_warning(self):
        report = validate(self.case, self.with_output(referenceTags=[]))
        self.assertTrue(report.ok)
        self.assertTrue(any("referenceTags가 비어" in w for w in report.warnings))

    def test_unknown_number_is_warning(self):
        report = validate(self.case, self.with_output(reasoning="피해 금액 9,000만 원을 고려했다."))
        self.assertTrue(any("9,000만 원" in w for w in report.warnings))

    def test_parse_output_accepts_code_fence(self):
        parsed = parse_output('```json\n{"a": 1}\n```')
        self.assertEqual(parsed, {"a": 1})


class ContaminationTest(Fixtures):
    def prediction(self, **values):
        base = {"knowsCase": False, "penaltyType": "PRISON", "prisonMonths": 180, "fineAmount": None,
                "suspensionMonths": None, "confidence": 0.3, "note": ""}
        base.update(values)
        return base

    def test_prompt_has_only_overview(self):
        prompt = build_contamination_prompt(self.case)
        self.assertIn(self.case["overview"], prompt)
        self.assertNotIn("5,000만 원을 공탁", prompt)
        self.assertIn("DEATH(사형)", prompt)

    def test_knows_case(self):
        verdict, _ = judge_one(self.court, self.prediction(knowsCase=True))
        self.assertEqual(verdict, "CONTAMINATED")

    def test_knows_case_as_string(self):
        verdict, _ = judge_one(self.court, self.prediction(knowsCase="true"))
        self.assertEqual(verdict, "CONTAMINATED")

    def test_knows_case_not_boolean_is_suspect(self):
        verdict, _ = judge_one(self.court, self.prediction(knowsCase="모름"))
        self.assertEqual(verdict, "SUSPECT")

    def test_exact_match(self):
        verdict, _ = judge_one(self.court, self.prediction(prisonMonths=120))
        self.assertEqual(verdict, "CONTAMINATED")

    def test_close_match_is_suspect(self):
        verdict, _ = judge_one(self.court, self.prediction(prisonMonths=122))
        self.assertEqual(verdict, "SUSPECT")

    def test_different_is_clean(self):
        verdict, _ = judge_one(self.court, self.prediction(prisonMonths=180))
        self.assertEqual(verdict, "CLEAN")

    def test_different_penalty_is_clean(self):
        verdict, _ = judge_one(self.court, self.prediction(penaltyType="LIFE", prisonMonths=None))
        self.assertEqual(verdict, "CLEAN")

    def test_same_life_is_suspect(self):
        court = dict(self.court, penaltyType="LIFE", prisonMonths=None)
        verdict, _ = judge_one(court, self.prediction(penaltyType="LIFE", prisonMonths=None))
        self.assertEqual(verdict, "SUSPECT")

    def test_court_reduced_to_prison_compares_final(self):
        # 실제 판결: 무기징역을 감경해 징역 15년 → 최종 선고 형벌 PRISON 180으로 비교한다
        court = dict(self.court, penaltyType="LIFE", reducedTo="PRISON", prisonMonths=180)
        verdict, _ = judge_one(court, self.prediction(penaltyType="PRISON", prisonMonths=180))
        self.assertEqual(verdict, "CONTAMINATED")

    def test_final_verdict_is_worst(self):
        final, _ = judge(self.court, [self.prediction(prisonMonths=180), self.prediction(knowsCase=True)])
        self.assertEqual(final, "CONTAMINATED")


class SeedSqlTest(Fixtures):
    def test_sql_shape(self):
        sql = build_sql(self.case, self.output, build_prompt(self.case), "model-x", "백승호")
        self.assertIn("SELECT id INTO v_case_id FROM legal_case WHERE title =", sql)
        self.assertIn("SET is_published = false", sql)
        self.assertIn("penalty_type, reduced_to, prison_months", sql)
        self.assertIn("'PRISON', NULL, 144", sql)
        self.assertIn("INSERT INTO judgment_factor", sql)
        self.assertIn("(2, 'UP')", sql)
        self.assertIn("'APPROVED', '백승호'", sql)
        self.assertIn("'judgment-v2'", sql)
        # 기본 출력은 psql에 바로 붙여 넣을 수 있게 BEGIN/COMMIT으로 감싼다
        self.assertTrue(sql.startswith("BEGIN;\n"))
        self.assertIn("\nCOMMIT;\n", sql)

    def test_sql_with_reduced_to(self):
        output = self.with_output(penaltyType="LIFE", reducedTo="PRISON", prisonMonths=180)
        sql = build_sql(self.case, output, build_prompt(self.case), "m", "r")
        self.assertIn("'LIFE', 'PRISON', 180", sql)

    def test_sql_checks_factor_ownership(self):
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r")
        self.assertIn(
            "FROM factor WHERE case_id = v_case_id AND display_order IN (2, 3, 5, 7, 8, 9)) <> 6", sql
        )
        self.assertIn("RAISE EXCEPTION", sql)

    def test_sql_looks_up_case_by_title_not_literal_id(self):
        # 환경마다 legal_case.id가 달라도 같은 SQL을 쓸 수 있어야 한다 (숫자 id를 그대로 심지 않는다)
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r")
        self.assertNotIn("caseId=", sql)
        self.assertIn(f"title = '{self.case['title']}'", sql)

    def test_sql_comment_title_has_no_newline(self):
        case = copy.deepcopy(self.case)
        case["title"] = "제목\nDROP TABLE judgment;"
        sql = build_sql(case, self.output, build_prompt(case), "m", "r")
        body_first_line = sql.splitlines()[1]  # [0]은 BEGIN;
        self.assertTrue(body_first_line.startswith("-- AI 판결 적재: 제목 DROP TABLE judgment;"))

    def test_sql_escapes_quotes(self):
        output = self.with_output(reasoning="피고인의 '반성'을 고려했다.")
        sql = build_sql(self.case, output, build_prompt(self.case), "m", "r")
        self.assertIn("''반성''", sql)

    def test_sql_flyway_omits_begin_commit(self):
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r", flyway=True)
        self.assertNotIn("BEGIN;", sql)
        self.assertNotIn("COMMIT;", sql)
        self.assertTrue(sql.startswith("-- AI 판결 적재:"))
        self.assertIn("DO $$", sql)


if __name__ == "__main__":
    unittest.main()
