// 캐릭터 그림을 둥근 창(마이페이지 카드)에 같은 크기로 맞춘다 (FE-67).
// 그림마다 투명 여백이 달라서(성향 이미지는 캐릭터가 이미지 높이의 62 ~ 78%, 역할 이미지는 여백 없음) 그림 전체를 창에 맞추면 캐릭터 크기가 제각각이다.
// 그래서 "불투명한 부분(캐릭터)의 경계 상자"를 재서, 그 상자가 창에서 같은 높이를 차지하도록 크기와 자리를 정한다.

const ALPHA_MIN = 16; // 이 값보다 불투명해야 캐릭터로 본다(0 ~ 255)

/**
 * 경계 상자(box)가 창 안에서 위 · 아래 여백을 뺀 높이를 채우도록 그림의 크기와 자리를 계산한다. 너비가 넘치면 너비에 맞춘다. 발은 창 아래에 둔다.
 * @param {{ width: number, height: number, box: { x0: number, y0: number, x1: number, y1: number } }} image 그림 원본 크기와 불투명 경계 상자(끝 칸 포함, 원본 픽셀)
 * @param {{ width: number, height: number }} win 창 크기
 * @param {{ top?: number, bottom?: number, side?: number, center?: boolean, centerWide?: boolean }} options top: 윗 여백(둥근 위쪽이 머리를 깎지 않게), bottom: 아랫 여백, side: 창 너비 중 쓸 비율, center: true면 발을 아래에 두지 않고 창 세로 가운데에 둔다, centerWide: true면 가로가 길어 너비에 맞춘 그림만 창 세로 가운데에 둔다(서 있는 캐릭터는 그대로 발을 아래에 둔다)
 * @returns {{ width: number, height: number, left: number, top: number }} 그림 상자의 크기와 창 안 위치(px)
 */
export function fitPortrait({ width, height, box }, win, { top = 22, bottom = 6, side = 0.92, center = false, centerWide = false } = {}) {
  const visibleW = box.x1 - box.x0 + 1;
  const visibleH = box.y1 - box.y0 + 1;
  const byHeight = Math.max(1, win.height - top - bottom) / visibleH;
  const byWidth = (win.width * side) / visibleW;
  const scale = Math.min(byHeight, byWidth);
  const middle = center || (centerWide && byWidth < byHeight); // 너비가 한계인 가로로 긴 그림은 아래에 붙이면 위가 비어 보인다
  return {
    width: width * scale,
    height: height * scale,
    left: win.width / 2 - (box.x0 + visibleW / 2) * scale,
    top: middle ? (win.height - visibleH * scale) / 2 - box.y0 * scale : win.height - bottom - (box.y1 + 1) * scale,
  };
}

// 네 모서리가 모두 불투명하고 거의 같은 색이면 그 색을 "단색 배경"으로 본다(투명 배경이 아닌 그림용). 아니면 null
const BG_CORNER_GAP = 24; // 모서리끼리 이 값(RGB 채널 차이)보다 다르면 배경이 아니라 그림으로 본다
const BG_TOLERANCE = 40; // 배경색과 이 값보다 가까운 칸은 배경으로 센다
const channelGap = (a, b) => Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]), Math.abs(a[2] - b[2]));
function solidBackground(data, width, height) {
  const at = (x, y) => { const i = (y * width + x) * 4; return [data[i], data[i + 1], data[i + 2], data[i + 3]]; };
  const corners = [at(0, 0), at(width - 1, 0), at(0, height - 1), at(width - 1, height - 1)];
  if (corners.some((c) => c[3] < 250)) return null;
  return corners.every((c) => channelGap(c, corners[0]) <= BG_CORNER_GAP) ? corners[0] : null;
}

/**
 * RGBA 픽셀(data, width x height)에서 캐릭터(투명하지도 단색 배경도 아닌 칸)의 경계 상자를 잰다. 끝 칸 포함. 없으면 null
 * @param {Uint8ClampedArray | number[]} data RGBA 픽셀
 */
export function foregroundBox(data, width, height) {
  const background = solidBackground(data, width, height);
  let x0 = width;
  let y0 = height;
  let x1 = -1;
  let y1 = -1;
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const i = (y * width + x) * 4;
      if (data[i + 3] <= ALPHA_MIN) continue;
      if (background && channelGap([data[i], data[i + 1], data[i + 2]], background) <= BG_TOLERANCE) continue;
      if (x < x0) x0 = x;
      if (x > x1) x1 = x;
      if (y < y0) y0 = y;
      if (y > y1) y1 = y;
    }
  }
  return x1 >= 0 ? { x0, y0, x1, y1 } : null;
}

// 줄여서 잰 경계 상자를 원본 픽셀 좌표로 되돌린다(ratio = 줄인 크기 / 원본 크기). 원본 밖으로 나가지 않게 자른다
export function scaleBoxUp(box, ratio, width, height) {
  return {
    x0: Math.floor(box.x0 / ratio),
    y0: Math.floor(box.y0 / ratio),
    x1: Math.min(width - 1, Math.ceil((box.x1 + 1) / ratio) - 1),
    y1: Math.min(height - 1, Math.ceil((box.y1 + 1) / ratio) - 1),
  };
}

// 이미지의 경계 상자를 잰다. 다른 출처 이미지(캔버스를 못 읽음) · 전부 투명한 이미지는 null
// 경계 상자는 카드 크기(수백 px)에 맞추는 용도라 긴 변 MEASURE_EDGE로 줄여 재도 오차가 1 ~ 2px이다(원본은 1672 × 941까지 있어 전체 펼침에서 느려진다)
const MEASURE_EDGE = 256;
const measured = new Map();
export function measureOpaqueBox(img) {
  const key = img.currentSrc || img.src;
  if (measured.has(key)) return measured.get(key);
  let box = null;
  try {
    const ratio = Math.min(1, MEASURE_EDGE / Math.max(img.naturalWidth, img.naturalHeight));
    const width = Math.max(1, Math.round(img.naturalWidth * ratio));
    const height = Math.max(1, Math.round(img.naturalHeight * ratio));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    context.drawImage(img, 0, 0, width, height);
    const small = foregroundBox(context.getImageData(0, 0, width, height).data, width, height);
    box = small && scaleBoxUp(small, ratio, img.naturalWidth, img.naturalHeight);
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
    else img.classList.remove('is-fitting'); // 못 쟀으면 기본 모양(contain) 그대로
  }, () => img.classList.remove('is-fitting'));
}
