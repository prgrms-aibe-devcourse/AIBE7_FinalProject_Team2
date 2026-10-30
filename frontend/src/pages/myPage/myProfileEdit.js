// 내 정보 수정(FE-77): 마이페이지 "내 정보 수정" 화면의 내부 틀. 닉네임은 서버(M-7 PUT /members/me/nickname, BE-139)에 저장하고 성공하면 이 브라우저의 로그인 표시값(session.js)도 바꾼다.
// 계정 정보(로그인 방식 · 아이디 · 가입일 · 최근 로그인)와 정하지 않은 닉네임은 서버 회원 정보(M-8 GET /members/me)로 채운다. 탈퇴 API는 아직 없다(FE-137).
// 구성: 프로필(프로필카드 · 닉네임 · 한 줄 소개 · 성향카드 · 재심) · 계정 관리(로그인 방식 · 현재 아이디 · 가입일 · 최근 로그인 · 비밀번호 변경 · 로그아웃 · 회원 탈퇴) · 약관. 알림 등 앱 설정은 "환경 설정"에서 다룬다
// 한 줄 소개는 서버(P-8 PATCH /profile-settings)에 저장한다(profileSettings.js, FE-81). 프로필카드 · 성향카드는 커뮤니티에서 항상 공개다(공개 범위 설정을 두지 않는다, 의도된 서비스 특징)
import { button, element, mockTag } from '../../components/siteLayout.js';
import { cardSignature } from '../../components/personCards.js';
import { signatureSvg, signerSeed } from '../../components/signatureInk.js';
import { API_MODE } from '../../api/config.js';
import { clearUser, currentUser, saveUser } from '../../utils/session.js';
import { NICKNAME_CLAMPED, NICKNAME_HINT, NICKNAME_MAX, isUnnamed, lockNicknameLength, nicknameError } from '../../utils/nickname.js';
import { BIO_MAX, getProfileSettings, updateProfileSettings } from '../../utils/profileSettings.js';
import { isNicknameChanged, planMeSync, saveNickname, saveStatus } from './nicknameSave.js';

const BIO_PLACEHOLDER = '사건 앞에서는 한 번 더 생각하는 판사입니다.';

const IS_MOCK = API_MODE === 'mock';

// 서버 시각(+09:00)을 화면 문구로. 값이 없으면 빈 줄 대신 "기록 없음"
const formatWhen = (iso) => {
  const date = iso ? new Date(iso) : null;
  return date && !Number.isNaN(date.getTime())
    ? date.toLocaleString('ko-KR', { dateStyle: 'long', timeStyle: 'short', timeZone: 'Asia/Seoul' })
    : '기록 없음';
};

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
  const unnamed = isUnnamed(user?.nickname); // 아직 닉네임을 정하지 않았으면(카카오 회원) 칸은 비워 둔다 — 표시 이름 '방청객 {회원 ID}'는 닉네임 형식이 아니다
  let savedNickname = unnamed ? '' : user?.nickname ?? ''; // 서버에 있는 닉네임. 바뀌지 않았으면 다시 저장하지 않는다
  let nicknameTouched = false; // 저장을 한 번이라도 시작했으면 늦게 온 회원 정보(M-8)로 닉네임 · 로그인 표시를 덮지 않는다
  input.value = savedNickname;
  if (unnamed) input.placeholder = '아직 정하지 않았어요';
  input.maxLength = NICKNAME_MAX;
  // 한글이 섞여 뒤를 자르면 칸 아래에 알린다(BE-134). error는 아래에서 만들어 부를 때 있다
  lockNicknameLength(input, { onClamp: () => { error.textContent = NICKNAME_CLAMPED; } });
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
  bio.maxLength = BIO_MAX * 2; // 입력칸은 UTF-16 단위로 세어 이모지가 일찍 막히므로 넉넉히 두고, 40자(코드 포인트)는 저장 전에 검사한다
  bio.placeholder = BIO_PLACEHOLDER; // 비워 두면 이 문구가 흐리게 보인다(저장되는 값은 아니다)
  bio.spellcheck = false;
  const bioLabel = element('label', 'edit-label', '한 줄 소개');
  bioLabel.htmlFor = bio.id;
  const bioCount = element('span', 'edit-count');
  const bioHint = element('p', 'edit-hint', `프로필카드와 커뮤니티에 함께 보여요. ${BIO_MAX}자까지 쓸 수 있어요.`);
  bioHint.id = 'edit-bio-hint';
  bioHint.append(' ', bioCount);
  const bioError = element('p', 'edit-error');
  bioError.id = 'edit-bio-error';
  bio.setAttribute('aria-describedby', 'edit-bio-hint edit-bio-error');
  const bioField = element('div', 'edit-field');
  bioField.append(bioLabel, bio, bioHint, bioError);

  // 프로필카드 미리보기: 마이페이지 첫 화면과 같은 카드(누르면 프로필카드 화면). 닉네임을 입력하는 대로 카드 이름이 바뀐다. 성향카드는 마이페이지 첫 화면에만 둔다
  const profileCard = card();
  let shownName = profileCard.querySelector('.my-card-name')?.textContent ?? '';
  let displayName = user?.nickname; // 칸이 비었을 때 미리보기가 쓰는 표시 이름. 서버 회원 정보(M-8)가 다른 값을 주면 바뀐다
  const syncPreview = () => {
    const name = input.value.trim() || displayName;
    const title = profileCard.querySelector('.my-card-name');
    if (name && title) {
      title.textContent = name;
      // 낭독 이름도 카드에 보이는 이름(서명의 "○○의 서명" 포함) 전부를 새 닉네임으로 바꾼다
      if (shownName) profileCard.setAttribute('aria-label', profileCard.getAttribute('aria-label').split(shownName).join(name));
      shownName = name;
      profileCard.querySelector('.sig-ink')?.replaceWith(cardSignature(name)); // 카드 아래가 서명이면 닉네임에 맞춰 다시 그린다
    }
    bioCount.textContent = `${[...bio.value.trim()].length}/${BIO_MAX}`;
  };
  input.addEventListener('input', syncPreview);
  bio.addEventListener('input', syncPreview);
  syncPreview();

  // 서명 미리보기: 닉네임을 입력하는 대로 서명이 새로 그려진다(서명은 닉네임이 씨앗이다)
  const signature = element('div', 'edit-field');
  const signatureBox = element('div', 'edit-signature');
  const paintSignature = () => {
    const seed = input.value.trim() || signerSeed({ nickname: displayName });
    signatureBox.replaceChildren(signatureSvg(seed, { label: `${seed}의 서명` }));
  };
  input.addEventListener('input', paintSignature);
  paintSignature();
  signature.append(element('span', 'edit-label', '서명 미리보기'), signatureBox, element('p', 'edit-hint', '닉네임을 바꾸면 서명도 새로 그려져요. 카드 아래 줄을 서명으로 고르면 이 서명이 보여요.'));

  // 프로필카드 화면(대표 이미지 · 꾸미기)은 카드 아래 버튼에서 연다
  const cardColumn = element('div', 'edit-card-col');
  cardColumn.append(profileCard, button('프로필카드에서 보기', () => go('profile'), 'secondary-button'));

  // 계정 관리 위쪽, 읽기 전용 정보: 실제 모드는 서버 회원 정보(M-8)가 오면 채운다. 목 모드에는 이 API가 없어 자리만 둔다
  const facts = element('dl', 'edit-facts');
  const showMe = Boolean(user) && !IS_MOCK && Boolean(api.getMe);
  // 서버 값이 없는 동안(FE-103)은 같은 문구를 세 번 쓰지 않고 한 줄로 묶는다
  // 서버 회원 정보를 기다리는 동안에는 로그인 방식도 단정하지 않는다(아이디 회원이 잠깐 "카카오 로그인"으로 보이지 않게)
  const loginMethod = !user ? '로그인 전 (이 브라우저의 익명 ID)' : IS_MOCK ? '개발용 목업 로그인' : showMe ? '불러오는 중이에요' : '카카오 로그인';
  facts.append(
    fact('로그인 방식', loginMethod),
    user ? fact('아이디 · 가입일 · 최근 로그인', showMe ? '불러오는 중이에요' : '서버 연동 후 표시돼요') : fact('현재 아이디', '로그인 전 (이 브라우저의 익명 ID)'),
  );

  // 계정 관리: 비밀번호 변경 · 로그아웃 · 회원 탈퇴. 비밀번호 API가 없어 자리만 두고, 탈퇴는 한 번 더 확인한다
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
  const password = button('비밀번호 변경', () => {}, 'secondary-button');
  password.disabled = true; // 준비 중. 비밀번호로 로그인하는 계정(관리자)만 해당하고, 일반 사용자는 카카오 로그인이라 비밀번호가 없다
  const passwordField = element('div', 'edit-field');
  passwordField.append(element('span', 'edit-label', '비밀번호'), password, element('p', 'edit-hint', '준비 중이에요. 비밀번호로 로그인하는 계정만 바꿀 수 있고, 카카오로 로그인했다면 카카오에서 바꿔요.'));
  const sessionActions = element('div', 'edit-actions');
  sessionActions.append(logout, withdrawOpen);
  const sessionField = element('div', 'edit-field');
  sessionField.append(element('span', 'edit-label', '로그아웃 · 탈퇴'), sessionActions, withdrawConfirm);
  const account = user ? [passwordField, sessionField] : [element('p', 'edit-hint', '로그인하면 계정을 관리할 수 있어요.')];

  // 약관 · 방침 입구
  const policies = element('nav', 'edit-actions');
  policies.setAttribute('aria-label', '약관 및 방침');
  policies.append(button('이용약관', () => navigate('terms'), 'secondary-button'), button('개인정보처리방침', () => navigate('privacy'), 'secondary-button'));
  const policyField = element('div', 'edit-field');
  policyField.append(policies, element('p', 'edit-hint', '가입과 이용에 적용되는 약관과 개인정보 처리 방침이에요.'));

  // 저장 · 취소
  const save = element('button', 'primary-button', '저장');
  save.type = 'submit';
  const actions = element('div', 'edit-actions edit-submit');
  actions.append(button('취소', () => go(null), 'secondary-button'), save);

  const profileFields = element('div', 'edit-profile');
  profileFields.append(nickname, bioField, signature);
  const profileBody = element('div', 'edit-profile-row'); // 왼쪽 프로필카드 + 겹친 성향카드, 오른쪽 닉네임 · 한 줄 소개
  profileBody.append(cardColumn, profileFields);

  form.append(
    section('프로필', profileBody, fan()),
    section('계정 관리', facts, ...account),
    section('약관 · 방침', policyField),
    status,
    actions,
  );
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    status.textContent = '';
    const nicknameChanged = Boolean(user) && isNicknameChanged(input.value, savedNickname); // 아직 정하지 않은 채 비워 둔 칸은 바꾸지 않은 것이다
    const message = nicknameChanged ? nicknameError(input.value) : '';
    error.textContent = message;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    const bioNext = bio.value.trim();
    const bioMessage = [...bioNext].length > BIO_MAX ? `한 줄 소개는 ${BIO_MAX}자까지 쓸 수 있어요. 조금 줄여 주세요.` : '';
    bioError.textContent = bioMessage;
    bio.setAttribute('aria-invalid', bioMessage ? 'true' : 'false');
    if (message) { input.focus(); return; } // 첫 오류 칸으로 초점
    if (bioMessage) { bio.focus(); return; }
    save.disabled = true; // 저장하는 동안 다시 누르지 못하게
    // 닉네임(M-7): 서버가 저장하고 중복이면 409. 성공하면 이 브라우저의 로그인 표시값도 바꾼다. 실패해도 한 줄 소개는 따로 저장한다
    if (nicknameChanged) nicknameTouched = true; // 저장을 시작했다 — 이후 늦게 온 회원 정보는 계정 정보 줄만 채운다
    const submitted = input.value; // 요청을 보낸 닉네임. 기다리는 동안 새로 입력했을 수 있다
    const nicknameResult = nicknameChanged ? await saveNickname({ api, wanted: submitted, saved: savedNickname }) : { changed: false };
    let nicknameStatusText = '';
    if (nicknameResult.changed && nicknameResult.ok) {
      savedNickname = nicknameResult.nickname; // 서버에 저장된 값은 언제나 갱신하고, 칸은 그 사이 새로 입력하지 않았을 때만 맞춘다(새 입력을 지우지 않는다)
      displayName = savedNickname;
      if (input.value === submitted) input.value = savedNickname;
      if (currentUser()) saveUser({ nickname: savedNickname, role: user.role }); // 기다리는 사이 로그아웃했으면 로그인 표시를 되살리지 않는다
      syncPreview();
      paintSignature();
    } else if (nicknameResult.changed) {
      error.textContent = nicknameResult.fieldMessage;
      input.setAttribute('aria-invalid', nicknameResult.fieldMessage ? 'true' : 'false');
      nicknameStatusText = nicknameResult.statusText;
      if (nicknameResult.fieldMessage) input.focus();
    }
    const bioSaved = bioNext === getProfileSettings().bio || await updateProfileSettings({ bio: bioNext });
    save.disabled = false;
    if (!bioSaved) bio.value = getProfileSettings().bio; // 거절되면 서버에 있는 값으로 돌린다
    status.textContent = saveStatus({ nicknameStatusText, bioSaved });
  });

  // 서버 회원 정보(M-8)가 오면 계정 정보 줄과 닉네임 칸을 서버 값으로 채운다. 못 받으면 자리 문구를 그대로 둔다
  if (showMe) {
    api.getMe().then((me) => {
      if (!box.isConnected || !me) return;
      facts.replaceChildren(
        fact('로그인 방식', me.provider === 'KAKAO' ? '카카오 로그인' : '아이디 로그인'),
        fact('아이디', me.loginId ?? '카카오 로그인은 아이디가 없어요'),
        fact('가입일', formatWhen(me.createdAt)),
        fact('최근 로그인', formatWhen(me.lastLoginAt)),
      );
      // 저장을 시작했다면 방금 저장한 값이 더 새롭다. 아니면 다른 곳에서 바꾼 닉네임을 아직 고치지 않은 칸에만 맞추고, 로그인 표시는 지금 값과 비교한다
      const plan = planMeSync({ me, inputValue: input.value, savedNickname, touched: nicknameTouched, currentNickname: currentUser()?.nickname });
      if (plan.savedNickname !== undefined) {
        savedNickname = plan.savedNickname;
        input.value = plan.inputValue;
      }
      if (plan.sessionNickname) {
        displayName = plan.sessionNickname;
        saveUser({ nickname: plan.sessionNickname, role: user.role });
      }
      // 값을 코드로 바꾸면 input 이벤트가 나지 않으므로 카드 이름 · 서명 미리보기를 직접 다시 그린다
      syncPreview();
      paintSignature();
    }).catch(() => {});
  }

  const head = element('p', 'edit-intro', '프로필과 계정을 관리해요. 알림 같은 앱 설정은 환경 설정에서 바꿀 수 있어요.');
  if (IS_MOCK) head.append(mockTag());
  box.append(head, form);
  return box;
}
