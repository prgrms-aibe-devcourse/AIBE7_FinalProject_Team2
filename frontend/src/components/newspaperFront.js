import { button, element, mockTag, renderSiteFooter } from './siteLayout.js';
import { API_MODE } from '../api/config.js';
import { casePhoto, commonCategoryOf, extraPhotosFromApi, extraPhotosOf, todayLabel, useCommonPhotoOnError } from '../utils/newspaper.js';

// 신문 지면 공통 짜임 (FE-32). S-02 사건 목록(신문 1면)과, 사건을 고른 뒤 이어지는 사건 기사 지면(S-20 · 1막)이
// 같은 제호 · 날짜 띠 · 탭 줄 · 배너 · [사진 | 기사 단]을 쓴다. 스타일은 caseList.css(.case-list-page)를 그대로 쓴다.

// 제호: 정보 줄(← 메인으로 · 판결 체험 신문) → [왼쪽 귀 | 제호 | 오른쪽 귀] → 날짜 띠
// FE-56: 정보 줄 왼쪽에 "메인으로"를 두고, 오른쪽의 "처음으로" · "서비스 소개"는 뺐다(공통 헤더가 모든 화면 위에 있다)
export function newspaperMasthead({ navigate, earRight, earLeft: givenLeft, title = '내Law남불 사건 목록' }) {
  const header = element('header', 'masthead');
  const infoRow = element('div', 'info-row');
  infoRow.append(button('← 메인으로', () => navigate('landing'), 'paper-link'), element('span', '', '판결 체험 신문'));

  const earLeft = givenLeft ?? element('div', 'ear ear-left');
  if (!givenLeft) earLeft.append('“모든 판결에는', element('br'), '이유가 있다”');
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

// 사건 기사 지면에 실을 기사(FE-64). API 17(casePaper)이 있으면 그 값(headline · deck · article · photo · caption ·
// desk · extraPhotos)을 쓴다 — 관리자가 신문 지면을 만들어 둔 사건은 책상 위 신문 → 집어 든 신문 → 사건 기사 지면의
// 제목 · 본문 · 사진이 항상 같다. casePaper를 못 받았으면(PAPER_NOT_FOUND · 조회 실패) 사건 데이터(API 1 · 16)와
// 대본 지면(fallback, 사건 1의 재연 컷)으로 예전처럼 채운다. 둘 다 못 받았을 때만 대본 지면을 그대로 쓴다
export function paperFromCase(data, casePaper, fallback = {}) {
  // 둘 다 없을 때만 대본 지면을 그대로 쓴다. 사건 데이터(API 16)만 못 받은 경우에도 지면(API 17)이 있으면 그것으로 그린다 —
  // 여기서 대본으로 돌아가면 같은 화면의 증거조사 사진만 서버 사진이 되어 지면과 어긋난다(PR #284 리뷰)
  if (!data && !casePaper) return fallback;
  if (casePaper) {
    // 사건 데이터를 못 받았을 수 있으므로(detail = null) data는 모두 null 안전하게 읽고, 빈 값은 대본 지면으로 채운다
    const article = casePaper.article?.length ? casePaper.article
      : data?.article?.length ? data.article
        : data?.shortIntro ? [data.shortIntro]
          : fallback.article ?? [];
    return {
      headline: casePaper.headline || data?.headline || data?.title || fallback.headline,
      deck: casePaper.deck || data?.deck || data?.shortIntro || fallback.deck,
      article,
      // 대표 사진: 지면 → 사건 썸네일(없으면 사건 번호로 고정한 공용 컷) → 대본 지면. casePhoto는 사건 데이터가 없어도 공용 컷을 준다
      photo: casePaper.photo || (data ? casePhoto(data) : fallback.photo ?? casePhoto(data)),
      extraPhotos: extraPhotosOf(extraPhotosFromApi(casePaper.extraPhotos), commonCategoryOf(data), Number(data?.caseId) || 0),
      caption: casePaper.caption || fallback.caption || '사건 이해를 돕기 위한 연출 이미지',
      desk: casePaper.desk || fallback.desk || '사회부',
    };
  }
  return {
    headline: data.headline || data.title,
    deck: data.deck || data.shortIntro,
    article: data.article?.length ? data.article : [data.shortIntro].filter(Boolean),
    photo: casePhoto(data),
    extraPhotos: extraPhotosOf(fallback.extraPhotos, commonCategoryOf(data), Number(data?.caseId) || 0), // 기사 속 작은 사진(대본 지면에만 있다, 사건 1의 재연 컷). 없는 사건은 범죄 분류에 맞는 공용 컷
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

function newsPhoto(src, width, height, seed = 0, role, category) {
  const photo = element('img', 'photo sheet-case-photo');
  useCommonPhotoOnError(photo, seed, role, category); // src보다 먼저 달아 두어야 첫 로딩 실패도 잡는다
  photo.src = src;
  photo.alt = ''; // 연출 이미지라 읽지 않는다(사건 내용은 기사 본문에 있다)
  photo.width = width; // 크기 힌트(CSS가 폭 · 비율을 정한다) — 사진이 늦게 떠도 화면이 밀리지 않게
  photo.height = height;
  photo.loading = 'lazy';
  return photo;
}
function smallFigure({ src, caption }, seed, role, category) {
  const figure = element('figure', 'story-photo is-small');
  figure.append(newsPhoto(src, 1000, 563, seed, role, category), element('figcaption', 'caption', caption ?? '사건 이해를 돕기 위한 연출 이미지'));
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
// 지면 목차: 신문의 다른 면으로 간다. 앱 기능을 신문의 면에 빗댄다 — 1면 = 오늘의 사건 목록, 3면 사회 = 지금 이 사건,
// 오피니언 = 판결 성향 테스트, 독자 = 마이페이지. 지금 면은 aria-current로 표시하고 누르지 않는다
const PAGES = [
  { page: '1면', name: '오늘의 지면', route: 'list' },
  { page: '3면', name: '사회', current: true },
  { page: '8면', name: '오피니언 · 판결 성향 테스트', route: 'test' },
  { page: '10면', name: '독자 · 마이페이지', route: 'me' },
];
function pageIndex(navigate) {
  const nav = element('nav', 'page-index');
  nav.setAttribute('aria-label', '신문 목차');
  PAGES.forEach(({ page, name, route, current }) => {
    const item = route ? button('', () => navigate(route), 'page-index-item') : element('span', 'page-index-item is-current');
    if (current) item.setAttribute('aria-current', 'page');
    item.append(element('b', '', page), element('span', '', name));
    nav.append(item);
  });
  return nav;
}

// 사건 메모: 제목 옆 상자(죄명 · 범죄 분류 · 참여 · 키워드). 받은 값만 줄로 그린다(형량 · 판결 정보는 없다)
function caseMemo(info) {
  const memo = element('aside', 'story-memo');
  memo.setAttribute('aria-label', '사건 메모');
  memo.append(element('h2', 'story-memo-title', '사건 메모'));
  const list = element('dl', '');
  [
    ['죄명', info.chargeName],
    ['범죄 분류', info.crimeCategoryLabel],
    ['참여', Number.isFinite(info.participantCount) ? `${info.participantCount.toLocaleString('ko-KR')}명` : null],
  ].forEach(([term, value]) => {
    if (!value) return;
    const dd = element('dd', '', value);
    if (term === '참여' && API_MODE === 'mock') dd.append(mockTag()); // 목 참여 수는 실제 반응이 아니다(FE-56)
    list.append(element('dt', '', term), dd);
  });
  memo.append(list);
  if (info.keywords?.length) memo.append(element('p', 'story-memo-tags', info.keywords.map((word) => `#${word}`).join('  ')));
  memo.append(element('p', 'story-memo-note', '형량은 체험을 마친 뒤 공개돼요.'));
  return memo;
}

/**
 * 사건 기사 지면(S-20 · 1막 무대의 장면). S-02 1면과 같은 짜임에 이 사건 기사 하나를 머리기사로 싣는다.
 * 무대 카메라가 맞추는 자리(data-focus): head(배너 제목) · photo(사진) · body(기사 단).
 * 탭 줄은 신문의 다른 면(1면 · 3면 사회 · 오피니언 · 독자)으로 가는 목차다(막 칩은 쓰지 않는다). 1막 엽서의 사전 판단 등 무대 카드는 지면 위에 엽서 팝업으로 뜬다
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
  tabsZone.append(pageIndex(navigate));

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
  figure.append(newsPhoto(paper.photo, 1200, 675, 0, undefined, commonCategoryOf(info)), element('figcaption', 'caption', paper.caption));
  const [sidePhoto, inlinePhoto] = extraPhotosOf(paper.extraPhotos);
  const media = element('div', 'story-media');
  media.append(figure);
  media.append(smallFigure(sidePhoto, 1, 'origin', commonCategoryOf(info))); // 발단사진 — 없거나 실패하면 발단 공용 컷

  const body = element('div', 'story-body');
  body.dataset.focus = 'body';
  paper.article.forEach((line, i) => {
    body.append(element('p', '', line));
    // 첫 단락 뒤에 인용구(기사 첫 문장), 둘째 단락 뒤에 작은 사진 — 신문 기사 면처럼 본문 사이에 쉼을 둔다
    if (i === 0) body.append(pullQuote(paper.article[0]));
    if (i === 1) body.append(smallFigure(inlinePhoto, 2, 'conclusion', commonCategoryOf(info))); // 결론사진 — 없거나 실패하면 결론 공용 컷
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
