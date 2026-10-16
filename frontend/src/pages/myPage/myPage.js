import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { renderToleranceScale } from '../../components/toleranceScale.js';
import { screenByStatus } from '../../utils/screenByStatus.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { AXES, CHARACTER_NAMES } from '../../utils/personalityAxes.js';
import { axisSeries, collectionOf, trailLeft, trailSummary } from '../../utils/myPageView.js';
import { SCRIPTS } from '../../content/scripts.js';

// S-17 마이페이지 (WBS 7.6.6). GET /my-page 한 번 호출로 받고, 기록이 없어도 404가 아니라 빈 200이다(명세 P-5).
// 내 성향(테스트 결과) · 종합 판결 성향(체험 평균 4축) · 성향 변화 흐름(체험별 4축 위치) · 체험한 사건 목록을 둔다.
// 종합 · 변화 흐름은 체험 → 성향 저장(7.6.7, BE-64) 뒤부터 채워지므로 그 전에는 null · 빈 배열로 와서 안내만 보인다.
// 로그인 전에는 익명 ID 기준이다(REQ-108).
const STATUS_LABELS = { COMPLETED: '체험 완료' };

// 종합 판결 성향 — 체험 평균. 네 축이 모두 차면 유형 이름도 함께 보여 준다
function renderJudgment(judgment) {
  const section = element('section', 'my-section');
  section.append(element('h2', '', '판결에서 드러난 성향'));
  if (!judgment) {
    section.append(element('p', 'my-hint', '사건을 체험하면 판결에서 무엇을 무겁게 봤는지 여기에 쌓여요.'));
    return section;
  }
  if (judgment.type) section.append(element('p', 'my-judgment-type', judgment.type.name));
  section.append(element('p', 'my-hint', `첫 회차 체험 ${judgment.experienceCount}건의 평균이에요.`));
  const list = element('ul', 'axis-list');
  AXES.forEach((axis) => list.append(renderAxisRow(axis, judgment.axisScores?.[axis.key])));
  section.append(list);
  return section;
}

// 성향 변화 흐름 — 축마다 체험 번호가 붙은 점이 엄격 ↔ 관대 막대 위를 옮겨 간다
function trailRow(axis, points) {
  const row = element('li', 'axis-row trail-row');
  row.append(element('p', 'axis-label', axis.label));
  if (points.length === 0) {
    row.append(element('p', 'axis-empty', '체험 자료가 아직 없어요'));
    return row;
  }
  const ends = element('div', 'axis-ends');
  ends.append(element('span', '', axis.strict.name), element('span', '', axis.lenient.name));
  const track = element('div', 'trail-track');
  track.setAttribute('role', 'img');
  track.setAttribute('aria-label', points.map((point) => `${point.order}번째 체험 ${trailLeft(point.ratio)}%`).join(', ') + ` (0%는 ${axis.strict.name}, 100%는 ${axis.lenient.name})`);
  points.forEach((point, index) => {
    const dot = element('span', index === points.length - 1 ? 'trail-dot is-latest' : 'trail-dot', String(point.order));
    dot.style.left = `${trailLeft(point.ratio)}%`;
    track.append(dot);
  });
  row.append(ends, track);
  const summary = trailSummary(points, axis);
  if (summary) row.append(element('p', 'trail-summary', summary));
  return row;
}

function renderTrail(history) {
  const section = element('section', 'my-section');
  section.append(element('h2', '', '성향 변화 흐름'));
  if (history.length === 0) {
    section.append(element('p', 'my-hint', '사건을 하나 마치면 첫 점이 찍혀요.'));
    return section;
  }
  section.append(element('p', 'my-hint', history.length === 1 ? '한 건을 더 체험하면 움직임이 보여요. 번호는 체험한 순서예요.' : '번호는 체험한 순서이고, 가장 최근 점이 진하게 보여요.'));
  const list = element('ul', 'axis-list');
  AXES.forEach((axis) => list.append(trailRow(axis, axisSeries(history, axis.key))));
  section.append(list);
  return section;
}

// 컬렉션 — 체험을 마친 사건의 일러스트. 사건마다 한 묶음이고, 체험하지 않은 사건은 나오지 않는다
function renderCollection(experiences) {
  const section = element('section', 'my-section');
  section.append(element('h2', '', '일러스트 컬렉션'));
  const groups = collectionOf(experiences, SCRIPTS);
  if (groups.length === 0) {
    section.append(element('p', 'my-hint', '사건 체험을 마치면 그 사건에 나온 일러스트가 여기에 모여요.'));
    return section;
  }
  const total = groups.reduce((sum, group) => sum + group.items.length, 0);
  section.append(element('p', 'my-hint', `지금까지 ${total}장을 모았어요.`));
  groups.forEach((group) => {
    const box = element('div', 'collection-group');
    box.append(element('h3', 'collection-title', group.title));
    const grid = element('ul', 'collection-grid');
    group.items.forEach((item) => {
      const card = element('li', 'collection-card');
      const img = element('img');
      img.src = item.src;
      img.alt = `${group.title} ${item.label} 일러스트`;
      img.loading = 'lazy';
      card.append(img, element('p', 'collection-label', item.label));
      grid.append(card);
    });
    box.append(grid);
    section.append(box);
  });
  return section;
}

export async function renderMyPage(container, { api, navigate }) {
  const root = element('div', 'my-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }));
  const main = element('main', 'my-main');
  root.append(main);

  const loading = element('p', 'message-panel', '불러오는 중이에요.');
  loading.setAttribute('role', 'status');
  main.append(loading);

  let data;
  try {
    data = await api.getMyPage();
  } catch {
    if (!active()) return;
    const panel = element('div', 'message-panel');
    panel.setAttribute('role', 'alert');
    panel.append(element('p', '', '마이페이지를 불러오지 못했어요.'), button('다시 시도', () => navigate('me', {}, { replace: true }), 'primary-button'));
    main.replaceChildren(panel);
    return;
  }
  if (!active()) return;

  const title = element('h1', '', '마이페이지');

  // 내 성향 — 결과가 없으면 테스트를 권한다
  const tendency = element('section', 'my-section');
  tendency.append(element('h2', '', '내 판결 성향'));
  if (data.personality) {
    const { type } = data.personality;
    const card = element('div', 'my-type');
    const text = element('div');
    text.append(
      element('p', 'my-type-name', type.name),
      element('p', 'my-type-character', CHARACTER_NAMES[type.character] ?? type.character),
      element('p', 'my-type-oneliner', type.oneLiner),
      element('p', 'my-type-tolerance', '관용 지수'),
      renderToleranceScale(type.tolerance),
    );
    const actions = element('div', 'my-actions');
    actions.append(
      button('결과 자세히 보기', () => navigate('testResult'), 'primary-button'),
      button('다시 하기', () => navigate('test'), 'secondary-button'),
    );
    card.append(text, actions);
    tendency.append(card);
  } else {
    const empty = element('div', 'my-empty');
    empty.append(
      element('p', '', '아직 성향 테스트를 하지 않았어요. 3분이면 내가 잘잘못을 따질 때 중요하게 보는 가치를 알 수 있어요.'),
      button('성향 테스트 하러 가기', () => navigate('test'), 'primary-button'),
    );
    tendency.append(empty);
  }

  // 체험한 사건 — 테스트 여부와 무관하게 보여 준다
  const experiences = element('section', 'my-section');
  experiences.append(element('h2', '', '체험한 사건'));
  if ((data.experiences ?? []).length === 0) {
    const empty = element('div', 'my-empty');
    empty.append(element('p', '', '아직 체험한 사건이 없어요.'), button('사건 목록 보기', () => navigate('list'), 'secondary-button'));
    experiences.append(empty);
  } else {
    const list = element('ul', 'my-cases');
    data.experiences.forEach((item) => {
      const row = element('li', 'my-case');
      const info = element('div');
      info.append(element('p', 'my-case-title', item.title), element('p', 'my-case-status', STATUS_LABELS[item.status] ?? '체험 중'));
      const label = item.status === 'COMPLETED' ? '결과 다시 보기' : '이어서 하기';
      const open = button(label, () => navigate(screenByStatus[item.status] ?? 'list', { caseId: item.caseId }), 'secondary-button');
      row.append(info, open);
      list.append(row);
    });
    experiences.append(list);
  }

  const note = element('p', 'my-note', '로그인하기 전에는 이 브라우저(익명 ID)를 기준으로 기록해요. 쿠키를 지우면 기록을 찾을 수 없어요.');
  main.replaceChildren(title, tendency, renderJudgment(data.judgmentPersonality), renderTrail(data.history ?? []), renderCollection(data.experiences), experiences, note);
}
