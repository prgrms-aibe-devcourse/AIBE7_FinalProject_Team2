import { collectionOf } from './myPageView.js';

// 수집 기록 보내기(P-9 POST /collection, FE-132). 사건을 마친 6막과 마이페이지 백필이 부른다.
// 멱등이라 같은 사건을 다시 보내도 된다(이미 가진 항목은 added에서 빠진다). 로그인한 회원만 부른다(로그인 전이면 서버가 401).
// 실패해도 체험 · 마이페이지 흐름을 막지 않는다 — 다음에 마이페이지에 들어올 때 백필이 다시 채운다.

const NOT_COLLECTIBLE = /^items\[(\d+)\]\.itemId$/;

// 보내는 중인 기록. 6막에서 바로 마이페이지로 가면 마이페이지가 이것을 기다린 뒤 불러온다
// (먼저 불러오면 방금 마친 사건이 "빠진 사건"으로 보여 백필 — "본 것"으로 저장 — 될 수 있다)
const pending = new Set();
export const recordsSettled = () => Promise.allSettled([...pending]);

// 400 VALIDATION_ERROR 중 "모을 수 있는 목록에 없음"(BE-136)으로 거절된 항목의 순서. 그 밖의 거절이면 빈 집합
export function rejectedIndexes(error) {
  if (error?.code !== 'VALIDATION_ERROR' || !Array.isArray(error.details)) return new Set();
  return new Set(error.details
    .filter((detail) => detail?.reason === 'NOT_COLLECTIBLE')
    .map((detail) => NOT_COLLECTIBLE.exec(detail.field ?? '')?.[1])
    .filter((index) => index !== undefined)
    .map(Number));
}

/**
 * 사건 하나에서 얻은 일러스트 · 의상을 보낸다. 하나라도 목록 밖이면 서버가 아무것도 저장하지 않으므로(BE-136),
 * 그 항목만 빼고 한 번 더 보낸다(대본에 그림이 없는 의상이 섞여도 나머지는 모이게).
 * @returns {Promise<object|null>} P-9 응답(added · collected · newlyUnlocked), 보내지 못했으면 null
 */
export function recordCollection(api, caseId, script, options) {
  const sending = send(api, caseId, script, options);
  pending.add(sending);
  sending.finally(() => pending.delete(sending));
  return sending;
}

async function send(api, givenCaseId, script, { backfill = false } = {}) {
  const caseId = Number(givenCaseId); // 주소에서 온 사건 번호는 문자열일 수 있다
  if (!api?.postCollection || !Number.isInteger(caseId) || caseId <= 0) return null;
  const items = collectionOf(caseId, script);
  const post = (list) => api.postCollection({ caseId, backfill, items: list });
  try {
    return await post(items);
  } catch (error) {
    const rejected = rejectedIndexes(error);
    const rest = items.filter((_, index) => !rejected.has(index));
    if (rejected.size > 0 && rest.length > 0) {
      console.warn('[FE-132] 모을 수 없는 항목을 빼고 다시 보냅니다', { caseId, rejected: items.filter((_, index) => rejected.has(index)) });
      try {
        return await post(rest);
      } catch (retryError) {
        console.warn('[FE-132] 수집 기록을 남기지 못했습니다', { caseId, error: retryError });
        return null;
      }
    }
    console.warn('[FE-132] 수집 기록을 남기지 못했습니다', { caseId, error });
    return null;
  }
}
