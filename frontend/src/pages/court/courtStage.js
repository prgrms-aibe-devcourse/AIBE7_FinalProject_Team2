// 7막 실제 판결 무대(흐름 목업). 법정에서 판사 역(내로)이 피고인 역(남불)에게 판결을 선고하고, 판결문을 한 장씩 넘기며 읽는다.
// S-06(6막)에서 실제 판결을 공개한 뒤 무대를 이어 받고(주소만 S-07로), 재진입(S-07 주소)에서도 같은 무대를 연다.
// 마지막 장면을 떠나면 세 판결 비교를 공개(API 13) · 조회(API 14)해 8막으로 이어 간다(onCompared, 주소만 S-08로)
import { formatPenalty, formatDispositions } from '../../utils/judgmentFormat.js';
import { formatMonths } from '../../components/recommendedRangeBar.js';
import { renderStage } from '../../components/stage.js';
import { renderComparisonStage, mountNewspaperStage, sceneRect } from '../comparison/comparisonStage.js';
import { restartMock, jumpActMock } from '../../mocks/mockStageButtons.js';

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

// 판결문 한 장: 제목 · 내용 · 쪽 번호. 한 장에 한 섹션만 올려 화면의 글자 수를 줄인다
function paper(title, at, total, ...children) {
  const sheet = element('div', 'card-body court-paper');
  sheet.append(element('p', 'cp-page', `${at} / ${total}`), element('h3', 'cp-title', title), ...children);
  return sheet;
}

// 판결문 장(대본 card 이름 → 내용). 내용이 없는 장(발췌 · 쉬운 설명이 null)은 빼고, 그 장면도 대본에서 뺀다
function courtPages(data) {
  const { judgment, source } = data;
  const pages = [];
  const penalty = [element('p', 'cp-penalty', formatPenalty(judgment))];
  if (judgment.suspensionMonths) penalty.push(element('p', 'cp-sub', `집행유예 ${formatMonths(judgment.suspensionMonths)}`));
  if (formatDispositions(judgment)) penalty.push(element('p', 'cp-sub', `부가 처분 · ${formatDispositions(judgment)}`));
  if (judgment.summary) penalty.push(element('p', 'cp-summary', judgment.summary));
  pages.push(['court-penalty', '주문', penalty]);
  if (judgment.reasoning) pages.push(['court-reasoning', '판단 근거', [element('p', '', judgment.reasoning)]]);
  if (judgment.excerpt) {
    const quote = element('blockquote');
    quote.append(element('p', '', judgment.excerpt));
    pages.push(['court-excerpt', '판결문 중에서', [quote]]);
  }
  if (judgment.plainExplanation) pages.push(['court-plain', '쉽게 말하면', [element('p', '', judgment.plainExplanation)]]);
  const list = element('ul', 'cp-factors');
  judgment.factors.filter(({ direction }) => direction === 'UP' || direction === 'DOWN').forEach((factor) => {
    const item = element('li');
    const up = factor.direction === 'UP';
    item.append(element('span', '', factor.label), element('span', `ai-factor-tag ${up ? 'up' : 'down'}`, up ? '↑ 무겁게' : '↓ 가볍게'));
    list.append(item);
  });
  const last = [list];
  if (source?.sourceOrg) last.push(element('p', 'cp-source', `출처 · ${source.sourceOrg}`));
  pages.push(['court-factors', '판단 요소', last]);
  return Object.fromEntries(pages.map(([name, title, children], i) => [name, paper(title, i + 1, pages.length, ...children)]));
}

/**
 * @returns {{ stage: HTMLElement, error: HTMLElement }} 무대와 그 아래 오류 안내(세 판결 비교 공개 실패)
 */
// 7막 → 8막: 같은 화면에서 7막 장면 자리 그대로 신문으로 빠져나오고 주소만 S-08로 바꾼다
export function openComparison({ root, script, comparison, caseId, caseTitle, navigate, api }) {
  const fromRect = sceneRect(root);
  mountNewspaperStage(root, { stage: renderComparisonStage({ script, data: comparison, caseId, caseTitle, navigate, api, fromRect }) });
  navigate('comparison', {}, { replace: true, keepView: true });
}

export function renderCourtStage({ script, data, caseId, api, navigate, active, onInvalid, onCompared }) {
  const { judgment } = data;
  // 주문 문장(판결문 말투): "피고인을 징역 10년에 처한다." 집행유예면 유예 문장을 잇는다
  const order = `피고인을 ${formatPenalty(judgment)}에 처한다.${judgment.suspensionMonths
    ? ` 다만 이 판결 확정일부터 ${formatMonths(judgment.suspensionMonths)}간 위 형의 집행을 유예한다.` : ''}`;
  const cards = courtPages(data);
  const act = script.acts['7'];
  const steps = act.steps
    .filter((step) => !step.card || cards[step.card])
    .map((step) => (step.text ? { ...step, text: step.text.replace('{order}', order) } : step));
  const error = element('p', 'field-error');
  error.setAttribute('role', 'alert');
  const stage = renderStage({
    script: { ...script, acts: { ...script.acts, 7: { ...act, steps } } },
    order: ['7'],
    cards,
    onRestart: restartMock(caseId),
    onJump: jumpActMock(caseId),
    onConfirm: async () => {
      error.textContent = '';
      try {
        await api.postComparisonReveal(caseId);
        const comparison = await api.getComparison(caseId);
        if (active()) onCompared(comparison);
      } catch (failure) {
        if (!active()) return false;
        if (failure?.code === 'INVALID_STATE') onInvalid(failure);
        else error.textContent = '세 판결 비교를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
      }
      return false;
    },
  });
  return { stage, error };
}
