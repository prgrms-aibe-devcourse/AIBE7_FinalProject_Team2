// 신문 면 넘기기 · 빈 칸 채우기 계산 (FE-118). 화면(S-02 caseListPage.js) 없이 시험할 수 있게 DOM을 쓰지 않는다.

/** 면 순서(keys)에서 지금 면(current)의 앞(-1) · 뒤(+1) 면 번호. 처음 · 끝을 넘으면 -1 */
export function faceIndexAfter(keys, current, step) {
  const at = keys.indexOf(current);
  if (at < 0) return -1;
  const next = at + step;
  return next >= 0 && next < keys.length ? next : -1;
}

// 손가락으로 넘기기: 가로로 충분히(또는 빠르게) 밀었고, 세로보다 확실히 가로일 때만. 세로 스크롤을 넘김으로 오해하지 않는다
const SWIPE_MIN_PX = 60;
const FLICK_MIN_PX = 24; // 빠르게 튕기면 짧아도 넘긴다(이 길이 아래는 손떨림)
const FLICK_SPEED = 0.11; // px/ms
/** 밀어낸 거리 · 시간으로 넘길 방향. 왼쪽으로 밀면 다음 면(+1), 오른쪽으로 밀면 앞 면(-1), 아니면 0 */
export function swipeStep({ dx, dy, ms }) {
  const across = Math.abs(dx);
  if (across <= Math.abs(dy) * 1.5) return 0;
  const flick = across >= FLICK_MIN_PX && across / Math.max(ms, 1) > FLICK_SPEED;
  if (across < SWIPE_MIN_PX && !flick) return 0;
  return dx < 0 ? 1 : -1;
}

/** "이번 호 다른 사건" 줄의 빈 칸 수. 한 줄 칸 수(넓은 화면 6 · 태블릿 3 · 모바일 2)마다 마지막 줄을 채울 만큼 */
export function emptySlots(count) {
  const need = (columns) => (columns - (count % columns)) % columns;
  return { six: need(6), three: need(3), two: need(2) };
}
