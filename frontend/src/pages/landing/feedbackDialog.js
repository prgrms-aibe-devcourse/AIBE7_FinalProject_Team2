import { button, element } from '../../components/siteLayout.js';

// 의견 보내기 창 (FE-62). 메인 책상의 펜을 누르면 뜬다. 지금은 틀만 있다 — 의견을 받는 서버 API가 없어 "보내기"는
// 접수하지 않고 준비 중이라고 알린다. API가 생기면 submit()에서 보내고 성공 문구로 바꾸면 된다.
// 창이 열려 있는 동안 Tab은 창 안에서만 돌고, Esc · 바깥 누르기 · 닫기로 닫으면 누른 곳으로 포커스가 돌아온다.
const FOCUSABLE = 'button:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';
const MAX_LENGTH = 500;

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
  const lead = element('p', 'feedback-lead', '서비스를 쓰면서 불편했거나 바라는 점을 적어 주세요.');

  const field = element('div', 'feedback-field');
  const label = element('label', '', '의견');
  label.htmlFor = 'feedback-text';
  const text = element('textarea');
  text.id = 'feedback-text';
  text.name = 'feedback';
  text.maxLength = MAX_LENGTH;
  text.rows = 6;
  text.autocomplete = 'off';
  text.placeholder = '예: 판결 입력 화면에서 형량 단위가 헷갈렸어요…';
  text.setAttribute('aria-describedby', 'feedback-count feedback-status');
  const count = element('p', 'feedback-count', `0 / ${MAX_LENGTH}자`);
  count.id = 'feedback-count';
  field.append(label, text, count);

  const status = element('p', 'feedback-status');
  status.id = 'feedback-status';
  status.setAttribute('role', 'status');

  const actions = element('div', 'feedback-actions');
  const send = button('보내기', () => submit(), 'primary-button');
  actions.append(button('닫기', () => requestShut(), 'secondary-button'), send);
  dialog.append(title, lead, field, status, actions);
  layer.append(dialog);

  text.addEventListener('input', () => {
    count.textContent = `${text.value.length} / ${MAX_LENGTH}자`;
    text.removeAttribute('aria-invalid');
    status.textContent = '';
    warned = false;
  });

  function submit() {
    if (!text.value.trim()) {
      text.setAttribute('aria-invalid', 'true');
      status.textContent = '의견을 적어 주세요.';
      text.focus();
      return;
    }
    // 같은 문구를 다시 넣으면 낭독기가 읽지 않으므로 비웠다가 다음 프레임에 넣는다
    status.textContent = '';
    requestAnimationFrame(() => { status.textContent = '의견 받기는 아직 준비 중이에요. 곧 열려요. 적어 주신 내용은 닫으면 사라져요.'; });
  }

  // 쓰던 내용이 있으면 처음 닫을 때 한 번 알리고, 한 번 더 닫으면 닫는다
  let warned = false;
  function requestShut() {
    if (text.value.trim() && !warned) {
      warned = true;
      status.textContent = '쓰던 내용이 사라져요. 닫으려면 한 번 더 눌러 주세요.';
      return;
    }
    shut();
  }

  function shut() {
    layer.remove();
    document.body.classList.remove('feedback-open');
    if (opener?.isConnected) opener.focus({ preventScroll: true });
  }

  layer.addEventListener('keydown', (event) => {
    event.stopPropagation();
    if (event.key === 'Escape') { event.preventDefault(); requestShut(); return; }
    if (event.key !== 'Tab') return;
    const items = [...dialog.querySelectorAll(FOCUSABLE)];
    if (!items.length) { event.preventDefault(); return; }
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  layer.addEventListener('mousedown', (event) => { if (event.target === layer) requestShut(); });

  document.body.classList.add('feedback-open');
  document.body.append(layer);
  // 마우스 기기는 바로 입력할 수 있게 입력칸에, 터치 기기는 화상 키보드가 갑자기 올라오지 않게 창에 포커스를 둔다
  (window.matchMedia('(pointer: fine)').matches ? text : dialog).focus({ preventScroll: true });
}
