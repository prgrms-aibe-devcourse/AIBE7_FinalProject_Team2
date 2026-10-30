// npm test : 실제 API의 커뮤니티 · 닉네임 · 회원 정보 함수 (BE-115 · BE-139, FE-137). fetch를 바꿔 끼워 요청 주소 · 방식 · 본문을 확인한다
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  createCommunityComment, createCommunityPost, getCommunityHome, getCommunityPost, getCommunityPosts, getMe,
  getMyShareableVerdict, putNickname, toggleCommunityLike,
} from './realApi.js';
import { logout } from './client.js';

let calls;
// responder(url, init)가 { status, body }를 돌려준다. 기본은 빈 200
function stubFetch(responder = () => ({ status: 200, body: {} })) {
  calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, method: init.method, body: init.body === undefined ? undefined : JSON.parse(init.body) });
    const { status, body } = await responder(url, init);
    return { status, ok: status >= 200 && status < 300, json: async () => body };
  };
}
const last = () => calls[calls.length - 1];

beforeEach(async () => {
  stubFetch(() => ({ status: 204 }));
  await logout(); // 테스트마다 토큰을 비운다
  stubFetch();
});

test('글 목록은 값이 있는 쿼리만 붙인다 — 첫 쪽 · 전체는 쿼리 없음', async () => {
  await getCommunityPosts();
  assert.equal(last().url, '/api/v1/community/posts');
  assert.equal(last().method, 'GET');
  await getCommunityPosts({ category: null, caseId: null, sort: 'LATEST', page: 1 });
  assert.equal(last().url, '/api/v1/community/posts?sort=LATEST');
  await getCommunityPosts({ category: 'QUESTION', caseId: 3, sort: 'LIKES', page: 2 });
  assert.equal(last().url, '/api/v1/community/posts?category=QUESTION&caseId=3&sort=LIKES&page=2');
});

test('상세 · 좋아요 · 댓글 · 메인은 명세 12장 주소로 부른다', async () => {
  await getCommunityPost(108);
  assert.deepEqual([last().method, last().url], ['GET', '/api/v1/community/posts/108']);
  await toggleCommunityLike(108);
  assert.deepEqual([last().method, last().url], ['POST', '/api/v1/community/posts/108/like']);
  await createCommunityComment(108, { body: '댓글' });
  assert.deepEqual([last().method, last().url, last().body], ['POST', '/api/v1/community/posts/108/comments', { body: '댓글' }]);
  await getCommunityHome();
  assert.deepEqual([last().method, last().url], ['GET', '/api/v1/community/home']);
});

test('글쓰기는 화면이 만든 본문을 그대로 보낸다(판결은 서버가 붙인다)', async () => {
  const body = { category: 'VERDICT', caseId: 1, title: '제목', body: '본문', images: [], shareVerdict: true };
  await createCommunityPost(body);
  assert.deepEqual([last().method, last().url, last().body], ['POST', '/api/v1/community/posts', body]);
});

test('붙일 수 있는 내 판결은 판결 확정 전(204)이면 null이다', async () => {
  stubFetch(() => ({ status: 204 }));
  assert.equal(await getMyShareableVerdict(), null);
  assert.equal(last().url, '/api/v1/community/shareable-verdict');
  const shareable = { caseId: 1, caseTitle: '사건', verdict: { penaltyType: 'PRISON', upCount: 1, downCount: 2 } };
  stubFetch(() => ({ status: 200, body: shareable }));
  assert.deepEqual(await getMyShareableVerdict(), shareable);
});

test('로그인 전 쓰기(401 UNAUTHORIZED)는 그 코드 그대로 던진다 — 화면이 로그인 안내를 보인다', async () => {
  stubFetch(() => ({ status: 401, body: { code: 'UNAUTHORIZED', message: '로그인이 필요합니다.' } }));
  await assert.rejects(toggleCommunityLike(108), { code: 'UNAUTHORIZED', status: 401 });
});

test('닉네임 설정은 PUT /members/me/nickname, 내 회원 정보는 GET /members/me', async () => {
  stubFetch(() => ({ status: 200, body: { nickname: '내로판사' } }));
  assert.deepEqual(await putNickname('내로판사'), { nickname: '내로판사' });
  assert.deepEqual([last().method, last().url, last().body], ['PUT', '/api/v1/members/me/nickname', { nickname: '내로판사' }]);
  await getMe();
  assert.deepEqual([last().method, last().url], ['GET', '/api/v1/members/me']);
});

test('닉네임 중복은 409 DUPLICATE_NICKNAME, 형식은 400 VALIDATION_ERROR 그대로 던진다', async () => {
  stubFetch(() => ({ status: 409, body: { code: 'DUPLICATE_NICKNAME', message: '이미 사용 중인 닉네임입니다.' } }));
  await assert.rejects(putNickname('내로판사'), { code: 'DUPLICATE_NICKNAME', status: 409 });
  stubFetch(() => ({ status: 400, body: { code: 'VALIDATION_ERROR', message: '', details: [{ field: 'nickname', reason: 'VALIDATION_ERROR' }] } }));
  await assert.rejects(putNickname('가'), (failure) => failure.code === 'VALIDATION_ERROR' && failure.details[0].field === 'nickname');
});
