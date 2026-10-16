// 메인 책상 커피잔 → 마이페이지 (FE-44). 커피잔이 살짝 들린 뒤 화면 가운데로 다가오며 커지고, 커피 표면이 화면을 가득 채우면
// 마이페이지로 간다. 마이페이지(myPage.js)가 그 커피 층을 받아 페이드아웃한다. 다가오는 동안 커피 표면이 살짝 일렁인다(pageTurn.css).
// 잔을 화면 크기로 미리 그려 두고 작게 줄여 시작한다 — 작게 그린 잔을 키우면 화면을 채울 때 흐려진다.
// 주소 이동 한 번에만 쓰고 버린다(새로고침 · 뒤로 가기에는 없다). 마이페이지가 받지 않으면 잠시 뒤 스스로 걷힌다.

const LIFT_MS = 320; // 잔 들기
const DIVE_MS = 900; // 잔이 다가와 커피가 화면을 채우기까지
const FADE_MS = 450; // 마이페이지에서 커피 층이 걷히는 시간
const UNCLAIMED_MS = 3000;
let pending = null;

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

/**
 * 커피잔으로 들어가는 연출. 끝나면(커피가 화면을 덮으면) resolve한다. 그다음 호출한 쪽이 마이페이지로 이동한다.
 * @param {HTMLElement} cup 잔 그림(테두리까지 원) @param {HTMLElement} coffee 잔 안의 커피 원. 둘 다 지금 보이는 자리 그대로 본뜬다
 */
export async function diveIntoCoffee(cup, coffee) {
  takeCoffee()?.remove();
  const cupBox = cup.getBoundingClientRect();
  const coffeeBox = coffee.getBoundingClientRect();
  const width = window.innerWidth;
  const height = window.innerHeight;
  // 끝 모습: 커피 원이 화면 가운데에서 네 모서리를 모두 덮는다
  const ratio = cupBox.width / coffeeBox.width;
  const size = Math.hypot(width, height) * 1.04 * ratio;

  const layer = div('coffee-dive-layer');
  layer.setAttribute('aria-hidden', 'true');
  layer.inert = true;
  const mug = div('coffee-dive-mug');
  const surface = div('coffee-dive-surface');
  surface.style.inset = `${((1 - 1 / ratio) / 2) * 100}%`;
  mug.append(surface);
  Object.assign(mug.style, { width: `${size}px`, height: `${size}px`, left: `${(width - size) / 2}px`, top: `${(height - size) / 2}px` });
  mug.style.setProperty('--rim', `${size * 0.075}px`);
  layer.append(mug);
  document.body.append(layer);

  const scale = cupBox.width / size;
  const x = cupBox.left + cupBox.width / 2 - width / 2;
  const y = cupBox.top + cupBox.height / 2 - height / 2;
  const at = (dx, dy, s) => `translate(${dx}px, ${dy}px) scale(${s})`;
  const resting = at(x, y, scale);
  const lifted = at(x, y - 12, scale * 1.08);
  cup.style.visibility = 'hidden'; // 본뜬 잔이 그 자리를 대신한다
  mug.style.transform = resting;
  // 들리면 그림자가 길고 흐려진다(신문 들기와 같은 문법). 다가오기 시작하면 앞 1/4 동안 그림자를 걷는다 — 화면만 한 잔에 그림자를 매 프레임 그리지 않게
  const shadow = (y, blur, alpha) => `drop-shadow(0 ${y}px ${blur}px color-mix(in srgb, var(--color-desk-shadow) ${alpha}%, transparent))`;
  await mug.animate([{ transform: resting, filter: shadow(4, 6, 40) }, { transform: lifted, filter: shadow(26, 22, 45) }],
    { duration: LIFT_MS, easing: 'cubic-bezier(.2, .8, .2, 1)', fill: 'forwards' }).finished.catch(() => {});
  // 가까워질수록 빨라진다(얼굴로 가져가듯). 끝에서는 커피만 보인다
  await mug.animate([{ transform: lifted, filter: shadow(26, 22, 45) }, { offset: 0.25, filter: shadow(0, 0, 0) }, { transform: at(0, 0, 1), filter: 'none' }],
    { duration: DIVE_MS, easing: 'cubic-bezier(.55, 0, .45, 1)', fill: 'forwards' }).finished.catch(() => {});

  pending = layer;
  setTimeout(() => {
    if (pending !== layer) return;
    pending = null;
    layer.remove();
  }, UNCLAIMED_MS);
}

/** @returns {HTMLElement | null} 넘겨받은 커피 층(한 번만 받는다) */
export function takeCoffee() {
  const layer = pending;
  pending = null;
  return layer;
}

/** 넘겨받은 커피 층을 제자리에서 페이드아웃하고 걷는다 */
export async function fadeCoffee(layer) {
  await layer.animate([{ opacity: 1 }, { opacity: 0 }], { duration: FADE_MS, easing: 'ease-out', fill: 'forwards' }).finished.catch(() => {});
  layer.remove();
}
