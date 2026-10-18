import { button, element } from '../../components/siteLayout.js';
import { imageOf } from '../../components/stage.js';
import { PERSONALITY_LINES } from '../../content/personalityLines.js';
import { guide, markFolderTurn, renderFolder, sheetMessage } from './personalityParts.js';

// S-15 판결 성향 테스트 — 시작 안내 + 문항 (WBS 7.6.1 콘텐츠 · 7.6.5 화면, FE-42 디자인). 한 화면에 한 문항, 5칸 선택지.
// 결과 전까지는 이전 문항으로 돌아가 답을 바꿀 수 있다(user-flow 3-2). 문항 문구에는 법률 용어를 쓰지 않는다.
// 칸 값은 0 ~ 4 (매우 A · 조금 A · 보통 · 조금 B · 매우 B). 점수 계산은 서버가 한다.
// 낭독기에서도 어떤 행동인지 알 수 있게, 라벨에 그 칸이 가리키는 행동 문장을 잇는다 (side: 'optionA' · 'optionB')
// 고르는 방법(FE-26 목업): 칸을 누르거나 숫자 1 ~ 5를 치면 바로 다음 문항으로 넘어간다. 화살표로 칸을 옮길 때는 넘어가지 않고 "다음"을 직접 누른다.
// 마지막 문항은 자동으로 넘기지 않는다 — 결과는 "결과 보기"를 눌러야 제출된다.
const SCALE = [
  { value: 0, degree: '매우', side: 'optionA' },
  { value: 1, degree: '조금', side: 'optionA' },
  { value: 2, degree: '보통', side: null },
  { value: 3, degree: '조금', side: 'optionB' },
  { value: 4, degree: '매우', side: 'optionB' },
];
const SCENARIO_LABELS = { FRIEND: '친구', FAMILY: '가족', WORK: '직장', NEIGHBOR: '이웃' };
const ADVANCE_MS = 260; // 고른 칸이 칠해지는 것을 보고 넘어가도록
const MIN_LOADING_MS = 900; // 결과 분석 연출(user-flow 3-2)이 깜빡이고 사라지지 않도록
const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// 안내 캐릭터 + 문항별 한 줄 대사(content/personalityLines.js). 앞 절반은 내로, 뒤 절반은 남불(대사에 적힌 캐릭터가 우선).
// 시작 · 절반 · 마지막만 자세가 바뀐다
function guideFor(question, index, total) {
  const { who = index < total / 2 ? 'naero' : 'nambul', line } = PERSONALITY_LINES[question.id] ?? {};
  const look = index === 0 ? 'guide-neutral'
    : index === total - 1 ? 'guide-happy'
      : index === Math.floor(total / 2) ? 'cheer-cheer'
        : 'idle-neutral';
  return guide(who, look, line);
}

// 그림을 받아 둔다. 늦거나 실패해도 연출을 막지 않게 한도를 둔다
const PRELOAD_LIMIT_MS = 1500;
function preload(src) {
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = image.onerror = () => resolve();
    image.src = src;
    window.setTimeout(resolve, PRELOAD_LIMIT_MS);
  });
}

// 답안 칸의 펜 체크 표시
const CHECK_SVG = '<svg viewBox="0 0 20 20" aria-hidden="true" focusable="false"><path d="M4 10.5 L8.2 14.5 L16 5.5"/></svg>';

export async function renderPersonalityPage(container, { api, navigate }) {
  // 검사지는 철을 가로로 눕혀 둔다. 문항과 표지 그림을 먼저 받아 두고 철을 그려, 올라오는 동안 시작 화면이 이미 보이게 한다(personality.css)
  let root, label, body;
  const active = () => container.contains(root);
  const keys = new AbortController(); // 숫자 키 단축키. 화면을 떠나면 뗀다
  const openFolder = () => {
    ({ root, label, body } = renderFolder(container, { navigate, sheetLabel: '문항지', orientation: 'landscape' }));
    root.classList.add('is-test');
  };
  const fetchQuestions = async () => {
    const [{ questions: list }] = await Promise.all([
      api.getPersonalityQuestions(),
      ...[['naero', 'guide-happy'], ['nambul', 'guide-neutral']].map(([who, look]) => preload(imageOf(who, look))),
    ]);
    return list;
  };

  let questions;
  async function load() {
    try {
      questions = await fetchQuestions();
    } catch {
      if (!root) openFolder();
      if (active()) sheetMessage(body, '문항을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.', { retry: retryLoad, alert: true });
      return;
    }
    if (!root) openFolder();
    if (active()) renderIntro();
    // 문항 · 답안 정리에서 쓸 안내 컷을 미리 받아 둔다(원본 레포 PNG라 처음 뜰 때 늦다)
    [['naero', 'idle-neutral'], ['nambul', 'idle-neutral'], ['nambul', 'cheer-cheer'], ['nambul', 'guide-happy'], ['naero', 'read-thinking'], ['nambul', 'read-thinking']]
      .forEach(([who, look]) => { new Image().src = imageOf(who, look); });
  }

  async function retryLoad() {
    sheetMessage(body, '문항을 불러오는 중이에요.');
    await load();
  }

  function renderIntro() {
    label.textContent = '표지';
    const intro = element('div', 'test-intro');
    const duo = element('div', 'test-duo');
    duo.append(guide('naero', 'guide-happy'), guide('nambul', 'guide-neutral'));
    const notes = element('section', 'test-notes');
    notes.setAttribute('aria-labelledby', 'test-notes-title');
    const notesTitle = element('h2', '', '적는 방법');
    notesTitle.id = 'test-notes-title';
    const list = element('ol');
    [
      `일상에서 마주칠 만한 ${questions.length}가지 상황이에요. 약 3분, 법을 몰라도 괜찮아요.`,
      '두 행동 가운데 내 마음이 더 가까운 쪽을 다섯 칸 중에 골라 주세요.',
      '정답은 없어요. 어느 유형이 옳다는 뜻도 아니에요.',
    ].forEach((text) => list.append(element('li', '', text)));
    notes.append(notesTitle, list);
    const start = button('시작하기', () => renderQuestion(0), 'primary-button test-start');
    intro.append(
      duo,
      element('h1', '', '잘잘못을 따질 때, 나는 무엇을 먼저 볼까?'),
      element('p', 'test-lead', `${questions.length}가지 상황에 답하면, 내가 중요하게 보는 가치에 맞는 판결 성향 유형이 나와요.`),
      notes,
      start,
    );
    body.replaceChildren(intro);
  }

  const answers = new Map(); // questionId → value
  let current = 0;
  let pickedByPointer = false;
  let advanceTimer = 0;

  // 답안 칸: 답한 문항 · 지금 문항 · 앞 문항을 모두 답한 다음 문항만 눌러서 갈 수 있다(건너뛰기 없음)
  // fresh = 방금 처음 답한 문항 id(그 칸만 체크를 긋는다)
  function answerGrid(index, fresh) {
    const nav = element('nav', 'answer-grid');
    nav.setAttribute('aria-label', '문항 진행');
    const count = element('p', 'answer-count');
    count.append(element('b', '', String(index + 1)), ` / ${questions.length}`);
    const cells = element('ol', 'answer-cells');
    const reachable = (i) => i <= index || questions.slice(0, i).every((q) => answers.has(q.id));
    questions.forEach((question, i) => {
      const item = element('li');
      const done = answers.has(question.id);
      const cell = button('', () => renderQuestion(i, { direction: i < index ? -1 : 1 }), `answer-cell${done ? ' is-done' : ''}${question.id === fresh ? ' is-fresh' : ''}${i === index ? ' is-current' : ''}`);
      cell.append(element('span', 'answer-no', String(i + 1)));
      if (done) cell.insertAdjacentHTML('beforeend', CHECK_SVG);
      cell.setAttribute('aria-label', `${i + 1}번 문항${done ? ', 답함' : ''}`);
      if (i === index) cell.setAttribute('aria-current', 'step');
      cell.disabled = !reachable(i);
      item.append(cell);
      cells.append(item);
    });
    nav.append(count, cells);
    return nav;
  }

  function renderQuestion(index, { direction = 1 } = {}) {
    window.clearTimeout(advanceTimer);
    current = index;
    label.textContent = '문항지';
    const question = questions[index];
    const last = index === questions.length - 1;
    const panel = element('div', 'test-question');
    panel.style.setProperty('--enter-x', `${direction * 14}px`);

    const fieldset = element('fieldset', 'test-fieldset');
    const legend = element('legend', 'test-scenario');
    legend.tabIndex = -1; // 문항이 바뀌면 여기로 포커스를 옮겨 낭독기가 새 문항을 읽는다
    legend.append(element('span', 'test-chip', SCENARIO_LABELS[question.scenario] ?? '일상'), element('span', 'test-situation', question.content));
    fieldset.append(legend);

    // 양 끝 행동 문장. 왼쪽 = A, 오른쪽 = B
    const ends = element('div', 'test-ends');
    [['A', question.optionA], ['B', question.optionB]].forEach(([mark, text]) => {
      const end = element('p', 'test-end');
      end.append(element('b', '', mark), element('span', '', text));
      ends.append(end);
    });

    const scale = element('div', 'test-scale');
    const near = (side) => element('span', 'test-near', `${side}에 가까워요`);
    const options = element('div', 'test-options');
    SCALE.forEach(({ value, degree, side }) => {
      const text = side ? `${degree}: ${question[side]}` : `${degree}: 어느 쪽도 아니에요`;
      const option = element('label', `test-option step-${value}`);
      const input = element('input');
      input.type = 'radio';
      input.name = `question-${question.id}`;
      input.value = String(value);
      input.checked = answers.get(question.id) === value;
      input.addEventListener('change', () => pick(value));
      const short = side ? `${degree} ${side === 'optionA' ? 'A' : 'B'}` : degree;
      option.append(input, element('span', 'test-dot'), element('span', 'test-degree', short), element('span', 'visually-hidden', text));
      options.append(option);
    });
    options.addEventListener('pointerdown', () => { pickedByPointer = true; });
    scale.append(near('A'), options, near('B'));
    fieldset.append(ends, scale);

    const actions = element('div', 'test-actions');
    const prev = button('이전', () => (index === 0 ? renderIntro() : renderQuestion(index - 1, { direction: -1 })), 'secondary-button');
    const hint = element('p', 'test-hint', last ? '다 고르면 결과를 볼 수 있어요' : '칸을 고르면 다음 문항으로 넘어가요');
    if (!last) hint.append(element('span', 'test-hint-keys', ' · 키보드 1 ~ 5'));
    const next = button(last ? '결과 보기' : '다음', goNext, 'primary-button');
    next.disabled = !answers.has(question.id);
    actions.append(prev, hint, next);

    const main = element('div', 'test-question-main');
    main.append(fieldset, actions);
    panel.append(guideFor(question, index, questions.length), main);
    body.replaceChildren(answerGrid(index), panel);
    legend.focus({ preventScroll: true });

    function pick(value) {
      const first = !answers.has(question.id);
      answers.set(question.id, value);
      next.disabled = false;
      const pointer = pickedByPointer;
      pickedByPointer = false;
      // 처음 답했으면 답안 칸을 다시 그려 체크를 단다(포커스는 지금 칸에 남는다)
      if (first) body.querySelector('.answer-grid')?.replaceWith(answerGrid(index, question.id));
      if (pointer && !last) advanceTimer = window.setTimeout(goNext, reducedMotion() ? 0 : ADVANCE_MS);
    }

    function goNext() {
      window.clearTimeout(advanceTimer);
      if (!active() || current !== index || !answers.has(question.id)) return;
      if (!last) renderQuestion(index + 1);
      else submit();
    }

    panel.pickByKey = (value) => {
      const input = options.querySelector(`input[value="${value}"]`);
      input.checked = true;
      input.focus({ preventScroll: true });
      pick(value);
      if (!last) advanceTimer = window.setTimeout(goNext, reducedMotion() ? 0 : ADVANCE_MS);
    };
  }

  // 숫자 1 ~ 5 = 왼쪽부터 다섯 칸
  document.addEventListener('keydown', (event) => {
    if (!active()) { keys.abort(); return; }
    if (event.altKey || event.ctrlKey || event.metaKey || event.isComposing) return;
    const value = Number(event.key) - 1;
    if (!Number.isInteger(value) || value < 0 || value > 4) return;
    const panel = body.querySelector('.test-question');
    if (!panel?.pickByKey) return;
    event.preventDefault();
    panel.pickByKey(value);
  }, { signal: keys.signal });

  async function submit() {
    label.textContent = '답안 정리';
    const loading = element('div', 'test-message is-scoring');
    loading.setAttribute('role', 'status');
    const duo = element('div', 'test-duo');
    duo.append(guide('naero', 'read-thinking'), guide('nambul', 'read-thinking'));
    loading.append(duo, element('p', '', '답안을 정리하는 중이에요.'));
    body.replaceChildren(loading);
    const started = performance.now();
    try {
      await api.submitPersonality(questions.map((question) => ({ questionId: question.id, value: answers.get(question.id) })));
    } catch {
      if (active()) {
        sheetMessage(body, '결과를 저장하지 못했어요. 고른 답은 그대로 있어요.', { retry: submit, alert: true });
        body.querySelector('.test-message').append(button('마지막 문항으로', () => renderQuestion(questions.length - 1), 'secondary-button'));
      }
      return;
    }
    const wait = reducedMotion() ? 0 : MIN_LOADING_MS - (performance.now() - started);
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
    if (active()) {
      keys.abort();
      markFolderTurn(); // 결과지는 눕힌 철을 돌려 세우며 보여 준다
      navigate('testResult');
    }
  }

  await load();
}
