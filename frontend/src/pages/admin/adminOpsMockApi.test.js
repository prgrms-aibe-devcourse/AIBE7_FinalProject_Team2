import assert from 'node:assert/strict';
import test, { beforeEach } from 'node:test';
import * as api from './adminMockApi.js';

const store = new Map();
globalThis.sessionStorage = { getItem: (k) => store.get(k) ?? null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
const code = (promise) => promise.then(() => null, (failure) => failure.code);
const reason = (promise) => promise.then(() => null, (failure) => failure.details?.[0]?.reason);

beforeEach(async () => {
  store.clear();
  api.resetAdminOpsMock();
  await api.login('admin@example.com', 'pw');
});

test('로그인하지 않으면 운영 API는 모두 401', async () => {
  store.clear();
  for (const call of [() => api.getDashboard(), () => api.getMembers(), () => api.getPosts(), () => api.getReports(), () => api.getAccounts(), () => api.getNotices(), () => api.getBannedWords()]) {
    assert.equal(await code(call()), 'UNAUTHORIZED');
  }
});

test('회원: 검색 · 상태 필터 · 쪽 나누기', async () => {
  const all = await api.getMembers({ size: 10 });
  assert.equal(all.items.length, 10);
  assert.equal(all.totalElements, 25);
  assert.equal((await api.getMembers({ status: 'SUSPENDED' })).totalElements, 1);
  assert.equal((await api.getMembers({ keyword: '푸른판사' })).totalElements, 1);
  assert.equal(await code(api.getMembers({ size: 1000 })), 'VALIDATION_ERROR');
});

test('회원 정지 · 해제: 사유 필수, 상태가 맞아야 한다', async () => {
  assert.equal(await code(api.suspendMember(1001, { reason: '  ' })), 'VALIDATION_ERROR');
  assert.equal(await code(api.suspendMember(1001, { reason: '사유', days: 0 })), 'VALIDATION_ERROR');
  assert.equal((await api.suspendMember(1001, { reason: '사유', days: 7 })).status, 'SUSPENDED');
  assert.equal(await reason(api.suspendMember(1001, { reason: '사유' })), 'ALREADY_SUSPENDED');
  assert.equal(await reason(api.suspendMember(1012, { reason: '사유' })), 'MEMBER_WITHDRAWN');
  assert.equal((await api.restoreMember(1001)).status, 'ACTIVE');
  assert.equal(await reason(api.restoreMember(1001)), 'NOT_SUSPENDED');
  assert.equal(await code(api.getMember(9)), 'MEMBER_NOT_FOUND');
});

test('게시글: 숨김 → 복구 → 삭제, 삭제 뒤에는 바꿀 수 없다', async () => {
  assert.equal((await api.hidePost(5001, '사유')).status, 'HIDDEN');
  assert.equal(await reason(api.hidePost(5001, '사유')), 'NOT_VISIBLE');
  assert.equal((await api.restorePost(5001)).status, 'VISIBLE');
  assert.equal((await api.deletePost(5001, '사유')).status, 'DELETED');
  assert.equal(await reason(api.deletePost(5001, '사유')), 'ALREADY_DELETED');
  assert.equal(await code(api.deletePost(5002, '')), 'VALIDATION_ERROR');
  assert.equal((await api.getPosts({ reported: true })).items.every((post) => post.reportCount > 0), true);
});

test('댓글 숨김 · 복구', async () => {
  const post = await api.getPost(5004);
  const commentId = post.comments[0].commentId;
  assert.equal((await api.hideComment(commentId)).status, 'HIDDEN');
  assert.equal(await reason(api.hideComment(commentId)), 'NOT_VISIBLE');
  assert.equal((await api.restoreComment(commentId)).status, 'VISIBLE');
  assert.equal(await code(api.hideComment(1)), 'COMMENT_NOT_FOUND');
});

test('신고 처리: 처리 방법이 대상에 반영되고, 한 번만 처리할 수 있다', async () => {
  assert.equal(await code(api.resolveReport(301, { action: 'BAN', note: '메모' })), 'VALIDATION_ERROR');
  assert.equal(await code(api.resolveReport(301, { action: 'HIDE', note: '' })), 'VALIDATION_ERROR');
  const resolved = await api.resolveReport(301, { action: 'HIDE', note: '광고 확인' });
  assert.equal(resolved.status, 'RESOLVED');
  assert.equal((await api.getPost(5004)).status, 'HIDDEN');
  assert.equal(await reason(api.resolveReport(301, { action: 'HIDE', note: '또' })), 'ALREADY_HANDLED');
  await api.resolveReport(304, { action: 'SUSPEND', note: '반복 욕설' });
  assert.equal((await api.getMember(1004)).status, 'SUSPENDED');
  assert.equal((await api.rejectReport(302, '중복 신고')).status, 'REJECTED');
  assert.equal((await api.getReports({ status: 'PENDING' })).items.every((item) => item.status === 'PENDING'), true);
});

test('관리자 계정: 추가 검증 · 중복 · 자기 자신 비활성화 금지', async () => {
  assert.equal(await code(api.createAccount({ email: 'bad', displayName: '이름', role: 'SUPER' })), 'VALIDATION_ERROR');
  assert.equal(await code(api.createAccount({ email: 'x@example.com', displayName: '이름', role: 'ROOT' })), 'VALIDATION_ERROR');
  assert.equal(await code(api.createAccount({ email: 'ADMIN@example.com', displayName: '이름', role: 'SUPER' })), 'DUPLICATE_EMAIL');
  const created = await api.createAccount({ email: 'New@Example.com', displayName: '새 담당', role: 'REVIEWER' });
  assert.equal(created.email, 'new@example.com');
  assert.equal(await reason(api.setAccountActive(1, false)), 'SELF_DEACTIVATE');
  assert.equal((await api.setAccountActive(created.adminId, false)).active, false);
  assert.equal((await api.getAuditLogs({ action: 'LOGIN_FAILURE' })).items.length, 1);
});

test('공지 · 금칙어', async () => {
  assert.equal(await code(api.createNotice({ title: '', body: '내용' })), 'VALIDATION_ERROR');
  const notice = await api.createNotice({ title: '새 공지', body: '내용' });
  assert.equal(notice.published, false);
  assert.equal((await api.updateNotice(notice.noticeId, { published: true, pinned: true })).published, true);
  assert.equal((await api.getNotices()).items[0].pinned, true); // 고정 공지가 맨 위
  assert.equal(await code(api.updateNotice(99, { pinned: true })), 'NOTICE_NOT_FOUND');
  assert.equal(await code(api.addBannedWord('불법도박')), 'DUPLICATE_WORD');
  assert.deepEqual((await api.addBannedWord('새말')).items.at(-1), '새말');
  assert.equal((await api.removeBannedWord('새말')).items.includes('새말'), false);
  assert.equal(await code(api.removeBannedWord('없는말')), 'WORD_NOT_FOUND');
});

test('대시보드: 처리 대기 신고 수가 신고 처리에 따라 줄어든다', async () => {
  const before = (await api.getDashboard()).counts.pendingReports;
  await api.rejectReport(302, '중복');
  assert.equal((await api.getDashboard()).counts.pendingReports, before - 1);
});
