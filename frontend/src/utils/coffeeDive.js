// 메인 책상 커피잔 → 마이페이지 (FE-44). 커피잔을 누르면 커다란 커피잔이 화면 우상단에서 빠르게 날아와 가운데에 선다.
// 잔이 날아오는 동안 책상이 마이페이지로 바뀌고(아래 층 페이드아웃), 다 바뀌면 잔이 오른쪽으로 빠져나간다.
// 잔은 처음부터 화면 크기로 그려 두고 transform만 움직인다(크게 그린 잔을 줄여 쓰는 쪽이 흐려지지 않는다).
// 연출 층은 body에 얹어 두므로 화면이 바뀌어도 끝까지 재생되고, 끝나면 스스로 걷힌다.
// 잔 아래의 책상은 이동 직전 모습을 본뜬 층이 덮고 있다가, 마이페이지로 넘어간 뒤 페이드아웃해 마이페이지로 부드럽게 바뀐다.

const FLY_IN_MS = 820; // 잔이 우상단에서 날아와 가운데에 서기까지(이 동안 책상이 마이페이지로 바뀐다)
const FLY_OUT_MS = 500; // 책상이 다 바뀐 뒤 잔이 오른쪽으로 빠져나가기까지
const NAVIGATE_AT_MS = 200; // 이 시점에 마이페이지로 이동한다(잔이 가운데로 오는 동안 아래에 마이페이지가 그려진다)
const TABLE_FADE_DELAY_MS = 60; // 이동한 마이페이지가 그려질 틈을 주고 본뜬 책상을 걷는다(책상이 바뀌기 시작: 0.26초)
const TABLE_GONE_MS = 720; // 본뜬 책상이 다 사라지는 시점(잔이 가운데에 서기 전)

const BELOW_FOLD = '.feature-section, .site-footer'; // 메인 화면에서 첫 화면 아래에 있는 구역

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

/**
 * 커다란 커피잔이 우상단에서 날아와 서고, 책상이 마이페이지로 바뀐 뒤 오른쪽으로 빠져나간다. 이동 시점(NAVIGATE_AT_MS)에 onNavigate를 부른다.
 * @param {() => void} onNavigate 마이페이지로 이동하는 함수 @param {HTMLElement} page 지금 보이는 메인 화면(잔 아래 책상으로 본뜬다)
 */
export function flyCoffee(onNavigate, page) {
  const width = window.innerWidth;
  const height = window.innerHeight;
  const size = Math.min(width, height) * 0.95; // 커다란 잔: 짧은 변에 가깝게

  const layer = div('coffee-fly-layer');
  layer.setAttribute('aria-hidden', 'true');
  layer.inert = true;
  // 책상 본뜨기: 화면이 바뀌어도 잠시 그대로 남겨 두었다가 페이드아웃한다(같은 id가 겹치지 않게 id는 뗀다)
  const table = div('coffee-fly-table');
  // 루트는 얕게(클래스만 따라오게) 복제하고, 화면 아래 서비스 소개 · 푸터는 빼서 클릭마다 큰 트리를 복제하지 않는다
  const copy = page.cloneNode(false);
  copy.append(...[...page.children].filter((child) => !child.matches(BELOW_FOLD)).map((child) => child.cloneNode(true)));
  copy.removeAttribute('id');
  copy.querySelectorAll('[id]').forEach((node) => node.removeAttribute('id'));
  const box = page.getBoundingClientRect();
  Object.assign(table.style, { left: `${box.left}px`, top: `${box.top}px`, width: `${box.width}px` });
  table.append(copy);
  layer.append(table);
  const mug = div('coffee-fly-mug');
  mug.append(div('coffee-fly-coffee'));
  Object.assign(mug.style, { width: `${size}px`, height: `${size}px`, left: `${(width - size) / 2}px`, top: `${(height - size) / 2}px` });
  mug.style.setProperty('--rim', `${size * 0.075}px`);
  layer.append(mug);
  document.body.append(layer);

  // 1) 우상단 화면 밖에서 조금 돌아간 채로 날아와 화면 가운데에 선다(페이드 없음)
  const from = `translate(${width * 0.62}px, ${-height * 0.62}px) rotate(14deg) scale(.92)`;
  const center = 'translate(0, 0) rotate(0deg) scale(1.08)';
  const flightIn = mug.animate([{ transform: from }, { transform: center }],
    { duration: FLY_IN_MS, easing: 'cubic-bezier(.35, .45, .75, 1)', fill: 'forwards' });
  // 2) 잔이 가운데에 서면(FLY_IN_MS) 오른쪽으로 빠져나간다. 책상은 그보다 먼저 마이페이지로 다 바뀌어 있다
  const away = `translate(${(width + size) / 2 + 40}px, 0) rotate(-8deg) scale(1.08)`;
  const flightOut = mug.animate([{ transform: center }, { transform: away }],
    { duration: FLY_OUT_MS, delay: FLY_IN_MS, easing: 'cubic-bezier(.6, 0, .9, .5)', fill: 'forwards' });
  const tableFade = table.animate([{ opacity: 1 }, { opacity: 0 }],
    { duration: TABLE_GONE_MS - NAVIGATE_AT_MS - TABLE_FADE_DELAY_MS, delay: NAVIGATE_AT_MS + TABLE_FADE_DELAY_MS, easing: 'ease-in-out', fill: 'both' });
  setTimeout(onNavigate, NAVIGATE_AT_MS);
  Promise.all([flightIn.finished, flightOut.finished, tableFade.finished]).catch(() => {}).then(() => layer.remove());
}
