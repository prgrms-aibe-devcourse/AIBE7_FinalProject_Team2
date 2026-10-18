// 화면 표 (IA 4장 "화면 목록"의 주소와 같다). 새 화면은 여기에 한 줄 추가한다.
//
//   name        navigate(name, { caseId })에 쓰는 화면 이름. utils/screenByStatus.js의 값과 같다
//   path        주소. :caseId는 사건 번호
//   render      화면 진입 함수 renderXxxPage(container, { caseId, caseHeader, api, navigate, section })
//   needsHeader 사건 상단 바(제목 · 유형)가 필요한 화면 — 라우터가 caseHeader를 채워서 넘긴다
//   ownsFooter  화면이 공통 푸터를 직접 그리면 true (아니면 라우터가 화면 아래에 푸터를 붙인다. REQ-071 모든 화면 법적 고지)
//   title       브라우저 탭 제목
import { renderAiPage } from '../pages/ai/aiPage.js';
import { renderCaseListPage } from '../pages/caseList/caseListPage.js';
import { renderCaseDetailPage } from '../pages/caseDetail/caseDetailPage.js';
import { renderComparisonPage } from '../pages/comparison/comparisonPage.js';
import { renderCourtPage } from '../pages/court/courtPage.js';
import { renderLandingPage } from '../pages/landing/landingPage.js';
import { renderMyPage } from '../pages/myPage/myPage.js';
import { renderOverviewPage } from '../pages/overview/overviewPage.js';
import { renderPersonalityPage } from '../pages/personality/personalityPage.js';
import { renderPersonalityResultPage } from '../pages/personality/personalityResultPage.js';
import { renderPersonalitySharedPage } from '../pages/personality/personalitySharedPage.js';
import { renderReviewPage } from '../pages/review/reviewPage.js';
import { renderSummaryPage } from '../pages/summary/summaryPage.js';
import { renderVerdictPage } from '../pages/verdict/verdictPage.js';
import { renderNotFoundPage } from './notFoundPage.js';

export const routes = [
  { name: 'landing', path: '/', render: renderLandingPage, ownsFooter: true, title: '내Law남불' },
  { name: 'list', path: '/cases', render: renderCaseListPage, ownsFooter: true, title: '사건 목록' },
  // S-20 사건 디테일(확장, IA v1.12): 신문에서 고른 기사를 펼쳐 놓은 화면. 체험 전에도 열린다(API 16)
  { name: 'caseDetail', path: '/cases/:caseId', render: renderCaseDetailPage, title: '사건 정보' },
  { name: 'overview', path: '/cases/:caseId/start', render: renderOverviewPage, title: '사건 개요' },
  { name: 'review', path: '/cases/:caseId/review', render: renderReviewPage, needsHeader: true, title: '사건 정보 확인' },
  { name: 'summary', path: '/cases/:caseId/summary', render: renderSummaryPage, needsHeader: true, title: '판결 전 최종 정리' },
  { name: 'verdict', path: '/cases/:caseId/verdict', render: renderVerdictPage, needsHeader: true, title: '판결 입력' },
  { name: 'ai', path: '/cases/:caseId/result/ai', render: renderAiPage, needsHeader: true, title: 'AI 판결' },
  { name: 'court', path: '/cases/:caseId/result/court', render: renderCourtPage, needsHeader: true, title: '실제 판결' },
  { name: 'comparison', path: '/cases/:caseId/result/compare', render: renderComparisonPage, needsHeader: true, title: '세 판결 비교' },
  // 확장: 판결 성향 테스트 · 마이페이지 (S-15 ~ S-17). 공유된 결과(/test/types/{shareId}, 해시 주소)는 유형 설명만 보여 준다(FR-8-6)
  { name: 'test', path: '/test', render: renderPersonalityPage, title: '판결 성향 테스트' },
  { name: 'testResult', path: '/test/result', render: renderPersonalityResultPage, title: '성향 결과' },
  { name: 'testType', path: '/test/types/:shareId', render: renderPersonalitySharedPage, title: '판결 성향 유형' },
  { name: 'me', path: '/me', render: renderMyPage, title: '마이페이지' },
  // 확장: S-19 관리자 검수 (FE-16). 관리자 코드는 사용자 화면 번들에 넣지 않으려고 필요할 때만 불러온다.
  // 관리자 화면은 관리자 api를 따로 쓰고(pages/admin/adminApi.js), 사용자 사이트 푸터 대신 자기 틀을 그린다
  { name: 'admin', path: '/admin', render: (container, options) => import('../pages/admin/adminListPage.js').then((m) => m.renderAdminListPage(container, options)), ownsFooter: true, title: '관리자' },
  { name: 'adminRegister', path: '/admin/register', render: (container, options) => import('../pages/admin/adminRegisterPage.js').then((m) => m.renderAdminRegisterPage(container, options)), ownsFooter: true, title: '관리자' },
  // 관리자 운영 메뉴 (FE-45): 대시보드 · 회원 · 게시글 · 신고 · 관리자 계정 · 공지. 서버 API가 생기기 전에는 목으로 동작한다
  { name: 'adminDashboard', path: '/admin/dashboard', render: (container, options) => import('../pages/admin/adminDashboardPage.js').then((m) => m.renderAdminDashboardPage(container, options)), ownsFooter: true, title: '관리자' },
  { name: 'adminMembers', path: '/admin/members', render: (container, options) => import('../pages/admin/adminMembersPage.js').then((m) => m.renderAdminMembersPage(container, options)), ownsFooter: true, title: '관리자' },
  { name: 'adminPosts', path: '/admin/posts', render: (container, options) => import('../pages/admin/adminPostsPage.js').then((m) => m.renderAdminPostsPage(container, options)), ownsFooter: true, title: '관리자' },
  { name: 'adminReports', path: '/admin/reports', render: (container, options) => import('../pages/admin/adminReportsPage.js').then((m) => m.renderAdminReportsPage(container, options)), ownsFooter: true, title: '관리자' },
  { name: 'adminAccounts', path: '/admin/accounts', render: (container, options) => import('../pages/admin/adminAccountsPage.js').then((m) => m.renderAdminAccountsPage(container, options)), ownsFooter: true, title: '관리자' },
  { name: 'adminNotices', path: '/admin/notices', render: (container, options) => import('../pages/admin/adminNoticesPage.js').then((m) => m.renderAdminNoticesPage(container, options)), ownsFooter: true, title: '관리자' },
  { name: 'adminCase', path: '/admin/cases/:caseId', render: (container, options) => import('../pages/admin/adminCasePage.js').then((m) => m.renderAdminCasePage(container, options)), ownsFooter: true, title: '관리자' },
  // S-14: 주소가 없다. 맞는 화면이 없는 주소거나 없는 사건일 때 보여 준다 (라우터 showNotFound · errorRedirect CASE_NOT_FOUND)
  { name: 'notFound', path: null, render: renderNotFoundPage, title: '찾을 수 없어요' },
];
