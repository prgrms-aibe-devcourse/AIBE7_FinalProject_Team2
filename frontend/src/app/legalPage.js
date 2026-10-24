// S-10 이용약관 · S-11 개인정보처리방침 (FE-62 입구, FE-91 틀). 조항 본문은 법무 검토 뒤에 정해진다(요구사항 명세서 미정 항목).
// 지금은 문서의 틀(제목 · 시행일 · 목차 · 조항 제목)만 있고 조항마다 "준비 중" 자리다. 본문이 정해지면 아래 SECTIONS의 body 배열만 채운다.
// body: 문단 문자열의 배열(비어 있으면 "준비 중" 안내가 나온다). 공통 헤더 · 푸터는 라우터가 붙인다.
import { button, element } from '../components/siteLayout.js';
import { reducedMotion } from '../utils/motion.js';

const DOCUMENTS = {
  terms: {
    title: '이용약관',
    route: 'terms',
    summary: '내Law남불 서비스를 이용하는 데 필요한 규칙이에요.',
    sections: [
      { title: '목적', body: [] },
      { title: '용어의 뜻', body: [] },
      { title: '약관의 효력과 변경', body: [] },
      { title: '회원가입과 계정', body: [] },
      { title: '서비스 내용 (판결 체험 · 성향 테스트 · 커뮤니티)', body: [] },
      { title: '서비스 이용 제한', body: [] },
      { title: '이용자의 의무', body: [] },
      { title: '콘텐츠와 저작권 (AI 판결 · 재구성 사건 · 이용자 작성 글)', body: [] },
      { title: '법률 자문이 아니라는 안내와 책임 제한', body: [] },
      { title: '회원 탈퇴와 이용 종료', body: [] },
      { title: '분쟁 해결', body: [] },
    ],
  },
  privacy: {
    title: '개인정보처리방침',
    route: 'privacy',
    summary: '내Law남불이 어떤 개인정보를 어떻게 다루는지 알려 드려요.',
    sections: [
      { title: '수집하는 개인정보 항목', body: [] },
      { title: '개인정보의 수집 · 이용 목적', body: [] },
      { title: '개인정보의 보유 · 이용 기간', body: [] },
      { title: '개인정보의 제3자 제공', body: [] },
      { title: '개인정보 처리의 위탁', body: [] },
      { title: '개인정보의 파기 절차와 방법', body: [] },
      { title: '이용자의 권리와 행사 방법', body: [] },
      { title: '쿠키와 이용 기록', body: [] },
      { title: '개인정보의 안전성 확보 조치', body: [] },
      { title: '개인정보 보호책임자와 문의처', body: [] },
      { title: '방침의 변경 안내', body: [] },
    ],
  },
};

function renderLegalPage(container, { navigate }, key) {
  const doc = DOCUMENTS[key];
  const root = element('main', 'legal-page'); // 라우터가 <main>을 두지 않아 본문 landmark를 여기서 만든다
  const article = element('article', 'legal-doc');

  // 두 문서를 오가는 줄(현재 문서는 눌리지 않는다)
  const switcher = element('nav', 'legal-switch');
  switcher.setAttribute('aria-label', '약관 · 방침');
  Object.values(DOCUMENTS).forEach((item) => {
    const tab = button(item.title, () => navigate(item.route), 'legal-switch-item');
    if (item.route === key) {
      tab.setAttribute('aria-current', 'page');
      tab.disabled = true;
    }
    switcher.append(tab);
  });

  const head = element('header', 'legal-head');
  const meta = element('p', 'legal-meta');
  meta.append(element('span', 'legal-draft', '내용 준비 중'), ' 시행일: 정해지면 알려 드려요');
  head.append(element('h1', '', doc.title), element('p', 'legal-summary', doc.summary), meta);

  // 목차: 누르면 그 조항으로 부드럽게 내려간다(움직임 줄이기면 바로)
  const toc = element('nav', 'legal-toc');
  toc.setAttribute('aria-label', `${doc.title} 목차`);
  const tocList = element('ol');
  const body = element('div', 'legal-body');
  doc.sections.forEach((section, index) => {
    const id = `legal-${key}-${index + 1}`;
    const target = element('section', 'legal-section');
    target.id = id;
    target.setAttribute('aria-labelledby', `${id}-title`);
    const heading = element('h2', '', `제${index + 1}조 ${section.title}`);
    heading.id = `${id}-title`;
    target.append(heading);
    if (section.body.length) section.body.forEach((text) => target.append(element('p', '', text)));
    else target.append(element('p', 'legal-pending', '이 조항은 준비 중이에요.'));
    body.append(target);

    const item = element('li');
    item.append(button(`제${index + 1}조 ${section.title}`, () => {
      target.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start' });
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
    }, 'legal-toc-item'));
    tocList.append(item);
  });
  toc.append(element('h2', '', '목차'), tocList);

  article.append(head, toc, body);
  const actions = element('div', 'legal-actions');
  actions.append(button('메인으로', () => navigate('landing'), 'primary-button'));
  root.append(switcher, article, actions);
  container.replaceChildren(root);
}

export const renderTermsPage = (container, options) => renderLegalPage(container, options, 'terms');
export const renderPrivacyPage = (container, options) => renderLegalPage(container, options, 'privacy');
