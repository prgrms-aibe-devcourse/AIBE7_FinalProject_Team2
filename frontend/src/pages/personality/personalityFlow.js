import { reducedMotion } from '../../utils/motion.js';

// 판결 성향 테스트 진행 규칙 (FE-55, PR #190 리뷰 반영). /test(서류철, personalityPage.js)와 /test/paper(신문 면, personalityPaperPage.js)가
// 같이 쓴다 — 화면은 그리기만 하고, 답 모으기 · 넘어가기 · 숫자 키 · 제출 규칙은 여기 한 곳에서 정한다(한쪽만 고쳐 두 화면이 달라지지 않게).
// 규칙(user-flow 3-2, FE-26 목업):
// - 칸을 누르거나 숫자 1 ~ 5를 치면 고른 칸이 칠해지는 것을 보고(ADVANCE_MS) 다음 문항으로 넘어간다. 화살표로 칸을 옮길 때는 넘어가지 않는다.
// - 마지막 문항은 자동으로 넘기지 않는다 — 결과는 "결과 보기"를 눌러야 제출된다.
// - 답한 문항 · 지금 문항 · 앞 문항을 모두 답한 다음 문항만 갈 수 있다(건너뛰기 없음). 결과 전까지는 이전 문항의 답을 바꿀 수 있다.
// - 제출은 결과 분석 연출이 깜빡이고 사라지지 않게 최소 MIN_LOADING_MS 동안 보여 준다.
const ADVANCE_MS = 260;
const MIN_LOADING_MS = 900;

/**
 * @param {{ api, isActive: () => boolean }} options isActive: 화면이 아직 보이는지(떠났으면 넘기기 · 키 · 제출 뒤 이동을 멈춘다)
 */
export function createTestFlow({ api, isActive }) {
  const answers = new Map(); // questionId → 0 ~ 4 (매우 A · 조금 A · 보통 · 조금 B · 매우 B)
  const keys = new AbortController(); // 숫자 키 단축키. 화면을 떠나면 뗀다
  let questions = [];
  let current = 0;
  let timer = 0;
  let listening = false;

  const isLast = (index) => index === questions.length - 1;
  return {
    answers,
    isLast,
    setQuestions(list) { questions = list; },
    /** 문항 칸(진행 칸)으로 target 문항에 갈 수 있는지 */
    canReach: (index, target) => target <= index || questions.slice(0, target).every((question) => answers.has(question.id)),
    /** 화면이 index 문항을 그릴 때 부른다(기다리던 자동 넘기기는 취소) */
    enter(index) {
      clearTimeout(timer);
      current = index;
    },
    /** 답을 적는다. advance면 잠시 뒤 goNext(마지막 문항은 넘기지 않는다). 처음 답한 문항이면 true */
    answer(index, value, { advance = false, goNext }) {
      const { id } = questions[index];
      const first = !answers.has(id);
      answers.set(id, value);
      if (advance && !isLast(index)) timer = setTimeout(goNext, reducedMotion() ? 0 : ADVANCE_MS);
      return first;
    },
    /** 다음으로: 지금 문항에 답했을 때만. 마지막 문항이면 submit */
    next(index, { show, submit }) {
      clearTimeout(timer);
      if (!isActive() || current !== index || !answers.has(questions[index].id)) return;
      if (!isLast(index)) show(index + 1);
      else submit();
    },
    /** 숫자 1 ~ 5 = 왼쪽부터 다섯 칸. pickByKey(value)가 true를 돌려주면(지금 문항이 받았으면) 키 기본 동작을 막는다. 한 번만 단다 */
    listenKeys(pickByKey) {
      if (listening) return;
      listening = true;
      document.addEventListener('keydown', (event) => {
        if (!isActive()) { keys.abort(); return; }
        if (event.altKey || event.ctrlKey || event.metaKey || event.isComposing) return;
        const value = Number(event.key) - 1;
        if (!Number.isInteger(value) || value < 0 || value > 4) return;
        if (pickByKey(value)) event.preventDefault();
      }, { signal: keys.signal });
    },
    stopKeys: () => keys.abort(),
    /** 답을 제출한다(최소 MIN_LOADING_MS). 실패하면 throw — 고른 답은 그대로 있다 */
    async submit() {
      const started = performance.now();
      await api.submitPersonality(questions.map((question) => ({ questionId: question.id, value: answers.get(question.id) })));
      const wait = reducedMotion() ? 0 : MIN_LOADING_MS - (performance.now() - started);
      if (wait > 0) await new Promise((resolve) => { setTimeout(resolve, wait); });
    },
  };
}
