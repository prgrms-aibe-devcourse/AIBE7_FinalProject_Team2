import { currentUser } from '../utils/session.js';
import { getBookmarkCharacter } from '../utils/bookmarkCharacter.js';
import { peek as peekExperience, readJudgment } from './mockExperienceStore.js';
import { mockCases as cases } from './caseListMockData.js';
import { seedAuthors, seedComments, seedPosts } from './communityMockData.js';
import { DEFAULT_DECO } from '../utils/profileDeco.js';
import { getCardLineSettings } from '../utils/profileSettings.js';
import { awardMockXp, mockLevelView } from './levelMockApi.js';

// 커뮤니티 목 — FE-47 (S-18). 서버 API가 아직 명세에 없어 화면 확인용으로만 둔다.
// 다른 사람의 글 · 댓글은 고정 값(communityMockData.js), 내가 쓴 글 · 댓글 · 좋아요는 브라우저 localStorage에 둔다.
// 읽기는 누구나, 쓰기 · 댓글 · 좋아요는 로그인해야 한다(목업 로그인, utils/session.js). 오류 코드 LOGIN_REQUIRED는 임시 이름 — API 설계 때 BE와 확정한다
const KEY = 'nlnb-mock-community';
const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
export const CATEGORIES = ['VERDICT', 'QUESTION', 'FREE'];
export const TITLE_MAX = 60;
export const BODY_MAX = 2000;
export const COMMENT_MAX = 500;
export const IMAGE_MAX = 3;
export const PAGE_SIZE = 6;
// 판결을 확정한 뒤(S-05 제출 이후)여야 내 판결을 붙일 수 있다
const VERDICT_DONE = ['VERDICT_CONFIRMED', 'AI_REVEALED', 'COMPLETED'];

function fail(code, message, details = null) {
  throw { code, message, details };
}

const empty = () => ({ posts: [], comments: {}, liked: {}, nextId: 1000 });
let memory = null; // 저장소를 못 쓰면 이번 방문 동안만
function load() {
  if (memory) return memory;
  try { return { ...empty(), ...JSON.parse(localStorage.getItem(KEY)) }; } catch { return empty(); }
}
function store(state) {
  try { localStorage.setItem(KEY, JSON.stringify(state)); memory = null; } catch (error) {
    memory = state;
    // 사진(data URL)이 많으면 저장 한도를 넘는다 — 이번 탭에서만 보이고 새로고침하면 사라진다
    console.warn('[communityMock] 브라우저 저장 한도를 넘어 이번 탭에서만 글을 보관합니다', error);
  }
}

function me() {
  const user = currentUser();
  if (!user) fail('LOGIN_REQUIRED', '로그인한 뒤 이용할 수 있습니다.');
  return { nickname: user.nickname, character: getBookmarkCharacter() };
}

// 글쓴이 명함(FE-88): 글 · 댓글의 author에 프로필카드 꾸미기 + 공개 성향카드를 붙인다. 내 글은 목에 내 꾸미기가 없어 기본 카드다(실제 API는 서버가 채운다)
const DEFAULT_AUTHOR = { intro: '', level: 1, job: { code: 'NONE', name: '무직' }, cardLine: { line: 'type', showLevel: false }, completedCount: 0, profileCard: { ...DEFAULT_DECO }, shownType: null };
// 내 글 · 내 댓글은 내 레벨 · 직업 · 카드 아래 줄 설정을 쓴다(실제 API는 서버가 채운다)
function myCard(author) {
  if (author.nickname !== currentUser()?.nickname) return {};
  const { level, job } = mockLevelView();
  const { cardLine, cardLevel } = getCardLineSettings();
  return { level, job, cardLine: { line: cardLine, showLevel: cardLevel } };
}
const withCard = (author) => ({ ...DEFAULT_AUTHOR, ...seedAuthors[author.nickname], ...myCard(author), ...author });

// 보는 사람이 이 사건 판결을 확정했는지(FE-93). 판결 전이면 화면이 남의 판결 카드를 접는다. 목 체험 기록은 사건 1 하나뿐이다. 필드 이름은 임시 — API 설계 때 BE와 확정한다
const viewerJudged = (caseId) => Boolean(currentUser()) && caseId === 1 && VERDICT_DONE.includes(peekExperience()?.status); // 로그아웃이면 판결 전(체험 기록은 브라우저에 남아도 사용자 것이 아니다)

const caseTitle = (caseId) => cases.find((item) => item.caseId === caseId)?.title ?? null;
const allPosts = (state) => [...state.posts, ...seedPosts];
const commentsOf = (state, postId) => [...(seedComments[postId] ?? []), ...(state.comments[postId] ?? [])];

// 목록 · 상세가 같은 모양을 쓴다. 목록은 본문을 줄이지 않고 그대로 준다(화면이 줄 수로 자른다)
function view(state, post) {
  const liked = Boolean(state.liked[post.postId]);
  const seedLike = seedPosts.some((seed) => seed.postId === post.postId) ? post.likeCount : 0;
  return {
    ...post,
    author: withCard(post.author),
    caseTitle: caseTitle(post.caseId),
    likeCount: seedLike + (liked ? 1 : 0),
    liked,
    commentCount: commentsOf(state, post.postId).length,
    mine: Boolean(post.mine),
    viewerJudged: viewerJudged(post.caseId),
  };
}

function findPost(state, postId) {
  const post = allPosts(state).find((item) => item.postId === postId);
  if (!post) fail('POST_NOT_FOUND', '글을 찾을 수 없습니다.');
  return post;
}

// 많이 본 글(목은 좋아요 순 셋)
const popularOf = (posts) => [...posts].sort((a, b) => b.likeCount - a.likeCount).slice(0, 3).map(({ postId, title }) => ({ postId, title }));

// GET /community/posts?category&caseId&sort&page — 글 목록(최신순 · 좋아요순 · 댓글순) + 많이 본 글 + 고를 수 있는 사건
export async function getCommunityPosts({ category = null, caseId = null, sort = 'LATEST', page = 1 } = {}) {
  await delay();
  const state = load();
  let list = allPosts(state).map((post) => view(state, post));
  if (category) list = list.filter((post) => post.category === category);
  if (caseId) list = list.filter((post) => post.caseId === caseId);
  const byDate = (a, b) => b.createdAt.localeCompare(a.createdAt);
  const order = {
    LATEST: byDate,
    LIKES: (a, b) => b.likeCount - a.likeCount || byDate(a, b),
    COMMENTS: (a, b) => b.commentCount - a.commentCount || byDate(a, b),
  }[sort] ?? byDate;
  list.sort(order);
  const end = Math.max(1, page) * PAGE_SIZE;
  return structuredClone({
    posts: list.slice(0, end),
    total: list.length,
    hasMore: list.length > end,
    popular: popularOf(allPosts(state).map((post) => view(state, post))),
    cases: cases.map(({ caseId: id, title }) => ({ caseId: id, title })),
  });
}

// 커뮤니티 메인(FE-89) 칸 수
export const HOT_SIZE = 3;
export const RECOMMENDED_SIZE = 3;
export const RANKING_SIZE = 10;
const HOT_POSTS = 3;
const RECOMMENDED_POSTS = 2;

// 메인에 싣는 사건 요약: 기사 조각(제목 · 부제 · 본문 두 단락 또는 사진)과 사건 정보 확인에 쓰는 값
const caseSummary = ({ caseId, title, crimeCategoryLabel, shortIntro, headline, deck, article, thumbnailUrl, participantCount, estimatedMinutes, scriptReady }) => ({
  caseId, title, crimeCategoryLabel, shortIntro, headline, deck, article: article.slice(0, 2), thumbnailUrl, participantCount, estimatedMinutes, scriptReady,
});
// 사건 글: 좋아요 많은 순으로 몇 개만(댓글 내용은 싣지 않는다)
const casePosts = (posts, size) => [...posts].sort((a, b) => b.likeCount - a.likeCount || b.createdAt.localeCompare(a.createdAt)).slice(0, size)
  .map(({ postId, title, likeCount, commentCount, author }) => ({ postId, title, likeCount, commentCount, author }));

// GET /community/home — 커뮤니티 메인(FE-89): 지금 이야기가 많은 사건 · 추천 사건 · 레벨 랭킹 + 많이 본 글 · 고를 수 있는 사건.
// 이야기 수(talkCount)는 실제 API에서는 최근 하루 글 · 댓글 수다. 목은 날짜가 고정이라 모든 글 · 댓글을 센다.
// 추천 사건은 이야기 많은 사건에 없는 사건을 이야기 수 · 판결한 사람 수 순으로 고른다(추천 기준은 BE와 확정한다).
// 랭킹은 레벨 순(같으면 체험한 사건 수 순). 레벨은 사건 체험 · 커뮤니티 활동으로 오르며 성향과 무관하다. 목은 글쓴이 명함 값(seedAuthors)으로 센다
export async function getCommunityHome() {
  await delay();
  const state = load();
  const posts = allPosts(state).map((post) => view(state, post));
  const byCase = cases.map((item) => {
    const own = posts.filter((post) => post.caseId === item.caseId);
    const talkCount = own.reduce((sum, post) => sum + 1 + post.commentCount, 0);
    return { item, own, talkCount };
  });
  const hot = byCase.filter(({ talkCount }) => talkCount > 0)
    .sort((a, b) => b.talkCount - a.talkCount || a.item.caseId - b.item.caseId).slice(0, HOT_SIZE);
  const hotIds = new Set(hot.map(({ item }) => item.caseId));
  const recommended = byCase.filter(({ item }) => !hotIds.has(item.caseId))
    .sort((a, b) => b.talkCount - a.talkCount || b.item.participantCount - a.item.participantCount).slice(0, RECOMMENDED_SIZE);
  const ranking = Object.keys(seedAuthors).map((nickname) => withCard({ nickname }))
    .sort((a, b) => b.level - a.level || b.completedCount - a.completedCount || a.nickname.localeCompare(b.nickname, 'ko')).slice(0, RANKING_SIZE)
    .map(({ nickname, level, job, cardLine, completedCount, profileCard, shownType }, index) => ({ rank: index + 1, nickname, level, job, cardLine, completedCount, profileCard, shownType }));
  return structuredClone({
    hotCases: hot.map(({ item, own, talkCount }) => ({ case: caseSummary(item), talkCount, postCount: own.length, posts: casePosts(own, HOT_POSTS) })),
    recommendedCases: recommended.map(({ item, own }) => ({ case: caseSummary(item), postCount: own.length, posts: casePosts(own, RECOMMENDED_POSTS) })),
    ranking,
    popular: popularOf(posts),
    cases: cases.map(({ caseId: id, title }) => ({ caseId: id, title })),
  });
}

// GET /community/posts/{postId} — 글 + 댓글(오래된 순)
export async function getCommunityPost(postId) {
  await delay();
  const state = load();
  const post = findPost(state, postId);
  return structuredClone({ ...view(state, post), comments: commentsOf(state, postId).map((comment) => ({ ...comment, author: withCard(comment.author) })) });
}

// 내 판결 붙이기에 쓸 값. 판결을 확정하지 않았으면 null. 목 체험 기록은 사건 1 하나뿐이다(mockExperienceStore)
export async function getMyShareableVerdict() {
  await delay();
  if (!VERDICT_DONE.includes(peekExperience()?.status)) return null; // 읽기만 한다: 체험 기록을 만들지 않는다
  const judgment = readJudgment();
  if (!judgment) return null;
  const factors = judgment.factors ?? [];
  return structuredClone({
    caseId: 1,
    caseTitle: caseTitle(1),
    verdict: {
      penaltyType: judgment.penaltyType, reducedTo: judgment.reducedTo ?? null,
      prisonMonths: judgment.prisonMonths ?? null, fineAmount: judgment.fineAmount ?? null, suspensionMonths: judgment.suspensionMonths ?? null,
      upCount: factors.filter((factor) => factor.direction === 'UP').length,
      downCount: factors.filter((factor) => factor.direction === 'DOWN').length,
    },
  });
}

// POST /community/posts { category, caseId, title, body, images, shareVerdict }
// images: 화면이 줄여 보낸 이미지 주소(data URL) 최대 3장. shareVerdict면 서버가 내 판결을 붙인다(화면이 판결 값을 보내지 않는다)
export async function createCommunityPost({ category, caseId = null, title, body, images = [], shareVerdict = false } = {}) {
  await delay();
  const author = me();
  const cleanTitle = typeof title === 'string' ? title.trim() : '';
  const cleanBody = typeof body === 'string' ? body.trim() : '';
  const details = [];
  if (!CATEGORIES.includes(category)) details.push({ field: 'category', reason: 'MUST_BE_ONE_OF_CATEGORIES' });
  if (caseId !== null && !caseTitle(caseId)) details.push({ field: 'caseId', reason: 'CASE_NOT_FOUND' });
  if (!cleanTitle || cleanTitle.length > TITLE_MAX) details.push({ field: 'title', reason: `MUST_BE_1_TO_${TITLE_MAX}_CHARS` });
  if (!cleanBody || cleanBody.length > BODY_MAX) details.push({ field: 'body', reason: `MUST_BE_1_TO_${BODY_MAX}_CHARS` });
  if (!Array.isArray(images) || images.length > IMAGE_MAX || images.some((src) => typeof src !== 'string' || !src)) details.push({ field: 'images', reason: `AT_MOST_${IMAGE_MAX}` });
  let verdict = null;
  if (shareVerdict) {
    const shareable = await getMyShareableVerdict();
    if (!shareable) details.push({ field: 'shareVerdict', reason: 'VERDICT_NOT_CONFIRMED' });
    else if (caseId !== null && caseId !== shareable.caseId) details.push({ field: 'shareVerdict', reason: 'CASE_MISMATCH' });
    else verdict = shareable.verdict;
  }
  if (details.length) fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', details);
  const state = load();
  const post = {
    postId: state.nextId,
    category,
    caseId: verdict ? 1 : caseId, // 판결을 붙이면 그 사건 글이다
    author, title: cleanTitle, body: cleanBody, images: [...images], verdict,
    likeCount: 0, createdAt: new Date().toISOString(), mine: true,
  };
  store({ ...state, posts: [post, ...state.posts], nextId: state.nextId + 1 });
  awardMockXp('post', post.postId);
  if (verdict) awardMockXp('verdictBonus', post.caseId);
  return structuredClone(view(load(), post));
}

// POST /community/posts/{postId}/like — 누를 때마다 켜고 끈다
export async function toggleCommunityLike(postId) {
  await delay();
  me();
  const state = load();
  findPost(state, postId);
  const liked = { ...state.liked };
  if (liked[postId]) delete liked[postId];
  else liked[postId] = true;
  const next = { ...state, liked };
  store(next);
  if (liked[postId]) awardMockXp('like', postId);
  const { likeCount } = view(next, findPost(next, postId));
  return { liked: Boolean(liked[postId]), likeCount };
}

// POST /community/posts/{postId}/comments { body }
export async function createCommunityComment(postId, { body } = {}) {
  await delay();
  const author = me();
  const state = load();
  findPost(state, postId);
  const text = typeof body === 'string' ? body.trim() : '';
  if (!text || text.length > COMMENT_MAX) fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', [{ field: 'body', reason: `MUST_BE_1_TO_${COMMENT_MAX}_CHARS` }]);
  const comment = { commentId: state.nextId, author, body: text, createdAt: new Date().toISOString(), mine: true };
  store({ ...state, comments: { ...state.comments, [postId]: [...(state.comments[postId] ?? []), comment] }, nextId: state.nextId + 1 });
  awardMockXp('comment', comment.commentId);
  return structuredClone({ ...comment, author: withCard(comment.author) });
}

// 목 전용: 내가 쓴 글 · 댓글 · 좋아요를 지워 처음부터 다시 확인한다
export async function resetCommunityMock() {
  await delay();
  memory = null;
  try { localStorage.removeItem(KEY); } catch { /* 저장소를 못 쓰면 지울 것도 없다 */ }
}
