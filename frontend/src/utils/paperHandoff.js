// 6막 신문 → 메인 책상 (FE-32). 6막에서 "신문 덮기" · "메인으로"를 고르면 지금 보던 지면(다음 날 신문의 맨 뒷면)을 본뜬 한 장을
// 화면 맨 위 장식 층에 같은 자리 · 같은 크기로 띄워 두고 메인으로 간다. 메인(landingPage.js)이 그 한 장을 받아
// 제자리에서 페이드아웃해 책상을 드러낸다.
// 주소 이동 한 번에만 쓰고 버린다(새로고침 · 뒤로 가기에는 없다). 메인이 받지 않으면 잠시 뒤 스스로 걷힌다.

const UNCLAIMED_MS = 3000;
let pending = null;

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

/**
 * @param {HTMLElement} sheet 6막 지면(카메라 변환 중이어도 지금 보이는 모습 그대로 본뜬다)
 * @param {{ backdrop?: string, blank?: boolean }} [options] backdrop: 지면 바깥에 보이던 바탕. 'desk'면 책상 나뭇결, 아니면 색(장식 층이 처음에 화면을 덮는다).
 *   blank: 기사 없이 빈 종이만 넘긴다(FE-55 "다른 사건 보기" — 뒤집힌 지면의 뒷면을 사건 목록이 받아 그 위에 기사를 페이드인한다)
 */
export function handPaper(sheet, { backdrop = 'transparent', blank = false } = {}) {
  takePaper()?.layer.remove();
  const rect = sheet.getBoundingClientRect(); // 카메라 변환까지 들어간 화면 자리
  const width = sheet.offsetWidth; // 변환 전 폭
  // 지면의 크기 단위(--u, 무대 폭 기준 cqw)를 px로 재 둔다 — 본뜬 지면은 무대 밖이라 같은 단위를 직접 넣어야 같은 모습이다
  const probe = div('');
  probe.style.cssText = 'position: absolute; visibility: hidden; width: calc(var(--u) * 100);';
  sheet.append(probe);
  const unit = probe.offsetWidth / 100;
  probe.remove();

  const copy = sheet.cloneNode(true);
  copy.removeAttribute('tabindex');
  copy.classList.remove('spot');
  copy.querySelectorAll('[id]').forEach((node) => node.removeAttribute('id'));
  copy.querySelectorAll('.is-on').forEach((node) => node.classList.remove('is-on'));
  copy.querySelectorAll('[data-live]').forEach((node) => node.removeAttribute('data-live'));
  copy.querySelector('.sheet-close')?.remove();
  copy.style.cssText = `--u: ${unit}px; width: ${width}px; margin: 0; left: 0; top: 0;`;

  // 화면에 보이던 부분만 한 장으로(지면이 화면보다 크면 화면 크기)
  const left = Math.max(0, rect.left);
  const top = Math.max(0, rect.top);
  const view = { left, top, width: Math.min(window.innerWidth, rect.right) - left, height: Math.min(window.innerHeight, rect.bottom) - top };
  const start = { x: rect.left - left, y: rect.top - top, scale: rect.width / width };
  copy.style.transform = `translate(${start.x}px, ${start.y}px) scale(${start.scale})`;

  const layer = div('paper-hand-layer');
  layer.setAttribute('aria-hidden', 'true');
  layer.inert = true;
  const shade = div('paper-hand-shade');
  if (backdrop === 'desk') shade.classList.add('is-desk');
  else shade.style.background = backdrop;
  const card = div('paper-hand stage-wrap'); // stage-wrap: 지면 스타일(stage.css)이 그대로 걸리게
  Object.assign(card.style, { left: `${view.left}px`, top: `${view.top}px`, width: `${view.width}px`, height: `${view.height}px` });
  if (!blank) card.append(copy); // 빈 종이는 카드(.paper-hand)의 바탕색만으로 그린다
  layer.append(shade, card);
  document.body.append(layer);

  const handed = { layer, shade, card, copy, width, view, start, rect };
  pending = handed;
  setTimeout(() => {
    if (pending !== handed) return;
    pending = null;
    layer.remove();
  }, UNCLAIMED_MS);
}

/** @returns {null | { layer, shade, card, copy, width: number, view, start, rect: DOMRect }} 넘겨받은 한 장(한 번만 받는다) */
export function takePaper() {
  const handed = pending;
  pending = null;
  return handed;
}
