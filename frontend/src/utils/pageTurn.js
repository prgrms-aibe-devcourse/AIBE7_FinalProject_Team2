// 신문 한 장 넘기기 연출 (FE-32, 2.5D). 지금 보이는 지면을 그대로 본뜬 한 장(leaf)이 지면 왼쪽 가장자리를 축으로 넘어간다.
// 오른쪽 끝이 화면 쪽으로 들려 올라와 왼쪽으로 지나가고(앞면 → 뒷면), 그 밑에 다음 면이 이미 놓여 있다.
// 뒤로 넘기기(back, FE-118): 거꾸로 앞 면이 왼쪽에서 넘어와 지금 면을 덮는다(나아가는 쪽과 돌아오는 쪽이 반대 방향).
// 쓰는 곳: S-02 면 넘기기(유형 탭 · 종이 귀 · 가장자리 화살표 · ← → · 밀기). 기사 → 사건 정보는 넘김이 아니라 제목 확대다(articleZoom.js, FE-118).
// 다음 면은 draw()가 그린다: 지금 면을 본뜬 직후에 부르므로, 앞으로는 넘어가는 한 장 밑에서, 뒤로는 그대로 둔 지금 면 밑에서 그려진다.
// 움직임은 transform · opacity 하나라 레이아웃을 다시 계산하지 않는다. 움직임 줄이기면 넘어가는 대신 이전 면이 0.2초 동안 흐려지며 다음 면이 드러난다(바뀌었다는 단서는 남긴다).
// 본뜬 지면은 장식이다(inert · aria-hidden, 눌러도 아무 일도 없다).
import { reducedMotion } from './motion.js';

export const TURN_MS = 1000; // 한 장을 천천히 넘길 때. 자주 넘기는 면 넘기기는 호출하는 쪽이 duration을 짧게 준다(0.5초)

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

// 지면을 보이는 그대로 본떠, 화면 기준 같은 자리에 놓을 한 장(스타일이 걸리도록 화면 클래스로 감싼다)
function snapshot(page, scopeClass) {
  const box = page.getBoundingClientRect();
  const copy = page.cloneNode(true);
  copy.removeAttribute('id');
  copy.querySelectorAll('[id]').forEach((node) => node.removeAttribute('id'));
  Object.assign(copy.style, { position: 'absolute', top: `${box.top}px`, left: '0', width: `${box.width}px`, margin: '0', transform: 'none' });
  const scope = div(`page-turn-scope ${scopeClass}`);
  scope.append(copy);
  return { scope, box };
}

// 넘기는 한 장(leaf)과 그 아래 그대로 둘 한 장(under)을 화면 위 덮개(overlay)에 얹는다. under는 뒤로 넘길 때만 있다
function cover(under) {
  const dim = div('page-turn-dim');
  const overlay = div('page-turn');
  overlay.setAttribute('aria-hidden', 'true');
  overlay.inert = true;
  overlay.append(dim);
  if (under) {
    const holder = div('page-turn-under');
    Object.assign(holder.style, { left: `${under.box.left}px`, width: `${under.box.width}px` });
    holder.append(under.scope);
    overlay.append(holder);
  }
  document.body.append(overlay);
  return { overlay, dim };
}

// turning(본뜬 한 장)을 넘긴다: 앞으로는 왼쪽으로 넘어가 사라지고, 뒤로(back)는 왼쪽에서 넘어와 덮는다. 다 넘기면 덮개를 걷는다.
// later(뒤로만): 앞면에 붙일 장을 아직 못 만들었을 때 그 장을 줄 Promise. 장은 뒷면부터 넘어오기 시작하고, 앞면이 보이기 시작할 때(FACE_AT)까지
// 오지 않았으면 그 자리에서 멈춰 기다린다. null이 오면(갈 지면이 없다) 넘기기를 그만두고 덮개를 흐려 걷는다
const FACE_AT = 0.4; // 뒤로 넘길 때 앞면이 비치기 시작하는 지점(아래 leaf 불투명도 0.15 ~ 0.45 사이)
function flip({ overlay, dim }, turning, { back, duration, box = turning?.box, later = null }) {
  const frontShade = div('page-turn-shade');
  const front = div('page-turn-face');
  front.append(frontShade);
  const leaf = div('page-turn-leaf');
  const place = ({ left, width }) => Object.assign(leaf.style, { left: `${left}px`, width: `${width}px` });
  const attach = (next) => {
    front.prepend(next.scope);
    place(next.box);
  };
  if (turning) attach(turning);
  else place(box);
  const backShade = div('page-turn-shade is-back');
  const backFace = div('page-turn-face is-back');
  backFace.append(div('page-turn-back-lines'), backShade);
  leaf.append(backFace, front);
  overlay.append(leaf);
  const fadeOut = () => overlay.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200, easing: 'ease-out', fill: 'forwards' }).finished.catch(() => {}).then(() => overlay.remove());

  if (reducedMotion()) {
    if (back) leaf.remove(); // 흐려지는 것은 지금 면(앞으로는 넘어가는 한 장, 뒤로는 밑에 둔 한 장)
    return (later ?? Promise.resolve()).then(fadeOut); // 뒤로는 갈 화면이 그려진 뒤에 흐린다
  }
  const timing = { duration, fill: 'forwards' };
  const shots = back ? [
    leaf.animate([{ transform: 'rotateY(-180deg)' }, { transform: 'rotateY(0deg)' }], { ...timing, easing: 'cubic-bezier(.7, 0, .45, .95)' }),
    leaf.animate([{ opacity: 0 }, { opacity: 0, offset: 0.15 }, { opacity: 1, offset: 0.45 }, { opacity: 1 }], timing), // 화면 왼쪽 끝에서 들려 오는 뒷면 자락은 늦게 보인다(앞으로 넘길 때의 거울)
    frontShade.animate([{ opacity: 0.7 }, { opacity: 0.7, offset: 0.45 }, { opacity: 0 }], timing),
    backShade.animate([{ opacity: 0 }, { opacity: 0.8 }], timing),
    dim.animate([{ opacity: 0 }, { opacity: 0.45, offset: 0.6 }, { opacity: 0 }], timing),
  ] : [
    leaf.animate([{ transform: 'rotateY(0deg)' }, { transform: 'rotateY(-180deg)' }], { ...timing, easing: 'cubic-bezier(.55, .05, .3, 1)' }),
    leaf.animate([{ opacity: 1 }, { opacity: 1, offset: 0.55 }, { opacity: 0, offset: 0.85 }, { opacity: 0 }], timing), // 넘어가는 끝자락(easing이 느려지는 구간)에 화면 왼쪽 끝에 남는 뒷면 자락을 일찍 지운다
    frontShade.animate([{ opacity: 0 }, { opacity: 0.7, offset: 0.55 }, { opacity: 0.7 }], timing),
    backShade.animate([{ opacity: 0.8 }, { opacity: 0 }], timing),
    dim.animate([{ opacity: 0 }, { opacity: 0.45, offset: 0.4 }, { opacity: 0 }], timing),
  ];
  const done = Promise.all(shots.map((shot) => shot.finished)).catch(() => {});
  if (!later) return done.then(() => overlay.remove());
  // 앞면을 기다린다: 오면 붙이고, 앞면이 비칠 때까지 안 왔으면 멈췄다가 오면 이어 넘긴다
  let arrived = false;
  let gone = false;
  later.then((next) => {
    arrived = true;
    if (next) attach(next);
    else gone = true;
  });
  setTimeout(async () => {
    if (arrived) return;
    shots.forEach((shot) => shot.pause());
    await later;
    if (gone) return;
    shots.forEach((shot) => shot.play());
  }, duration * FACE_AT);
  return Promise.race([done, later.then((next) => next ?? 'gone')]).then(async (first) => {
    if (first === 'gone') {
      shots.forEach((shot) => shot.cancel());
      leaf.remove();
      return fadeOut();
    }
    await done;
    return overlay.remove();
  });
}

/**
 * @param {HTMLElement} page 넘길 지면(보이는 그대로 복제한다)
 * @param {{ scopeClass: string, duration?: number, back?: boolean, draw?: () => void }} options scopeClass: 지면 스타일이 걸려 있는 화면 클래스(예: 'case-list-page'),
 *   duration: 넘기는 시간(ms), back: 앞 면으로 돌아가기(왼쪽에서 넘어온다), draw: 다음 면을 그리는 함수(지금 면을 본뜬 직후에 부른다)
 * @returns {Promise<void>} 한 장이 다 넘어가 사라진 시점
 */
export function turnPage(page, { scopeClass, duration = TURN_MS, back = false, draw = () => {} }) {
  const before = snapshot(page, scopeClass);
  draw();
  const turning = back ? snapshot(page, scopeClass) : before; // 뒤로는 새로 그린 면이 넘어오는 한 장이다
  return flip(cover(back ? before : null), turning, { back, duration });
}

// 다른 화면의 지면이 다 그려질 때까지 기다린다(지금 지면이 아닌 새 .paper, 그 화면이 불러오는 중이 아닐 때). 못 찾으면 null
// 화면이 바뀌었는데 신문 지면이 아예 없으면(메인 · 8막 · 마이페이지 등) 넘길 곳이 아니므로 timeout을 기다리지 않고 바로 null
const NO_PAPER_MS = 50;
function nextPaper(old, timeout) {
  const started = performance.now();
  const view = document.getElementById('route-view');
  let changedAt = null;
  const watcher = view ? new MutationObserver(() => { changedAt ??= performance.now(); }) : null;
  watcher?.observe(view, { childList: true });
  return new Promise((resolve) => {
    const finish = (value) => { watcher?.disconnect(); resolve(value); };
    const look = () => {
      const papers = [...document.querySelectorAll('.paper')].filter((node) => node !== old && node.isConnected && !node.closest('.page-turn'));
      const ready = papers.find((node) => !node.closest('.is-loading'));
      if (ready) finish(ready);
      else if (changedAt !== null && !papers.length && performance.now() - changedAt > NO_PAPER_MS) finish(null);
      else if (performance.now() - started > timeout) finish(null);
      else requestAnimationFrame(look);
    };
    requestAnimationFrame(look);
  });
}

// 넘기기 전에 갈 지면을 가라앉힌다: 화면 안에 보이는 사진을 미리 해독하고(본뜬 장이 처음 그려질 때 해독이 겹쳐 프레임이 끊겼다),
// 갈 화면이 그린 직후 rAF로 하는 일(여백 칸 짜기 등)이 끝나도록 두 프레임 더 기다린다. 사진이 늦어도 SETTLE_MS를 넘기지 않는다
const SETTLE_MS = 400;
const frame = () => new Promise((resolve) => requestAnimationFrame(() => resolve()));
async function settle(next) {
  const visible = [...next.querySelectorAll('img')].filter((image) => image.getBoundingClientRect().top < window.innerHeight);
  const decoded = Promise.all(visible.map((image) => {
    image.loading = 'eager';
    return image.decode().catch(() => {});
  }));
  await Promise.race([decoded, new Promise((resolve) => { setTimeout(resolve, SETTLE_MS); })]);
  await frame();
  await frame();
}

/**
 * 다른 화면(라우트)의 지면으로 한 장 넘긴다(FE-126, 신문 성향 테스트 지면 → 1면 · 색인 · 오피니언 면). 연출은 turnPage와 같다.
 * 앞으로: 지금 지면이 넘어가며 그 밑에 갈 화면이 그려진다. 뒤로(back): 지금 지면을 덮개에 그대로 두고, 한 장이 곧바로 왼쪽에서 뒷면부터 넘어온다.
 * 갈 화면이 다 그려지면 그 지면을 본떠 앞면에 붙인다(앞면이 비칠 때까지 못 그렸으면 그 자리에서 멈춰 기다린다).
 * 갈 화면이 신문 지면이 아니거나 timeout 안에 그려지지 않으면 넘기기를 그만두고 덮개를 흐려 걷는다
 * @param {HTMLElement} page 지금 지면
 * @param {{ scopeClass: string, nextScopeClass?: string, duration?: number, back?: boolean, go: () => unknown, timeout?: number }} options
 *   scopeClass: 지금 지면의 화면 클래스, nextScopeClass: 갈 지면의 화면 클래스(기본 scopeClass), go: 화면을 옮기는 함수(navigate · history.back)
 * @returns {Promise<void>}
 */
export async function turnPageAcross(page, { scopeClass, nextScopeClass = scopeClass, duration = TURN_MS, back = false, go, timeout = 2500 }) {
  if (!back) return turnPage(page, { scopeClass, duration, draw: go });
  const before = snapshot(page, scopeClass);
  const shield = cover(before);
  go();
  // 장은 곧바로 뒷면부터 넘어오기 시작하고, 그사이 갈 지면이 그려지면 본떠 앞면에 붙인다(로딩 동안 화면이 멈춰 있지 않게)
  const later = nextPaper(page, timeout).then(async (next) => {
    if (!next) return null;
    await settle(next);
    return snapshot(next, nextScopeClass);
  });
  return flip(shield, null, { back: true, duration, box: before.box, later });
}

// 신문 지면(성향 테스트 · 사건 정보 등)에서 다른 신문 면으로 갈 때 한 장을 넘긴다(FE-126). 연출은 사건 목록 면 넘기기와 같다(0.5초).
// 앞 쪽 면(1면 · 8면 · 이전으로)은 그 면이 왼쪽에서 넘어와 지금 지면을 덮고(back), 뒤 쪽 면(12면 오피니언 등)은 지금 지면이 넘어가며 밑의 면이 드러난다.
// 넘기는 동안 다시 눌러도 한 번만 간다. 움직임 줄이기는 흐려짐으로 바뀐다
const LEAVE_TURN_MS = 500; // caseListPage.js TAB_TURN_MS와 같다
/**
 * @param {HTMLElement} sheet 지금 지면
 * @param {string} scopeClass 지금 지면 스타일이 걸려 있는 화면 클래스(그 화면 루트의 class, 예: 'case-list-page case-paper-page overview-page')
 * @returns {(go: () => unknown, options?: { back?: boolean }) => void} 지면을 넘기며 go(화면 이동)를 부르는 함수
 */
export function leaveByTurn(sheet, scopeClass) {
  let leaving = false;
  return (go, { back = true } = {}) => {
    if (leaving) return;
    leaving = true;
    turnPageAcross(sheet, { scopeClass, nextScopeClass: 'case-list-page', duration: LEAVE_TURN_MS, back, go })
      .finally(() => { leaving = false; });
  };
}
