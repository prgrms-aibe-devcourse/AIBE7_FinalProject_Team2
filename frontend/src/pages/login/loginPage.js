import { button, element } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/index.js';
import { TOKEN_NOT_ISSUED } from '../../api/authFlow.js';
import { clearUser, currentUser, isAdmin, saveUser } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';
import { adminLogout, loginTarget, tryAdminLogin } from './adminLogin.js';
import { canGoBackInApp } from '../../app/router.js';
import { devJumpBar } from './loginDevJump.js';
import { loginDesk } from './loginDesk.js';
import { kakaoButton, setBusy } from './loginParts.js';

// S-12 로그인 (FE-52, IA 4장 /login). 책상 위 펠트 패드(FE-69)에 카카오 로그인 버튼 + 아이디 · 비밀번호 폼.
// 목 모드에서는 개발용 목업 로그인(mocks/authMockApi.js)이, 실제 모드에서는 카카오 로그인(realApi.js login, FE-74)이 받는다.
// 아이디 · 비밀번호(FE-117): 아이디에 @가 있으면 관리자 로그인(A-2), 없으면 회원 아이디 로그인(M-6)으로만 보낸다(adminLoginRule.js).
// 로그인하지 않아도 모든 판결 체험을 할 수 있다(REQ-003).
// 로그인하면 닉네임을 이 브라우저에 두고(utils/session.js) 공통 헤더의 "로그인" 자리에 닉네임이 나온다.
// FE-104: 아이디 · 비밀번호 폼 아래 회원가입(signupPage.js) 링크.
// FE-117: 목 · 실제 모드 모두 api.login이 있어 예전의 "관리자 입력칸만 보여 주고 준비 중 안내"(adminOnly) 분기는 지웠다 (PR #308 리뷰)

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
    { label: '로그인 중(로딩)', run: () => { const { busy } = showForm(); setBusy(busy, true, busy[1]); } },
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

  function showForm() {
    const heading = element('h1', '', '로그인');
    heading.id = 'login-heading';
    const lead = element('p', 'login-lead', '로그인하지 않아도 모든 판결 체험을 할 수 있어요.');
    const parts = [heading, lead];
    if (API_MODE === 'mock') {
      parts.push(element('p', 'login-mock-note', '개발용 목업 로그인이에요. 아무 아이디 · 비밀번호로 들어가지고(@가 있는 아이디는 관리자), 입력값은 어디에도 보내거나 남기지 않아요.'));
    }

    const error = element('p', 'login-error');
    error.id = 'login-error';
    error.setAttribute('role', 'alert');
    error.hidden = true;

    const kakao = kakaoButton('카카오 로그인', () => signIn({ method: 'kakao' }));
    const divider = element('p', 'login-divider', '또는');

    const form = element('form', 'login-form');
    form.noValidate = true; // 빈칸 안내는 아래 문구로 직접 한다(브라우저 말풍선 대신)
    // 회원이 이메일을 넣으면 관리자 로그인으로 간다(@ 분기) — 입력 전에 알려 둔다 (PR #308 리뷰 · FE-117 결정 기록)
    const idInput = field(form, 'login-id', '아이디', { type: 'text', autocomplete: 'username', hint: '회원 아이디에는 @가 없어요. 관리자는 이메일로 로그인해요.' });
    const passwordInput = field(form, 'login-password', '비밀번호', { type: 'password', autocomplete: 'current-password' });
    const submit = element('button', 'primary-button login-submit', '로그인');
    submit.type = 'submit';
    form.append(error, submit);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      if (pending) return;
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

    // 회원가입은 지금 기록 항목을 바꿔(replace) 연다: 가입을 마치고 뒤로 가면 로그인 화면이 아니라 로그인 전에 보던 화면으로 돌아간다
    const signup = element('p', 'login-signup', '처음이신가요?');
    signup.append(button('회원가입', () => navigate('signup', {}, { replace: true }), 'login-link'));
    // 로그인하지 않고 나가는 길(헤더 말고도 패드 안에서 바로)
    const browse = element('p', 'login-browse');
    browse.append(button('로그인 없이 사건 보러 가기', () => navigate('list'), 'login-link'));

    parts.push(kakao, divider, form, signup, browse);
    let pending = false; // return보다 앞에 둔다(뒤에 두면 초기화되지 않아 로그인 때 ReferenceError)
    peek(null);
    card.replaceChildren(...parts);
    return { form, showError, busy: [kakao, submit] };

    // 오류 문구는 누른 버튼 가까이에 둔다: 카카오는 버튼 바로 아래, 아이디 · 비밀번호는 로그인 버튼 위
    function showError(message, near = form) {
      if (near === kakao) kakao.after(error); else form.insertBefore(error, submit);
      error.textContent = message;
      error.hidden = false;
    }

    async function signIn(request) {
      if (pending) return;
      pending = true;
      const busy = [kakao, submit];
      setBusy(busy, true, request.method === 'kakao' ? kakao : submit);
      error.hidden = true;
      try {
        // 아이디에 @가 있으면 관리자 로그인만, 없으면 회원 로그인만 시도한다(FE-117, adminLoginRule.js loginTarget). 둘 다 부르지 않는다.
        // 관리자면 마이페이지 자리가 관리자 페이지가 된다 (FE-53)
        // 로그인 표시(saveUser)는 화면을 떠났는지와 상관없이 남긴다 — 서버는 이미 로그인됐으므로, 건너뛰면 헤더만 로그아웃으로 보인다.
        // 화면을 떠났으면 이동만 하지 않는다
        if (loginTarget(request) === 'admin') {
          const admin = await tryAdminLogin(request.id, request.password);
          if (!admin) throw { code: 'INVALID_CREDENTIALS' };
          saveUser({ nickname: admin.displayName || admin.email, role: 'admin' });
          if (!container.contains(root)) return; // 기다리는 사이 다른 화면으로 갔으면 이동하지 않는다
          navigate(homeRoute(), {}, { replace: true });
          return;
        }
        // 여기부터는 회원 로그인(카카오 · 아이디). @가 없는 입력은 관리자 API로 가지 않는다
        const { nickname } = await api.login(request);
        saveUser({ nickname }); // 화면을 떠났어도 로그인 표시는 남긴다(위 관리자와 같은 이유)
        if (!container.contains(root)) return;
        // 보던 화면에서 로그인 자리를 눌러 왔으면 그 화면으로 돌아간다(진행 상태는 서버 · 쿠키에 있어 그대로다). 바로 /login으로 왔으면 메인으로
        if (canGoBackInApp()) history.back(); else navigate('landing', {}, { replace: true });
      } catch (failure) {
        if (!container.contains(root)) return;
        if (failure?.code !== 'LOGIN_CANCELLED') showError(failureMessage(failure, request.method), request.method === 'kakao' ? kakao : form);
        setBusy(busy, false);
        pending = false;
      }
    }
  }
}

// 실패 원인별 문구: 연결 실패는 다시 시도, 틀린 입력은 확인, 요청 제한은 잠시 기다리기, 그 밖은 일반 안내.
// 아이디가 없는지 · 비밀번호가 틀렸는지는 서버도 나누지 않으므로(BE-113 결정 3) 화면도 한 문구로 알린다
function failureMessage(failure, method) {
  if (failure?.code === 'NETWORK_ERROR') return '서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  if (failure?.code === 'TOO_MANY_REQUESTS') return '로그인 시도가 너무 많아요. 잠시 뒤 다시 시도해 주세요.';
  // 아이디 · 비밀번호는 맞았는데 첫 토큰을 받지 못했다(api/authFlow.js). 남은 쿠키는 지웠으니 다시 로그인하면 된다
  if (failure?.code === TOKEN_NOT_ISSUED) return '로그인을 마치지 못했어요. 잠시 뒤 다시 로그인해 주세요.';
  if (method === 'password' && (failure?.code === 'INVALID_CREDENTIALS' || failure?.code === 'VALIDATION_ERROR')) {
    return '아이디 또는 비밀번호가 맞지 않아요. 다시 확인해 주세요.';
  }
  return '로그인하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}

// 라벨 + 입력칸 한 줄(+ 안내 문구). 안내 문구 · 빈칸 안내(#login-error)를 입력칸 설명으로 잇는다. 안내 모양은 가입 화면과 같다(signup-hint)
function field(form, id, label, attributes) {
  const row = element('div', 'login-field');
  const caption = element('label', '', label);
  caption.htmlFor = id;
  const input = element('input');
  input.id = id;
  input.name = id;
  Object.assign(input, { type: attributes.type, autocomplete: attributes.autocomplete });
  input.spellcheck = false; // 아이디 · 비밀번호에 맞춤법 밑줄이 뜨지 않게
  input.autocapitalize = 'off'; // 모바일에서 아이디 첫 글자가 대문자가 되지 않게
  input.addEventListener('input', () => input.removeAttribute('aria-invalid'));
  row.append(caption, input);
  const described = [];
  if (attributes.hint) {
    const help = element('p', 'signup-hint', attributes.hint);
    help.id = `${id}-hint`;
    row.append(help);
    described.push(help.id);
  }
  described.push('login-error');
  input.setAttribute('aria-describedby', described.join(' '));
  form.append(row);
  return input;
}
