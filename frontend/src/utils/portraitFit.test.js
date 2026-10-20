// npm test : 캐릭터 그림을 창에 맞추는 계산 (FE-67). 브라우저 쪽(measureOpaqueBox · fitPortraitIn)은 캔버스가 필요해 여기서 재지 않는다
import test from 'node:test';
import assert from 'node:assert/strict';
import { fitPortrait } from './portraitFit.js';

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
