import { currentUser } from '../utils/session.js';

// 사건 리뷰(독자 엽서) 목 — FE-55. 서버 API가 아직 명세에 없어(별점 · 한 줄 의견) 화면 확인용으로만 둔다.
// 내가 보낸 엽서는 브라우저 localStorage에, 다른 독자의 엽서는 아래 고정 값을 쓴다.
const KEY = 'nlnb-mock-case-review';
const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
export const COMMENT_MAX = 200;

const readers = [
  { id: 'r1', nickname: '새벽의 배심원', rating: 5, comment: '형량을 정하는 게 이렇게 어려운 줄 몰랐어요. 요소를 하나씩 따져 보니 AI와 실제 판결이 왜 갈렸는지 알겠더라고요.', createdAt: '2026-10-17' },
  { id: 'r2', nickname: '밤샘 방청객', rating: 4, comment: '신문 형식이라 몰입이 잘 돼요. 마지막 비교 화면이 특히 좋았습니다.', createdAt: '2026-10-16' },
  { id: 'r3', nickname: '말없는 판사', rating: 3, comment: '내용은 좋은데 중간에 읽을 글이 조금 많았어요.', createdAt: '2026-10-14' },
];

function fail(code, message, details = null) {
  throw { code, message, details };
}

function readMine() {
  try { return JSON.parse(localStorage.getItem(KEY)) ?? {}; } catch { return {}; }
}

function saveMine(all) {
  try { localStorage.setItem(KEY, JSON.stringify(all)); } catch { /* 저장소를 못 쓰면 이번 방문 동안만 */ memory = all; }
}
let memory = null;
const mineAll = () => memory ?? readMine();

function summarize(list) {
  const count = list.length;
  const sum = list.reduce((total, { rating }) => total + rating, 0);
  return { count, averageRating: count ? Math.round((sum / count) * 10) / 10 : null };
}

// GET /cases/{caseId}/reviews — 평균 별점 · 개수 · 내 엽서(없으면 null) · 다른 독자 엽서(최신순)
export async function getCaseReviews(caseId) {
  await delay();
  if (!Number.isInteger(caseId) || caseId < 1) fail('CASE_NOT_FOUND', '사건을 찾을 수 없습니다.');
  const mine = mineAll()[caseId] ?? null;
  const all = mine ? [mine, ...readers] : readers;
  return structuredClone({ ...summarize(all), mine, reviews: readers });
}

// POST /cases/{caseId}/reviews { rating, comment } — 사건마다 한 장. 보낸 엽서는 바꾸지 않는다(사전 판단 엽서와 같다)
export async function postCaseReview(caseId, { rating, comment } = {}) {
  await delay();
  if (!Number.isInteger(caseId) || caseId < 1) fail('CASE_NOT_FOUND', '사건을 찾을 수 없습니다.');
  const text = typeof comment === 'string' ? comment.trim() : '';
  const details = [];
  if (!(Number.isInteger(rating) && rating >= 1 && rating <= 5)) details.push({ field: 'rating', reason: 'MUST_BE_INTEGER_1_TO_5' });
  if (!text || text.length > COMMENT_MAX) details.push({ field: 'comment', reason: `MUST_BE_1_TO_${COMMENT_MAX}_CHARS` });
  if (details.length) fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', details);
  const all = mineAll();
  if (all[caseId]) fail('ALREADY_REVIEWED', '이미 엽서를 보낸 사건이에요.');
  const saved = { id: 'mine', nickname: currentUser()?.nickname ?? '이름 없는 독자', rating, comment: text, createdAt: new Date().toISOString().slice(0, 10) };
  saveMine({ ...all, [caseId]: saved });
  return structuredClone(saved);
}

// 목 전용: 보낸 엽서를 지워 처음부터 다시 확인한다
export async function resetCaseReviewMock() {
  await delay();
  memory = null;
  try { localStorage.removeItem(KEY); } catch { /* 저장소를 못 쓰면 지울 것도 없다 */ }
}
