import { button, element, renderSiteFooter } from './siteLayout.js';
import { fallbackImages, todayLabel } from '../utils/newspaper.js';

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
    photo: data.thumbnailUrl || fallbackImages[data.crimeType] || fallbackImages.MURDER,
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
  ear.append(element('b', '', `사건 ${String(info.caseId).padStart(2, '0')}`), `${info.crimeCategoryLabel ?? ''} · 형량은 체험을 마친 뒤 공개됩니다.`);
  return ear;
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
  const front = element('main', 'front');

  const tabsZone = element('div', 'tabs-zone');
  const actSlot = element('div', 'paper-acts');
  actSlot.dataset.slot = 'acts';
  tabsZone.append(actSlot, element('p', 'case-count', `${info.title}`));

  const banner = element('div', 'banner');
  banner.dataset.focus = 'head';
  banner.append(element('h2', '', paper.headline));

  const figure = element('figure', 'lead-photo');
  figure.dataset.focus = 'photo';
  const photo = element('img', 'photo sheet-case-photo');
  photo.src = paper.photo;
  photo.alt = '';
  figure.append(photo, element('figcaption', 'caption', paper.caption));

  const lead = element('article', 'article lead-col');
  lead.dataset.focus = 'body';
  const kicker = element('div', 'kicker');
  const square = element('span', `type-square type-${info.crimeType}`);
  square.setAttribute('aria-hidden', 'true');
  kicker.append(element('span', '', '머리기사'), element('span', '', '·'), square, element('span', '', info.crimeCategoryLabel ?? ''));
  const dense = element('div', 'dense');
  paper.article.forEach((line) => dense.append(element('p', '', line)));
  lead.append(kicker, element('p', 'lead-intro', paper.deck), element('p', 'byline', paper.desk ?? '사회부'), dense);

  const grid = element('section', 'front-grid');
  grid.setAttribute('aria-label', '머리기사');
  grid.append(figure, lead);

  front.append(tabsZone, banner, grid);
  const note = element('p', 'paper-note', '이 지면의 사건과 기사는 판결 체험을 위해 재구성한 것이며, 사진은 AI로 생성한 연출 이미지입니다.');
  // 법적 고지는 S-02처럼 지면 맨 아래 줄로 둔다(화면 아래 공통 푸터는 stage.css에서 숨긴다)
  sheet.append(newspaperMasthead({ navigate, earLeft, earRight, title: `내Law남불 — ${info.title}` }), front, note, renderSiteFooter());
  return sheet;
}
