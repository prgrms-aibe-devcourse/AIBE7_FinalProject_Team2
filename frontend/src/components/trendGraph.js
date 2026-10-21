// 성향 변화 흐름(FE-70) — 마이페이지 성향카드 화면과 내 결과 지면(FE-75)이 함께 쓴다.
// 축마다 작은 꺾은선 그래프(가로 = 테스트 → 체험 순서, 세로 = 위 관대 · 아래 엄격, 가운데 줄 = 반반) + 같은 값의 표
import { element, mockTag } from './siteLayout.js';
import { AXES } from '../utils/personalityAxes.js';
import { TREND_RECENT, trendSlotOrders, axisChange, axisTrend, leanText } from '../utils/myPageView.js';

// 축마다 흐름 점(성향 테스트 → 체험 순서)과 변경폭(처음 → 현재 판결 성향). 변경폭 정리 · 변화 흐름 그래프가 함께 쓴다(FE-70)
// data: 마이페이지 응답(P-5)
export function trendsOf(data) {
  const history = data.history ?? [];
  return AXES.map((axis) => {
    const points = axisTrend(history, axis.key, data.personality?.axisScores);
    return { axis, points, change: axisChange(points, axis, data.judgmentPersonality?.axisScores?.[axis.key]) };
  });
}

const SVG_NS = 'http://www.w3.org/2000/svg';
function svgNode(tag, attrs = {}, text) {
  const node = document.createElementNS(SVG_NS, tag);
  Object.entries(attrs).forEach(([key, value]) => node.setAttribute(key, String(value)));
  if (text != null) node.textContent = text;
  return node;
}
const CHART = { width: 320, height: 160, axis: 34, left: 52, right: 22, top: 10, bottom: 30 }; // axis: 세로 눈금 글자 자리(이 왼쪽), left: 첫 점 자리, bottom: 가로 눈금 글자 자리
// 세로 눈금: 툴팁 · 표(leanText)와 같이 기운 쪽 비중으로 읽는다. 위 끝 관대 100, 가운데 50(반반), 아래 끝 엄격 100
const Y_TICKS = [[1, '100'], [0.75, '75'], [0.5, '50'], [0.25, '75'], [0, '100']];
const FOCUS_BAND = 22; // 강조한 칸(이 사건) 띠 폭

function trendChart({ axis, points }, slots) {
  const figure = element('figure', 'trend-chart');
  const caption = element('figcaption', 'trend-title');
  const ends = element('span', 'trend-ends'); // 위 · 아래 끝이 어느 쪽인지. 그래프 안에 두면 끝에 찍힌 점과 겹친다
  ends.append(element('span', '', `↑ ${axis.lenient.name}`), element('span', '', `↓ ${axis.strict.name}`));
  caption.append(element('span', '', axis.label), ends);
  figure.append(caption);
  if (points.length === 0) {
    figure.append(element('p', 'axis-empty', '체험 자료가 아직 없어요'));
    return figure;
  }
  const { width, height, axis: axisX, left, right, top, bottom } = CHART;
  const plotBottom = height - bottom;
  const xOf = (order) => {
    const index = slots.findIndex((slot) => slot.order === order);
    return slots.length === 1 ? (axisX + width) / 2 : left + (index * (width - left - right)) / (slots.length - 1);
  };
  const yOf = (ratio) => plotBottom - ratio * (plotBottom - top);
  const chart = svgNode('svg', { viewBox: `0 0 ${width} ${height}`, class: 'trend-svg', role: 'img' });
  chart.setAttribute('aria-label', `${axis.label} 흐름: ${points.map((point) => `${point.title} ${leanText(point.ratio, axis)}`).join(', ')}`);
  const middle = yOf(0.5);
  chart.append(
    svgNode('rect', { x: axisX, y: top, width: width - axisX, height: middle - top, class: 'trend-zone is-lenient' }),
    svgNode('rect', { x: axisX, y: middle, width: width - axisX, height: plotBottom - middle, class: 'trend-zone is-strict' }),
  );
  // 강조한 칸(내 결과 지면의 이 사건): 세로 띠를 깔아 그 칸의 점과 눈금이 먼저 읽히게 한다
  const focus = slots.find((slot) => slot.isFocus);
  if (focus) chart.append(svgNode('rect', { x: xOf(focus.order) - FOCUS_BAND / 2, y: top, width: FOCUS_BAND, height: plotBottom - top, class: 'trend-focus' }));
  Y_TICKS.forEach(([ratio, label]) => {
    const y = yOf(ratio);
    if (ratio !== 0.5) chart.append(svgNode('line', { x1: axisX, x2: width, y1: y, y2: y, class: 'trend-grid-line' }));
    chart.append(svgNode('text', { x: axisX - 6, y, class: 'trend-tick', 'text-anchor': 'end', 'dominant-baseline': 'middle' }, label));
  });
  chart.append(svgNode('line', { x1: axisX, x2: width, y1: middle, y2: middle, class: 'trend-mid' }));
  slots.forEach((slot) => chart.append(svgNode('text', { x: xOf(slot.order), y: height - 10, class: `trend-tick${slot.isFocus ? ' is-focus' : ''}`, 'text-anchor': 'middle' }, slot.label)));
  // 체험 점은 실선으로 잇고, 현재(최근 3건 평균)는 체험이 아니라 평균이라 마지막 체험 점에서 점선으로 잇는다
  const steps = points.filter((point) => !point.isCurrent);
  const current = points.find((point) => point.isCurrent);
  const at = (point) => `${xOf(point.order)},${yOf(point.ratio)}`;
  if (steps.length > 1) chart.append(svgNode('polyline', { points: steps.map(at).join(' '), class: 'trend-line' }));
  if (current && steps.length > 0) chart.append(svgNode('polyline', { points: `${at(steps[steps.length - 1])} ${at(current)}`, class: 'trend-line is-current' }));

  const tip = element('div', 'trend-tip');
  tip.setAttribute('aria-hidden', 'true'); // 같은 내용은 그래프 이름 · 표가 읽어 준다
  const show = (point, hit) => {
    tip.replaceChildren(element('strong', '', point.title), element('span', '', leanText(point.ratio, axis)));
    const box = figure.getBoundingClientRect();
    const dot = hit.getBoundingClientRect();
    tip.style.left = `${dot.left + dot.width / 2 - box.left}px`;
    tip.style.top = `${dot.top - box.top}px`;
    tip.classList.add('is-shown');
  };
  points.forEach((point) => {
    const x = xOf(point.order);
    const y = yOf(point.ratio);
    const isFocus = focus?.order === point.order;
    chart.append(svgNode('circle', { cx: x, cy: y, r: point.isCurrent || isFocus ? 5.5 : 4.5, class: `trend-dot${point.isTest ? ' is-test' : ''}${point.isCurrent ? ' is-current' : ''}${isFocus ? ' is-focus' : ''}` }));
    const hit = svgNode('circle', { cx: x, cy: y, r: 16, class: 'trend-hit' }); // 점보다 넉넉한 마우스 자리
    hit.addEventListener('pointerenter', () => show(point, hit));
    hit.addEventListener('pointerdown', () => show(point, hit)); // 터치: 누르면 보이고, 그래프의 빈 곳을 누르면 닫힌다
    hit.addEventListener('pointerleave', (event) => { if (event.pointerType === 'mouse') tip.classList.remove('is-shown'); });
    chart.append(hit);
  });
  chart.addEventListener('pointerdown', (event) => { if (!event.target.classList.contains('trend-hit')) tip.classList.remove('is-shown'); });
  figure.append(chart, tip);
  return figure;
}

// 그래프와 같은 값을 표로(스크린리더 · 키보드 · 정확한 수치 확인용)
function trendTable(trends, slots) {
  const details = element('details', 'trend-table');
  details.append(element('summary', '', '표로 보기'));
  const table = element('table');
  const headRow = element('tr');
  headRow.append(element('th', '', '시점'), ...trends.map(({ axis }) => element('th', '', axis.label)));
  const head = element('thead');
  head.append(headRow);
  const body = element('tbody');
  slots.forEach((slot) => {
    const line = element('tr', slot.isFocus ? 'is-focus' : undefined);
    const when = element('th', '', slot.isFocus ? `${slot.title} (이 사건)` : slot.title);
    when.scope = 'row';
    line.append(when, ...trends.map(({ axis, points }) => {
      const point = points.find((item) => item.order === slot.order);
      return element('td', '', point ? leanText(point.ratio, axis) : '—');
    }));
    body.append(line);
  });
  table.append(head, body);
  details.append(table);
  return details;
}

// judgment: 현재 판결 성향(맨 오른쪽 "현재" 칸). mock: 목 모드면 안내에 "목데이터"
// focus: 강조할 체험 순서(history의 1부터 번호). 내 결과 지면에서 "이 사건" 칸을 띠 · 진한 눈금으로 보여 준다(FE-75)
// hint({ hasTest, focused }): 그래프 위 안내 문구를 바꿀 때(내 결과 지면은 기사 문체)
// table: false면 "표로 보기"를 싣지 않는다(내 결과 지면). 값은 그래프 이름(aria-label)에 그대로 있다
export function renderTrend(trends, history, judgment, { mock = false, focus, hint: hintText, table = true } = {}) {
  const box = element('div', 'my-detail');
  if (history.length === 0) {
    box.append(element('p', 'my-hint', '사건을 하나 마치면 첫 점이 찍혀요.'));
    return box;
  }
  // 가로 칸: 시작점(테스트, 없으면 첫 체험) + 최근 체험 10건. 값이 없는 칸도 자리는 남겨 축끼리 같은 칸에 줄을 맞춘다
  const hasTest = trends.some(({ points }) => points[0]?.isTest);
  const orders = trendSlotOrders(history.length, hasTest, focus);
  // 눈금 숫자는 보이는 체험끼리 완료한 순서대로 1부터 매긴다(앞의 체험이 빠져도 숫자가 건너뛰지 않는다)
  const experienceOrders = orders.filter((order) => order > 0);
  const slots = orders.map((order) => {
    if (order === 0) return { order, label: '테스트', title: '성향 테스트' };
    const seq = experienceOrders.indexOf(order) + 1;
    return { order, label: String(seq), title: `${seq}. ${history[order - 1].title}`, isFocus: order === focus };
  });
  // 맨 오른쪽 "현재" 칸: 현재 판결 성향(최근 3건 평균, 변경폭의 지금과 같은 값). 축 값이 없으면 칸만 남는다
  const currentScores = judgment?.axisScores;
  if (currentScores) slots.push({ order: 'now', label: '현재', title: `현재 · 최근 체험 ${judgment.experienceCount}건 평균` });
  const titleOf = new Map(slots.map((slot) => [slot.order, slot.title]));
  const shown = trends.map((trend) => {
    const points = trend.points.filter((point) => titleOf.has(point.order)).map((point) => ({ ...point, title: titleOf.get(point.order) }));
    const now = currentScores?.[trend.axis.key];
    if (typeof now === 'number') points.push({ order: 'now', ratio: now, title: titleOf.get('now'), isCurrent: true });
    return { ...trend, points };
  });
  const focused = slots.some((slot) => slot.isFocus);
  const hint = element('p', 'my-hint', hintText?.({ hasTest, focused })
    ?? `${hasTest ? '성향 테스트와' : '첫 체험과'} 최근 체험 ${TREND_RECENT}건까지 보여요. 가운데 줄이 반반이고, 숫자는 사건을 마친 순서, 현재는 최근 체험 3건의 평균이에요. 점을 누르거나 마우스를 올리면 사건과 비중이 보여요.`);
  if (mock) hint.append(mockTag());
  const grid = element('div', 'trend-grid');
  shown.forEach((trend) => grid.append(trendChart(trend, slots)));
  box.append(hint, grid);
  if (table) box.append(trendTable(shown, slots));
  return box;
}
