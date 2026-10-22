// 내 정보 수정(FE-77): 마이페이지 "내 정보 수정" 화면의 내부 틀. 서버 닉네임 · 탈퇴 API가 아직 없어 저장은 이 브라우저의 로그인 표시값(session.js)에만 반영한다.
// 구성: 프로필(닉네임 · 한 줄 소개 · 재심 · 공개 범위) · 계정 정보 · 내 기록 관리 · 계정 관리(로그아웃 · 회원 탈퇴) · 약관. 알림 등 앱 설정은 "환경 설정"에서 다룬다
// 한 줄 소개 · 공개 범위도 서버 API 전이라 이 브라우저(localStorage)에만 저장한다
import { button, element, mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { clearUser, currentUser, saveUser } from '../../utils/session.js';
import { NICKNAME_HINT, NICKNAME_MAX, nicknameError } from '../../utils/nickname.js';
import { localStore } from '../../utils/safeStorage.js';
import { RETAKE_NOTES, openRetakeDialog, retakeAfter, retakeState } from './baselineRetake.js';

const EXTRAS_KEY = 'nlnb-profile-extras';
const BIO_MAX = 40;
const DEFAULT_EXTRAS = { bio: '', showProfileCard: true, showTypeCard: true };

function loadExtras() {
  try {
    const saved = JSON.parse(localStore()?.getItem(EXTRAS_KEY) ?? 'null');
    return { ...DEFAULT_EXTRAS, ...(saved && typeof saved === 'object' ? saved : {}) };
  } catch {
    return { ...DEFAULT_EXTRAS };
  }
}

function saveExtras(extras) {
  try {
    localStore().setItem(EXTRAS_KEY, JSON.stringify(extras));
  } catch {
    // 저장소를 못 쓰면 이번 방문 동안만 화면에 남는다
  }
}

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

// 켜고 끄는 항목 한 줄
function toggle(id, label, checked, onChange) {
  const input = element('input');
  input.type = 'checkbox';
  input.id = id;
  input.checked = checked;
  input.addEventListener('change', () => onChange(input.checked));
  const text = element('label', '', label);
  text.htmlFor = id;
  const row = element('div', 'edit-toggle');
  row.append(input, text);
  return row;
}

export function renderProfileEdit({ api, navigate, go, data = {} }) {
  const user = currentUser();
  const extras = loadExtras();
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
  bio.value = extras.bio;
  bio.maxLength = BIO_MAX;
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

  // 한 줄 소개 글자 수. 프로필카드 · 성향카드 그림은 마이페이지 첫 화면에 있어 여기에는 두지 않는다
  const syncCount = () => { bioCount.textContent = `${bio.value.length}/${BIO_MAX}`; };
  bio.addEventListener('input', syncCount);
  syncCount();

  const portrait = element('div', 'edit-field');
  portrait.append(element('span', 'edit-label', '대표 이미지 · 카드 꾸미기'), button('프로필카드에서 바꾸기', () => go('profile'), 'secondary-button'));

  // 공개 범위: 커뮤니티가 열리면 남에게 보여줄 카드를 고른다
  const visibility = element('div', 'edit-field');
  visibility.append(
    element('span', 'edit-label', '공개 범위'),
    toggle('edit-show-profile', '프로필카드를 다른 사람에게 보여 줘요', extras.showProfileCard, (on) => { extras.showProfileCard = on; }),
    toggle('edit-show-type', '성향카드를 다른 사람에게 보여 줘요', extras.showTypeCard, (on) => { extras.showTypeCard = on; }),
    element('p', 'edit-hint', '커뮤니티가 열리면 적용돼요. 지금은 이 브라우저에만 기억해 둬요.'),
  );

  // 성향카드 다시 정하기: 성향카드 화면 가운데 칸과 같은 동작(1회, 처음 카드는 남는다)
  const retake = element('div', 'edit-field');
  const state = retakeState(data);
  retake.append(element('span', 'edit-label', '성향카드 재심'));
  if (state === 'done') {
    const after = retakeAfter(data.baselineRetake.result);
    retake.append(
      element('p', 'edit-hint', `${after ? `${after}에 ` : ''}재심을 받았어요. 초심 카드와 함께 성향카드 화면에서 볼 수 있어요.`),
      button('성향카드 보기', () => go('type'), 'secondary-button'),
    );
  } else if (state === 'needTest') {
    retake.append(element('p', 'edit-hint', '재심은 첫 성향 테스트 뒤에 청구할 수 있어요.'), button('첫 성향 테스트 하기', () => navigate('test'), 'secondary-button'));
  } else {
    const open = button('재심 청구하기', () => openRetakeDialog({ data, navigate, from: open }), 'secondary-button');
    open.disabled = state !== 'ready';
    retake.append(element('p', 'edit-hint', `성향 테스트를 한 번 더 해서 성향카드를 다시 정해요. 초심 카드는 그대로 남아요. ${RETAKE_NOTES[state]}`), open);
  }

  // 계정 정보(읽기 전용): 서버가 로그인 방식 · 가입일 · 최근 로그인을 주기 전까지는 자리만 둔다
  const facts = element('dl', 'edit-facts');
  facts.append(
    fact('로그인 방식', user ? (IS_MOCK ? '개발용 목업 로그인' : '카카오 로그인') : '로그인 전 (이 브라우저의 익명 ID)'),
    fact('가입일', user ? '서버 연동 후 표시돼요' : '—'),
    fact('최근 로그인', user ? '서버 연동 후 표시돼요' : '—'),
  );

  // 내 기록 관리: 기록은 익명 ID(쿠키) 기준. 계정으로 옮기기 · 초기화 · 내보내기는 서버 연동 후
  const recordCount = (data.experiences ?? []).length;
  const hasType = Boolean(data.personality?.type);
  const summary = element('p', 'edit-hint', `이 브라우저에 남은 기록: 체험 ${recordCount}건 · 성향 결과 ${hasType ? '있음' : '없음'}`);
  if (IS_MOCK) summary.append(' ', mockTag());
  const transfer = button('내 계정으로 옮기기 (준비 중)', () => {}, 'secondary-button');
  transfer.disabled = true;
  const exportButton = button('기록 내보내기 (준비 중)', () => {}, 'secondary-button');
  exportButton.disabled = true;
  const recordActions = element('div', 'edit-actions');
  recordActions.append(transfer, exportButton);
  const [resetOpen, resetConfirm] = dangerAction({
    id: 'edit-reset-agree',
    openLabel: '기록 초기화',
    agreeText: '체험 기록과 성향 결과가 지워지고 되돌릴 수 없다는 것을 이해했어요. 계정은 그대로 남아요.',
    runLabel: '기록 지우기',
    onRun: () => { status.textContent = '기록 초기화는 서버 연동 후 열려요. 지금은 기록이 그대로 남아 있어요.'; },
  });
  const records = element('div', 'edit-field');
  records.append(
    summary,
    element('p', 'edit-hint', user ? '로그인 전에 쌓은 기록을 이 계정으로 합칠 수 있어요.' : '로그인하면 이 브라우저의 기록을 내 계정으로 옮길 수 있어요. 쿠키를 지우면 로그인 전 기록은 찾을 수 없어요.'),
    recordActions,
    resetOpen,
    resetConfirm,
  );

  // 계정 관리: 로그아웃 · 회원 탈퇴. 탈퇴는 한 번 더 확인한다
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
  const account = element('div', 'edit-field');
  account.append(user ? logout : element('p', 'edit-hint', '로그인 전에는 로그아웃 · 탈퇴할 계정이 없어요.'), ...(user ? [withdrawOpen, withdrawConfirm] : []));

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

  const profileBody = element('div', 'edit-profile');
  profileBody.append(nickname, bioField);

  form.append(
    section('프로필', profileBody, portrait, retake, visibility),
    section('계정 정보', facts),
    section('내 기록 관리', records),
    section('계정 관리', account),
    section('약관 · 방침', policyField),
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
    saveExtras({ ...extras, bio: bio.value.trim() });
    if (user) saveUser({ nickname: input.value, role: user.role }); // 서버 닉네임 API 전: 이 브라우저의 로그인 표시값만 바꾼다
    status.textContent = '저장했어요.';
  });

  const head = element('p', 'edit-intro', '프로필과 계정을 관리해요. 알림 같은 앱 설정은 환경 설정에서 바꿀 수 있어요.');
  if (IS_MOCK) head.append(mockTag());
  box.append(head, form);
  return box;
}
