// 캐릭터 그림을 둥근 창(마이페이지 카드)에 같은 크기로 맞춘다 (FE-67).
// 그림마다 투명 여백이 달라서(성향 이미지는 캐릭터가 이미지 높이의 62 ~ 78%, 역할 이미지는 여백 없음) 그림 전체를 창에 맞추면 캐릭터 크기가 제각각이다.
// 그래서 "불투명한 부분(캐릭터)의 경계 상자"를 재서, 그 상자가 창에서 같은 높이를 차지하도록 크기와 자리를 정한다.

const ALPHA_MIN = 16; // 이 값보다 불투명해야 캐릭터로 본다(0 ~ 255)

/**
 * 경계 상자(box)가 창 안에서 위 · 아래 여백을 뺀 높이를 채우도록 그림의 크기와 자리를 계산한다. 너비가 넘치면 너비에 맞춘다. 발은 창 아래에 둔다.
 * @param {{ width: number, height: number, box: { x0: number, y0: number, x1: number, y1: number } }} image 그림 원본 크기와 불투명 경계 상자(끝 칸 포함, 원본 픽셀)
 * @param {{ width: number, height: number }} win 창 크기
 * @param {{ top?: number, bottom?: number, side?: number }} options top: 윗 여백(둥근 위쪽이 머리를 깎지 않게), bottom: 아랫 여백, side: 창 너비 중 쓸 비율
 * @returns {{ width: number, height: number, left: number, top: number }} 그림 상자의 크기와 창 안 위치(px)
 */
export function fitPortrait({ width, height, box }, win, { top = 22, bottom = 6, side = 0.92 } = {}) {
  const visibleW = box.x1 - box.x0 + 1;
  const visibleH = box.y1 - box.y0 + 1;
  const scale = Math.min(Math.max(1, win.height - top - bottom) / visibleH, (win.width * side) / visibleW);
  return {
    width: width * scale,
    height: height * scale,
    left: win.width / 2 - (box.x0 + visibleW / 2) * scale,
    top: win.height - bottom - (box.y1 + 1) * scale,
  };
}

// 이미지의 불투명 경계 상자를 잰다. 다른 출처 이미지(캔버스를 못 읽음) · 전부 투명한 이미지는 null
const measured = new Map();
export function measureOpaqueBox(img) {
  const key = img.currentSrc || img.src;
  if (measured.has(key)) return measured.get(key);
  let box = null;
  try {
    const canvas = document.createElement('canvas');
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    context.drawImage(img, 0, 0);
    const { data } = context.getImageData(0, 0, canvas.width, canvas.height);
    let x0 = canvas.width;
    let y0 = canvas.height;
    let x1 = -1;
    let y1 = -1;
    for (let y = 0; y < canvas.height; y += 1) {
      for (let x = 0; x < canvas.width; x += 1) {
        if (data[(y * canvas.width + x) * 4 + 3] > ALPHA_MIN) {
          if (x < x0) x0 = x;
          if (x > x1) x1 = x;
          if (y < y0) y0 = y;
          if (y > y1) y1 = y;
        }
      }
    }
    if (x1 >= 0) box = { x0, y0, x1, y1 };
  } catch {
    box = null; // 캔버스가 막힌 이미지(다른 출처) — 맞추지 않고 창에 그대로 담는다
  }
  measured.set(key, box);
  return box;
}

/**
 * 창(windowEl) 안의 그림(img)을 위 계산대로 맞춘다. 창이 그려지거나 크기가 바뀔 때마다 다시 맞춘다(반응형).
 * 재지 못하면 CSS의 기본(창 안에 contain)으로 보인다. 맞추는 동안은 가려 두어 크기가 튀어 보이지 않게 한다.
 */
export function fitPortraitIn(windowEl, img, options) {
  let box;
  img.classList.add('is-fitting');
  const apply = () => {
    if (!box || !windowEl.clientWidth || !windowEl.clientHeight) return;
    const fit = fitPortrait({ width: img.naturalWidth, height: img.naturalHeight, box }, { width: windowEl.clientWidth, height: windowEl.clientHeight }, options);
    Object.assign(img.style, { width: `${fit.width}px`, height: `${fit.height}px`, left: `${fit.left}px`, top: `${fit.top}px` });
    img.classList.remove('is-fitting');
    img.classList.add('is-fitted');
  };
  const observer = new ResizeObserver(() => {
    if (!windowEl.isConnected) { observer.disconnect(); return; }
    apply();
  });
  observer.observe(windowEl);
  img.decode().then(() => {
    box = measureOpaqueBox(img);
    if (box) apply();
    else img.classList.remove('is-fitting'); // 못 쟀으면 기본 모양 그대로
  }, () => img.classList.remove('is-fitting'));
}
