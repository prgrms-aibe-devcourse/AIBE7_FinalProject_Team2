import { button, element, renderSiteFooter } from './siteLayout.js';
import { casePhoto, todayLabel } from '../utils/newspaper.js';

// 신문 지면 공통 짜임 (FE-32). S-02 사건 목록(신문 1면)과, 사건을 고른 뒤 이어지는 사건 기사 지면(S-20 · 1 · 2막)이
// 같은 제호 · 날짜 띠 · 탭 줄 · 배너 · [사진 | 기사 단]을 쓴다. 스타일은 caseList.css(.case-list-page)를 그대로 쓴다.

// 제호: 정보 줄(판결 체험 신문 · 보조 메뉴) → [왼쪽 귀 | 제호 | 오른쪽 귀] → 날짜 띠
export function newspaperMasthead({ navigate, earRight, earLeft: givenLeft, title = '내Law남불 사건 목록' }) {
  const header = element('header', 'masthead');
  const infoRow = element('div', 'info-row');
  const links = element('nav', 'info-links');
  links.setAttribute('aria-label', '보조 메뉴');
  links.append(
    button('← 처음으로', () => navigate('landing'), 'paper-link'),
    button('서비스 소개', () => navigate('landing', { section: 'intro' }), 'paper-link'),
  );
  infoRow.append(element('span', '', '판결 체험 신문'), links);

  const earLeft = givenLeft ?? element('div', 'ear ear-left');
  if (!givenLeft) earLeft.append('“모든 판결에는', element('br'), '이유가 있다”', element('small', '', '사건을 읽다. 판단을 묻다.'));
  const brand = element('div', 'brand');
  const heading = element('h1');
  heading.setAttribute('aria-label', title);
  heading.append(element('span', '', '내'), element('span', 'law', 'Law'), element('span', '', '남불'));
  brand.append(heading);
  const mast = element('div', 'mast');
  mast.append(earLeft, brand, earRight);

  const dateline = element('div', 'dateline');
  dateline.append(element('span', '', '내Law남불'), element('span', '', todayLabel()), element('span', '', '참여 무료'));
  header.append(infoRow, mast, dateline);
  return header;
}

// 사건 기사 지면에 실을 기사: 메인 책상 신문 · S-02와 같은 사건 데이터(API 1 · 16의 headline · deck · article · thumbnailUrl)에서 만든다.
// 그래야 책상 위 신문 → 집어 든 신문 → 사건 기사 지면의 제목 · 본문 · 사진이 항상 같다. 사건 데이터가 없으면 대본 지면(fallback)을 쓴다
export function paperFromCase(data, fallback = {}) {
  if (!data) return fallback;
  return {
    headline: data.headline || data.title,
    deck: data.deck || data.shortIntro,
    article: data.article?.length ? data.article : [data.shortIntro].filter(Boolean),
    photo: casePhoto(data),
    extraPhotos: fallback.extraPhotos ?? [], // 기사 속 작은 사진(대본 지면에만 있다, 사건 1의 재연 컷)
    caption: '사건 이해를 돕기 위한 연출 이미지',
    desk: '사회부', // S-02 기사와 같은 기자 줄
  };
}

// 사건 기사 지면의 귀(제호 양옆). 왼쪽은 "다른 사건 보기"(S-02로), 오른쪽은 사건 번호 · 분류(S-20에서는 "이어서 하기"로 바꿔 끼운다)
export function earButton(title, sub, action, className = '') {
  const node = button('', action, `ear ear-button ${className}`.trim());
  node.append(element('b', '', title), element('small', '', sub));
  return node;
}
export function caseInfoEar(info) {
  const ear = element('div', 'ear ear-right');
  ear.append(element('b', '', `사건 ${String(info.caseId).padStart(2, '0')}`), `${info.crimeCategoryLabel ?? ''} · 형량 비공개`);
  return ear;
}

function newsPhoto(src, width, height) {
  const photo = element('img', 'photo sheet-case-photo');
  photo.src = src;
  photo.alt = ''; // 연출 이미지라 읽지 않는다(사건 내용은 기사 본문에 있다)
  photo.width = width; // 크기 힌트(CSS가 폭 · 비율을 정한다) — 사진이 늦게 떠도 화면이 밀리지 않게
  photo.height = height;
  photo.loading = 'lazy';
  return photo;
}
function smallFigure({ src, caption }) {
  const figure = element('figure', 'story-photo is-small');
  figure.append(newsPhoto(src, 1000, 563), element('figcaption', 'caption', caption ?? '사건 이해를 돕기 위한 연출 이미지'));
  return figure;
}
// 인용구: 기사 첫 문장(없는 말을 지어내지 않는다)
function pullQuote(paragraph) {
  const first = (paragraph ?? '').split(/(?<=다\.)\s*/)[0].trim();
  const quote = element('blockquote', 'story-quote');
  quote.setAttribute('aria-hidden', 'true'); // 본문 첫 문장을 되풀이한 것이라 읽지 않는다
  quote.textContent = `“${first}”`;
  return quote;
}
// 사건 메모: 제목 옆 상자(죄명 · 범죄 분류 · 예상 소요 · 참여 · 키워드). 받은 값만 줄로 그린다(형량 · 판결 정보는 없다)
const DIFFICULTY = { LOW: '쉬움', MID: '보통', HIGH: '어려움' };
function caseMemo(info) {
  const memo = element('aside', 'story-memo');
  memo.setAttribute('aria-label', '사건 메모');
  memo.append(element('h2', 'story-memo-title', '사건 메모'));
  const list = element('dl', '');
  [
    ['죄명', info.chargeName],
    ['범죄 분류', info.crimeCategoryLabel],
    ['예상 소요', info.estimatedMinutes ? `약 ${info.estimatedMinutes}분${DIFFICULTY[info.difficulty] ? ` · ${DIFFICULTY[info.difficulty]}` : ''}` : null],
    ['참여', Number.isFinite(info.participantCount) ? `${info.participantCount.toLocaleString('ko-KR')}명` : null],
  ].forEach(([term, value]) => { if (value) list.append(element('dt', '', term), element('dd', '', value)); });
  memo.append(list);
  if (info.keywords?.length) memo.append(element('p', 'story-memo-tags', info.keywords.map((word) => `#${word}`).join('  ')));
  memo.append(element('p', 'story-memo-note', '형량은 체험을 마친 뒤 공개돼요.'));
  return memo;
}

/**
 * 사건 기사 지면(S-20 · 1 · 2막 무대의 장면). S-02 1면과 같은 짜임에 이 사건 기사 하나를 머리기사로 싣는다.
 * 무대 카메라가 맞추는 자리(data-focus): head(배너 제목) · photo(사진) · body(기사 단).
 * 빈 자리(data-slot): acts(탭 줄 — 막 칩). 2막 사전 판단 등 무대 카드는 지면 위에 엽서 팝업으로 뜬다(stage.css)
 * @param {{ paper: object, info: object, navigate: Function, earRight?: HTMLElement }} options paper: 대본의 지면(headline · deck · photo · caption · article),
 *   info: 사건(title · crimeType · crimeCategoryLabel · caseId), earRight: 오른쪽 귀를 바꿔 끼울 때(S-20 "이어서 하기")
 */
export function caseNewspaper({ paper, info, navigate, earRight = caseInfoEar(info) }) {
  const sheet = element('div', 'paper case-paper');
  const earLeft = earButton('← 다른 사건 보기', '오늘의 지면으로 돌아가요', () => navigate('list'), 'ear-left');

  // 안쪽 면 머리글: 1면 제호(큰 글씨) 대신 작은 제호 + 면 이름 · 날짜 · 쪽수. 양 끝은 귀 버튼. 지면이 넘어갔다는 게 한눈에 보이게 한다
  const runner = element('header', 'page-head');
  const title = element('div', 'page-head-title');
  const wordmark = element('p', 'page-head-brand');
  wordmark.setAttribute('aria-label', '내Law남불');
  wordmark.append(element('span', '', '내'), element('span', 'law', 'Law'), element('span', '', '남불'));
  title.append(wordmark, element('p', 'page-head-meta', `사회면 · ${todayLabel()} · 3면`));
  runner.append(earLeft, title, earRight);

  const tabsZone = element('div', 'tabs-zone');
  const actSlot = element('div', 'paper-acts');
  actSlot.dataset.slot = 'acts';
  tabsZone.append(actSlot);

  // 기사(A안, 실제 신문 기사 면): [제목 · 부제 · 기자 줄 | 사건 메모] → [사진 · 작은 사진 | 본문 두 단(첫 글자 크게 · 인용구 · 작은 사진)]
  const article = element('article', 'story');
  article.setAttribute('aria-label', paper.headline);
  const head = element('div', 'story-head');
  head.dataset.focus = 'head';
  const byline = element('p', 'story-byline');
  byline.append(element('b', '', `${paper.desk ?? '사회부'} 기자`), element('span', '', todayLabel()),
    element('span', '', `${info.crimeCategoryLabel ?? ''} · 사건 ${String(info.caseId).padStart(2, '0')}`));
  head.append(element('h1', 'story-title', paper.headline), element('p', 'story-deck', paper.deck), byline);
  const top = element('div', 'story-top');
  top.append(head, caseMemo(info));

  const figure = element('figure', 'story-photo');
  figure.dataset.focus = 'photo';
  figure.append(newsPhoto(paper.photo, 1200, 675), element('figcaption', 'caption', paper.caption));
  const [sidePhoto, inlinePhoto] = paper.extraPhotos ?? [];
  const media = element('div', 'story-media');
  media.append(figure);
  if (sidePhoto) media.append(smallFigure(sidePhoto));

  const body = element('div', 'story-body');
  body.dataset.focus = 'body';
  paper.article.forEach((line, i) => {
    body.append(element('p', '', line));
    // 첫 단락 뒤에 인용구(기사 첫 문장), 둘째 단락 뒤에 작은 사진 — 신문 기사 면처럼 본문 사이에 쉼을 둔다
    if (i === 0) body.append(pullQuote(paper.article[0]));
    if (i === 1 && inlinePhoto) body.append(smallFigure(inlinePhoto));
  });
  const main = element('div', 'story-main');
  main.append(media, body);
  article.append(top, main);

  const front = element('main', 'front');
  front.append(tabsZone, article);
  const note = element('p', 'paper-note', '이 지면의 사건과 기사는 판결 체험을 위해 재구성한 것이며, 사진은 AI로 생성한 연출 이미지입니다.');
  // 법적 고지는 S-02처럼 지면 맨 아래 줄로 둔다(화면 아래 공통 푸터는 stage.css에서 숨긴다)
  sheet.append(runner, front, note, renderSiteFooter());
  return sheet;
}
