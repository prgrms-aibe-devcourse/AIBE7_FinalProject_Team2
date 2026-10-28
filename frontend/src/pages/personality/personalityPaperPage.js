import { button, element, renderSiteFooter } from '../../components/siteLayout.js';
import { earButton, innerPageHead, pageIndex } from '../../components/newspaperFront.js';
import { todayLabel } from '../../utils/newspaper.js';
import { imageOf } from '../../components/stage.js';
import { PERSONALITY_LINES } from '../../content/personalityLines.js';
import { sheetMessage } from './personalityParts.js';
import { reducedMotion } from '../../utils/motion.js';
import { canGoBackInApp } from '../../app/router.js';
import { createTestFlow } from './personalityFlow.js';

// FE-55 판결 성향 테스트 — 6막 "다음 날 신문"의 뒷면(오피니언 2면). 6막 지면의 "판결 성향 테스트" 광고를 누르면 신문을 한 장 넘겨 들어온다.
// 지면 문법은 6막 신문(stage.css .sheet)과 같다: 제호 띠 · 이중 괘선 · 명조 기사체 · 흑백 지면 사진.
// 문항 하나 = 찬반 토론 지면 한 판(A · B 두 단이 맞선다), 답 = 아래 독자 설문 응모권의 다섯 칸.
// 문항 · 답 · 제출은 기존 성향 테스트(personalityPage.js, /test)와 같은 api를 쓰고, 결과는 기존 결과 화면(/test/result)으로 간다.
// 고르는 방법(진행 규칙)도 같은 personalityFlow.js를 쓴다: 칸을 누르거나 숫자 1 ~ 5를 치면 다음 문항으로 넘어가고, 마지막 문항은 "결과 보기"를 눌러야 제출된다.
const SCALE = [
  { value: 0, degree: '매우', side: 'A' },
  { value: 1, degree: '조금', side: 'A' },
  { value: 2, degree: '보통', side: null },
  { value: 3, degree: '조금', side: 'B' },
  { value: 4, degree: '매우', side: 'B' },
];

// 지면 사진: 안내 캐릭터를 흑백 지면 사진으로 싣는다(그림은 장식, 설명은 사진 설명에)
function photo(cuts, caption) {
  const figure = element('figure', 'tp-photo');
  const frame = element('div', 'tp-photo-frame');
  cuts.forEach(([who, look]) => {
    const image = element('img', `is-${who}`);
    image.src = imageOf(who, look);
    image.alt = '';
    image.addEventListener('error', () => image.remove(), { once: true });
    frame.append(image);
  });
  figure.append(frame, element('figcaption', '', caption));
  return figure;
}

export async function renderPersonalityPaperPage(container, { api, navigate, from }) {
  const root = element('div', 'case-list-page tp-page'); // 사건 목록 · 사건 정보와 같은 신문 스타일 범위(책상 · 종이 · 안쪽 면)
  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  const sheet = element('article', 'paper inner-page tp-sheet');
  sheet.setAttribute('aria-label', '신문 10면 오피니언 · 판결 성향 테스트');
  if (from === 'paper' && !reducedMotion()) sheet.classList.add('is-turning-in');
  if (from) history.replaceState({ ...history.state, extras: {} }, ''); // 새로고침 · 뒤로 가기로는 넘기는 연출을 되풀이하지 않는다

  // 안쪽 면 머리글(S-20과 같다): 왼쪽 귀 "이전으로"(앱 안의 이전 화면으로, 링크로 바로 들어왔으면 메인), 오른쪽 귀 "메인으로", 제호 아래 면 이름 · 날짜 · 쪽수, 그 밑 지면 목차
  const runner = innerPageHead({
    earLeft: earButton('← 이전으로', '앞에 보던 화면으로 돌아가요', () => (canGoBackInApp() ? history.back() : navigate('landing')), 'ear-left'),
    earRight: earButton('메인으로', '책상으로 돌아가요', () => navigate('landing'), 'ear-right'),
    meta: `오피니언면 · ${todayLabel()} · 10면`,
  });
  const tabsZone = element('div', 'tabs-zone');
  tabsZone.append(pageIndex(navigate, 'test'));
  const body = element('div', 'tp-body');
  sheet.append(runner, tabsZone, body, renderSiteFooter()); // 법적 고지는 다른 신문 지면처럼 지면 맨 아래 줄(라우터의 바깥 푸터는 ownsFooter로 끈다)
  root.append(element('h1', 'visually-hidden', '판결 성향 테스트'), desk, sheet);
  container.replaceChildren(root);
  window.scrollTo(0, 0);

  const active = () => container.contains(root);
  const flow = createTestFlow({ api, isActive: active }); // 진행 규칙(답 · 넘기기 · 숫자 키 · 제출)은 /test와 같다
  const { answers } = flow;
  let questions;

  async function load() {
    sheetMessage(body, '지면을 펼치는 중이에요.');
    try {
      ({ questions } = await api.getPersonalityQuestions());
    } catch {
      if (active()) sheetMessage(body, '문항을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.', { retry: load, alert: true });
      return;
    }
    flow.setQuestions(questions);
    if (active()) renderIntro();
  }

  // 표지 기사: 설문 소개 + 사진 + 응모권(시작하기)
  function renderIntro() {
    flow.leave();
    const story = element('section', 'tp-intro');
    const lead = element('div', 'tp-intro-lead');
    const text = element('div', 'tp-columns');
    [
      `일상에서 마주칠 만한 ${questions.length}가지 상황을 실었다. 법을 몰라도 괜찮다. 약 3분이면 끝난다.`,
      '상황마다 두 행동이 맞선다. 내 마음이 더 가까운 쪽을 응모권의 다섯 칸 가운데 골라 체크하면 된다.',
      '정답은 없다. 어느 유형이 옳다는 뜻도 아니다. 잘잘못을 따질 때 무엇을 먼저 보는지, 그 순서를 보여 줄 뿐이다.',
    ].forEach((line) => text.append(element('p', 'tp-text', line)));
    lead.append(
      element('h2', 'tp-headline', '잘잘못을 따질 때, 나는 무엇을 먼저 볼까'),
      element('p', 'tp-deck', `${questions.length}가지 상황에 답하면 내가 중요하게 보는 가치에 맞는 판결 성향 유형이 나온다`),
      text,
    );
    const coupon = element('div', 'tp-coupon tp-coupon-start');
    coupon.append(element('p', 'tp-coupon-title', '독자 설문 참여권'), element('p', 'tp-coupon-text', '오려 낼 필요 없이 눌러서 참여하세요.'),
      button('설문 시작하기', () => renderQuestion(0), 'primary-button'));
    const side = element('div', 'tp-intro-side');
    side.append(photo([['naero', 'guide-happy'], ['nambul', 'guide-neutral']], '설문을 진행하는 내로(왼쪽)와 남불'), coupon);
    story.append(lead, side);
    body.replaceChildren(story);
    story.querySelector('.tp-headline').tabIndex = -1;
    story.querySelector('.tp-headline').focus({ preventScroll: true });
  }

  // 응답 현황: 답한 문항 · 지금 문항 · 앞을 모두 답한 다음 문항만 눌러서 갈 수 있다(건너뛰기 없음)
  function progress(index) {
    const nav = element('nav', 'tp-progress');
    nav.setAttribute('aria-label', '문항 진행');
    nav.append(element('span', 'tp-progress-count', `설문 ${index + 1} / ${questions.length}`));
    const cells = element('ol', 'tp-progress-cells');
    questions.forEach((question, i) => {
      const item = element('li');
      const done = answers.has(question.id);
      const cell = button(String(i + 1), () => renderQuestion(i), `tp-progress-cell${done ? ' is-done' : ''}${i === index ? ' is-current' : ''}`);
      cell.setAttribute('aria-label', `${i + 1}번 문항${done ? ', 답함' : ''}`);
      if (i === index) cell.setAttribute('aria-current', 'step');
      cell.disabled = !flow.canReach(index, i);
      item.append(cell);
      cells.append(item);
    });
    // 좁은 화면은 번호 칸 대신 답한 만큼 차는 막대(personalityPaper.css)
    const bar = element('span', 'tp-progress-bar');
    bar.setAttribute('aria-hidden', 'true');
    bar.style.setProperty('--done', String(answers.size / questions.length));
    nav.append(cells, bar);
    return nav;
  }

  // 칸 고정: 모든 문항의 같은 부분(제목 · 한마디 · A · B)을 한 칸에 겹쳐 쌓아 가장 긴 문항 높이를 차지하게 한다(지금 문항만 보인다).
  // 그래서 문항이 바뀌어도 제목 · 한마디 · A | B · 응모권 · 이전/다음이 늘 같은 자리에 있다(화면 폭이 바뀌면 CSS 격자가 따라간다)
  // build(question, i) → 그 문항의 그 부분. 다른 문항 것은 보이지 않고 낭독기도 읽지 않는다
  function stacked(className, index, build) {
    const stack = element('div', `tp-stack ${className}`);
    questions.forEach((item, i) => {
      const part = build(item, i) ?? element('div');
      if (i !== index) {
        part.classList.add('is-ghost');
        part.setAttribute('aria-hidden', 'true');
        part.inert = true;
      }
      stack.append(part);
    });
    return stack;
  }
  const lineOf = (question, i) => {
    const { who = i < questions.length / 2 ? 'naero' : 'nambul', line } = PERSONALITY_LINES[question.id] ?? {};
    return { who, line };
  };
  function quoteOf(question, i) {
    const { who, line } = lineOf(question, i);
    if (!line) return null;
    const quote = element('figure', 'tp-quote');
    const face = element('img', `tp-quote-face is-${who}`);
    face.src = imageOf(who, 'idle-neutral');
    face.alt = '';
    face.addEventListener('error', () => face.remove(), { once: true });
    quote.append(face, element('blockquote', '', line), element('figcaption', '', who === 'naero' ? '진행 · 내로' : '진행 · 남불'));
    return quote;
  }

  // 찬반 토론 한 판: 제목 | 한마디 / A | B / 응모권 / 이전 · 다음
  function renderQuestion(index) {
    flow.enter(index);
    const question = questions[index];
    const last = flow.isLast(index);

    const story = element('section', 'tp-debate');
    const head = element('div', 'tp-debate-head');
    head.append(
      stacked('tp-stack-title', index, (item, i) => element(i === index ? 'h2' : 'p', 'tp-headline tp-situation', item.content)),
      stacked('tp-stack-quote', index, quoteOf),
    );
    const headline = head.querySelector('h2');
    headline.id = `tp-question-${question.id}`;
    headline.tabIndex = -1; // 문항이 바뀌면 여기로 포커스를 옮겨 낭독기가 새 문항을 읽는다

    const sides = element('div', 'tp-sides');
    [['A', 'optionA'], ['B', 'optionB']].forEach(([mark, key]) => {
      const column = element('div', `tp-side is-${mark.toLowerCase()}`);
      column.append(element('p', 'tp-side-mark', mark), stacked('tp-stack-side', index, (item) => element('p', 'tp-side-text', item[key])));
      sides.append(column);
    });

    // 응모권: 다섯 칸 라디오. 왼쪽 끝 = A, 오른쪽 끝 = B
    const coupon = element('div', 'tp-coupon');
    const ballot = element('div', 'tp-ballot');
    ballot.setAttribute('role', 'radiogroup');
    ballot.setAttribute('aria-labelledby', headline.id);
    let pointer = false;
    SCALE.forEach(({ value, degree, side }) => {
      const option = element('label', `tp-option step-${value}`);
      const input = element('input');
      input.type = 'radio';
      input.name = headline.id;
      input.value = String(value);
      input.checked = answers.get(question.id) === value;
      input.addEventListener('change', () => pick(value));
      const said = side ? `${degree} ${side}` : degree;
      const full = side ? `${degree}: ${question[side === 'A' ? 'optionA' : 'optionB']}` : '보통: 어느 쪽도 아니에요';
      option.append(input, element('span', 'tp-box'), element('span', 'tp-degree', said), element('span', 'visually-hidden', full));
      ballot.append(option);
    });
    ballot.addEventListener('pointerdown', () => { pointer = true; });
    ballot.addEventListener('keydown', () => { pointer = false; }); // 화살표로 옮길 때는 넘어가지 않는다
    const ends = element('div', 'tp-ballot-ends');
    ends.setAttribute('aria-hidden', 'true');
    ends.append(element('span', '', 'A에 가까워요'), element('span', '', 'B에 가까워요'));
    coupon.append(element('p', 'tp-coupon-title', '독자 설문 응모권'), ballot, ends);

    const actions = element('div', 'tp-actions');
    const prev = button('이전', () => (index === 0 ? renderIntro() : renderQuestion(index - 1)), 'secondary-button');
    const hint = element('p', 'tp-hint', last ? '다 고르면 결과를 볼 수 있어요' : '칸을 고르면 다음 문항으로 넘어가요 · 키보드 1 ~ 5');
    const next = button(last ? '결과 보기' : '다음', goNext, 'primary-button');
    next.disabled = !answers.has(question.id);
    actions.append(prev, hint, next);

    story.append(head, sides, coupon, actions);
    story.pickByKey = (value) => {
      const input = ballot.querySelector(`input[value="${value}"]`);
      input.checked = true;
      input.focus({ preventScroll: true });
      pick(value, true);
    };
    body.replaceChildren(progress(index), story);
    headline.focus({ preventScroll: true });

    function pick(value, byKey = false) {
      const first = flow.answer(index, value, { advance: pointer || byKey, goNext });
      pointer = false;
      next.disabled = false;
      if (first) body.querySelector('.tp-progress')?.replaceWith(progress(index));
    }

    function goNext() {
      flow.next(index, { show: renderQuestion, submit });
    }
  }

  // 숫자 1 ~ 5 = 왼쪽부터 다섯 칸(문항 화면에서만)
  flow.listenKeys((value) => {
    const story = body.querySelector('.tp-debate');
    if (!story?.pickByKey) return false;
    story.pickByKey(value);
    return true;
  });

  async function submit() {
    sheetMessage(body, '응모권을 모아 정리하는 중이에요.');
    try {
      await flow.submit();
    } catch {
      if (active()) {
        sheetMessage(body, '결과를 저장하지 못했어요. 고른 답은 그대로 있어요.', { retry: submit, alert: true });
        body.querySelector('.test-message').append(button('마지막 문항으로', () => renderQuestion(questions.length - 1), 'secondary-button'));
      }
      return;
    }
    if (active()) {
      flow.stopKeys();
      navigate('testResult');
    }
  }

  await load();
}
