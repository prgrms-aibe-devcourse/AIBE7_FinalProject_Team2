import { button, element } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { loadAdminApi } from './adminApi.js';
import { focusHeading, labeledField } from './adminKit.js';
import { errorText } from './adminLabels.js';
import { ADMIN_MENU, isMenuReady } from './adminMenu.js';
import { clearUser, isAdmin } from '../../utils/session.js';
import './admin.css';

// S-19 관리자 화면 틀: 로그인 확인(A-4) → 없으면 로그인 폼(A-1 · A-2) → 있으면 화면을 그린다.
// 사용자 사이트 헤더 · 푸터 대신 관리자 전용 머리줄을 쓴다(사용자 메뉴로 내부 정보 화면이 이어지지 않게).
// 화면 도중 세션이 끝나 401이 오면 ctx.unauthorized()로 로그인 폼을 다시 보여 준다.
// FE-105: 본문 건너뛰기 링크, 작업 완료 알림(ctx.notify — 화면 아래에 떠 있는 알림줄이라 스크롤 위치와 상관없이 보인다),
// 목 모드 "목데이터" 표시, 틀 오류에 다시 시도, 화면에 들어오면 제목(h1)으로 포커스

/**
 * @param {HTMLElement} container
 * @param {{ navigate: Function, title: string, section?: string, draw: (ctx) => Promise<void> }} options
 *   section  왼쪽 메뉴에서 눌린 것으로 표시할 항목(adminMenu.js의 key)
 *   draw(ctx)  ctx = { api, admin, main, active, unauthorized, setTitle, status, notify }
 */
export async function renderAdminShell(container, { navigate, title, section, draw }) {
  const root = element('div', 'admin-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  setTitle(title); // 틀 오류 · 로그인 전에도 탭 제목으로 어느 화면인지 알 수 있게
  const api = await loadAdminApi();
  if (!active()) return;

  // 본문 건너뛰기: 해시 이동 대신 포커스만 옮긴다(주소가 바뀌면 라우터가 화면을 다시 그린다)
  const skip = element('a', 'admin-skip', '본문으로 건너뛰기');
  skip.href = '#admin-main';
  skip.addEventListener('click', (event) => {
    event.preventDefault();
    if (main.querySelector('h1')) focusHeading(main); // 화면 제목에 포커스 테두리가 보이고 낭독기도 제목을 읽는다. 아직 제목이 없으면(불러오는 중) 본문 자체로
    else main.focus();
  });
  const header = element('header', 'admin-header');
  const brand = button('', () => navigate('landing'), 'admin-brand'); // 로고는 사용자 사이트 메인으로
  brand.append('내', element('span', 'law', 'Law'), '남불 ', element('small', '', '관리자'));
  const brandWrap = element('div', 'admin-brand-wrap');
  brandWrap.append(brand);
  if (API_MODE === 'mock') brandWrap.append(element('span', 'admin-mock-label', '목데이터')); // 목 모드 숫자 · 목록은 모두 예시 값이다
  const user = element('div', 'admin-user');
  header.append(brandWrap, user);
  const main = element('main', 'admin-main');
  main.id = 'admin-main';
  main.tabIndex = -1;
  const body = element('div', 'admin-body');
  const menu = drawMenu();
  body.append(menu, main);
  // 작업 완료 알림: 화면을 다시 그려도 남아 있게 main 밖에 둔다. 다음 알림이 오거나 몇 초 뒤에 사라진다
  const toast = element('p', 'admin-toast');
  toast.setAttribute('role', 'status');
  toast.hidden = true;
  let toastTimer = 0;
  let toastTextTimer = 0;
  root.append(skip, header, body, toast);
  // 좁은 화면에서 메뉴는 옆으로 밀리는 한 줄이다: 지금 화면의 항목이 보이게 그 자리로 민다(페이지는 스크롤하지 않는다)
  const current = menu.querySelector('.is-active');
  if (current && menu.scrollWidth > menu.clientWidth) {
    menu.scrollLeft = Math.max(0, current.getBoundingClientRect().left - menu.getBoundingClientRect().left - 16);
  }

  function notify(text) {
    clearTimeout(toastTimer);
    clearTimeout(toastTextTimer);
    // role=status 영역이 숨어 있는 동안 바뀐 글은 낭독되지 않는다: 먼저 보이게 하고, 글은 다음 task에서 쓴다
    toast.hidden = false;
    toastTextTimer = setTimeout(() => { toast.textContent = text; }, 0);
    toastTimer = setTimeout(() => { toast.hidden = true; toast.textContent = ''; }, 6000);
  }

  // 왼쪽 메뉴: 로그인 전에는 숨긴다(showLogin이 body.dataset.locked로 가린다)
  function drawMenu() {
    const nav = element('nav', 'admin-menu');
    nav.setAttribute('aria-label', '관리자 메뉴');
    ADMIN_MENU.forEach((group) => {
      const block = element('div', 'admin-menu-group');
      block.append(element('p', 'admin-menu-title', group.group));
      group.items.forEach((item) => {
        const link = button(item.label, () => navigate(item.route), `admin-menu-item${item.key === section ? ' is-active' : ''}`);
        if (item.key === section) link.setAttribute('aria-current', 'page');
        if (!isMenuReady(item)) {
          link.classList.add('is-soon');
          link.setAttribute('aria-disabled', 'true');
          link.setAttribute('aria-label', `${item.label} — 준비 중`);
          link.append(element('small', '', '준비 중'));
          link.addEventListener('click', (event) => event.stopImmediatePropagation(), { capture: true });
        }
        block.append(link);
      });
      nav.append(block);
    });
    return nav;
  }

  function setTitle(text) {
    document.title = `${text} · 관리자 · 내Law남불`;
  }

  function status(text, role = 'status') {
    const panel = element('p', 'admin-message', text);
    panel.setAttribute('role', role);
    main.replaceChildren(panel);
  }

  // 틀을 그리지 못했을 때(관리자 정보 확인 · 화면 그리기 실패): 화면 제목 + 이유 + 다시 시도
  function failed(text, retry) {
    const panel = element('div', 'admin-message');
    panel.setAttribute('role', 'alert');
    panel.append(element('p', '', text), button('다시 시도', retry));
    main.replaceChildren(element('h1', '', title), panel);
  }

  function showLogin(notice) {
    if (isAdmin()) clearUser(); // 로그아웃 · 세션 만료로 로그인 폼이 뜨면 사용자 사이트의 관리자 표시도 거둔다
    body.dataset.locked = '1';
    user.replaceChildren();
    setTitle('로그인');
    const form = element('form', 'admin-login');
    form.noValidate = true;
    const heading = element('h1', '', '관리자 로그인');
    const email = element('input');
    Object.assign(email, { type: 'email', name: 'email', autocomplete: 'username', required: true, maxLength: 100 });
    const password = element('input');
    Object.assign(password, { type: 'password', name: 'password', autocomplete: 'current-password', required: true, maxLength: 100 });
    const emailLabel = labeledField('이메일', email);
    const passwordLabel = labeledField('비밀번호', password);
    const error = element('p', 'admin-error');
    error.setAttribute('role', 'alert');
    if (notice) error.textContent = notice;
    const submit = element('button', 'primary-button', '로그인');
    submit.type = 'submit';
    form.append(heading, emailLabel, passwordLabel, error, submit);

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (submit.disabled) return;
      if (!email.value.trim() || !password.value) {
        error.textContent = '이메일과 비밀번호를 입력해 주세요.';
        (email.value.trim() ? password : email).focus(); // 비어 있는 첫 칸으로
        return;
      }
      submit.disabled = true;
      error.textContent = '';
      try {
        await api.csrf(); // 로그인도 CSRF 토큰이 필요하다 (8-1)
        const admin = await api.login(email.value.trim(), password.value);
        password.value = '';
        if (active()) await enter(admin);
      } catch (failure) {
        if (!active()) return;
        error.textContent = failure?.code === 'INVALID_CREDENTIALS' ? '이메일 또는 비밀번호가 맞지 않아요.' : errorText(failure, '로그인하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
        submit.disabled = false;
        password.focus();
      }
    });
    main.replaceChildren(form);
    email.focus();
  }

  // 성공하거나 401(이미 끝난 세션 · 비활성 계정, BE-67)이면 로그아웃된 것으로 본다.
  // 그 밖의 실패(연결 오류 등)는 서버 세션이 남아 있을 수 있으므로 로그인 폼으로 보내지 않고 알린다
  async function logout(event) {
    const trigger = event?.currentTarget;
    if (trigger) trigger.disabled = true;
    try {
      await api.logout();
    } catch (failure) {
      if (!active()) return;
      if (failure?.code !== 'UNAUTHORIZED') {
        const alert = element('p', 'admin-error', `로그아웃하지 못했어요. ${errorText(failure, '잠시 뒤 다시 시도해 주세요.')}`);
        alert.setAttribute('role', 'alert');
        main.querySelector(':scope > .admin-logout-error')?.remove();
        alert.classList.add('admin-logout-error');
        main.prepend(alert);
        if (trigger) trigger.disabled = false;
        return;
      }
    }
    if (active()) showLogin('로그아웃했어요.');
  }

  async function enter(admin) {
    delete body.dataset.locked;
    user.replaceChildren(element('span', '', admin.displayName || admin.email), button('로그아웃', logout, 'secondary-button'));
    setTitle(title);
    const ctx = {
      api, admin, main, active, setTitle, status, notify,
      unauthorized: () => showLogin('세션이 끝났어요. 다시 로그인해 주세요.'),
    };
    try {
      await draw(ctx);
      // 화면에 들어오면 제목으로 포커스: 로그인 뒤 · 메뉴로 옮겨 온 뒤 키보드 위치가 문서 처음(body)에 남지 않게
      const focused = document.activeElement;
      if (active() && (!focused || focused === document.body || !root.contains(focused))) focusHeading(main);
    } catch (failure) {
      if (!active()) return;
      if (failure?.code === 'UNAUTHORIZED') ctx.unauthorized();
      else failed(errorText(failure, '화면을 불러오지 못했어요.'), () => enter(admin));
    }
  }

  async function start() {
    status('관리자 정보를 확인하는 중이에요.');
    try {
      const admin = await api.me();
      if (active()) await enter(admin);
    } catch (failure) {
      if (!active()) return;
      if (failure?.code === 'UNAUTHORIZED') showLogin();
      else failed(errorText(failure, '관리자 정보를 확인하지 못했어요.'), start);
    }
  }
  await start();
}
