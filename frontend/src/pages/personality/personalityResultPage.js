import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { AXES } from '../../utils/personalityAxes.js';

// S-16 성향 결과 — 내 결과 (WBS 7.6.5). 공유된 결과(/test/types/{code})와 공유 버튼은 공유 API 계약(FR-8-6) 뒤에 붙인다.
// 캐릭터 이미지는 아직 없어서(7.6.8) 이름표 자리만 둔다. 문구는 docs/personality-test-content.md 4 · 5장.
// 4축 그래프: 축마다 양쪽 성향의 비중(관대 쪽 비중 = axisScores). 관용 지수 게이지: 0 ~ 4칸.
const pct = (ratio) => Math.round(ratio * 100);

function axisRow(axis, lenientRatio) {
  const strict = pct(1 - lenientRatio);
  const lenient = 100 - strict;
  const row = element('li', 'axis-row');
  row.append(element('p', 'axis-label', axis.label));
  const labels = element('div', 'axis-ends');
  labels.append(
    element('span', strict > lenient ? 'is-major' : '', `${axis.strict.letter} ${axis.strict.name} ${strict}%`),
    element('span', lenient > strict ? 'is-major' : '', `${lenient}% ${axis.lenient.name} ${axis.lenient.letter}`),
  );
  const bar = element('div', 'axis-bar');
  bar.setAttribute('role', 'img');
  bar.setAttribute('aria-label', `${axis.strict.name} ${strict}%, ${axis.lenient.name} ${lenient}%`);
  const fill = element('span');
  fill.style.width = `${strict}%`; // 왼쪽 = 엄격 쪽 비중
  bar.append(fill);
  row.append(labels, bar);
  return row;
}

function toleranceGauge(level) {
  const gauge = element('div', 'tolerance');
  const steps = element('div', 'tolerance-steps');
  steps.setAttribute('role', 'img');
  steps.setAttribute('aria-label', `관용 지수 4단계 중 ${level}`);
  for (let i = 1; i <= 4; i += 1) steps.append(element('span', i <= level ? 'is-on' : ''));
  gauge.append(element('p', '', `관용 지수 ${level} / 4`), steps);
  gauge.append(element('p', 'tolerance-note', '관용 지수는 높을수록 좋은 것이 아니에요. 잘잘못을 따질 때 어떤 가치를 더 중요하게 보는지를 나타낼 뿐이에요.'));
  return gauge;
}

export async function renderPersonalityResultPage(container, { api, navigate }) {
  const root = element('div', 'personality-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }));
  const main = element('main', 'personality-main');
  root.append(main);

  const loading = element('p', 'message-panel', '결과를 불러오는 중이에요.');
  loading.setAttribute('role', 'status');
  main.append(loading);

  let result;
  try {
    result = await api.getMyPersonality();
  } catch {
    if (!active()) return;
    const panel = element('div', 'message-panel');
    panel.setAttribute('role', 'alert');
    panel.append(element('p', '', '결과를 불러오지 못했어요.'), button('다시 시도', () => navigate('test'), 'primary-button'));
    main.replaceChildren(panel);
    return;
  }
  if (!active()) return;
  if (!result) {
    navigate('test', {}, { replace: true }); // 아직 테스트를 하지 않았으면 테스트로 보낸다
    return;
  }

  const { type, typeCode, axisScores } = result;
  const card = element('section', 'result-card');
  const portrait = element('div', 'result-portrait');
  portrait.append(element('strong', '', type.character), element('span', '', type.meme));
  portrait.setAttribute('aria-label', `${type.character} 캐릭터 (이미지 준비 중)`);

  const head = element('div', 'result-head');
  head.append(
    element('p', 'personality-kicker', '나의 판결 성향'),
    element('p', 'result-code', typeCode),
    element('h1', '', type.name),
    element('p', 'result-oneliner', type.oneLiner),
  );
  card.append(portrait, head);

  const graph = element('section', 'result-section');
  graph.append(element('h2', '', '가치관 4축'));
  const list = element('ul', 'axis-list');
  AXES.forEach((axis) => list.append(axisRow(axis, axisScores[axis.key])));
  graph.append(list, toleranceGauge(type.tolerance));

  const daily = element('section', 'result-section');
  daily.append(element('h2', '', '일상에서는'), element('p', '', type.daily));
  const verdict = element('section', 'result-section');
  verdict.append(element('h2', '', '판결을 볼 때는'), element('p', '', type.verdict));

  const notice = element('section', 'result-notice');
  [
    '이 결과는 일상 가치관을 기준으로 한 비교이며, 실제 법적 판단과는 다를 수 있어요.',
    '4글자 판결 성향 유형은 성격 유형 검사와 별개예요. 알고 있는 성격 유형과 다르게 나올 수 있어요.',
  ].forEach((text) => notice.append(element('p', '', text)));

  const actions = element('div', 'personality-actions result-actions');
  actions.append(
    button('실제 사건으로 판결하러 가기', () => navigate('list'), 'primary-button'),
    button('마이페이지에서 보기', () => navigate('me'), 'secondary-button'),
    button('다시 하기', () => navigate('test'), 'secondary-button'),
  );

  main.replaceChildren(card, graph, daily, verdict, notice, actions);
}
