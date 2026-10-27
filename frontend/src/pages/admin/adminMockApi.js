// 관리자 API 목 (API 명세 8장 A-1 ~ A-15, A-13-1 ~ A-13-13). 7.4.7 구현 전에 S-19 화면을 만들고 확인하는 용도다.
// 사건 · 원문은 모두 가상 값이다. 상태는 메모리에만 두고(새로고침하면 처음으로), 로그인 여부만 sessionStorage에 둔다.
// 원본 판결문 같은 내부 정보를 브라우저 저장소에 남기지 않기 위해서다.
import { ADMIN, SESSION_KEY, delay, fail, now, requireLogin } from './adminMockBase.js';

export * from './adminOpsMockApi.js'; // 운영 메뉴(대시보드 · 회원 · 게시글 · 신고 · 관리자 계정 · 공지) 목

function baseFactors(prefix) {
  return [
    // 요약어 후보(BE-80): 제안 / 목록에 있음 / 애매함 / 후보 계산을 켜지 않음(null) 네 경우를 한 사건에서 모두 볼 수 있게 섞었다
    { factorId: prefix + 1, displayOrder: 1, label: '빌린 돈을 갚으라는 말에 화가 나 다툼이 시작됐다', preLabel: '금전 문제로 다투다 일어났다', revealStage: 'OVERVIEW', summaryTag: '금전 갈등', valueAxis: 'FAULT_STANDARD', valueAxisStatus: 'AUTO', valueAxisVotes: { runs: 5, counts: { FAULT_STANDARD: 3, NONE: 2 }, needsReview: false }, summaryTagCandidates: { candidates: [{ tag: '채무 다툼', score: 0.9211 }, { tag: '반복된 다툼', score: 0.8735 }], inVocabulary: false, ambiguous: false, suggested: '채무 다툼' } },
    { factorId: prefix + 2, displayOrder: 2, label: '범행 뒤 스스로 경찰서를 찾아갔다', preLabel: null, revealStage: 'DETAIL', summaryTag: '자수', valueAxis: 'APOLOGY_SINCERITY', valueAxisStatus: 'AUTO', valueAxisVotes: { runs: 5, counts: { APOLOGY_SINCERITY: 5 }, needsReview: false }, summaryTagCandidates: { candidates: [{ tag: '자수', score: 1 }], inVocabulary: true, ambiguous: false, suggested: null } },
    { factorId: prefix + 3, displayOrder: 3, label: '유족과 합의하지 못했다', preLabel: null, revealStage: 'DETAIL', summaryTag: '합의 없음', valueAxis: null, valueAxisStatus: 'AUTO', valueAxisVotes: { runs: 5, counts: { PRINCIPLE_RELATION: 2, NONE: 3 }, needsReview: true }, summaryTagCandidates: { candidates: [{ tag: '합의 불발', score: 0.8812 }, { tag: '피해 회복 없음', score: 0.8764 }], inVocabulary: false, ambiguous: true, suggested: null } },
    { factorId: prefix + 4, displayOrder: 4, label: '범행 당시 술에 취한 상태였다', preLabel: null, revealStage: 'DETAIL', summaryTag: '음주', valueAxis: 'FAULT_STANDARD', valueAxisStatus: 'AUTO', valueAxisVotes: { runs: 5, counts: { FAULT_STANDARD: 5 }, needsReview: false }, summaryTagCandidates: null },
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

// 값 모양은 파이프라인 build_generation_report(tools/ai-judgment/pipeline.py)와 같다.
// 회차 1은 경고 · 의심 판정이 있는 경우, 회차 2는 깨끗한 경우, 회차 3은 점검을 건너뛴 경우를 보여 준다
function aiGeneration(round) {
  const contamination = [
    { verdict: 'SUSPECT', reason: '가까운 응답 3/5 (비율 기준 이상)', counts: { EXACT: 0, CLOSE: 2, SAME_TYPE: 1, FAR: 2, KNOWS: 0, INVALID: 0 }, criteria: { minAnswered: 3, exactRatio: 0.5, suspectRatio: 0.5 } },
    { verdict: 'CLEAN', reason: '응답 5개 중 정확히 · 가깝게 맞힌 비율이 기준 미만', counts: { EXACT: 0, CLOSE: 1, SAME_TYPE: 0, FAR: 4, KNOWS: 0, INVALID: 0 }, criteria: { minAnswered: 3, exactRatio: 0.5, suspectRatio: 0.5 } },
    { verdict: 'SKIPPED' },
  ][round - 1];
  return {
    modelName: '(가상 모델)',
    promptVersion: 'v-mock',
    inputSnapshot: {
      promptVersion: 'v-mock',
      system: '당신은 형사 사건의 양형을 판단하는 재판부다. (가상 시스템 프롬프트)',
      user: '사건 개요: 빌려준 돈을 돌려받는 문제로 다투던 중 …\n참고 판례: 가상 판례 A, 가상 판례 B\n양형기준: 살인범죄 양형기준 (가상 버전)',
    },
    generationReport: {
      runKey: `mock-run-${round}`, pipeline: 'mock-pipeline', modelSpec: 'gemini:gemini-mock', servedModel: 'gemini-mock-001', runIndex: round, generatedAt: '2026-10-20T09:00:00+09:00',
      usage: { inputTokens: 5210, outputTokens: 840 },
      validationWarnings: round === 1 ? ['참고 판례 1건의 죄명이 다름'] : [],
      selection: { strategy: 'consensus', reason: '판단 요소 근거가 가장 구체적인 회차', score: 0.8125 },
      contamination,
      rag: round === 3
        ? { status: 'SKIPPED' }
        : { status: 'OK', fallbackReasons: [], embeddingModel: 'gemini-embedding-mock', guideline: '살인범죄 양형기준 (가상 버전)', guidelineBasis: 'CURRENT', guidelineFactors: [], precedents: [{ id: 801, score: 0.9123 }, { id: 802, score: 0.8841 }], excludedSuspects: 1, warnings: [] },
    },
  };
}

function judgment(judgmentId, caseId, subjectType, prisonMonths, extra = {}) {
  return {
    judgmentId, caseId, subjectType,
    reviewStatus: 'PENDING', isPublished: false, reviewedBy: null, reviewedAt: null, reviewNote: null,
    judgment: aiJudgmentBody(prisonMonths),
    generation: subjectType === 'AI' ? aiGeneration(judgmentId % 3 + 1) : null,
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

const PHOTO_BASE = 'https://example.invalid/case-photos';

function incidentPhoto(photoId, role, caption) {
  return { id: photoId, imageUrl: `${PHOTO_BASE}/incident-${role.toLowerCase()}-${photoId}.jpg`, caption,
    role, displayOrder: role === 'ORIGIN' ? 0 : 1, createdAt: '2026-10-19T09:00:00+09:00' };
}

function mockPaper() {
  return { desk: '사회부', headline: '빌린 돈 문제로 다투다 벌어진 사건', deck: '오랜 금전 다툼 끝에 벌어진 일이다',
    photo: null, caption: '다투는 두 사람', article: ['첫 문단입니다.', '둘째 문단입니다.'],
    updatedAt: '2026-10-19T09:00:00+09:00' };
}

/** 대사 한 줄. 파이프라인이 만드는 모양 그대로다(A-13-10) */
function line(factorId, label, role, character, text, warnings = [], attempts = 1) {
  return { factorId, label, role, character, text, attempts, validation: { ok: text !== null, warnings } };
}

/** 검수 거리가 있는 대본: 둘째 줄을 끝내 만들지 못해 publishable이 false다 */
function draftScript() {
  return {
    evidence: [
      line(1, '빌린 돈을 갚으라는 말에 화가 나 다툼이 시작됐다', 'PROSECUTOR', '나로', '돈을 갚으라는 말에 피고인이 먼저 언성을 높였습니다.'),
      line(2, '다투던 중 흉기를 집어 들었다', 'DEFENSE', '남불', null, ['증거 카드에 없는 사실을 말했습니다'], 3),
      line(3, '범행 뒤 현장을 떠났다', 'PROSECUTOR', '나로', '피고인은 구호 조치 없이 집을 나갔습니다.'),
    ],
    cast: { prosecutor: '나로', defense: '남불' },
    model: 'google:gemini-2.5-flash', balanceWarning: '검사 쪽 대사가 변호인보다 두 배 깁니다',
    publishable: false, stopped: false,
    generatedAt: '2026-10-20T09:00:00+09:00', updatedAt: '2026-10-20T09:10:00+09:00',
    reviewStatus: 'PENDING', isPublished: false, reviewedBy: null, reviewedAt: null, reviewNote: null,
  };
}

/** 이미 승인해 공개 중인 대본 */
function approvedScript() {
  return {
    evidence: [
      line(1, '층간 소음으로 여러 번 다툼이 있었다', 'PROSECUTOR', '나로', '소음 문제로 다툼이 반복됐습니다.'),
      line(2, '피고인이 먼저 문을 두드렸다', 'DEFENSE', '남불', '피고인은 대화를 하려 했을 뿐입니다.'),
    ],
    cast: { prosecutor: '나로', defense: '남불' },
    model: 'google:gemini-2.5-flash', balanceWarning: null, publishable: true, stopped: false,
    generatedAt: '2026-10-18T09:00:00+09:00', updatedAt: '2026-10-19T09:00:00+09:00',
    reviewStatus: 'APPROVED', isPublished: true, reviewedBy: 'admin@example.com',
    reviewedAt: '2026-10-19T09:00:00+09:00', reviewNote: null,
  };
}

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
    imports: [],
    // 사건 사진(A-13-4 · 5) · 신문 지면(A-13-6 ~ 8) · 대본(A-13-10 ~ 13). 사건 번호를 키로 둔다
    photos: { 13: [incidentPhoto(21, 'ORIGIN', '다툼이 격해진 순간'), incidentPhoto(22, 'CONCLUSION', '집을 나서는 뒷모습')] },
    papers: { 13: mockPaper() },
    // 12번은 검수 거리를 보여 주는 대본이다 — 생성하지 못한 줄이 있어 publishable이 false다(승인 버튼이 막힌다).
    // 13번은 이미 승인해 공개 중인 대본이다
    scripts: { 12: draftScript(), 13: approvedScript() },
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
    // 대본 요약(BE-108). 공개를 막지 않고 경고로만 쓴다 — publishReadiness.reasons에는 넣지 않는다
    script: scriptSummary(item.caseId),
  });
}

function scriptSummary(caseId) {
  const script = state.scripts[caseId];
  return script
    ? { exists: true, reviewStatus: script.reviewStatus, publishable: script.publishable }
    : { exists: false, reviewStatus: null, publishable: null };
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

// A-14 · A-15 · A-14-1 사건 등록 (원본 판결문 → 파이프라인 실행 → 적재).
// 목은 getImport를 부를 때마다 단계를 하나씩 진행한다. 생성이 끝나면(SUCCEEDED) 실제 서버와 같이 caseId가 아직 없고,
// 관리자가 적재(A-14-1 loadImport)를 불러야 DRAFT 사건이 생긴다 — 조회가 쓰기를 하지 않는다(BE-93 2차).
// 원문 텍스트의 표식으로 실패 경로를 확인할 수 있다: [실패] 비식별화 단계 실패 · [제목중복] 적재에서 DUPLICATE_TITLE
// · [적재실패] 적재에서 IMPORT_RESULT_INVALID · [결과소실] 적재에서 IMPORT_NOT_READY(생성 결과가 사라진 경우).
// 표식은 등록할 때 읽어 두고 원문 자체는 목에도 남기지 않는다(FR-5-3).
const IMPORT_STAGES = ['extract', 'court', 'contamination', 'rag', 'generate', 'select', 'axis', 'load'];
// 파이프라인 서비스가 늘 끄고 시작하는 단계(COMMON-50). 오류가 아니라 건너뜀이며, load는 서버가 A-14-1로 따로 한다
const SKIPPED_STAGES = new Set(['contamination', 'rag', 'load']);
const COURT_LEVELS = ['FIRST', 'APPEAL', 'SUPREME'];
const MAX_ORIGINAL_LENGTH = 200000;

export async function createImport(request) {
  await delay();
  requireLogin();
  const invalid = (field) => fail('VALIDATION_ERROR', '입력값을 확인해 주세요.', { status: 400, details: [{ field, reason: 'INVALID' }] });
  const sources = request?.sources;
  if (!['MURDER', 'FRAUD', 'INJURY', 'ROBBERY', 'THEFT'].includes(request?.crimeType)) invalid('crimeType');
  if (!Array.isArray(sources) || sources.length < 1 || sources.length > 3) invalid('sources');
  sources.forEach((source, index) => {
    if (!COURT_LEVELS.includes(source?.courtLevel)) invalid(`sources[${index}].courtLevel`);
    if (!source.caseNumber || source.caseNumber.length > 50) invalid(`sources[${index}].caseNumber`);
    if (!source.originalText?.trim() || source.originalText.length > MAX_ORIGINAL_LENGTH) invalid(`sources[${index}].originalText`);
    if (source.decidedAt && !/^\d{4}-\d{2}-\d{2}$/.test(source.decidedAt)) invalid(`sources[${index}].decidedAt`);
  });
  if (new Set(sources.map((source) => source.courtLevel)).size !== sources.length) invalid('sources');
  if (state.imports.some((item) => item.status === 'QUEUED' || item.status === 'RUNNING')) fail('IMPORT_IN_PROGRESS', '다른 사건을 등록하는 중이에요. 끝난 뒤 다시 시도해 주세요.', { status: 409 });
  const known = new Set(state.cases.flatMap((item) => item.sources.map((source) => source.caseNumber)));
  if (sources.some((source) => known.has(source.caseNumber))) fail('DUPLICATE_SOURCE', '이미 등록한 사건번호예요.', { status: 409 });

  const marked = (mark) => sources.some((source) => source.originalText.includes(mark));
  const entry = {
    importId: state.imports.length + 1, status: 'QUEUED', crimeType: request.crimeType, step: -1,
    willFail: marked('[실패]'),
    // 적재(A-14-1)에서 돌려줄 거절. 원문을 남기지 않으므로 등록할 때 표식만 읽어 둔다
    loadFailure: marked('[제목중복]') ? 'DUPLICATE_TITLE' : marked('[적재실패]') ? 'IMPORT_RESULT_INVALID' : marked('[결과소실]') ? 'IMPORT_NOT_READY' : null,
    sources: sources.map(({ originalText, ...rest }) => { void originalText; return rest; }), // 원문은 목에도 남기지 않는다
    caseId: null, errorCode: null, message: null, createdAt: now(), finishedAt: null,
  };
  state.imports.push(entry);
  return { importId: entry.importId, status: entry.status };
}

function importView(entry) {
  const stages = IMPORT_STAGES.map((name, index) => {
    if (SKIPPED_STAGES.has(name)) return { name, status: 'SKIPPED' }; // 실제 서버도 이 셋은 항상 SKIPPED다
    let status = 'PENDING';
    if (index < entry.step) status = 'DONE';
    else if (index === entry.step) status = entry.status === 'FAILED' ? 'FAILED' : entry.status === 'RUNNING' ? 'RUNNING' : 'DONE';
    if (entry.status === 'SUCCEEDED') status = 'DONE';
    return { name, status };
  });
  return structuredClone({
    importId: entry.importId, status: entry.status, stages, caseId: entry.caseId,
    errorCode: entry.errorCode, message: entry.message, createdAt: entry.createdAt, finishedAt: entry.finishedAt,
  });
}

// 생성(파이프라인)만 끝낸다. 사건은 아직 만들지 않는다 — 적재는 관리자가 A-14-1로 누른다
function succeedImport(entry) {
  Object.assign(entry, { status: 'SUCCEEDED', finishedAt: now() });
}

function findImport(importId) {
  return state.imports.find((item) => item.importId === Number(importId)) ?? fail('IMPORT_NOT_FOUND', '등록 작업을 찾을 수 없어요.', { status: 404 });
}

export async function getImport(importId) {
  await delay();
  requireLogin();
  const entry = findImport(importId);
  if (entry.status === 'QUEUED' || entry.status === 'RUNNING') {
    entry.step += 1;
    entry.status = 'RUNNING';
    if (entry.willFail && entry.step === 0) {
      Object.assign(entry, { status: 'FAILED', errorCode: 'EXTRACT_FAILED', message: '비식별화에 실패했어요. 원문을 확인한 뒤 다시 등록해 주세요.', finishedAt: now() });
    } else if (entry.step >= IMPORT_STAGES.length) {
      entry.step = IMPORT_STAGES.length - 1;
      succeedImport(entry);
    }
  }
  return importView(entry);
}

// A-14-1 적재 — 생성 결과를 사건(DRAFT)으로 만든다. 판결 후보 둘은 공개하지 않은 상태로 들어간다(A-9 승인 대기)
export async function loadImport(importId) {
  await delay();
  requireLogin();
  const entry = findImport(importId);
  // 생성이 끝나지 않았거나 이미 적재한 등록은 거절한다(실제 서버와 같은 기준)
  if (entry.status !== 'SUCCEEDED' || entry.caseId !== null) fail('IMPORT_NOT_READY', '아직 적재할 수 없어요.', { status: 409 });
  // 생성 결과가 사라진 경우(파이프라인에서 작업이 없어짐). 다시 눌러도 소용없고 사유는 message로만 구분된다
  if (entry.loadFailure === 'IMPORT_NOT_READY') fail('IMPORT_NOT_READY', '생성 결과가 사라졌습니다. 다시 등록해 주세요.', { status: 409 });
  if (entry.loadFailure === 'DUPLICATE_TITLE') fail('DUPLICATE_TITLE', '같은 제목의 사건이 이미 있어요.', { status: 409 });
  if (entry.loadFailure === 'IMPORT_RESULT_INVALID') {
    fail('IMPORT_RESULT_INVALID', '생성 결과를 사건으로 만들 수 없어요.', { status: 422, details: [{ field: 'case.factors[2].label', reason: 'REQUIRED' }] });
  }
  const caseId = Math.max(...state.cases.map((item) => item.caseId)) + 1;
  const judgmentId = Math.max(...state.judgments.map((item) => item.judgmentId)) + 1;
  const first = entry.sources[0];
  state.cases.push({
    caseId, status: 'DRAFT', content: content(`새로 등록한 사건 #${caseId}`, { crimeType: entry.crimeType, incidentDate: first.decidedAt ?? null }),
    sources: entry.sources.map((source, index) => ({ sourceId: 900 + caseId * 10 + index, ...source, isFinal: Boolean(source.isFinal), note: source.note ?? null, hasOriginalText: true })),
    factors: baseFactors(caseId * 100), penaltyRules: 1, createdAt: now(), updatedAt: now(),
  });
  state.judgments.push(judgment(judgmentId, caseId, 'AI', 168, { createdAt: now() }), judgment(judgmentId + 1, caseId, 'COURT', 144, { createdAt: now() }));
  entry.caseId = caseId;
  return importView(entry);
}

// ─────────────────────────────────────────────────────────────
// A-13-1 ~ A-13-13 사건 사진 · 신문 지면 · 대본 검수 (FE-106)
// 서버 규칙을 그대로 흉내 낸다 — 공개한 사건은 바꿀 수 없고, 거절 코드도 명세와 같은 값을 쓴다.
// ─────────────────────────────────────────────────────────────

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;	// 8-1 공통 multipart 상한
const ROLES = ['ORIGIN', 'CONCLUSION'];

/** 공개한 사건은 사진 · 지면을 바꿀 수 없다 (A-11과 같은 규칙) */
function requireEditable(item) {
  if (item.status === 'PUBLISHED') invalidState(item, 'CASE_PUBLISHED');
}

function requireImage(file) {
  if (!file || !file.size) fail('INVALID_IMAGE_FILE', '이미지 파일을 골라 주세요.', { status: 400 });
  if (!IMAGE_TYPES.includes(file.type)) fail('INVALID_IMAGE_FILE', 'JPG · PNG · WebP만 올릴 수 있어요.', { status: 400 });
  if (file.size > MAX_IMAGE_BYTES) fail('INVALID_IMAGE_FILE', '10MB 이하만 올릴 수 있어요.', { status: 400 });
}

// A-13-1 · A-13-2 프롬프트 추천
export async function suggestRepresentativePrompt(caseId) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  return { prompt: `신문 지면에 쓸 삽화를 그려줘. ${item.content.overview.slice(0, 40)}… 어두운 실내에서 두 사람이 다투는 장면.`
    + ' 등장인물은 얼굴 없는 회색 엑스트라로 그리고, 특정 인물을 알아볼 수 있게 그리지 않는다.' };
}

export async function suggestIncidentPrompt(caseId, hint) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  if (hint && hint.length > 500) fail('VALIDATION_ERROR', '힌트는 500자까지 쓸 수 있어요.', { status: 400 });
  return { prompt: `사건이 시작된 장면을 그려줘. ${item.content.shortIntro} 얼굴 없는 회색 엑스트라로 그린다.${hint ? ` (${hint})` : ''}` };
}

// A-13-3 대표사진 교체 — 응답은 A-6과 같은 사건 상세다
export async function uploadRepresentativePhoto(caseId, file) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  requireEditable(item);
  requireImage(file);
  item.content.thumbnailUrl = `${PHOTO_BASE}/representative-${item.caseId}-${Date.now()}.jpg`;
  touch(item);
  return detail(item);
}

// A-13-4 발단 · 결론 사진 업로드 (같은 역할로 다시 올리면 교체한다 — 역할별 1장)
export async function uploadIncidentPhoto(caseId, { file, role, caption, prompt } = {}) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  requireEditable(item);
  if (!ROLES.includes(role)) fail('VALIDATION_ERROR', '발단 · 결론 중 하나를 골라 주세요.', { status: 400 });
  if (caption && caption.length > 200) fail('VALIDATION_ERROR', '설명은 200자까지 쓸 수 있어요.', { status: 400 });
  requireImage(file);
  void prompt;	// 서버는 기록만 하고 응답에 내려주지 않는다
  const list = state.photos[item.caseId] ?? (state.photos[item.caseId] = []);
  const kept = list.find((photo) => photo.role === role);
  const saved = {
    id: kept?.id ?? Math.floor(Math.random() * 1000) + 100,
    imageUrl: `${PHOTO_BASE}/incident-${role.toLowerCase()}-${Date.now()}.jpg`,
    caption: caption ?? null, role, displayOrder: role === 'ORIGIN' ? 0 : 1, createdAt: now(),
  };
  if (kept) list.splice(list.indexOf(kept), 1, saved); else list.push(saved);
  touch(item);
  return structuredClone(saved);
}

// A-13-5 목록 (공개한 사건도 조회는 된다)
export async function getIncidentPhotos(caseId) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  const list = (state.photos[item.caseId] ?? []).slice().sort((a, b) => a.displayOrder - b.displayOrder);
  return structuredClone(list);
}

// A-13-9 삭제
export async function deleteIncidentPhoto(caseId, role) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  if (!ROLES.includes(role)) fail('VALIDATION_ERROR', '발단 · 결론 중 하나를 골라 주세요.', { status: 400 });
  requireEditable(item);
  const list = state.photos[item.caseId] ?? [];
  const found = list.find((photo) => photo.role === role);
  if (!found) fail('INCIDENT_PHOTO_NOT_FOUND', '그 자리의 사진이 없어요.', { status: 404 });
  list.splice(list.indexOf(found), 1);
  touch(item);
  return null;	// 204
}

// A-13-6 ~ A-13-8 신문 지면
const paperOf = (item) => state.papers[item.caseId] ?? fail('PAPER_NOT_FOUND', '아직 신문 지면을 만들지 않았어요.', { status: 404 });

/** 지면 응답에는 대표사진 · 발단 · 결론 사진이 함께 들어간다(따로 저장하지 않고 그때그때 붙인다) */
function paperView(item) {
  const paper = state.papers[item.caseId];
  return structuredClone({
    ...paper,
    photo: item.content.thumbnailUrl,
    extraPhotos: (state.photos[item.caseId] ?? []).slice().sort((a, b) => a.displayOrder - b.displayOrder),
  });
}

export async function generateCasePaper(caseId) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  requireEditable(item);
  state.papers[item.caseId] = {
    ...mockPaper(), headline: `${item.content.title.slice(0, 20)}`, deck: item.content.shortIntro,
    article: [item.content.overview, '목 모드에서 지은 둘째 문단입니다.'], updatedAt: now(),
  };
  touch(item);
  return paperView(item);
}

export async function getCasePaper(caseId) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  paperOf(item);
  return paperView(item);
}

export async function updateCasePaper(caseId, changes) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  const paper = paperOf(item);
  requireEditable(item);
  if (changes.headline !== undefined && (!changes.headline || changes.headline.length > 200)) {
    fail('VALIDATION_ERROR', '헤드라인은 1 ~ 200자로 써 주세요.', { status: 400 });
  }
  if (changes.article !== undefined && (!Array.isArray(changes.article) || !changes.article.length)) {
    fail('VALIDATION_ERROR', '본문은 한 문단 이상이어야 해요.', { status: 400 });
  }
  Object.assign(paper, changes, { updatedAt: now() });
  touch(item);
  return paperView(item);
}

// A-13-10 ~ A-13-13 대본 검수
const scriptOf = (item) => state.scripts[item.caseId] ?? fail('SCRIPT_NOT_FOUND', '이 사건에는 대본이 없어요.', { status: 404 });

export async function getCaseScript(caseId) {
  await delay(); requireLogin();
  return structuredClone(scriptOf(findCase(caseId)));
}

export async function approveCaseScript(caseId) {
  await delay(); requireLogin();
  const item = findCase(caseId);
  const script = scriptOf(item);
  // 파이프라인이 어긋난다고 표시한 대본은 승인할 수 없다 (서버도 409로 막는다)
  if (!script.publishable) {
    fail('INVALID_STATE', '증거 카드와 대사가 어긋난 대본은 승인할 수 없어요. 대본을 다시 생성해 주세요.', { status: 409 });
  }
  Object.assign(script, { reviewStatus: 'APPROVED', isPublished: true, reviewedBy: ADMIN.email, reviewedAt: now(), reviewNote: null, updatedAt: now() });
  return structuredClone(script);	// 대본 검수는 사건 상태를 바꾸지 않는다(touch하지 않는다)
}

export async function rejectCaseScript(caseId, reasonText) {
  await delay(); requireLogin();
  const script = scriptOf(findCase(caseId));
  const note = (reasonText ?? '').trim();
  if (!note || note.length > 500) fail('VALIDATION_ERROR', '반려 사유는 1 ~ 500자로 써 주세요.', { status: 400 });
  Object.assign(script, { reviewStatus: 'REJECTED', isPublished: false, reviewedBy: ADMIN.email, reviewedAt: now(), reviewNote: note, updatedAt: now() });
  return structuredClone(script);
}

/** A-13-13 대사 한 줄 수정. 길이는 파이프라인과 같은 방식으로 센다(줄바꿈은 글자 수에 넣지 않는다) */
export async function updateCaseScriptLine(caseId, factorId, text) {
  await delay(); requireLogin();
  const script = scriptOf(findCase(caseId));
  const value = (text ?? '').trim();
  const lines = value.split('\n').filter((part) => part.trim());
  if (!lines.length) fail('VALIDATION_ERROR', '대사를 입력해 주세요.', { status: 400 });
  if (lines.length > 2) fail('VALIDATION_ERROR', '대사는 2줄까지 쓸 수 있어요.', { status: 400 });
  const chars = lines.reduce((sum, part) => sum + part.length, 0);
  if (chars > 100) fail('VALIDATION_ERROR', `대사는 줄바꿈을 빼고 100자까지 쓸 수 있어요. (지금 ${chars}자)`, { status: 400 });

  const found = script.evidence.find((entry) => entry.factorId === Number(factorId));
  if (!found) fail('SCRIPT_LINE_NOT_FOUND', '대본에 그 판단 요소의 대사가 없어요.', { status: 404 });
  found.text = value;
  found.validation = { ...found.validation, ok: true, manual: true };	// 사람이 고침
  found.editedAt = now();
  // 파이프라인과 같은 식으로 다시 계산한다: 멈춘 대본은 줄 자체가 모자라 올라가지 않는다
  script.publishable = !script.stopped && script.evidence.every((entry) => Boolean(entry.text));
  Object.assign(script, { reviewStatus: 'PENDING', isPublished: false, reviewedBy: null, reviewedAt: null, reviewNote: null, updatedAt: now() });
  return structuredClone(script);
}
