import { button, element } from '../../components/siteLayout.js';
import { loadAdminApi } from './adminApi.js';
import { errorText } from './adminLabels.js';
import './admin.css';

// S-19 관리자 화면 틀: 로그인 확인(A-4) → 없으면 로그인 폼(A-1 · A-2) → 있으면 화면을 그린다.
// 사용자 사이트 헤더 · 푸터 대신 관리자 전용 머리줄을 쓴다(사용자 메뉴로 내부 정보 화면이 이어지지 않게).
// 화면 도중 세션이 끝나 401이 오면 ctx.unauthorized()로 로그인 폼을 다시 보여 준다.

/**
 * @param {HTMLElement} container
 * @param {{ navigate: Function, title: string, draw: (ctx) => Promise<void> }} options
 *   draw(ctx)  ctx = { api, admin, main, active, unauthorized, setTitle }
 */
export async function renderAdminShell(container, { navigate, title, draw }) {
  const root = element('div', 'admin-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  const api = await loadAdminApi();
  if (!active()) return;

  const header = element('header', 'admin-header');
  const brand = button('', () => navigate('admin'), 'admin-brand');
  brand.append('내', element('span', 'law', 'Law'), '남불 ', element('small', '', '관리자'));
  const user = element('div', 'admin-user');
  header.append(brand, user);
  const main = element('main', 'admin-main');
  root.append(header, main);

  function setTitle(text) {
    document.title = `${text} · 관리자 · 내Law남불`;
  }

  function status(text, role = 'status') {
    const panel = element('p', 'admin-message', text);
    panel.setAttribute('role', role);
    main.replaceChildren(panel);
  }

  function showLogin(notice) {
    user.replaceChildren();
    setTitle('로그인');
    const form = element('form', 'admin-login');
    form.noValidate = true;
    const heading = element('h1', '', '관리자 로그인');
    const email = element('input');
    Object.assign(email, { type: 'email', name: 'email', autocomplete: 'username', required: true, maxLength: 100 });
    const password = element('input');
    Object.assign(password, { type: 'password', name: 'password', autocomplete: 'current-password', required: true, maxLength: 100 });
    const emailLabel = element('label', 'admin-field');
    emailLabel.append(element('span', '', '이메일'), email);
    const passwordLabel = element('label', 'admin-field');
    passwordLabel.append(element('span', '', '비밀번호'), password);
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
    user.replaceChildren(element('span', '', admin.displayName || admin.email), button('로그아웃', logout, 'secondary-button'));
    setTitle(title);
    const ctx = {
      api, admin, main, active, setTitle, status,
      unauthorized: () => showLogin('세션이 끝났어요. 다시 로그인해 주세요.'),
    };
    try {
      await draw(ctx);
    } catch (failure) {
      if (!active()) return;
      if (failure?.code === 'UNAUTHORIZED') ctx.unauthorized();
      else status(errorText(failure, '화면을 불러오지 못했어요.'), 'alert');
    }
  }

  status('관리자 정보를 확인하는 중이에요.');
  try {
    const admin = await api.me();
    if (active()) await enter(admin);
  } catch (failure) {
    if (!active()) return;
    if (failure?.code === 'UNAUTHORIZED') showLogin();
    else status(errorText(failure, '관리자 정보를 확인하지 못했어요.'), 'alert');
  }
}
