"""AI 판결 오프라인 도구 테스트. 실행: tools/ai-judgment에서 `python3 -m unittest discover tests`"""

import copy
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

from build_prompt import InputError, build_prompt  # noqa: E402
from check_contamination import (  # noqa: E402
    aggregate, build_contamination_prompt, classify, criteria_with, judge, prison_tolerance,
)
from common import factors_by_id, final_penalty, format_months, format_penalty_range, format_won, load_json  # noqa: E402
from to_seed_sql import build_sql, check_factor_label_drift, main as to_seed_sql_main, parse_reviewed_at  # noqa: E402
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

    def category(self, court=None, **values):
        return classify(court or self.court, self.prediction(**values))[0]

    def test_classify_knows(self):
        self.assertEqual(self.category(knowsCase=True), "KNOWS")
        self.assertEqual(self.category(knowsCase="true"), "KNOWS")
        self.assertEqual(self.category(knowsCase="모름"), "INVALID")
        self.assertEqual(classify(self.court, ["not", "object"])[0], "INVALID")

    def test_classify_exact_close_far(self):
        # 실제 120개월: 허용 오차 max(1, min(6, 18)) = 6개월
        self.assertEqual(self.category(prisonMonths=120), "EXACT")
        self.assertEqual(self.category(prisonMonths=126), "CLOSE")
        self.assertEqual(self.category(prisonMonths=127), "FAR")
        self.assertEqual(self.category(prisonMonths=None), "FAR")
        self.assertEqual(self.category(prisonMonths=120, suspensionMonths=24), "FAR")  # 집행유예 여부가 다름
        self.assertEqual(self.category(penaltyType="LIFE", prisonMonths=None), "FAR")

    def test_prison_tolerance_scales_with_term(self):
        criteria = criteria_with()
        self.assertEqual(prison_tolerance(4, criteria), 1)      # 짧은 형량: 최소 1개월 (예전 ±2개월은 50%였다)
        self.assertEqual(prison_tolerance(20, criteria), 3)     # 15%
        self.assertEqual(prison_tolerance(240, criteria), 6)    # 긴 형량: 최대 6개월
        short = dict(self.court, prisonMonths=4)
        self.assertEqual(self.category(short, prisonMonths=6), "FAR")
        self.assertEqual(self.category(short, prisonMonths=5), "CLOSE")

    def test_classify_life_and_reduced(self):
        life = dict(self.court, penaltyType="LIFE", prisonMonths=None)
        self.assertEqual(self.category(life, penaltyType="LIFE", prisonMonths=None), "SAME_TYPE")
        # 실제 판결: 무기징역을 감경해 징역 15년 → 최종 선고 형벌 PRISON 180으로 비교한다
        reduced = dict(self.court, penaltyType="LIFE", reducedTo="PRISON", prisonMonths=180)
        self.assertEqual(self.category(reduced, penaltyType="PRISON", prisonMonths=180), "EXACT")

    def test_aggregate_rules(self):
        cases = [
            (["KNOWS"], "CONTAMINATED"),                                         # 안다고 답하면 적어도 확정
            (["EXACT", "EXACT"], "INSUFFICIENT"),                               # 응답 2개 < 최소 5개
            (["EXACT"] * 3 + ["FAR"] * 2, "CONTAMINATED"),                      # 정확히 맞힘 60%
            (["EXACT"] + ["FAR"] * 4, "SUSPECT"),                               # 정확히 맞힘 1개 (우연 가능)
            (["CLOSE"] * 3 + ["FAR"] * 2, "SUSPECT"),                           # 가까움 60%
            (["CLOSE"] * 2 + ["FAR"] * 3, "CLEAN"),                             # 가까움 40%
            (["SAME_TYPE"] * 3 + ["FAR"] * 2, "SUSPECT"),
            (["INVALID"] + ["FAR"] * 4, "SUSPECT"),
            (["FAR"] * 10, "CLEAN"),
        ]
        for categories, expected in cases:
            verdict, reason, counts = aggregate(categories)
            self.assertEqual(verdict, expected, categories)
            self.assertEqual(sum(counts.values()), len(categories))

    def test_aggregate_criteria_override(self):
        self.assertEqual(aggregate(["EXACT"] * 2, {"minAnswered": 2})[0], "CONTAMINATED")
        self.assertEqual(aggregate(["CLOSE"] * 2 + ["FAR"] * 3, {"suspectRatio": 0.4})[0], "SUSPECT")

    def test_judge_reason_has_no_predicted_values(self):
        predictions = [self.prediction(prisonMonths=m) for m in (125, 116, 133, 200, 90)]
        verdict, results, reason, counts = judge(self.court, predictions)
        self.assertEqual(verdict, "CLEAN")   # 가까움 2/5 < 50%
        self.assertEqual([c for c, _ in results], ["CLOSE", "CLOSE", "FAR", "FAR", "FAR"])
        for value in ("125", "116", "133", "200", "120"):
            self.assertNotIn(value, reason)
            self.assertFalse(any(value in why for _, why in results))


class SeedSqlTest(Fixtures):
    def test_sql_shape(self):
        sql = build_sql(self.case, self.output, build_prompt(self.case), "model-x", "백승호")
        self.assertIn("SELECT id INTO STRICT v_case_id FROM legal_case WHERE title =", sql)
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

    def test_sql_title_lookup_is_strict_with_distinct_exceptions(self):
        # 제목 중복(TOO_MANY_ROWS)과 제목 없음(NO_DATA_FOUND)을 각각 다른 메시지로 멈춘다
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r")
        self.assertIn("WHEN NO_DATA_FOUND THEN", sql)
        self.assertIn("찾을 수 없습니다", sql)
        self.assertIn("WHEN TOO_MANY_ROWS THEN", sql)
        self.assertIn("유일하지 않습니다", sql)

    def test_sql_checks_factor_label_not_just_order(self):
        # display_order뿐 아니라 label까지 맞아야 통과한다 (사건 파일 · 시드의 요소 순서가 어긋나면 멈춤)
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r")
        self.assertIn("(2, '다투던 중 집에 있던 흉기를 집어 들었다')", sql)
        self.assertIn("LEFT JOIN factor f ON f.case_id = v_case_id AND f.display_order = v.display_order", sql)
        self.assertIn("WHERE f.id IS NULL OR f.label IS DISTINCT FROM v.label", sql)

    def test_sql_factor_check_handles_null_label(self):
        # label이 NULL인 요소도 DB 라벨과 다르면 걸러져야 한다 (NULL과의 <> 비교는 UNKNOWN이라 통과해 버림)
        case = copy.deepcopy(self.case)
        for f in case["factors"]:
            if f["factorId"] == 2:
                f["label"] = None
        sql = build_sql(case, self.output, build_prompt(case), "m", "r")
        self.assertIn("(2, NULL)", sql)
        self.assertIn("IS DISTINCT FROM", sql)
        self.assertIn("RAISE EXCEPTION", sql)

    def test_sql_looks_up_case_by_title_not_literal_id(self):
        # 환경마다 legal_case.id가 달라도 같은 SQL을 쓸 수 있어야 한다 (숫자 id를 그대로 심지 않는다)
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r")
        self.assertNotIn("caseId=", sql)
        self.assertIn(f"title = '{self.case['title']}'", sql)

    def test_sql_comment_title_has_no_newline_but_lookup_uses_raw_title(self):
        case = copy.deepcopy(self.case)
        case["title"] = "제목\nDROP TABLE judgment;"
        sql = build_sql(case, self.output, build_prompt(case), "m", "r")
        body_first_line = sql.splitlines()[1]  # [0]은 BEGIN;
        self.assertTrue(body_first_line.startswith("-- AI 판결 적재: 제목 DROP TABLE judgment;"))
        # 조회 · 오류 메시지에는 정규화하지 않은 원본 제목을 그대로 써서 시드의 title과 비교된다
        self.assertIn("WHERE title = '제목\nDROP TABLE judgment;'", sql)

    def test_sql_escapes_quotes(self):
        output = self.with_output(reasoning="피고인의 '반성'을 고려했다.")
        sql = build_sql(self.case, output, build_prompt(self.case), "m", "r")
        self.assertIn("''반성''", sql)

    def test_sql_reviewed_at_fixed_when_given(self):
        # 검수 시각을 주면 고정값, 적재 시각(created_at)은 now() 그대로
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r",
                        reviewed_at=parse_reviewed_at("2026-10-06T00:00:00+09:00"))
        self.assertIn("'APPROVED', 'r', TIMESTAMPTZ '2026-10-06 00:00:00+09:00', now()", sql)

    def test_sql_reviewed_at_defaults_to_now(self):
        # 기존 사용법(옵션 없음)은 그대로 now()
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r")
        self.assertIn("'APPROVED', 'r', now(), now()", sql)

    def write_temp_json(self, data):
        tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False, encoding="utf-8")
        json.dump(data, tmp)
        tmp.close()
        self.addCleanup(lambda: Path(tmp.name).unlink(missing_ok=True))
        return tmp.name

    def label_snapshot(self, label_changes=None, **top_changes):
        snapshot = {
            "caseTitle": self.case["title"],
            "labels": {str(fid): f["label"] for fid, f in factors_by_id(self.case).items()},
        }
        snapshot["labels"].update(label_changes or {})
        snapshot.update(top_changes)
        return snapshot

    def test_factor_label_drift_skipped_without_snapshot(self):
        # --factor-labels를 주지 않으면 기존 동작 그대로 건너뛴다
        self.assertEqual(check_factor_label_drift(self.case, self.output, None), [])

    def test_factor_label_drift_passes_when_labels_match(self):
        path = self.write_temp_json(self.label_snapshot())
        self.assertEqual(check_factor_label_drift(self.case, self.output, path), [])

    def test_factor_label_drift_detects_changed_label(self):
        # 사건 파일과 DB가 함께 바뀌어 서로는 맞아떨어져도, 이 출력이 생성될 때의 라벨과
        # 다르면(판단 요소 구성이 바뀐 뒤의 예전 출력) 잡아내야 한다
        path = self.write_temp_json(self.label_snapshot({"2": "예전에는 다른 뜻이었던 요소"}))
        errors = check_factor_label_drift(self.case, self.output, path)
        self.assertTrue(any("factorId=2" in e for e in errors))
        self.assertTrue(any("예전에는 다른 뜻이었던 요소" in e for e in errors))

    def test_factor_label_drift_ignores_unselected_factors(self):
        # 이 출력이 고르지 않은 요소의 라벨이 바뀐 건 상관없다(출력이 참조하지 않으므로)
        path = self.write_temp_json(self.label_snapshot({"99": "출력에 없는 요소"}))
        self.assertEqual(check_factor_label_drift(self.case, self.output, path), [])

    def test_factor_label_drift_detects_removed_factor(self):
        # 이 출력이 고른 요소가 지금 사건 파일에서 아예 삭제됐으면 current_label이 None이라 역시 다르게 잡힌다
        case = copy.deepcopy(self.case)
        case["factors"] = [f for f in case["factors"] if f["factorId"] != 2]
        path = self.write_temp_json(self.label_snapshot())
        errors = check_factor_label_drift(case, self.output, path)
        self.assertTrue(any("factorId=2" in e and "None" in e for e in errors))

    def test_factor_label_drift_rejects_selected_factor_missing_in_snapshot(self):
        # 출력이 고른 요소는 생성 시점에 반드시 있었다 → 스냅샷에 없으면 다른 회차 · 사건의
        # 스냅샷을 잘못 지정한 것이니 조용히 통과시키지 않는다
        selected = str(self.output["factors"][0]["factorId"])
        snapshot = self.label_snapshot()
        del snapshot["labels"][selected]
        path = self.write_temp_json(snapshot)
        errors = check_factor_label_drift(self.case, self.output, path)
        self.assertTrue(any(f"factorId={selected}" in e for e in errors))
        self.assertTrue(any("스냅샷에 없는 요소" in e for e in errors))

    def test_factor_label_drift_rejects_case_title_mismatch(self):
        path = self.write_temp_json(self.label_snapshot(caseTitle="다른 사건"))
        errors = check_factor_label_drift(self.case, self.output, path)
        self.assertTrue(any("사건" in e and "다릅니다" in e for e in errors))

    def test_factor_label_drift_rejects_malformed_snapshot_shape(self):
        path = self.write_temp_json({"caseTitle": self.case["title"]})  # labels가 없다
        errors = check_factor_label_drift(self.case, self.output, path)
        self.assertTrue(any("형식이 올바르지 않습니다" in e for e in errors))

    def test_factor_label_drift_rejects_unreadable_snapshot_file(self):
        missing = str(Path(tempfile.gettempdir()) / "does-not-exist-factor-labels.json")
        errors = check_factor_label_drift(self.case, self.output, missing)
        self.assertTrue(any("읽을 수 없습니다" in e for e in errors))

    def run_cli(self, *extra):
        """to_seed_sql main()을 예시 파일로 실행 → (종료 코드, stdout, stderr)."""
        argv = ["to_seed_sql.py", str(EXAMPLES / "case_input.json"), str(EXAMPLES / "ai_output_sample.json"),
                "--model-name", "m", "--reviewed-by", "r", "--accept-warnings", *extra]
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(sys, "argv", argv), redirect_stdout(out), redirect_stderr(err):
            code = to_seed_sql_main()
        return code, out.getvalue(), err.getvalue()

    def test_main_accepts_matching_factor_labels(self):
        path = self.write_temp_json(self.label_snapshot())
        code, out, err = self.run_cli("--factor-labels", path)
        self.assertEqual(code, 0)
        self.assertIn("INSERT INTO judgment_factor", out)

    def test_main_rejects_factor_label_drift(self):
        path = self.write_temp_json(self.label_snapshot({"2": "바뀐 라벨"}))
        code, out, err = self.run_cli("--factor-labels", path)
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertIn("factorId=2", err)
        self.assertIn("[중단] 판단 요소 라벨이 생성 시점과 달라", err)

    def test_main_rejects_invalid_reviewed_at(self):
        # 형식이 틀리면 SQL을 만들지 않고 종료 코드 1
        code, out, err = self.run_cli("--reviewed-at", "어제")
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertIn("[오류]", err)

    def test_main_flyway_requires_reviewed_at(self):
        # Flyway 파일은 환경마다 실행 시각이 달라 검수 시각을 반드시 고정한다
        code, out, err = self.run_cli("--flyway")
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertIn("--flyway에는 --reviewed-at이 필요합니다", err)
        code, out, _ = self.run_cli("--flyway", "--reviewed-at", "2026-10-06T00:00:00+09:00")
        self.assertEqual(code, 0)
        self.assertIn("TIMESTAMPTZ '2026-10-06 00:00:00+09:00'", out)

    def test_main_flyway_without_factor_labels_warns(self):
        # 필수는 아니지만, 드리프트 검사를 건너뛰고 있다는 걸 경고로 알린다
        code, out, err = self.run_cli("--flyway", "--reviewed-at", "2026-10-06T00:00:00+09:00")
        self.assertEqual(code, 0)
        self.assertIn("[경고] --factor-labels가 없어", err)

    def test_main_without_reviewed_at_warns_for_psql_mode(self):
        # 수동 psql용(기본)은 옵션 없이도 만들지만 now()가 들어간다고 경고한다
        code, out, err = self.run_cli()
        self.assertEqual(code, 0)
        self.assertIn("now(), now()", out)
        self.assertIn("[경고] --reviewed-at이 없어", err)

    def test_main_warns_future_reviewed_at(self):
        code, out, err = self.run_cli("--reviewed-at", "2999-01-01T00:00:00+09:00")
        self.assertEqual(code, 0)
        self.assertIn("현재보다 미래", err)

    def test_parse_reviewed_at_rejects_offset_out_of_range(self):
        # PostgreSQL이 받지 못하는 오프셋은 SQL을 만들기 전에 막는다. 실제 범위의 양 끝은 통과
        self.assertEqual(parse_reviewed_at("2026-10-06T00:00:00+14:00").utcoffset().total_seconds(), 14 * 3600)
        self.assertEqual(parse_reviewed_at("2026-10-06T00:00:00-12:00").utcoffset().total_seconds(), -12 * 3600)
        for bad in ("2026-10-06T00:00:00+16:00", "2026-10-06T00:00:00-13:00", "2026-10-06T00:00:00+14:01"):
            with self.subTest(bad=bad):
                with self.assertRaisesRegex(ValueError, "범위"):
                    parse_reviewed_at(bad)

    def test_parse_reviewed_at_requires_timezone_and_format(self):
        self.assertEqual(parse_reviewed_at("2026-10-06T09:30:00Z").utcoffset().total_seconds(), 0)
        for bad in ("2026-10-06T00:00:00", "2026-10-06 오전", "어제"):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    parse_reviewed_at(bad)

    def test_sql_flyway_omits_begin_commit(self):
        sql = build_sql(self.case, self.output, build_prompt(self.case), "m", "r", flyway=True)
        self.assertNotIn("BEGIN;", sql)
        self.assertNotIn("COMMIT;", sql)
        self.assertTrue(sql.startswith("-- AI 판결 적재:"))
        self.assertIn("DO $sql_seed$", sql)
        self.assertIn("$sql_seed$;", sql)

    def test_sql_dollar_quote_avoids_collision_in_embedded_text(self):
        # reasoning에 기본 구분자($sql_seed$)와 같은 문자열이 들어 있으면 블록이 조기 종료되지 않도록
        # 다른 구분자를 고른다
        output = self.with_output(reasoning="이상한 입력입니다 $sql_seed$ DROP TABLE judgment; $sql_seed$")
        sql = build_sql(self.case, output, build_prompt(self.case), "m", "r")
        self.assertIn("DO $sql_seed1$", sql)
        self.assertIn("$sql_seed1$;", sql)
        self.assertNotIn("DO $sql_seed$", sql)


if __name__ == "__main__":
    unittest.main()
