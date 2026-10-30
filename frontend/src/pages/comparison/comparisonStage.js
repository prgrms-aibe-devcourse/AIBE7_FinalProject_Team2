// 6막 세 판결 비교 무대. 재판(신문 속 사건)이 끝나고 현실의 신문으로 돌아온다 — 판사의 책상 위 "다음 날 신문"에
// 내 체험 결과가 녹아들어 내 판결 · AI 판결 · 실제 판결이 기사로 실렸다.
// 지면은 화면보다 크고, 장면마다 카메라가 기사 하나(대본 focus)로 옮겨 간다. 5막 법정이 1면 사진 속으로 빠져나오며 시작한다(대본 from: "photo").
// FE-32: 무대 상자 없이 화면 전체가 카메라 틀이다(mountNewspaperStage — 넓은 화면은 무대가 화면을 가득 채우고, 좁은 화면은 지면 안 무대)
// 세 판결은 같은 크기 · 같은 무게로 싣고 어느 쪽이 맞았는지 말하지 않는다(PRODUCT.md 원칙 1).
// 마지막 장면에서 다음 행동을 고른다: 메인으로 · 여기 남아있기(지면 읽기 → 다른 사건 보기 · 메인으로) · 엽서 쓰기 · 공유하기. "다른 사건 보기"는 읽기 모드의 신문 귀로만 나온다(FE-98 확인).
// S-07(5막)에서 비교를 공개한 뒤 무대를 이어 받고(주소만 S-08로), 재진입(S-08 주소)에서도 같은 무대를 연다
import { formatPenalty, formatDispositions } from '../../utils/judgmentFormat.js';
import { formatMonths } from '../../components/recommendedRangeBar.js';
import { renderStage, courtSet, imageOf, courtPhotoCast } from '../../components/stage.js';
import { openCaseReviewPopup } from '../caseReview/caseReviewPopup.js';
import { restartMock, jumpActMock } from '../../mocks/mockStageButtons.js';
import { editorNote } from '../../utils/preJudgmentCompare.js';
import { handPaper } from '../../utils/paperHandoff.js';
import { castOf } from '../../utils/myPageView.js';
import { reducedMotion } from '../../utils/motion.js';
import { mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';

const SUBJECTS = [['USER', '내 판결'], ['AI', 'AI 판결'], ['COURT', '실제 판결']];
const DIRECTIONS = { UP: ['무겁게', 'up'], DOWN: ['가볍게', 'down'] };
const STAY_ZOOM_MS = 700;
const FLIP_MS = [300, 380]; // 다른 사건 보기: 신문을 들어 올려 옆으로 세우는 시간 · 뒷면이 보이게 마저 뒤집는 시간
const TURN_MS = 600; // 성향 테스트: 신문을 왼쪽 가장자리를 축으로 넘겨 세우는 시간

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
// 지면 기사 한 꼭지: 카메라가 찾는 이름(data-focus) + 제목 + 내용. 마이페이지 내 결과 지면(FE-75)도 같은 꼭지를 쓴다
export function story(focus, title, ...children) {
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

// 1면 사진: 5막 마지막 법정 그대로(묶음 A 판사 왼쪽 · 묶음 B 피고인 역 오른쪽, 대본 배역 script.cast를 따른다, FE-141).
// 지면에서는 흑백 사진이고, 시작 장면에서만 색이 남아 있다
const CAST_NAME = { naero: '내로', nambul: '남불' };
function courtPhoto(cast) {
  const figure = element('figure', 'sheet-figure');
  const photo = element('div', 'sheet-photo');
  photo.dataset.focus = 'photo';
  const [judge, actor] = courtPhotoCast(cast);
  const people = [judge, actor].map(({ who, look, side, flip }) => {
    const image = picture(imageOf(who, look), `sheet-photo-${side === 'left' ? 'a' : 'b'}`);
    if (flip) image.dataset.flip = '1'; // 무대처럼 가운데를 본다(FE-128)
    return image;
  });
  photo.append(courtSet(), ...people);
  photo.setAttribute('role', 'img');
  const and = CAST_NAME[judge.who] === '남불' ? '과' : '와'; // 받침이 있으면 "과"
  photo.setAttribute('aria-label', `선고가 열린 법정. 판사 역의 ${CAST_NAME[judge.who]}${and} 피고인 역의 ${CAST_NAME[actor.who]}`);
  figure.append(photo, element('figcaption', '', '선고가 열린 법정(판결 체험 재연)'));
  return figure;
}

// 다음 날 신문 한 장: 제호 · 1면(제목 · 사진) · 세 판결 · 판단 요소 · 칼럼(처음 → 직접) · 분석(공통점 · 차이점) · 사진란(만난 모습)
// 제호 · 1면 · 독자 의견 · 만난 모습 · 광고는 마이페이지 내 결과 지면(FE-75)도 그대로 쓴다(아래 내보낸 함수들)
function newspaper(data, script, caseTitle, caseId, navigate, openReview) {
  const { judgments, matrix, ruleSentences } = data;
  const sheet = element('article', 'sheet');
  sheet.setAttribute('aria-label', '다음 날 신문');

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

  sheet.append(masthead(caseId), frontStory(caseTitle, undefined, script.cast), letterStory(data, openReview),
    story('verdicts', '세 판결, 나란히', verdicts),
    castStory(script, navigate),
    story('factors', '판단 요소, 어디서 갈렸나', table),
    story('rules', '같게 본 것, 다르게 본 것', ...rules),
    endingAds(advertisements()));
  return sheet;
}

// 제호: 정보 줄 · [왼쪽 귀 | 제호 | 오른쪽 귀] · 날짜(다음 날)
export function masthead(caseId) {
  const mast = element('header', 'sheet-mast');
  const info = element('div', 'sheet-info');
  info.append(element('span', '', '판결 체험 신문'), element('span', '', '종합'));
  const brand = element('div', 'sheet-brand');
  brand.append('내', element('span', 'law', 'Law'), '남불');
  const date = element('div', 'sheet-date');
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000);
  date.append(element('span', '', new Intl.DateTimeFormat('ko-KR', { dateStyle: 'full' }).format(tomorrow)), element('span', '', '참여 무료'));
  // 귀(제호 양옆): "여기 남아있기"로 지면을 읽을 때만 보인다(courtSketch.css). 왼쪽은 다른 사건 보기(신문을 뒤집고 1면으로),
  // 오른쪽은 이 지면의 사건 번호(누르지 않는다, FE-120 — 전에는 "고민 중" 메모였다)
  const earLeft = element('button', 'sheet-ear sheet-ear-left is-clickable');
  earLeft.type = 'button';
  earLeft.append(element('b', '', '← 다른 사건 보기'), element('small', '', '오늘의 지면으로'));
  const earRight = element('div', 'sheet-ear sheet-ear-right');
  earRight.append(element('b', '', `사건 ${String(caseId).padStart(2, '0')}`), element('small', '', '판결 확정'));
  const brandRow = element('div', 'sheet-brand-row');
  brandRow.append(earLeft, brand, earRight);
  mast.append(info, brandRow, date);
  return mast;
}

// 1면: 헤드라인 · 법정 사진 · 리드. leadText: { headline, deck, text }를 넘기면 그 문구로 바꾼다(내 결과 지면, FE-75).
// cast: 대본 배역(script.cast). 없으면 기본 배역(내로 판사 · 남불 피고인 역)
// deck · text는 글자와 요소(굵은 형량 등)의 배열이다
export function frontStory(caseTitle, leadText, cast) {
  const front = element('section', 'sheet-story sheet-front');
  front.dataset.focus = 'front';
  const lead = element('div', 'sheet-lead');
  const deck = element('p', 'sheet-deck');
  deck.append(...(leadText?.deck ?? [`${caseTitle}, 내 판결과 AI 판결, 실제 재판부의 판결을 나란히 싣는다`]));
  const text = element('p', 'sheet-text');
  text.append(...(leadText?.text ?? ['같은 사건 기록을 놓고 세 쪽이 각자 형량을 정했다. 무엇을 무겁게 보고 무엇을 가볍게 봤는지, 지면 곳곳에 나눠 실었다.']));
  lead.append(deck, text);
  const frontBody = element('div', 'sheet-front-body');
  frontBody.append(courtPhoto(cast), lead);
  front.append(element('h2', 'sheet-headline', leadText?.headline ?? '같은 사건, 세 개의 판결'), frontBody);
  return front;
}

// 이번 사건에서 만난 모습(대본의 역할 의상). 누르면 마이페이지 컬렉션으로 간다(FE-44)
export function castStory(script, navigate) {
  const cast = element('ul', 'sheet-cast-grid');
  castOf(script).forEach(({ who, role, label }) => {
    const item = element('li');
    const open = element('button', 'sheet-cast-link');
    open.type = 'button';
    // 체험 중에는 누를 수 없다("여기 남아있기"로 지면을 읽는 상태가 되면 stay()가 연다)
    open.disabled = true;
    open.dataset.openLabel = `${label} — 마이페이지 컬렉션에서 보기`;
    open.append(picture(imageOf(who, `role:${role}`), ''), element('span', '', label));
    open.addEventListener('click', () => navigate('me', { view: 'collection' }));
    item.append(open);
    cast.append(item);
  });
  return story('cast', '이번 사건에서 만난 모습', cast);
}

// openReview(letter): 엽서를 눌렀을 때 독자 엽서를 연다
export function letterStory(data, openReview) {
  const { preToFinal } = data;
  // 독자 의견란: 1막에서 보낸 엽서가 다음 날 신문 독자란에 실려 돌아온다. 처음 보낸 첫인상만 다시 싣는다(API 14의 preToFinal.preJudgment).
  // 아래 편집부 의견은 그 첫인상을 실제 판결과 견줘 한 줄로 적는다(어느 쪽이 맞는지는 말하지 않는다).
  // 추후 확장(우선도 낮음): 사용자 데이터가 쌓이면 이 칸에 다른 독자들의 첫인상 분포를 함께 보여 줄 수 있다 — 지금은 데이터가 없어 보여 주지 않는다.
  const letter = element('div', 'sheet-letter');
  // 엽서를 누르면 이 엽서가 떠올라 독자 엽서로 펼쳐진다("엽서 쓰기" 버튼과 같은 내용). 체험 중에는 누를 수 없고(아무 표시도 없다),
  // "여기 남아있기"로 지면을 읽는 상태가 되면 stay()가 is-clickable을 붙여 연다
  letter.addEventListener('click', () => { if (letter.classList.contains('is-clickable')) openReview(letter); });
  letter.addEventListener('keydown', (event) => {
    if (!letter.classList.contains('is-clickable') || (event.key !== 'Enter' && event.key !== ' ')) return;
    event.preventDefault();
    event.stopPropagation();
    openReview(letter);
  });
  const letterMark = element('span', 'sheet-letter-mark');
  letterMark.setAttribute('aria-hidden', 'true');
  letterMark.append('접수', element('br'), '완료');
  letter.append(element('p', 'sheet-letter-q', '이 사건, 어느 정도의 형량이 맞다고 생각하나요?'),
    element('p', 'sheet-letter-a', preToFinal.preJudgment.label),
    crowdBars(preToFinal) ?? element('div', 'sheet-letter-lines'), letterMark);
  const note = editorNote(data.preToFinal?.direction);
  if (note) {
    const edit = element('p', 'sheet-letter-edit');
    edit.append(element('b', '', '편집부 의견'), note);
    letter.append(edit);
  }
  return story('letters', '독자 의견', letter);
}

// 광고 칸을 눌러서 동작하는 버튼으로 만든다(읽는 지면에서만 쓴다). 키보드는 Enter · Space
function pressable(box, label, action) {
  box.classList.add('is-clickable');
  box.setAttribute('role', 'button');
  box.tabIndex = 0;
  box.setAttribute('aria-label', label);
  box.addEventListener('click', action);
  box.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    action();
  });
}

// 독자 의견란 엽서 아래 막대그래프(FE-120): 이 사건을 읽은 독자들이 기사만 보고 고른 첫인상 분포. 내가 고른 구간은 막대를 짙게 · 숫자를 굵게 한다.
// 낭독기는 행마다 "구간 N%"로 읽고, 내 구간은 이름에 "(내 선택)"을 숨긴 글자로 붙인다
// preToFinal.crowd는 목 전용이라(API 14에 아직 없음) 없으면 그래프 없이 엽서의 빈 줄을 그대로 둔다
function crowdBars(preToFinal) {
  const crowd = preToFinal?.crowd;
  if (!crowd?.options?.length) return null;
  const box = element('div', 'sheet-crowd');
  const head = element('p', 'sheet-crowd-head', `이 사건 독자 ${crowd.total.toLocaleString('ko-KR')}명의 첫인상`);
  // 지금 crowd는 목에만 있지만, API 14에 실제 값이 들어와도 "목데이터"가 붙지 않도록 목 모드에서만 붙인다(FE-142 리뷰)
  if (API_MODE === 'mock') head.append(mockTag());
  const list = element('ul', 'sheet-crowd-list');
  crowd.options.forEach((option) => {
    const mine = option.rangeOptionId === preToFinal.preJudgment.rangeOptionId;
    const row = element('li', `sheet-crowd-row${mine ? ' is-mine' : ''}`);
    const bar = element('span', 'sheet-crowd-bar');
    bar.setAttribute('aria-hidden', 'true');
    const fill = element('span');
    fill.style.width = `${option.percent}%`;
    bar.append(fill);
    const label = element('span', 'sheet-crowd-label', option.label);
    if (mine) label.append(element('span', 'visually-hidden', ' (내 선택)'));
    row.append(label, bar, element('span', 'sheet-crowd-pct', `${option.percent}%`));
    list.append(row);
  });
  box.append(head, list);
  return box;
}

// 6막 끝 광고 단 문구(FE-120): 가운데 칸은 "공유하기", 오른쪽 칸은 "메인으로"가 된다(눌러서 동작하도록 만드는 건 renderComparisonStage). 내 결과 지면은 이 문구를 쓰지 않는다
function endingAds(ads) {
  const share = ads.querySelector('.sheet-ad.is-today');
  share.querySelector('.sheet-ad-tag').textContent = '공유';
  share.querySelector('.sheet-ad-title').textContent = '공유하기';
  share.querySelector('.sheet-ad-text').textContent = '이 사건을 친구에게 보내 같이 판결해 보세요. 링크에는 판결 내용이 담기지 않아요.';
  const main = ads.querySelector('.sheet-ad.is-notice');
  main.classList.add('is-main'); // 진한 면(courtSketch.css)
  main.querySelector('.sheet-ad-tag').textContent = '이동';
  main.querySelector('.sheet-ad-title').textContent = '메인으로';
  main.querySelector('.sheet-ad-text').textContent = '신문을 책상에 내려놓고 메인으로 돌아가요.';
  return ads;
}

// 지면을 읽는 상태로 연다: 만난 모습 · 독자 엽서 · 성향 테스트 광고를 누를 수 있게 한다(체험 중에는 막혀 있다).
// onTestAd: 성향 테스트 광고를 눌렀을 때(6막은 신문을 넘겨 오피니언 면으로, FE-55)
export function openForReading(sheet, onTestAd) {
  sheet.querySelectorAll('.sheet-cast-link').forEach((link) => {
    link.disabled = false;
    link.setAttribute('aria-label', link.dataset.openLabel);
  });
  const letter = sheet.querySelector('.sheet-letter');
  if (letter) {
    letter.classList.add('is-clickable');
    letter.setAttribute('role', 'button');
    letter.tabIndex = 0;
    letter.setAttribute('aria-label', '독자 엽서 — 이 사건에 엽서 쓰기');
  }
  // 지면 아래 "판결 성향 테스트" 광고
  const testAd = sheet.querySelector('.sheet-ad.is-lead');
  if (testAd) pressable(testAd, '판결 성향 테스트 — 신문을 넘겨 시작하기', onTestAd); // 광고 단이 없어도 읽기 화면은 열린다
}

// 지면 맨 아래 광고 · 알림 단: 서비스 안의 다른 메뉴를 신문 광고처럼 싣는다(누르지 않는 장식, 메인 책상의 물건과 같은 이름).
// 마지막 기사(공통점 · 차이점, 만난 모습)가 지면 끝에 붙지 않게 해서, 카메라가 그 기사를 화면 가운데로 당길 수 있다
export function advertisements() {
  const ads = element('aside', 'sheet-ads');
  ads.setAttribute('aria-label', '광고 · 알림');
  const ad = (kind, tag, title, text) => {
    const box = element('div', `sheet-ad ${kind}`);
    box.append(element('p', 'sheet-ad-tag', tag), element('p', 'sheet-ad-title', title), element('p', 'sheet-ad-text', text));
    return box;
  };
  ads.append(
    ad('is-lead', '광고', '판결 성향 테스트', '나는 엄하게 보는 편일까, 사정을 살피는 편일까? 3분이면 끝나는 판결 성향 테스트.'),
    ad('is-today', '알림', '오늘의 지면', '다른 사건도 판결을 기다리고 있어요. 신문을 펼쳐 다음 사건을 골라 보세요.'),
    ad('is-notice', '첫인상 엽서 모집', '첫인상을 보내 주세요', '기사만 읽고 보낸 엽서는 다음 날 독자 의견란에 실립니다.'),
    element('p', 'sheet-ads-note', '이 지면의 사건과 기사는 판결 체험을 위해 재구성한 것이며, 사진은 AI로 생성한 연출 이미지입니다.'),
  );
  return ads;
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
 * @returns {HTMLElement} 6막 무대. 마지막 대사창의 선택지로 다른 사건 · 메인 · 공유를 고른다
 */
// fromRect: 5막 무대 장면이 있던 화면 사각형(이어 받을 때). 그 자리 그대로에서 신문으로 빠져나온다
export function renderComparisonStage({ script, data, caseId, caseTitle, navigate, api, fromRect }) {
  const { judgments } = data;
  const vsCourt = diffLine(judgments.USER, judgments.COURT, '실제 판결',
    '실제 판결과 형량이 같았네요. 그런데 무겁게 본 요소도 같았을까요?', (head) => `${head} 어떤 요소를 다르게 봤을까요?`);
  const vsAi = diffLine(judgments.USER, judgments.AI, 'AI 판결',
    'AI 판결과 형량이 같아요! 그래도 이유까지 같은지는 따로 봐야 해요.', (head) => `${head} 다른 자리에서 보면 다르게 보이기도 해요!`);
  const act = script.acts['6'];
  const pre = data.preToFinal.preJudgment.label;
  const editor = editorNote(data.preToFinal?.direction); // 편집부 의견을 못 만들면(direction이 없거나 모르는 값) 그 대사는 건너뛴다
  const steps = act.steps.filter((step) => editor || !step.text?.includes('{editor}'))
    .map((step) => (step.text ? { ...step, text: step.text.replace('{vsCourt}', vsCourt).replace('{vsAi}', vsAi).replace('{pre}', pre).replace('{editor}', editor ?? '') } : step));
  const narrow = window.matchMedia('(max-width: 680px)').matches;
  const sheet = newspaper(data, script, caseTitle, caseId, navigate, (letter) => openCaseReviewPopup({ caseId, api, navigate, from: letter }));
  const stage = renderStage({
    script: { ...script, acts: { ...script.acts, 6: { ...act, steps } } },
    order: ['6'],
    sheet,
    fromRect,
    cameraFill: true, // 책상 없이 신문만 화면 가득(1막 지면처럼)
    cameraMinScale: 0, // 좁은 화면도 기사가 폭에 맞게 들어오게(FE-97: 예전에는 1.3배로 키워 기사 오른쪽이 화면 밖으로 잘렸다). 글자 크기는 stage.css 좁은 화면 --u로 키운다
    cameraMaxUnit: narrow ? 0 : 9.5, // 큰 화면: 본문 약 15px · 제목 약 26px 이상으로 키우지 않는다
    choices: { end: [
      { label: '메인으로', action: () => toDesk(), primary: true }, // 판사의 책상(데스크톱 메인)
      { label: '여기 남아있기', action: () => stay() },
      { label: '엽서 쓰기', action: () => openCaseReviewPopup({ caseId, api, navigate, from: stage.querySelector('.spr.b'), hand: true }) }, // 남불이 엽서를 건넨다
      { label: '공유하기', action: (note) => share(note, caseId) },
    ] },
    onRestart: restartMock(caseId),
    onJump: jumpActMock(caseId),
  });
  // 여기 남아있기: 무대를 멈추고(말풍선 · 캐릭터 · 조작 줄을 걷고) 신문을 스크롤해 읽는다.
  // 카메라가 당겨 보던 지면이 그 자리에서 지면 전체 크기로 줄어든다(줌아웃). 읽기 모드의 지면은 transform을 !important로 끄므로
  // 개별 속성(translate · scale)으로 움직인다. 움직임 줄이기면 바로 바뀐다
  function stay() {
    const from = sheet.getBoundingClientRect();
    stage.freeze();
    stage.classList.add('reading-paper');
    openForReading(sheet, turnToTest);
    armEndingAds();
    window.scrollTo(0, 0);
    sheet.setAttribute('tabindex', '-1');
    sheet.focus({ preventScroll: true });
    if (reducedMotion()) return;
    const to = sheet.getBoundingClientRect();
    if (!to.width) return;
    sheet.style.transformOrigin = '0 0';
    // 확대된 지면이 문서 스크롤 영역을 넓히지 않게(좁은 화면에서 가로 스크롤바 · 높이 튐) 무대 밖으로 삐져나온 부분을 애니메이션 동안만 잘라 둔다
    stage.style.overflow = 'clip';
    sheet.animate([
      { translate: `${from.left - to.left}px ${from.top - to.top}px`, scale: String(from.width / to.width) },
      { translate: '0 0', scale: '1' },
    ], { duration: STAY_ZOOM_MS, easing: 'cubic-bezier(.2, .8, .2, 1)' }).finished
      .catch(() => {})
      .then(() => { sheet.style.transformOrigin = ''; stage.style.overflow = ''; });
  }
  // 메인으로: 지금 보던 지면을 메인에 넘기고 간다. 메인에서 줌아웃하며 책상 위 신문 자리로 줄어든다(landingPage.js)
  let closing = false;
  function toDesk() {
    if (closing) return;
    closing = true;
    stage.freeze();
    handPaper(sheet, { backdrop: 'desk' }); // 지면 양옆은 책상
    navigate('landing', { from: 'paper' });
  }
  // 읽기 화면: 왼쪽 귀 "다른 사건 보기"(신문을 뒤집어 1면으로), 맨 아래 광고 단의 "공유하기" · "메인으로"(신문을 책상에 내려놓기).
  // 메인 가는 길을 사건 목록이 아니라 메인으로 둔 건 목록을 거듭 보게 하는 피로감 때문이다(FE-120)
  sheet.querySelector('.sheet-ear-left').addEventListener('click', foldToList);
  // 광고 칸은 읽는 상태가 된 뒤에만 눌린다(무대 중 지면 클릭이 "다음"이라 미리 달아 두면 공유 · 메인으로가 튀어 나간다)
  function armEndingAds() {
    const shareAd = sheet.querySelector('.sheet-ad.is-today');
    if (shareAd) {
      const text = shareAd.querySelector('.sheet-ad-text');
      const original = text.textContent;
      pressable(shareAd, '공유하기 — 이 사건 링크 보내기', async () => {
        await share(text, caseId);
        if (!text.textContent) text.textContent = original; // 공유 창을 닫았으면 안내 문구를 되돌린다
      });
    }
    const mainAd = sheet.querySelector('.sheet-ad.is-notice');
    if (mainAd) pressable(mainAd, '메인으로 — 신문을 책상에 내려놓기', toDesk);
  }
  let folding = false;
  function foldToList() {
    if (folding) return;
    folding = true;
    if (reducedMotion()) { navigate('list'); return; }
    // 신문을 뒤집는다(플립): 살짝 들어 올리며 옆으로 세우고(앞면이 사라짐), 마저 돌려 빈 뒷면이 보이게 내려놓는다.
    // 축은 지면의 세로 가운데, 높이는 지금 보는 화면 가운데다. 읽기 모드의 지면은 transform을 끄므로 개별 속성(rotate · scale)으로 움직인다
    tilt('50%');
    const [up, down] = FLIP_MS;
    sheet.animate([{ rotate: 'y 0deg', scale: '1' }, { rotate: 'y 90deg', scale: '1.05' }],
      { duration: up, easing: 'cubic-bezier(.45, 0, .8, .5)', fill: 'forwards' }).finished
      .then(() => {
        sheet.classList.add('is-flipped'); // 옆으로 선 순간 앞면 기사를 감춘다(뒷면 = 빈 지면)
        return sheet.animate([{ rotate: 'y 90deg', scale: '1.05' }, { rotate: 'y 180deg', scale: '1' }],
          { duration: down, easing: 'cubic-bezier(.2, .5, .3, 1)', fill: 'forwards' }).finished;
      })
      .catch(() => {}).then(() => {
        if (closing) return; // 뒤집는 사이 메인으로 갔다면 목록으로 되돌리지 않는다
        // 뒤집힌 빈 뒷면을 사건 목록에 넘긴다: 목록이 그 종이를 자기 지면 자리로 옮기고 기사를 그 위에 페이드인한다(caseListPage.js)
        handPaper(sheet, { backdrop: pageBackdrop(sheet), blank: true });
        navigate('list', { from: 'flip' });
      });
  }
  // 성향 테스트: 신문을 왼쪽 가장자리를 축으로 넘겨 세운다. 넘어간 뒷면(오피니언 면, /test/paper)이 같은 축에서 내려앉으며 이어진다
  function turnToTest() {
    if (folding || closing) return;
    folding = true;
    if (reducedMotion()) { navigate('testPaper'); return; }
    tilt('0');
    sheet.animate([{ rotate: 'y 0deg' }, { rotate: 'y -88deg' }],
      { duration: TURN_MS, easing: 'cubic-bezier(.45, 0, .7, .4)', fill: 'forwards' }).finished
      .catch(() => {}).then(() => {
        if (closing) return; // 넘기는 사이 메인으로 갔다면 테스트 면으로 가지 않는다(foldToList와 같다)
        navigate('testPaper', { from: 'paper' });
      });
  }
  // 3D로 돌릴 준비: 원근은 지면의 부모에 건다(보는 눈은 지금 화면 가운데). 지면이 화면보다 길어서 축 높이를 화면 가운데로 맞춘다
  function tilt(axisX) {
    const parent = sheet.parentElement;
    const middle = window.innerHeight / 2;
    parent.style.perspective = '2400px';
    parent.style.perspectiveOrigin = `50% ${middle - parent.getBoundingClientRect().top}px`;
    sheet.style.transformOrigin = `${axisX} ${middle - sheet.getBoundingClientRect().top}px`;
  }
  return stage;
}

// 지면 바깥에 보이는 바탕색(지면에서 위로 올라가며 처음 칠해진 색). 넘겨줄 때 장식 층이 같은 색으로 화면을 덮는다
function pageBackdrop(node) {
  for (let parent = node.parentElement; parent; parent = parent.parentElement) {
    const color = getComputedStyle(parent).backgroundColor;
    if (color && color !== 'transparent' && color !== 'rgba(0, 0, 0, 0)') return color;
  }
  return 'transparent';
}

// 6막 화면 틀: 무대(카메라 틀)가 화면 전체를 채운다 — 판사의 책상 위 다음 날 신문을 화면 가득 들여다본다(FE-32, comparison.css)
// 5막에서 이어 받을 때는 renderComparisonStage를 부르기 전에 sceneRect(root)로 5막 장면 자리를 재 둔다
export const sceneRect = (root) => root.querySelector('.stage-wrap .scene')?.getBoundingClientRect();
export function mountNewspaperStage(root, { stage }) {
  root.className = 'comparison-page comparison-full'; // comparison-page: 화면 공통 글꼴 · [hidden] 규칙(review.css)
  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  root.replaceChildren(desk, element('h1', 'visually-hidden', '세 판결 비교'), stage);
  window.scrollTo(0, 0);
}

// 무대 화면 틀(4 ~ 6막이 같은 화면에서 이어질 때도 쓴다): 사건 제목 줄 + 가운데 넓은 무대
export function mountStage(root, { caseHeader, badge, heading, stage, extra = [] }) {
  root.classList.add('has-stage');
  const header = element('header', 'case-topbar');
  header.append(element('strong', 'case-title', caseHeader.title), element('span', 'case-badge', badge));
  const main = element('div', 'review-main');
  // 무대 안 상자 제목(h3)이 h1 바로 밑에서 단계를 건너뛰지 않게 보이지 않는 h2를 끼운다(FE-97)
  main.append(element('h1', 'page-heading visually-hidden', heading), element('h2', 'visually-hidden', `${heading} 진행 화면`), stage, ...extra);
  const layout = element('main', 'review-layout');
  layout.append(main);
  root.replaceChildren(header, layout);
}
