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
import { renderComparisonPage } from '../pages/comparison/comparisonPage.js';
import { renderCourtPage } from '../pages/court/courtPage.js';
import { renderLandingPage } from '../pages/landing/landingPage.js';
import { renderOverviewPage } from '../pages/overview/overviewPage.js';
import { renderReviewPage } from '../pages/review/reviewPage.js';
import { renderSummaryPage } from '../pages/summary/summaryPage.js';
import { renderVerdictPage } from '../pages/verdict/verdictPage.js';
import { renderNotFoundPage } from './notFoundPage.js';

export const routes = [
  { name: 'landing', path: '/', render: renderLandingPage, ownsFooter: true, title: '내Law남불' },
  { name: 'list', path: '/cases', render: renderCaseListPage, ownsFooter: true, title: '사건 목록' },
  { name: 'overview', path: '/cases/:caseId/start', render: renderOverviewPage, title: '사건 개요' },
  { name: 'review', path: '/cases/:caseId/review', render: renderReviewPage, needsHeader: true, title: '사건 정보 확인' },
  { name: 'summary', path: '/cases/:caseId/summary', render: renderSummaryPage, needsHeader: true, title: '판결 전 최종 정리' },
  { name: 'verdict', path: '/cases/:caseId/verdict', render: renderVerdictPage, needsHeader: true, title: '판결 입력' },
  { name: 'ai', path: '/cases/:caseId/result/ai', render: renderAiPage, needsHeader: true, title: 'AI 판결' },
  { name: 'court', path: '/cases/:caseId/result/court', render: renderCourtPage, needsHeader: true, title: '실제 판결' },
  { name: 'comparison', path: '/cases/:caseId/result/compare', render: renderComparisonPage, needsHeader: true, title: '세 판결 비교' },
  // S-14: 주소가 없다. 맞는 화면이 없는 주소거나 없는 사건일 때 보여 준다 (라우터 showNotFound · errorRedirect CASE_NOT_FOUND)
  { name: 'notFound', path: null, render: renderNotFoundPage, title: '찾을 수 없어요' },
];
