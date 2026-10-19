// S-14 404 · 오류 화면 (IA 4장: 잘못된 사건 ID 등 — 와이어프레임 없음, MVP 범위에서 직접 구성)
// 공통 헤더 · 푸터는 라우터가 위아래에 붙인다 (FE-56, routes.js에 ownsHeader · ownsFooter 없음). 공통 스타일(.message-panel,
// .primary-button, .secondary-button)은 src/style.css — 화면 CSS를 빌리지 않는다 (FE-2 리뷰 반영 유지).
import { button, element } from '../components/siteLayout.js';

export function renderNotFoundPage(container, { navigate }) {
  const root = element('div', 'not-found-page');

  const actions = element('div', 'not-found-actions');
  actions.append(
    button('사건 목록으로', () => navigate('list'), 'primary-button'),
    button('처음으로', () => navigate('landing'), 'secondary-button'),
  );

  const panel = element('div', 'message-panel not-found-panel');
  panel.setAttribute('role', 'alert');
  panel.append(
    element('h1', '', '페이지를 찾을 수 없어요'),
    element('p', '', '주소가 정확하지 않거나, 사건을 찾을 수 없어요.'),
    actions,
  );

  root.append(panel);
  container.replaceChildren(root);
}
