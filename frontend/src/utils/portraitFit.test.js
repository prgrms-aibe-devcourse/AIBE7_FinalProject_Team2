// npm test : 캐릭터 그림을 창에 맞추는 계산 (FE-67). 브라우저 쪽(measureOpaqueBox · fitPortraitIn)은 캔버스가 필요해 여기서 재지 않는다
import test from 'node:test';
import assert from 'node:assert/strict';
import { fitPortrait, foregroundBox } from './portraitFit.js';

const WINDOW = { width: 170, height: 258 };
const approx = (actual, expected, message) => assert.ok(Math.abs(actual - expected) < 0.01, `${message}: ${actual} ≠ ${expected}`);

test('여백 없는 그림은 창 높이에서 위 · 아래 여백을 뺀 높이로 맞춘다', () => {
  const fit = fitPortrait({ width: 288, height: 560, box: { x0: 0, y0: 0, x1: 287, y1: 559 } }, WINDOW);
  approx(fit.height, 258 - 22 - 6, '높이');
  approx(fit.top, 22, '윗 여백');
  approx(fit.left, (170 - fit.width) / 2, '가운데');
});

test('투명 여백이 큰 그림도 캐릭터(불투명 부분)의 높이가 같아진다', () => {
  const roomyBox = { x0: 200, y0: 150, x1: 610, y1: 771 };
  const roomy = fitPortrait({ width: 800, height: 1000, box: roomyBox }, WINDOW);
  const tight = fitPortrait({ width: 288, height: 560, box: { x0: 0, y0: 0, x1: 287, y1: 559 } }, WINDOW);
  const roomyVisible = (roomyBox.y1 - roomyBox.y0 + 1) * (roomy.height / 1000);
  const tightVisible = 560 * (tight.height / 560);
  approx(roomyVisible, tightVisible, '캐릭터 높이');
});

test('캐릭터의 발(불투명 아래 끝)이 창 아래 여백에 닿는다', () => {
  const fit = fitPortrait({ width: 800, height: 1000, box: { x0: 200, y0: 150, x1: 610, y1: 771 } }, WINDOW);
  const scale = fit.height / 1000;
  approx(fit.top + 772 * scale, 258 - 6, '발 위치');
});

test('너비가 넘치면 너비에 맞춘다', () => {
  const fit = fitPortrait({ width: 1000, height: 1000, box: { x0: 0, y0: 0, x1: 999, y1: 999 } }, WINDOW, { side: 0.9 });
  approx(fit.width, 170 * 0.9, '너비');
});

test('캐릭터의 가로 가운데가 창 가운데에 온다', () => {
  const fit = fitPortrait({ width: 800, height: 1000, box: { x0: 100, y0: 100, x1: 499, y1: 799 } }, WINDOW);
  const scale = fit.width / 800;
  approx(fit.left + (100 + 200) * scale, 85, '가로 가운데');
});

// width x height 픽셀 격자를 만든다. paint(x, y)가 [r, g, b, a]를 돌려준다
const pixels = (width, height, paint) => {
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y += 1) for (let x = 0; x < width; x += 1) data.set(paint(x, y), (y * width + x) * 4);
  return data;
};
const inside = (x, y) => x >= 3 && x <= 5 && y >= 2 && y <= 7; // 캐릭터 칸

test('투명 배경이면 불투명한 부분이 경계 상자다', () => {
  const data = pixels(10, 10, (x, y) => (inside(x, y) ? [200, 30, 30, 255] : [0, 0, 0, 0]));
  assert.deepEqual(foregroundBox(data, 10, 10), { x0: 3, y0: 2, x1: 5, y1: 7 });
});

test('투명하지 않은 단색 배경도 걷어 내고 캐릭터만 잰다', () => {
  const data = pixels(10, 10, (x, y) => (inside(x, y) ? [200, 30, 30, 255] : [250, 248, 240, 255]));
  assert.deepEqual(foregroundBox(data, 10, 10), { x0: 3, y0: 2, x1: 5, y1: 7 });
});

test('모서리 색이 제각각인 사진은 배경이 없는 그림이라 전체가 경계 상자다', () => {
  const data = pixels(10, 10, (x, y) => [x * 25, y * 25, 120, 255]);
  assert.deepEqual(foregroundBox(data, 10, 10), { x0: 0, y0: 0, x1: 9, y1: 9 });
});

test('전부 투명하면 null이다', () => {
  assert.equal(foregroundBox(pixels(4, 4, () => [0, 0, 0, 0]), 4, 4), null);
});

test('center를 켜면 캐릭터가 창 세로 가운데에 온다', () => {
  const fit = fitPortrait({ width: 100, height: 200, box: { x0: 10, y0: 40, x1: 89, y1: 139 } }, { width: 150, height: 300 }, { top: 10, bottom: 10, center: true });
  approx(fit.top + 40 * (fit.height / 200) + 50 * (fit.height / 200), 150, '캐릭터 중심 높이');
});

test('그림 전체가 캐릭터여도 위 · 아래 여백을 남기고 창 안에 들어온다', () => {
  const fit = fitPortrait({ width: 300, height: 560, box: { x0: 0, y0: 0, x1: 299, y1: 559 } }, { width: 150, height: 250 }, { top: 20, bottom: 20, center: true });
  approx(fit.height, 210, '높이는 창 − 위 · 아래 여백');
  approx(fit.top, 20, '위 여백');
});
