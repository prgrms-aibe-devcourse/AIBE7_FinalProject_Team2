// 관리자 운영 메뉴 API 목 (API 명세 8-4 A-16 ~ A-41 초안). 서버 API가 생기기 전에 화면 틀을 만들고 확인하는 용도다.
// 회원 · 게시글 · 신고는 모두 가상 값이다(실제 개인정보 없음). 상태는 메모리에만 두고 새로고침하면 처음으로 돌아간다.
import { ADMIN, delay, fail, now, paginate, requireLogin } from './adminMockBase.js';

const DAY = 24 * 60 * 60 * 1000;
const ago = (days, hours = 0) => new Date(Date.now() - days * DAY - hours * 3600 * 1000).toISOString();

const NICKNAMES = ['푸른판사', '정의의저울', '밤샘독서가', '조용한방청객', '느긋한재판장', '꼼꼼한서기', '작은법정', '새벽의변호인', '아침배심원', '둥근저울', '다정한검사', '깊은숲속', '차분한판결', '맑은증인', '늦은오후', '바른걸음', '고요한법원', '하얀가운', '든든한방패', '반짝이는눈', '느린우체부', '여름밤', '겨울햇살', '가을바람', '봄비내리는날'];

function createMembers() {
  return NICKNAMES.map((nickname, index) => {
    const status = index === 3 ? 'SUSPENDED' : index === 11 ? 'WITHDRAWN' : 'ACTIVE';
    return {
      memberId: 1001 + index, nickname, email: `user${index + 1}@example.com`, provider: 'KAKAO', status,
      joinedAt: ago(60 - index * 2), lastLoginAt: ago(index % 9, index), experienceCount: (index * 7) % 23, postCount: index % 5, reportCount: index === 3 ? 3 : index % 4 === 0 ? 1 : 0,
      suspendedUntil: status === 'SUSPENDED' ? ago(-7) : null, suspendReason: status === 'SUSPENDED' ? '반복된 욕설 게시글 (가상)' : null,
    };
  });
}

function createPosts() {
  const titles = ['이 사건 징역 12년이면 적당한가요?', '자수한 점을 얼마나 반영해야 할까요', '오늘의 사건 같이 얘기해요', '[광고] 무료 상담 받아보세요', '처음 해보는데 너무 재밌네요', '재판부 판결이 생각보다 낮아서 놀랐어요', '양형기준 어디서 확인하나요?', '다들 어떤 성향 나왔어요?', '욕설이 섞인 가상의 글입니다', '피해자 입장에서 생각해 봤어요'];
  return titles.map((title, index) => ({
    postId: 5001 + index, title, body: `${title}\n\n(가상 게시글 본문입니다. 실제 사용자 글이 아닙니다.)`, memberId: 1001 + (index * 3) % NICKNAMES.length, authorNickname: NICKNAMES[(index * 3) % NICKNAMES.length],
    status: index === 8 ? 'HIDDEN' : 'VISIBLE', hiddenReason: index === 8 ? '욕설 (신고 처리)' : null, createdAt: ago(index, index * 2), reportCount: index === 3 ? 4 : index === 8 ? 2 : 0,
    comments: Array.from({ length: index % 4 }, (_, i) => ({ commentId: 9000 + index * 10 + i, authorNickname: NICKNAMES[(index + i + 1) % NICKNAMES.length], content: `(가상 댓글 ${i + 1}) ${i === 1 ? '이건 광고 아닌가요?' : '저는 조금 다르게 생각해요.'}`, status: 'VISIBLE', createdAt: ago(index, index * 2 - i), reportCount: index === 3 && i === 1 ? 1 : 0 })),
  }));
}

function createReports() {
  return [
    { reportId: 301, targetType: 'POST', targetId: 5004, targetSummary: '[광고] 무료 상담 받아보세요', reason: 'SPAM', detail: '상담 광고 링크가 들어 있어요.', reporter: NICKNAMES[2], status: 'PENDING', createdAt: ago(0, 3), handledBy: null, handledAt: null, action: null, note: null },
    { reportId: 302, targetType: 'POST', targetId: 5004, targetSummary: '[광고] 무료 상담 받아보세요', reason: 'SPAM', detail: null, reporter: NICKNAMES[5], status: 'PENDING', createdAt: ago(0, 1), handledBy: null, handledAt: null, action: null, note: null },
    { reportId: 303, targetType: 'COMMENT', targetId: 9041, targetSummary: '(가상 댓글 2) 이건 광고 아닌가요?', reason: 'ABUSE', detail: '특정 사용자를 비꼬는 말투예요.', reporter: NICKNAMES[8], status: 'PENDING', createdAt: ago(1), handledBy: null, handledAt: null, action: null, note: null },
    { reportId: 304, targetType: 'MEMBER', targetId: 1004, targetSummary: `${NICKNAMES[3]} (회원)`, reason: 'ABUSE', detail: '여러 글에 반복해서 욕설을 남겨요.', reporter: NICKNAMES[1], status: 'PENDING', createdAt: ago(2), handledBy: null, handledAt: null, action: null, note: null },
    { reportId: 305, targetType: 'POST', targetId: 5009, targetSummary: '욕설이 섞인 가상의 글입니다', reason: 'ABUSE', detail: null, reporter: NICKNAMES[7], status: 'RESOLVED', createdAt: ago(3), handledBy: ADMIN.email, handledAt: ago(2), action: 'HIDE', note: '욕설 확인, 글 숨김' },
    { reportId: 306, targetType: 'POST', targetId: 5002, targetSummary: '자수한 점을 얼마나 반영해야 할까요', reason: 'OTHER', detail: '의견이 맘에 안 들어요.', reporter: NICKNAMES[9], status: 'REJECTED', createdAt: ago(4), handledBy: ADMIN.email, handledAt: ago(4), action: null, note: '의견 차이일 뿐 운영 정책 위반이 아님' },
  ];
}

function createState() {
  return {
    members: createMembers(),
    posts: createPosts(),
    reports: createReports(),
    accounts: [
      { adminId: 1, email: 'admin@example.com', displayName: '관리자', role: 'SUPER', active: true, lastLoginAt: ago(0, 1), createdAt: ago(90) },
      { adminId: 2, email: 'reviewer@example.com', displayName: '검수 담당', role: 'REVIEWER', active: true, lastLoginAt: ago(2), createdAt: ago(40) },
      { adminId: 3, email: 'former@example.com', displayName: '전 담당자', role: 'REVIEWER', active: false, lastLoginAt: ago(30), createdAt: ago(80) },
    ],
    audits: [
      { auditId: 1, adminEmail: ADMIN.email, action: 'CASE_PUBLISH', targetType: 'CASE', targetId: 13, createdAt: ago(1, 2) },
      { auditId: 2, adminEmail: ADMIN.email, action: 'JUDGMENT_APPROVE', targetType: 'JUDGMENT', targetId: 601, createdAt: ago(1, 3) },
      { auditId: 3, adminEmail: 'reviewer@example.com', action: 'SOURCE_VIEW', targetType: 'CASE_SOURCE', targetId: 31, createdAt: ago(2) },
      { auditId: 4, adminEmail: 'reviewer@example.com', action: 'LOGIN_SUCCESS', targetType: null, targetId: null, createdAt: ago(2, 1) },
      { auditId: 5, adminEmail: 'unknown@example.com', action: 'LOGIN_FAILURE', targetType: null, targetId: null, createdAt: ago(3) },
      { auditId: 6, adminEmail: ADMIN.email, action: 'CASE_UPDATE', targetType: 'CASE', targetId: 12, createdAt: ago(4) },
    ],
    notices: [
      { noticeId: 1, title: '서비스 점검 안내 (가상)', body: '10월 말 새벽에 점검이 있어요. (가상 공지)', pinned: true, published: true, createdAt: ago(5) },
      { noticeId: 2, title: '새 사건이 공개됐어요 (가상)', body: '층간 소음 다툼 사건이 공개됐어요. (가상 공지)', pinned: false, published: true, createdAt: ago(2) },
      { noticeId: 3, title: '다음 업데이트 예고 (가상)', body: '준비 중인 공지예요.', pinned: false, published: false, createdAt: ago(0, 5) },
    ],
    bannedWords: ['광고문의', '불법도박', '대출상담'],
  };
}

let state = createState();

/** 목 전용: 처음 상태로 되돌린다 */
export function resetAdminOpsMock() {
  state = createState();
}

const find = (list, key, id, code, message) => list.find((item) => item[key] === Number(id)) ?? fail(code, message, { status: 404 });
const needText = (value, max, field) => {
  if (typeof value !== 'string' || !value.trim() || value.length > max) fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', { status: 400, details: [{ field, reason: 'INVALID' }] });
  return value.trim();
};
const conflict = (reason) => fail('INVALID_STATE', '지금 상태에서는 할 수 없어요.', { status: 409, details: [{ field: 'status', reason }] });
const matches = (text, keyword) => !keyword || String(text).toLowerCase().includes(keyword.trim().toLowerCase());

// A-16 대시보드
export async function getDashboard() {
  await delay();
  requireLogin();
  const daily = (values) => values.map((count, index) => ({ date: ago(values.length - 1 - index).slice(0, 10), count }));
  return structuredClone({
    counts: {
      reviewPendingCases: 1, newMembers7d: 12, experiences7d: 87, pendingReports: state.reports.filter((item) => item.status === 'PENDING').length,
      hiddenPosts: state.posts.filter((item) => item.status === 'HIDDEN').length, suspendedMembers: state.members.filter((item) => item.status === 'SUSPENDED').length,
    },
    daily: { members: daily([1, 2, 0, 3, 1, 2, 3]), experiences: daily([9, 14, 8, 17, 11, 12, 16]) },
    recent: state.audits.slice(0, 5).map((item) => ({ adminEmail: item.adminEmail, action: item.action, createdAt: item.createdAt })),
  });
}

// A-17 ~ A-20 회원
export async function getMembers({ keyword, status, page = 0, size = 20 } = {}) {
  await delay();
  requireLogin();
  const rows = state.members.filter((item) => (!status || item.status === status) && (matches(item.nickname, keyword) || matches(item.email, keyword)));
  return paginate(rows, page, size);
}

export async function getMember(memberId) {
  await delay();
  requireLogin();
  const member = find(state.members, 'memberId', memberId, 'MEMBER_NOT_FOUND', '회원을 찾을 수 없어요.');
  return structuredClone({ ...member, recentPosts: state.posts.filter((post) => post.memberId === member.memberId).map(({ postId, title, status, createdAt }) => ({ postId, title, status, createdAt })) });
}

export async function suspendMember(memberId, { reason, days } = {}) {
  await delay();
  requireLogin();
  const member = find(state.members, 'memberId', memberId, 'MEMBER_NOT_FOUND', '회원을 찾을 수 없어요.');
  const text = needText(reason, 200, 'reason');
  if (days !== null && days !== undefined && !(Number.isInteger(days) && days >= 1 && days <= 365)) fail('VALIDATION_ERROR', '정지 기간은 1 ~ 365일이에요.', { status: 400 });
  if (member.status !== 'ACTIVE') conflict(member.status === 'SUSPENDED' ? 'ALREADY_SUSPENDED' : 'MEMBER_WITHDRAWN');
  Object.assign(member, { status: 'SUSPENDED', suspendReason: text, suspendedUntil: days ? new Date(Date.now() + days * DAY).toISOString() : null });
  return structuredClone(member);
}

export async function restoreMember(memberId) {
  await delay();
  requireLogin();
  const member = find(state.members, 'memberId', memberId, 'MEMBER_NOT_FOUND', '회원을 찾을 수 없어요.');
  if (member.status !== 'SUSPENDED') conflict('NOT_SUSPENDED');
  Object.assign(member, { status: 'ACTIVE', suspendReason: null, suspendedUntil: null });
  return structuredClone(member);
}

// A-21 ~ A-27 게시글 · 댓글
export async function getPosts({ keyword, status, reported, page = 0, size = 20 } = {}) {
  await delay();
  requireLogin();
  const rows = state.posts
    .filter((item) => (!status || item.status === status) && (reported !== true || item.reportCount > 0) && (matches(item.title, keyword) || matches(item.authorNickname, keyword)))
    .map(({ comments, body, ...rest }) => ({ ...rest, commentCount: comments.length }));
  return paginate(rows, page, size);
}

export async function getPost(postId) {
  await delay();
  requireLogin();
  return structuredClone(find(state.posts, 'postId', postId, 'POST_NOT_FOUND', '게시글을 찾을 수 없어요.'));
}

export async function hidePost(postId, reason) {
  await delay();
  requireLogin();
  const post = find(state.posts, 'postId', postId, 'POST_NOT_FOUND', '게시글을 찾을 수 없어요.');
  const text = needText(reason, 200, 'reason');
  if (post.status !== 'VISIBLE') conflict('NOT_VISIBLE');
  Object.assign(post, { status: 'HIDDEN', hiddenReason: text });
  return structuredClone(post);
}

export async function restorePost(postId) {
  await delay();
  requireLogin();
  const post = find(state.posts, 'postId', postId, 'POST_NOT_FOUND', '게시글을 찾을 수 없어요.');
  if (post.status !== 'HIDDEN') conflict('NOT_HIDDEN');
  Object.assign(post, { status: 'VISIBLE', hiddenReason: null });
  return structuredClone(post);
}

export async function deletePost(postId, reason) {
  await delay();
  requireLogin();
  const post = find(state.posts, 'postId', postId, 'POST_NOT_FOUND', '게시글을 찾을 수 없어요.');
  const text = needText(reason, 200, 'reason');
  if (post.status === 'DELETED') conflict('ALREADY_DELETED');
  Object.assign(post, { status: 'DELETED', hiddenReason: text });
  return structuredClone(post);
}

function findComment(commentId) {
  for (const post of state.posts) {
    const comment = post.comments.find((item) => item.commentId === Number(commentId));
    if (comment) return comment;
  }
  return fail('COMMENT_NOT_FOUND', '댓글을 찾을 수 없어요.', { status: 404 });
}

export async function hideComment(commentId) {
  await delay();
  requireLogin();
  const comment = findComment(commentId);
  if (comment.status !== 'VISIBLE') conflict('NOT_VISIBLE');
  comment.status = 'HIDDEN';
  return structuredClone(comment);
}

export async function restoreComment(commentId) {
  await delay();
  requireLogin();
  const comment = findComment(commentId);
  if (comment.status !== 'HIDDEN') conflict('NOT_HIDDEN');
  comment.status = 'VISIBLE';
  return structuredClone(comment);
}

// A-28 ~ A-30 신고
export async function getReports({ status, targetType, page = 0, size = 20 } = {}) {
  await delay();
  requireLogin();
  const rows = state.reports.filter((item) => (!status || item.status === status) && (!targetType || item.targetType === targetType));
  return paginate(rows, page, size);
}

export async function resolveReport(reportId, { action, note } = {}) {
  await delay();
  requireLogin();
  const report = find(state.reports, 'reportId', reportId, 'REPORT_NOT_FOUND', '신고를 찾을 수 없어요.');
  if (!['HIDE', 'SUSPEND', 'WARN'].includes(action)) fail('VALIDATION_ERROR', '처리 방법을 골라 주세요.', { status: 400, details: [{ field: 'action', reason: 'INVALID' }] });
  const text = needText(note, 300, 'note');
  if (report.status !== 'PENDING') conflict('ALREADY_HANDLED');
  // 처리 방법에 따라 대상도 함께 바꾼다 (목: 게시글 숨김 · 회원 정지만 반영)
  if (action === 'HIDE' && report.targetType === 'POST') { const post = state.posts.find((item) => item.postId === report.targetId); if (post && post.status === 'VISIBLE') Object.assign(post, { status: 'HIDDEN', hiddenReason: text }); }
  if (action === 'HIDE' && report.targetType === 'COMMENT') { const comment = findComment(report.targetId); comment.status = 'HIDDEN'; }
  if (action === 'SUSPEND' && report.targetType === 'MEMBER') { const member = state.members.find((item) => item.memberId === report.targetId); if (member?.status === 'ACTIVE') Object.assign(member, { status: 'SUSPENDED', suspendReason: text, suspendedUntil: new Date(Date.now() + 7 * DAY).toISOString() }); }
  Object.assign(report, { status: 'RESOLVED', action, note: text, handledBy: ADMIN.email, handledAt: now() });
  return structuredClone(report);
}

export async function rejectReport(reportId, note) {
  await delay();
  requireLogin();
  const report = find(state.reports, 'reportId', reportId, 'REPORT_NOT_FOUND', '신고를 찾을 수 없어요.');
  const text = needText(note, 300, 'note');
  if (report.status !== 'PENDING') conflict('ALREADY_HANDLED');
  Object.assign(report, { status: 'REJECTED', action: null, note: text, handledBy: ADMIN.email, handledAt: now() });
  return structuredClone(report);
}

// A-31 ~ A-35 관리자 계정 · 감사 로그
export async function getAccounts() {
  await delay();
  requireLogin();
  return structuredClone({ items: state.accounts });
}

export async function createAccount({ email, displayName, role } = {}) {
  await delay();
  requireLogin();
  const address = needText(email, 100, 'email');
  const name = needText(displayName, 30, 'displayName');
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(address)) fail('VALIDATION_ERROR', '이메일 형식을 확인해 주세요.', { status: 400, details: [{ field: 'email', reason: 'INVALID' }] });
  if (!['SUPER', 'REVIEWER'].includes(role)) fail('VALIDATION_ERROR', '권한을 골라 주세요.', { status: 400, details: [{ field: 'role', reason: 'INVALID' }] });
  if (state.accounts.some((item) => item.email === address.toLowerCase())) fail('DUPLICATE_EMAIL', '이미 등록된 이메일이에요.', { status: 409 });
  const account = { adminId: Math.max(...state.accounts.map((item) => item.adminId)) + 1, email: address.toLowerCase(), displayName: name, role, active: true, lastLoginAt: null, createdAt: now() };
  state.accounts.push(account);
  return structuredClone(account);
}

export async function setAccountActive(adminId, active) {
  await delay();
  requireLogin();
  const account = find(state.accounts, 'adminId', adminId, 'ADMIN_NOT_FOUND', '관리자 계정을 찾을 수 없어요.');
  if (account.adminId === ADMIN.adminId && !active) conflict('SELF_DEACTIVATE'); // 자기 자신은 잠글 수 없다
  account.active = Boolean(active);
  return structuredClone(account);
}

export async function getAuditLogs({ action, page = 0, size = 20 } = {}) {
  await delay();
  requireLogin();
  return paginate(state.audits.filter((item) => !action || item.action === action), page, size);
}

// A-36 ~ A-41 공지 · 금칙어
export async function getNotices() {
  await delay();
  requireLogin();
  return structuredClone({ items: state.notices.slice().sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.createdAt.localeCompare(a.createdAt)) });
}

export async function createNotice({ title, body, pinned = false, published = false } = {}) {
  await delay();
  requireLogin();
  const notice = { noticeId: Math.max(0, ...state.notices.map((item) => item.noticeId)) + 1, title: needText(title, 100, 'title'), body: needText(body, 2000, 'body'), pinned: Boolean(pinned), published: Boolean(published), createdAt: now() };
  state.notices.push(notice);
  return structuredClone(notice);
}

export async function updateNotice(noticeId, changes = {}) {
  await delay();
  requireLogin();
  const notice = find(state.notices, 'noticeId', noticeId, 'NOTICE_NOT_FOUND', '공지를 찾을 수 없어요.');
  const next = { ...notice };
  if ('title' in changes) next.title = needText(changes.title, 100, 'title');
  if ('body' in changes) next.body = needText(changes.body, 2000, 'body');
  if ('pinned' in changes) next.pinned = Boolean(changes.pinned);
  if ('published' in changes) next.published = Boolean(changes.published);
  Object.assign(notice, next);
  return structuredClone(notice);
}

export async function getBannedWords() {
  await delay();
  requireLogin();
  return structuredClone({ items: state.bannedWords });
}

export async function addBannedWord(word) {
  await delay();
  requireLogin();
  const text = needText(word, 30, 'word');
  if (state.bannedWords.includes(text)) fail('DUPLICATE_WORD', '이미 있는 금칙어예요.', { status: 409 });
  state.bannedWords.push(text);
  return structuredClone({ items: state.bannedWords });
}

export async function removeBannedWord(word) {
  await delay();
  requireLogin();
  const index = state.bannedWords.indexOf(word);
  if (index < 0) fail('WORD_NOT_FOUND', '금칙어를 찾을 수 없어요.', { status: 404 });
  state.bannedWords.splice(index, 1);
  return structuredClone({ items: state.bannedWords });
}
