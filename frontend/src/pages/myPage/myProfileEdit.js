// 내 정보 수정(FE-77): 마이페이지 "내 정보 수정" 화면의 내부 틀. 서버 닉네임 · 탈퇴 API가 아직 없어 저장은 이 브라우저의 로그인 표시값(session.js)에만 반영한다.
// 구성: 프로필(프로필카드 · 닉네임 · 한 줄 소개 · 성향카드 · 재심) · 계정 정보 · 계정 관리(현재 아이디 · 비밀번호 변경 · 로그아웃 · 회원 탈퇴) · 약관. 알림 등 앱 설정은 "환경 설정"에서 다룬다
// 한 줄 소개는 서버(P-8 PATCH /profile-settings)에 저장한다(profileSettings.js, FE-81). 프로필카드 · 성향카드는 커뮤니티에서 항상 공개다(공개 범위 설정을 두지 않는다, 의도된 서비스 특징)
import { button, element, mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { clearUser, currentUser, saveUser } from '../../utils/session.js';
import { NICKNAME_HINT, NICKNAME_MAX, nicknameError } from '../../utils/nickname.js';
import { BIO_MAX, getProfileSettings, updateProfileSettings } from '../../utils/profileSettings.js';

const BIO_PLACEHOLDER = '사건 앞에서는 한 번 더 생각하는 판사입니다.';

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

// 되돌릴 수 없는 동작: 버튼을 누르면 동의 체크가 있는 확인 상자가 열리고, 체크해야 실행 버튼이 켜진다
function dangerAction({ id, openLabel, agreeText, runLabel, onRun }) {
  const confirm = element('div', 'edit-confirm');
  confirm.hidden = true;
  const agree = element('input');
  agree.type = 'checkbox';
  agree.id = id;
  const agreeLabel = element('label', '', agreeText);
  agreeLabel.htmlFor = id;
  const agreeRow = element('div', 'edit-agree');
  agreeRow.append(agree, agreeLabel);
  const run = button(runLabel, onRun, 'secondary-button edit-danger');
  run.disabled = true;
  agree.addEventListener('change', () => { run.disabled = !agree.checked; });
  const open = button(openLabel, () => { confirm.hidden = false; open.hidden = true; agree.focus(); }, 'secondary-button edit-danger');
  const cancel = button('취소', () => { confirm.hidden = true; open.hidden = false; agree.checked = false; run.disabled = true; open.focus(); }, 'secondary-button');
  const actions = element('div', 'edit-actions');
  actions.append(run, cancel);
  confirm.append(agreeRow, actions);
  return [open, confirm];
}

export function renderProfileEdit({ api, navigate, go, data = {}, card, fan }) {
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
  const nickname = element('div', 'edit-field');
  nickname.append(label, input, hint, error);

  // 한 줄 소개: 프로필카드와 커뮤니티에 함께 보일 문구
  const bio = element('input', 'edit-input');
  bio.id = 'edit-bio';
  bio.name = 'bio';
  bio.type = 'text';
  bio.value = getProfileSettings().bio;
  bio.maxLength = BIO_MAX;
  bio.placeholder = BIO_PLACEHOLDER; // 비워 두면 이 문구가 흐리게 보인다(저장되는 값은 아니다)
  bio.spellcheck = false;
  bio.setAttribute('aria-describedby', 'edit-bio-hint');
  const bioLabel = element('label', 'edit-label', '한 줄 소개');
  bioLabel.htmlFor = bio.id;
  const bioCount = element('span', 'edit-count');
  const bioHint = element('p', 'edit-hint', `프로필카드에 함께 보여요. 최대 ${BIO_MAX}자.`);
  bioHint.id = 'edit-bio-hint';
  bioHint.append(' ', bioCount);
  const bioField = element('div', 'edit-field');
  bioField.append(bioLabel, bio, bioHint);

  // 프로필카드 미리보기: 마이페이지 첫 화면과 같은 카드(누르면 프로필카드 화면). 닉네임을 입력하는 대로 카드 이름이 바뀐다. 성향카드는 마이페이지 첫 화면에만 둔다
  const profileCard = card();
  const syncPreview = () => {
    const name = input.value.trim() || user?.nickname;
    const title = profileCard.querySelector('.my-card-name');
    if (name && title) {
      title.textContent = name;
      profileCard.setAttribute('aria-label', profileCard.getAttribute('aria-label').replace(/^(프로필카드 열기: )[^,]*/, `$1${name}`));
    }
    bioCount.textContent = `${bio.value.length}/${BIO_MAX}`;
  };
  input.addEventListener('input', syncPreview);
  bio.addEventListener('input', syncPreview);
  syncPreview();

  const portrait = element('div', 'edit-field');
  portrait.append(element('span', 'edit-label', '대표 이미지 · 카드 꾸미기'), button('프로필카드에서 바꾸기', () => go('profile'), 'secondary-button'));

  // 계정 정보(읽기 전용): 서버가 로그인 방식 · 가입일 · 최근 로그인을 주기 전까지는 자리만 둔다
  const facts = element('dl', 'edit-facts');
  facts.append(
    fact('로그인 방식', user ? (IS_MOCK ? '개발용 목업 로그인' : '카카오 로그인') : '로그인 전 (이 브라우저의 익명 ID)'),
    fact('가입일', user ? '서버 연동 후 표시돼요' : '—'),
    fact('최근 로그인', user ? '서버 연동 후 표시돼요' : '—'),
  );

  // 계정 관리: 현재 아이디 · 비밀번호 변경 · 로그아웃 · 회원 탈퇴. 아이디 · 비밀번호 API가 없어 앞의 둘은 자리만 두고, 탈퇴는 한 번 더 확인한다
  const logout = button('로그아웃', async () => {
    await Promise.resolve(api.logout?.()).catch(() => {}); // 서버 쪽 로그아웃이 없거나 실패해도 이 브라우저의 표시는 지운다
    clearUser();
    navigate('landing', {}, { replace: true });
  }, 'secondary-button');
  const [withdrawOpen, withdrawConfirm] = dangerAction({
    id: 'edit-withdraw-agree',
    openLabel: '회원 탈퇴',
    agreeText: '탈퇴하면 계정이 사라지고 되돌릴 수 없다는 것을 이해했어요.',
    runLabel: '탈퇴하기',
    onRun: () => { status.textContent = '회원 탈퇴는 서버 연동 후 열려요. 지금은 계정이 그대로 남아 있어요.'; },
  });
  const accountId = element('dl', 'edit-facts');
  accountId.append(fact('현재 아이디', user ? '서버 연동 후 표시돼요' : '로그인 전 (이 브라우저의 익명 ID)'));
  const password = button('비밀번호 변경 (준비 중)', () => {}, 'secondary-button');
  password.disabled = true; // 비밀번호로 로그인하는 계정(관리자)만 해당. 일반 사용자는 카카오 로그인이라 비밀번호가 없다
  const passwordHint = element('p', 'edit-hint', user ? '비밀번호로 로그인하는 계정만 바꿀 수 있어요. 카카오로 로그인했다면 비밀번호는 카카오에서 바꿔요.' : '로그인하면 계정 정보를 관리할 수 있어요.');
  const account = element('div', 'edit-field');
  account.append(accountId, ...(user ? [password] : []), passwordHint, ...(user ? [logout, withdrawOpen, withdrawConfirm] : []));

  // 약관 · 방침 입구
  const policies = element('nav', 'edit-actions');
  policies.setAttribute('aria-label', '약관 및 방침');
  policies.append(button('이용약관', () => navigate('terms'), 'secondary-button'), button('개인정보처리방침', () => navigate('privacy'), 'secondary-button'));
  const policyField = element('div', 'edit-field');
  policyField.append(element('p', 'edit-hint', '가입과 이용에 적용되는 약관과 개인정보 처리 방침이에요.'), policies);

  // 저장 · 취소
  const save = element('button', 'primary-button', '저장');
  save.type = 'submit';
  const actions = element('div', 'edit-actions');
  actions.append(save, button('취소', () => go(null), 'secondary-button'));

  const profileFields = element('div', 'edit-profile');
  profileFields.append(nickname, bioField, portrait);
  const profileBody = element('div', 'edit-profile-row'); // 왼쪽 프로필카드 + 겹친 성향카드, 오른쪽 닉네임 · 한 줄 소개
  profileBody.append(profileCard, profileFields);

  form.append(
    section('프로필', profileBody, fan(), element('p', 'edit-hint', '프로필카드와 성향카드는 커뮤니티에서 항상 다른 사람에게 보여요.')),
    section('계정 정보', facts),
    section('계정 관리', account),
    section('약관 · 방침', policyField),
    status,
    actions,
  );
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    status.textContent = '';
    const message = user ? nicknameError(input.value) : '';
    error.textContent = message;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    if (message) { input.focus(); return; } // 첫 오류 칸으로 초점
    if (user) saveUser({ nickname: input.value, role: user.role }); // 서버 닉네임 API 전: 이 브라우저의 로그인 표시값만 바꾼다
    const bioNext = bio.value.trim();
    save.disabled = true; // 저장하는 동안 다시 누르지 못하게
    const bioSaved = bioNext === getProfileSettings().bio || await updateProfileSettings({ bio: bioNext });
    save.disabled = false;
    if (!bioSaved) bio.value = getProfileSettings().bio; // 거절되면 서버에 있는 값으로 돌린다
    status.textContent = bioSaved ? '저장했어요.' : '한 줄 소개를 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  });

  const head = element('p', 'edit-intro', '프로필과 계정을 관리해요. 알림 같은 앱 설정은 환경 설정에서 바꿀 수 있어요.');
  if (IS_MOCK) head.append(mockTag());
  box.append(head, form);
  return box;
}
