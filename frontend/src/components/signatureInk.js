// 서명 그림 (FE-94). utils/signature.js가 만든 한 획을 SVG로 그리고, 앞에서부터 이어서 쓰는 연출을 건다.
// 커뮤니티 글쓰기 · 글 상세 글쓴이 표시 · 판결 체험 3막 서명란이 함께 쓴다. 서명은 장식이라 낭독기에는 "○○의 서명" 한 줄만 읽힌다.
import { currentUser } from '../utils/session.js';
import { reducedMotion } from '../utils/motion.js';
import { anonSeed, fullPath, pathAt, signatureOf } from '../utils/signature.js';

const NS = 'http://www.w3.org/2000/svg';
const MAX_HEIGHT = 58; // 그림 높이 상한(px)
const MAX_WIDTH = 260;

// 서명 씨앗: 로그인했으면 닉네임, 아니면 이 브라우저의 임시 식별값(3막 익명)
export const signerSeed = (user = currentUser()) => user?.nickname ?? anonSeed();

// 서명 SVG. blank면 비어 있는 상태(쓰기 전)로 만든다. 크기는 서명 비율대로 상한 안에서 정한다
export function signatureSvg(seed, { label = null, blank = false } = {}) {
  const signature = signatureOf(seed);
  const scale = Math.min(0.9, MAX_HEIGHT / signature.h, MAX_WIDTH / signature.w);
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('class', 'sig-ink');
  svg.setAttribute('viewBox', `0 0 ${signature.w.toFixed(1)} ${signature.h.toFixed(1)}`);
  svg.setAttribute('width', String(Math.round(signature.w * scale)));
  svg.setAttribute('height', String(Math.round(signature.h * scale)));
  svg.setAttribute('focusable', 'false');
  if (label) { svg.setAttribute('role', 'img'); svg.setAttribute('aria-label', label); } else svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS(NS, 'path');
  if (!blank) path.setAttribute('d', fullPath(signature));
  svg.append(path);
  svg._signature = signature;
  return svg;
}

// 쓰기: 처음부터 끝까지 한 번에 이어서, 서명 길이에 따라 0.8 ~ 1.2초. 끝나면 이행하는 약속을 돌려준다.
// 움직임을 줄인 환경에서는 바로 완성본을 둔다. 다시 부르면 앞 연출은 멈춘다
export function writeSignature(svg) {
  const path = svg.querySelector('path');
  const signature = svg._signature;
  const token = (svg._writing = (svg._writing ?? 0) + 1);
  if (reducedMotion()) {
    path.setAttribute('d', fullPath(signature));
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    let start = null;
    path.setAttribute('d', pathAt(signature, 0));
    const frame = (now) => {
      if (svg._writing !== token) { resolve(); return; }
      start ??= now;
      const progress = Math.min(1, (now - start) / signature.durationMs);
      path.setAttribute('d', progress >= 1 ? fullPath(signature) : pathAt(signature, progress));
      if (progress >= 1) resolve(); else requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
  });
}

// 쓴 서명을 지운다(올리지 못했을 때). 쓰는 중이면 멈춘다
export function eraseSignature(svg) {
  svg._writing = (svg._writing ?? 0) + 1;
  svg.querySelector('path').removeAttribute('d');
}
