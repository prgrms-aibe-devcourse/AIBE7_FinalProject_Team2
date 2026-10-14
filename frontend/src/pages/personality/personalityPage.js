import { button, element, renderSiteHeader } from '../../components/siteLayout.js';

// S-15 판결 성향 테스트 — 시작 안내 + 문항 (WBS 7.6.1 콘텐츠 · 7.6.5 화면). 한 화면에 한 문항, 5칸 선택지.
// 결과 전까지는 이전 문항으로 돌아가 답을 바꿀 수 있다(user-flow 3-2). 문항 문구에는 법률 용어를 쓰지 않는다.
// 칸 값은 0 ~ 4 (매우 A · 조금 A · 보통 · 조금 B · 매우 B). 점수 계산은 서버가 한다.
// 낭독기에서도 어떤 행동인지 알 수 있게, 라벨에 그 칸이 가리키는 행동 문장을 잇는다 (side: 'optionA' · 'optionB')
const SCALE = [
  { value: 0, degree: '매우', side: 'optionA' },
  { value: 1, degree: '조금', side: 'optionA' },
  { value: 2, degree: '보통', side: null },
  { value: 3, degree: '조금', side: 'optionB' },
  { value: 4, degree: '매우', side: 'optionB' },
];
const SCENARIO_LABELS = { FRIEND: '친구', FAMILY: '가족', WORK: '직장', NEIGHBOR: '이웃' };

export async function renderPersonalityPage(container, { api, navigate }) {
  const root = element('div', 'personality-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);

  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }));
  const main = element('main', 'personality-main');
  root.append(main);

  function showMessage(text, retry) {
    const panel = element('div', 'message-panel');
    panel.setAttribute('role', 'status');
    panel.append(element('p', '', text));
    if (retry) panel.append(button('다시 시도', retry, 'primary-button'));
    main.replaceChildren(panel);
  }

  let questions;
  async function load() {
    showMessage('문항을 불러오는 중이에요.');
    try {
      ({ questions } = await api.getPersonalityQuestions());
    } catch {
      if (active()) showMessage('문항을 불러오지 못했어요.', load);
      return;
    }
    if (active()) renderIntro();
  }

  function renderIntro() {
    const card = element('section', 'personality-intro');
    card.append(
      element('p', 'personality-kicker', '판결 성향 테스트'),
      element('h1', '', '잘잘못을 따질 때, 나는 무엇을 먼저 볼까?'),
      element('p', 'personality-lead', `일상에서 마주칠 만한 ${questions.length}가지 상황에 답하면, 내가 중요하게 보는 가치에 맞는 판결 성향 유형이 나와요.`),
    );
    const notes = element('ul', 'personality-notes');
    [
      `약 3분, ${questions.length}문항이에요. 법을 몰라도 괜찮아요.`,
      '정답은 없어요. 어느 유형이 옳다는 뜻도 아니에요.',
      '결과가 나오기 전까지는 이전 문항으로 돌아가 답을 바꿀 수 있어요.',
    ].forEach((text) => notes.append(element('li', '', text)));
    card.append(notes, button('시작하기', () => renderQuestion(0), 'primary-button'));
    main.replaceChildren(card);
  }

  const answers = new Map(); // questionId → value

  function renderQuestion(index) {
    const question = questions[index];
    const panel = element('section', 'personality-question');

    const progress = element('div', 'personality-progress');
    progress.append(
      element('span', '', `${index + 1} / ${questions.length}`),
      (() => {
        const track = element('div', 'personality-progress-track');
        const fill = element('span');
        fill.style.width = `${((index + 1) / questions.length) * 100}%`;
        track.append(fill);
        return track;
      })(),
    );

    const fieldset = element('fieldset', 'personality-fieldset');
    const legend = element('legend', 'personality-scenario');
    legend.append(element('span', 'personality-chip', SCENARIO_LABELS[question.scenario]), question.content);
    fieldset.append(legend);

    // 양 끝 행동 문장. 왼쪽 = 매우 A, 오른쪽 = 매우 B
    const ends = element('div', 'personality-ends');
    ends.append(element('p', '', question.optionA), element('p', '', question.optionB));
    const scale = element('div', 'personality-scale');
    scale.setAttribute('role', 'presentation');
    SCALE.forEach(({ value, degree, side }) => {
      const label = side ? `${degree}: ${question[side]}` : `${degree}: 어느 쪽도 아니에요`;
      const option = element('label', `personality-option step-${value}`);
      const input = element('input');
      input.type = 'radio';
      input.name = `question-${question.id}`;
      input.value = String(value);
      input.checked = answers.get(question.id) === value;
      input.addEventListener('change', () => {
        answers.set(question.id, value);
        next.disabled = false; // 키보드 화살표로 칸을 옮겨도 넘어가지 않도록 "다음"은 직접 누른다
      });
      option.append(input, element('span', 'personality-dot'), element('span', 'visually-hidden', label));
      scale.append(option);
    });
    fieldset.append(ends, scale);

    const actions = element('div', 'personality-actions');
    const prev = button('이전', () => (index === 0 ? renderIntro() : renderQuestion(index - 1)), 'secondary-button');
    const next = button(index === questions.length - 1 ? '결과 보기' : '다음', goNext, 'primary-button');
    next.disabled = !answers.has(question.id);
    actions.append(prev, next);

    panel.append(progress, fieldset, actions);
    main.replaceChildren(panel);

    function goNext() {
      if (!answers.has(question.id)) return;
      if (index < questions.length - 1) renderQuestion(index + 1);
      else submit();
    }
  }

  async function submit() {
    showMessage('결과를 분석하는 중이에요.');
    try {
      await api.submitPersonality(questions.map((question) => ({ questionId: question.id, value: answers.get(question.id) })));
    } catch {
      if (active()) showMessage('결과를 저장하지 못했어요.', submit);
      return;
    }
    if (active()) navigate('testResult');
  }

  await load();
}
