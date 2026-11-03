"""특별 모드 오류 분류 테스트 (BE-165). 실행: tools/ai-judgment에서 `python3 -m unittest tests.test_special_mode`"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from special_mode import PREFIX, marker, split_errors  # noqa: E402

ERRORS = [
    "excerpt: 원문 인용이 아닙니다 — 원문에서 찾을 수 없습니다 (⟦ ⟧ 밖의 글자가 원문과 다름)",
    "factors[1].evidence: 상세 주소 의심",
    "summary: 정확한 날짜 의심",
    "sections[0]: 정확한 나이 의심",
    "reasoning: 평가 표현 '정답'",
    "재판부가 고려한 판단 요소가 없습니다",
    "factors[2].evidence: 전화번호 의심",
    "excerpt: 마스킹 표시가 남음 의심",
    "notes[0]: 사건번호 의심",
    "summary가 100자를 넘습니다",
    "excerpt: 원문 인용이 아닙니다 — 원문 그대로인 부분이 너무 적습니다 (⟦ ⟧로 바꾼 부분이 대부분)",  # 지어낸 인용 방지
    "excerpt: 원문 인용이 아닙니다 — ⟦ ⟧ 안의 말은 1 ~ 20자여야 합니다 (식별 정보를 일반화한 말만)",
    "excerpt: 원문 인용이 아닙니다 — ⟦ ⟧ 짝이 맞지 않습니다",
    "summary: 동 · 호수 주소 의심",
    "판단 요소가 없습니다",  # extract: 사건에 판단 요소가 없음(구조) — 낮추지 않는다
    "excerpt: 새로 생긴 검사",  # 목록에 없는 오류는 막는다
]


class SplitErrorsTest(unittest.TestCase):
    def test_off_keepsAll(self):
        self.assertEqual(split_errors(ERRORS, None, "court"), (ERRORS, []))

    def test_court_format_and_all(self):
        kept, down = split_errors(ERRORS, "format", "court")
        self.assertEqual(down, [PREFIX + e for e in ERRORS[:4]])
        self.assertEqual(kept, ERRORS[4:])
        kept, down = split_errors(ERRORS, "all", "court")
        self.assertEqual(down, [PREFIX + e for e in ERRORS[:6]])
        self.assertEqual(kept, ERRORS[6:])  # 개인정보 최소선 · 구조 오류 · 모르는 오류는 어느 모드든 남는다

    def test_extract_keepsDateAndAge(self):
        # extract는 사용자에게 보이는 사건 본문이라 정확한 날짜 · 나이를 어느 모드든 막는다
        for mode in ("format", "all"):
            kept, down = split_errors(ERRORS, mode, "extract")
            self.assertEqual(down, [PREFIX + e for e in ERRORS[:2] + (ERRORS[4:6] if mode == "all" else [])])
            self.assertIn("summary: 정확한 날짜 의심", kept)
            self.assertIn("sections[0]: 정확한 나이 의심", kept)

    def test_unknownMode_rejected(self):
        with self.assertRaises(ValueError):
            split_errors(ERRORS, "everything", "court")

    def test_marker(self):
        self.assertIsNone(marker("all", []))
        self.assertEqual(marker("all", ["x"]), {"mode": "all", "downgraded": ["x"]})


if __name__ == "__main__":
    unittest.main()
