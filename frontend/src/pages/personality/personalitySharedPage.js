import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { renderToleranceScale } from '../../components/toleranceScale.js';
import { descriptionSections, noticeBlock, typeCard } from './personalityParts.js';

// 공유된 성향 결과 /test/types/{code} (S-16, FR-8-6). 친구가 링크를 열면 **유형 설명만** 보인다 —
// 공유한 사람의 응답 · 축 점수 · 체험 기록은 이 화면의 데이터에 없다. "나도 해 보기"로 S-15에 보낸다.
export async function renderPersonalitySharedPage(container, { code, api, navigate }) {
  const root = element('div', 'personality-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }));
  const main = element('main', 'personality-main');
  root.append(main);

  async function load() {
    const loading = element('p', 'message-panel', '유형을 불러오는 중이에요.');
    loading.setAttribute('role', 'status');
    main.replaceChildren(loading);

    let type;
    try {
      type = await api.getPersonalityType(code);
    } catch {
      if (!active()) return;
      const panel = element('div', 'message-panel');
      panel.setAttribute('role', 'alert');
      panel.append(element('p', '', '유형을 불러오지 못했어요.'), button('다시 시도', load, 'primary-button'));
      main.replaceChildren(panel);
      return;
    }
    if (!active()) return;
    if (!type) {
      const panel = element('div', 'message-panel');
      panel.setAttribute('role', 'alert');
      panel.append(element('p', '', '찾을 수 없는 유형이에요. 링크를 다시 확인해 주세요.'), button('나도 해 보기', () => navigate('test'), 'primary-button'));
      main.replaceChildren(panel);
      return;
    }

    const gauge = element('section', 'result-section');
    gauge.append(
      element('h2', '', '관용 지수'),
      renderToleranceScale(type.tolerance),
      element('p', 'tolerance-note', '관용 지수는 높을수록 좋은 것이 아니에요. 잘잘못을 따질 때 어떤 가치를 더 중요하게 보는지를 나타낼 뿐이에요.'),
    );
    const actions = element('div', 'personality-actions result-actions');
    actions.append(button('나도 해 보기', () => navigate('test'), 'primary-button'));
    main.replaceChildren(typeCard(type, type.code, '친구가 공유한 판결 성향'), gauge, ...descriptionSections(type), noticeBlock(), actions);
  }

  await load();
}
