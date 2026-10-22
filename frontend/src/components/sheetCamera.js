// 신문 카메라(1막 사건 기사, 6막 다음 날 신문). 화면보다 큰 지면(sheet)을 옮겨 기사 하나(data-focus)를 틀(frame)에 맞춘다.
// 기사마다 크기가 달라서, 어느 기사든 틀 안에서 비슷한 크기로 보이게 배율을 맞춘다(크게 키우지는 않는다, MAX_SCALE).
// 옮길 때는 지금 배율에서 다음 배율로 곧장 간다 — 큰 기사로 가면 줌아웃만, 작은 기사로 가면 줌인만(중간에 물러났다 들어가지 않는다).
// 움직임은 transform 하나라 레이아웃을 다시 계산하지 않는다.
// 예외 하나: from을 주면 그 요소가 무대를 꽉 채운 장면에서 시작해 한 번 빠져나온다(5막 법정 → 6막 신문 사진)
import { reducedMotion } from '../utils/motion.js';

const EASE = 'cubic-bezier(.45, 0, .2, 1)';
const MAX_SCALE = 1.15;
const css = ({ s, x, y }) => `translate(${x}px, ${y}px) scale(${s})`;

/**
 * @param {HTMLElement} sheet 지면(무대 장면 안에 절대 위치, transform-origin 0 0)
 * @param {HTMLElement} frame 기사를 맞출 틀(장면 안의 빈 요소, 위치는 CSS가 정한다)
 * @param {{ fill?: boolean, minScale?: number, maxUnit?: number }} [options] fill: 지면이 장면 가장자리 밖을 보이지 않게(배율 · 위치를 지면 안으로 묶는다, 6막 화면 가득)
 *   minScale: 기사를 이 배율보다 작게 보이지 않는다(좁은 화면에서 기사 일부만 크게, 왼쪽 위부터)
 *   maxUnit: 화면에 보이는 지면 단위(--u, 지면 폭의 1/118)를 이 px보다 크게 키우지 않는다(큰 화면에서 글씨가 지나치게 커지지 않게).
 *     fill보다 앞선다 — 지면이 화면보다 좁아지면 가운데에 두고 양옆에 책상이 보인다
 * @returns {{ go(name: string, options?: { from?: string, fromRect?: DOMRect, onStart?: () => void }): void }}
 *   fromRect: 시작 장면의 요소(from)를 이 화면 사각형에 맞춘 데서 시작한다(앞 막 무대가 있던 자리 그대로 이어 보이게)
 */
export function sheetCamera(sheet, frame, { fill = false, minScale = 0, maxUnit = 0 } = {}) {
  let at = null; // 지금 맞춰 둔 기사
  let pending = null; // 무대가 아직 그려지지 않아 크기를 모를 때 미뤄 둔 이동
  let shot = null; // 진행 중인 카메라 이동(WAAPI)
  let seen = false;

  // 지면 안 위치(변환 전 좌표). 중첩된 요소도 지면까지 offset을 더해 올라간다
  function box(target) {
    let x = 0;
    let y = 0;
    for (let node = target; node && node !== sheet; node = node.offsetParent) { x += node.offsetLeft; y += node.offsetTop; }
    return { x, y, w: target.offsetWidth, h: target.offsetHeight };
  }
  const find = (name) => sheet.querySelector(`[data-focus="${name}"]`) ?? sheet;
  const area = () => ({ x: frame.offsetLeft, y: frame.offsetTop, w: frame.offsetWidth, h: frame.offsetHeight });
  // 기사를 틀 가운데에, 틀에 들어오는 배율로. 아주 긴 기사(틀 높이의 1.6배 넘게)는 너무 작아지지 않게 폭에 맞추고 제목이 보이게 위를 맞춘다
  function place(b) {
    const f = area();
    const cap = maxUnit ? Math.min(MAX_SCALE, maxUnit / (sheet.offsetWidth / 118)) : MAX_SCALE;
    let s = Math.max(minScale, Math.min(f.w / b.w, b.h > b.w * (f.h / f.w) * 1.6 ? Infinity : f.h / b.h, cap));
    let y = b.h * s <= f.h ? f.y + (f.h - b.h * s) / 2 - b.y * s : f.y - b.y * s;
    // 기사가 틀보다 넓으면(minScale로 크게 볼 때) 왼쪽부터 보인다 — 글줄의 시작이 잘리지 않게
    let x = b.w * s <= f.w ? f.x + (f.w - b.w * s) / 2 - b.x * s : f.x - b.x * s;
    if (fill) {
      // 지면(sheet 전체)이 장면을 덮도록 배율 하한을 두고, 지면 가장자리가 장면 안으로 들어오지 않게 위치를 묶는다
      const scene = sheet.parentElement;
      const W = sheet.offsetWidth;
      const H = sheet.offsetHeight;
      const min = Math.min(cap, Math.max(scene.clientWidth / W, scene.clientHeight / H));
      if (s < min) {
        s = min;
        x = f.x + (f.w - b.w * s) / 2 - b.x * s;
        y = f.y - b.y * s;
      }
      // 지면이 장면보다 넓으면 가장자리가 안으로 들어오지 않게 묶고, 좁으면(maxUnit) 가운데에 둔다
      x = W * s >= scene.clientWidth ? Math.min(0, Math.max(scene.clientWidth - W * s, x)) : (scene.clientWidth - W * s) / 2;
      y = H * s >= scene.clientHeight ? Math.min(0, Math.max(scene.clientHeight - H * s, y)) : (scene.clientHeight - H * s) / 2;
    }
    return { s, x, y };
  }
  // 시작 장면(앞 막 무대 자리): 요소를 화면 사각형 rect에 그대로 맞춘다
  function onto(b, rect) {
    const scene = sheet.parentElement.getBoundingClientRect();
    const s = rect.width / b.w;
    return { s, x: rect.left - scene.left - b.x * s, y: rect.top - scene.top - b.y * s };
  }
  // 시작 장면: 요소가 장면 전체를 빈틈없이 채운다
  function cover(b) {
    const scene = sheet.parentElement;
    const s = Math.max(scene.clientWidth / b.w, scene.clientHeight / b.h);
    return { s, x: (scene.clientWidth - b.w * s) / 2 - b.x * s, y: (scene.clientHeight - b.h * s) / 2 - b.y * s };
  }

  function go(name, options = {}) {
    // 지금 보는 기사만 또렷하게(나머지 지면은 CSS가 흐리게 둔다)
    sheet.querySelectorAll('.is-on').forEach((node) => node.classList.remove('is-on'));
    const target = find(name);
    if (target !== sheet) target.classList.add('is-on');
    sheet.classList.toggle('spot', target !== sheet);
    if (!frame.offsetWidth) { pending = { name, options }; return; }
    pending = null;
    const now = getComputedStyle(sheet).transform; // 이동 도중에 다음 장면으로 넘어가면 지금 보이는 자리에서 잇는다
    const previous = at;
    shot?.cancel();
    shot = null;
    const to = place(box(target));
    sheet.style.transform = css(to);
    at = name;
    if (reducedMotion() || (previous === null && !options.from) || previous === name) return;

    let keyframes;
    let duration;
    if (previous === null) {
      const live = find(options.from);
      live.setAttribute('data-live', '');
      requestAnimationFrame(() => requestAnimationFrame(() => live.removeAttribute('data-live')));
      keyframes = [{ transform: css(options.fromRect ? onto(box(live), options.fromRect) : cover(box(live))) }, { transform: css(to) }];
      duration = 1500;
    } else {
      // 이동 + 배율 차이만큼의 줌: 거리에 따라 0.6 ~ 1초
      const from = place(box(find(previous)));
      const distance = Math.hypot(to.x - from.x, to.y - from.y) / (area().w || 1);
      keyframes = [{ transform: now }, { transform: css(to) }];
      duration = Math.round(Math.min(1000, 600 + distance * 300));
    }
    options.onStart?.();
    shot = sheet.animate(keyframes, { duration, easing: EASE });
  }

  // 무대 크기가 바뀌면(창 크기 · 모바일 회전) 지금 기사에 다시 맞춘다
  const observer = new ResizeObserver(() => {
    if (!frame.isConnected) { if (seen) observer.disconnect(); return; }
    if (!frame.offsetWidth) return;
    seen = true;
    if (pending) { go(pending.name, pending.options); return; }
    if (at && shot?.playState !== 'running') sheet.style.transform = css(place(box(find(at))));
  });
  observer.observe(frame);
  // 틀이 한 번도 그려지지 않은 채 무대가 사라지면 위 콜백이 오지 않아 관찰이 남는다 — 일정 시간 뒤에도 못 봤으면 끊는다
  setTimeout(() => { if (!seen) observer.disconnect(); }, 5000);
  return { go };
}
