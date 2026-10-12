import { formatPenalty, formatDispositions } from '../../utils/judgmentFormat.js';

const directionSymbols = { UP: '↑', DOWN: '↓' };
const categoryLabels = { ALL_SAME: '셋 모두 같게 본 요소', ONLY_ME_MISSED: '나만 고려하지 않은 요소', DIVERGED: '판단이 엇갈린 요소' };
const subjectLabels = { USER: '내 판결', AI: 'AI 판결', COURT: '실제 판결' };

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

export async function renderComparisonPage(container, { caseId, caseHeader, api, navigate }) {
  const root = element('div', 'comparison-page');
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

  function judgmentCard(subjectType, judgment) {
    const card = element('section', `judgment-card judgment-card-${subjectType.toLowerCase()}`);
    card.append(element('span', 'judgment-card-label', subjectLabels[subjectType]), element('strong', 'judgment-card-penalty', formatPenalty(judgment)));
    const dispositions = formatDispositions(judgment);
    if (dispositions) card.append(element('p', 'judgment-card-sub', dispositions));
    if (judgment.summary) card.append(element('p', 'judgment-card-summary', judgment.summary));
    return card;
  }

  function matrixCell(direction) {
    return element('td', `matrix-direction${direction ? ` is-${direction.toLowerCase()}` : ' is-none'}`, direction ? directionSymbols[direction] : '—');
  }

  function draw(data) {
    const { preToFinal, judgments, matrix, ruleSentences } = data;

    const header = element('header', 'case-topbar');
    header.append(element('strong', 'case-title', `판결 비교 · ${caseHeader.title}`), button('다른 사건 체험', () => navigate('list'), 'secondary-button'));

    // 최상단: 처음 판단 → 직접 판결(REQ-057).
    const preSection = element('section', 'pre-to-final');
    preSection.append(element('h2', '', '처음 판단 → 직접 판결'));
    const compare = element('div', 'pre-to-final-compare');
    const preBox = element('div', 'pre-to-final-box');
    preBox.append(element('span', 'muted', '뉴스로 봤을 때'), element('strong', '', preToFinal.preJudgment.label));
    const arrow = element('span', 'pre-to-final-arrow', '→');
    arrow.setAttribute('aria-hidden', 'true');
    const finalBox = element('div', 'pre-to-final-box');
    finalBox.append(element('span', 'muted', '모두 보고 판결했을 때'), element('strong', '', preToFinal.finalJudgmentText));
    compare.append(preBox, arrow, finalBox);
    preSection.append(compare, element('p', 'pre-to-final-summary', preToFinal.summaryText));

    // 3열 판결 카드 + 한 줄 요약(REQ-058 · 109).
    const cards = element('div', 'judgment-cards');
    cards.append(judgmentCard('USER', judgments.USER), judgmentCard('AI', judgments.AI), judgmentCard('COURT', judgments.COURT));

    // 판단 요소 비교 매트릭스(REQ-060 · 061).
    const matrixSection = element('section', 'matrix-section');
    matrixSection.append(element('h2', '', '판단 요소 비교'), element('p', 'muted', '↑ 형량을 높이는 방향 / ↓ 낮추는 방향 / — 고려하지 않음'));
    const table = element('table', 'matrix-table');
    const thead = element('thead');
    const headRow = element('tr');
    ['판단 요소', '내 판결', 'AI 판결', '실제 판결', '분류'].forEach((text) => headRow.append(element('th', '', text)));
    thead.append(headRow);
    const tbody = element('tbody');
    for (const row of matrix) {
      const tr = element('tr');
      const rowLabel = element('th', 'matrix-row-label', row.label);
      rowLabel.setAttribute('scope', 'row');
      tr.append(rowLabel, matrixCell(row.user), matrixCell(row.ai), matrixCell(row.court));
      const categoryCell = element('td');
      categoryCell.append(element('span', `category-tag category-${row.category.toLowerCase().replace(/_/g, '-')}`, categoryLabels[row.category]));
      tr.append(categoryCell);
      tbody.append(tr);
    }
    table.append(thead, tbody);
    matrixSection.append(table);

    // 공통점 · 차이점: MVP는 매트릭스 기반 규칙 문장(REQ-063).
    const ruleSection = element('div', 'rule-sentences');
    const commonBox = element('section', 'rule-box');
    commonBox.append(element('h3', '', '공통점'));
    ruleSentences.common.forEach((text) => commonBox.append(element('p', '', text)));
    const diffBox = element('section', 'rule-box');
    diffBox.append(element('h3', '', '차이점'));
    ruleSentences.differences.forEach((text) => diffBox.append(element('p', '', text)));
    ruleSection.append(commonBox, diffBox);

    root.replaceChildren(header, preSection, cards, matrixSection, ruleSection);
  }

  async function load() {
    if (loading || !active()) return;
    loading = true;
    message('판결 비교 정보를 불러오는 중…');
    try {
      const data = await api.getComparison(caseId);
      if (active()) draw(data);
    } catch (error) {
      if (!active()) return;
      // 보통은 withErrorRedirect가 currentStatus에 맞는 화면으로 먼저 이동시킨다(app/errorRedirect.js).
      // 여기 오는 것은 래퍼가 이동하지 못한 경우다 — 화면 표(screenByStatus)에 없는 상태값이거나,
      // 이미 이 화면이라 라우터가 이동을 건너뛴 경우(예: 이 화면에서 보낸 요청이 지금 상태를 그대로 돌려줌).
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
