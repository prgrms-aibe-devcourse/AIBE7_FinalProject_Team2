import { AXES } from '../utils/personalityAxes.js';
import { imageNumberOf, QUESTIONS, TYPES } from './personalityMockData.js';

// 판결 성향 테스트 · 마이페이지 목 API. 응답 모양은 API 명세 9장(성향 API, v0.20)의 P-1 ~ P-5를 따른다.
// 채점은 docs/personality-scoring.md 2장을 따른다. 문항의 글자 · 축은 서버 쪽 정보라 응답에 넣지 않는다.
// 결과는 목 전용으로 브라우저 localStorage에 둔다 (실제로는 서버가 익명 ID 기준으로 저장).

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const storageKey = 'nlnb-mock-personality';
const WEIGHTS = [2, 1, 0, 1, 2]; // 매우 A · 조금 A · 보통 · 조금 B · 매우 B
// 캐릭터 이미지 파일(7.6.8)이 들어오면 true로 바꾼다. 그 전에는 imageUrl이 null이고 화면은 대체 이미지를 쓴다(명세 9-1)
const IMAGES_READY = true;

// 체험한 사건 목록 목. 실제로는 마이페이지 API가 체험 기록에서 만든다 (사건마다 최근 회차 하나, 명세 P-5)
const mockExperiences = [
  { caseId: 1, title: '가상 살인 사건', crimeType: 'MURDER', status: 'COMPLETED', attemptNo: 1, updatedAt: '2026-10-14T15:00:00+09:00' },
];

const MESSAGES = {
  VALIDATION_ERROR: '모든 문항에 답해 주세요.',
  PERSONALITY_RESULT_NOT_FOUND: '성향 테스트 결과가 없어요.',
  PERSONALITY_TYPE_NOT_FOUND: '찾을 수 없는 유형이에요.',
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

// 공유 주소용 해시. 실제로는 서버가 시드로 고정한다(유형 코드를 주소에 드러내지 않는 값, 명세 9-1). 목은 코드에서 결정적으로 만든다
function shareIdOf(code) {
  let hash = 2166136261; // FNV-1a
  for (const char of `nlnb-share:${code}`) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619) >>> 0;
  }
  return hash.toString(36).padStart(7, '0') + (hash % 36).toString(36); // 8자
}
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
    imageUrl: IMAGES_READY ? `/images/characters/personality/${number}.webp` : null,
  };
}

// 결과 공통 형식(명세 9-1)
function toResult({ typeCode, axisScores, testedAt }) {
  return { type: toType(typeCode), axisScores, testedAt };
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

// P-2 응답 제출. answers = [{ questionId, value }] (value 0 ~ 4: 매우 A · 조금 A · 보통 · 조금 B · 매우 B)
export async function submitPersonality(answers) {
  await delay();
  const byId = {};
  (Array.isArray(answers) ? answers : []).forEach((answer) => { byId[answer.questionId] = answer.value; });
  const complete = QUESTIONS.every((question) => Number.isInteger(byId[question.id]) && byId[question.id] >= 0 && byId[question.id] <= 4);
  if (!complete) fail('VALIDATION_ERROR');
  const result = { ...score(byId), testedAt: new Date().toISOString() };
  try {
    localStorage.setItem(storageKey, JSON.stringify(result));
  } catch { /* 사생활 보호 모드 등에서 저장하지 못해도 제출 결과는 돌려준다 */ }
  return toResult(result);
}

// P-3 내 성향 결과 조회. 아직 테스트를 하지 않았으면 404 PERSONALITY_RESULT_NOT_FOUND
export async function getMyPersonality() {
  await delay();
  const saved = savedResult();
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

// 체험별 4축 점수 목 (명세 P-5 예시와 같은 모양). 실제로는 체험 → 성향 저장(7.6.7)이 만든다. 완료한 사건 1건 + 아래 두 건으로 화면을 확인한다
const mockHistory = [
  { caseId: 1, title: '가상 살인 사건', completedAt: '2026-10-14T15:00:00+09:00', axisScores: { APOLOGY_SINCERITY: 1.0, FAULT_STANDARD: 0.0, PRINCIPLE_RELATION: 1.0, ORDER_OPPORTUNITY: null } },
  { caseId: 2, title: '가상 사기 사건', completedAt: '2026-10-15T11:00:00+09:00', axisScores: { APOLOGY_SINCERITY: 0.5, FAULT_STANDARD: 0.25, PRINCIPLE_RELATION: 0.75, ORDER_OPPORTUNITY: 0.5 } },
  { caseId: 3, title: '가상 상해 사건', completedAt: '2026-10-16T09:00:00+09:00', axisScores: { APOLOGY_SINCERITY: 0.25, FAULT_STANDARD: 0.25, PRINCIPLE_RELATION: 0.9, ORDER_OPPORTUNITY: 0.75 } },
];

// 축마다 값이 있는 체험의 평균. 값이 하나도 없는 축은 null (명세 P-5)
function averageAxes(history) {
  const axisScores = {};
  AXES.forEach((axis) => {
    const values = history.map((entry) => entry.axisScores[axis.key]).filter((value) => typeof value === 'number');
    axisScores[axis.key] = values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
  });
  return axisScores;
}

// P-5 마이페이지 조회. 기록이 없어도 404가 아니라 빈 200이다. 종합 판결 성향 · 성향 변화 흐름은 체험 → 성향 저장(7.6.7) 전에는
// 실제로 null · 빈 배열이다. 목은 화면 확인용으로 체험 기록 목을 채워 둔다. 기록이 없는 상태는 `?empty=history`로 볼 수 있다
export async function getMyPage() {
  await delay();
  const saved = savedResult();
  const noHistory = new URLSearchParams(globalThis.location?.search ?? '').get('empty') === 'history';
  const history = noHistory ? [] : mockHistory;
  return structuredClone({
    personality: saved ? toResult(saved) : null,
    judgmentPersonality: history.length === 0 ? null : { axisScores: averageAxes(history), type: null, experienceCount: history.length },
    history: history.map(({ caseId, title, completedAt, axisScores }) => ({ caseId, title, completedAt, axisScores })),
    experiences: mockExperiences,
  });
}
