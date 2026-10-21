import { button, element } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/index.js';
import { clearUser, currentUser, isAdmin, saveUser } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';
import { adminLogout, tryAdminLogin } from './adminLogin.js';
import { canGoBackInApp } from '../../app/router.js';
import { devJumpBar } from './loginDevJump.js';

// S-12 로그인 (FE-52, IA 4장 /login). 책상 위 펠트 패드(FE-69)에 카카오 로그인 버튼 + 아이디 · 비밀번호 폼.
// 목 모드에서는 개발용 목업 로그인(mocks/authMockApi.js)이, 실제 모드에서는 카카오 로그인(realApi.js login, FE-74)이 받는다.
// 실제 모드의 아이디 · 비밀번호는 관리자 로그인만 된다. 로그인하지 않아도 모든 판결 체험을 할 수 있다(REQ-003).
// 로그인하면 닉네임을 이 브라우저에 두고(utils/session.js) 공통 헤더의 "로그인" 자리에 닉네임이 나온다. 회원가입(/signup)은 아직 없다
const KAKAO_SVG = '<svg viewBox="0 0 18 18" aria-hidden="true" focusable="false"><path d="M9 1.6C4.6 1.6 1 4.4 1 7.9c0 2.2 1.5 4.2 3.7 5.3l-.9 3.4c-.1.3.3.6.5.4l4.1-2.7.6.1c4.4 0 8-2.8 8-6.3S13.4 1.6 9 1.6Z"/></svg>';
const NOT_READY = '로그인은 아직 준비 중이에요. 로그인하지 않아도 모든 판결 체험을 할 수 있어요.';

// 책상 위 펠트 패드(FE-69). 메인 책상(S-01)과 같은 나뭇결 위에 메인처럼 가로로 긴 데스크 패드를 놓고, 옆에 펜을 하나 걸쳐 둔다(장식).
// 로그인 · 로그인됨 · 카카오 콜백 화면이 함께 쓴다. 상태가 바뀌면 card 안만 바꾼다
export function loginDesk(container, tag = 'section') {
  const root = element('div', 'login-page');
  const main = element('main', 'login-main');
  const desk = element('div', 'login-desk');
  const card = element(tag, 'login-card');
  const pen = element('span', 'login-pen');
  pen.setAttribute('aria-hidden', 'true');
  desk.append(card, pen);
  main.append(desk);
  root.append(main); // 공통 헤더는 라우터가 붙인다(FE-56)
  container.replaceChildren(root);

  // 내로 · 남불이 패드 뒤에서 위 가장자리 너머로 고개를 내민다(장식). 네이비 내로가 펠트에 묻히지 않게 몸 아래쪽은 패드가 가린다.
  // look은 characters/ 아래 '폴더/표정'(예: 'cheek/idle-sorry'), 없으면 치운다. 고개 내밀 자리는 .login-card-message가 만든다
  function peek(look) {
    desk.querySelectorAll('.login-peek').forEach((node) => node.remove());
    card.classList.toggle('login-card-message', Boolean(look));
    if (!look) return;
    const [folder, face] = look.split('/');
    ['naero', 'nambul'].forEach((who) => {
      const image = element('img', `login-peek peek-${who}`);
      image.src = `/images/characters/${folder}/${who}_${face}.webp`;
      image.alt = '';
      image.decoding = 'async';
      desk.prepend(image);
    });
  }
  return { root, main, desk, card, peek };
}

export function renderLoginPage(container, { api, navigate }) {
  const { root, main, card, peek } = loginDesk(container);
  card.setAttribute('aria-labelledby', 'login-heading');

  const user = currentUser();
  if (user) showSignedIn(user);
  else showForm();

  // 개발용: 모든 화면 상태로 바로 넘어간다(목 모드만). 로그인 표시는 바꾸지 않고 모양만 그린다
  const jump = devJumpBar([
    { label: '입력 폼', run: () => showForm() },
    { label: '빈칸 오류', run: () => showForm().form.requestSubmit() },
    { label: '로그인 실패', run: () => showForm().showError('로그인하지 못했어요. 잠시 뒤 다시 시도해 주세요.') },
    { label: '로그인 중(로딩)', run: () => { const { busy } = showForm(); busy.forEach((node) => { node.disabled = true; }); } },
    { label: '로그인됨', run: () => showSignedIn({ nickname: '내로판사' }, false) },
    { label: '관리자 로그인됨', run: () => showSignedIn({ nickname: '운영자' }, true) },
    { label: '콜백 로딩', run: () => window.location.assign('/auth/callback?dev=loading') },
    { label: '콜백 실패', run: () => window.location.assign('/auth/callback?error=login_failed') },
  ]);
  if (jump) main.append(jump);

  // 이미 로그인한 채로 왔을 때: 누구로 로그인했는지 + 마이페이지(관리자는 관리자 페이지) · 로그아웃
  function showSignedIn({ nickname }, admin = isAdmin()) {
    const heading = element('h1', '', '로그인했어요');
    heading.id = 'login-heading';
    const actions = element('div', 'login-actions');
    actions.append(
      button(admin ? '관리자 페이지로' : '마이페이지로', () => navigate(admin ? homeRoute() : 'me'), 'primary-button'),
      button('로그아웃', async () => {
        await (admin ? adminLogout() : Promise.resolve(api.logout?.()).catch(() => {})); // 서버 쪽 로그아웃이 없거나 실패해도 이 브라우저의 표시는 지운다
        clearUser();
        navigate('landing', {}, { replace: true });
      }, 'secondary-button'),
    );
    peek('scene/react-happy');
    card.replaceChildren(heading, element('p', 'login-lead', `${nickname} 님으로 로그인한 상태예요.`), actions);
  }

  // 사용자 로그인이 없는 실제 모드(api.login 없음)에서는 카카오 · 회원가입 없이 관리자 로그인 입력칸만 보여 주고, 일반 로그인은 준비 중이라고 알린다 (FE-53)
  function showForm() {
    const adminOnly = !api.login;
    const heading = element('h1', '', '로그인');
    heading.id = 'login-heading';
    const lead = element('p', 'login-lead', adminOnly ? NOT_READY : '로그인하지 않아도 모든 판결 체험을 할 수 있어요.');
    const parts = [heading, lead];
    if (API_MODE === 'mock') {
      parts.push(element('p', 'login-mock-note', '개발용 목업 로그인이에요. 아무 아이디 · 비밀번호로 들어가지고(admin으로 시작하는 아이디는 관리자), 입력값은 어디에도 보내거나 남기지 않아요.'));
    }

    const error = element('p', 'login-error');
    error.id = 'login-error';
    error.setAttribute('role', 'alert');
    error.hidden = true;

    const kakao = element('button', 'kakao-button');
    kakao.type = 'button';
    kakao.innerHTML = KAKAO_SVG;
    kakao.append(element('span', '', '카카오 로그인'));
    kakao.addEventListener('click', () => signIn({ method: 'kakao' }));

    const divider = element('p', 'login-divider', '또는');

    const form = element('form', 'login-form');
    form.noValidate = true; // 빈칸 안내는 아래 문구로 직접 한다(브라우저 말풍선 대신)
    const idInput = field(form, 'login-id', '아이디', { type: 'text', autocomplete: 'username' });
    const passwordInput = field(form, 'login-password', '비밀번호', { type: 'password', autocomplete: 'current-password' });
    const submit = element('button', 'primary-button login-submit', '로그인');
    submit.type = 'submit';
    form.append(error, submit);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const empty = [idInput, passwordInput].filter((input) => !input.value.trim());
      [idInput, passwordInput].forEach((input) => {
        if (empty.includes(input)) input.setAttribute('aria-invalid', 'true');
        else input.removeAttribute('aria-invalid');
      });
      if (empty.length) {
        showError(empty.length === 2 ? '아이디와 비밀번호를 입력해 주세요.' : `${empty[0] === idInput ? '아이디' : '비밀번호'}를 입력해 주세요.`);
        empty[0].focus();
        return;
      }
      signIn({ method: 'password', id: idInput.value.trim(), password: passwordInput.value });
    });

    const signup = element('p', 'login-signup', '아직 계정이 없나요? ');
    const signupButton = element('button', 'login-link', '회원가입');
    signupButton.type = 'button';
    signupButton.setAttribute('aria-disabled', 'true');
    signupButton.setAttribute('aria-label', '회원가입 — 준비 중');
    signup.append(signupButton);

    parts.push(...(adminOnly ? [form] : [kakao, divider, form, signup]));
    peek(null);
    card.replaceChildren(...parts);
    return { form, showError, busy: [kakao, submit] };

    function showError(message) {
      error.textContent = message;
      error.hidden = false;
    }

    let pending = false;
    async function signIn(request) {
      if (pending) return;
      pending = true;
      const busy = [kakao, submit];
      busy.forEach((node) => { node.disabled = true; });
      error.hidden = true;
      try {
        // 아이디 · 비밀번호는 관리자 계정인지 먼저 본다. 관리자면 마이페이지 자리가 관리자 페이지가 된다 (FE-53)
        // 관리자 로그인이 불안정해도 일반 로그인은 막지 않는다: 일반 로그인이 있으면 관리자 쪽 오류는 넘기고, 관리자 입력칸만 있을 때(adminOnly)만 알린다
        let admin = null;
        if (request.method === 'password') {
          try {
            admin = await tryAdminLogin(request.id, request.password);
          } catch (failure) {
            if (adminOnly || API_MODE === 'real') throw failure; // 실제 모드는 아이디 · 비밀번호 회원 로그인이 없어 관리자 쪽 오류를 그대로 알린다
          }
        }
        if (!container.contains(root)) return; // 기다리는 사이 다른 화면으로 갔으면 이동하지 않는다
        if (admin) {
          saveUser({ nickname: admin.displayName || admin.email, role: 'admin' });
          navigate(homeRoute(), {}, { replace: true });
          return;
        }
        if (!api.login) {
          showError(request.method === 'password' ? `아이디와 비밀번호를 확인해 주세요. ${NOT_READY}` : NOT_READY);
          busy.forEach((node) => { node.disabled = false; });
          pending = false;
          return;
        }
        const { nickname } = await api.login(request);
        if (!container.contains(root)) return;
        saveUser({ nickname });
        // 보던 화면에서 로그인 자리를 눌러 왔으면 그 화면으로 돌아간다(진행 상태는 서버 · 쿠키에 있어 그대로다). 바로 /login으로 왔으면 메인으로
        if (canGoBackInApp()) history.back(); else navigate('landing', {}, { replace: true });
      } catch (failure) {
        if (!container.contains(root)) return;
        if (failure?.code !== 'LOGIN_CANCELLED') showError(failure?.code === 'VALIDATION_ERROR' ? '아이디와 비밀번호를 확인해 주세요.' : '로그인하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
        busy.forEach((node) => { node.disabled = false; });
        pending = false;
      }
    }
  }
}

// 라벨 + 입력칸 한 줄. 빈칸 안내(#login-error)를 입력칸 설명으로 잇는다
function field(form, id, label, attributes) {
  const row = element('div', 'login-field');
  const caption = element('label', '', label);
  caption.htmlFor = id;
  const input = element('input');
  input.id = id;
  input.name = id;
  Object.assign(input, { type: attributes.type, autocomplete: attributes.autocomplete });
  input.setAttribute('aria-describedby', 'login-error');
  input.addEventListener('input', () => input.removeAttribute('aria-invalid'));
  row.append(caption, input);
  form.append(row);
  return input;
}
