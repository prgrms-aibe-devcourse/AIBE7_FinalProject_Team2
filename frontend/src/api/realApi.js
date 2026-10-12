// 실제 백엔드 API. src/mocks/*MockApi.js와 함수 이름 · 인자 · 응답 형식이 같다 (docs/api-specification.md)
// 화면은 api 객체로 주입받아 쓰므로, 목 ↔ 실제 전환은 VITE_API_MODE로 한다 (src/api/index.js).
import { get, post } from './client.js';

const experience = (caseId) => `/cases/${encodeURIComponent(caseId)}/experience`;

// API 1 — 사건 목록 (crimeType: MURDER | FRAUD | INJURY, 없으면 전체)
export const getCases = (crimeType) =>
  get(crimeType === undefined ? '/cases' : `/cases?crimeType=${encodeURIComponent(crimeType)}`);

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
