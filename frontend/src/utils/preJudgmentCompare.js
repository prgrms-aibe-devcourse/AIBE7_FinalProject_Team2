// 사전 판단과 실제 판결의 비교 — 6막(세 판결 비교) 독자 의견란의 "편집부 의견" 한 줄에 쓴다.
// 방향은 서버가 계산한다(API 14 preToCourt.direction). 서버 기준은 "재판부"라서 사용자 관점 문구로는 뒤집어 읽는다:
// HEAVIER(재판부가 더 무거움) = 사용자는 실제보다 가볍게, LIGHTER(재판부가 더 가벼움) = 실제보다 무겁게 판단했다. 모르는 값이면 의견 줄은 숨긴다.

// 편집부 의견(짧게). 어느 쪽이 맞는지 말하지 않고 사실만 적는다(PRODUCT.md 원칙 1)
const EDITOR_NOTES = {
  SAME: '실제 사건과 비슷하게 판단했어요.',
  HEAVIER: '실제 사건보다 가볍게 판단했어요.',
  LIGHTER: '실제 사건보다 무겁게 판단했어요.',
};
export const editorNote = (direction) => EDITOR_NOTES[direction] ?? null;
