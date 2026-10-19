// 공통 사이트 헤더 · 푸터 (REQ-071 법적 고지). S-01 · S-02 · S-14가 헤더를 그리고, 푸터는 S-01 · S-02가 직접, 나머지 화면은 라우터가 붙인다. 스타일은 src/style.css
import { motionSwitchOn, reducedMotion, setMotionSwitch, systemReducesMotion } from '../utils/motion.js';

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

// 공통 헤더(FE-50): 화면 위에 고정된다(style.css). 메뉴는 사건 목록 · 성향검사 · 커뮤니티 · 연출 스위치 · 로그인(맨 오른쪽).
// 헤더로 옮겨 갈 때는 연출 없이 바로 간다 — 연출은 책상 위 물건을 눌렀을 때만 넘기는 표시(from)로 켜지고, 헤더는 그 표시를 넘기지 않는다
export function renderSiteHeader({ navigate }) {
  const header = element('header', 'site-header');
  // 로고는 "Law"만 따로 감싸 둔다. 색은 화면 CSS가 정한다(S-01은 브론즈, 나머지 화면은 글자색 그대로)
  const logo = button('', () => navigate('landing'), 'logo-button');
  logo.append('내', element('span', 'law', 'Law'), '남불');
  const nav = element('nav', 'site-nav');
  nav.setAttribute('aria-label', '주요 메뉴');
  const here = window.location.pathname;
  const tab = (label, name, path) => {
    const node = button(label, () => navigate(name), 'nav-button');
    if (here === path || here.startsWith(`${path}/`)) node.setAttribute('aria-current', 'page');
    return node;
  };
  // 아직 화면이 없는 메뉴: 누를 수 없고 낭독기에는 "준비 중"으로 읽힌다. 화면이 생기면 tab()으로 바꾼다
  const soon = (label, className = 'nav-button') => {
    const node = element('button', className, label);
    node.type = 'button';
    node.setAttribute('aria-disabled', 'true');
    node.setAttribute('aria-label', `${label} — 준비 중`);
    return node;
  };
  nav.append(
    tab('사건 목록', 'list', '/cases'),
    tab('성향검사', 'test', '/test'),
    soon('커뮤니티'),
    motionSwitch(),
    soon('로그인', 'nav-button login-button'), // 사용자 로그인 화면 · API가 아직 없다
  );
  header.append(logo, nav);
  return header;
}

// 연출 스위치(utils/motion.js). 기기의 "동작 줄이기"가 켜져 있으면 꺼진 채로 누를 수 없다
function motionSwitch() {
  const node = element('button', 'nav-button motion-switch');
  node.type = 'button';
  node.setAttribute('role', 'switch');
  const track = element('span', 'switch-track');
  track.setAttribute('aria-hidden', 'true');
  track.append(element('span', 'switch-thumb'));
  node.append(element('span', '', '연출'), track);
  const sync = () => node.setAttribute('aria-checked', String(!reducedMotion()));
  if (systemReducesMotion()) {
    node.setAttribute('aria-disabled', 'true');
    node.setAttribute('aria-label', '연출 — 기기의 동작 줄이기 설정으로 꺼져 있어요');
  } else {
    node.addEventListener('click', () => {
      setMotionSwitch(!motionSwitchOn());
      sync();
    });
  }
  sync();
  return node;
}

export function renderSiteFooter() {
  const footer = element('footer', 'site-footer');
  footer.append(
    element('p', '', '이 서비스의 사건은 실제 판결을 바탕으로 비식별화 처리한 내용입니다.'),
    element('p', '', '내Law남불은 법률 자문이나 특정 사건에 대한 법적 판단을 제공하지 않습니다.'),
  );
  return footer;
}
