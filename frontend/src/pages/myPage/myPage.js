import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { renderToleranceScale } from '../../components/toleranceScale.js';
import { screenByStatus } from '../../utils/screenByStatus.js';

// S-17 마이페이지 (WBS 7.6.6). 지금은 내 성향(테스트 결과)과 체험한 사건 목록만 둔다.
// 성향 변화 흐름 · 종합 4축 그래프는 체험 → 성향 저장(7.6.7) 뒤에 붙인다. 로그인 전에는 익명 ID 기준이다(REQ-108).
const STATUS_LABELS = { COMPLETED: '체험 완료' };

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
    const { type, typeCode } = data.personality;
    const card = element('div', 'my-type');
    const text = element('div');
    text.append(
      element('p', 'my-type-code', typeCode),
      element('p', 'my-type-name', `${type.name} · ${type.character}`),
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
  if (data.experiences.length === 0) {
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
  main.replaceChildren(title, tendency, experiences, note);
}
