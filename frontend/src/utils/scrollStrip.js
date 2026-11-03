// 가로로 미는 줄(신문 면 탭 · 면 색인)의 공용 도우미 (FE-161).
// - centerInStrip: 지금 면 항목을 줄 가운데로 가로만 굴린다(scrollIntoView는 세로로도 페이지를 움직여 쓰지 않는다)
// - watchStripEdges: 줄 앞 · 뒤에 더 있으면 data-more-start / data-more-end를 달아 CSS가 "더 있음"을 보이게 한다

// 항목이 줄 안에서 차지하는 자리(offset 값은 넘기는 연출(transform)에 영향받지 않는다)
function offsetInStrip(strip, item) {
  return item.offsetParent === strip ? item.offsetLeft : item.offsetLeft - strip.offsetLeft;
}

export function centerInStrip(strip, item) {
  if (!strip || !item || strip.scrollWidth <= strip.clientWidth) return false; // 다 들어가면 그대로
  strip.scrollLeft = offsetInStrip(strip, item) - (strip.clientWidth - item.offsetWidth) / 2;
  return true;
}

function syncEdges(strip) {
  const max = strip.scrollWidth - strip.clientWidth;
  strip.toggleAttribute('data-more-start', max > 1 && strip.scrollLeft > 1);
  strip.toggleAttribute('data-more-end', max > 1 && strip.scrollLeft < max - 1);
}

// 줄이 화면에 붙은 뒤에 재야 해서(scrollWidth는 붙기 전에 0) 붙을 때까지 몇 프레임 기다린다.
// 한계: 최대 10프레임(약 160ms)만 기다리고 그만둔다. 지금 호출처는 같은 틱에 붙인다
// 같은 줄에 여러 번 불러도 되게(면을 바꿔 다시 그릴 때마다 지금 면만 다시 맞춘다) 감시는 한 번만 붙인다
const watched = new WeakSet();
export function watchStripEdges(strip, { current, tries = 10 } = {}) {
  if (typeof requestAnimationFrame !== 'function') return; // 테스트(node) 환경
  requestAnimationFrame(() => {
    if (!strip.isConnected) { if (tries > 0) watchStripEdges(strip, { current, tries: tries - 1 }); return; }
    if (current) centerInStrip(strip, typeof current === 'function' ? current() : current);
    syncEdges(strip);
    if (watched.has(strip)) return;
    watched.add(strip);
    strip.addEventListener('scroll', () => syncEdges(strip), { passive: true });
    if (typeof ResizeObserver === 'function') new ResizeObserver(() => syncEdges(strip)).observe(strip); // 창 크기 · 글자 크기가 바뀌면 다시
  });
}
