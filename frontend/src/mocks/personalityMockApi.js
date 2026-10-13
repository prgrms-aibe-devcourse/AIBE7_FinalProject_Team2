import { AXES } from '../utils/personalityAxes.js';
import { QUESTIONS, TYPES } from './personalityMockData.js';

// 판결 성향 테스트 · 마이페이지 목 API. 성향 API 명세는 7.6.3 DB 스키마 시점(10/19)에 정해지므로,
// 응답 모양은 화면에 필요한 것만 임시로 정했다. 채점은 docs/personality-scoring.md 2장을 따른다.
// 결과는 목 전용으로 브라우저 localStorage에 둔다 (실제로는 서버가 익명 ID 기준으로 저장).

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const storageKey = 'nlnb-mock-personality';
const WEIGHTS = [2, 1, 0, 1, 2]; // 매우 A · 조금 A · 보통 · 조금 B · 매우 B

// 체험한 사건 목록 목. 실제로는 마이페이지 API가 체험 기록에서 만든다
const mockExperiences = [
  { caseId: 1, title: '가상 살인 사건', status: 'COMPLETED' },
];

function fail(code, message) {
  throw { code, message };
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

function toResult({ typeCode, axisScores }) {
  return { typeCode, axisScores, type: TYPES[typeCode] };
}

// 문항 조회
export async function getPersonalityQuestions() {
  await delay();
  return structuredClone({ questions: QUESTIONS });
}

// 응답 제출. answers = [{ questionId, value }] (value 0 ~ 4: 매우 A · 조금 A · 보통 · 조금 B · 매우 B)
export async function submitPersonality(answers) {
  await delay();
  const byId = {};
  (Array.isArray(answers) ? answers : []).forEach((answer) => { byId[answer.questionId] = answer.value; });
  const complete = QUESTIONS.every((question) => Number.isInteger(byId[question.id]) && byId[question.id] >= 0 && byId[question.id] <= 4);
  if (!complete) fail('VALIDATION_ERROR', '모든 문항에 답해 주세요.');
  const result = score(byId);
  try {
    localStorage.setItem(storageKey, JSON.stringify(result));
  } catch { /* 사생활 보호 모드 등에서 저장하지 못해도 제출 결과는 돌려준다 */ }
  return toResult(result);
}

// 내 성향 결과 조회. 아직 테스트를 하지 않았으면 null
export async function getMyPersonality() {
  await delay();
  const saved = load();
  return saved && TYPES[saved.typeCode] ? toResult(saved) : null;
}

// 공유된 결과용 유형 설명 조회 (개인 응답 · 점수 없음). 모르는 코드면 null
export async function getPersonalityType(code) {
  await delay();
  const type = TYPES[String(code).toUpperCase()];
  return type ? structuredClone(type) : null;
}

// 마이페이지 조회 (테스트 결과 + 체험한 사건). 성향 변화 흐름 · 종합 성향은 아직 없다
export async function getMyPage() {
  await delay();
  const saved = load();
  return structuredClone({
    personality: saved && TYPES[saved.typeCode] ? toResult(saved) : null,
    experiences: mockExperiences,
  });
}
