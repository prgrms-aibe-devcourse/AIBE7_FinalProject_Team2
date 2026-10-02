// S-14 404 · 오류 화면 (자리표시자)
// FE-3(S-14 오류 화면 · 반응형)에서 IA 기준 화면으로 교체한다. 라우터가 쓰는 진입 함수 형식은 그대로 유지한다.
import { button, element } from '../components/siteLayout.js';

export function renderNotFoundPage(container, { navigate }) {
  const root = element('div', 'review-page not-found-page');
  const panel = element('div', 'message-panel');
  panel.setAttribute('role', 'alert');
  panel.append(
    element('p', '', '찾을 수 없는 사건이거나 잘못된 주소예요.'),
    button('사건 목록으로', () => navigate('list'), 'primary-button'),
  );
  root.append(panel);
  container.replaceChildren(root);
}
