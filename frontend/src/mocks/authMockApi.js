// 개발용 목업 로그인 (FE-52, S-12). 목 모드에서 화면 · 헤더 상태를 확인하려고 둔다. 실제 모드는 realApi.js의
// 같은 이름(login · signup · logout, FE-74 · FE-117)을 쓴다.
// 어떤 아이디 · 비밀번호든 로그인된다. 계정을 저장하거나 비밀번호를 어디에도 보내지 · 남기지 않는다.
// @가 있는 아이디는 로그인 화면이 목 관리자 로그인으로 보내므로(adminLoginRule.js) 여기로 오지 않는다
import { nicknameReason } from '../utils/nickname.js';

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const KAKAO_NICKNAME = '카카오 회원'; // 목업 카카오 로그인의 닉네임
const NICKNAME_MAX = 12;
// 목업 닉네임 중복 확인용: '중복'으로 시작하는 닉네임은 이미 쓰는 닉네임으로 본다(중복 안내 화면 확인용, admin 아이디와 같은 방식). 서버는 대소문자 무관 중복 금지다(BE-139)
const isTakenNickname = (nickname) => String(nickname).trim().startsWith('중복');

// 오류 모양은 서버와 같다: details는 [{ field, reason }] 배열(API 명세 1-4, PR #306 리뷰). 칸이 없으면 null
// reason: details의 사유. 없으면 code와 같다(서버의 BusinessException.ofField). 닉네임은 서버처럼 사유 이름(BE-134)
function fail(code, field = null, reason = code) {
  const messages = { VALIDATION_ERROR: '입력값을 확인해 주세요.', DUPLICATE_ID: '이미 쓰고 있는 아이디예요.', DUPLICATE_NICKNAME: '이미 쓰고 있는 닉네임이에요.' };
  throw { code, message: messages[code], currentStatus: null, details: field ? [{ field, reason }] : null };
}

// 목업 닉네임: 아이디 그대로(최대 12자). @가 있는 아이디는 로그인 화면이 목 관리자 로그인으로 보내 여기 오지 않으므로
// 예전의 "@ 앞부분만" 처리는 지웠다 (FE-117 · PR #308 리뷰)
export function mockNicknameOf(id) {
  return String(id).trim().slice(0, NICKNAME_MAX);
}

// method: 'kakao' | 'password'. 응답 { nickname }
export async function login({ method, id, password } = {}) {
  await delay();
  if (method === 'kakao') return { nickname: KAKAO_NICKNAME };
  if (method !== 'password') fail('VALIDATION_ERROR', 'method');
  if (!mockNicknameOf(id ?? '')) fail('VALIDATION_ERROR', 'id');
  if (!password) fail('VALIDATION_ERROR', 'password');
  return { nickname: mockNicknameOf(id) };
}

// 목업 회원가입 (FE-104). 계정을 만들지 않고 닉네임만 돌려준다(가입하면 바로 로그인된 상태).
// admin으로 시작하는 아이디는 이미 있는 아이디로 본다(중복 안내 화면 확인용). 응답 { nickname }
export async function signup({ id, password, nickname } = {}) {
  await delay();
  if (!String(id ?? '').trim() || !password || !String(nickname ?? '').trim()) fail('VALIDATION_ERROR');
  if (String(id).trim().startsWith('admin')) fail('DUPLICATE_ID', 'id');
  if (isTakenNickname(nickname)) fail('DUPLICATE_NICKNAME', 'nickname');
  return { nickname: String(nickname).trim() };
}

// 목업 닉네임 설정 (BE-139, M-7). 서버와 같은 형식 검사 · 중복 금지를 흉내 낸다. 저장은 화면이 로그인 표시값(session.js)에 한다. 응답 { nickname }
export async function putNickname(nickname) {
  await delay();
  const value = String(nickname ?? '');
  const reason = nicknameReason(value); // 서버(Nickname.validate)와 같은 사유 이름 (BE-134)
  if (reason) fail('VALIDATION_ERROR', 'nickname', reason);
  if (isTakenNickname(value)) fail('DUPLICATE_NICKNAME', 'nickname');
  return { nickname: value };
}

// 목업 일러스트 총 개수 (BE-142, 서버 I-1). 서버 응답과 같은 모양이고, 값은 지금 이미지 폴더 기준의 예시다
export async function getIllustrationSummary() {
  await delay();
  return { total: 212, byKind: { cases: 76, 'characters/adult': 9, 'characters/cheek': 4, 'characters/cheek-slim': 4, 'characters/deform': 2, 'characters/icon/cropped': 18, 'characters/icon/generated': 12, 'characters/law': 9, 'characters/mascot': 12, 'characters/personality': 32, 'characters/roles': 10, 'characters/scene': 16, 'characters/standing': 8 } };
}

// 목업 첫 로그인 가져오기 (FE-138, 서버 M-9 ~ M-11). 안내 창을 확인하려고 늘 후보가 있는 것으로 보이고, 가져오기 · 초기화를 고르면 이번 방문 동안은 없다
let importDone = false;
export async function getAnonymousImport() {
  await delay();
  return importDone ? null : { experienceCount: 3, preJudgmentCount: 2 };
}
export async function importAnonymous() {
  await delay();
  importDone = true;
  return null;
}
export async function discardAnonymous() {
  await delay();
  importDone = true;
  return null;
}

export async function logout() {
  await delay();
  return null;
}
