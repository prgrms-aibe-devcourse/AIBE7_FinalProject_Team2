// 화면 표 (IA 4장 "화면 목록"의 주소와 같다). 새 화면은 여기에 한 줄 추가한다.
//
//   name        navigate(name, { caseId })에 쓰는 화면 이름. utils/screenByStatus.js의 값과 같다
//   path        주소. :caseId는 사건 번호
//   render      화면 진입 함수 renderXxxPage(container, { caseId, caseHeader, api, navigate, section })
//   needsHeader 사건 상단 바(제목 · 유형)가 필요한 화면 — 라우터가 caseHeader를 채워서 넘긴다
//   ownsFooter  화면이 공통 푸터를 직접 그리면 true (아니면 라우터가 화면 아래에 푸터를 붙인다. REQ-071 모든 화면 법적 고지)
//   ownsHeader  공통 헤더(상단 내비)를 라우터가 붙이지 않는 화면 — 관리자처럼 자기 헤더를 그리는 화면만(FE-56, 마이페이지는 FE-66부터 공통 헤더를 쓴다)
//   theme       화면의 색 테마 이름 — 라우터가 화면을 그리기 시작하는 순간 공통 헤더와 body에 data-theme으로 붙인다(마이페이지: coffee, FE-66)
//   title       브라우저 탭 제목
import { renderAiPage } from '../pages/ai/aiPage.js';
import { renderCaseListPage } from '../pages/caseList/caseListPage.js';
import { renderCaseDetailPage } from '../pages/caseDetail/caseDetailPage.js';
import { renderCaseReviewPage } from '../pages/caseReview/caseReviewPage.js';
import { renderCommunityHomePage } from '../pages/community/communityHomePage.js';
import { renderCommunityPage } from '../pages/community/communityPage.js';
import { renderCommunityPostPage } from '../pages/community/communityPostPage.js';
import { renderCommunityWritePage } from '../pages/community/communityWritePage.js';
import { renderComparisonPage } from '../pages/comparison/comparisonPage.js';
import { renderCourtPage } from '../pages/court/courtPage.js';
import { renderLandingPage } from '../pages/landing/landingPage.js';
import { renderAuthCallbackPage } from '../pages/login/authCallbackPage.js';
import { renderLoginPage } from '../pages/login/loginPage.js';
import { renderSignupPage } from '../pages/login/signupPage.js';
import { renderMyPage } from '../pages/myPage/myPage.js';
import { renderMyResultPage } from '../pages/myPage/myResultPage.js';
import { renderOverviewPage } from '../pages/overview/overviewPage.js';
import { renderPersonalityPage } from '../pages/personality/personalityPage.js';
import { renderPersonalityPaperResultPage } from '../pages/personality/personalityPaperResultPage.js';
import { renderPersonalityPaperPage } from '../pages/personality/personalityPaperPage.js';
import { renderPersonalityResultPage } from '../pages/personality/personalityResultPage.js';
import { renderPersonalitySharedPage } from '../pages/personality/personalitySharedPage.js';
import { renderReviewPage } from '../pages/review/reviewPage.js';
import { renderVerdictPage } from '../pages/verdict/verdictPage.js';
import { renderPrivacyPage, renderTermsPage } from './legalPage.js';
import { renderNotFoundPage } from './notFoundPage.js';

export const routes = [
  { name: 'landing', path: '/', render: renderLandingPage, ownsFooter: true, title: '내Law남불' },
  { name: 'list', path: '/cases', render: renderCaseListPage, ownsFooter: true, title: '사건 목록' },
  // S-20 사건 디테일(확장, IA v1.12): 신문에서 고른 기사를 펼쳐 놓은 화면. 체험 전에도 열린다(API 16)
  { name: 'caseDetail', path: '/cases/:caseId', render: renderCaseDetailPage, title: '사건 정보' },
  // 판결 체험 6막 (BE-82 주소 · 화면 ID). 옛 주소는 라우터가 새 주소로 옮긴다(legacyPaths.js)
  { name: 'overview', path: '/cases/:caseId/impression', render: renderOverviewPage, title: '첫인상' },
  { name: 'review', path: '/cases/:caseId/trial', render: renderReviewPage, needsHeader: true, title: '재판' },
  { name: 'verdict', path: '/cases/:caseId/verdict', render: renderVerdictPage, needsHeader: true, title: '판결 입력' },
  { name: 'ai', path: '/cases/:caseId/ai-verdict', render: renderAiPage, needsHeader: true, title: 'AI 판결' },
  { name: 'court', path: '/cases/:caseId/court-verdict', render: renderCourtPage, needsHeader: true, title: '실제 판결' },
  { name: 'comparison', path: '/cases/:caseId/compare', render: renderComparisonPage, needsHeader: true, title: '세 판결 비교' },
  // S-09 리뷰 페이지 · 독자 엽서 (FE-55, 화면 ID는 BE-82): 6막 세 판결 비교 마지막 대사창의 "엽서 쓰기"로 들어오는 독자 엽서(별점 + 내 의견). 리뷰 API가 없어 목 모드에서만 동작한다
  { name: 'caseReview', path: '/cases/:caseId/letters', render: renderCaseReviewPage, title: '독자 엽서' },
  // 확장: 판결 성향 테스트 · 마이페이지 (S-15 ~ S-17). 공유된 결과(/test/types/{shareId}, 해시 주소)는 유형 설명만 보여 준다(FR-8-6)
  { name: 'test', path: '/test', render: renderPersonalityPage, title: '판결 성향 테스트' },
  // FE-55: 6막 신문의 성향 테스트 광고에서 신문을 넘겨 들어오는 오피니언 면(문항 · 결과는 /test와 같다)
  { name: 'testPaper', path: '/test/paper', render: renderPersonalityPaperPage, ownsFooter: true, title: '판결 성향 테스트' }, // 법적 고지는 지면 안 맨 아래(FE-122)
  // 신문 성향 테스트 결과 (FE-126): 신문 테스트를 마치면 오는 지면. 내용은 /test/result와 같고 모양만 신문이다
  { name: 'testPaperResult', path: '/test/paper/result', render: renderPersonalityPaperResultPage, ownsFooter: true, title: '판결 성향 결과' },
  { name: 'testResult', path: '/test/result', render: renderPersonalityResultPage, title: '판결 성향 결과' },
  { name: 'testType', path: '/test/types/:shareId', render: renderPersonalitySharedPage, title: '판결 성향 유형' },
  // 마이페이지도 공통 헤더를 쓰고 색만 커피색으로 덮어쓴다(FE-66, myPage.css)
  { name: 'me', path: '/me', render: renderMyPage, theme: 'coffee', title: '마이페이지' },
  // 내 결과 (FE-75): 마이페이지 "내 결과 보기". 6막 세 판결 비교 지면 모양에 내 판단 · 내 성향을 싣는다(읽기 전용, 마친 체험만)
  { name: 'myResult', path: '/me/cases/:caseId', render: renderMyResultPage, needsHeader: true, title: '내 결과' },
  // S-18 커뮤니티 (FE-47): 메인 책상의 노트북으로 들어온다. 커뮤니티 API가 없어 목 모드에서만 동작한다(실제 모드는 "준비 중")
  // /community는 메인(이야기 많은 사건 · 레벨 랭킹 · 추천 사건, FE-89), 글 목록은 /community/posts
  { name: 'community', path: '/community', render: renderCommunityHomePage, title: '커뮤니티' },
  { name: 'communityPosts', path: '/community/posts', render: renderCommunityPage, title: '커뮤니티 글 목록' },
  { name: 'communityWrite', path: '/community/write', render: renderCommunityWritePage, title: '커뮤니티 글쓰기' },
  { name: 'communityPost', path: '/community/posts/:postId', render: renderCommunityPostPage, title: '커뮤니티' },
  // S-12 로그인 (FE-52): 목 모드는 개발용 목업 로그인, 실제 모드는 카카오 로그인(FE-74)
  { name: 'login', path: '/login', render: renderLoginPage, title: '로그인' },
  // 회원가입 (FE-104): 목 모드는 목업 가입, 실제 모드는 서버 API가 생길 때까지 카카오로 시작하기만 된다
  { name: 'signup', path: '/signup', render: renderSignupPage, title: '회원가입' },
  // 카카오 로그인 콜백 (FE-74): 서버(M-4)가 로그인을 마치고 보내는 주소. 주소는 서버 설정과 같아야 한다
  { name: 'authCallback', path: '/auth/callback', render: renderAuthCallbackPage, title: '로그인' },
  // 확장: S-19 관리자 검수 (FE-16). 관리자 코드는 사용자 화면 번들에 넣지 않으려고 필요할 때만 불러온다.
  // 관리자 화면은 관리자 api를 따로 쓰고(pages/admin/adminApi.js), 사용자 사이트 푸터 대신 자기 틀을 그린다
  { name: 'admin', path: '/admin', render: (container, options) => import('../pages/admin/adminListPage.js').then((m) => m.renderAdminListPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  { name: 'adminRegister', path: '/admin/register', render: (container, options) => import('../pages/admin/adminRegisterPage.js').then((m) => m.renderAdminRegisterPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  // 관리자 운영 메뉴 (FE-45): 대시보드 · 회원 · 게시글 · 신고 · 관리자 계정 · 공지. 서버 API가 생기기 전에는 목으로 동작한다
  { name: 'adminDashboard', path: '/admin/dashboard', render: (container, options) => import('../pages/admin/adminDashboardPage.js').then((m) => m.renderAdminDashboardPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  { name: 'adminMembers', path: '/admin/members', render: (container, options) => import('../pages/admin/adminMembersPage.js').then((m) => m.renderAdminMembersPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  { name: 'adminPosts', path: '/admin/posts', render: (container, options) => import('../pages/admin/adminPostsPage.js').then((m) => m.renderAdminPostsPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  { name: 'adminReports', path: '/admin/reports', render: (container, options) => import('../pages/admin/adminReportsPage.js').then((m) => m.renderAdminReportsPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  { name: 'adminAccounts', path: '/admin/accounts', render: (container, options) => import('../pages/admin/adminAccountsPage.js').then((m) => m.renderAdminAccountsPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  { name: 'adminNotices', path: '/admin/notices', render: (container, options) => import('../pages/admin/adminNoticesPage.js').then((m) => m.renderAdminNoticesPage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  { name: 'adminCase', path: '/admin/cases/:caseId', render: (container, options) => import('../pages/admin/adminCasePage.js').then((m) => m.renderAdminCasePage(container, options)), ownsFooter: true, ownsHeader: true, title: '관리자' },
  // S-10 이용약관 · S-11 개인정보처리방침 (FE-62): 메인 맨 아래 입구. 조항이 정해질 때까지 "준비 중" 틀이다
  { name: 'terms', path: '/terms', render: renderTermsPage, title: '이용약관' },
  { name: 'privacy', path: '/privacy', render: renderPrivacyPage, title: '개인정보처리방침' },
  // S-14: 주소가 없다. 맞는 화면이 없는 주소거나 없는 사건일 때 보여 준다 (라우터 showNotFound · errorRedirect CASE_NOT_FOUND)
  { name: 'notFound', path: null, render: renderNotFoundPage, title: '찾을 수 없어요' },
];
