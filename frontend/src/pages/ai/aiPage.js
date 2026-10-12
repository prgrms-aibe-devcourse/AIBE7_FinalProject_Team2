import { formatMonths } from '../../components/recommendedRangeBar.js';
import { formatPenalty, diffPhrase } from '../../utils/judgmentFormat.js';

const directionLabels = { UP: '가중 요소', DOWN: '감경 요소' };

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

export async function renderAiPage(container, { caseId, caseHeader, api, navigate }) {
  const root = element('div', 'ai-page');
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
    if (items.length) items.forEach((factor) => list.append(element('li', '', factor.label)));
    else list.append(element('li', 'muted', '고려한 요소가 없어요.'));
    group.append(list);
    return group;
  }

  function draw(data) {
    const { judgment, myJudgment, diffFromMine, references } = data;

    const header = element('header', 'case-topbar');
    header.append(element('strong', 'case-title', `AI 판결 · ${caseHeader.title}`), element('span', 'case-badge', '2 / 3 공개'));

    // 공개 진행 탭(와이어프레임 S-07): 내 판결은 이동하지 않는 정보, 실제 판결은 API 11로만 공개한다.
    const tabs = element('div', 'release-tabs');
    const myTab = element('div', 'release-tab');
    const myState = formatPenalty(myJudgment) + (myJudgment.suspensionMonths ? ` (집행유예 ${formatMonths(myJudgment.suspensionMonths)})` : '');
    myTab.append(element('span', 'release-tab-label', '내 판결'), element('span', 'release-tab-state', myState));
    const aiTab = element('div', 'release-tab is-active');
    aiTab.append(element('span', 'release-tab-label', 'AI 판결'), element('span', 'release-tab-state', '공개 중'));
    const courtTab = element('div', 'release-tab is-locked');
    courtTab.append(element('span', 'release-tab-label', '실제 판결'), element('span', 'release-tab-state', '아직 비공개'));
    tabs.append(myTab, aiTab, courtTab);

    const main = element('main', 'ai-layout');
    const highlight = element('section', 'ai-highlight');
    highlight.append(element('h2', 'ai-eyebrow', 'AI 판결 핵심'));
    const penaltyBox = element('div', 'penalty-box');
    penaltyBox.append(element('span', 'muted', '형벌 · 형량'), element('strong', 'penalty-value', formatPenalty(judgment)));
    if (judgment.suspensionMonths) penaltyBox.append(element('p', '', `집행유예 ${formatMonths(judgment.suspensionMonths)}`));
    highlight.append(penaltyBox);
    const diffBox = element('div', 'diff-box');
    diffBox.append(element('h3', '', '내 판결과의 차이'), element('p', 'diff-text', diffPhrase(diffFromMine)));
    highlight.append(diffBox);
    const refBox = element('div', 'reference-box');
    refBox.append(element('h3', '', '참고한 자료'));
    const refList = element('ul', 'reference-tags');
    if (references.length) references.forEach((text) => refList.append(element('li', 'reference-tag', text)));
    else refList.append(element('li', 'reference-tag muted', '등록된 참고 자료가 없어요.'));
    refBox.append(refList);
    highlight.append(refBox);

    const detail = element('section', 'ai-detail');
    detail.append(element('h2', '', '주요 판단 근거'), element('p', 'ai-reasoning', judgment.reasoning));
    const factorGrid = element('div', 'factor-grid');
    factorGrid.append(factorGroup('UP', judgment.factors), factorGroup('DOWN', judgment.factors));
    detail.append(factorGrid);
    main.append(highlight, detail);

    const footer = element('div', 'ai-footer');
    const revealError = element('p', 'field-error');
    revealError.setAttribute('role', 'alert');
    let revealing = false;
    const revealButton = button('실제 판결 확인하기', async () => {
      if (revealing || !active()) return;
      revealing = true;
      revealError.textContent = '';
      revealButton.disabled = true;
      revealButton.textContent = '확인하는 중…';
      try {
        await api.postCourtReveal(caseId);
        if (active()) navigate('court');
      } catch (error) {
        if (!active()) return;
        // 보통은 withErrorRedirect가 currentStatus에 맞는 화면으로 먼저 이동시킨다(app/errorRedirect.js).
        // 여기 오는 것은 래퍼가 이동하지 못한 경우다 — 화면 표(screenByStatus)에 없는 상태값이거나,
        // 이미 이 화면이라 라우터가 이동을 건너뛴 경우(예: 이 화면에서 보낸 요청이 지금 상태를 그대로 돌려줌).
        if (error?.code === 'INVALID_STATE') {
          message(`지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`);
          console.info('현재 상태:', error.currentStatus);
        } else {
          revealError.textContent = '실제 판결을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.';
          revealButton.disabled = false;
          revealButton.textContent = '실제 판결 확인하기';
        }
      } finally { revealing = false; }
    }, 'primary-button');
    footer.append(revealError, revealButton);

    root.replaceChildren(header, tabs, main, footer);
  }

  async function load() {
    if (loading || !active()) return;
    loading = true;
    message('AI 판결 정보를 불러오는 중…');
    try {
      const data = await api.getAiJudgment(caseId);
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
