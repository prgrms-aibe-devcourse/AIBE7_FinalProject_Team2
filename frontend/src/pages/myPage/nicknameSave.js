// 내 정보 수정의 닉네임 저장 · 서버 회원 정보 반영 규칙 (FE-137). 화면(myProfileEdit.js)에서 떼어 두어 node --test로 확인한다(nicknameSave.test.js).
// 화면은 이 함수들이 돌려주는 값을 그대로 칸 · 결과 줄 · 로그인 표시에 적는다.

// 중복(409) · 형식(400)은 닉네임 칸 아래에 고칠 방법으로, 그 밖의 실패는 저장 결과 줄에 알린다
export const NICKNAME_TAKEN = '이미 쓰고 있는 닉네임이에요. 다른 닉네임을 골라 주세요.';
export const NICKNAME_REJECTED = '서버가 이 닉네임을 받지 못했어요. 한글 · 영문 · 숫자로 다시 적어 주세요.';
// 로그인이 풀렸으면(401) 다시 시도가 아니라 다시 로그인으로 안내한다 — 커뮤니티 글쓰기 · 댓글 문구와 같다
export const SESSION_EXPIRED = '로그인이 풀렸어요. 다시 로그인해 주세요.';

/** 서버에 있는 닉네임과 입력이 다른가. 아직 정하지 않은 채(saved '') 비워 둔 칸은 바꾸지 않은 것이다 */
export const isNicknameChanged = (wanted, saved) => wanted.trim() !== saved;

/** 저장 실패를 화면 문구로: fieldMessage는 닉네임 칸 아래(없으면 ''), statusText는 저장 결과 줄 */
export function nicknameFailure(failure) {
  const fieldMessage = failure?.code === 'DUPLICATE_NICKNAME' ? NICKNAME_TAKEN : failure?.code === 'VALIDATION_ERROR' ? NICKNAME_REJECTED : '';
  const statusText = failure?.code === 'UNAUTHORIZED' ? SESSION_EXPIRED
    : fieldMessage ? '닉네임을 저장하지 못했어요.'
      : '닉네임을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  return { fieldMessage, statusText };
}

/**
 * 바뀐 닉네임이면 서버(M-7)에 저장한다. 바뀌지 않았으면 부르지 않는다.
 * @returns {Promise<{ changed: false } | { changed: true, ok: true, nickname: string } | { changed: true, ok: false, fieldMessage: string, statusText: string }>}
 */
export async function saveNickname({ api, wanted, saved }) {
  if (!isNicknameChanged(wanted, saved)) return { changed: false };
  const nickname = wanted.trim();
  try {
    const result = await api.putNickname(nickname);
    return { changed: true, ok: true, nickname: result?.nickname ?? nickname };
  } catch (failure) {
    return { changed: true, ok: false, ...nicknameFailure(failure) };
  }
}

/** 저장 결과 줄. 닉네임과 한 줄 소개는 따로 저장하고, 한쪽이 실패해도 다른 쪽은 저장한다 */
export function saveStatus({ nicknameStatusText, bioSaved }) {
  if (nicknameStatusText) return bioSaved ? `${nicknameStatusText} 한 줄 소개는 저장했어요.` : `${nicknameStatusText} 한 줄 소개도 저장하지 못했어요.`;
  return bioSaved ? '저장했어요.' : '한 줄 소개를 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}

/**
 * 서버 회원 정보(M-8)가 늦게 도착했을 때 닉네임 칸 · 로그인 표시를 어떻게 맞출지 (정하지 않은 것은 undefined).
 * - 이미 저장을 시작했다면(touched) 방금 저장한 값이 더 새로우므로 건드리지 않는다. 계정 정보 줄은 화면이 따로 채운다
 * - 아직 고치지 않은 칸만 서버 닉네임으로 맞춘다(다른 곳에서 바꾼 경우)
 * - 로그인 표시는 화면을 그릴 때의 값이 아니라 지금 값(currentNickname)과 비교한다
 */
export function planMeSync({ me, inputValue, savedNickname, touched, currentNickname }) {
  if (touched || !me) return {};
  const plan = {};
  const serverNickname = me.nickname ?? '';
  if (inputValue.trim() === savedNickname && serverNickname !== savedNickname) {
    plan.savedNickname = serverNickname;
    plan.inputValue = serverNickname;
  }
  if (me.displayName && me.displayName !== currentNickname) plan.sessionNickname = me.displayName;
  return plan;
}
