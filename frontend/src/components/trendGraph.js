// 성향 변화 흐름(FE-70) — 마이페이지 성향카드 화면과 내 결과 지면(FE-75)이 함께 쓴다.
// 축마다 작은 꺾은선 그래프(가로 = 테스트 → 체험 순서, 세로 = 위 관대 · 아래 엄격, 가운데 줄 = 반반) + 같은 값의 표
import { element, mockTag } from './siteLayout.js';
import { AXES } from '../utils/personalityAxes.js';
import { TREND_RECENT, trendSlotOrders, withRetakeSlot, axisChange, axisTrend, leanText } from '../utils/myPageView.js';

// 시작점 테스트 점수: 초심(첫 테스트, FE-77). 재응시해도 바뀌지 않는다. 초심이 첫 판결이면 테스트 점이 없다.
// 서버가 아직 initialPersonality를 주지 않으면(실제 모드, 후속 BE) 테스트 현재 결과로 둔다
function startTestScores(data) {
  if (data.initialPersonality === undefined) return data.personality?.axisScores;
  return data.initialPersonality?.source === 'TEST' ? data.initialPersonality.axisScores : null;
}

// 축마다 흐름 점(초심 → 체험 순서)과 변경폭(초심 → 최근 판결 성향). 변경폭 정리 · 변화 흐름 그래프가 함께 쓴다(FE-70)
// data: 마이페이지 응답(P-5)
export function trendsOf(data) {
  const history = data.history ?? [];
  return AXES.map((axis) => {
    const points = axisTrend(history, axis.key, startTestScores(data));
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
const CHART = { width: 320, height: 160, axis: 34, left: 52, right: 22, top: 10, bottom: 30, anchorGap: 0.7 }; // axis: 세로 눈금 글자 자리(이 왼쪽), left: 첫 점 자리, bottom: 가로 눈금 글자 자리
// 세로 눈금: 툴팁 · 표(leanText)와 같이 기운 쪽 비중으로 읽는다. 위 끝 관대 100, 가운데 50(반반), 아래 끝 엄격 100
const Y_TICKS = [[1, '100'], [0.75, '75'], [0.5, '50'], [0.25, '75'], [0, '100']];
const FOCUS_BAND = 22; // 강조한 칸(이 사건) 띠 폭

// retakeBetween: 재심이 낀 두 칸의 order [앞, 뒤](FE-77). 칸을 하나 더 만들면 체험 사이 간격(기울기)이 사람마다 달라져, 칸 없이 두 칸 사이에 세로 점선으로 얹는다
function trendChart({ axis, points }, slots, retakeBetween) {
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
  const { width, height, axis: axisX, left, right, bottom } = CHART;
  const top = retakeBetween ? CHART.top + 14 : CHART.top; // 재심 글자 자리(그래프 위)
  const plotBottom = height - bottom;
  // 칸은 모두 같은 간격이다. 재심이 초심 바로 뒤(체험 0건 뒤) · 최근 바로 앞(맨 끝)에 끼면 그 사이만 anchorGap칸 더 띄워 점이 겹치지 않게 한다
  const gap = CHART.anchorGap;
  const startGap = retakeBetween?.[0] === 0 ? gap : 0;
  const nowGap = retakeBetween?.[1] === 'now' ? gap : 0;
  const step = slots.length > 1 ? (width - left - right) / (slots.length - 1 + startGap + nowGap) : 0;
  const slotX = (order) => {
    const index = slots.findIndex((slot) => slot.order === order);
    if (slots.length === 1) return (axisX + width) / 2;
    return left + (index + (order !== 0 ? startGap : 0) + (order === 'now' ? nowGap : 0)) * step;
  };
  // 재심: 앞 · 뒤 칸의 가운데. 한쪽 칸이 없으면(맨 앞 · 맨 뒤) 반 칸 옆
  const retakeX = (() => {
    if (!retakeBetween) return null;
    const [before, after] = retakeBetween.map((order) => (order == null ? null : slotX(order)));
    const half = (step || 40) / 2;
    if (before != null && after != null) return (before + after) / 2;
    return Math.max(axisX + 6, Math.min(width - 4, before != null ? before + half : after - half));
  })();
  const xOf = (order) => (order === 'retake' ? retakeX : slotX(order));
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
    // 눈금선은 75(위 · 아래)만 점선으로 긋는다. 50 · 100은 위 · 아래 면(trend-zone)의 경계와 그래프 끝이 이미 보여 선을 두지 않는다(눈금 글자는 둔다)
    if (ratio === 0.75 || ratio === 0.25) chart.append(svgNode('line', { x1: axisX, x2: width, y1: y, y2: y, class: 'trend-grid-line' }));
    chart.append(svgNode('text', { x: axisX - 6, y, class: 'trend-tick', 'text-anchor': 'end', 'dominant-baseline': 'middle' }, label));
  });
  slots.forEach((slot) => chart.append(svgNode('text', { x: xOf(slot.order), y: height - 10, class: `trend-tick${slot.isFocus ? ' is-focus' : ''}`, 'text-anchor': 'middle' }, slot.label)));
  // 기준점 세로 점선(FE-77): 초심 · 최근 칸, 재심 자리. 재심은 가로축 글자와 겹치지 않게 그래프 위에 글자를 둔다
  slots.filter((slot) => slot.order === 0 || slot.order === 'now').forEach((slot) => {
    chart.append(svgNode('line', { x1: slotX(slot.order), x2: slotX(slot.order), y1: top, y2: plotBottom, class: 'trend-anchor-line' }));
  });
  if (retakeX != null) {
    chart.append(
      svgNode('line', { x1: retakeX, x2: retakeX, y1: top, y2: plotBottom, class: 'trend-anchor-line' }),
      svgNode('text', { x: retakeX, y: top - 5, class: 'trend-tick is-retake', 'text-anchor': 'middle' }, '재심'),
    );
  }
  // 체험 점끼리만 실선으로 잇는다. 기준점(초심 · 재심 · 최근 평균)은 한 번의 판결이 아니라 잇지 않고 마름모로 따로 찍는다
  const steps = points.filter((point) => !point.isCurrent && !point.isTest);
  const at = (point) => `${xOf(point.order)},${yOf(point.ratio)}`;
  if (steps.length > 1) chart.append(svgNode('polyline', { points: steps.map(at).join(' '), class: 'trend-line' }));

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
    chart.append(point.isTest || point.isCurrent
      ? svgNode('polygon', { points: `${x},${y - 6} ${x + 6},${y} ${x},${y + 6} ${x - 6},${y}`, class: `trend-dot ${point.isCurrent ? 'is-current' : 'is-test'}` })
      : svgNode('circle', { cx: x, cy: y, r: isFocus ? 5.5 : 4.5, class: `trend-dot${isFocus ? ' is-focus' : ''}` }));
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

// judgment: 최근 판결 성향(맨 오른쪽 "최근" 칸). mock: 목 모드면 안내에 "목데이터"
// retake: 재심 테스트({ result, after(그 전에 마친 체험 수), title }, FE-77). 없으면 null
// focus: 강조할 체험 순서(history의 1부터 번호). 내 결과 지면에서 "이 사건" 칸을 띠 · 진한 눈금으로 보여 준다(FE-75)
// hint({ hasTest, focused }): 그래프 위 안내 문구를 바꿀 때(내 결과 지면은 기사 문체)
// table: false면 "표로 보기"를 싣지 않는다(내 결과 지면). 값은 그래프 이름(aria-label)에 그대로 있다
export function renderTrend(trends, history, judgment, { mock = false, focus, hint: hintText, table = true, retake = null } = {}) {
  const box = element('div', 'my-detail');
  if (history.length === 0) {
    box.append(element('p', 'my-hint', '사건을 하나 마치면 첫 점이 찍혀요.'));
    return box;
  }
  // 가로 칸: 초심(테스트가 있을 때만) + 최근 체험 10건. 값이 없는 칸도 자리는 남겨 축끼리 같은 칸에 줄을 맞춘다.
  // 재심은 표 · 점 순서에만 끼우고(orders), 그래프 가로 칸에는 넣지 않는다(chartSlots)
  const hasTest = trends.some(({ points }) => points[0]?.isTest);
  const baseOrders = trendSlotOrders(history.length, hasTest, focus);
  const orders = retake ? withRetakeSlot(baseOrders, retake.after) : baseOrders;
  // 눈금 숫자는 보이는 체험끼리 완료한 순서대로 1부터 매긴다(앞의 체험이 빠져도 숫자가 건너뛰지 않는다)
  const experienceOrders = orders.filter((order) => order > 0);
  const slots = orders.map((order) => {
    if (order === 0) return { order, label: '초심', title: '초심 · 성향 테스트' };
    if (order === 'retake') return { order, label: '재심', title: retake.title };
    const seq = experienceOrders.indexOf(order) + 1;
    return { order, label: String(seq), title: `${seq}. ${history[order - 1].title}`, isFocus: order === focus };
  });
  // 맨 오른쪽 "최근" 칸: 최근 판결 성향(최근 3건 평균, 변경폭의 최근과 같은 값). 축 값이 없으면 칸만 남는다
  const currentScores = judgment?.axisScores;
  if (currentScores) slots.push({ order: 'now', label: '최근', title: `최근 · 체험 ${judgment.experienceCount}건 평균` });
  const titleOf = new Map(slots.map((slot) => [slot.order, slot.title]));
  const chartSlots = slots.filter((slot) => slot.order !== 'retake');
  const at = orders.indexOf('retake');
  const retakeBetween = at === -1 ? null : [orders[at - 1] ?? null, orders[at + 1] ?? (currentScores ? 'now' : null)];
  const shown = trends.map((trend) => {
    const points = trend.points.filter((point) => titleOf.has(point.order)).map((point) => ({ ...point, title: titleOf.get(point.order) }));
    const again = retake?.result.axisScores?.[trend.axis.key];
    if (typeof again === 'number') points.push({ order: 'retake', ratio: again, title: retake.title, isTest: true });
    points.sort((a, b) => orders.indexOf(a.order) - orders.indexOf(b.order)); // 재심 점을 순서 자리로
    const now = currentScores?.[trend.axis.key];
    if (typeof now === 'number') points.push({ order: 'now', ratio: now, title: titleOf.get('now'), isCurrent: true });
    return { ...trend, points };
  });
  const focused = slots.some((slot) => slot.isFocus);
  const hint = element('p', 'my-hint', hintText?.({ hasTest, focused })
    ?? `${hasTest ? '초심(성향 테스트)과 ' : ''}최근 체험 ${TREND_RECENT}건까지 보여요. 가운데 줄이 반반이고, 숫자는 사건을 마친 순서, 최근은 최근 체험 3건의 평균이에요. 마름모는 기준점(초심${retake ? ' · 재심' : ''} · 최근)이라 체험 선과 잇지 않아요. 점을 누르거나 마우스를 올리면 사건과 비중이 보여요.`);
  if (mock) hint.append(mockTag());
  const grid = element('div', 'trend-grid');
  shown.forEach((trend) => grid.append(trendChart(trend, chartSlots, retakeBetween)));
  box.append(hint, grid);
  if (table) box.append(trendTable(shown, slots));
  return box;
}
