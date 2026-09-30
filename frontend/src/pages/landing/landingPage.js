import { button, element, renderSiteFooter, renderSiteHeader } from '../../components/siteLayout.js';

const features = [
  { title: '먼저 판단하기', text: '사건 정보를 보기 전에 내 생각을 먼저 고릅니다.' },
  { title: '직접 판결하기', text: '사건 기록과 법률 정보를 확인하고 판결을 내립니다.' },
  { title: '세 판결 비교', text: '내 판결, AI 판결, 실제 판결을 나란히 비교합니다.' },
];

// S-01 랜딩 / 홈
export function renderLandingPage(container, { navigate, section }) {
  const root = element('div', 'landing-page');
  const intro = element('section', 'feature-section');
  intro.id = 'intro';
  intro.setAttribute('aria-labelledby', 'feature-heading');
  const showIntro = () => intro.scrollIntoView({ behavior: 'smooth' });

  const hero = element('section', 'hero');
  const actions = element('div', 'hero-actions');
  actions.append(
    button('사건 목록 보기', () => navigate('list'), 'primary-button'),
    button('체험 방식 알아보기', showIntro),
  );
  hero.append(
    element('h1', '', '당신이 판사라면, 어떤 판결을 내리겠습니까?'),
    element('p', 'hero-copy', '실제 사건을 바탕으로 판결을 직접 내려 보고, AI 판결과 실제 판결을 비교해 보세요.'),
    actions,
  );

  const heading = element('h2', '', '이렇게 체험해요');
  heading.id = 'feature-heading';
  const cards = element('ol', 'feature-cards');
  features.forEach(({ title, text }, index) => {
    const card = element('li', 'feature-card');
    card.append(element('span', 'feature-step', String(index + 1)), element('h3', '', title), element('p', '', text));
    cards.append(card);
  });
  intro.append(heading, cards);

  root.append(renderSiteHeader({ navigate, onIntro: showIntro }), hero, intro, renderSiteFooter());
  container.replaceChildren(root);
  if (section === 'intro') showIntro();
}
