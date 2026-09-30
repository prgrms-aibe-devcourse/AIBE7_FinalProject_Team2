import { renderProgressSidebar } from '../review/progressSidebar.js';
import { screenByStatus } from '../../utils/screenByStatus.js';

// S-03 사건 개요 · 사전 판단
export async function renderOverviewPage(container, { caseId, api, navigate }) {
  const root = document.createElement('div');
  root.className = 'review-page overview-page';
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  let loading = false;
  let submitting = false;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function button(text, action, className = 'secondary-button') {
    const node = element('button', className, text);
    node.type = 'button';
    node.addEventListener('click', action);
    return node;
  }
  // 이미 제출했다면(INVALID_STATE) 현재 상태의 화면으로 보낸다. (API 명세 1-6, REQ-103)
  function redirectByState(error) {
    const screen = screenByStatus[error.currentStatus];
    if (screen && screen !== 'overview') navigate(screen, { caseId });
    return Boolean(screen);
  }
  function showError(error) {
    if (!active()) return;
    if (error?.code === 'INVALID_STATE' && redirectByState(error)) return;
    const box = element('section', 'message-panel');
    box.setAttribute('role', 'alert');
    if (error?.code === 'CASE_NOT_FOUND') {
      box.append(element('p', '', '사건을 찾을 수 없어요.'), button('사건 목록으로', () => navigate('list')));
    } else {
      box.append(element('p', '', '정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'), button('다시 시도', load));
    }
    root.replaceChildren(box);
  }
  async function submit(rangeOptionId, trigger, notice) {
    if (submitting) return;
    submitting = true;
    trigger.disabled = true;
    trigger.setAttribute('aria-busy', 'true');
    notice.textContent = '';
    try {
      // MVP는 작용 요소(factorIds)를 보내지 않는다. (REQ-093 확장)
      await api.postPreJudgment(caseId, { rangeOptionId });
      if (active()) navigate('review', { caseId });
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'INVALID_STATE' && redirectByState(error)) return;
      trigger.disabled = false;
      trigger.removeAttribute('aria-busy');
      notice.textContent = '제출하지 못했어요. 잠시 후 다시 시도해 주세요.';
    } finally {
      submitting = false;
    }
  }
  function draw({ case: info, rangeOptions }) {
    const header = element('header', 'case-topbar');
    const progress = element('div', 'top-progress');
    const meter = element('progress');
    meter.max = 6;
    meter.value = 1;
    meter.setAttribute('aria-label', '진행 상황');
    progress.append(meter, element('span', '', '1 / 6'));
    header.append(button('← 목록', () => navigate('list'), 'back-button'),
      element('strong', 'case-title', info.title), element('span', 'case-badge', info.chargeName), progress);

    const main = element('div', 'review-main');
    main.append(element('h1', 'page-heading', '사건 개요 · 사전 판단'));

    const overview = element('section', 'overview-box');
    const facts = element('dl', 'overview-facts');
    [['죄명', info.chargeName], ['범죄 분류', info.crimeCategoryLabel]].forEach(([label, value]) => {
      const row = element('div');
      row.append(element('dt', '', label), element('dd', '', value));
      facts.append(row);
    });
    overview.append(element('h2', '', '사건 개요'), facts, element('p', 'overview-text', info.overview));

    const guide = element('p', 'guide-box', '지금은 뉴스에서 볼 수 있는 정도의 정보만 있어요. 기사로 이 사건을 접했다고 생각하고 판단해 보세요.');

    const form = element('form', 'pre-judgment-form');
    form.noValidate = true;
    const fieldset = element('fieldset');
    fieldset.append(element('legend', '', '이 사건, 어느 정도의 형량이 맞다고 생각하나요?'));
    const options = element('div', 'range-options');
    rangeOptions.forEach(({ rangeOptionId, label }) => {
      const item = element('label', 'range-option');
      const input = element('input');
      input.type = 'radio';
      input.name = 'rangeOptionId';
      input.value = String(rangeOptionId);
      input.addEventListener('change', () => { submitButton.disabled = false; });
      item.append(input, element('span', '', label));
      options.append(item);
    });
    fieldset.append(options);

    const warning = element('p', 'submit-warning', '제출하면 이 판단은 바꿀 수 없어요.');
    const notice = element('p', 'submit-error');
    notice.setAttribute('role', 'alert');
    const submitButton = element('button', 'primary-button', '판단 제출하고 사건 자세히 보기');
    submitButton.type = 'submit';
    submitButton.disabled = true;
    const actions = element('div', 'section-actions');
    actions.append(submitButton);
    form.append(fieldset, warning, notice, actions);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const checked = form.querySelector('input[name="rangeOptionId"]:checked');
      if (checked) submit(Number(checked.value), submitButton, notice);
    });

    main.append(overview, guide, form);
    const layout = element('main', 'review-layout');
    layout.append(main);
    renderProgressSidebar(layout, { lastReviewedStep: 0, openStep: 1 });
    root.replaceChildren(header, layout);
  }
  async function load() {
    if (loading || !active()) return;
    loading = true;
    const message = element('p', 'message-panel', '사건 정보를 불러오는 중…');
    message.setAttribute('role', 'status');
    root.replaceChildren(message);
    try {
      const data = await api.getOverview(caseId);
      if (active()) draw(data);
    } catch (error) { showError(error); }
    finally { loading = false; }
  }
  await load();
}
