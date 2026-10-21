import { button, element } from '../../components/siteLayout.js';

// AI 판결 공개 전 로딩 · 안내 (7.7.4, REQ-128). S-05에서 판결을 확정한 직후 S-06 앞에 한 번만 보여 준다.
// 문구는 판결을 "지금 만드는 중"이라고도, "미리 만들어 둔 것"이라고도 단정하지 않고 생성 · 불러오기 양쪽으로 읽히게 쓴다 (REQ-128, 10/14 팀 결정).
// "방금 확정했는지"는 sessionStorage 표시로 안다 — 새로고침 · 결과 다시 보기에서는 보여 주지 않는다.
const MIN_SHOW_MS = 2500; // 연출이 너무 빨리 지나가지 않게 하는 최소 시간 (안내 3줄을 읽을 시간이라 문서 초안의 1초보다 길게 둔다)
const keyOf = (caseId) => `nlnb-ai-reveal-intro-${caseId}`;

/** S-05 확정 성공 직후에 부른다 */
export function markAiRevealIntro(caseId) {
  try { sessionStorage.setItem(keyOf(caseId), '1'); } catch { /* 저장소를 못 써도 안내만 건너뛴다 */ }
}

/** S-06이 열릴 때 부른다. 표시가 있었으면 지우고 true */
export function takeAiRevealIntro(caseId) {
  try {
    const marked = sessionStorage.getItem(keyOf(caseId)) === '1';
    sessionStorage.removeItem(keyOf(caseId));
    return marked;
  } catch {
    return false;
  }
}

const NOTES = [
  '같은 사건 기록만 보고 AI가 내린 판결이에요.',
  '방금 내린 내 판결과 실제 재판부의 판결은 AI가 보지 않았어요.',
  '법률 자문이 아니에요. 내 판결과 견주어 생각해 보는 참고 자료로만 봐 주세요.',
];

/**
 * @param {HTMLElement} root 화면 루트(내용을 이 안에 그린다)
 * @param {{ ready: Promise<unknown>, onContinue: () => void }} options
 *   ready      AI 판결 정보를 불러오는 요청. 성공하면 "AI 판결 보기"가 열린다. 실패 안내는 부르는 쪽(S-06)이 한다
 *   onContinue "AI 판결 보기"를 눌렀을 때
 */
export function renderAiRevealIntro(root, { ready, onContinue }) {
  const panel = element('section', 'reveal-intro');
  panel.setAttribute('aria-live', 'polite');

  const spinner = element('div', 'reveal-spinner');
  spinner.setAttribute('aria-hidden', 'true');
  const title = element('h1', '', '내 판결을 확정했어요');
  const status = element('p', 'reveal-status', 'AI 판결을 기다리는 중이에요…');
  const notes = element('ul', 'reveal-notes');
  NOTES.forEach((text) => notes.append(element('li', '', text)));
  const actions = element('div', 'reveal-actions');
  const next = button('AI 판결 보기', onContinue, 'primary-button');
  next.disabled = true;
  actions.append(next);

  panel.append(spinner, title, status, notes, actions);
  root.replaceChildren(panel);

  const minimum = new Promise((resolve) => setTimeout(resolve, MIN_SHOW_MS));
  Promise.all([ready, minimum]).then(() => {
    if (!panel.isConnected) return;
    spinner.hidden = true;
    status.textContent = 'AI 판결을 열 준비가 됐어요.';
    next.disabled = false;
    next.focus();
  }, () => { /* 요청 실패는 S-06이 처리한다 */ });
}
