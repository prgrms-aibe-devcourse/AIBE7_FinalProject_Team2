// 공통 사이트 헤더 · 푸터 (REQ-071 법적 고지). S-01 · S-02 · S-14가 헤더를 그리고, 푸터는 S-01 · S-02가 직접, 나머지 화면은 라우터가 붙인다. 스타일은 src/style.css
import { motionSwitchOn, reducedMotion, setMotionSwitch, systemReducesMotion } from '../utils/motion.js';
import { currentUser, isAdmin } from '../utils/session.js';
import { homeRoute } from '../pages/admin/adminMenu.js';
import { API_MODE, setApiMode } from '../api/config.js';

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

// 공통 헤더(FE-50): 화면 위에 고정된다(style.css). 메뉴는 사건 목록 · 성향 테스트 · 커뮤니티 · 연출 스위치 · 로그인(맨 오른쪽).
// 로그인 자리(FE-52): 로그아웃 상태면 "로그인"(→ S-12), 로그인 상태면 닉네임(→ 마이페이지). 상태는 utils/session.js에서 읽는다
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
  nav.append(
    tab('사건 목록', 'list', '/cases'),
    tab('성향 테스트', 'test', '/test'),
    tab('커뮤니티', 'community', '/community'),
    motionSwitch(),
    ...(import.meta.env?.DEV ? [modeSwitch()] : []),
  );
  // 로그인 자리는 메뉴 밖 맨 오른쪽. 좁은 화면에서는 로고와 같은 줄에 남고 메뉴가 아랫줄로 내려간다(style.css)
  header.append(logo, nav, account(navigate, here));
  return header;
}

// 로그인 자리: 닉네임이 길면 줄여 보이고(…), 낭독기에는 전체 닉네임과 갈 곳을 읽는다
function account(navigate, here) {
  const user = currentUser();
  if (!user) {
    const login = button('로그인', () => navigate('login'), 'nav-button login-button');
    if (here === '/login') login.setAttribute('aria-current', 'page');
    return login;
  }
  // 관리자는 마이페이지 자리가 관리자 페이지다 (FE-53)
  const admin = isAdmin(user);
  const me = button('', () => navigate(admin ? homeRoute() : 'me'), 'nav-button login-button is-user');
  me.append(element('span', 'account-name', user.nickname));
  me.setAttribute('aria-label', `${user.nickname} — ${admin ? '관리자 페이지' : '마이페이지'}`);
  if (admin ? here.startsWith('/admin') : here === '/me') me.setAttribute('aria-current', 'page');
  return me;
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

// 목 ↔ 실제 API 스위치(개발 서버 전용). 켜면 실제 백엔드(8080 프록시), 끄면 목 데이터. 누르면 새로 불러온다
function modeSwitch() {
  const node = element('button', 'nav-button motion-switch');
  node.type = 'button';
  node.setAttribute('role', 'switch');
  node.setAttribute('aria-checked', String(API_MODE === 'real'));
  const track = element('span', 'switch-track');
  track.setAttribute('aria-hidden', 'true');
  track.append(element('span', 'switch-thumb'));
  node.append(element('span', '', API_MODE === 'real' ? '실제 API' : '목 데이터'), track);
  node.addEventListener('click', () => setApiMode(API_MODE === 'real' ? 'mock' : 'real'));
  return node;
}

// 목데이터 표기(FE-56): 실제 데이터가 아직 없어 목 값으로 채운 수치 옆에 붙인다. 숨기지 않고 목이라고 밝힌다(실제 반응 · 통계를 지어내지 않는다)
export function mockTag() {
  return element('span', 'mock-tag', '목데이터');
}

// navigate를 넘기면 이용약관 · 개인정보처리방침 입구를 함께 그린다(FE-62: 지금은 메인 맨 아래에서만)
export function renderSiteFooter({ navigate } = {}) {
  const footer = element('footer', 'site-footer');
  footer.append(
    element('p', '', '이 서비스의 사건은 실제 판결을 바탕으로 비식별화 처리한 내용입니다.'),
    element('p', '', '내Law남불은 법률 자문이나 특정 사건에 대한 법적 판단을 제공하지 않습니다.'),
  );
  if (navigate) {
    const links = element('nav', 'footer-links');
    links.setAttribute('aria-label', '약관 및 방침');
    links.append(button('이용약관', () => navigate('terms'), 'footer-link'), button('개인정보처리방침', () => navigate('privacy'), 'footer-link'));
    footer.append(links);
  }
  return footer;
}
