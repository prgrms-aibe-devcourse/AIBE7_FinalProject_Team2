// 신문 면 넘기기 · 빈 칸 채우기 · 면 목록 계산 (FE-118 · FE-119). 화면(S-02 caseListPage.js) 없이 시험할 수 있게 DOM을 쓰지 않는다.

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

/**
 * 지금 보여 줄 면 목록. 0건이면 숨기는 면(hideWhenEmpty — 범죄 유형 면)은 건수가 0일 때 빼되(FE-119), 지금 보고 있는 면은 건수가 0이어도 남긴다(보던 면이 사라지지 않게).
 * 건수를 아직 모르면(목록을 받기 전) 전부 보인다. 1면 · 색인 · 성향 테스트 · 오피니언처럼 숨기지 않는 면은 건수와 상관없이 늘 있다(FE-123 면 정의: pages/caseList/faces.js)
 * @param {{ key?: string, hideWhenEmpty?: boolean, count: (summary: object) => number | null }[]} faces 전체 면(순서대로)
 * @param {object | null | undefined} summary API 1의 건수 요약
 * @param {string | undefined} selected 지금 보고 있는 면
 */
export function visibleFaces(faces, summary, selected) {
  return faces.filter((face) => !face.hideWhenEmpty || !summary || face.key === selected || face.count(summary) > 0);
}

/** 한 면에 먼저 보이는 기사 수(머리기사 1 + 추천 2 + 다른 사건 6). 넘치는 기사는 1면에서는 빼고, 유형 면에서는 "이 면 더 보기"로 편다 */
export const FRONT_LIMIT = 9;
export const RECOMMENDED = 2;
export const REST_SHOWN = FRONT_LIMIT - 1 - RECOMMENDED;

/** 정렬된 사건을 면에 놓을 자리로 나눈다. front면 FRONT_LIMIT건만 쓰고 나머지는 버리며(다른 면 · 색인에서 본다), 아니면 넘치는 것을 more로 돌려준다 */
export function layoutCases(ordered, { front }) {
  const [lead, ...others] = front ? ordered.slice(0, FRONT_LIMIT) : ordered;
  const recommended = others.slice(0, RECOMMENDED);
  const afterRecs = others.slice(RECOMMENDED);
  return { lead, recommended, rest: afterRecs.slice(0, REST_SHOWN), more: front ? [] : afterRecs.slice(REST_SHOWN) };
}

// ---- 빈 지면 채우기 (FE-119) ----
// 기사가 모자라 비는 자리를 신문 짜임에 맞춰 채운다. 두 종류다: 기사 칸 안의 빈 칸(추천 반쪽 · 다른 사건 마지막 줄 — emptySlots로 칸 수를 세어 채움)과,
// 면 맨 아래에 남는 세로 여백(아래 planFiller — 남는 높이 · 폭을 재서 줄 수 · 칸 수를 정함). 둘이 같은 덱에서 뽑아 한 면 안에서 같은 지면이 되풀이되지 않는다.
export const FILL_GAP = 18; // 칸 · 줄 사이(기사 격자 간격과 같은 눈금)
const FILL_MIN_COL = 360; // 칸 하나의 최소 폭
const FILL_MAX_COLS = 4;
const FILL_MIN_ROW = 120; // 줄 하나의 최소 높이(법정 상식 한 칸이 들어가는 높이)
const FILL_MAX_ROWS = 2; // 면 아래 여백이 아무리 커도 2줄까지(그 이상은 읽을거리가 모자라 글자 없는 큰 상자만 늘어난다)
const FILL_TALL_ROW = 190; // 이 높이 이상이면 사진 지면을 섞는다

/**
 * 면 맨 아래 세로 여백(space px · 폭 width px)을 채울 줄 · 칸 계획. 여백이 줄 최소 높이보다 작으면 null(채우지 않는다).
 * 줄 높이는 남는 높이를 줄 수로 나눈 값이라 칸이 여백을 빈틈없이 채운다. 높은 줄(190px 이상)은 사진 지면 · 광고를 섞고, 낮은 줄은 광고 · 법정 상식으로 채운다
 * @returns {{ columns: number, rows: number, rowHeight: number, kinds: ('photo' | 'ad' | 'fact')[] } | null} kinds는 줄 → 칸 순서의 종류(광고는 한 번만)
 */
export function planFiller({ space, width }) {
  if (!(space >= FILL_MIN_ROW) || !(width > 0)) return null;
  const columns = Math.max(1, Math.min(FILL_MAX_COLS, Math.floor((width + FILL_GAP) / (FILL_MIN_COL + FILL_GAP))));
  const rows = Math.min(FILL_MAX_ROWS, Math.floor((space + FILL_GAP) / (FILL_MIN_ROW + FILL_GAP)));
  const rowHeight = (space - FILL_GAP * (rows - 1)) / rows;
  // 높은 줄은 사진을 대각선으로 엇갈리게(첫 줄 첫 칸, 둘째 줄 끝 칸) 놓아 사진이 한 줄에 몰리지 않게 한다
  const pattern = rowHeight >= FILL_TALL_ROW ? ['photo', 'fact', 'ad', 'fact', 'fact', 'photo'] : ['ad', 'fact', 'fact'];
  let adUsed = false;
  const kinds = Array.from({ length: rows * columns }, (_, i) => {
    const kind = pattern[i % pattern.length];
    if (kind !== 'ad') return kind;
    if (adUsed) return 'fact';
    adUsed = true;
    return 'ad';
  });
  return { columns, rows, rowHeight, kinds };
}

/**
 * 한 면을 그리는 동안 쓰는 채움 덱: 법정 상식 · 사진은 뽑을 때마다 다음 것, 광고는 한 번만(그다음 광고 요청은 법정 상식이 된다). seed는 면마다 다른 것부터 시작하게.
 * fork()는 지금까지 뽑은 상태를 그대로 이어받은 새 덱이다(원본은 변하지 않는다) — 면 아래 여백을 다시 채울 때마다 같은 결과가 나오게 한다
 */
export function createFillerDeck(seed = 0, state = { fact: seed, photo: seed, ad: false }) {
  const next = (kind) => {
    if (kind === 'ad' && !state.ad) { state.ad = true; return { kind: 'ad' }; }
    if (kind === 'photo') return { kind: 'photo', n: state.photo++ };
    return { kind: 'fact', n: state.fact++ };
  };
  // 빈 칸 count개: 둘 이상이면 맨 앞이 광고, 나머지는 법정 상식
  const take = (count) => Array.from({ length: count }, (_, i) => next(count >= 2 && i === 0 ? 'ad' : 'fact'));
  const fork = () => createFillerDeck(seed, { ...state });
  return { next, take, fork };
}
