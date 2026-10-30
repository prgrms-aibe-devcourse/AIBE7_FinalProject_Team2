"""판결 체험 대본 — 검수 오류 고쳐 쓰기 (BE-122 2차) 테스트. 모델은 호출하지 않는다(가짜 함수).

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import sys
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import llm  # noqa: E402
import script_assembler as sa  # noqa: E402
import script_repair as sp  # noqa: E402
import script_review as sr  # noqa: E402
from test_script_assembler import CASE, CAST, CLOSING, CLS, INTRO, JUDGMENT_OUTPUT, OPENING, FakeCaller  # noqa: E402
from test_script_review import generated  # noqa: E402


def reviewed(**kwargs):
    """검수까지 마친 생성 결과 (generate_units 반환값과 같은 모양)."""
    result = generated(**kwargs)
    result["review"] = sr.review_script(CASE, JUDGMENT_OUTPUT, result, strip_prefix=sa.strip_line_prefix)
    return result


def repair(result, responses, retries=2):
    caller = FakeCaller(responses)
    logs = []
    fixed = sp.repair_units(CASE, JUDGMENT_OUTPUT, CAST, CLS, result, model="openai:a", caller=caller, retries=retries,
                            max_retries=0, log=logs.append)
    return caller, fixed, logs


def errors(result):
    return [(i["unit"], i["path"], i["code"]) for i in result["review"] if i["severity"] == sr.ERROR]


BAD_DEFENSE = {**CLOSING, "defense": "변호인 측 최종변론입니다. 피고인은 3번이나 사과했습니다."}
GOOD_DEFENSE_TEXT = "변호인 측 최종변론입니다. 피고인은 여러 차례 사과했습니다."


class PointerTest(unittest.TestCase):
    def test_get_and_set_at(self):
        self.assertEqual(sp.get_at(OPENING, "/questions/1/q"), OPENING["questions"][1]["q"])
        patched = sp.set_at(OPENING, "/indict/1", "새 줄")
        self.assertEqual(patched["indict"], [OPENING["indict"][0], "새 줄"])
        self.assertEqual(OPENING["indict"][1], "적용 법조는 형법 제250조 제1항입니다.")  # 원본은 그대로


class LineRepairTest(unittest.TestCase):
    def test_rewrites_only_the_failing_line(self):
        result = reviewed(units={"U4": BAD_DEFENSE})
        self.assertEqual(errors(result), [("U4", "/defense", "NUMBER_NOT_IN_SOURCE")])
        caller, fixed, _ = repair(result, [{"text": GOOD_DEFENSE_TEXT}])
        self.assertEqual(errors(fixed), [])
        self.assertEqual(fixed["units"]["U4"]["value"], {**CLOSING, "defense": GOOD_DEFENSE_TEXT})
        self.assertEqual(fixed["repairs"], [{"unit": "U4", "path": "/defense", "codes": ["NUMBER_NOT_IN_SOURCE"],
                                             "action": "line", "attempts": 1, "ok": True}])
        self.assertEqual(result["units"]["U4"]["value"], BAD_DEFENSE)  # 넘긴 값은 바꾸지 않는다
        call = caller.calls[0]
        # 단위를 만들 때와 같은 블록(페르소나 · ROLE · 가드)과 사건 데이터에 지금 줄 · 고칠 점 · 앞 줄을 더한다
        self.assertEqual(call["system"], sa.closing_request(CASE, CLS, CAST, generated()["units"]["U0"]["value"],
                                                            OPENING)[0])
        self.assertIn("피고인은 3번이나 사과했습니다", call["user"])
        self.assertIn("입력에 없는 숫자 '3'", call["user"])
        self.assertIn(CLOSING["prosecutor"], call["user"])  # 앞 줄
        self.assertIn("구형", call["user"])  # U4는 구형 금지를 다시 넣는다
        self.assertTrue(call["json_output"])

    def test_rejected_rewrite_is_retried_with_new_problems(self):
        """고친 줄에 다른 오류가 있거나 형식(구형 금지 등)이 깨지면 받지 않고, 새 고칠 점으로 다시 요청한다."""
        still_bad = {"text": "변호인 측 최종변론입니다. 피고인은 5번이나 사과했습니다."}
        sentencing = {"text": "변호인 측 최종변론입니다. 징역 3년에 처해 주십시오."}
        caller, fixed, _ = repair(reviewed(units={"U4": BAD_DEFENSE}), [still_bad, sentencing, CLOSING], retries=2)
        # 두 번째 요청: 원래 줄 · 원래 고칠 점은 그대로, 받지 않은 줄과 그 이유를 더한다
        second = caller.calls[1]["user"]
        self.assertIn("3번이나", second)
        self.assertIn("입력에 없는 숫자 '3'", second)
        self.assertIn("5번이나 사과했습니다.\"도 받지 않았습니다", second)
        self.assertIn("입력에 없는 숫자 '5'", second)
        # 줄 2번 실패 → U4는 단위째 다시 만든다(한 번). 다시 만든 값이 깨끗하면 바꾼다
        self.assertEqual([(r["action"], r["attempts"], r["ok"]) for r in fixed["repairs"]],
                         [("line", 2, False), ("unit", 1, True)])
        self.assertEqual(fixed["units"]["U4"]["value"], CLOSING)
        self.assertEqual(errors(fixed), [])

    def test_u2_is_never_regenerated(self):
        """U2는 쟁점이 모두진술 · 신문과 한 호출이라 다시 만들면 U4까지 어긋난다 — 줄만 고치고, 끝내 남은 오류는 둔다."""
        bad = {**OPENING, "issues_narr": ["쟁점은 범행 경위입니다.", OPENING["issues_narr"][1]]}
        caller, fixed, _ = repair(reviewed(units={"U2": bad}), [{"text": "쟁점은 범행 경위입니다."}] * 2)
        self.assertEqual(len(caller.calls), 2)
        self.assertEqual([(r["unit"], r["action"], r["ok"]) for r in fixed["repairs"]], [("U2", "line", False)])
        self.assertEqual(errors(fixed), [("U2", "/issues_narr/0", "FORMAL_ENDING")])
        self.assertFalse(sa.build_script(CASE, CAST, CLS, fixed, model="openai:a")["publishable"])

    def test_fix_unit_regenerates_without_line_attempts(self):
        """번호 · 화자만 남은 U1 줄(LINE_MISSING, fix: unit)은 줄을 고쳐 쓰지 않고 U1을 다시 만든다."""
        empty = {"lines": [*INTRO["lines"][:4], "⑤남불:"]}
        caller, fixed, _ = repair(reviewed(units={"U1": empty}), [INTRO])
        self.assertEqual(len(caller.calls), 1)
        self.assertIn("기사를 짚는 대사 5줄", caller.calls[0]["user"])  # 단위 요청(intro_request)
        self.assertEqual(fixed["repairs"], [{"unit": "U1", "path": "", "codes": ["LINE_MISSING"], "action": "unit",
                                             "attempts": 1, "ok": True}])
        self.assertEqual(errors(fixed), [])

    def test_regenerated_unit_kept_only_when_better(self):
        caller, fixed, _ = repair(reviewed(units={"U4": BAD_DEFENSE}), ["JSON 아님", "JSON 아님", BAD_DEFENSE])
        self.assertEqual(fixed["units"]["U4"]["value"], BAD_DEFENSE)  # 다시 만든 값도 같은 오류 → 지금 값을 둔다
        self.assertEqual([r["ok"] for r in fixed["repairs"]], [False, False])
        self.assertEqual(errors(fixed), [("U4", "/defense", "NUMBER_NOT_IN_SOURCE")])

    def test_evidence_line_is_regenerated_as_plain_text(self):
        """U3는 BE-107처럼 대사 텍스트만 받는다(JSON 아님). 지난 대사 · 고칠 점을 덧붙인다."""
        evidence = [{"factorId": 2, "role": "prosecutor", "text": "검사 측 증거입니다. 흉기를 3번 휘둘렀습니다."},
                    {"factorId": 3, "role": "prosecutor", "text": "구호 조치 없이 떠난 사실입니다."},
                    {"factorId": 7, "role": "defense", "text": None}]
        result = reviewed(evidence=evidence)
        self.assertEqual(errors(result), [("U3", "/0", "NUMBER_NOT_IN_SOURCE"), ("U3", "/2", "LINE_MISSING")])
        caller, fixed, _ = repair(result, ["검사 측 증거입니다. 흉기를 집어 든 사실입니다.",
                                           "변호인 측 증거입니다. 수사 초기부터 인정한 기록입니다."])
        self.assertEqual(errors(fixed), [])
        self.assertFalse(any(call["json_output"] for call in caller.calls))
        self.assertIn("지난번 대사", caller.calls[0]["user"])
        self.assertNotIn("지난번 대사", caller.calls[1]["user"])  # 만들지 못했던 줄은 지난 대사가 없다
        self.assertIn("변호인 측 증거입니다", caller.calls[1]["user"])  # 변호인 측 첫 대사 지시
        self.assertEqual([r["factorId"] for r in fixed["repairs"]], [2, 7])
        self.assertEqual(fixed["evidence"][2]["text"], "변호인 측 증거입니다. 수사 초기부터 인정한 기록입니다.")
        self.assertTrue(fixed["evidence"][2]["validation"]["ok"])

    def test_quota_stops_repair_and_keeps_errors(self):
        caller, fixed, logs = repair(reviewed(units={"U4": BAD_DEFENSE}), [llm.LLMQuotaExhaustedError("일 한도")])
        self.assertEqual(errors(fixed), [("U4", "/defense", "NUMBER_NOT_IN_SOURCE")])
        self.assertTrue(any("호출 한도 소진" in line for line in logs))

    def test_zero_retries_does_nothing(self):
        caller, fixed, _ = repair(reviewed(units={"U4": BAD_DEFENSE}), [], retries=0)
        self.assertEqual(caller.calls, [])
        self.assertEqual(fixed["repairs"], [])

    def test_warnings_are_not_repaired(self):
        """경고는 문장 비교라 고쳐 쓰게 하면 맞는 대사를 바꿀 수 있다 — 고치지 않는다."""
        emotion = {**CLOSING, "prosecutor": "검사 측 의견입니다. 유족이 받은 상처가 큽니다."}
        caller, fixed, _ = repair(reviewed(units={"U4": emotion}), [])
        self.assertEqual(caller.calls, [])
        self.assertIn("EMOTION_NOT_IN_SOURCE", [i["code"] for i in fixed["review"]])

    def test_repairs_appear_in_script(self):
        _, fixed, _ = repair(reviewed(units={"U4": BAD_DEFENSE}), [{"text": GOOD_DEFENSE_TEXT}])
        script = sa.build_script(CASE, CAST, CLS, fixed, model="openai:a")
        self.assertEqual(len(script["repairs"]), 1)
        self.assertTrue(script["publishable"])


if __name__ == "__main__":
    unittest.main()
