// 8막 세 판결 비교 무대. 재판(신문 속 사건)이 끝나고 현실의 신문으로 돌아온다 — 판사의 책상 위 "다음 날 신문"에
// 내 체험 결과가 녹아들어 내 판결 · AI 판결 · 실제 판결이 기사로 실렸다.
// 지면은 화면보다 크고, 장면마다 카메라가 기사 하나(대본 focus)로 옮겨 간다. 7막 법정이 1면 사진 속으로 빠져나오며 시작한다(대본 from: "photo").
// FE-32: 무대 상자 없이 화면 전체가 카메라 틀이다(mountNewspaperStage — 넓은 화면은 무대가 화면을 가득 채우고, 좁은 화면은 지면 안 무대)
// 세 판결은 같은 크기 · 같은 무게로 싣고 어느 쪽이 맞았는지 말하지 않는다(PRODUCT.md 원칙 1).
// 마지막 장면에서 다음 행동을 고른다: 다른 사건 보기 · 메인으로 · 공유하기.
// S-08(7막)에서 비교를 공개한 뒤 무대를 이어 받고(주소만 S-09로), 재진입(S-09 주소)에서도 같은 무대를 연다
import { formatPenalty, formatDispositions } from '../../utils/judgmentFormat.js';
import { formatMonths } from '../../components/recommendedRangeBar.js';
import { renderStage, courtSet, imageOf } from '../../components/stage.js';
import { restartMock, jumpActMock } from '../overview/overviewPage.js';
import { editorNote } from '../../utils/preJudgmentCompare.js';

const SUBJECTS = [['USER', '내 판결'], ['AI', 'AI 판결'], ['COURT', '실제 판결']];
const DIRECTIONS = { UP: ['무겁게', 'up'], DOWN: ['가볍게', 'down'] };
// 이번 사건에서 만난 모습: 대본에 나온 역할 의상(look "role:…")을 모은다
const ROLE_NAMES = { actor: 'A씨 역', prosecutor: '검사', defense: '변호인', judge: '판사' };
const NAMES = { naero: '내로', nambul: '남불' };

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function picture(src, className, alt = '') {
  const image = element('img', className);
  image.src = src;
  image.alt = alt;
  image.addEventListener('error', () => image.remove(), { once: true });
  return image;
}
// 지면 기사 한 꼭지: 카메라가 찾는 이름(data-focus) + 제목 + 내용
function story(focus, title, ...children) {
  const node = element('section', `sheet-story sheet-${focus}`);
  node.dataset.focus = focus;
  node.append(element('h3', 'sheet-head', title), ...children);
  return node;
}

// 형량 차이 대사: 둘 다 징역이면 몇 년 차이인지, 형벌이 다르면 종류가 다르다고만 말한다(옳고 그름은 말하지 않는다)
function diffLine(mine, other, name, same, differ) {
  if (formatPenalty(mine) === formatPenalty(other) && (mine.suspensionMonths ?? null) === (other.suspensionMonths ?? null)) return same;
  const prison = (j) => (j.reducedTo ?? j.penaltyType) === 'PRISON';
  if (prison(mine) && prison(other) && mine.prisonMonths !== other.prisonMonths) {
    return differ(`${name}과는 ${formatMonths(Math.abs(mine.prisonMonths - other.prisonMonths))} 차이가 나요.`);
  }
  return differ(`${name}과는 형벌이 달라요.`);
}

// 1면 사진: 7막 마지막 법정 그대로(판사 역 내로 · 피고인 역 남불). 지면에서는 흑백 사진이고, 시작 장면에서만 색이 남아 있다
function courtPhoto() {
  const figure = element('figure', 'sheet-figure');
  const photo = element('div', 'sheet-photo');
  photo.dataset.focus = 'photo';
  photo.append(courtSet(), picture(imageOf('naero', 'role:judge'), 'sheet-photo-n'), picture(imageOf('nambul', 'role:actor'), 'sheet-photo-b'));
  photo.setAttribute('role', 'img');
  photo.setAttribute('aria-label', '선고가 열린 법정. 판사 역의 내로와 피고인 역의 남불');
  figure.append(photo, element('figcaption', '', '선고가 열린 법정(판결 체험 재연)'));
  return figure;
}

// 다음 날 신문 한 장: 제호 · 1면(제목 · 사진) · 세 판결 · 판단 요소 · 칼럼(처음 → 직접) · 분석(공통점 · 차이점) · 사진란(만난 모습)
function newspaper(data, script, caseTitle) {
  const { preToFinal, judgments, matrix, ruleSentences } = data;
  const sheet = element('article', 'sheet');
  sheet.setAttribute('aria-label', '다음 날 신문');

  const mast = element('header', 'sheet-mast');
  const info = element('div', 'sheet-info');
  info.append(element('span', '', '판결 체험 신문'), element('span', '', '종합'));
  const brand = element('div', 'sheet-brand');
  brand.append('내', element('span', 'law', 'Law'), '남불');
  const date = element('div', 'sheet-date');
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000);
  date.append(element('span', '', new Intl.DateTimeFormat('ko-KR', { dateStyle: 'full' }).format(tomorrow)), element('span', '', '참여 무료'));
  mast.append(info, brand, date);

  const front = element('section', 'sheet-story sheet-front');
  front.dataset.focus = 'front';
  const lead = element('div', 'sheet-lead');
  lead.append(element('p', 'sheet-deck', `${caseTitle}, 내 판결과 AI 판결, 실제 재판부의 판결을 나란히 싣는다`),
    element('p', 'sheet-text', '같은 사건 기록을 놓고 세 쪽이 각자 형량을 정했다. 무엇을 무겁게 보고 무엇을 가볍게 봤는지, 지면 곳곳에 나눠 실었다.'));
  const frontBody = element('div', 'sheet-front-body');
  frontBody.append(courtPhoto(), lead);
  front.append(element('h2', 'sheet-headline', '같은 사건, 세 개의 판결'), frontBody);

  const verdicts = element('div', 'sheet-verdict-row');
  SUBJECTS.forEach(([key, label]) => {
    const judgment = judgments[key];
    const box = element('div', `sheet-verdict is-${key.toLowerCase()}`);
    box.append(element('p', 'sheet-who', label), element('p', 'sheet-penalty', formatPenalty(judgment)));
    const sub = formatDispositions(judgment);
    if (sub) box.append(element('p', 'sheet-sub', sub));
    if (judgment.summary) box.append(element('p', 'sheet-text', judgment.summary));
    verdicts.append(box);
  });

  const table = element('table', 'sheet-matrix');
  const head = element('tr');
  head.append(element('th', '', '판단 요소'), ...SUBJECTS.map(([, label]) => element('th', '', label)));
  const thead = element('thead');
  thead.append(head);
  const tbody = element('tbody');
  matrix.forEach((row) => {
    const tr = element('tr');
    const name = element('th', '', row.label);
    name.setAttribute('scope', 'row');
    tr.append(name, ...[row.user, row.ai, row.court].map((direction) => {
      const [word, tone] = DIRECTIONS[direction] ?? ['고려 안 함', 'none'];
      return element('td', `sheet-dir is-${tone}`, word);
    }));
    tbody.append(tr);
  });
  table.append(thead, tbody);

  const rules = [['공통점', ruleSentences.common], ['차이점', ruleSentences.differences]].map(([title, lines]) => {
    const part = element('div', 'sheet-rule');
    part.append(element('h4', '', title), ...lines.map((line) => element('p', 'sheet-text', line)));
    return part;
  });

  const seen = new Map();
  Object.values(script.acts).forEach((act) => {
    [act.look, ...act.steps.map((step) => step.look)].forEach((look) => Object.entries(look ?? {}).forEach(([who, value]) => {
      if (NAMES[who] && value.startsWith('role:')) seen.set(`${who}:${value.slice(5)}`, [who, value.slice(5)]);
    }));
  });
  const cast = element('ul', 'sheet-cast-grid');
  seen.forEach(([who, role]) => {
    const item = element('li');
    item.append(picture(imageOf(who, `role:${role}`), ''), element('span', '', `${NAMES[who]} · ${ROLE_NAMES[role] ?? role}`));
    cast.append(item);
  });

  // 독자 의견란: 2막에서 보낸 엽서가 다음 날 신문 독자란에 실려 돌아온다. 처음 보낸 첫인상만 다시 싣는다(API 14의 preToFinal.preJudgment).
  // 아래 편집부 의견은 그 첫인상을 실제 판결과 견줘 한 줄로 적는다(어느 쪽이 맞는지는 말하지 않는다).
  // 추후 확장(우선도 낮음): 사용자 데이터가 쌓이면 이 칸에 다른 독자들의 첫인상 분포를 함께 보여 줄 수 있다 — 지금은 데이터가 없어 보여 주지 않는다.
  const letter = element('div', 'sheet-letter');
  const letterMark = element('span', 'sheet-letter-mark');
  letterMark.setAttribute('aria-hidden', 'true');
  letterMark.append('접수', element('br'), '완료');
  letter.append(element('p', 'sheet-letter-q', '이 사건, 어느 정도의 형량이 맞다고 생각하나요?'),
    element('p', 'sheet-letter-a', preToFinal.preJudgment.label),
    element('p', 'sheet-letter-by', '— 기사만 읽고 보낸 첫인상, 독자 엽서'), letterMark);
  const note = editorNote(preToFinal.preJudgment.label, judgments.COURT);
  if (note) {
    const edit = element('p', 'sheet-letter-edit');
    edit.append(element('b', '', '편집부 의견'), note);
    letter.append(edit);
  }

  sheet.append(mast, front,
    story('letters', '독자 의견', letter),
    story('verdicts', '세 판결, 나란히', verdicts),
    story('cast', '이번 사건에서 만난 모습', cast),
    story('factors', '판단 요소, 어디서 갈렸나', table),
    story('rules', '같게 본 것, 다르게 본 것', ...rules));
  return sheet;
}

// 공유: 받는 사람이 체험 전에 판결을 보지 않게 형량 없이 이 사건의 사건 디테일(S-20, /cases/{caseId}) 주소를 보낸다(앵커링 방지, FE-38).
// 받은 사람은 시작 전 지면(캐릭터 없음)에서 "시작하기"로 1막부터 체험한다
async function share(note, caseId) {
  const url = `${window.location.origin}/cases/${caseId}`;
  const text = '같은 사건, 당신이라면 어떻게 판결할까요?';
  note.textContent = '';
  if (navigator.share) {
    try {
      await navigator.share({ title: '내Law남불', text, url });
      return;
    } catch (error) {
      if (error?.name === 'AbortError') return; // 공유 창을 닫은 경우
      // 공유 창을 못 열면(권한 · 미지원 환경) 링크 복사로 넘어간다
    }
  }
  try {
    await navigator.clipboard.writeText(`${text} ${url}`);
    note.textContent = '링크를 복사했어요. 친구에게 붙여 넣어 보내 보세요.';
  } catch {
    note.textContent = `링크를 복사하지 못했어요. 주소를 직접 복사해 주세요: ${url}`;
  }
}

/**
 * @returns {HTMLElement} 8막 무대. 마지막 대사창의 선택지로 다른 사건 · 메인 · 공유를 고른다
 */
export function renderComparisonStage({ script, data, caseId, caseTitle, navigate }) {
  const { judgments } = data;
  const vsCourt = diffLine(judgments.USER, judgments.COURT, '실제 판결',
    '실제 판결과 형량이 같았네요. 그런데 무겁게 본 요소도 같았을까요?', (head) => `${head} 어떤 요소를 다르게 봤을까요?`);
  const vsAi = diffLine(judgments.USER, judgments.AI, 'AI 판결',
    'AI 판결과 형량이 같아요! 그래도 이유까지 같은지는 따로 봐야 해요.', (head) => `${head} 다른 자리에서 보면 다르게 보이기도 해요!`);
  const act = script.acts['8'];
  const pre = data.preToFinal.preJudgment.label;
  const editor = editorNote(pre, judgments.COURT); // 편집부 의견을 못 만들면(구간 이름을 못 읽음) 그 대사는 건너뛴다
  const steps = act.steps.filter((step) => editor || !step.text?.includes('{editor}'))
    .map((step) => (step.text ? { ...step, text: step.text.replace('{vsCourt}', vsCourt).replace('{vsAi}', vsAi).replace('{pre}', pre).replace('{editor}', editor ?? '') } : step));
  return renderStage({
    script: { ...script, acts: { ...script.acts, 8: { ...act, steps } } },
    order: ['8'],
    sheet: newspaper(data, script, caseTitle),
    cameraFill: window.matchMedia('(min-width: 681px)').matches, // 넓은 화면: 책상 없이 신문만 화면 가득(1막 지면처럼)
    choices: { end: [
      { label: '다른 사건 보기', action: () => navigate('list'), primary: true },
      { label: '메인으로', action: () => navigate('landing') },
      { label: '공유하기', action: (note) => share(note, caseId) },
    ] },
    onRestart: restartMock(caseId),
    onJump: jumpActMock(caseId),
  });
}

// 8막 화면 틀: 무대(카메라 틀)가 화면 전체를 채운다 — 판사의 책상 위 다음 날 신문을 화면 가득 들여다본다(FE-32, comparison.css)
export function mountNewspaperStage(root, { stage }) {
  root.className = 'comparison-page comparison-full'; // comparison-page: 화면 공통 글꼴 · [hidden] 규칙(review.css)
  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  root.replaceChildren(desk, element('h1', 'visually-hidden', '세 판결 비교'), stage);
  window.scrollTo(0, 0);
}

// 무대 화면 틀(6 ~ 8막이 같은 화면에서 이어질 때도 쓴다): 사건 제목 줄 + 가운데 넓은 무대
export function mountStage(root, { caseHeader, badge, heading, stage, extra = [] }) {
  root.classList.add('has-stage');
  const header = element('header', 'case-topbar');
  header.append(element('strong', 'case-title', caseHeader.title), element('span', 'case-badge', badge));
  const main = element('div', 'review-main');
  main.append(element('h1', 'page-heading visually-hidden', heading), stage, ...extra);
  const layout = element('main', 'review-layout');
  layout.append(main);
  root.replaceChildren(header, layout);
}
