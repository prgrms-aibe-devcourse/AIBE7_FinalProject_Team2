import { button, element } from '../../components/siteLayout.js';
import { reducedMotion } from '../../utils/motion.js';

// 의견 보내기 창 (FE-62). 메인 책상의 펜 · 엽서를 누르면 뜬다. 의견은 바깥 설문 링크로 받는다(입력칸 없음).
// 창이 열려 있는 동안 Tab은 창 안에서만 돌고, Esc · 바깥 누르기 · 닫기로 닫으면 누른 곳으로 포커스가 돌아온다.
const FOCUSABLE = 'button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';
const FEEDBACK_URL = '#'; // 의견 받을 링크(정해지면 넣는다)
const FLY_MS = 650;
const SWAP_MS = 160; // 도착 무렵 엽서 앞면은 사라지고 창 내용이 나타난다(엽서 안에서만 바뀐다)
const SWAP_DELAY_MS = 165;
const EASE = 'cubic-bezier(.2,.8,.2,1)';
const PEN_GAP = 50;

// 책상 위 엽서가 화면 중앙으로 날아와 그대로 창이 된다. 창은 엽서와 같은 비율(landing.css .feedback-dialog)이라
// 엽서 자리 · 기울기(-7도)에서 같은 비율로 커지기만 한다. 창 안에 엽서 앞면(우표 · 제목 · 줄)을 겹쳐 같이 키우고, 도착 무렵 앞면이 사라지며 내용이 나타난다.
// 펜은 엽서에서 떨어져 창 오른쪽에 오른쪽으로 기울어진 채 내려앉는다.
// 책상 그림은 기준 좌표계(1672px)를 줄여 쓰므로 크기는 상자 크기(offsetWidth)와 화면 크기(rect)의 비율로 맞춘다.
// 책상 화면이 아니거나(좁은 화면의 메뉴 카드) 연출이 꺼져 있으면 null
function flyObjects(dialog) {
  if (reducedMotion()) return null;
  const [card, pen] = ['postcard', 'pen'].map((key) => document.querySelector(`.landing-page .obj-${key}`));
  if ([card, pen].some((node) => !node || !node.offsetWidth || !node.getBoundingClientRect().width)) return null;
  const to = dialog.getBoundingClientRect();
  const toX = to.left + to.width / 2;
  const toY = to.top + to.height / 2;
  const cardRect = card.getBoundingClientRect();

  // 엽서 앞면: 책상 엽서 그림을 본떠 창 크기로 키워 겹친다. 기울기 · 그림자 · 종이는 창이 맡는다
  // (.landing-page 아래에서만 그림 규칙이 맞으므로 겉 상자에 그 이름을 붙인다)
  const face = element('div', 'landing-page feedback-face');
  face.setAttribute('aria-hidden', 'true');
  face.style.cssText = `width:${card.offsetWidth}px;height:${card.offsetHeight}px;transform:scale(${to.width / card.offsetWidth}, ${to.height / card.offsetHeight});`;
  const art = card.firstElementChild.cloneNode(true);
  art.style.cssText = 'transform:none;filter:none;background:none;';
  face.append(art);
  dialog.append(face);

  const dx = cardRect.left + cardRect.width / 2 - toX;
  const dy = cardRect.top + cardRect.height / 2 - toY;
  dialog.animate([
    { transform: `translate(${dx}px, ${dy}px) rotate(-7deg) scale(${cardRect.width / to.width}, ${cardRect.height / to.height})` },
    { transform: 'none' },
  ], { duration: FLY_MS, easing: EASE, fill: 'backwards' });
  [...dialog.children].forEach((child) => {
    if (child === face) child.animate([{ opacity: 1 }, { opacity: 0 }], { duration: SWAP_MS, delay: SWAP_DELAY_MS, fill: 'forwards' });
    else child.animate([{ opacity: 0 }, { opacity: 1 }], { duration: SWAP_MS, delay: SWAP_DELAY_MS, fill: 'backwards' });
  });

  // 펜: 창 오른쪽에 내려앉는다. 그림이 -24도라 +48도를 더해 +24도(오른쪽 기울기)로 만든다
  const w = pen.offsetWidth;
  const h = pen.offsetHeight;
  const penRect = pen.getBoundingClientRect();
  const nib = element('div', 'feedback-ghost');
  nib.setAttribute('aria-hidden', 'true');
  nib.style.cssText = `position:fixed;left:0;top:0;width:${w}px;height:${h}px;z-index:1001;pointer-events:none;`;
  nib.append(...[...pen.children].map((child) => child.cloneNode(true)));
  card.closest('.landing-page').append(nib);
  const penX = Math.min(to.right + PEN_GAP, window.innerWidth - w / 2 - 8);
  nib.animate([
    { transform: `translate(${penRect.left + penRect.width / 2 - w / 2}px, ${penRect.top + penRect.height / 2 - h / 2}px) scale(${penRect.width / w})` },
    { transform: `translate(${penX - w / 2}px, ${toY - h / 2}px) rotate(48deg)` },
  ], { duration: FLY_MS, easing: EASE, fill: 'forwards' });

  card.style.visibility = 'hidden';
  pen.style.visibility = 'hidden';
  return { ghosts: [nib], sources: [card, pen], face };
}

export function openFeedbackDialog() {
  if (document.querySelector('.feedback-layer')) return;
  const opener = document.activeElement;
  const layer = element('div', 'feedback-layer');
  const dialog = element('section', 'feedback-dialog');
  dialog.setAttribute('role', 'dialog');
  dialog.setAttribute('aria-modal', 'true');
  dialog.setAttribute('aria-labelledby', 'feedback-title');
  dialog.tabIndex = -1;

  const title = element('h2', '', '의견 보내기');
  title.id = 'feedback-title';
  const lead = element('p', 'feedback-lead', '서비스를 쓰면서 불편했거나 바라는 점을 들려주세요.');
  const link = element('a', 'feedback-link primary-button', '의견 남기러 가기');
  link.href = FEEDBACK_URL;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  const words = element('div', 'feedback-words');
  words.append(title, lead, link);

  // 엽서 오른쪽 빈자리: 내로 · 남불
  const art = element('div', 'feedback-art');
  art.setAttribute('aria-hidden', 'true');
  ['naero', 'nambul'].forEach((who) => {
    const image = element('img');
    image.src = `/images/characters/standing/${who}.webp`;
    image.alt = '';
    image.width = 577; // 크기 힌트(늦게 떠도 창이 밀리지 않게)
    image.height = 800;
    art.append(image);
  });
  const body = element('div', 'feedback-body');
  body.append(words, art);

  const actions = element('div', 'feedback-actions');
  actions.append(button('닫기', () => shut(), 'secondary-button'));
  dialog.append(body, actions);
  layer.append(dialog);

  function shut() {
    fly?.ghosts.forEach((ghost) => ghost.remove());
    fly?.sources.forEach((node) => { node.style.visibility = ''; });
    layer.remove();
    document.body.classList.remove('feedback-open');
    document.documentElement.classList.remove('feedback-gutter');
    if (opener?.isConnected) opener.focus({ preventScroll: true });
  }

  layer.addEventListener('keydown', (event) => {
    event.stopPropagation();
    if (event.key === 'Escape') { event.preventDefault(); shut(); return; }
    if (event.key !== 'Tab') return;
    const items = [...dialog.querySelectorAll(FOCUSABLE)];
    if (!items.length) { event.preventDefault(); return; }
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  layer.addEventListener('mousedown', (event) => { if (event.target === layer) shut(); });

  // 스크롤을 잠그면 스크롤바가 사라져 화면 폭이 넓어지며 뒤 지면이 움찔하므로, 스크롤바가 있던 화면이면 그 자리를 비워 둔다
  const page = document.documentElement;
  page.classList.toggle('feedback-gutter', window.innerWidth > page.clientWidth);
  document.body.classList.add('feedback-open');
  document.body.append(layer);
  const fly = flyObjects(dialog);
  if (fly) {
    layer.animate([{ opacity: 0 }, { opacity: 1 }], { duration: FLY_MS, easing: 'ease-out', fill: 'backwards' });
    setTimeout(() => fly.face.remove(), SWAP_DELAY_MS + SWAP_MS + 50);
  }
  dialog.focus({ preventScroll: true });
}
