import { button, element } from '../../components/siteLayout.js';
import { imageOf } from '../../components/stage.js';
import { PERSONALITY_LINES } from '../../content/personalityLines.js';
import { canTurnFolder, guide, markFolderTurn, renderFolder, setFolderTurnFrom, sheetMessage, takeFolderTurn } from './personalityParts.js';
import { createTestFlow } from './personalityFlow.js';

// S-15 판결 성향 테스트 — 시작 안내 + 문항 (WBS 7.6.1 콘텐츠 · 7.6.5 화면, FE-42 디자인). 한 화면에 한 문항, 5칸 선택지.
// 결과 전까지는 이전 문항으로 돌아가 답을 바꿀 수 있다(user-flow 3-2). 문항 문구에는 법률 용어를 쓰지 않는다.
// 칸 값은 0 ~ 4 (매우 A · 조금 A · 보통 · 조금 B · 매우 B). 점수 계산은 서버가 한다.
// 낭독기에서도 어떤 행동인지 알 수 있게, 라벨에 그 칸이 가리키는 행동 문장을 잇는다 (side: 'optionA' · 'optionB')
// 고르는 방법 · 넘어가기 · 숫자 키 · 제출 규칙은 personalityFlow.js(신문 면 /test/paper와 같이 쓴다). 이 파일은 서류철 화면만 그린다.
const SCALE = [
  { value: 0, degree: '매우', side: 'optionA' },
  { value: 1, degree: '조금', side: 'optionA' },
  { value: 2, degree: '보통', side: null },
  { value: 3, degree: '조금', side: 'optionB' },
  { value: 4, degree: '매우', side: 'optionB' },
];

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

export async function renderPersonalityPage(container, { api, navigate, from, baseline = false, deskY = 0 }) {
  // 검사지는 철을 가로로 눕혀 둔다. 문항과 표지 그림을 먼저 받아 두고 철을 그려, 올라오는 동안 시작 화면이 이미 보이게 한다(personality.css)
  let root, label, body;
  const active = () => container.contains(root);
  const flow = createTestFlow({ api, isActive: active, baseline }); // 진행 규칙(답 · 넘기기 · 숫자 키 · 제출)은 신문 면(/test/paper)과 같다
  const { answers } = flow;
  const here = window.location.pathname;
  const stillHere = () => window.location.pathname === here; // 문항을 받는 사이 다른 화면으로 가면 새 화면을 덮어쓰지 않는다
  // 책상의 서류철에서 왔으면 철이 아래에서 올라온다. 표시는 바로 지워 새로고침 · 뒤로 가기로는 되풀이하지 않는다
  const rise = from === 'desk';
  if (rise) history.replaceState({ ...history.state, extras: {} }, '');
  // 공유된 결과지에서 "나도 해 보기"로 왔으면 세운 철이 눕는다. 값은 한 번만 읽어 새로고침으로는 되풀이하지 않는다(책상에서 올라올 때는 올라오는 연출이 먼저)
  const turnFrom = rise ? null : takeFolderTurn();
  const openFolder = () => {
    ({ root, label, body } = renderFolder(container, { navigate, sheetLabel: '문항지', orientation: 'landscape', rise, deskY }));
    root.classList.add('is-test');
    if (turnFrom && canTurnFolder()) {
      setFolderTurnFrom(root.querySelector('.test-folder'), turnFrom);
      root.classList.add('is-turning');
      root.addEventListener('animationend', (event) => {
        if (event.animationName === 'folder-content-in') root.classList.remove('is-turning');
      });
    }
    // 숫자 1 ~ 5 = 왼쪽부터 다섯 칸. 철이 생긴 뒤에 단다(문항을 받는 중에는 아직 철이 없다)
    flow.listenKeys((value) => {
      const panel = body.querySelector('.test-question');
      if (!panel?.pickByKey) return false;
      panel.pickByKey(value);
      return true;
    });
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
      flow.setQuestions(questions);
    } catch {
      if (!stillHere()) return;
      if (!root) openFolder();
      if (active()) sheetMessage(body, '문항을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.', { retry: retryLoad, alert: true });
      return;
    }
    if (!stillHere()) return;
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
    flow.leave();
    measureOnce();
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
    fitFolder();
  }

  let pickedByPointer = false;
  // 표지와 문항지가 같은 크기의 철이 되게, 표지 · 모든 문항 중 가장 큰 높이를 철의 최소 높이로 잡아 둔다(데스크톱)
  let folderMin = 0;
  let measured = false;
  const desktop = () => window.matchMedia('(min-width: 721px)').matches;
  function fitFolder() {
    const folder = body.closest('.test-folder');
    if (!folder || !desktop()) return;
    folderMin = Math.max(folderMin, folder.offsetHeight);
    folder.style.minHeight = `${folderMin}px`;
  }
  // 처음 한 번 모든 문항을 그려 높이를 잰다(그리는 사이 화면은 갱신되지 않는다)
  function measureOnce() {
    if (measured || !desktop()) return;
    measured = true;
    questions.forEach((_, i) => renderQuestion(i));
  }

  // 답안 칸: 답한 문항 · 지금 문항 · 앞 문항을 모두 답한 다음 문항만 눌러서 갈 수 있다(건너뛰기 없음)
  // fresh = 방금 처음 답한 문항 id(그 칸만 체크를 긋는다)
  function answerGrid(index, fresh) {
    const nav = element('nav', 'answer-grid');
    nav.setAttribute('aria-label', '문항 진행');
    const count = element('p', 'answer-count');
    count.append(element('b', '', String(index + 1)), ` / ${questions.length}`);
    const cells = element('ol', 'answer-cells');
    questions.forEach((question, i) => {
      const item = element('li');
      const done = answers.has(question.id);
      const cell = button('', () => renderQuestion(i, { direction: i < index ? -1 : 1 }), `answer-cell${done ? ' is-done' : ''}${question.id === fresh ? ' is-fresh' : ''}${i === index ? ' is-current' : ''}`);
      cell.append(element('span', 'answer-no', String(i + 1)));
      if (done) cell.insertAdjacentHTML('beforeend', CHECK_SVG);
      cell.setAttribute('aria-label', `${i + 1}번 문항${done ? ', 답함' : ''}`);
      if (i === index) cell.setAttribute('aria-current', 'step');
      cell.disabled = !flow.canReach(index, i);
      item.append(cell);
      cells.append(item);
    });
    nav.append(count, cells);
    return nav;
  }

  function renderQuestion(index, { direction = 1 } = {}) {
    pickedByPointer = false; // 이미 고른 칸을 다시 눌러 남은 표시가 다음 문항으로 넘어가지 않게
    measureOnce();
    flow.enter(index);
    label.textContent = '문항지';
    const question = questions[index];
    const last = flow.isLast(index);
    const panel = element('div', 'test-question');
    panel.style.setProperty('--enter-x', `${direction * 14}px`);

    // legend는 안에 다른 칸을 함께 묶어 가운데로 놓을 수 없어서, fieldset 대신 role=group으로 묶는다
    const fieldset = element('div', 'test-fieldset');
    fieldset.setAttribute('role', 'group');
    const legend = element('div', 'test-scenario');
    legend.id = `question-${question.id}-text`;
    fieldset.setAttribute('aria-labelledby', legend.id);
    legend.tabIndex = -1; // 문항이 바뀌면 여기로 포커스를 옮겨 낭독기가 새 문항을 읽는다
    legend.append(element('span', 'test-situation', question.content));

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
    options.addEventListener('keydown', () => { pickedByPointer = false; }); // 화살표로 옮길 때는 넘어가지 않는다
    scale.append(near('A'), options, near('B'));
    const lead = element('div', 'test-lead'); // 문항 + A/B 칸. 선택 칸 위 공간의 세로 중앙
    lead.append(legend, ends);
    fieldset.append(lead, scale);

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
    fitFolder();
    legend.focus({ preventScroll: true });

    function pick(value, byKey = false) {
      const first = flow.answer(index, value, { advance: pickedByPointer || byKey, goNext });
      pickedByPointer = false;
      next.disabled = false;
      // 처음 답했으면 답안 칸을 다시 그려 체크를 단다(포커스는 지금 칸에 남는다)
      if (first) body.querySelector('.answer-grid')?.replaceWith(answerGrid(index, question.id));
    }

    function goNext() {
      flow.next(index, { show: renderQuestion, submit });
    }

    panel.pickByKey = (value) => {
      const input = options.querySelector(`input[value="${value}"]`);
      input.checked = true;
      input.focus({ preventScroll: true });
      pick(value, true);
    };
  }

  async function submit() {
    label.textContent = '답안 정리';
    const loading = element('div', 'test-message is-scoring');
    loading.setAttribute('role', 'status');
    const duo = element('div', 'test-duo');
    duo.append(guide('naero', 'read-thinking'), guide('nambul', 'read-thinking'));
    loading.append(duo, element('p', '', '답안을 정리하는 중이에요.'));
    body.replaceChildren(loading);
    try {
      await flow.submit();
    } catch (error) {
      if (active() && error?.code === 'BASELINE_RETAKE_USED') { // 다시 정하기를 이미 썼다(다른 탭 등). 다시 보내도 같으니 마이페이지로 안내한다
        sheetMessage(body, '재심은 이미 한 번 청구했어요.', { alert: true, action: { label: '마이페이지로', run: () => navigate('me') } });
        return;
      }
      if (active()) {
        sheetMessage(body, '결과를 저장하지 못했어요. 고른 답은 그대로 있어요.', { retry: submit, alert: true });
        body.querySelector('.test-message').append(button('마지막 문항으로', () => renderQuestion(questions.length - 1), 'secondary-button'));
      }
      return;
    }
    if (active()) {
      flow.stopKeys();
      markFolderTurn(body.closest('.test-folder')); // 결과지는 눕힌 철을 같은 자리 · 크기에서 돌려 세우며 보여 준다
      navigate('testResult');
    }
  }

  // 주소로 바로 들어오면 철을 먼저 펴고 불러오는 중임을 알린다(빈 화면에 푸터만 보이지 않게, FE-56).
  // 책상에서 올라올 때는 그대로 문항을 받은 뒤 철을 그린다(그동안 책상이 보이고, 올라오는 동안 표지가 이미 보인다)
  if (!rise) {
    openFolder();
    sheetMessage(body, '문항을 불러오는 중이에요.');
  }
  await load();
}
