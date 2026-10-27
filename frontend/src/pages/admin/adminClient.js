// 관리자 API 전용 fetch (API 명세 8-1). 사용자 화면의 공용 src/api/client.js와 따로 둔다.
// - 세션 쿠키(NLNB_ADMIN_SESSION)를 함께 보낸다(credentials: 'include').
// - 상태를 바꾸는 요청(POST · PATCH · PUT · DELETE)은 쿠키 XSRF-TOKEN 값을 헤더 X-XSRF-TOKEN에 담는다.
// - 본문이 FormData면 multipart로 보낸다(사진 업로드 A-13-3 · A-13-4).
//   쿠키가 없으면 A-1(GET /admin/auth/csrf)로 먼저 받는다. 403이 오면 토큰을 새로 받아 한 번만 다시 보낸다.
// - 실패하면 사용자 API와 같은 에러 본문 { code, message, currentStatus, details, status }를 throw한다.
import { API_BASE_URL } from '../../api/config.js';

const ADMIN_BASE = `${API_BASE_URL}/admin`;
const CSRF_COOKIE = 'XSRF-TOKEN';
let csrfHeaderName = 'X-XSRF-TOKEN'; // A-1 응답의 headerName으로 갱신한다

function readCookie(name) {
  const found = document.cookie.split('; ').find((part) => part.startsWith(`${name}=`));
  return found ? decodeURIComponent(found.slice(name.length + 1)) : null;
}

function fallbackError(code, message, status) {
  return { code, message, currentStatus: null, details: null, status };
}

async function send(method, endpoint, body) {
  const headers = { Accept: 'application/json' };
  // 사진 업로드(A-13-3 · A-13-4)는 multipart다. Content-Type을 우리가 정하면 경계 문자열(boundary)이
  // 빠져 서버가 본문을 파싱하지 못하므로, FormData일 때는 브라우저가 알아서 붙이게 둔다 (FE-106)
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const token = readCookie(CSRF_COOKIE);
    if (token) headers[csrfHeaderName] = token;
  }
  let response;
  try {
    response = await fetch(`${ADMIN_BASE}${endpoint}`, {
      method,
      credentials: 'include',
      cache: 'no-store', // 원본 판결문 등 내부 정보를 브라우저 캐시에 남기지 않는다
      headers,
      body: body === undefined ? undefined : (isForm ? body : JSON.stringify(body)),
    });
  } catch {
    throw fallbackError('NETWORK_ERROR', '서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.', 0);
  }
  if (response.status === 204) return null;

  let data = null;
  try {
    data = await response.json();
  } catch {
    if (response.ok) throw fallbackError('INTERNAL_ERROR', '서버 응답을 해석할 수 없어요.', response.status);
  }
  if (response.ok) return data;
  // 401은 로그인 실패(INVALID_CREDENTIALS)만 빼고 모두 "로그아웃됨"으로 본다. 사용 중 계정이 비활성되면
  // 로그아웃 요청까지 401이 오므로, 응답 코드와 관계없이 화면이 로그인 폼으로 돌아가게 UNAUTHORIZED로 맞춘다 (BE-67 합의)
  if (response.status === 401 && data?.code !== 'INVALID_CREDENTIALS') {
    throw { code: 'UNAUTHORIZED', message: data?.message ?? '로그인이 필요해요.', currentStatus: null, details: null, status: 401 };
  }
  if (data && typeof data.code === 'string') {
    throw { code: data.code, message: data.message, currentStatus: data.currentStatus ?? null, details: data.details ?? null, status: response.status };
  }
  // 코드 없는 5xx: 502 · 503 · 504는 프록시가 백엔드에 닿지 못한 경우라 연결 오류, 그 밖은 서버 오류로 본다
  if ([502, 503, 504].includes(response.status)) throw fallbackError('NETWORK_ERROR', '서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.', response.status);
  if (response.status >= 500) throw fallbackError('INTERNAL_ERROR', '서버 오류가 발생했어요.', response.status);
  if (response.status === 403) throw fallbackError('FORBIDDEN', '권한이 없어요.', 403);
  throw fallbackError('INTERNAL_ERROR', '서버 오류가 발생했어요.', response.status);
}

/** A-1. CSRF 토큰 쿠키를 받는다 */
export async function fetchCsrf() {
  const data = await send('GET', '/auth/csrf');
  if (data?.headerName) csrfHeaderName = data.headerName;
  return data;
}

async function mutate(method, endpoint, body) {
  if (!readCookie(CSRF_COOKIE)) await fetchCsrf();
  try {
    return await send(method, endpoint, body);
  } catch (error) {
    if (error?.status !== 403) throw error;
    await fetchCsrf(); // 토큰이 바뀌었을 수 있다(로그인 뒤 세션 교체 등). 한 번만 다시 보낸다
    return send(method, endpoint, body);
  }
}

export const adminGet = (endpoint) => send('GET', endpoint);
export const adminPost = (endpoint, body) => mutate('POST', endpoint, body);
export const adminPatch = (endpoint, body) => mutate('PATCH', endpoint, body);
export const adminPut = (endpoint, body) => mutate('PUT', endpoint, body);
export const adminDelete = (endpoint) => mutate('DELETE', endpoint);
