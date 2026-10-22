import { AXES } from '../utils/personalityAxes.js';
import { imageNumberOf, QUESTIONS, TYPES } from './personalityMockData.js';
import { shareIdOfCode } from '../utils/personalityTypes.js';
import { mockCases } from './caseListMockData.js';
import { mockProfileView, setStatsReader } from './profileDecoMockApi.js';

// 판결 성향 테스트 · 마이페이지 목 API. 응답 모양은 API 명세 9장(성향 API, v0.20)의 P-1 ~ P-5를 따른다.
// 채점은 docs/personality-scoring.md 2장을 따른다. 문항의 글자 · 축은 서버 쪽 정보라 응답에 넣지 않는다.
// 결과는 목 전용으로 브라우저 localStorage에 둔다 (실제로는 서버가 익명 ID 기준으로 저장).

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const storageKey = 'nlnb-mock-personality';
const logKey = 'nlnb-mock-personality-log'; // 응시 기록 전체(처음 성향 · 다시 정하기용, 명세 v0.50). 실제로는 서버의 personality_result 행들
let memoryLog = null; // 저장소를 못 쓸 때(테스트 · 사생활 보호 모드) 이번 실행 동안의 기록
const WEIGHTS = [2, 1, 0, 1, 2]; // 매우 A · 조금 A · 보통 · 조금 B · 매우 B


const MESSAGES = {
  VALIDATION_ERROR: '모든 문항에 답해 주세요.',
  PERSONALITY_RESULT_NOT_FOUND: '성향 테스트 결과가 없어요.',
  PERSONALITY_TYPE_NOT_FOUND: '찾을 수 없는 유형이에요.',
  BASELINE_RETAKE_USED: '재심은 이미 한 번 청구했어요.',
};

function fail(code) {
  throw { code, message: MESSAGES[code], currentStatus: null, details: null };
}

function load() {
  try {
    return JSON.parse(localStorage.getItem(storageKey));
  } catch {
    return null;
  }
}

// 답 하나(value 0 ~ 4)가 가리키는 글자와 점수. 보통(2)은 글자 · 점수가 없다
function pick(question, value) {
  if (value === 2) return { letter: null, score: 0 };
  return { letter: value < 2 ? question.a.letter : question.b.letter, score: WEIGHTS[value] };
}

// 축 점수 = (관대 점수 − 엄격 점수 + 2n) ÷ 4n (0 ~ 1, 관대 쪽 비중). 글자는 합이 0 이상이면 관대(동점도 관대)
function score(answers) {
  const letters = [];
  const axisScores = {};
  AXES.forEach((axis) => {
    const questions = QUESTIONS.filter((question) => question.axis === axis.key);
    let gap = 0; // 관대 − 엄격
    questions.forEach((question) => {
      const { letter, score: points } = pick(question, answers[question.id]);
      if (letter === axis.lenient.letter) gap += points;
      else if (letter === axis.strict.letter) gap -= points;
    });
    axisScores[axis.key] = (gap + 2 * questions.length) / (4 * questions.length);
    letters.push(gap >= 0 ? axis.lenient.letter : axis.strict.letter);
  });
  return { typeCode: letters.join(''), axisScores };
}

// 공유 주소용 해시(유형 코드를 주소에 드러내지 않는 값, 명세 9-1). 서버 시드와 같은 값을 쓴다(og/personality-types.json).
// 목과 실제 API가 같은 shareId를 써야 공유 주소(P-4)가 목 · 실제에서 같은 유형을 가리킨다(FE-75)
const shareIdOf = shareIdOfCode;
const SHARE_IDS = Object.fromEntries(Object.keys(TYPES).map((code) => [shareIdOf(code), code]));

// 유형 공통 형식(명세 9-1). 4글자 코드 · 밈 장면 설명(scene)은 응답에 넣지 않는다
function toType(code) {
  const { name, character, tolerance, oneLiner, meme, daily, verdict } = TYPES[code];
  const number = String(imageNumberOf(code)).padStart(2, '0');
  return {
    shareId: shareIdOf(code),
    name,
    character,
    tolerance,
    oneLiner,
    meme,
    dailyDescription: daily,
    verdictDescription: verdict,
    imageUrl: `/images/characters/personality/${number}.webp`,
  };
}

// 결과 공통 형식(명세 9-1). completedExperienceCount는 v0.50 전 저장값이면 null
function toResult({ typeCode, axisScores, testedAt, completedExperienceCount = null }) {
  return { type: toType(typeCode), axisScores, testedAt, completedExperienceCount };
}

// 응시 기록(오래된 순). v0.50 전 목 저장값(현재 결과 하나)만 있으면 그것을 첫 기록으로 본다
function loadLog() {
  if (memoryLog) return memoryLog;
  try {
    const log = JSON.parse(localStorage.getItem(logKey));
    if (Array.isArray(log)) return log.filter((entry) => TYPES[entry?.typeCode] && typeof entry.testedAt === 'string');
  } catch { /* 저장소를 못 쓰면 아래로 */ }
  const saved = savedResult();
  return saved ? [saved] : [];
}

// 현재 결과 = 가장 최근 응시(저장소를 못 써도 이번 실행 동안의 기록에서 읽는다)
const currentResult = () => loadLog().at(-1) ?? null;

function saveLog(log) {
  try {
    localStorage.setItem(logKey, JSON.stringify(log));
    memoryLog = null;
  } catch {
    memoryLog = log;
  }
}

// 저장된 결과가 유효할 때만 돌려준다. testedAt이 없는 이전 버전 저장값은 없는 것으로 보고 다시 응시하게 한다
function savedResult() {
  const saved = load();
  return saved && TYPES[saved.typeCode] && typeof saved.testedAt === 'string' ? saved : null;
}

// P-1 문항 조회. 축 · 글자는 내려 주지 않는다(채점은 서버)
export async function getPersonalityQuestions() {
  await delay();
  return structuredClone({
    questions: QUESTIONS.map(({ id, scenario, content, a, b }) => ({ id, scenario, content, optionA: a.text, optionB: b.text })),
  });
}

// P-2 응답 제출. answers = [{ questionId, value }] (value 0 ~ 4: 매우 A · 조금 A · 보통 · 조금 B · 매우 B).
// baseline: 성향카드 다시 정하기(사용자당 1회, 이미 썼으면 409 BASELINE_RETAKE_USED). 테스트한 적이 없으면 첫 테스트로 처리한다(명세 v0.50)
export async function submitPersonality(answers, { baseline = false } = {}) {
  await delay();
  const byId = {};
  (Array.isArray(answers) ? answers : []).forEach((answer) => { byId[answer.questionId] = answer.value; });
  const complete = QUESTIONS.every((question) => Number.isInteger(byId[question.id]) && byId[question.id] >= 0 && byId[question.id] <= 4);
  if (!complete) fail('VALIDATION_ERROR');
  const log = loadLog();
  const retake = baseline && log.length > 0;
  if (retake && log.some((entry) => entry.baselineRetake)) fail('BASELINE_RETAKE_USED');
  const result = { ...score(byId), testedAt: new Date().toISOString(), completedExperienceCount: profileStats().completed };
  try {
    localStorage.setItem(storageKey, JSON.stringify(result));
  } catch { /* 사생활 보호 모드 등에서 저장하지 못해도 제출 결과는 돌려준다 */ }
  saveLog([...log, { ...result, baselineRetake: retake }]);
  return toResult(result);
}

// P-3 내 성향 결과 조회. 아직 테스트를 하지 않았으면 404 PERSONALITY_RESULT_NOT_FOUND
export async function getMyPersonality() {
  await delay();
  const saved = currentResult();
  if (!saved) fail('PERSONALITY_RESULT_NOT_FOUND');
  return toResult(saved);
}

// P-4 공유된 결과용 유형 설명 조회 (개인 응답 · 점수 없음). 모르는 해시면 404 PERSONALITY_TYPE_NOT_FOUND
export async function getPersonalityType(shareId) {
  await delay();
  const key = String(shareId);
  if (!Object.hasOwn(SHARE_IDS, key)) fail('PERSONALITY_TYPE_NOT_FOUND'); // constructor · __proto__ 같은 상속 속성은 없는 유형이다
  return toType(SHARE_IDS[key]);
}

// 체험별 4축 점수 목 (명세 P-5 예시와 같은 모양). 실제로는 체험 → 성향 저장(7.6.7)이 만든다.
// 사건 목록 목(caseListMockData) 9건을 완료 순서대로 한 번씩 체험한 사용자로 맞춘다. 점수는 축별 감경 · 가중 개수의 비율이라
// 0 · 1로 튀지 않는 분수이고, 고르지 않은 축은 null이다. 뒤로 갈수록 사과와 진정성 · 질서와 기회는 엄격 쪽, 잘잘못의 기준은 관대 쪽으로 옮겨 간다
const AXIS_KEYS = ['APOLOGY_SINCERITY', 'FAULT_STANDARD', 'PRINCIPLE_RELATION', 'ORDER_OPPORTUNITY'];
const HISTORY_SCORES = [
  // [caseId, 완료 시각, 사과와 진정성, 잘잘못의 기준, 원칙과 관계, 질서와 기회]
  [1, '2026-10-06T21:10:00+09:00', 0.67, 0.5, 0.75, 0.5],
  [2, '2026-10-08T20:40:00+09:00', 0.75, 0.33, 0.67, 0.6],
  [3, '2026-10-09T22:05:00+09:00', 0.5, 0.4, null, 0.5],
  [4, '2026-10-11T19:30:00+09:00', 0.6, 0.5, 0.6, 0.4],
  [5, '2026-10-13T21:50:00+09:00', 0.4, 0.6, 0.5, 0.5],
  [6, '2026-10-15T20:15:00+09:00', 0.5, 0.67, 0.67, 0.33],
  [7, '2026-10-17T22:30:00+09:00', 0.33, 0.67, 0.5, 0.25],
  [8, '2026-10-19T21:00:00+09:00', 0.25, 0.75, 0.67, 0.33],
  [9, '2026-10-20T20:20:00+09:00', 0.4, 0.6, null, 0.4],
];
const mockHistory = HISTORY_SCORES.map(([caseId, completedAt, ...scores]) => ({
  caseId,
  title: mockCases.find((item) => item.caseId === caseId).title,
  completedAt,
  axisScores: Object.fromEntries(AXIS_KEYS.map((key, index) => [key, scores[index]])),
}));

// 체험한 사건 목록 목. 실제로는 마이페이지 API가 체험 기록에서 만든다 (사건마다 최근 회차 하나, 명세 P-5). 위 체험 기록과 같은 사건들이다
const mockExperiences = mockHistory.map(({ caseId, title, completedAt }) => ({
  caseId, title, crimeType: mockCases.find((item) => item.caseId === caseId).crimeType, status: 'COMPLETED', attemptNo: 1, updatedAt: completedAt,
}));
// "내가 체험한 사건" 목록의 페이지 나누기(10건씩) 확인용 추가 목. 사건은 9개뿐이라 2 ~ 9번 사건을 이름만 바꿔 되풀이한다(제목을 누르면 원래 사건으로 간다).
// 체험 기록(history) · 성향 기록에는 넣지 않으므로 성향 그래프 · 현재 성향은 그대로다. 결과는 사건 번호로 찾아 원래 사건의 결과가 나오므로, hasResult: false로 "내 결과 보기"를 숨긴다
const EXTRA_CASE_IDS = [2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5, 6, 7];
const extraExperiences = EXTRA_CASE_IDS.map((caseId, index) => {
  const { title, crimeType } = mockCases.find((item) => item.caseId === caseId);
  return { caseId, title: `${title} (목 예시 ${index + 1})`, crimeType, status: 'COMPLETED', attemptNo: 1, hasResult: false, updatedAt: new Date(Date.UTC(2026, 8, 30 - index, 12)).toISOString() };
});


// 현재 판결 성향은 최근 체험 이만큼의 평균이다(명세 P-5, 성향 산식 4장)
const RECENT_COUNT = 3;

// 축마다 값이 있는 체험의 평균. 값이 하나도 없는 축은 null (명세 P-5)
function averageAxes(history) {
  const axisScores = {};
  AXES.forEach((axis) => {
    const values = history.map((entry) => entry.axisScores[axis.key]).filter((value) => typeof value === 'number');
    axisScores[axis.key] = values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
  });
  return axisScores;
}

// 도전과제 판정에 쓰는 값(P-5의 profileDeco · achievements · unlocked). 완료한 사건 수는 체험 기록 목에서 센다
const profileStats = () => ({
  completed: new Set(mockExperiences.filter((item) => item.status === 'COMPLETED').map((item) => item.caseId)).size,
  hasType: currentResult() ? 1 : 0,
});
setStatsReader(profileStats);

// 현재 판결 성향: 최근 3건 평균. 네 축이 다 있을 때만 유형(글자는 0.5 이상이면 관대, 산식 3-3), 축이 모두 비면 null
function judgmentOf(history) {
  const recent = history.slice(-RECENT_COUNT); // 목 기록은 완료 순서대로다
  if (recent.length === 0) return null;
  const axisScores = averageAxes(recent);
  const values = AXES.map((axis) => axisScores[axis.key]);
  if (values.every((value) => value === null)) return null;
  const code = values.every((value) => value !== null) ? AXES.map((axis, index) => (values[index] >= 0.5 ? axis.lenient.letter : axis.strict.letter)).join('') : null;
  return { axisScores, type: code && TYPES[code] ? toType(code) : null, experienceCount: recent.length };
}

// 초심(처음 성향, 명세 v0.50): 첫 테스트. 테스트 전이면 첫 판결(완료 순서로 처음 3건 평균, 유형이 있을 때만)
function initialOf(log, history) {
  if (log.length > 0) return { source: 'TEST', ...toResult(log[0]) };
  const first = judgmentOf(history.slice(0, RECENT_COUNT));
  return first?.type ? { source: 'JUDGMENT', ...first } : null;
}

const historyLine = ({ caseId, title, completedAt, axisScores }) => ({ caseId, title, completedAt, axisScores });

// P-5 마이페이지 조회. 기록이 없어도 404가 아니라 빈 200이다. 종합 판결 성향 · 성향 변화 흐름은 체험 → 성향 저장(7.6.7) 전에는
// 실제로 null · 빈 배열이다. 목은 화면 확인용으로 체험 기록 목을 채워 둔다. 기록이 없는 상태는 `?empty=history`로 볼 수 있다
export async function getMyPage() {
  await delay();
  const saved = currentResult();
  const noHistory = new URLSearchParams(globalThis.location?.search ?? '').get('empty') === 'history';
  const history = noHistory ? [] : mockHistory;
  const log = loadLog();
  const retake = log.find((entry) => entry.baselineRetake) ?? null;
  return structuredClone({
    personality: saved ? toResult(saved) : null,
    initialPersonality: initialOf(log, history),
    baselineRetake: { available: log.length > 0 && !retake, result: retake ? toResult(retake) : null },
    judgmentPersonality: judgmentOf(history),
    // 사건별 유형(history[].type, BE-100): 서버와 같이 그 체험 하나만으로 낸 종합의 유형이다.
    // 주의: judgmentOf는 평균 점수를 0.5와 비교해 글자를 정하고, 서버는 정확한 분수의 부호로 정한다(산식 7장).
    // 체험 한 건이면 두 방식의 결과가 같지만, 최근 3건 평균(judgmentPersonality)은 0.5 근처에서 서버와 다를 수 있다(PR #246 리뷰)
    history: history.map((entry) => ({ ...historyLine(entry), type: judgmentOf([entry])?.type ?? null })),
    experiences: [...mockExperiences, ...extraExperiences],
    ...mockProfileView(profileStats()),
  });
}
