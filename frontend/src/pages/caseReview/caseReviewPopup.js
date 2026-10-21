import { button, element } from '../../components/siteLayout.js';
import { mountCaseReview } from './caseReviewPage.js';
import { reducedMotion } from '../../utils/motion.js';

// 8막 "엽서 쓰기" 버튼 · 지면의 엽서를 누르면 날아오는 엽서 한 장(FE-55). 화면(/cases/:caseId/opinion)과 같은 내용을 쓴다.
// 2막 사전 판단 엽서와 같은 연출(paperStage.css nambul-hand · postcard-in): 남불이 앞으로 나서며 손을 내밀고, 엽서가 그 손에서 날아와 가운데에서 펼쳐진다.
// 읽기 화면에서 지면의 엽서를 눌렀으면(남불이 없다) 그 엽서가 제자리에서 떠올라 가운데로 온다.
// 닫으면(쓰던 내용이 있으면 한 번 묻고) 엽서가 온 곳으로 돌아가며 사라지고, 누른 곳으로 포커스가 돌아온다. 뒤 무대는 그대로다(새 화면으로 가지 않는다)
const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';
const FLY_MS = 700;
const FLY_DELAY_MS = 220; // 남불이 손을 내미는 사이(2막과 같다)
const BACK_MS = 380;
const READY_WAIT_MS = 500; // 엽서 내용을 받는 동안 기다리는 최대 시간(받은 뒤에 날려야 날아오는 중에 엽서 크기가 바뀌지 않는다)

// 엽서가 떠 있는 동안 뒤 화면 스크롤을 잠근다. 잠그면 스크롤바가 사라져 화면 폭이 넓어지며 뒤 지면이 다시 계산돼 움찔하므로,
// 스크롤바가 있던 화면이면 그 자리를 비워 둔다(html.case-review-gutter, caseReview.css). 스크롤바가 없던 화면은 건드리지 않는다
function lockScroll() {
  const root = document.documentElement;
  root.classList.toggle('case-review-gutter', window.innerWidth > root.clientWidth);
  document.body.classList.add('case-review-open');
}
function unlockScroll() {
  document.body.classList.remove('case-review-open');
  document.documentElement.classList.remove('case-review-gutter');
}

/**
 * @param {{ caseId, api, navigate, from?: Element, hand?: boolean }} options
 *   from: 엽서가 나오는 곳. hand면 남불 그림(.spr.b)이라 손 자리에서, 아니면 그 요소(지면의 엽서) 크기 그대로에서 날아온다
 */
export function openCaseReviewPopup({ caseId, api, navigate, from, hand = false }) {
  if (document.querySelector('.case-review-layer')) return;
  const opener = document.activeElement;
  const layer = element('div', 'case-review-layer');
  const dialog = element('section', 'case-review-dialog');
  dialog.setAttribute('role', 'dialog');
  dialog.setAttribute('aria-modal', 'true');
  dialog.setAttribute('aria-labelledby', 'case-review-dialog-title');
  dialog.tabIndex = -1;
  const title = element('h2', 'visually-hidden', '독자 엽서');
  title.id = 'case-review-dialog-title';
  const close = button('닫기', () => requestShut(), 'secondary-button case-review-close');
  const body = element('div', 'case-review');
  dialog.append(title, body, close);
  layer.append(dialog);

  let closing = false;
  let away = null; // 엽서가 출발한 자리(돌아갈 때 쓴다)
  // 쓰던 엽서(별점 · 의견)가 있으면 닫기 전에 한 번 묻는다. 빈 엽서 · 이미 보낸 엽서는 바로 닫힌다 (PR #190 리뷰 반영)
  const hasDraft = () => Boolean(body.querySelector('.case-review-field textarea')?.value.trim() || body.querySelector('.case-review-stars input:checked'));
  let asking = null;
  function requestShut() {
    if (closing) return;
    if (asking) return;
    if (!hasDraft()) { shut(); return; }
    asking = element('div', 'case-review-confirm');
    asking.setAttribute('role', 'group');
    asking.setAttribute('aria-label', '닫기 확인');
    const keep = button('계속 쓰기', () => keepWriting(), 'secondary-button');
    asking.append(element('p', '', '쓰던 엽서가 사라져요. 닫을까요?'), keep, button('닫기', () => shut(), 'primary-button'));
    close.replaceWith(asking);
    asking.scrollIntoView({ block: 'nearest' }); // 닫기는 엽서 맨 아래라 스크롤 밖일 수 있다
    keep.focus({ preventScroll: true });
  }
  function keepWriting() {
    asking?.replaceWith(close);
    asking = null;
    (body.querySelector('.case-review-field textarea') ?? close).focus({ preventScroll: true });
  }
  async function shut() {
    if (closing) return;
    closing = true;
    if (away && !reducedMotion()) {
      const timing = { duration: BACK_MS, easing: 'cubic-bezier(.5, 0, .75, 0)', fill: 'forwards' };
      await Promise.all([
        dialog.animate([{ opacity: 1, transform: 'none' }, { opacity: 0, transform: away }], timing).finished,
        layer.animate([{ opacity: 1 }, { opacity: 0 }], timing).finished,
      ]).catch(() => {});
    }
    layer.remove();
    unlockScroll();
    window.removeEventListener('popstate', dispose);
    if (opener?.isConnected) opener.focus({ preventScroll: true });
  }
  // 엽서 안의 키는 뒤 무대(← → Space로 넘기기)에 닿지 않게 여기서 끊는다. Tab은 엽서 안에서만 돈다
  layer.addEventListener('keydown', (event) => {
    event.stopPropagation();
    if (event.key === 'Escape') { event.preventDefault(); if (asking) keepWriting(); else requestShut(); return; } // 묻는 중의 Esc는 계속 쓰기
    if (event.key !== 'Tab') return;
    const items = [...dialog.querySelectorAll(FOCUSABLE)].filter((node) => !node.closest('[hidden]'));
    if (!items.length) { event.preventDefault(); return; }
    const first = items[0];
    const last = items[items.length - 1];
    // 별점 라디오는 Tab 순서에 선택된 한 개만 들어가므로, 같은 그룹이면 첫 항목으로 본다
    const atFirst = document.activeElement === first || (first.type === 'radio' && document.activeElement?.name === first.name);
    if (event.shiftKey && (atFirst || document.activeElement === dialog)) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  layer.addEventListener('mousedown', (event) => { if (event.target === layer) requestShut(); });

  lockScroll();
  document.body.append(layer);
  dialog.focus({ preventScroll: true });
  // 엽서 안에서 다른 화면으로 가면(다른 사건 보러 가기 · 체험하러 가기) 엽서 층을 바로 걷고 간다(남겨 두면 새 화면을 덮는다)
  const dispose = () => { closing = true; layer.remove(); unlockScroll(); window.removeEventListener('popstate', dispose); };
  window.addEventListener('popstate', dispose); // 브라우저 뒤로 가기로 화면이 바뀔 때도 엽서 층을 남기지 않는다
  const leave = (...args) => { dispose(); return navigate(...args); };
  const mounted = mountCaseReview(body, { caseId, api, navigate: leave });
  if (!reducedMotion()) {
    // 남불은 바로 손을 내밀고, 엽서는 내용이 준비되면(늦으면 READY_WAIT_MS 뒤) 날아온다
    const handing = hand && from?.getBoundingClientRect().width;
    if (handing) {
      from.classList.remove('delivering');
      void from.offsetWidth;
      from.classList.add('delivering');
    }
    const handedAt = performance.now();
    dialog.style.opacity = '0';
    if (handing) {
      // 남불이 손을 내미는 동안은 어두운 가림막을 걸지 않는다(처음부터 걸면 남불이 가림막 뒤에 가려져 움직임이 보이지 않는다).
      // 손을 내민 뒤 엽서가 날아올 즈음 서서히 어두워진다
      const dim = getComputedStyle(layer).backgroundColor;
      layer.animate([{ backgroundColor: 'transparent' }, { backgroundColor: dim }], { duration: 450, delay: FLY_DELAY_MS, easing: 'ease-out', fill: 'backwards' });
    } else layer.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 250, easing: 'ease-out' });
    Promise.race([mounted, new Promise((done) => { setTimeout(done, READY_WAIT_MS); })]).then(() => {
      if (closing || !layer.isConnected) return;
      dialog.style.opacity = '';
      away = fly(handing ? Math.max(0, FLY_DELAY_MS - (performance.now() - handedAt)) : 0);
    });
  }

  // 엽서가 날아온다. 출발 자리를 transform 한 줄로 돌려준다(닫을 때 그리로 돌아간다)
  function fly(delay) {
    const to = dialog.getBoundingClientRect();
    const rect = from?.getBoundingClientRect();
    const center = { x: to.left + to.width / 2, y: to.top + to.height / 2 };
    let start;
    if (rect?.width && hand) {
      // 남불 손 자리(2막 deliver()와 같은 비율)에서 작게 접힌 채 기울어져 나온다
      start = `translate(${rect.left + rect.width * 0.3 - center.x}px, ${rect.top + rect.height * 0.45 - center.y}px) scale(.3) rotate(-14deg)`;
    } else if (rect?.width) {
      // 지면의 엽서가 그 크기 그대로 떠오른다
      start = `translate(${rect.left + rect.width / 2 - center.x}px, ${rect.top + rect.height / 2 - center.y}px) scale(${rect.width / to.width}) rotate(-3deg)`;
    } else {
      start = 'translate(0, 40vh) scale(.5) rotate(-8deg)'; // 출발할 곳이 보이지 않으면 화면 아래에서
    }
    dialog.animate([
      { opacity: hand ? 0 : 1, transform: start },
      { opacity: 1, offset: 0.25 },
      { opacity: 1, transform: 'none' },
    ], { duration: FLY_MS, delay, easing: 'cubic-bezier(.25, .8, .25, 1)', fill: 'backwards' });
    return start;
  }
}
