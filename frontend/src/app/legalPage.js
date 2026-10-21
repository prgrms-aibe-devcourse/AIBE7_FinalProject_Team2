// S-10 이용약관 · S-11 개인정보처리방침 (FE-62). 조항이 아직 정해지지 않아 입구와 틀만 있다.
// 내용이 정해지면 NOTICE 자리만 본문으로 바꾼다. 공통 헤더 · 푸터는 라우터가 붙인다.
import { button, element } from '../components/siteLayout.js';

function renderLegalPage(container, { navigate }, title) {
  const root = element('main', 'not-found-page'); // 라우터가 <main>을 두지 않아 본문 landmark를 여기서 만든다
  const actions = element('div', 'not-found-actions');
  actions.append(button('메인으로', () => navigate('landing'), 'primary-button'));
  const panel = element('div', 'message-panel not-found-panel');
  panel.append(
    element('h1', '', title),
    element('p', '', `${title} 내용을 준비하고 있어요. 정해지는 대로 이곳에 올릴게요.`),
    actions,
  );
  root.append(panel);
  container.replaceChildren(root);
}

export const renderTermsPage = (container, options) => renderLegalPage(container, options, '이용약관');
export const renderPrivacyPage = (container, options) => renderLegalPage(container, options, '개인정보처리방침');
