import { formatMonths } from '../../components/recommendedRangeBar.js';
import { renderAiRevealIntro, takeAiRevealIntro } from './aiRevealIntro.js';
import { formatPenalty, diffPhrase } from '../../utils/judgmentFormat.js';
import { renderStage, LAWS, LAW_TITLES } from '../../components/stage.js';
import { SCRIPTS } from '../../content/scripts.js';
import { restartMock, jumpActMock } from '../overview/overviewPage.js';
import { API_MODE } from '../../api/config.js';
import { renderCourtStage } from '../court/courtStage.js';
import { renderComparisonStage, mountStage, mountNewspaperStage } from '../comparison/comparisonStage.js';

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

  // 대본의 제목이 사건 제목과 같을 때만 무대를 쓴다(S-03 ~ S-06과 같은 규칙)
  const candidate = SCRIPTS[caseId];
  const script = candidate?.title === caseHeader.title ? candidate : undefined;

  // 판단 요소를 짚는 대사: 로마다 말투가 다르다(캐릭터 프로필 4-3, 재판 말투 경어 · 짧은 문장). 인자는 "정한다" 대신 "본다 · 살핀다"
  const FACTOR_LINES = {
    'law-scale': (label, how) => `'${label}', 이 사정은 ${how} 보겠습니다.`,
    'law-gavel': (label, how) => `'${label}'. ${how} 봅니다.`,
    'law-book': (label, how) => `기록을 한 번 더 보겠습니다. '${label}', ${how} 살피겠습니다.`,
  };
  // 6막 대본을 AI 판결로 채운다: {"factors": true}는 판단 요소마다 한 장면(저울 → 법봉 → 법전 순서로 돌아가며),
  // say "law"는 판사(셋이 함께 서면 로울, 발표는 발표하는 판사), {penalty}는 형량, {summary}는 AI 판결 한 줄 요약.
  // summary는 생성 계약대로 "~한 판단"(S-09 판결 카드와 같은 문장)이라 대사에서 "입니다"를 붙이고, 없으면(null) 그 줄을 뺀다.
  // 판단 요소가 하나도 없으면 "하나씩 짚어 보겠습니다" 대신 textNoFactors를 쓴다. 이름표는 내로처럼 아이콘 · 이름(AI 판사) · 직책(재판장 · 배석판사).
  // 발표하는 판사(캐릭터 프로필 5-3): 감경 비율이 1/3 미만이면 로봉, 2/3 초과면 로북, 그 사이(또는 요소 없음)면 로울
  function act6Script(data, speakerOverride) {
    const { judgment } = data;
    const factors = judgment.factors.filter(({ direction }) => direction === 'UP' || direction === 'DOWN');
    const down = factors.filter(({ direction }) => direction === 'DOWN').length;
    const ratio = down / (factors.length || 1);
    const speaker = speakerOverride ?? (!factors.length ? 'law-scale' : ratio < 1 / 3 ? 'law-gavel' : ratio > 2 / 3 ? 'law-book' : 'law-scale');
    const penalty = formatPenalty(judgment) + (judgment.suspensionMonths ? `에 집행유예 ${formatMonths(judgment.suspensionMonths)}` : '');
    const steps = script.acts['6'].steps.flatMap((step) => {
      if (step.factors) {
        return factors.map((factor, k) => {
          const who = LAWS[k % LAWS.length];
          return {
            say: who, law: 'three', role: LAW_TITLES[who], card: `factor-${k}`,
            text: FACTOR_LINES[who](factor.label, factor.direction === 'UP' ? '무겁게' : '가볍게'),
          };
        });
      }
      if (step.say !== 'law') return [step];
      const who = step.law === 'deliver' ? speaker : LAWS[0];
      const text = (!factors.length && step.textNoFactors ? step.textNoFactors : step.text).split('\n')
        .filter((line) => !line.includes('{summary}') || judgment.summary)
        .map((line) => line.replace('{penalty}', penalty).replace('{summary}', judgment.summary))
        .join('\n');
      return [{ ...step, say: who, role: LAW_TITLES[who], text }];
    });
    return { script: { ...script, acts: { ...script.acts, 6: { ...script.acts['6'], steps } } }, factors };
  }
  // 판단 요소 보드(흐름 목업): 짚은 요소가 한 줄씩 쌓이고, 줄 끝에 무겁게 · 가볍게를 단다. 지금 짚는 줄을 강조한다
  function factorBoard(factors, at) {
    const board = element('div', 'card-body ai-factor-board');
    board.append(element('h3', '', '판단 요소'));
    const list = element('ul');
    factors.slice(0, at + 1).forEach((factor, i) => {
      const item = element('li', i === at ? 'now' : '');
      if (i === at) item.setAttribute('aria-current', 'true');
      const up = factor.direction === 'UP';
      item.append(element('span', 'ai-factor-label', factor.label), element('span', `ai-factor-tag ${up ? 'up' : 'down'}`, up ? '↑ 무겁게' : '↓ 가볍게'));
      list.append(item);
    });
    board.append(list);
    return board;
  }
  // 발표 카드: 판결문처럼 종이 한 장(주문 · 이유 · 재판부). 내 판결과의 차이는 8막에서 비교한다
  function aiCard(data) {
    const { judgment, references } = data;
    const paper = element('div', 'card-body verdict-paper');
    const order = [`피고인을 ${formatPenalty(judgment)}에 처한다.`];
    if (judgment.suspensionMonths) order.push(`다만 이 판결 확정일부터 ${formatMonths(judgment.suspensionMonths)}간 위 형의 집행을 유예한다.`);
    paper.append(element('p', 'vp-kind', 'AI 판결'), element('h3', 'vp-title', '판결'),
      element('p', 'vp-case', `사건  ${caseHeader.title}`),
      element('h4', '', '주문'), ...order.map((line) => element('p', 'vp-order', line)),
      element('h4', '', '이유'), element('p', '', judgment.reasoning));
    if (references.length) paper.append(element('p', 'vp-refs', `참고한 자료 · ${references.join(', ')}`));
    const sign = element('p', 'vp-sign');
    sign.append(element('span', '', 'AI 판사 합의부'), element('span', '', '재판장 · 배석판사 2'));
    paper.append(sign);
    return paper;
  }
  // 6막 AI 판사의 합의(흐름 목업). 처음에 아이캐치(AI 판결을 기다리는 중 + 안내)를 띄우고,
  // 마지막 장면을 떠나면 실제 판결을 공개(API 11) · 조회(API 12)해 같은 법정에서 7막으로 이어 간다(주소만 S-08로)
  // test: { speaker, fromThink } — 발표 판사 테스트(목 데이터 전용): 고른 판사로 발표하게 하고 합의 장면부터 다시 본다
  function drawStage(data, test = {}) {
    root.classList.add('has-stage');
    const header = element('header', 'case-topbar');
    header.append(element('strong', 'case-title', caseHeader.title), element('span', 'case-badge', 'AI 판결'));
    const { script: filled, factors } = act6Script(data, test.speaker);
    const cards = { ai: aiCard(data) };
    factors.forEach((_, k) => { cards[`factor-${k}`] = factorBoard(factors, k); });
    const revealError = element('p', 'field-error');
    revealError.setAttribute('role', 'alert');
    const stage = renderStage({
      script: filled,
      order: ['6'],
      cards,
      ...(test.fromThink ? { start: (step) => step.law === 'think' } : {}),
      onRestart: restartMock(caseId),
      onJump: jumpActMock(caseId),
      onConfirm: async () => {
        revealError.textContent = '';
        try {
          await api.postCourtReveal(caseId);
          const court = await api.getCourtJudgment(caseId);
          if (active()) drawCourt(court);
        } catch (error) {
          if (!active()) return false;
          if (error?.code === 'INVALID_STATE') message(`지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`);
          else revealError.textContent = '실제 판결을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.';
        }
        return false;
      },
    });
    const main = element('div', 'review-main');
    main.append(element('h1', 'page-heading visually-hidden', 'AI 판결'), stage, revealError);
    if (API_MODE === 'mock') {
      // 테스트 기간용: 발표 판사를 골라 합의 → 발표 연출(좌우 판사가 가운데로 오는 것 포함)을 바로 본다
      const tools = element('div', 'stage-test');
      tools.append(element('span', '', '발표 판사 테스트(목 데이터)'));
      [['law-gavel', '로봉 · 왼쪽'], ['law-scale', '로울 · 가운데'], ['law-book', '로북 · 오른쪽']].forEach(([who, label]) => {
        tools.append(button(label, () => drawStage(data, { speaker: who, fromThink: true }), 'stage-test-button'));
      });
      main.append(tools);
    }
    const layout = element('main', 'review-layout');
    layout.append(main);
    root.replaceChildren(header, layout);
    if (test.fromThink) return;
    // 기다림은 아이캐치로(문구는 생성 · 불러오기 양쪽으로 읽히게, REQ-128)
    stage.interlude({
      title: 'AI 판결을 기다리는 중',
      sub: '곧 AI 판결이 도착해요.',
      k: 'AI 판결을 보기 전에',
      lines: ['이 AI 판결은 같은 사건 기록을 바탕으로 미리 만들어 둔 판결이에요.', '방금 내린 내 판결과 실제 재판부의 판결은 AI가 보지 않았어요.'],
      cast: [['naero', 'cheek:idle-thinking'], ['nambul', 'cheek:idle-thinking']],
      ms: 4200,
    });
  }

  // 7막 실제 판결: 6막 무대를 다시 불러오지 않고 이어 가며, 주소만 S-08로 바꾼다(새로고침하면 7막부터)
  function drawCourt(court) {
    const { stage, error } = renderCourtStage({
      script, data: court, caseId, api, navigate, active,
      onInvalid: (failure) => message(`지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${failure.currentStatus})`),
      // 8막도 같은 방식으로 이어 가고 주소만 S-09로 바꾼다
      onCompared: (comparison) => {
        mountNewspaperStage(root, { stage: renderComparisonStage({ script, data: comparison, caseId, caseTitle: caseHeader.title, navigate }) });
        navigate('comparison', {}, { replace: true, keepView: true });
      },
    });
    mountStage(root, { caseHeader, badge: '실제 판결', heading: '실제 판결', stage, extra: [error] });
    navigate('court', {}, { replace: true, keepView: true });
  }

  function draw(data) {
    if (script) { drawStage(data); return; }
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

  async function load(intro = false) {
    if (loading || !active()) return;
    loading = true;
    // 판결을 막 확정하고 온 경우(S-06 → S-07)에는 로딩 · 안내를 먼저 보여 준다 (7.7.4)
    if (!intro) message('AI 판결 정보를 불러오는 중…');
    try {
      const request = api.getAiJudgment(caseId);
      if (intro) {
        renderAiRevealIntro(root, {
          ready: request,
          onContinue: () => request.then((data) => { if (active()) draw(data); }),
        });
        await request; // 실패하면 아래 catch가 안내를 바꾼다
        return;
      }
      const data = await request;
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
  // 무대가 있으면 확정 직후 안내는 6막의 기다림 장면이 대신한다(표시는 지운다)
  const intro = takeAiRevealIntro(caseId);
  await load(intro && !script);
}
