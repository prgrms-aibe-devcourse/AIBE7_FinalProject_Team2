// 관리자 운영 메뉴 API 실제 호출 (API 명세 8-4 A-16 ~ A-41 초안). adminOpsMockApi.js와 함수 이름 · 인자 · 응답 형식이 같다.
// 서버 구현 전이라 404가 온다. 서버를 만드는 쪽은 명세 8-4를 기준으로 하고, 계약이 바뀌면 명세와 목을 함께 고친다.
import { adminDelete, adminGet, adminPatch, adminPost } from './adminClient.js';

const id = (value) => encodeURIComponent(value);

function query(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') search.set(key, String(value)); });
  const text = search.toString();
  return text ? `?${text}` : '';
}

// A-16 대시보드
export const getDashboard = () => adminGet('/dashboard');

// A-17 ~ A-20 회원
export const getMembers = ({ keyword, status, page = 0, size = 20 } = {}) => adminGet(`/members${query({ keyword, status, page, size })}`);
export const getMember = (memberId) => adminGet(`/members/${id(memberId)}`);
export const suspendMember = (memberId, { reason, days }) => adminPost(`/members/${id(memberId)}/suspend`, { reason, days: days ?? null });
export const restoreMember = (memberId) => adminPost(`/members/${id(memberId)}/restore`);

// A-21 ~ A-27 게시글 · 댓글
export const getPosts = ({ keyword, status, reported, page = 0, size = 20 } = {}) => adminGet(`/posts${query({ keyword, status, reported, page, size })}`);
export const getPost = (postId) => adminGet(`/posts/${id(postId)}`);
export const hidePost = (postId, reason) => adminPost(`/posts/${id(postId)}/hide`, { reason });
export const restorePost = (postId) => adminPost(`/posts/${id(postId)}/restore`);
export const deletePost = (postId, reason) => adminPost(`/posts/${id(postId)}/delete`, { reason });
export const hideComment = (commentId) => adminPost(`/comments/${id(commentId)}/hide`);
export const restoreComment = (commentId) => adminPost(`/comments/${id(commentId)}/restore`);

// A-28 ~ A-30 신고
export const getReports = ({ status, targetType, page = 0, size = 20 } = {}) => adminGet(`/reports${query({ status, targetType, page, size })}`);
export const resolveReport = (reportId, { action, note }) => adminPost(`/reports/${id(reportId)}/resolve`, { action, note });
export const rejectReport = (reportId, note) => adminPost(`/reports/${id(reportId)}/reject`, { note });

// A-31 ~ A-35 관리자 계정 · 감사 로그
export const getAccounts = () => adminGet('/accounts');
export const createAccount = ({ email, displayName, role }) => adminPost('/accounts', { email, displayName, role });
export const setAccountActive = (adminId, active) => adminPost(`/accounts/${id(adminId)}/${active ? 'activate' : 'deactivate'}`);
export const getAuditLogs = ({ action, page = 0, size = 20 } = {}) => adminGet(`/audit-logs${query({ action, page, size })}`);

// A-36 ~ A-41 공지 · 금칙어
export const getNotices = () => adminGet('/notices');
export const createNotice = ({ title, body, pinned, published }) => adminPost('/notices', { title, body, pinned: Boolean(pinned), published: Boolean(published) });
export const updateNotice = (noticeId, changes) => adminPatch(`/notices/${id(noticeId)}`, changes);
export const getBannedWords = () => adminGet('/banned-words');
export const addBannedWord = (word) => adminPost('/banned-words', { word });
export const removeBannedWord = (word) => adminDelete(`/banned-words/${id(word)}`);
