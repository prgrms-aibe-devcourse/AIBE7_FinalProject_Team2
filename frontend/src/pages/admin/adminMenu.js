import { API_MODE } from '../../api/config.js';

// 관리자 왼쪽 메뉴. route는 app/routes.js의 이름, key는 각 화면이 renderAdminShell에 넘기는 section 값이다.
// serverReady: false는 서버 API가 아직 없는 화면(운영 메뉴 A-16 ~ A-41)이다. 목 모드에서는 그대로 열리고,
// 실제 모드(VITE_API_MODE=real)에서는 "준비 중"으로 두어 누르면 404만 나는 일을 막는다. 서버가 생기면 그 항목의 serverReady만 지운다.
// 사건 등록(A-14 · A-15 · A-14-1)은 BE-93 1차 · 2차가 모두 머지되고 화면이 적재 · caseId 없는 상태를 다루게 되어 열었다(FE-72).
// 파이프라인 서비스를 켜지 않은 서버에서는 등록을 시도하면 502 IMPORT_UNAVAILABLE 안내가 뜬다(COMMON-50).
export const ADMIN_MENU = [
  { group: '개요', items: [{ key: 'dashboard', label: '대시보드', route: 'adminDashboard', serverReady: false }] },
  { group: '사건', items: [{ key: 'cases', label: '사건 검수', route: 'admin' }, { key: 'register', label: '사건 등록', route: 'adminRegister' }] },
  { group: '커뮤니티', items: [{ key: 'members', label: '회원 관리', route: 'adminMembers', serverReady: false }, { key: 'posts', label: '게시글 · 댓글', route: 'adminPosts', serverReady: false }, { key: 'reports', label: '신고 관리', route: 'adminReports', serverReady: false }] },
  { group: '시스템', items: [{ key: 'accounts', label: '관리자 계정', route: 'adminAccounts', serverReady: false }, { key: 'notices', label: '공지 · 설정', route: 'adminNotices', serverReady: false }] },
];

// 지금 모드에서 누를 수 있는 항목인지. mode는 테스트에서 실제 모드를 흉내 낼 때만 넘긴다
export const isMenuReady = (item, mode = API_MODE) => mode !== 'real' || item.serverReady !== false;

// 화면(section = 메뉴 key)이 지금 모드에서 서버를 불러도 되는지(FE-143). 메뉴에 없는 key는 준비된 것으로 본다
export function isSectionReady(section, mode = API_MODE) {
  const item = ADMIN_MENU.flatMap((group) => group.items).find((entry) => entry.key === section);
  return !item || isMenuReady(item, mode);
}

// 머리줄 로고가 가는 곳: 실제 모드에서는 서버가 있는 사건 검수
export const homeRoute = () => (API_MODE === 'real' ? 'admin' : 'adminDashboard');
