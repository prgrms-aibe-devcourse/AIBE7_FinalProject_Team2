import { button, element } from '../../components/siteLayout.js';
import { mountCaseReview } from './caseReviewPage.js';
import { reducedMotion } from '../../utils/motion.js';

// 8막 "리뷰하기" 버튼 · 독자 의견 엽서를 누르면 날아오는 엽서 한 장(FE-55). 화면(/cases/:caseId/opinion)과 같은 내용을 쓴다.
// 2막 사전 판단 엽서와 같은 연출(paperStage.css nambul-hand · postcard-in): 남불이 앞으로 나서며 손을 내밀고, 엽서가 그 손에서 날아와 가운데에서 펼쳐진다.
// 읽기 화면에서 지면의 독자 의견 엽서를 눌렀으면(남불이 없다) 그 엽서가 제자리에서 떠올라 가운데로 온다.
// 닫으면(쓰던 내용이 있으면 한 번 묻고) 엽서가 온 곳으로 돌아가며 사라지고, 누른 곳으로 포커스가 돌아온다. 뒤 무대는 그대로다(새 화면으로 가지 않는다)
const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';
const FLY_MS = 700;
const FLY_DELAY_MS = 220; // 남불이 손을 내미는 사이(2막과 같다)
const BACK_MS = 380;
const READY_WAIT_MS = 500; // 엽서 내용을 받는 동안 기다리는 최대 시간(받은 뒤에 날려야 날아오는 중에 엽서 크기가 바뀌지 않는다)

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
    document.body.classList.remove('case-review-open');
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
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  layer.addEventListener('mousedown', (event) => { if (event.target === layer) requestShut(); });

  document.body.classList.add('case-review-open');
  document.body.append(layer);
  dialog.focus({ preventScroll: true });
  const mounted = mountCaseReview(body, { caseId, api, navigate });
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
    layer.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 250, easing: 'ease-out' });
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
