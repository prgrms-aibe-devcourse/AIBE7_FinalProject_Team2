// S-14 404 · 오류 화면 (IA 4장: 잘못된 사건 ID 등 — 와이어프레임 없음, MVP 범위에서 직접 구성)
// 다른 화면과 달리 사건 맥락이 없는 화면이라, S-01 · S-02처럼 사이트 헤더(로고 · 메뉴)를 직접 그린다.
// 공통 푸터는 라우터가 아래에 붙인다 (routes.js에 ownsFooter 없음). 공통 스타일(.message-panel,
// .primary-button, .secondary-button)은 src/style.css — 화면 CSS를 빌리지 않는다 (FE-2 리뷰 반영 유지).
import { button, element, renderSiteHeader } from '../components/siteLayout.js';

export function renderNotFoundPage(container, { navigate }) {
  const root = element('div', 'not-found-page');
  // 서비스 소개 메뉴는 S-02와 같이 랜딩의 소개 섹션으로 보낸다
  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }));

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
