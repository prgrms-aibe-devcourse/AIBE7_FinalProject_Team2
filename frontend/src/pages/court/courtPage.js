import { formatPenalty, formatDispositions } from '../../utils/judgmentFormat.js';

const directionLabels = { UP: '재판부 가중 요소', DOWN: '재판부 감경 요소' };

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

export async function renderCourtPage(container, { caseId, caseHeader, api, navigate }) {
  const root = element('div', 'court-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  let loading = false;

  function message(text, action) {
    const panel = element('section', 'message-panel');
    panel.setAttribute('role', 'status');
    panel.append(element('p', '', text));
    if (action) panel.append(action);
    root.replaceChildren(panel);
  }

  function factorGroup(direction, factors) {
    const group = element('div', `factor-group factor-group-${direction.toLowerCase()}`);
    group.append(element('h3', '', directionLabels[direction]));
    const list = element('ul', 'factor-list-plain');
    const items = factors.filter((factor) => factor.direction === direction);
    if (items.length) items.forEach((factor) => {
      const item = element('li');
      item.append(element('span', '', factor.label));
      if (factor.evidence) item.append(element('p', 'factor-evidence', factor.evidence));
      list.append(item);
    });
    else list.append(element('li', 'muted', '고려한 요소가 없어요.'));
    group.append(list);
    return group;
  }

  function draw(data) {
    const { judgment, myJudgment, aiJudgment } = data;

    const header = element('header', 'case-topbar');
    header.append(element('strong', 'case-title', `실제 판결 · ${caseHeader.title}`), element('span', 'case-badge', '3 / 3 공개'));

    // 강조 배너: 실제 형량 + 집행유예·부가 처분, 내·AI 판결 형량(와이어프레임 S-08).
    const banner = element('section', 'court-banner');
    const bannerMain = element('div', 'banner-main');
    bannerMain.append(element('span', 'muted', '실제 법원의 판결'), element('strong', 'banner-penalty', formatPenalty(judgment)));
    const dispositions = formatDispositions(judgment);
    if (dispositions) bannerMain.append(element('p', 'banner-sub', dispositions));
    const bannerCompare = element('div', 'banner-compare');
    bannerCompare.append(element('p', '', `내 판결: ${formatPenalty(myJudgment)}`), element('p', '', `AI 판결: ${formatPenalty(aiJudgment)}`));
    banner.append(bannerMain, bannerCompare);

    const main = element('main', 'court-main');
    const reasoning = element('section', 'court-reasoning');
    reasoning.append(element('h2', '', '재판부 판단 근거'), element('p', '', judgment.reasoning));
    const excerpt = element('blockquote', 'court-excerpt');
    excerpt.append(element('p', '', judgment.excerpt));
    const plain = element('section', 'court-plain');
    plain.append(element('h3', '', '쉽게 말하면'), element('p', '', judgment.plainExplanation));
    const factorGrid = element('div', 'factor-grid');
    factorGrid.append(factorGroup('UP', judgment.factors), factorGroup('DOWN', judgment.factors));
    main.append(reasoning, excerpt, plain, factorGrid);

    const footer = element('div', 'court-footer');
    const compareError = element('p', 'field-error');
    compareError.setAttribute('role', 'alert');
    let comparing = false;
    const compareButton = button('세 판결 비교 보기', async () => {
      if (comparing || !active()) return;
      comparing = true;
      compareError.textContent = '';
      compareButton.disabled = true;
      compareButton.textContent = '불러오는 중…';
      try {
        await api.postComparisonReveal(caseId);
        if (active()) navigate('comparison');
      } catch (error) {
        if (!active()) return;
        if (error?.code === 'INVALID_STATE') {
          message(`지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`);
          console.info('현재 상태:', error.currentStatus);
        } else {
          compareError.textContent = '세 판결 비교를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
          compareButton.disabled = false;
          compareButton.textContent = '세 판결 비교 보기';
        }
      } finally { comparing = false; }
    }, 'primary-button');
    footer.append(compareError, compareButton);

    root.replaceChildren(header, banner, main, footer);
  }

  async function load() {
    if (loading || !active()) return;
    loading = true;
    message('실제 판결 정보를 불러오는 중…');
    try {
      const data = await api.getCourtJudgment(caseId);
      if (active()) draw(data);
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'INVALID_STATE') {
        // AI 판결 미확인이면 S-07로 이동한다(IA 9장, REQ-050 순서 유지).
        message('먼저 AI 판결을 확인해 주세요.', button('AI 판결 확인하러 가기', () => navigate('ai'), 'primary-button'));
        console.info('현재 상태:', error.currentStatus);
      } else {
        message('정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', button('다시 시도', load));
      }
    } finally { loading = false; }
  }
  await load();
}
