// 실제 백엔드 API. src/mocks/*MockApi.js와 함수 이름 · 인자 · 응답 형식이 같다 (docs/api-specification.md)
// 화면은 api 객체로 주입받아 쓰므로, 목 ↔ 실제 전환은 VITE_API_MODE로 한다 (src/api/index.js).
import { get, post } from './client.js';

const experience = (caseId) => `/cases/${encodeURIComponent(caseId)}/experience`;

// API 1 — 사건 목록 (crimeType: MURDER | FRAUD | INJURY, 없으면 전체)
export const getCases = (crimeType) =>
  get(crimeType === undefined ? '/cases' : `/cases?crimeType=${encodeURIComponent(crimeType)}`);

// API 16 — 공개 사건 상세 (S-20 사건 디테일. 체험 없이 부를 수 있고, 쿠키는 읽기만 한다)
export const getCaseDetail = (caseId) => get(`/cases/${encodeURIComponent(caseId)}`);

// API 2 — 체험 시작 (있으면 기존 체험, 쿠키가 없으면 익명 ID 쿠키를 내려준다)
export const startExperience = (caseId) => post(experience(caseId));

// API 4 · 5 — 사건 개요 · 사전 판단 제출
export const getOverview = (caseId) => get(`${experience(caseId)}/overview`);
export const postPreJudgment = (caseId, body) => post(`${experience(caseId)}/pre-judgment`, body);

// API 6 · 7 — 사건 정보, 섹션 확인 기록
export const getReview = (caseId) => get(`${experience(caseId)}/review`);
export const postReviewStep = (caseId, step) => post(`${experience(caseId)}/review-steps`, { step });

// API 8 · 9 — 판결 입력 정보, 판결 제출
export const getVerdictForm = (caseId) => get(`${experience(caseId)}/verdict-form`);
export const postVerdict = (caseId, body) => post(`${experience(caseId)}/verdict`, body);

// API 10 ~ 14 — AI 판결, 실제 판결 공개 · 조회, 비교 공개 · 조회
// (백엔드 구현 전에는 404가 온다. 구현되면 이 함수들이 그대로 동작한다)
export const getAiJudgment = (caseId) => get(`${experience(caseId)}/judgments/ai`);
export const postCourtReveal = (caseId) => post(`${experience(caseId)}/court-reveal`);
export const getCourtJudgment = (caseId) => get(`${experience(caseId)}/judgments/court`);
export const postComparisonReveal = (caseId) => post(`${experience(caseId)}/comparison-reveal`);
export const getComparison = (caseId) => get(`${experience(caseId)}/comparison`);

// 성향 API P-1 ~ P-5 (명세 9장). 응답 제출(P-2)만 익명 ID 쿠키를 새로 발급한다
export const getPersonalityQuestions = () => get('/personality/questions');
export const submitPersonality = (answers) => post('/personality/results', { answers });
export const getMyPersonality = () => get('/personality/results/me');
export const getPersonalityType = (shareId) => get(`/personality/types/${encodeURIComponent(shareId)}`);
export const getMyPage = () => get('/my-page');
