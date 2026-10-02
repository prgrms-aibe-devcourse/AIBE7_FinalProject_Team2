// fetch 공통 함수 (API 명세 1-1 ~ 1-4)
// - 성공(2xx): 응답 본문(결과 객체)을 그대로 돌려준다. 성공 응답은 감싸지 않는다.
// - 실패: 목 API와 같은 에러 본문 { code, message, currentStatus, details }를 throw한다.
//   화면은 목이든 실제든 같은 방식(error.code · error.currentStatus)으로 에러를 처리한다.
import { API_BASE_URL } from './config.js';

// 서버가 에러 본문을 주지 못한 경우(연결 실패, 서버 다운 등)의 공통 에러
function fallbackError(code, message, status) {
  return { code, message, currentStatus: null, details: null, status };
}

/**
 * @param {'GET'|'POST'} method
 * @param {string} endpoint  /api/v1 뒤의 경로 (예: '/cases/1/experience')
 * @param {object} [body] JSON 본문 (POST)
 */
export async function request(method, endpoint, body) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method,
      // 익명 ID 쿠키(NLNB_AID)를 함께 보낸다. 쿠키 발급은 체험 시작(API 2) 응답의 Set-Cookie가 한다.
      credentials: 'include',
      headers: body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' },
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
