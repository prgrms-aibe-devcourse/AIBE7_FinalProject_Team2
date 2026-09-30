import { renderLawInfoBox } from '../../components/lawInfoBox.js';
import { renderRecommendedRangeBar } from '../../components/recommendedRangeBar.js';

export async function renderSummaryPage(container, { caseId, caseHeader, api, navigate }) {
  const root = document.createElement('div');
  root.className = 'summary-page';
  container.replaceChildren(root);
  const active = () => container.contains(root);
  let loading = false;
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
  function message(text, action) {
    const panel = element('section', 'message-panel');
    panel.setAttribute('role', 'status');
    panel.append(element('p', '', text));
    if (action) panel.append(action);
    root.replaceChildren(panel);
  }
  function draw(data) {
    if (data.status !== 'REVIEWED') {
      message('사건 정보를 모두 확인한 뒤 볼 수 있어요.', button('사건 정보 확인으로', () => navigate('review')));
      return;
    }
    const header = element('header', 'case-topbar');
    header.append(button('← 이전 단계', () => navigate('review'), 'back-button'), element('strong', 'case-title', caseHeader.title), element('span', 'case-badge', '최종 정리'));
    const progress = element('div', 'top-progress');
    const meter = element('progress');
    meter.max = 6;
    meter.value = 5;
    meter.setAttribute('aria-label', '최종 정리 진행');
    progress.append(meter, element('span', '', '5 / 6'));
    header.append(progress);
    const main = element('main', 'summary-main');
    const intro = element('div', 'summary-intro');
    intro.append(element('h1', '', '판결 전 최종 정리'), element('p', 'muted', '지금까지 확인한 내용을 한 번에 다시 검토한 뒤 판결로 넘어갑니다.'));
    const layout = element('div', 'summary-layout');
    const facts = element('div', 'summary-facts');
    const overview = element('section', 'summary-block');
    overview.append(element('h2', '', '사건 개요'));
    data.sections.find(({ step }) => step === 1)?.items.forEach((item) => overview.append(element('p', '', item.content)));
    const summary = element('section', 'summary-block');
    summary.append(element('h2', '', '핵심 사실 요약'));
    const list = element('ul', 'summary-list');
    data.summary.forEach((item) => list.append(element('li', '', item)));
    summary.append(list);
    const argumentsBox = element('section', 'summary-block');
    argumentsBox.append(element('h2', '', '양측 주장'));
    const grid = element('div', 'argument-grid');
    data.sections.find(({ step }) => step === 3)?.items.forEach((item) => {
      const card = element('section', 'argument-card');
      card.append(element('h3', '', item.title), element('p', '', item.content));
      grid.append(card);
    });
    argumentsBox.append(grid);
    facts.append(overview, summary, argumentsBox);
    const law = element('aside', 'summary-law');
    law.setAttribute('aria-label', '법률 및 양형기준 참고');
    renderLawInfoBox(law, { law: data.law });
    renderRecommendedRangeBar(law, { law: data.law });
    layout.append(facts, law);
    const actions = element('div', 'summary-actions');
    actions.append(button('전체 기록 다시 보기', () => navigate('review')), button('판결 내리러 가기', () => navigate('verdict'), 'primary-button'));
    main.append(intro, layout, actions);
    root.replaceChildren(header, main);
  }
  async function load() {
    if (loading || !active()) return;
    loading = true;
    message('사건 정보를 불러오는 중…');
    try {
      const data = await api.getReview(caseId);
      if (active()) draw(data);
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'INVALID_STATE') {
        message(`지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`);
        console.info('현재 상태:', error.currentStatus);
      } else {
        message('정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', button('다시 시도', load));
      }
    } finally { loading = false; }
  }
  await load();
}
