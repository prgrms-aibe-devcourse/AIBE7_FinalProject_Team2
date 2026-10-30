import { button, element } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/index.js';
import { TOKEN_NOT_ISSUED } from '../../api/authFlow.js';
import { currentUser, isAdmin, saveUser } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';
import { canGoBackInApp } from '../../app/router.js';
import { devJumpBar } from './loginDevJump.js';
import { loginDesk } from './loginDesk.js';
import { kakaoButton, setBusy } from './loginParts.js';
import { NICKNAME_MAX, NICKNAME_MAX_HANGUL, lockNicknameLength } from '../../utils/nickname.js';
import { validateSignup } from './signupRule.js';

// 회원가입 (FE-104, /signup). 로그인(S-12)과 같은 책상 위 펠트 패드에 카카오로 시작하기 + 아이디 · 비밀번호 · 닉네임 가입 폼.
// 목 모드는 목업 가입(mocks/authMockApi.js signup)이, 실제 모드는 회원가입 API(M-5, realApi.js signup — FE-117)가 받아
// 가입하면 바로 로그인된 상태가 된다. 카카오로 시작하기는 카카오 로그인(FE-74)으로 가입된다.
// FE-117: 목 · 실제 모드 모두 api.signup · api.login이 있어 예전의 "준비 중" 안내 분기는 지웠다 (PR #308 리뷰)
const NETWORK = '서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
const TOO_MANY = '요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.';
// 계정은 만들어졌는데 첫 토큰을 받지 못했다(api/authFlow.js). "가입하지 못했어요"라고 하면 다시 눌러 409(이미 쓰는 아이디)를 보게 된다
const SIGNED_UP_LOGIN_AGAIN = '가입은 됐어요. 로그인 화면에서 방금 만든 아이디로 로그인해 주세요.';
// 서버 형식 검사(400 VALIDATION_ERROR)가 칸 이름을 알려 주면 그 칸에 붙이는 문구. 화면 검사를 통과했는데 서버가 거절한 경우라 드물다
// 이미 쓰는 닉네임(409 DUPLICATE_NICKNAME, BE-139: 대소문자만 달라도 같다)
const DUPLICATE_NICKNAME = '이미 쓰고 있는 닉네임이에요. 다른 닉네임을 골라 주세요.';
// agreed: 서버도 약관 동의를 확인한다(BE-133). 화면 검사를 통과한 뒤라 거의 없지만, 오면 동의 줄에 붙인다
const SERVER_FIELD_MESSAGE = { id: '아이디 형식을 다시 확인해 주세요.', password: '비밀번호 형식을 다시 확인해 주세요.', nickname: '닉네임 형식을 다시 확인해 주세요.', agreed: '이용약관과 개인정보처리방침에 동의해 주세요.' };

export function renderSignupPage(container, { api, navigate }) {
  const { root, main, card, peek } = loginDesk(container);
  card.setAttribute('aria-labelledby', 'signup-heading');

  const user = currentUser();
  if (user) showSignedIn(user);
  else showForm();

  // 개발용: 화면 상태로 바로 넘어간다(목 모드 · 개발 서버만)
  const jump = devJumpBar([
    { label: '입력 폼', run: () => showForm() },
    { label: '칸 오류', run: () => showForm().form.requestSubmit() },
    { label: '아이디 중복', run: () => showForm().fill({ id: 'admin_naero' }).fieldError('id', '이미 쓰고 있는 아이디예요. 다른 아이디를 골라 주세요. 방금 가입했다면 로그인해 주세요.') },
    { label: '닉네임 중복', run: () => showForm().fill({ nickname: '중복판사' }).fieldError('nickname', DUPLICATE_NICKNAME) },
    { label: '가입 중(로딩)', run: () => { const { busy } = showForm(); setBusy(busy, true, busy[1], '가입하는 중…'); } },
    { label: '가입됨 · 로그인 필요', run: () => showForm().showSignedUpLoginAgain() },
    { label: '이미 로그인', run: () => showSignedIn({ nickname: '내로판사' }, false) },
  ]);
  if (jump) main.append(jump);

  function showSignedIn({ nickname }, admin = isAdmin()) {
    const heading = element('h1', '', '이미 로그인했어요');
    heading.id = 'signup-heading';
    const actions = element('div', 'login-actions');
    actions.append(
      button(admin ? '관리자 페이지로' : '마이페이지로', () => navigate(admin ? homeRoute() : 'me'), 'primary-button'),
      button('사건 목록으로', () => navigate('list'), 'secondary-button'),
    );
    peek('scene/react-happy');
    card.replaceChildren(heading, element('p', 'login-lead', `${nickname} 님으로 로그인한 상태라 새로 가입하지 않아도 돼요.`), actions);
  }

  function showForm() {
    const heading = element('h1', '', '회원가입');
    heading.id = 'signup-heading';
    // 목 모드의 가입은 계정을 만들지 않으므로 판결 기록이 이어진다고 안내하지 않는다
    const lead = API_MODE === 'mock'
      ? '가입하지 않아도 모든 판결 체험을 할 수 있어요.'
      : '가입하면 이 브라우저에서 한 판결 기록이 계정으로 이어져요. 가입하지 않아도 모든 판결 체험을 할 수 있어요.';
    const parts = [heading, element('p', 'login-lead', lead)];
    if (API_MODE === 'mock') {
      parts.push(element('p', 'login-mock-note', '개발용 목업 가입이에요. 계정을 만들지 않고 닉네임으로 로그인한 상태만 되고, 입력값은 어디에도 보내거나 남기지 않아요(admin으로 시작하는 아이디는 이미 있는 아이디로 보여요).'));
    }

    const error = element('p', 'login-error');
    error.id = 'signup-error';
    error.setAttribute('role', 'alert');
    error.hidden = true;

    const kakao = kakaoButton('카카오로 시작하기', startWithKakao);
    const divider = element('p', 'login-divider', '또는 아이디로 가입');

    const form = element('form', 'login-form signup-form');
    form.noValidate = true; // 칸마다 아래 문구로 직접 안내한다(브라우저 말풍선 대신)
    const fields = {
      id: field(form, 'signup-id', '아이디', { type: 'text', autocomplete: 'username', hint: '영문 소문자 · 숫자 · 밑줄(_) 4~20자' }),
      password: field(form, 'signup-password', '비밀번호', { type: 'password', autocomplete: 'new-password', hint: '영문과 숫자를 섞어 8~64자' }),
      passwordConfirm: field(form, 'signup-password-confirm', '비밀번호 확인', { type: 'password', autocomplete: 'new-password' }),
      nickname: field(form, 'signup-nickname', '닉네임', { type: 'text', autocomplete: 'nickname', hint: `2자 이상, 한글은 ${NICKNAME_MAX_HANGUL}자 · 영문 · 숫자는 ${NICKNAME_MAX}자까지 · 커뮤니티와 랭킹에 보여요`, maxLength: NICKNAME_MAX }),
      agreed: agreement(form),
    };
    lockNicknameLength(fields.nickname.input);
    const submit = element('button', 'primary-button login-submit', '가입하기');
    submit.type = 'submit';
    form.append(error, submit);

    // 칸을 떠날 때 값이 있으면 그 칸만 검사한다(빈칸 안내는 가입하기를 눌렀을 때만). 비밀번호를 고치면 확인 칸도 다시 본다
    ['id', 'password', 'passwordConfirm', 'nickname'].forEach((name) => {
      fields[name].input.addEventListener('change', () => {
        const errors = validateSignup(readValues());
        const check = name === 'password' ? ['password', 'passwordConfirm'] : [name];
        check.forEach((key) => { if (fields[key].input.value) fieldError(key, errors[key] ?? null); });
      });
    });

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      submitSignup();
    });

    // 로그인으로 돌아갈 때도 기록 항목을 바꾼다(로그인 ↔ 회원가입을 오가도 뒤로 가기는 그 전에 보던 화면)
    const toLogin = element('p', 'login-signup', '이미 계정이 있나요?');
    toLogin.append(button('로그인', () => navigate('login', {}, { replace: true }), 'login-link'));
    const browse = element('p', 'login-browse');
    browse.append(button('가입 없이 사건 보러 가기', () => navigate('list'), 'login-link'));

    parts.push(kakao, divider, form, toLogin, browse);
    let pending = false;
    peek(null);
    card.replaceChildren(...parts);
    const helpers = { form, showError, fieldError, fill, showSignedUpLoginAgain, busy: [kakao, submit] };
    return helpers;

    // 가입은 됐는데 첫 토큰을 받지 못했을 때(TOKEN_NOT_ISSUED): 안내 옆에 로그인 화면으로 가는 버튼을 둔다 (PR #308 리뷰).
    // 가입하기를 다시 누르면 409(이미 쓰는 아이디)가 나오므로 한 단계를 줄인다. 버튼은 안내 문구 안에 있어 다음 안내가 나오면 함께 사라진다
    function showSignedUpLoginAgain() {
      showError(SIGNED_UP_LOGIN_AGAIN);
      error.append(' ', button('로그인 화면으로', () => navigate('login', {}, { replace: true }), 'login-link'));
      return helpers;
    }

    function readValues() {
      return {
        id: fields.id.input.value,
        password: fields.password.input.value,
        passwordConfirm: fields.passwordConfirm.input.value,
        nickname: fields.nickname.input.value,
        agreed: fields.agreed.input.checked,
      };
    }

    function fill(values) {
      Object.entries(values).forEach(([key, value]) => { fields[key].input.value = value; });
      return helpers;
    }

    // message가 없으면 그 칸 안내를 지운다
    function fieldError(name, message) {
      const { input, note } = fields[name];
      note.textContent = message ?? '';
      note.hidden = !message;
      if (message) input.setAttribute('aria-invalid', 'true');
      else input.removeAttribute('aria-invalid');
      return helpers;
    }

    // 칸에 매이지 않는 안내(연결 실패 · 요청 제한 · 가입됨): 카카오는 버튼 바로 아래, 가입 폼은 가입하기 버튼 위
    function showError(message, near = form) {
      if (near === kakao) kakao.after(error); else form.insertBefore(error, submit);
      error.textContent = message;
      error.hidden = false;
      return helpers;
    }

    async function submitSignup() {
      if (pending) return;
      error.hidden = true;
      const values = readValues();
      const errors = validateSignup(values);
      Object.keys(fields).forEach((name) => fieldError(name, errors[name] ?? null));
      const first = Object.keys(errors)[0];
      if (first) {
        fields[first].input.focus();
        return;
      }
      pending = true;
      setBusy([kakao, submit], true, submit, '가입하는 중…');
      try {
        const { nickname } = await api.signup({ id: values.id.trim(), password: values.password, nickname: values.nickname.trim(), agreed: values.agreed });
        saveUser({ nickname }); // 화면을 떠났어도 로그인 표시는 남긴다 — 서버는 이미 가입 · 로그인됐다
        if (!container.contains(root)) return; // 기다리는 사이 다른 화면으로 갔으면 이동하지 않는다
        finish();
      } catch (failure) {
        if (!container.contains(root)) return;
        // 서버 details는 [{ field, reason }]. 아는 칸 이름일 때만 그 칸에 붙인다(hasOwn: toString 같은 기본 속성 이름에 걸리지 않게)
        const serverField = failure?.code === 'VALIDATION_ERROR' && Array.isArray(failure.details)
          ? failure.details.find((detail) => Object.hasOwn(SERVER_FIELD_MESSAGE, detail?.field ?? ''))?.field
          : null;
        if (failure?.code === TOKEN_NOT_ISSUED) {
          showSignedUpLoginAgain();
        } else if (failure?.code === 'DUPLICATE_ID') {
          // 서버가 가입(커밋) 뒤 토큰 발급에서 실패하면 500이 나가고, 다시 누르면 이 409를 본다(BE-113 추후 재검토 · PR #306 리뷰).
          // 그 사람은 이미 가입된 것이라 로그인으로 안내한다
          fieldError('id', '이미 쓰고 있는 아이디예요. 다른 아이디를 골라 주세요. 방금 가입했다면 로그인해 주세요.');
          fields.id.input.focus();
        } else if (failure?.code === 'DUPLICATE_NICKNAME') {
          fieldError('nickname', DUPLICATE_NICKNAME);
          fields.nickname.input.focus();
        } else if (serverField) {
          fieldError(serverField, SERVER_FIELD_MESSAGE[serverField]);
          fields[serverField].input.focus();
        } else if (failure?.code === 'TOO_MANY_REQUESTS') {
          showError(TOO_MANY);
        } else {
          showError(failure?.code === 'NETWORK_ERROR' ? NETWORK : '가입하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
        }
        setBusy([kakao, submit], false);
        pending = false;
      }
    }

    // 카카오로 시작하기는 카카오 로그인과 같다: 처음이면 서버가 회원으로 만든다(FE-74)
    async function startWithKakao() {
      if (pending) return;
      error.hidden = true;
      pending = true;
      setBusy([kakao, submit], true, kakao);
      try {
        const { nickname } = await api.login({ method: 'kakao' });
        saveUser({ nickname }); // 목 모드만 여기 온다(실제 카카오는 브라우저 이동). 가입 폼과 같은 순서로 둔다
        if (!container.contains(root)) return;
        finish();
      } catch (failure) {
        if (!container.contains(root)) return;
        if (failure?.code !== 'LOGIN_CANCELLED') showError(failure?.code === 'NETWORK_ERROR' ? NETWORK : '카카오로 시작하지 못했어요. 잠시 뒤 다시 시도해 주세요.', kakao);
        setBusy([kakao, submit], false);
        pending = false;
      }
    }
  }

  // 가입하기 전에 보던 화면으로 돌아간다(로그인 화면에서 왔어도 replace로 열어 그 전 화면). 바로 /signup으로 왔으면 메인으로
  function finish() {
    if (canGoBackInApp()) history.back(); else navigate('landing', {}, { replace: true });
  }
}

// 라벨 + 입력칸 + (도움말) + 칸 안내 한 줄. 도움말과 칸 안내를 입력칸 설명으로 잇는다
function field(form, id, label, { type, autocomplete, hint, maxLength }) {
  const row = element('div', 'login-field');
  const caption = element('label', '', label);
  caption.htmlFor = id;
  const input = element('input');
  input.id = id;
  input.name = id;
  Object.assign(input, { type, autocomplete });
  if (maxLength) input.maxLength = maxLength;
  input.spellcheck = false;
  input.autocapitalize = 'off'; // 모바일에서 아이디 첫 글자가 대문자가 되지 않게
  row.append(caption, input);
  const described = [];
  if (hint) {
    const help = element('p', 'signup-hint', hint);
    help.id = `${id}-hint`;
    row.append(help);
    described.push(help.id);
  }
  const note = fieldNote(id);
  row.append(note);
  described.push(note.id);
  input.setAttribute('aria-describedby', described.join(' '));
  input.addEventListener('input', () => {
    input.removeAttribute('aria-invalid');
    note.hidden = true;
  });
  form.append(row);
  return { input, note };
}

// 필수 동의 한 줄: 체크박스 + 문구, 아래에 두 문서 링크(입력한 내용이 사라지지 않게 새 탭으로 연다)
function agreement(form) {
  const row = element('div', 'signup-agree');
  const check = element('div', 'signup-agree-check');
  const input = element('input');
  input.type = 'checkbox';
  input.id = 'signup-agree';
  input.name = 'signup-agree';
  const caption = element('label', '', '(필수) 이용약관과 개인정보처리방침에 동의해요');
  caption.htmlFor = input.id;
  check.append(input, caption);
  const links = element('p', 'signup-agree-links');
  [['/terms', '이용약관 보기'], ['/privacy', '개인정보처리방침 보기']].forEach(([href, text]) => {
    const link = element('a', 'signup-doc-link', text);
    link.href = href;
    link.target = '_blank';
    link.rel = 'noopener';
    links.append(link);
  });
  const note = fieldNote(input.id);
  input.setAttribute('aria-describedby', note.id);
  input.addEventListener('change', () => {
    input.removeAttribute('aria-invalid');
    note.hidden = true;
  });
  row.append(check, links, note);
  form.append(row);
  return { input, note };
}

function fieldNote(id) {
  const note = element('p', 'login-error signup-field-error');
  note.id = `${id}-error`;
  note.hidden = true;
  return note;
}
