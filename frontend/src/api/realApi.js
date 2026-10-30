// 실제 백엔드 API. src/mocks/*MockApi.js와 함수 이름 · 인자 · 응답 형식이 같다 (docs/api-specification.md)
// 화면은 api 객체로 주입받아 쓰므로, 목 ↔ 실제 전환은 VITE_API_MODE로 한다 (src/api/index.js).
import { API_BASE_URL } from './config.js';
import { get, logout, patch, post, put, reissueAccessToken } from './client.js';
import { completeLogin } from './authFlow.js';

const experience = (caseId) => `/cases/${encodeURIComponent(caseId)}/experience`;

// API 1 — 사건 목록 (crimeType: MURDER | FRAUD | INJURY | ROBBERY | THEFT, 없으면 전체)
export const getCases = (crimeType) =>
  get(crimeType === undefined ? '/cases' : `/cases?crimeType=${encodeURIComponent(crimeType)}`);

// API 16 — 공개 사건 상세 (S-20 사건 디테일. 체험 없이 부를 수 있고, 쿠키는 읽기만 한다)
export const getCaseDetail = (caseId) => get(`/cases/${encodeURIComponent(caseId)}`);

// API 17 — 공개 신문 지면 (S-20 기사 지면 · 증거조사. FE-64). 헤드라인 · 본문 · 발단 · 결론 사진(extraPhotos.origin · conclusion, 고정 자리)
export const getCasePaper = (caseId) => get(`/cases/${encodeURIComponent(caseId)}/paper`);

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
export const submitPersonality = (answers, { baseline = false } = {}) => post('/personality/results', baseline ? { answers, baseline } : { answers }); // baseline: 성향카드 다시 정하기(명세 v0.50)
export const getMyPersonality = () => get('/personality/results/me');
export const getPersonalityType = (shareId) => get(`/personality/types/${encodeURIComponent(shareId)}`);
export const getMyPage = () => get('/my-page');

// 출석 기록 P-6 (BE-97) — 사이트에 들어올 때 한 번 부른다. 항상 204이고, 익명 ID 쿠키가 없으면 서버가 아무것도 하지 않는다(발급도 안 한다)
export const postAttendance = () => post('/attendance');
// 프로필 꾸미기 저장 P-7 (BE-96) — 꾸미기 전체(대표 이미지 · 테두리 · 배경 · 카드 책갈피 · 책갈피 캐릭터 · 리본 색)를 한 번에 보낸다.
// 성공하면 저장된 꾸미기를 돌려주고, 쿠키가 없었으면 익명 ID가 발급된다. 400 VALIDATION_ERROR · 409 DECO_LOCKED는 details에 필드별 사유가 온다
export const putProfileDeco = (deco) => put('/profile-deco', deco);

// P-8 — 프로필 선택 설정 부분 저장(FE-81, BE-104). 보낸 칸만 바꾸고, 응답은 세 값 전체 { shownCard, profileCardType, bio }
export const patchProfileSettings = (settings) => patch('/profile-settings', settings);

// 커뮤니티 C-1 ~ C-7 (BE-115, 명세 12장, FE-137). 목(mocks/communityMockApi.js)과 같은 이름 · 인자 · 응답 모양이다.
// 읽기는 누구나, 글쓰기 · 좋아요 · 댓글은 로그인한 회원만(401 UNAUTHORIZED — 목의 임시 코드 LOGIN_REQUIRED를 이 코드로 맞췄다)
// 사진 올리기는 서버에 아직 없어(후속) images가 비어 있지 않으면 400 VALIDATION_ERROR(details images)다 — 화면은 실제 모드에서 사진 칸을 숨긴다
const community = (postId) => `/community/posts/${encodeURIComponent(postId)}`;
// 값이 있는 쿼리만 붙인다(category · caseId는 없으면 전체, sort는 LATEST · LIKES · COMMENTS, page는 1부터 — 첫 글부터 page쪽 끝까지 내려 준다)
export function getCommunityPosts({ category, caseId, sort, page } = {}) {
  const query = new URLSearchParams();
  if (category) query.set('category', category);
  if (caseId) query.set('caseId', String(caseId));
  if (sort) query.set('sort', sort);
  if (page && page > 1) query.set('page', String(page));
  const text = query.toString();
  return get(text ? `/community/posts?${text}` : '/community/posts');
}
export const getCommunityPost = (postId) => get(community(postId));
export const createCommunityPost = (body) => post('/community/posts', body);
export const toggleCommunityLike = (postId) => post(`${community(postId)}/like`);
export const createCommunityComment = (postId, body) => post(`${community(postId)}/comments`, body);
export const getCommunityHome = () => get('/community/home');
// 글에 붙일 수 있는 내 판결. 판결을 확정하기 전이면 204라 null이다(client.js)
export const getMyShareableVerdict = () => get('/community/shareable-verdict');

// 회원 정보 M-8 · 닉네임 설정 M-7 (BE-139, 명세 10장). 로그인한 회원만.
// getMe: { nickname(없으면 null), displayName(없으면 '방청객 {회원 ID}'), provider: KAKAO | LOCAL, loginId(카카오는 null), createdAt, lastLoginAt }
// putNickname: 200 { nickname } · 400 VALIDATION_ERROR(details nickname) · 409 DUPLICATE_NICKNAME(대소문자 무관 중복)
export const getMe = () => get('/members/me');
export const putNickname = (nickname) => put('/members/me/nickname', { nickname });

// 레벨 · 직업 L-1 · L-2 (BE-106, 명세 11장). 응답은 둘 다 { level, xp, currentLevelXp, nextLevelXp, job: { code, name }, jobs: [...] }.
// 마이페이지(P-5)의 level도 같은 모양이다. 아직 안 열린 직업을 고르면 409 JOB_LOCKED
export const getLevel = () => get('/level');
export const putLevelJob = (job) => put('/level/job', { job });

// 회원 로그인 (FE-74 · FE-117, API 명세 10장). 목(mocks/authMockApi.js)과 같은 login · signup · logout 이름을 쓴다
// - 카카오(M-3)는 fetch가 아니라 브라우저 이동이다. 카카오 → 서버 콜백(M-4) → 화면 /auth/callback으로 돌아온다(authCallbackPage.js).
//   이동하는 동안 끝나지 않는 약속을 돌려준다. 카카오 화면에서 뒤로 와 이 화면이 그대로 복원되면(bfcache) LOGIN_CANCELLED로 끝내 버튼을 되살린다
// - 아이디(M-6, BE-113): 서버는 리프레시 토큰 쿠키(NLNB_RT)만 심고 { nickname }을 준다. 카카오 콜백과 같이 바로 M-1 재발급으로
//   첫 액세스 토큰을 받는다. 실패는 401 INVALID_CREDENTIALS(아이디 없음 · 비밀번호 틀림 · 형식 틀림을 나누지 않음) ·
//   400 VALIDATION_ERROR(빈 값 · 길이 초과) · 403 FORBIDDEN(Origin) · 429 TOO_MANY_REQUESTS · 재발급 실패 TOKEN_NOT_ISSUED(api/authFlow.js)
// - 관리자(@가 있는 아이디)는 여기로 오지 않는다(loginPage.js가 adminLogin.js로 보낸다, FE-117)

// 아이디 가입 · 로그인 응답 뒤 M-1 재발급으로 첫 액세스 토큰을 받는다(api/authFlow.js).
// 재발급이 실패하면 남은 리프레시 쿠키를 로그아웃으로 지우고 TOKEN_NOT_ISSUED로 알린다
const withAccessToken = (pending) => completeLogin(pending, { reissue: reissueAccessToken, logout });

export function login({ method, id, password } = {}) {
  if (method === 'password') return withAccessToken(post('/auth/login', { id, password }));
  if (method !== 'kakao') return Promise.reject({ code: 'VALIDATION_ERROR', message: '입력값을 확인해 주세요.', currentStatus: null, details: [{ field: 'method', reason: 'VALIDATION_ERROR' }] });
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

// M-5 아이디 회원가입 (BE-113 · FE-117). 가입하면 바로 로그인된다(응답 · 쿠키는 아이디 로그인과 같다).
// 중복 아이디는 409 DUPLICATE_ID, 중복 닉네임은 409 DUPLICATE_NICKNAME(BE-139, 대소문자 무관), 형식 오류는 400 VALIDATION_ERROR(details에 칸 이름), Origin 검증 실패 403, 요청 제한 429
// agreed: 이용약관 · 개인정보처리방침 동의(BE-133). 서버는 true만 받고, 없거나 false면 400(details agreed). 동의 시각 · 약관 버전은 서버가 남긴다
export const signup = ({ id, password, nickname, agreed }) => withAccessToken(post('/auth/signup', { id, password, nickname, agreed: agreed === true }));

// M-1 · M-2 — 재발급(콜백 화면이 첫 액세스 토큰을 받는다) · 로그아웃
export const reissueToken = () => reissueAccessToken();
export { logout };
