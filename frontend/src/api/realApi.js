// 실제 백엔드 API. src/mocks/*MockApi.js와 함수 이름 · 인자 · 응답 형식이 같다 (docs/api-specification.md)
// 화면은 api 객체로 주입받아 쓰므로, 목 ↔ 실제 전환은 VITE_API_MODE로 한다 (src/api/index.js).
import { API_BASE_URL } from './config.js';
import { get, logout, post, reissueAccessToken } from './client.js';

const experience = (caseId) => `/cases/${encodeURIComponent(caseId)}/experience`;

// API 1 — 사건 목록 (crimeType: MURDER | FRAUD | INJURY, 없으면 전체)
export const getCases = (crimeType) =>
  get(crimeType === undefined ? '/cases' : `/cases?crimeType=${encodeURIComponent(crimeType)}`);

// API 16 — 공개 사건 상세 (S-20 사건 디테일. 체험 없이 부를 수 있고, 쿠키는 읽기만 한다)
export const getCaseDetail = (caseId) => get(`/cases/${encodeURIComponent(caseId)}`);

// API 2 — 체험 시작 (있으면 기존 체험, 쿠키가 없으면 익명 ID 쿠키를 내려준다)
export const startExperience = (caseId) => post(experience(caseId));

// API 3 — 내 체험 상태 (S-09 리뷰 페이지 진입 확인. 체험이 없으면 EXPERIENCE_NOT_FOUND)
export const getExperience = (caseId) => get(experience(caseId));

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

// 성향 API P-1 ~ P-5 (명세 9장) — 문항 · 응답 제출 · 내 결과 · 공유용 유형 · 마이페이지. 응답 제출(P-2)만 익명 ID 쿠키를 새로 발급한다
// 내 결과가 없으면 P-3은 404 PERSONALITY_RESULT_NOT_FOUND이고, 마이페이지(P-5)는 기록이 없어도 빈 200이다
export const getPersonalityQuestions = () => get('/personality/questions');
export const submitPersonality = (answers) => post('/personality/results', { answers });
export const getMyPersonality = () => get('/personality/results/me');
export const getPersonalityType = (shareId) => get(`/personality/types/${encodeURIComponent(shareId)}`);
export const getMyPage = () => get('/my-page');

// 회원 로그인 (FE-74, API 명세 10장). 목(mocks/authMockApi.js)과 같은 login · logout 이름을 쓴다
// 카카오(M-3)는 fetch가 아니라 브라우저 이동이다. 카카오 → 서버 콜백(M-4) → 화면 /auth/callback으로 돌아온다(authCallbackPage.js).
// 이동하는 동안 끝나지 않는 약속을 돌려준다. 카카오 화면에서 뒤로 와 이 화면이 그대로 복원되면(bfcache) LOGIN_CANCELLED로 끝내 버튼을 되살린다
// 아이디 · 비밀번호 회원 로그인은 없다(관리자만, adminLogin.js)
export function login({ method } = {}) {
  if (method !== 'kakao') return Promise.reject({ code: 'VALIDATION_ERROR', message: '입력값을 확인해 주세요.', currentStatus: null, details: { field: 'method' } });
  return new Promise((resolve, reject) => {
    const restored = (event) => {
      if (!event.persisted) return;
      window.removeEventListener('pageshow', restored);
      reject({ code: 'LOGIN_CANCELLED', message: '', currentStatus: null, details: null });
    };
    window.addEventListener('pageshow', restored);
    window.location.assign(`${API_BASE_URL}/auth/oauth2/authorization/kakao`);
  });
}

// M-1 · M-2 — 재발급(콜백 화면이 첫 액세스 토큰을 받는다) · 로그아웃
export const reissueToken = () => reissueAccessToken();
export { logout };
