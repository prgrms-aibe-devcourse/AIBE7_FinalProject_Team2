// 첫인상(사전 판단)과 내 최종 판결의 비교 — 6막(세 판결 비교) 독자 의견란의 "편집부 의견" 한 줄에 쓴다(FE-98).
// 방향은 서버가 계산한다(API 14 preToFinal.direction): HEAVIER = 최종 판결이 처음 생각보다 무겁다. 모르는 값이면 의견 줄은 숨긴다.
// 예전에는 첫인상과 "실제 판결"(preToCourt)을 비교해 실제 판결이 잣대처럼 읽혔다. 비교 대상을 내 두 판단으로 바꾼다(PRODUCT.md 원칙 1 · 핵심 카피 "다 알고 나서도, 같은 판단일까?")

// 편집부 의견(짧게). 어느 쪽이 맞는지 말하지 않고 내 판단이 어떻게 바뀌었는지만 적는다
const EDITOR_NOTES = {
  SAME: '처음 생각한 정도로 판결했어요.',
  HEAVIER: '처음 생각보다 무겁게 판결했어요.',
  LIGHTER: '처음 생각보다 가볍게 판결했어요.',
};
export const editorNote = (direction) => EDITOR_NOTES[direction] ?? null;
