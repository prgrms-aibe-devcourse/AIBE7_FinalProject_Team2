// 내 정보 수정(FE-77): 마이페이지 "내 정보 수정" 화면의 내부 틀. 서버 닉네임 · 탈퇴 API가 아직 없어 저장은 이 브라우저의 로그인 표시값(session.js)에만 반영한다.
// 구성: 프로필(닉네임) · 계정 정보 · 내 기록 관리 · 계정 관리(로그아웃 · 회원 탈퇴). 알림 등 앱 설정은 "환경 설정"에서 다룬다
import { button, element, mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { clearUser, currentUser, saveUser } from '../../utils/session.js';
import { NICKNAME_HINT, NICKNAME_MAX, nicknameError } from '../../utils/nickname.js';

const IS_MOCK = API_MODE === 'mock';

function section(title, ...children) {
  const box = element('section', 'edit-section');
  box.append(element('h2', 'edit-title', title), ...children);
  return box;
}

// 읽기 전용 정보 한 줄
function fact(label, value) {
  const row = element('div', 'edit-fact');
  row.append(element('dt', '', label), element('dd', '', value));
  return row;
}

export function renderProfileEdit({ api, navigate, go }) {
  const user = currentUser();
  const box = element('div', 'my-detail edit-page');
  const form = element('form', 'edit-form');
  form.noValidate = true; // 브라우저 기본 말풍선 대신 아래 안내 문구를 쓴다
  const status = element('p', 'edit-status');
  status.setAttribute('role', 'status'); // 저장 결과를 스크린리더가 읽는다

  // 프로필: 닉네임. 로그인하지 않았으면 바꿀 닉네임이 없다
  const input = element('input', 'edit-input');
  input.id = 'edit-nickname';
  input.name = 'nickname';
  input.type = 'text';
  input.value = user?.nickname ?? '';
  input.maxLength = NICKNAME_MAX;
  input.autocomplete = 'nickname';
  input.spellcheck = false;
  input.disabled = !user;
  input.setAttribute('aria-describedby', 'edit-nickname-hint edit-nickname-error');
  const label = element('label', 'edit-label', '닉네임');
  label.htmlFor = input.id;
  const hint = element('p', 'edit-hint', user ? NICKNAME_HINT : '로그인하면 닉네임을 바꿀 수 있어요.');
  hint.id = 'edit-nickname-hint';
  const error = element('p', 'edit-error');
  error.id = 'edit-nickname-error';
  const portrait = element('div', 'edit-field');
  portrait.append(element('span', 'edit-label', '대표 이미지 · 카드 꾸미기'), button('프로필카드에서 바꾸기', () => go('profile'), 'secondary-button'));
  const nickname = element('div', 'edit-field');
  nickname.append(label, input, hint, error);

  // 계정 정보(읽기 전용): 서버가 로그인 방식 · 가입일을 주기 전까지는 자리만 둔다
  const facts = element('dl', 'edit-facts');
  facts.append(
    fact('로그인 방식', user ? (IS_MOCK ? '개발용 목업 로그인' : '카카오 로그인') : '로그인 전 (이 브라우저의 익명 ID)'),
    fact('가입일', user ? '서버 연동 후 표시돼요' : '—'),
  );

  // 내 기록 관리: 기록은 익명 ID(쿠키) 기준이라 내보내기 · 가져오기는 후속
  const records = element('div', 'edit-field');
  const exportButton = button('기록 내보내기 (준비 중)', () => {}, 'secondary-button');
  exportButton.disabled = true;
  records.append(
    element('p', 'edit-hint', '체험 기록과 성향 결과는 로그인 전에는 이 브라우저(익명 ID)를 기준으로 남아요. 쿠키를 지우면 기록을 찾을 수 없어요.'),
    exportButton,
  );

  // 계정 관리: 로그아웃 · 회원 탈퇴. 탈퇴는 한 번 더 확인한다
  const logout = button('로그아웃', async () => {
    await Promise.resolve(api.logout?.()).catch(() => {}); // 서버 쪽 로그아웃이 없거나 실패해도 이 브라우저의 표시는 지운다
    clearUser();
    navigate('landing', {}, { replace: true });
  }, 'secondary-button');
  const confirm = element('div', 'edit-confirm');
  confirm.hidden = true;
  const agree = element('input');
  agree.type = 'checkbox';
  agree.id = 'edit-withdraw-agree';
  const agreeLabel = element('label', '', '탈퇴하면 계정이 사라지고 되돌릴 수 없다는 것을 이해했어요.');
  agreeLabel.htmlFor = agree.id;
  const agreeRow = element('div', 'edit-agree');
  agreeRow.append(agree, agreeLabel);
  const withdraw = button('탈퇴하기', () => { status.textContent = '회원 탈퇴는 서버 연동 후 열려요. 지금은 계정이 그대로 남아 있어요.'; }, 'secondary-button edit-danger');
  withdraw.disabled = true;
  agree.addEventListener('change', () => { withdraw.disabled = !agree.checked; });
  const open = button('회원 탈퇴', () => { confirm.hidden = false; open.hidden = true; agree.focus(); }, 'secondary-button edit-danger');
  const cancelWithdraw = button('취소', () => { confirm.hidden = true; open.hidden = false; agree.checked = false; withdraw.disabled = true; open.focus(); }, 'secondary-button');
  const confirmActions = element('div', 'edit-actions');
  confirmActions.append(withdraw, cancelWithdraw);
  confirm.append(agreeRow, confirmActions);
  const account = element('div', 'edit-field');
  account.append(user ? logout : element('p', 'edit-hint', '로그인 전에는 로그아웃 · 탈퇴할 계정이 없어요.'), ...(user ? [open, confirm] : []));

  // 저장 · 취소
  const save = element('button', 'primary-button', '저장');
  save.type = 'submit';
  const actions = element('div', 'edit-actions');
  actions.append(save, button('취소', () => go(null), 'secondary-button'));

  form.append(
    section('프로필', nickname, portrait),
    section('계정 정보', facts),
    section('내 기록 관리', records),
    section('계정 관리', account),
    status,
    actions,
  );
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    status.textContent = '';
    const message = user ? nicknameError(input.value) : '';
    error.textContent = message;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    if (message) { input.focus(); return; } // 첫 오류 칸으로 초점
    if (!user) { status.textContent = '바꿀 내용이 없어요.'; return; }
    saveUser({ nickname: input.value, role: user.role }); // 서버 닉네임 API 전: 이 브라우저의 로그인 표시값만 바꾼다
    status.textContent = '저장했어요.';
  });

  const head = element('p', 'edit-intro', '프로필과 계정을 관리해요. 알림 같은 앱 설정은 환경 설정에서 바꿀 수 있어요.');
  if (IS_MOCK) head.append(mockTag());
  box.append(head, form);
  return box;
}
