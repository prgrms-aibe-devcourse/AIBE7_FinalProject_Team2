// npm test : 커뮤니티 목 API 계약 (FE-47). 실제 API를 붙일 때 이 규칙을 기준으로 맞춘다
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  BODY_MAX, COMMENT_MAX, HOT_SIZE, IMAGE_MAX, PAGE_SIZE, RANKING_SIZE, RECOMMENDED_SIZE, TITLE_MAX,
  createCommunityComment, createCommunityPost, getCommunityHome, getCommunityPost, getCommunityPosts, getMyShareableVerdict, resetCommunityMock, toggleCommunityLike,
} from './communityMockApi.js';
import { save as saveExperience, saveJudgment } from './mockExperienceStore.js';
import { clearUser, saveUser } from '../utils/session.js';

const post = (extra = {}) => ({ category: 'FREE', title: '제목', body: '본문', ...extra });

// node에는 localStorage가 없어 테스트마다 빈 저장소를 둔다. 기본은 로그인한 상태
beforeEach(async () => {
  const data = new Map();
  globalThis.localStorage = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: (key) => data.delete(key) };
  await resetCommunityMock();
  saveUser({ nickname: '테스트 판사' });
});

test('목록은 최신순, 말머리 · 사건으로 거르고 쪽마다 PAGE_SIZE개씩 늘어난다', async () => {
  const first = await getCommunityPosts();
  assert.equal(first.posts.length, PAGE_SIZE);
  assert.equal(first.hasMore, first.total > PAGE_SIZE);
  const dates = first.posts.map((item) => item.createdAt);
  assert.deepEqual(dates, [...dates].sort().reverse());
  const second = await getCommunityPosts({ page: 2 });
  assert.equal(second.posts.length, Math.min(first.total, PAGE_SIZE * 2));

  const verdicts = await getCommunityPosts({ category: 'VERDICT' });
  assert.ok(verdicts.posts.length > 0 && verdicts.posts.every((item) => item.category === 'VERDICT'));
  const case1 = await getCommunityPosts({ caseId: 1 });
  assert.ok(case1.posts.every((item) => item.caseId === 1 && item.caseTitle));
});

test('판결 공유 글에는 내 판결만 있다(AI · 실제 판결 값이 없다)', async () => {
  const { posts } = await getCommunityPosts({ category: 'VERDICT' });
  for (const item of posts) {
    assert.ok(item.verdict);
    assert.deepEqual(Object.keys(item.verdict).sort(), ['downCount', 'fineAmount', 'penaltyType', 'prisonMonths', 'reducedTo', 'suspensionMonths', 'upCount']);
  }
});

test('없는 글은 POST_NOT_FOUND', async () => {
  await assert.rejects(getCommunityPost(99999), { code: 'POST_NOT_FOUND' });
  await assert.rejects(toggleCommunityLike(99999), { code: 'POST_NOT_FOUND' });
  await assert.rejects(createCommunityComment(99999, { body: '댓글' }), { code: 'POST_NOT_FOUND' });
});

test('로그아웃 상태면 쓰기 · 댓글 · 좋아요는 LOGIN_REQUIRED, 읽기는 된다', async () => {
  clearUser();
  await assert.rejects(createCommunityPost(post()), { code: 'LOGIN_REQUIRED' });
  await assert.rejects(toggleCommunityLike(108), { code: 'LOGIN_REQUIRED' });
  await assert.rejects(createCommunityComment(108, { body: '댓글' }), { code: 'LOGIN_REQUIRED' });
  assert.ok((await getCommunityPost(108)).title);
});

test('글 입력값: 말머리 · 제목 1 ~ 60자 · 본문 1 ~ 2000자 · 이미지 3장까지', async () => {
  const invalid = [
    post({ category: 'NOTICE' }), post({ title: '   ' }), post({ title: 'a'.repeat(TITLE_MAX + 1) }),
    post({ body: '' }), post({ body: 'a'.repeat(BODY_MAX + 1) }), post({ images: Array(IMAGE_MAX + 1).fill('data:image/jpeg;base64,AA') }),
    post({ caseId: 999 }),
  ];
  for (const body of invalid) await assert.rejects(createCommunityPost(body), { code: 'VALIDATION_ERROR' });
  const saved = await createCommunityPost(post({ title: '  앞뒤 공백  ', images: ['data:image/jpeg;base64,AA'] }));
  assert.equal(saved.title, '앞뒤 공백');
  assert.equal(saved.mine, true);
  assert.equal((await getCommunityPosts()).posts[0].postId, saved.postId); // 새 글이 맨 위
});

test('내 판결은 판결을 확정한 뒤에만 붙일 수 있고, 붙이면 사건 1 글이 된다', async () => {
  saveExperience({ status: 'REVIEWED', lastReviewedStep: 4 });
  assert.equal(await getMyShareableVerdict(), null);
  await assert.rejects(createCommunityPost(post({ category: 'VERDICT', shareVerdict: true })), { code: 'VALIDATION_ERROR' });

  saveExperience({ status: 'VERDICT_CONFIRMED', lastReviewedStep: 4 });
  saveJudgment({ penaltyType: 'PRISON', reducedTo: null, prisonMonths: 120, factors: [{ factorId: 1, direction: 'UP' }, { factorId: 2, direction: 'DOWN' }, { factorId: 3, direction: 'UP' }] });
  const shareable = await getMyShareableVerdict();
  assert.equal(shareable.verdict.upCount, 2);
  assert.equal(shareable.verdict.downCount, 1);
  await assert.rejects(createCommunityPost(post({ category: 'VERDICT', caseId: 2, shareVerdict: true })), { code: 'VALIDATION_ERROR' });
  const saved = await createCommunityPost(post({ category: 'VERDICT', shareVerdict: true }));
  assert.equal(saved.caseId, 1);
  assert.equal(saved.verdict.prisonMonths, 120);
});

test('글에는 보는 사람이 그 사건 판결을 확정했는지가 붙는다 — 판결 전이면 화면이 판결 카드를 접는다 (FE-93)', async () => {
  const judged = async () => (await getCommunityPost(108)).viewerJudged; // 108은 사건 1의 판결 공유 글
  assert.equal(await judged(), false);
  saveExperience({ status: 'REVIEWED', lastReviewedStep: 4 });
  assert.equal(await judged(), false);
  saveExperience({ status: 'VERDICT_CONFIRMED', lastReviewedStep: 4 });
  assert.equal(await judged(), true);
  clearUser(); // 로그아웃하면 기록이 브라우저에 남아도 판결 전으로 본다
  assert.equal(await judged(), false);
  assert.equal((await getCommunityPost(106)).viewerJudged, false); // 사건 없는 자유 글
});

test('내 판결 조회는 체험 기록을 만들지 않는다', async () => {
  assert.equal(await getMyShareableVerdict(), null);
  assert.equal(localStorage.getItem('nlnb-mock-review'), null);
});

test('좋아요는 누를 때마다 켜고 끈다', async () => {
  const before = (await getCommunityPost(108)).likeCount;
  assert.deepEqual(await toggleCommunityLike(108), { liked: true, likeCount: before + 1 });
  assert.deepEqual(await toggleCommunityLike(108), { liked: false, likeCount: before });
});

test('댓글은 공백을 뺀 1 ~ 500자, 달면 댓글 수가 는다', async () => {
  for (const body of ['', '  ', 'a'.repeat(COMMENT_MAX + 1), undefined]) {
    await assert.rejects(createCommunityComment(108, { body }), { code: 'VALIDATION_ERROR' });
  }
  const before = await getCommunityPost(108);
  const comment = await createCommunityComment(108, { body: ' 좋은 글이에요 ' });
  assert.equal(comment.body, '좋은 글이에요');
  const after = await getCommunityPost(108);
  assert.equal(after.commentCount, before.commentCount + 1);
  assert.equal(after.comments.at(-1).commentId, comment.commentId); // 오래된 순, 새 댓글은 맨 아래
});

test('글 · 댓글의 글쓴이에는 명함(프로필카드 꾸미기 + 공개 성향카드)이 붙는다 (FE-88)', async () => {
  const { posts } = await getCommunityPosts({ caseId: 1 }); // 첫 쪽에 들도록 사건 1 글에서 찾는다
  const seeded = posts.find((item) => item.author.nickname === '새벽의 배심원');
  assert.ok(seeded.author.profileCard.portrait.startsWith('/images/'));
  assert.equal(seeded.author.shownType.slot, 'current');
  assert.match(seeded.author.shownType.type.imageUrl, /^\/images\/characters\/personality\/\d{2}\.webp$/);
  const detail = await getCommunityPost(seeded.postId);
  assert.ok(detail.comments.every((comment) => comment.author.profileCard && 'shownType' in comment.author));
  // 내가 쓴 글은 목에 내 꾸미기가 없어 기본 카드 · 성향 없음으로 온다
  const mine = await createCommunityPost(post());
  assert.equal(mine.author.shownType, null);
  assert.equal(mine.author.profileCard.frame, 'basic');
});

test('메인: 이야기 많은 사건은 글 · 댓글 수 순, 추천 사건은 그 사건들과 겹치지 않는다 (FE-89)', async () => {
  const home = await getCommunityHome();
  assert.equal(home.hotCases.length, HOT_SIZE);
  const talks = home.hotCases.map((item) => item.talkCount);
  assert.deepEqual(talks, [...talks].sort((a, b) => b - a));
  assert.equal(home.recommendedCases.length, RECOMMENDED_SIZE);
  const hotIds = new Set(home.hotCases.map((item) => item.case.caseId));
  assert.ok(home.recommendedCases.every((item) => !hotIds.has(item.case.caseId)));
  // 사건 글은 좋아요 순 몇 개만, 댓글 내용은 싣지 않는다. 전체 글 수는 글 목록 사건 거르기와 같다
  for (const item of [...home.hotCases, ...home.recommendedCases]) {
    const likes = item.posts.map((post) => post.likeCount);
    assert.deepEqual(likes, [...likes].sort((a, b) => b - a));
    assert.ok(item.posts.every((post) => !('comments' in post) && !('body' in post) && post.author.profileCard));
    assert.equal(item.postCount, (await getCommunityPosts({ caseId: item.case.caseId })).total);
  }
});

test('메인: 새 글 · 댓글이 이야기 수에 더해진다', async () => {
  const before = (await getCommunityHome()).hotCases.find((item) => item.case.caseId === 1).talkCount;
  const saved = await createCommunityPost(post({ caseId: 1 }));
  await createCommunityComment(saved.postId, { body: '댓글' });
  const after = (await getCommunityHome()).hotCases.find((item) => item.case.caseId === 1).talkCount;
  assert.equal(after, before + 2);
});

test('메인 랭킹: 레벨 순 열 명, 순위 · 레벨 · 프로필카드 · 공개 성향만 싣는다', async () => {
  const { ranking } = await getCommunityHome();
  assert.equal(ranking.length, RANKING_SIZE);
  assert.deepEqual(ranking.map((item) => item.rank), Array.from({ length: RANKING_SIZE }, (_, i) => i + 1));
  for (let i = 1; i < ranking.length; i += 1) {
    const [a, b] = [ranking[i - 1], ranking[i]];
    assert.ok(a.level > b.level || (a.level === b.level && a.completedCount >= b.completedCount));
  }
  assert.deepEqual(Object.keys(ranking[0]).sort(), ['completedCount', 'level', 'nickname', 'profileCard', 'rank', 'shownType']);
});
