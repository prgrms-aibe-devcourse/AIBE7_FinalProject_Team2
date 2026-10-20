import { button, element, renderSiteFooter } from '../../components/siteLayout.js';
import { casePhoto, fallbackImages, todayLabel } from '../../utils/newspaper.js';
import { takePaper } from '../../utils/paperHandoff.js';
import { flyCoffee } from '../../utils/coffeeDive.js';
import { bootLaptop } from '../../utils/laptopBoot.js';
import { reducedMotion } from '../../utils/motion.js';
import { isAdmin } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';
import { peelOff } from '../../utils/peelOff.js';

// S-01 메인 — 판사의 책상 (FE-18, 시안 "B안 일러스트 · C안 구도", 논의기록 2026-10-12 S01 메인 책상 시안 결정)
// 책상 위 물건이 곧 메뉴다: 신문 = 사건 목록(S-02), 서류철 = 판결 성향 테스트(S-15), 커피잔 = 마이페이지(S-17), 노트북 = 커뮤니티.
// 펜 = 의견 보내기(FE-50, 준비 중). 휴대폰을 없애고 그 자리에 두었다.
// 그림은 CSS · 인라인 SVG로만 그린다(이미지 파일 없음). 색은 style.css의 --color-desk-* 역할 토큰만 쓴다.
// 2D 평면 일러스트라 물건은 평소에 들리거나 기울지 않는다(팀 결정). 720px 이하에서는 책상 대신 메뉴 카드를 보여 준다.
// 흐름 목업 ①(FE-32): 신문을 누르면 신문만 책상에서 들려 올라오고(그림자가 길어진다), 이어서 신문 지면(S-02)이 화면 아래에서 올라온다(FE-50,
// 책상은 그 뒤에서 흐려진다 — caseListPage.js). 신문 들기는 이 연출에만 쓰는 예외다.
// 커피잔을 누르면(FE-44) 커다란 커피잔이 우상단에서 빠르게 날아와 서는 동안 책상이 마이페이지로 바뀌고, 다 바뀌면 잔이 오른쪽으로 빠져나간다(utils/coffeeDive.js).
// 노트북을 누르면(FE-47) 화면이 눈을 감듯 어두워졌다가 눈을 뜨듯 열리며 커뮤니티가 나온다 — 컴퓨터 부팅(utils/laptopBoot.js).
// 책상에는 내로(신문 위쪽, 포스트잇에 가려 있음) · 남불(펜 아래)이 볼을 괴고 엎드려 있다(장식).

// 책상 그림의 기준 좌표계. 화면 너비에 맞춰 통째로 줄인다(--desk-scale)
const SCENE_WIDTH = 1672;

// 물건(메뉴) 목록. route가 없으면 준비 중이라 누를 수 없다(낭독기에는 "준비 중"으로 읽힌다).
// 화면이 생기면 route에 화면 이름(routes.js의 name)만 넣으면 된다.
// 책상 위 물건에는 이름표(메모지)를 붙이지 않는다(FE-50). label은 낭독기 이름과 좁은 화면 메뉴 카드에 쓴다
const OBJECTS = {
  folder: { label: '판결 성향 테스트', route: 'test', mobileText: '나의 판결 성향 알아보기' },
  news: { label: '실제 사건 판결 체험', route: 'list', mobileText: '오늘의 사건' },
  mug: { label: '마이페이지', route: 'me', mobileText: '내 판결 기록' },
  laptop: { label: '커뮤니티', route: 'community', mobileText: '다른 사람의 생각' },
  pen: { label: '의견 보내기', route: null }, // FE-50: 펜 = 의견 보내기. 화면이 생기면 route만 넣는다(좁은 화면 메뉴에는 아직 없다)
};
const ADMIN_MUG = { label: '관리자 페이지', route: homeRoute(), mobileText: '사건 검수 · 운영' }; // 관리자로 로그인하면 마이페이지 자리 (FE-53)
const SOON = '곧 열려요';
// 책상 위 내로 · 남불(기준 좌표계 px, 오른쪽 끝에서 잰다 — FE-50에서 소품 배치를 좌우로 뒤집었다).
// shortcut: 원본 이미지 레포 PNG를 직접 읽는다, 웹 webp 변환(WBS 7.6.8) 때 public 경로로 옮긴다
const CUTS = 'https://raw.githubusercontent.com/peanutboi710/NaeLawNamBul_Images/main/character/cuts/';
const CHEEKS = [
  { who: 'naero', look: 'idle-sorry', right: 644, top: 2, height: 170 }, // 가운데에서 살짝 오른쪽(가운데 x ≈ 960), 턱이 신문 위쪽 가장자리에 걸친다
  { who: 'nambul', look: 'idle-thinking', right: 1300, top: 782, height: 158 },
];
let newsCache = null; // 사건 목록(API 1) 마지막 응답
const LIFT_MS = 380; // 신문을 집어 드는 시간(landing.css .is-held와 같게). 그 뒤 지면이 아래에서 올라온다
// 8막에서 "메인으로" 올 때: 보던 지면이 줌아웃(제자리에서 .82배)과 페이드아웃을 함께 하며 책상이 드러난다
const FADE_MS = 800;

// 서비스 소개(FE-50): "뉴스만 본 나 → 모든 사실을 본 나"를 잇는 한 줄. 양 끝은 상태, 가운데 두 지점은 되돌릴 수 없는 판단이다
const TRACK = [
  { pole: '뉴스만 본 나' },
  { title: '첫인상 판단', text: '기사 제목과 재연만 보고 먼저 고릅니다. 고른 뒤에는 바꿀 수 없어요.' },
  { title: '최종 판결', text: '사실관계 · 양측 주장 · 법률을 차례로 읽고 형량을 정합니다. 확정하면 바꿀 수 없어요.' },
  { pole: '모든 사실을 본 나' },
];
const VERDICTS = ['내 판결', 'AI 판결', '실제 판결']; // 세 판결은 대등하다(같은 모양으로 나란히)

// 서류철 그림 (장식). 색은 클래스로 landing.css에서 칠한다
const FOLDER_SVG = `
<svg viewBox="0 0 443 521" aria-hidden="true" focusable="false">
  <path class="folder-back" d="M262 14 L318 4 Q326 3 328 11 L338 52 L372 47 Q380 46 382 54 L438 420 Q440 432 428 436 L120 516 Z"/>
  <path class="folder-shade" d="M380 128 L396 126 L404 196 L389 199 Z"/>
  <path class="folder-sheet" d="M268 36 L346 30 L414 404 L340 424 Z"/>
  <g class="folder-lines">
    <path d="M300 70 L330 67"/><path d="M306 104 L338 101"/><path d="M313 140 L345 137"/><path d="M320 176 L352 173"/>
    <path d="M327 212 L359 209"/><path d="M334 248 L366 245"/><path d="M341 284 L373 281"/><path d="M348 320 L380 317"/>
  </g>
  <path class="folder-front" d="M4 85 L262 6 Q272 3 275 13 L372 404 Q375 414 365 418 L104 516 Q94 519 92 509 L2 96 Q0 88 4 85 Z"/>
  <path class="folder-gloss" d="M4 85 L262 6 Q272 3 275 13 L282 40 L12 128 Z"/>
  <path class="folder-edge" d="M92 509 L104 516 L365 418 L372 404 L360 400 L100 498 Z"/>
</svg>`;

function span(className, text) {
  return element('span', className, text);
}

// S-01 메인
export function renderLandingPage(container, { api, navigate, section, from }) {
  const objects = isAdmin() ? { ...OBJECTS, mug: ADMIN_MUG } : OBJECTS;
  const goMug = () => navigate(objects.mug.route, objects.mug.route === 'me' ? { from: 'mug' } : {}); // 마이페이지만 커피잔에서 왔다는 표시를 쓴다
  const root = element('div', 'landing-page');
  const intro = element('section', 'feature-section');
  intro.id = 'intro';
  intro.setAttribute('aria-labelledby', 'feature-heading');
  const showIntro = () => intro.scrollIntoView({ behavior: 'smooth' });

  // ---- 책상 (넓은 화면) ----
  const scene = element('section', 'desk-scene');
  scene.setAttribute('aria-labelledby', 'desk-heading');
  const world = element('div', 'desk-world');
  scene.append(world);

  // 화면 제목은 낭독기용으로만 둔다(FE-50에서 책상 위 헤드라인을 없앴다)
  const h1 = element('h1', 'visually-hidden', '다 알고 나서도, 같은 판단일까?');
  h1.id = 'desk-heading';
  scene.prepend(h1);

  const deco = (className) => {
    const node = span(className);
    node.setAttribute('aria-hidden', 'true');
    return node;
  };

  // 물건 = 버튼. 준비 중이면 aria-disabled로 두고 누르지 않는다(포커스는 받아 "준비 중"을 읽게 한다)
  function deskObject(key, ...art) {
    const { label, route } = objects[key];
    const node = element('button', `desk-object obj-${key}${route ? '' : ' is-soon'}`);
    node.type = 'button';
    node.setAttribute('aria-label', route ? label : `${label} — 준비 중`);
    if (route) node.addEventListener('click', () => (ACTIONS[key] ?? (() => navigate(route)))(node));
    else node.setAttribute('aria-disabled', 'true');
    node.append(...art);
    return node;
  }

  // 서류철
  const folderArt = span('folder-art');
  folderArt.innerHTML = FOLDER_SVG;
  folderArt.append(deco('folder-label'));
  folderArt.lastChild.textContent = '3분 성향검사';

  // 신문 — 집어 들면 펼쳐지는 S-02 지면을 줄여 놓은 모습(FE-32): 정보 줄 → [귀 | 제호 | 귀] → 날짜 띠 → 유형 탭 줄 →
  // 배너 제목 → [사진 | 머리기사 단] → 추천 2건(지면 끝까지, FE-50). 사건 목록의 머리기사로 채운다(불러오기 전 · 실패 시 기본 문구)
  const news = span('news-art');
  news.setAttribute('aria-hidden', 'true');
  const newsSheets = span('news-sheets');
  newsSheets.append(span(''), span(''));
  const info = span('news-info');
  const infoLinks = span('news-info-links');
  infoLinks.append(span('', '← 메인으로'), span('', '판결 체험 신문')); // S-02 정보 줄(newspaperFront.js, FE-56)과 같게 왼쪽에 모은다
  info.append(infoLinks);
  const earLeft = span('news-ear');
  earLeft.append(element('b', '', '“모든 판결에는 이유가 있다”'));
  const earRight = span('news-ear');
  const earCount = span('', '법정에 오른 사건들');
  earRight.append(element('b', '', '오늘의 지면'), earCount);
  const brand = element('b', 'news-brand');
  brand.append('내', span('law', 'Law'), '남불');
  const mast = span('news-mast');
  mast.append(earLeft, brand, earRight);
  const date = span('news-date');
  date.append(span('', '내Law남불'), span('', todayLabel()), span('', '참여 무료'));
  const tabs = span('news-tabs');
  const tabCount = span('news-count', '');
  ['전체', '살인', '사기', '상해'].forEach((label, i) => tabs.append(span(i === 0 ? 'on' : '', label)));
  tabs.append(tabCount);
  const banner = span('news-banner', '오늘의 지면을 펼쳐 보세요');
  const photo = element('img', 'news-photo');
  photo.alt = '';
  photo.width = 3; // 비율 힌트(3:2). 크기는 CSS가 정한다
  photo.height = 2;
  photo.src = fallbackImages.MURDER;
  const kicker = span('news-kicker', '머리기사');
  const deck = span('news-deck', '실제 판결을 바탕으로 재구성한 사건이 기다리고 있습니다.');
  const lead = span('news-lead', '기사 제목만 보고 결론을 내리기는 이릅니다. 사건 기록을 한 단계씩 읽고, 판사석에서 직접 판결해 보세요.');
  const leadCol = span('news-lead-col');
  leadCol.append(kicker, deck, span('news-byline', '사회부'), lead);
  const front = span('news-front');
  front.append(photo, leadCol);
  const recs = span('news-recs');
  news.append(info, mast, date, tabs, banner, front, recs);

  // 노트북 화면의 말풍선은 커뮤니티 예시 문구(실제 글 아님). 메인은 처음 판단 전 화면이라 특정 사건 · 판결 결과를 떠올리게 하는 말은 쓰지 않는다
  const laptop = span('laptop-art');
  laptop.setAttribute('aria-hidden', 'true');
  const screen = span('laptop-screen');
  const display = span('laptop-display');
  const bubble = (text, who, mine) => {
    const node = span(mine ? 'bubble mine' : 'bubble');
    if (who) node.append(element('small', '', who));
    node.append(text);
    return node;
  };
  display.append(bubble('같은 기록을 읽었는데 생각이 이렇게 다르네요', '익명'), bubble('저는 처음 생각과 판결이 달라졌어요', '', true));
  screen.append(display);
  const body = span('laptop-body');
  body.append(span('laptop-keys'), span('laptop-trackpad'));
  laptop.append(screen, body);


  // 커피잔(위에서 내려다본 잔 · 커피 · 손잡이). 커피 원은 들어가기 연출의 기준 자리라 따로 둔다
  const mug = span('mug-art');
  mug.setAttribute('aria-hidden', 'true');
  mug.append(span('mug-coffee'));

  const cheeks = CHEEKS.map(({ who, look, right, top, height }) => {
    const image = element('img', `desk-cheek cheek-${who}`);
    image.src = `${CUTS}${who}/base/cheek/${who}_cut-cheek_${look}.png`;
    image.alt = '';
    image.setAttribute('aria-hidden', 'true');
    Object.assign(image.style, { right: `${right}px`, top: `${top}px`, height: `${height}px` });
    image.addEventListener('error', () => image.remove(), { once: true });
    return image;
  });

  // 내로를 가린 포스트잇(FE-50, 놀이). 누르면 떼어져 날아가고 내로가 드러난다. 화면을 다시 그리면 다시 붙어 있다.
  // 떼는 동안 포커스를 잃지 않게 바로 아래 신문으로 옮긴다
  const newsButton = deskObject('news', newsSheets, news);
  const rescue = element('button', 'desk-rescue');
  rescue.type = 'button';
  rescue.append(span('', '포스트잇을 제거해'), span('', '귀여운 고양이를'), span('', '구해주세요'));
  rescue.addEventListener('click', () => peelOff(rescue, { from: 'rotate(4deg)', to: 'translate(70px, -150px) rotate(32deg)', focusTarget: () => newsButton }));

  // 엽서(FE-50, 장식). 펜 옆 빈자리를 채운다. 우표는 판결 체험의 첫인상 엽서(paperStage.css)와 같다
  const postcard = deco('desk-postcard');
  postcard.append(span('postcard-stamp', '내Law\n남불'), span('postcard-title', '엽 서'), span('postcard-lines'));

  world.append(
    deco('desk-wood'), deco('desk-pad'),
    postcard,
    deskObject('pen', span('pen-art')),
    deskObject('folder', folderArt),
    newsButton,
    deskObject('laptop', laptop),
    deskObject('mug', mug),
    ...cheeks,
    rescue,
  );

  // 신문 집어 들기: 신문을 들어 올린 뒤 지면(S-02)으로 간다. 지면은 화면 아래에서 올라오고 이 책상은 그 뒤에서 흐려진다(caseListPage.js).
  // 움직임 줄이기면 바로 간다
  let lifting = false;
  function lift(target) {
    if (lifting) return;
    if (reducedMotion()) { navigate('list', { from: 'desk' }); return; }
    lifting = true;
    target.classList.add('is-held');
    setTimeout(() => { if (root.isConnected) navigate('list', { from: 'desk' }); }, LIFT_MS);
  }
  // 커피잔: 커다란 잔이 우상단에서 날아와 서고(coffeeDive), 날아오는 도중 마이페이지로 넘어간다. 책상이 다 바뀌면 잔이 오른쪽으로 빠져나간다. 움직임 줄이기면 바로 간다
  function sip() {
    if (lifting) return;
    if (reducedMotion()) { goMug(); return; }
    lifting = true;
    flyCoffee(() => { if (root.isConnected) goMug(); }, root);
  }
  // 노트북: 눈을 감았다 뜨듯 커뮤니티가 나온다(laptopBoot). 움직임 줄이기면 바로 간다
  function openLaptop() {
    if (lifting) return;
    if (reducedMotion()) { navigate('community'); return; }
    lifting = true;
    bootLaptop(() => (root.isConnected ? navigate('community') : undefined)); // 커뮤니티가 다 그려질 때까지 눈을 감고 기다린다(laptopBoot.js)
  }
  // 서류철: 성향 검사 철이 화면 아래에서 올라온다(personality.css folder-rise). 헤더 · 서비스 소개 버튼으로 가면 연출 없이 바로 간다
  const ACTIONS = { news: lift, mug: sip, laptop: openLaptop, folder: () => navigate('test', { from: 'desk' }) };

  // 기준 좌표계(1672px)를 화면 너비에 맞춘다
  const fit = () => world.style.setProperty('--desk-scale', String(scene.clientWidth / SCENE_WIDTH));
  const observer = new ResizeObserver(() => {
    if (!root.isConnected) { observer.disconnect(); return; }
    fit();
  });
  observer.observe(scene);

  // ---- 메뉴 카드 (좁은 화면) ----
  const mobile = element('nav', 'desk-menu');
  mobile.setAttribute('aria-label', '메인 메뉴');
  const mobileHead = element('div', 'desk-menu-head');
  mobileHead.append(element('p', 'desk-menu-title', '다 알고 나서도, 같은 판단일까?'),
    element('p', '', '뉴스만 본 나와 모든 사실을 본 나. 하나를 골라 보세요.'));
  const mobileList = element('div', 'desk-menu-list');
  const mobileCounts = {};
  const mobileNews = {};
  ['news', 'folder', 'mug', 'laptop'].forEach((key) => {
    const { label, route, mobileText } = objects[key];
    const item = element('button', `desk-menu-item item-${key}${route ? '' : ' is-soon'}`);
    item.type = 'button';
    const text = span('', route ? mobileText : SOON);
    if (key === 'news') {
      // 모바일에서도 책상 위 신문 한 장: 작은 제호 + 머리기사 제목 + 사진(사건 목록으로 채운다)
      item.classList.add('is-paper');
      const brandRow = span('mn-brand');
      const wordmark = element('b', '', '');
      wordmark.append('내', span('law', 'Law'), '남불');
      brandRow.append(wordmark, span('mn-date', todayLabel()));
      mobileNews.headline = span('mn-headline', '오늘의 지면을 펼쳐 보세요');
      mobileNews.photo = element('img', 'mn-photo');
      mobileNews.photo.alt = '';
      mobileNews.photo.width = 84; // 크기 힌트(늦게 떠도 카드가 밀리지 않게)
      mobileNews.photo.height = 84;
      mobileNews.photo.src = fallbackImages.MURDER;
      const body = span('mn-body');
      body.append(mobileNews.headline, mobileNews.photo);
      item.append(brandRow, body, element('strong', '', label), text);
    } else if (key === 'mug') {
      // 모바일에서도 위에서 본 커피잔 하나. 누르면 같은 연출로 들어간다
      const cup = span('mug-art');
      cup.setAttribute('aria-hidden', 'true');
      cup.append(span('mug-coffee'));
      item.append(cup, element('strong', '', label), text);
    } else item.append(element('strong', '', label), text);
    if (key === 'mug') item.addEventListener('click', sip);
    else if (route) item.addEventListener('click', () => navigate(route, key === 'news' || key === 'folder' ? { from: 'desk' } : {}));
    else item.setAttribute('aria-disabled', 'true');
    mobileCounts[key] = text;
    mobileList.append(item);
  });
  mobile.append(mobileHead, mobileList);

  // ---- 서비스 소개 ----
  // 책상 밖 바닥에 펼쳐 둔 신문 한 장(FE-50). 왼쪽 "뉴스만 본 나"에서 오른쪽 "모든 사실을 본 나"까지 한 줄로 잇고,
  // 줄 위 두 지점이 되돌릴 수 없는 판단이다. 첫인상 전까지는 아는 것이 적어 점선, 그 뒤는 실선이다.
  // 아래 단은 세 판결을 같은 모양으로 나란히 둔다
  const heading = element('h2', '', '이렇게 체험해요');
  heading.id = 'feature-heading';
  const track = element('ol', 'feature-track');
  TRACK.forEach(({ pole, title, text }) => {
    const stop = element('li', pole ? 'track-pole' : 'track-stop');
    const mark = span('track-mark');
    mark.setAttribute('aria-hidden', 'true');
    if (pole) stop.append(mark, element('strong', '', pole));
    else stop.append(mark, element('h3', '', title), element('p', '', text));
    track.append(stop);
  });
  const verdicts = element('div', 'feature-verdicts');
  verdicts.setAttribute('aria-labelledby', 'verdicts-heading');
  const verdictsHeading = element('h3', '', '세 판결을 나란히');
  verdictsHeading.id = 'verdicts-heading';
  const verdictList = element('ul', 'verdict-list');
  verdictList.append(...VERDICTS.map((name) => element('li', '', name)));
  verdicts.append(verdictsHeading, verdictList, element('p', 'verdict-note', '실제 판결은 맨 마지막에 공개돼요.'));
  const introActions = element('div', 'feature-actions');
  introActions.append(
    button('사건 목록 보기', () => navigate('list'), 'primary-button'),
    button('판결 성향 테스트부터', () => navigate('test'), 'secondary-button'),
  );
  const sheet = element('div', 'feature-sheet');
  sheet.append(heading, track, verdicts, introActions);
  intro.append(sheet);

  root.append(scene, mobile, intro, renderSiteFooter()); // 공통 헤더는 라우터가 붙인다(FE-56)
  container.replaceChildren(root);
  fit();
  if (section === 'intro') showIntro();

  // 8막에서 왔으면(from: 'paper') 넘겨받은 지면이 화면을 덮은 채로 시작한다.
  // 지면이 페이드아웃되며 책상이 드러난다.
  // 좁은 화면은 메뉴의 신문 카드로 같은 연출. 새로고침 · 뒤로 가기로는 되풀이하지 않고, 움직임 줄이기면 연출 없이 바로 책상이다
  if (from === 'paper') {
    history.replaceState({ ...history.state, extras: {} }, '');
    const handed = takePaper();
    if (handed && !reducedMotion()) turnOver(handed);
    else handed?.layer.remove();
  }
  // 넘겨받은 지면은 제자리에서 줄어들며(줌아웃) 페이드아웃하고, 아래의 책상이 드러난다
  async function turnOver({ layer }) {
    layer.style.transformOrigin = '50% 50%';
    await layer.animate([{ opacity: 1, transform: 'scale(1)' }, { opacity: 0, transform: 'scale(.82)' }], { duration: FADE_MS, easing: 'cubic-bezier(.4, 0, .2, 1)', fill: 'forwards' }).finished.catch(() => {});
    layer.remove();
  }

  // ---- 신문 지면 채우기 (사건 목록 API 1). 실패해도 책상은 그대로 쓴다 ----
  // 한 번 받은 목록은 모듈에 두고 다시 올 때 처음부터 채운다(8막 "메인으로" 등에서 책상이 드러날 때 기본 사진이 사건 사진으로 튀지 않게).
  // 새 응답이 같으면(JSON 비교) 다시 그리지 않는다
  async function fillNews() {
    try {
      if (newsCache) paintNews(newsCache);
      const fresh = await api.getCases();
      if (!root.isConnected) return;
      if (newsCache && JSON.stringify(newsCache) === JSON.stringify(fresh)) return;
      newsCache = fresh;
      paintNews(fresh);
    } catch (error) {
      console.warn('메인 신문 지면을 채우지 못했어요:', error);
    }
  }
  function paintNews({ summary, cases }) {
    if (!cases?.length) return;
    const ordered = [...cases].sort((a, b) => b.participantCount - a.participantCount);
    const [top, ...others] = ordered;
    banner.textContent = top.headline || top.title;
    deck.textContent = top.deck || top.shortIntro;
    kicker.textContent = `머리기사 · ${top.crimeCategoryLabel}`;
    earCount.textContent = `법정에 오른 사건 ${summary.total}건`;
    tabCount.textContent = `총 ${summary.total}건`;
    lead.textContent = top.article?.[0] || top.shortIntro;
    photo.src = casePhoto(top);
    recs.replaceChildren(...others.slice(0, 2).map((item) => {
      const node = span('news-rec');
      // S-02 추천 사건처럼 사진(16:9) → 제목 → 기사. 기사는 문단을 이어 붙여 정해 둔 줄 수(landing.css)만큼 채운다
      const picture = element('img', 'news-rec-photo');
      picture.alt = '';
      picture.width = 16; // 비율 힌트. 크기는 CSS가 정한다
      picture.height = 9;
      picture.src = casePhoto(item);
      node.append(picture, element('b', '', item.headline || item.title), span('', item.article?.join(' ') || item.shortIntro));
      return node;
    }));
    mobileCounts.news.textContent = `오늘의 사건 ${summary.total}건`;
    mobileNews.headline.textContent = top.headline || top.title;
    mobileNews.photo.src = casePhoto(top);
  }
  if (api?.getCases) fillNews();
}
