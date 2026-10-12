// 공통 사이트 헤더 · 푸터 (REQ-071 법적 고지). S-01 · S-02 · S-14가 헤더를 그리고, 푸터는 S-01 · S-02가 직접, 나머지 화면은 라우터가 붙인다. 스타일은 src/style.css
export function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

export function button(text, action, className = 'secondary-button') {
  const node = element('button', className, text);
  node.type = 'button';
  node.addEventListener('click', action);
  return node;
}

export function renderSiteHeader({ navigate, onIntro }) {
  const header = element('header', 'site-header');
  // 로고는 "Law"만 따로 감싸 둔다. 색은 화면 CSS가 정한다(S-01은 브론즈, 나머지 화면은 글자색 그대로)
  const logo = button('', () => navigate('landing'), 'logo-button');
  logo.append('내', element('span', 'law', 'Law'), '남불');
  header.append(
    logo,
    (() => {
      const nav = element('nav', 'site-nav');
      nav.setAttribute('aria-label', '주요 메뉴');
      nav.append(
        button('사건 목록', () => navigate('list'), 'nav-button'),
        button('서비스 소개', onIntro, 'nav-button'),
      );
      return nav;
    })(),
  );
  return header;
}

export function renderSiteFooter() {
  const footer = element('footer', 'site-footer');
  footer.append(
    element('p', '', '이 서비스의 사건은 실제 판결을 바탕으로 비식별화 처리한 내용입니다.'),
    element('p', '', '내Law남불은 법률 자문이나 특정 사건에 대한 법적 판단을 제공하지 않습니다.'),
  );
  return footer;
}
