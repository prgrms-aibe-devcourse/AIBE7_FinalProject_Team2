// 관리자 왼쪽 메뉴. route는 app/routes.js의 이름, key는 각 화면이 renderAdminShell에 넘기는 section 값이다.
export const ADMIN_MENU = [
  { group: '개요', items: [{ key: 'dashboard', label: '대시보드', route: 'adminDashboard' }] },
  { group: '사건', items: [{ key: 'cases', label: '사건 검수', route: 'admin' }, { key: 'register', label: '사건 등록', route: 'adminRegister' }] },
  { group: '커뮤니티', items: [{ key: 'members', label: '회원 관리', route: 'adminMembers' }, { key: 'posts', label: '게시글 · 댓글', route: 'adminPosts' }, { key: 'reports', label: '신고 관리', route: 'adminReports' }] },
  { group: '시스템', items: [{ key: 'accounts', label: '관리자 계정', route: 'adminAccounts' }, { key: 'notices', label: '공지 · 설정', route: 'adminNotices' }] },
];
