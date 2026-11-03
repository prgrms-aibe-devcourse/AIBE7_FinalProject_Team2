import test from 'node:test';
import assert from 'node:assert/strict';
import { centerInStrip, watchStripEdges } from './scrollStrip.js';

const strip = (o) => ({ scrollWidth: 600, clientWidth: 300, scrollLeft: 0, offsetLeft: 0, ...o });
const item = (o) => ({ offsetLeft: 400, offsetWidth: 100, offsetParent: null, ...o });

test('centerInStrip: 지금 항목을 줄 가운데로 가로만 굴린다', () => {
  const s = strip({}); const i = item({ offsetParent: s });
  assert.equal(centerInStrip(s, i), true);
  assert.equal(s.scrollLeft, 400 - (300 - 100) / 2);
});

test('centerInStrip: offsetParent가 줄이 아니면 줄의 offsetLeft를 뺀다', () => {
  const s = strip({ offsetLeft: 50 });
  centerInStrip(s, item({ offsetParent: {} }));
  assert.equal(s.scrollLeft, 400 - 50 - 100);
});

test('centerInStrip: 줄이 넘치지 않거나 항목이 없으면 아무것도 하지 않는다', () => {
  const s = strip({ scrollWidth: 300 });
  assert.equal(centerInStrip(s, item({ offsetParent: s })), false);
  assert.equal(s.scrollLeft, 0);
  assert.equal(centerInStrip(strip({}), null), false);
});

test('watchStripEdges: requestAnimationFrame이 없는 환경(node)에서는 조용히 넘어간다', () => {
  assert.doesNotThrow(() => watchStripEdges(strip({})));
});
