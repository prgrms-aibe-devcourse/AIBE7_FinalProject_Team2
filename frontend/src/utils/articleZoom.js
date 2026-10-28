// 기사 → 사건 정보(S-20) 진입 연출 (FE-118, 공유 요소 전환). 신문에서 누른 기사의 제목이 사건 정보 지면의 큰 제목 자리로 커지며 날아간다.
// 종이 한 장 넘기기(pageTurn.js)는 면 넘기기 전용이 되어, 사건으로 들어가는 길은 넘김과 다른 움직임(확대)으로 구분한다.
// 1) 신문(S-02)이 liftArticle로 지금 지면을 본뜬 한 장을 화면 맨 위 장식 층에 띄우고 사건 정보로 간다(라우터가 스크롤을 맨 위로 올린다).
// 2) 사건 정보가 다 그려지면 landArticle(제목)로 받는다: 본뜬 지면이 흐려지며 걷히는 동안 제목이 원래 자리에서 새 제목 자리로 날아간다.
//    날아가는 글자는 새 제목과 같은 글꼴 · 폭 · 줄바꿈으로 만들어, 다 내려앉은 순간 진짜 제목과 바꿔도 티가 나지 않는다.
// 움직임은 transform · opacity만 쓴다. 움직임 줄이기면 연출 없이 바로 간다. 사건 정보가 제때 받지 않으면(느린 응답 · 오류) 잠시 뒤 스스로 걷힌다.
import { reducedMotion } from './motion.js';

const UNCLAIMED_MS = 1500; // 사건 정보는 늦으면 1초에 "불러오는 중"을 보이며 받는다(caseDetailPage.js). 그마저 없을 때의 안전장치
const FLY_MS = 620; // 화면 위에서 자리를 옮기는 움직임이라 ease-in-out
const FLY_EASE = 'cubic-bezier(.65, 0, .35, 1)';
const FADE_MS = 400; // 본뜬 신문이 다 사라지는 시간. 이어서 새 지면의 내용이 같은 시간 동안 나타난다(IN_MS) — 사용자 결정(눈이 아파서 겹치지 않고 하나가 다 사라진 뒤 다음이 나온다).
const IN_MS = 400; // 그 사이에는 책상이 아니라 새 지면의 빈 종이가 보인다(종이는 처음부터 놓여 있고 내용만 나타난다 — 6막에서 뒤집고 온 신문 목록과 같은 방식)
const DROP_MS = 200; // 받지 않은 채 걷을 때
let pending = null;

/**
 * 날아가는 제목의 출발 변환(기준점은 제목 왼쪽 위). 새 제목 크기에서 원래 제목 글자 크기로 줄여 원래 자리에 겹친다.
 * 가운데 정렬 제목(머리기사 배너)은 가운데끼리, 나머지는 왼쪽 위끼리 맞춘다
 * @param {{ left: number, top: number, width: number, height: number, fontSize: number }} source 원래 제목(화면 px, 글자 크기도 보이는 크기)
 * @param {{ left: number, top: number, width: number, height: number, fontSize: number }} target 새 제목
 */
export function flyStart(source, target, { centered = false } = {}) {
  const scale = source.fontSize / target.fontSize;
  if (!centered) return { x: source.left - target.left, y: source.top - target.top, scale };
  return {
    x: source.left + source.width / 2 - (target.left + (target.width * scale) / 2),
    y: source.top + source.height / 2 - (target.top + (target.height * scale) / 2),
    scale,
  };
}

// 화면에 보이는 글자 크기(카메라 · 확대 변환까지 넣은 값)
function visibleScale(node) {
  return node.offsetWidth ? node.getBoundingClientRect().width / node.offsetWidth : 1;
}

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

function drop(zoom, ms = DROP_MS) {
  window.clearTimeout(zoom.timer);
  zoom.layer.animate([{ opacity: 1 }, { opacity: 0 }], { duration: ms, easing: 'ease-out', fill: 'forwards' })
    .finished.catch(() => {}).then(() => zoom.layer.remove());
}

/**
 * 누른 기사를 들어 올린다: 지금 지면을 본뜬 한 장을 같은 자리에 띄워 둔다(그다음 사건 정보로 가는 것은 부르는 쪽).
 * @param {HTMLElement | null} headline 날아갈 원래 제목(머리기사 배너 · 기사 제목). 화면 밖이면 날지 않고 지면만 걷힌다
 * @param {{ page: HTMLElement, scopeClass: string }} options page: 본뜰 지면, scopeClass: 지면 스타일이 걸린 화면 클래스
 */
export function liftArticle(headline, { page, scopeClass }) {
  if (pending) { drop(pending, 0); pending = null; }
  if (reducedMotion()) return;
  const box = page.getBoundingClientRect();
  const seen = headline?.getBoundingClientRect();
  const onScreen = seen && seen.bottom > 0 && seen.top < window.innerHeight;
  headline?.setAttribute('data-zoom-source', '');
  const copy = page.cloneNode(true);
  headline?.removeAttribute('data-zoom-source');
  copy.removeAttribute('id');
  copy.querySelectorAll('[id]').forEach((node) => node.removeAttribute('id'));
  Object.assign(copy.style, { position: 'absolute', top: `${box.top}px`, left: `${box.left}px`, width: `${box.width}px`, margin: '0', transform: 'none', translate: 'none' });

  const scope = div(`article-zoom-scope ${scopeClass}`);
  scope.append(copy);
  const layer = div('article-zoom');
  layer.setAttribute('aria-hidden', 'true');
  layer.inert = true;
  layer.append(scope);
  document.body.append(layer);

  let source = null;
  if (onScreen) {
    const style = getComputedStyle(headline);
    source = {
      node: copy.querySelector('[data-zoom-source]'),
      rect: { left: seen.left, top: seen.top, width: seen.width, height: seen.height, fontSize: parseFloat(style.fontSize) * visibleScale(headline) },
      centered: style.textAlign === 'center',
    };
  }
  pending = { layer, scope, source, timer: window.setTimeout(() => { if (pending?.layer === layer) { drop(pending); pending = null; } }, UNCLAIMED_MS) };
}

// 새 제목을 본뜬 글자: 같은 글꼴 · 폭 · 줄바꿈(보이는 크기로)
function flyingCopy(target, rect) {
  const style = getComputedStyle(target);
  const scale = visibleScale(target);
  const px = (value) => (Number.isFinite(parseFloat(value)) ? `${parseFloat(value) * scale}px` : value);
  const fly = div('article-zoom-fly');
  fly.textContent = target.textContent;
  Object.assign(fly.style, {
    left: `${rect.left}px`, top: `${rect.top}px`, width: `${rect.width}px`,
    fontFamily: style.fontFamily, fontWeight: style.fontWeight, fontSize: px(style.fontSize), lineHeight: px(style.lineHeight), letterSpacing: px(style.letterSpacing),
    color: style.color, textAlign: style.textAlign, wordBreak: style.wordBreak, textWrap: style.textWrap,
  });
  return fly;
}

/**
 * 사건 정보가 들어 올린 기사를 받는다. 들어 올린 것이 없으면 false(부르는 쪽이 평소대로 나타난다).
 * @param {HTMLElement | null} target 내려앉을 새 제목. 없으면(불러오는 중 안내 등) 본뜬 지면만 걷는다
 * @param {HTMLElement} [page] 나타날 새 지면. 종이는 그대로 두고 그 안의 내용(첫 자식들)만 본뜬 신문이 다 사라진 뒤 페이드인한다
 */
export function landArticle(target, page) {
  const zoom = pending;
  if (!zoom) return false;
  pending = null;
  window.clearTimeout(zoom.timer);
  // 새 지면의 종이(.paper)는 처음부터 온전히 있고, 그 안의 내용만 숨겼다가 나타난다. 종이를 찾지 못하면 지면 전체를 나타낸다
  const sheet = page?.matches?.('.paper') ? page : page?.querySelector?.('.paper');
  const fadeIns = [...(sheet?.children ?? (page ? [page] : []))].map((node) => node.animate([{ opacity: 0 }, { opacity: 1 }], { duration: IN_MS, delay: FADE_MS, easing: 'ease-out', fill: 'backwards' }));
  if (!target || !zoom.source) { drop(zoom, FADE_MS); return true; }
  target.style.visibility = 'hidden'; // 날아온 글자가 내려앉을 때까지 진짜 제목은 비워 둔다
  window.requestAnimationFrame(() => {
    const seen = target.getBoundingClientRect();
    const rect = { left: seen.left, top: seen.top, width: seen.width, height: seen.height, fontSize: parseFloat(getComputedStyle(target).fontSize) * visibleScale(target) };
    const { x, y, scale } = flyStart(zoom.source.rect, rect, { centered: zoom.source.centered });
    const fly = flyingCopy(target, rect);
    zoom.layer.append(fly);
    const from = `translate(${x}px, ${y}px) scale(${scale})`;
    const shots = [
      // 출발점은 원래 제목과 줄바꿈이 달라, 처음 잠깐은 원래 제목에서 날아가는 글자로 겹쳐 바꾼다
      fly.animate([{ transform: from, opacity: 0 }, { opacity: 1, offset: 0.18 }, { transform: 'none', opacity: 1 }], { duration: FLY_MS, easing: FLY_EASE, fill: 'forwards' }),
      zoom.scope.animate([{ opacity: 1 }, { opacity: 0 }], { duration: FADE_MS, easing: 'ease-out', fill: 'forwards' }),
    ];
    shots.push(...fadeIns); // 새 지면 내용이 다 나타날 때까지 날아온 글자를 둔다(진짜 제목이 비치기 전에 바꾸지 않게)
    if (zoom.source.node) shots.push(zoom.source.node.animate([{ opacity: 1 }, { opacity: 0 }], { duration: FLY_MS * 0.18, easing: 'ease-out', fill: 'forwards' }));
    Promise.all(shots.map((shot) => shot.finished)).catch(() => {}).then(() => {
      target.style.visibility = '';
      zoom.layer.remove();
    });
  });
  return true;
}
