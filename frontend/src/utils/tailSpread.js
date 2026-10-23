// 메인 맨 아래 신문 끝자락 → 사건 목록 (FE-87). 누르면 벽 밑에 기울어져 들어간 신문이 곧바로 위로 잡아당겨지며 커지고,
// 들어 올린 뒤에는 그 밑에서 함께 움직이던 사건 목록 지면으로 넘어가 화면을 채운다.
// 움직이는 것은 둘이지만 한 장처럼 보인다: 끝자락을 본뜬 한 장(face, 누르기 전 모습 그대로)과 그 밑의 사건 목록 지면(paper).
// 둘은 매 순간 같은 자리 · 같은 폭 · 같은 기울기다. 들어 올리는 동안은 본뜬 한 장이 보이고, 살짝 흐려지며 걷혀 사건 목록 지면이 드러난다.
// 움직임은 구간을 이어 붙이지 않고 네 갈래가 동시에 겹친다(경계에서 멈추거나 튀는 곳이 없다):
//   올라감 = 처음부터 빠르게 올라가 부드럽게 멎는다(iOS 서랍 곡선) — 잡아당기는 손맛
//   커짐 · 기울기 풀림 = 조금 늦게 시작해 가속 · 감속한다 — 올라가는 도중부터 함께 커지고 바로 선다
//   벽 선 = 아래는 처음에 벽 선(화면 가로선)에서 잘려 있다가(벽 밑에서 빠져나오는 중) 커지는 동안 열린다
// 곡선이 여럿이라 CSS 한 줄로는 못 쓰므로 미리 계산한 자리를 촘촘한 키프레임으로 넘긴다(WAAPI · transform · clip-path · opacity · filter만, 메인 스레드를 쓰지 않는다)

const STEPS = 48;
// 전체 시간과 본뜬 한 장이 걷히는 구간(들어 올린 뒤). 사건 목록이 아직 덜 그려졌으면 걷히기 직전에서 기다린다.
// 중간 너비(책상이 화면 폭에 맞춰 줄어드는 구간 — 메인이 from.middle로 알려 준다)는 끝자락(메인 신문)과 사건 목록 지면의 배치가 많이 달라,
// 조금 더 천천히 들어 올리고 더 길게 걷어 바뀌는 과정이 보이게 한다(들어 올림 약 0.32초 · 바뀜 0.42초 · 커짐 0.36초). 넓은 너비 · 좁은 너비(720px 이하)는 기본
const PACE = { base: { ms: 900, handover: [.26, .42] }, middle: { ms: 1100, handover: [.29, .67] } };

// CSS cubic-bezier(x1, y1, x2, y2)와 같은 곡선. x(진행 시간)에서 y(진행량)를 이분법으로 찾는다
function bezier(x1, y1, x2, y2) {
  const at = (a, b, t) => 3 * a * t * (1 - t) ** 2 + 3 * b * t * t * (1 - t) + t ** 3;
  return (x) => {
    let lo = 0;
    let hi = 1;
    for (let i = 0; i < 24; i += 1) {
      const mid = (lo + hi) / 2;
      if (at(x1, x2, mid) < x) lo = mid; else hi = mid;
    }
    return at(y1, y2, (lo + hi) / 2);
  };
}
const RISE = bezier(.32, .72, 0, 1); // 올라감
const GROW = bezier(.65, 0, .35, 1); // 커짐 · 기울기 풀림(올라감보다 늦게 붙는다)
const lerp = (a, b, t) => a + (b - a) * t;
const window01 = (t, from, to) => Math.min(1, Math.max(0, (t - from) / (to - from)));
const smooth = (t) => t * t * (3 - 2 * t);
const FAR = 100000; // 잘리지 않는 쪽 끝(지면 높이와 상관없이)

/**
 * 누르기 전 끝자락의 모습. 기울어진 지면의 왼쪽 위 모서리(corner), 기울기(rotate, 도), 지면 폭(width), 벽 선 y(floor)
 * @param {HTMLElement} sheet 끝자락 지면
 * @param {HTMLElement} tilted 기울기가 걸린 요소(sheet를 감싼 버튼)
 * @param {number} floor
 */
// 마우스를 올려 들썩이는 중(지면 자체에도 기울기가 걸림)에 눌러도 그 순간 모습 그대로 잡는다. 지면은 이 뒤 숨겨지므로 들썩임은 멈춘 채 둔다
export function tailPose(sheet, tilted, floor) {
  const matrix = (node) => {
    const turn = getComputedStyle(node).transform;
    return turn === 'none' ? new DOMMatrix() : new DOMMatrix(turn);
  };
  const origin = (node) => getComputedStyle(node).transformOrigin.split(' ').map(parseFloat);
  const outer = matrix(tilted);
  const inner = matrix(sheet);
  const innerOrigin = origin(sheet);
  const saved = tilted.style.transform;
  tilted.style.transform = 'none'; // 기울이기 전 상자를 재고 바로 되돌린다(그리기 전이라 화면에 보이지 않는다)
  sheet.style.animation = 'none';
  const box = tilted.getBoundingClientRect();
  const page = sheet.getBoundingClientRect();
  tilted.style.transform = saved;
  const zoom = page.width / sheet.offsetWidth || 1; // 좁은 화면은 지면에 zoom이 걸려 있어 지면 자체의 변환 값도 그만큼 줄어 보인다
  const turn = (m, pivot, point) => {
    const dx = point.x - pivot.x;
    const dy = point.y - pivot.y;
    return { x: pivot.x + m.a * dx + m.c * dy + m.e, y: pivot.y + m.b * dx + m.d * dy + m.f };
  };
  const innerPivot = { x: page.left + innerOrigin[0] * zoom, y: page.top + innerOrigin[1] * zoom };
  const scaled = new DOMMatrix([inner.a, inner.b, inner.c, inner.d, inner.e * zoom, inner.f * zoom]);
  const corner = turn(scaled, innerPivot, { x: page.left, y: page.top }); // 지면 자체의 들썩임
  const outerOrigin = origin(tilted);
  const both = outer.multiply(inner);
  return { corner: turn(outer, { x: box.left + outerOrigin[0], y: box.top + outerOrigin[1] }, corner), rotate: Math.atan2(both.b, both.a) * 180 / Math.PI, width: page.width, floor };
}

// 지면(자기 좌표, 폭 natural)을 왼쪽 위 모서리 기준으로 놓는 transform과, 화면 가로선 limit 아래를 자르는 clip-path
function place(corner, rotate, scale, natural, limit, box) {
  const rad = rotate * Math.PI / 180;
  const sin = Math.sin(rad);
  const cos = Math.cos(rad);
  // 자기 좌표 (x, y)가 화면에서 y = corner.y + scale(x·sin + y·cos)이므로 limit에 닿는 y
  const cut = (x) => Math.max(0, Math.min(FAR, ((limit - corner.y) / scale - x * sin) / cos));
  return {
    transform: `translate(${corner.x - box.left}px, ${corner.y - box.top}px) rotate(${rotate}deg) scale(${scale})`,
    clipPath: `polygon(0 0, ${natural}px 0, ${natural}px ${cut(natural)}px, 0 ${cut(0)}px)`,
  };
}

// 누르기 전 모습 그대로 놓는 스타일(본뜬 한 장을 처음 띄울 때)
export function restingStyle(from) {
  return place(from.corner, from.rotate, 1, from.width, from.floor, { left: 0, top: 0 });
}

/**
 * @param {{ paper: HTMLElement, face: HTMLElement, from: ReturnType<typeof tailPose>, backdrop?: HTMLElement | null, ready: Promise<unknown> }} parts
 *   paper = 제자리의 사건 목록 지면, face = 끝자락을 본뜬 한 장(왼쪽 위 0, 0에 놓인 고정 층 안의 상자, 폭 from.width),
 *   backdrop = 뒤에 남긴 메인(함께 흐려진다), ready = 사건 목록이 다 그려지면 끝나는 약속
 */
export async function spreadFromTail({ paper, face, from, backdrop, ready }) {
  const { ms: duration, handover } = from.middle ? PACE.middle : PACE.base;
  const to = paper.getBoundingClientRect();
  const origin = { left: 0, top: 0 };
  const open = window.innerHeight + 40; // 벽 선이 다 열리면 화면 아래 밖
  const frames = { paper: [], face: [] };
  for (let i = 0; i <= STEPS; i += 1) {
    const t = i / STEPS;
    const grow = GROW(t);
    const width = lerp(from.width, to.width, grow);
    const rotate = lerp(from.rotate, 0, grow);
    const corner = { x: lerp(from.corner.x, to.left, grow), y: lerp(from.corner.y, to.top, RISE(t)) };
    const limit = lerp(from.floor, open, smooth(window01(t, .3, .8)));
    frames.paper.push({ offset: t, ...place(corner, rotate, width / to.width, to.width, limit, to) });
    const fade = window01(t, ...handover);
    frames.face.push({ offset: t, ...place(corner, rotate, width / from.width, from.width, limit, origin), opacity: 1 - smooth(fade), filter: `blur(${(Math.sin(Math.PI * fade) * 3).toFixed(2)}px)` });
  }
  const timing = { duration, easing: 'linear', fill: 'forwards' };
  paper.style.transformOrigin = '0 0';
  face.style.transformOrigin = '0 0';
  const moving = [paper.animate(frames.paper, timing), face.animate(frames.face, timing),
    ...(backdrop ? [backdrop.animate([{ opacity: 1 }, { opacity: 1, offset: .3 }, { opacity: 0 }], timing)] : [])];
  // 넘겨주기 직전까지 사건 목록이 다 그려지지 않았으면 그 자리에서 기다렸다가 이어 간다
  let drawn = false;
  ready.finally(() => { drawn = true; });
  const hold = setTimeout(() => {
    if (drawn) return;
    moving.forEach((animation) => animation.pause());
    ready.finally(() => moving.forEach((animation) => animation.play()));
  }, duration * handover[0]);
  await moving[0].finished.catch(() => {});
  clearTimeout(hold);
  moving.forEach((animation) => animation.cancel());
  paper.style.transformOrigin = '';
}
