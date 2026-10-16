// 신문 한 장 넘기기 연출 (FE-32, 2.5D). 지금 보이는 지면을 그대로 본뜬 한 장(leaf)이 지면 왼쪽 가장자리를 축으로 넘어간다.
// 오른쪽 끝이 화면 쪽으로 들려 올라와 왼쪽으로 지나가고(앞면 → 뒷면), 그 밑에 다음 면이 이미 놓여 있다.
// 쓰는 곳: S-02 기사 클릭(→ S-20), S-02 유형 탭 바꾸기. 호출하는 쪽이 "다음 면"을 바로 그려 두면, 이 한 장이 걷히며 드러난다.
// 움직임은 transform · opacity 하나라 레이아웃을 다시 계산하지 않는다. 움직임 줄이기면 넘어가는 대신 이전 면이 0.2초 동안 흐려지며 다음 면이 드러난다(바뀌었다는 단서는 남긴다).
// 본뜬 지면은 장식이다(inert · aria-hidden, 눌러도 아무 일도 없다).

const TURN_MS = 1000; // 기사 → 상세처럼 한 번 가는 이동. 자주 누르는 탭 바꾸기는 호출하는 쪽이 duration을 짧게 준다(0.5초)
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

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
    leaf.animate([{ opacity: 1 }, { opacity: 1, offset: 0.82 }, { opacity: 0 }], timing), // 다 넘어간 뒤 화면 왼쪽 끝에 남는 뒷면 자락을 지운다
    frontShade.animate([{ opacity: 0 }, { opacity: 0.7, offset: 0.55 }, { opacity: 0.7 }], timing),
    backShade.animate([{ opacity: 0.8 }, { opacity: 0 }], timing),
    dim.animate([{ opacity: 0 }, { opacity: 0.45, offset: 0.4 }, { opacity: 0 }], timing),
  ];
  return Promise.all(shots.map((shot) => shot.finished)).catch(() => {}).then(() => overlay.remove());
}

/**
 * 신문 접어 내려놓기 (FE-32, 8막 "신문 덮기"). 펼쳐 든 신문의 오른쪽 절반이 가운데 접힌 선을 축으로 왼쪽 위로 접히고,
 * 반으로 접힌 신문이 아래로 내려가며 사라진다. 지금 보이는 지면을 본떠 화면 위에서 움직이고, 원래 지면은 숨긴다.
 * @param {HTMLElement} page 접을 지면
 * @param {{ scopeClasses?: string[] }} options scopeClasses: 지면 스타일이 걸리는 바깥 클래스들(바깥 → 안 순서)
 * @returns {Promise<void>} 다 내려간 시점(움직임 줄이기면 0.2초 페이드)
 */
export function foldPaper(page, { scopeClasses = [] } = {}) {
  const box = page.getBoundingClientRect();
  const copy = () => {
    const node = page.cloneNode(true);
    node.removeAttribute('id');
    node.querySelectorAll('[id]').forEach((child) => child.removeAttribute('id'));
    Object.assign(node.style, { position: 'absolute', top: '0', left: '0', width: `${box.width}px`, margin: '0', transform: 'none' });
    return scopeClasses.reduceRight((inner, className) => {
      const scope = div(className);
      Object.assign(scope.style, { position: 'absolute', inset: '0', minHeight: '0', padding: '0', background: 'none' });
      scope.append(inner);
      return scope;
    }, node);
  };
  const place = (node, left, width) => Object.assign(node.style, { position: 'absolute', top: `${box.top}px`, left: `${left}px`, width: `${width}px`, height: `${box.height}px`, overflow: 'hidden' });

  // 왼쪽 절반(그대로 남는 쪽)
  const half = box.width / 2;
  const left = div('fold-half');
  place(left, box.left, half);
  left.append(copy());
  // 오른쪽 절반(접히는 장): 앞면은 지면 오른쪽, 뒷면은 신문 뒷장
  const leaf = div('fold-leaf');
  Object.assign(leaf.style, { position: 'absolute', top: `${box.top}px`, left: `${box.left + half}px`, width: `${half}px`, height: `${box.height}px`, transformOrigin: 'left center', transformStyle: 'preserve-3d' });
  const front = div('fold-face');
  const frontCopy = copy();
  frontCopy.style.left = `${-half}px`;
  frontCopy.style.width = `${box.width}px`;
  front.append(frontCopy);
  const back = div('fold-face is-back');
  leaf.append(front, back);
  const group = div('fold-group');
  group.append(left, leaf);
  const overlay = div('page-fold');
  overlay.setAttribute('aria-hidden', 'true');
  overlay.inert = true;
  overlay.append(group);
  document.body.append(overlay);
  page.style.visibility = 'hidden';

  if (reducedMotion()) {
    return overlay.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200, easing: 'ease-out', fill: 'forwards' }).finished.catch(() => {});
  }
  const foldShot = leaf.animate([{ transform: 'rotateY(0deg)' }, { transform: 'rotateY(-178deg)' }], { duration: 650, easing: 'cubic-bezier(.55, .05, .3, 1)', fill: 'forwards' });
  return foldShot.finished.catch(() => {}).then(() => group.animate(
    [{ transform: 'none', opacity: 1 }, { transform: 'translateY(70vh) scale(.72) rotate(-4deg)', opacity: 0 }],
    { duration: 520, easing: 'cubic-bezier(.5, 0, .75, .3)', fill: 'forwards' },
  ).finished.catch(() => {}));
}
