// fetch 공통 함수 (API 명세 1-1 ~ 1-4)
// - 성공(2xx): 응답 본문(결과 객체)을 그대로 돌려준다. 성공 응답은 감싸지 않는다.
// - 실패: 목 API와 같은 에러 본문 { code, message, currentStatus, details }를 throw한다.
//   화면은 목이든 실제든 같은 방식(error.code · error.currentStatus)으로 에러를 처리한다.
// - 액세스 토큰(FE-74): 있으면 Authorization에 싣고, 401이면 M-1 재발급을 한 번 한 뒤 같은 요청을 다시 보낸다.
import { API_BASE_URL } from './config.js';
import { clearUser, currentUser } from '../utils/session.js';

// 회원 액세스 토큰. 메모리에만 둔다(API 명세 10장) — 새로고침하면 사라지고, 401이 오면 재발급으로 다시 받는다
let accessToken = null;
// 재발급 중인 요청. 리프레시 토큰이 재발급마다 로테이션돼 동시에 두 번 부르면 두 번째가 실패하므로 하나로 묶는다
let reissuing = null;
// 로그아웃할 때마다 올린다. 그 전에 시작한 재발급 결과는 버린다 (PR #235 리뷰)
let session = 0;

// 서버가 에러 본문을 주지 못한 경우(연결 실패, 서버 다운 등)의 공통 에러
function fallbackError(code, message, status) {
  return { code, message, currentStatus: null, details: null, status };
}

/**
 * @param {'GET'|'POST'|'PUT'|'PATCH'} method
 * @param {string} endpoint  /api/v1 뒤의 경로 (예: '/cases/1/experience')
 * @param {object} [body] JSON 본문 (POST · PUT · PATCH)
 */
export async function request(method, endpoint, body) {
  const sentWith = accessToken;
  try {
    return await send(method, endpoint, body);
  } catch (failure) {
    // 재발급 · 로그아웃(/auth/...) 자신의 401은 다시 시도하지 않는다
    if (failure?.status !== 401 || endpoint.startsWith('/auth/')) throw failure;
    // 토큰도 로그인 표시도 없으면 리프레시 쿠키도 없다 — 재발급하지 않고 그대로 401을 넘긴다 (PR #235 리뷰)
    if (sentWith === null && !currentUser()) throw failure;
    // 기다리는 사이 다른 요청이 이미 새 토큰을 받았으면 재발급 없이 그 토큰으로 다시 보낸다
    if (accessToken === sentWith) await reissueAccessToken().catch(() => { throw failure; });
    return send(method, endpoint, body);
  }
}

// M-1 토큰 재발급. 리프레시 토큰 쿠키(NLNB_RT)로 새 액세스 토큰을 받는다.
// 401(쿠키 없음 · 만료 · 로그아웃됨)이면 로그인이 끝난 것이라 이 브라우저의 로그인 표시도 지운다. 연결 오류 등은 지우지 않는다
export function reissueAccessToken() {
  const startedIn = session;
  reissuing ??= send('POST', '/auth/token/reissue', {})
    .then((data) => {
      if (startedIn === session) accessToken = data.accessToken; // 기다리는 사이 로그아웃했으면 토큰을 다시 채우지 않는다
      return data;
    })
    .catch((failure) => {
      accessToken = null;
      if (failure?.status === 401) clearUser();
      throw failure;
    })
    .finally(() => { reissuing = null; });
  return reissuing;
}

// M-2 로그아웃. 서버 응답과 상관없이 들고 있던 액세스 토큰은 버린다
export function logout() {
  session += 1;
  accessToken = null;
  return send('POST', '/auth/logout', {});
}

async function send(method, endpoint, body) {
  const headers = body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method,
      // 익명 ID 쿠키(NLNB_AID)를 함께 보낸다. 쿠키 발급은 체험 시작(API 2) 응답의 Set-Cookie가 한다.
      credentials: 'include',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw fallbackError('NETWORK_ERROR', '서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.', 0);
  }

  if (response.status === 204) return null;

  let data = null;
  let bodyIsJson = true;
  try {
    data = await response.json();
  } catch {
    bodyIsJson = false; // 본문이 JSON이 아니면 아래에서 처리한다 (프록시 오류로 HTML이 온 경우 등)
  }

  if (response.ok) {
    // 200인데 JSON이 아니면 조용히 null을 돌려주지 않는다 (화면이 구조 분해하다 TypeError로 원인을 숨기게 됨, 리뷰 반영)
    if (!bodyIsJson) {
      throw fallbackError('INTERNAL_ERROR', '서버 응답을 해석할 수 없어요.', response.status);
    }
    return data;
  }

  // 서버의 공통 에러 형식(API 명세 1-4)이면 그대로 던진다.
  if (data && typeof data.code === 'string') {
    throw { code: data.code, message: data.message, currentStatus: data.currentStatus ?? null, details: data.details ?? null, status: response.status };
  }
  // 서버가 에러 본문을 주지 않았다. 5xx는 프록시 · 서버가 응답하지 못한 경우(백엔드 미실행, 502 등)라 연결 문제로 본다.
  if (response.status >= 500) {
    throw fallbackError('NETWORK_ERROR', '서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.', response.status);
  }
  throw fallbackError('INTERNAL_ERROR', '서버 오류가 발생했어요.', response.status);
}

export const get = (endpoint) => request('GET', endpoint);
export const post = (endpoint, body) => request('POST', endpoint, body ?? {});
export const put = (endpoint, body) => request('PUT', endpoint, body ?? {});
export const patch = (endpoint, body) => request('PATCH', endpoint, body ?? {});
