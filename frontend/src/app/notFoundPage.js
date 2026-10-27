// S-14 404 · 오류 화면 (IA 4장: 잘못된 사건 ID 등 — 와이어프레임 없음, MVP 범위에서 직접 구성)
// 공통 헤더 · 푸터는 라우터가 위아래에 붙인다 (FE-56, routes.js에 ownsHeader · ownsFooter 없음).
// FE-104: 로그인과 같은 책상(loginDesk.js) 위에 "찾지 못한 서류" 한 장을 놓고, 내로 · 남불이 서류 뒤에서 미안한 얼굴로 고개를 내민다.
// 없는 사건(/cases/…, /me/cases/…)과 잘못된 주소는 문구를 나눈다. 화면이 통째로 바뀌는 것이라 알림 역할(role=alert)은 두지 않는다
import { button, element } from '../components/siteLayout.js';
import { loginDesk } from '../pages/login/loginDesk.js';

const CASE_PATH = /^\/(me\/)?cases\//;

export function renderNotFoundPage(container, { navigate }) {
  const { root, card, peek } = loginDesk(container, 'section');
  root.classList.add('not-found-page');
  card.classList.add('not-found-sheet');
  card.setAttribute('aria-labelledby', 'not-found-heading');
  peek('cheek/idle-sorry');

  const path = window.location.pathname;
  const missingCase = CASE_PATH.test(path);
  const heading = element('h1', '', missingCase ? '사건 기록을 찾지 못했어요' : '이 주소의 서류가 없어요');
  heading.id = 'not-found-heading';
  const lead = element('p', 'not-found-lead', missingCase
    ? '사건 번호가 바뀌었거나 아직 공개되지 않은 사건이에요. 사건 목록에서 다시 골라 주세요.'
    : '주소가 바뀌었거나 잘못 입력된 것 같아요. 사건 목록이나 처음 화면에서 다시 시작해 주세요.');
  // 찾은 주소를 서류철 꼬리표처럼 적어 둔다(무엇을 찾다 못 찾았는지 보이게)
  const label = element('p', 'not-found-label');
  label.append(element('span', '', '찾은 주소'), element('code', '', path));

  const actions = element('div', 'login-actions not-found-actions');
  actions.append(
    button('사건 목록으로', () => navigate('list'), 'primary-button'),
    button('처음으로', () => navigate('landing'), 'secondary-button'),
  );
  card.append(heading, lead, label, actions);
}
