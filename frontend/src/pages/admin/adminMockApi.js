// 관리자 API 목 (API 명세 8장 A-1 ~ A-13). 7.4.7 구현 전에 S-19 화면을 만들고 확인하는 용도다.
// 사건 · 원문은 모두 가상 값이다. 상태는 메모리에만 두고(새로고침하면 처음으로), 로그인 여부만 sessionStorage에 둔다.
// 원본 판결문 같은 내부 정보를 브라우저 저장소에 남기지 않기 위해서다.
const delay = () => new Promise((resolve) => setTimeout(resolve, 200));
const SESSION_KEY = 'nlnb-mock-admin-session';
const ADMIN = { adminId: 1, email: 'admin@example.com', displayName: '관리자' };

function fail(code, message, extra = {}) {
  throw { code, message, currentStatus: null, details: null, ...extra };
}

function loggedIn() {
  try { return sessionStorage.getItem(SESSION_KEY) === '1'; } catch { return false; }
}
function requireLogin() {
  if (!loggedIn()) fail('UNAUTHORIZED', '로그인이 필요해요.', { status: 401 });
}

const now = () => new Date().toISOString();

function baseFactors(prefix) {
  return [
    { factorId: prefix + 1, displayOrder: 1, label: '빌린 돈을 갚으라는 말에 화가 나 다툼이 시작됐다', preLabel: '금전 문제로 다투다 일어났다', revealStage: 'OVERVIEW', summaryTag: '금전 갈등', valueAxis: 'FAULT_STANDARD', valueAxisStatus: 'AUTO', valueAxisVotes: { runs: 5, counts: { FAULT_STANDARD: 3, NONE: 2 }, needsReview: false } },
    { factorId: prefix + 2, displayOrder: 2, label: '범행 뒤 스스로 경찰서를 찾아갔다', preLabel: null, revealStage: 'DETAIL', summaryTag: '자수', valueAxis: 'APOLOGY_SINCERITY', valueAxisStatus: 'AUTO', valueAxisVotes: { runs: 5, counts: { APOLOGY_SINCERITY: 5 }, needsReview: false } },
    { factorId: prefix + 3, displayOrder: 3, label: '유족과 합의하지 못했다', preLabel: null, revealStage: 'DETAIL', summaryTag: '합의 없음', valueAxis: null, valueAxisStatus: 'AUTO', valueAxisVotes: { runs: 5, counts: { PRINCIPLE_RELATION: 2, NONE: 3 }, needsReview: true } },
  ];
}

function aiJudgmentBody(prisonMonths) {
  return {
    penaltyType: 'PRISON', reducedTo: null, prisonMonths, fineAmount: null, suspensionMonths: null,
    summary: `징역 ${prisonMonths / 12}년`,
    reasoning: '금전 갈등에서 시작된 우발적 범행으로 보이나 결과가 중대하다. 자수한 점은 유리하게, 유족과 합의하지 못한 점은 불리하게 고려했다.',
    referenceTags: ['형법 제250조', '살인범죄 양형기준'],
    factors: [
      { label: '빌린 돈을 갚으라는 말에 화가 나 다툼이 시작됐다', direction: 'DOWN', evidence: '계획적 범행으로 볼 사정이 없다.' },
      { label: '범행 뒤 스스로 경찰서를 찾아갔다', direction: 'DOWN', evidence: '자수했다.' },
      { label: '유족과 합의하지 못했다', direction: 'UP', evidence: '피해 회복이 이뤄지지 않았다.' },
    ],
  };
}

function aiGeneration(round) {
  return {
    modelName: '(가상 모델)',
    promptVersion: 'v-mock',
    inputSnapshot: { similarPrecedents: ['가상 판례 A', '가상 판례 B'], guideline: '살인범죄 양형기준 (가상 버전)' },
    generationReport: { warnings: round === 1 ? ['참고 판례 1건의 죄명이 다름'] : [], contaminationCheck: '통과', selectedRound: round, selectionReason: '판단 요소 근거가 가장 구체적인 회차' },
  };
}

function judgment(judgmentId, caseId, subjectType, prisonMonths, extra = {}) {
  return {
    judgmentId, caseId, subjectType,
    reviewStatus: 'PENDING', isPublished: false, reviewedBy: null, reviewedAt: null, reviewNote: null,
    judgment: aiJudgmentBody(prisonMonths),
    generation: subjectType === 'AI' ? aiGeneration(judgmentId % 2 + 1) : null,
    createdAt: '2026-10-20T09:00:00+09:00',
    ...extra,
  };
}

const content = (title, extra = {}) => ({
  title, crimeType: 'MURDER', chargeName: '살인',
  shortIntro: '돈 문제로 다투던 지인 사이에서 벌어진 사건이에요.',
  overview: '피고인은 빌려준 돈을 돌려받는 문제로 지인과 다투던 중 범행에 이르렀다. (가상 사건 개요)',
  keywords: ['금전 갈등', '지인'], difficulty: 'MID', estimatedMinutes: 15, thumbnailUrl: null,
  appliedLaw: '형법 제250조 제1항', statutoryPenaltyText: '사형, 무기 또는 5년 이상의 징역',
  recommendedMinMonths: 120, recommendedMaxMonths: 192, recommendedBasis: '살인범죄 양형기준 제2유형 기본 영역 (가상)',
  incidentDate: '2024-03-02', guideline: { guidelineId: 4, versionName: '2023 수정 (가상)' },
  deidentifiedItems: ['인명', '지명', '사건번호'],
  ...extra,
});

const ORIGINAL_TEXT = '가상지방법원\n판결\n\n사건 2099고합000 살인\n피고인 A\n\n주문\n피고인을 징역 12년에 처한다.\n\n이유\n(가상 원문입니다. 실제 판결문이 아닙니다.)';

function createState() {
  return {
    cases: [
      {
        caseId: 12, status: 'DRAFT', content: content('빌린 돈 문제로 찾아온 지인을 살해한 사건'),
        sources: [{ sourceId: 30, courtLevel: 'FIRST', caseNumber: '2099고합000', courtName: '가상지방법원', decidedAt: '2025-01-10', isFinal: true, sourceOrg: '법제처', note: null, hasOriginalText: true }],
        factors: baseFactors(100), penaltyRules: 1,
        createdAt: '2026-10-20T09:00:00+09:00', updatedAt: '2026-10-20T09:00:00+09:00',
      },
      {
        caseId: 13, status: 'PUBLISHED', content: content('층간 소음 다툼 끝에 이웃을 숨지게 한 사건', { shortIntro: '오랜 층간 소음 갈등 끝에 벌어진 사건이에요.', keywords: ['층간 소음', '이웃'] }),
        sources: [{ sourceId: 31, courtLevel: 'FIRST', caseNumber: '2099고합001', courtName: '가상지방법원', decidedAt: '2025-02-14', isFinal: true, sourceOrg: '법제처', note: null, hasOriginalText: true }],
        factors: baseFactors(200).map((factor) => ({ ...factor, valueAxisStatus: 'CONFIRMED' })), penaltyRules: 1,
        createdAt: '2026-10-18T09:00:00+09:00', updatedAt: '2026-10-19T09:00:00+09:00',
      },
    ],
    judgments: [
      judgment(501, 12, 'AI', 180),
      judgment(503, 12, 'AI', 168, { createdAt: '2026-10-20T09:05:00+09:00' }),
      judgment(502, 12, 'COURT', 144),
      judgment(601, 13, 'AI', 156, { reviewStatus: 'APPROVED', isPublished: true, reviewedBy: 'admin@example.com', reviewedAt: '2026-10-19T09:00:00+09:00' }),
      judgment(602, 13, 'COURT', 144, { reviewStatus: 'APPROVED', isPublished: true, reviewedBy: 'admin@example.com', reviewedAt: '2026-10-19T09:00:00+09:00' }),
      judgment(603, 13, 'AI', 168, { createdAt: '2026-10-21T09:00:00+09:00' }),
    ],
  };
}

let state = createState();

/** 목 전용: 처음 상태로 되돌린다 */
export function resetAdminMock() {
  state = createState();
}

const findCase = (caseId) => state.cases.find((item) => item.caseId === Number(caseId)) ?? fail('CASE_NOT_FOUND', '사건을 찾을 수 없어요.', { status: 404 });
const findJudgment = (judgmentId) => state.judgments.find((item) => item.judgmentId === Number(judgmentId)) ?? fail('JUDGMENT_NOT_FOUND', '판결을 찾을 수 없어요.', { status: 404 });
const candidatesOf = (caseId) => state.judgments.filter((item) => item.caseId === caseId);
const reason = (code) => ({ currentStatus: null, details: [{ field: 'case', reason: code }], status: 409 });

function invalidState(item, code) {
  fail('INVALID_STATE', '지금 상태에서는 할 수 없어요.', { ...reason(code), currentStatus: item.status });
}

function touch(item) {
  if (item.status === 'DRAFT') item.status = 'REVIEW'; // 관리자가 처음 바꾸는 순간 DRAFT → REVIEW (8-3 #3)
  item.updatedAt = now();
}

function penaltyLabel(entry) {
  return `징역 ${entry.judgment.prisonMonths / 12}년`;
}

function readiness(item) {
  const list = candidatesOf(item.caseId);
  const reasons = [];
  if (!list.some((entry) => entry.subjectType === 'AI' && entry.isPublished)) reasons.push('NO_PUBLISHED_AI');
  if (!list.some((entry) => entry.subjectType === 'COURT' && entry.isPublished)) reasons.push('NO_PUBLISHED_COURT');
  if (!item.factors.length) reasons.push('NO_FACTORS');
  if (!item.factors.some((factor) => factor.revealStage === 'OVERVIEW')) reasons.push('NO_OVERVIEW_FACTOR');
  if (!item.penaltyRules) reasons.push('NO_PENALTY_RULE');
  return { ready: reasons.length === 0, reasons };
}

function detail(item) {
  const list = candidatesOf(item.caseId).slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const summary = (entry) => ({ judgmentId: entry.judgmentId, reviewStatus: entry.reviewStatus, isPublished: entry.isPublished, penaltyLabel: penaltyLabel(entry), modelName: entry.generation?.modelName ?? null, createdAt: entry.createdAt });
  return structuredClone({
    caseId: item.caseId, status: item.status, content: item.content, sources: item.sources, factors: item.factors,
    candidates: { AI: list.filter((entry) => entry.subjectType === 'AI').map(summary), COURT: list.filter((entry) => entry.subjectType === 'COURT').map(summary) },
    publishReadiness: readiness(item),
  });
}

function judgmentDetail(entry) {
  const { createdAt, ...rest } = entry;
  void createdAt;
  return structuredClone(rest);
}

// A-1 ~ A-4
export async function csrf() {
  await delay();
  return { headerName: 'X-XSRF-TOKEN' };
}

export async function login(email, password) {
  await delay();
  if (!email || !password || email.length > 100 || password.length > 100) fail('VALIDATION_ERROR', '이메일과 비밀번호를 입력해 주세요.', { status: 400 });
  // 목은 아무 이메일 · 비밀번호로 로그인된다. 실패를 보려면 비밀번호에 wrong을 넣는다
  if (password === 'wrong') fail('INVALID_CREDENTIALS', '이메일 또는 비밀번호가 맞지 않아요.', { status: 401 });
  try { sessionStorage.setItem(SESSION_KEY, '1'); } catch { /* 저장소를 못 쓰면 이번 화면에서만 로그인 */ }
  return { ...ADMIN, email: email.toLowerCase() };
}

export async function logout() {
  await delay();
  requireLogin();
  try { sessionStorage.removeItem(SESSION_KEY); } catch { /* 무시 */ }
  return null;
}

export async function me() {
  await delay();
  requireLogin();
  return { ...ADMIN };
}

// A-5
export async function getCases({ status = 'DRAFT,REVIEW', pending, page = 0, size = 20 } = {}) {
  await delay();
  requireLogin();
  const statuses = status.split(',');
  if (statuses.some((value) => !['DRAFT', 'REVIEW', 'PUBLISHED'].includes(value)) || page < 0 || size < 1 || size > 100) {
    fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', { status: 400 });
  }
  const rows = state.cases
    .filter((item) => statuses.includes(item.status))
    .map((item) => {
      const list = candidatesOf(item.caseId);
      const pendingCounts = {
        aiCandidates: list.filter((entry) => entry.subjectType === 'AI' && entry.reviewStatus === 'PENDING').length,
        courtCandidates: list.filter((entry) => entry.subjectType === 'COURT' && entry.reviewStatus === 'PENDING').length,
        axisNeedsReview: item.factors.filter((factor) => factor.valueAxisStatus === 'AUTO').length,
      };
      const lastActivityAt = [item.updatedAt, ...list.map((entry) => entry.reviewedAt ?? entry.createdAt)].sort().at(-1);
      return {
        caseId: item.caseId, title: item.content.title, crimeType: item.content.crimeType, status: item.status, pending: pendingCounts,
        hasPublishedAi: list.some((entry) => entry.subjectType === 'AI' && entry.isPublished),
        hasPublishedCourt: list.some((entry) => entry.subjectType === 'COURT' && entry.isPublished),
        createdAt: item.createdAt, updatedAt: item.updatedAt, lastActivityAt,
      };
    })
    .filter((row) => pending !== true || row.pending.aiCandidates + row.pending.courtCandidates > 0)
    .sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt));
  return structuredClone({ items: rows.slice(page * size, page * size + size), page, size, totalElements: rows.length });
}

// A-6 ~ A-8
export async function getCase(caseId) {
  await delay();
  requireLogin();
  return detail(findCase(caseId));
}

export async function getSourceText(caseId, sourceId) {
  await delay();
  requireLogin();
  const item = findCase(caseId);
  if (!item.sources.some((source) => source.sourceId === Number(sourceId))) fail('CASE_NOT_FOUND', '사건을 찾을 수 없어요.', { status: 404 });
  return { sourceId: Number(sourceId), originalText: ORIGINAL_TEXT };
}

export async function getJudgment(judgmentId) {
  await delay();
  requireLogin();
  return judgmentDetail(findJudgment(judgmentId));
}

// A-9 · A-10
export async function approveJudgment(judgmentId) {
  await delay();
  requireLogin();
  const entry = findJudgment(judgmentId);
  const item = findCase(entry.caseId);
  if (entry.reviewStatus === 'APPROVED' && entry.isPublished) return judgmentDetail(entry);
  if (item.status === 'PUBLISHED') invalidState(item, 'CASE_PUBLISHED');
  if (entry.reviewStatus === 'REJECTED') invalidState(item, 'CANDIDATE_REJECTED');
  candidatesOf(item.caseId).filter((other) => other.subjectType === entry.subjectType).forEach((other) => { other.isPublished = false; });
  Object.assign(entry, { reviewStatus: 'APPROVED', isPublished: true, reviewedBy: ADMIN.email, reviewedAt: now() });
  touch(item);
  return judgmentDetail(entry);
}

export async function rejectJudgment(judgmentId, reasonText) {
  await delay();
  requireLogin();
  if (typeof reasonText !== 'string' || !reasonText.trim() || reasonText.length > 500) fail('VALIDATION_ERROR', '반려 사유를 1 ~ 500자로 입력해 주세요.', { status: 400 });
  const entry = findJudgment(judgmentId);
  const item = findCase(entry.caseId);
  if (entry.isPublished) invalidState(item, 'CANDIDATE_PUBLISHED');
  if (entry.reviewStatus === 'REJECTED') invalidState(item, 'CANDIDATE_REJECTED');
  Object.assign(entry, { reviewStatus: 'REJECTED', reviewedBy: ADMIN.email, reviewedAt: now(), reviewNote: reasonText });
  touch(item);
  return judgmentDetail(entry);
}

// A-11
const EDITABLE = ['title', 'shortIntro', 'overview', 'keywords', 'difficulty', 'estimatedMinutes', 'thumbnailUrl', 'recommendedMinMonths', 'recommendedMaxMonths', 'recommendedBasis'];

export async function updateCase(caseId, changes) {
  await delay();
  requireLogin();
  const item = findCase(caseId);
  const keys = Object.keys(changes ?? {});
  const invalid = (field) => fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', { status: 400, details: [{ field, reason: 'INVALID' }] });
  if (!keys.length) invalid('body');
  keys.forEach((key) => { if (!EDITABLE.includes(key)) invalid(key); });
  if (item.status === 'PUBLISHED') invalidState(item, 'CASE_PUBLISHED');
  const next = { ...item.content, ...changes };
  if (!next.title || next.title.length > 100) invalid('title');
  if (!next.shortIntro || next.shortIntro.length > 200) invalid('shortIntro');
  if (!next.overview) invalid('overview');
  if (!Array.isArray(next.keywords) || next.keywords.some((word) => typeof word !== 'string')) invalid('keywords');
  if (next.recommendedMinMonths != null && next.recommendedMaxMonths != null && next.recommendedMinMonths > next.recommendedMaxMonths) invalid('recommendedMinMonths');
  if (state.cases.some((other) => other.caseId !== item.caseId && other.content.title === next.title)) fail('DUPLICATE_TITLE', '다른 사건과 제목이 겹쳐요.', { status: 409 });
  item.content = next;
  touch(item);
  return detail(item);
}

// A-12
export async function setValueAxis(factorId, valueAxis) {
  await delay();
  requireLogin();
  if (valueAxis !== null && !['APOLOGY_SINCERITY', 'FAULT_STANDARD', 'PRINCIPLE_RELATION', 'ORDER_OPPORTUNITY'].includes(valueAxis)) {
    fail('VALIDATION_ERROR', '모르는 가치관 축이에요.', { status: 400 });
  }
  const item = state.cases.find((entry) => entry.factors.some((factor) => factor.factorId === Number(factorId)));
  if (!item) fail('FACTOR_NOT_FOUND', '판단 요소를 찾을 수 없어요.', { status: 404 });
  const factor = item.factors.find((entry) => entry.factorId === Number(factorId));
  Object.assign(factor, { valueAxis, valueAxisStatus: 'CONFIRMED' });
  touch(item);
  return { factorId: factor.factorId, valueAxis, valueAxisStatus: 'CONFIRMED' };
}

// A-13
export async function publishCase(caseId) {
  await delay();
  requireLogin();
  const item = findCase(caseId);
  if (item.status === 'PUBLISHED') invalidState(item, 'CASE_PUBLISHED');
  const { ready, reasons } = readiness(item);
  if (!ready) fail('PUBLISH_NOT_READY', '공개 조건을 채우지 못했어요.', { status: 409, details: reasons.map((code) => ({ field: 'case', reason: code })) });
  item.status = 'PUBLISHED';
  item.updatedAt = now();
  return detail(item);
}
