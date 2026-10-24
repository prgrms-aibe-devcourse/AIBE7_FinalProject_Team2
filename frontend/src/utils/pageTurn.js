// 신문 한 장 넘기기 연출 (FE-32, 2.5D). 지금 보이는 지면을 그대로 본뜬 한 장(leaf)이 지면 왼쪽 가장자리를 축으로 넘어간다.
// 오른쪽 끝이 화면 쪽으로 들려 올라와 왼쪽으로 지나가고(앞면 → 뒷면), 그 밑에 다음 면이 이미 놓여 있다.
// 쓰는 곳: S-02 기사 클릭(→ S-20), S-02 유형 탭 바꾸기. 호출하는 쪽이 "다음 면"을 바로 그려 두면, 이 한 장이 걷히며 드러난다.
// 움직임은 transform · opacity 하나라 레이아웃을 다시 계산하지 않는다. 움직임 줄이기면 넘어가는 대신 이전 면이 0.2초 동안 흐려지며 다음 면이 드러난다(바뀌었다는 단서는 남긴다).
// 본뜬 지면은 장식이다(inert · aria-hidden, 눌러도 아무 일도 없다).
import { reducedMotion } from './motion.js';

const TURN_MS = 1000; // 기사 → 상세처럼 한 번 가는 이동. 자주 누르는 탭 바꾸기는 호출하는 쪽이 duration을 짧게 준다(0.5초)

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

/**
 * @param {HTMLElement} page 넘길 지면(보이는 그대로 복제한다)
 * @param {{ scopeClass: string, duration?: number }} options scopeClass: 지면 스타일이 걸려 있는 화면 클래스(예: 'case-list-page'), duration: 넘기는 시간(ms)
 * @returns {Promise<void>} 한 장이 다 넘어가 사라진 시점
 */
export function turnPage(page, { scopeClass, duration = TURN_MS }) {
  const box = page.getBoundingClientRect();

  const copy = page.cloneNode(true);
  copy.removeAttribute('id');
  copy.querySelectorAll('[id]').forEach((node) => node.removeAttribute('id'));
  Object.assign(copy.style, { position: 'absolute', top: `${box.top}px`, left: '0', width: `${box.width}px`, margin: '0', transform: 'none' });

  const scope = div(`page-turn-scope ${scopeClass}`);
  scope.append(copy);
  const frontShade = div('page-turn-shade');
  const front = div('page-turn-face');
  front.append(scope, frontShade);
  const backShade = div('page-turn-shade is-back');
  const back = div('page-turn-face is-back');
  back.append(div('page-turn-back-lines'), backShade);
  const leaf = div('page-turn-leaf');
  Object.assign(leaf.style, { left: `${box.left}px`, width: `${box.width}px` });
  leaf.append(back, front);
  const dim = div('page-turn-dim');
  const overlay = div('page-turn');
  overlay.setAttribute('aria-hidden', 'true');
  overlay.inert = true;
  overlay.append(dim, leaf);
  document.body.append(overlay);

  if (reducedMotion()) {
    return overlay.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200, easing: 'ease-out', fill: 'forwards' }).finished.catch(() => {}).then(() => overlay.remove());
  }
  const timing = { duration, fill: 'forwards' };
  const shots = [
    leaf.animate([{ transform: 'rotateY(0deg)' }, { transform: 'rotateY(-180deg)' }], { ...timing, easing: 'cubic-bezier(.55, .05, .3, 1)' }),
    leaf.animate([{ opacity: 1 }, { opacity: 1, offset: 0.55 }, { opacity: 0, offset: 0.85 }, { opacity: 0 }], timing), // 넘어가는 끝자락(easing이 느려지는 구간)에 화면 왼쪽 끝에 남는 뒷면 자락을 일찍 지운다
    frontShade.animate([{ opacity: 0 }, { opacity: 0.7, offset: 0.55 }, { opacity: 0.7 }], timing),
    backShade.animate([{ opacity: 0.8 }, { opacity: 0 }], timing),
    dim.animate([{ opacity: 0 }, { opacity: 0.45, offset: 0.4 }, { opacity: 0 }], timing),
  ];
  return Promise.all(shots.map((shot) => shot.finished)).catch(() => {}).then(() => overlay.remove());
}
