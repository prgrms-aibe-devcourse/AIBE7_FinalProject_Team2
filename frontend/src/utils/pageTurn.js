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

  const frontShade = div('page-turn-shade');
  const front = div('page-turn-face');
  front.append(turning.scope, frontShade);
  const backShade = div('page-turn-shade is-back');
  const backFace = div('page-turn-face is-back');
  backFace.append(div('page-turn-back-lines'), backShade);
  const leaf = div('page-turn-leaf');
  Object.assign(leaf.style, { left: `${turning.box.left}px`, width: `${turning.box.width}px` });
  leaf.append(backFace, front);
  const dim = div('page-turn-dim');
  const overlay = div('page-turn');
  overlay.setAttribute('aria-hidden', 'true');
  overlay.inert = true;
  overlay.append(dim);
  if (back) { // 넘어오는 한 장이 다 덮을 때까지 지금 면을 그대로 보여 둔다
    const under = div('page-turn-under');
    Object.assign(under.style, { left: `${before.box.left}px`, width: `${before.box.width}px` });
    under.append(before.scope);
    overlay.append(under);
  }
  overlay.append(leaf);
  document.body.append(overlay);

  if (reducedMotion()) {
    if (back) leaf.remove(); // 흐려지는 것은 지금 면(앞으로는 넘어가는 한 장, 뒤로는 밑에 둔 한 장)
    return overlay.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200, easing: 'ease-out', fill: 'forwards' }).finished.catch(() => {}).then(() => overlay.remove());
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
  return Promise.all(shots.map((shot) => shot.finished)).catch(() => {}).then(() => overlay.remove());
}
