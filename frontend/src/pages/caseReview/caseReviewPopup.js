import { button, element } from '../../components/siteLayout.js';
import { mountCaseReview } from './caseReviewPage.js';

// 8막 "리뷰하기" 버튼 · 독자 의견 엽서를 누르면 뜨는 팝업(FE-55). 화면(/cases/:caseId/opinion)과 같은 내용을 쓴다.
// 뒤 무대는 그대로 두고(새 화면으로 가지 않는다), 닫으면 누른 곳으로 포커스가 돌아온다
const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';

export function openCaseReviewPopup({ caseId, api, navigate }) {
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
  const close = button('닫기', () => shut(), 'secondary-button case-review-close');
  const body = element('div', 'case-review');
  dialog.append(title, body, close);
  layer.append(dialog);

  function shut() {
    layer.remove();
    document.body.classList.remove('case-review-open');
    if (opener?.isConnected) opener.focus({ preventScroll: true });
  }
  // 팝업 안의 키는 뒤 무대(← → Space로 넘기기)에 닿지 않게 여기서 끊는다. Tab은 팝업 안에서만 돈다
  layer.addEventListener('keydown', (event) => {
    event.stopPropagation();
    if (event.key === 'Escape') { event.preventDefault(); shut(); return; }
    if (event.key !== 'Tab') return;
    const items = [...dialog.querySelectorAll(FOCUSABLE)].filter((node) => !node.closest('[hidden]'));
    if (!items.length) { event.preventDefault(); return; }
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  layer.addEventListener('mousedown', (event) => { if (event.target === layer) shut(); });

  document.body.classList.add('case-review-open');
  document.body.append(layer);
  dialog.focus({ preventScroll: true });
  mountCaseReview(body, { caseId, api, navigate });
}
