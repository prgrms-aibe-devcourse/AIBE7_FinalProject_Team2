"""특별 모드 (BE-165): 검사 오류 일부를 경고로 낮춰 초안 · 적재까지 진행하고, 관리자가 원 판결문과 대조해 검수한다.

- format (B): 형식 오류만 낮춘다 — 원문 인용 대조(원문에서 찾을 수 없음) · 상세 주소(행정구역), court만 정확한 날짜 · 정확한 나이
- all (A): format에 더해 내용 오류(평가 표현 · 재판부가 고려한 요소 없음)도 낮춘다

어느 모드든 낮추지 않는 것: 개인정보 최소선(마스킹 표시 · 주민등록번호 · 이메일 · 전화번호 · 계좌번호 · 사건번호 · 법원명),
DB 제약 · 구조 오류(스키마 · 길이 상한 · 판단 요소 번호 등 — 낮춰도 적재에서 실패한다), 실제 선고 형량 노출.
허용 목록 방식이라 새로 생긴 검사 오류는 기본으로 막힌다.
오류 메시지에는 위치와 규칙만 있고 원본 값은 없다(deidentify.residual_check · court_draft.check_draft).
"""

MODES = ("format", "all")

# 인용 대조는 "원문에서 찾을 수 없음"(쪽 번호 · 문장부호 오탐 가능)만 낮춘다. 지어낸 인용을 막는 장치(원문 그대로인 부분이 너무 적음 ·
# ⟦ ⟧ 안 길이 · ⟦ ⟧ 짝)는 어느 모드든 막는다. 상세 주소는 행정구역만이다(동 · 호수는 "동 · 호수 주소 의심"으로 따로 막는다)
_FORMAT = (": 원문 인용이 아닙니다 — 원문에서 찾을 수 없습니다", ": 상세 주소 의심")
# 정확한 날짜 · 나이는 court(근거를 원문 그대로 인용해야 해서 모델이 피하기 어려움)에서만 낮춘다. extract는 사용자에게 보이는
# 사건 본문이라 어느 모드든 막는다 (해송님 결정, BE-165)
_COURT_ONLY = (": 정확한 날짜 의심", ": 정확한 나이 의심")
_ALL = (": 평가 표현 '", "재판부가 고려한 판단 요소가 없습니다")
PREFIX = "[특별 모드] "


def split_errors(errors, mode, stage):
    """(그대로 막는 오류, 경고로 낮춘 오류). stage는 "extract" 또는 "court". mode가 None이면 아무것도 낮추지 않는다."""
    if mode is None:
        return list(errors), []
    if mode not in MODES:
        raise ValueError(f"특별 모드는 {' · '.join(MODES)} 중 하나입니다: {mode!r}")
    allowed = _FORMAT + (_COURT_ONLY if stage == "court" else ()) + (_ALL if mode == "all" else ())
    kept, downgraded = [], []
    for error in errors:
        (downgraded if any(rule in error for rule in allowed) else kept).append(error)
    return kept, [PREFIX + e for e in downgraded]


def marker(mode, downgraded):
    """보고서 · 초안에 남길 표시. 낮춘 것이 없으면 None (특별 모드로 돌렸어도 표시하지 않는다)."""
    return {"mode": mode, "downgraded": downgraded} if downgraded else None
