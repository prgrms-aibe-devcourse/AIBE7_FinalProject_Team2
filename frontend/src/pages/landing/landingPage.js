import { button, element, mockTag, renderSiteFooter } from '../../components/siteLayout.js';
import { casePhoto, commonCategoryOf, fallbackImages, todayLabel, useCommonPhotoOnError } from '../../utils/newspaper.js';
import { holdPaper, takePaper } from '../../utils/paperHandoff.js';
import { restingStyle, tailPose } from '../../utils/tailSpread.js';
import { flyCoffee } from '../../utils/coffeeDive.js';
import { bootLaptop } from '../../utils/laptopBoot.js';
import { reducedMotion } from '../../utils/motion.js';
import { isAdmin } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';
import { peelOff } from '../../utils/peelOff.js';
import { openFeedbackDialog } from './feedbackDialog.js';

// S-01 메인 — 판사의 책상 (FE-18, 시안 "B안 일러스트 · C안 구도", 논의기록 2026-10-12 S01 메인 책상 시안 결정)
// 책상 위 물건이 곧 메뉴다: 신문 = 사건 목록(S-02), 서류철 = 판결 성향 테스트(S-15), 커피잔 = 마이페이지(S-17), 노트북 = 커뮤니티.
// 펜 = 의견 보내기(FE-50 자리 · FE-62 창 틀). 휴대폰을 없애고 그 자리에 두었다.
// 그림은 CSS · 인라인 SVG로만 그린다(이미지 파일 없음). 색은 style.css의 --color-desk-* 역할 토큰만 쓴다.
// 2D 평면 일러스트라 물건은 평소에 들리거나 기울지 않는다(팀 결정). 720px 이하에서는 책상 대신 메뉴 카드를 보여 준다.
// 흐름 목업 ①(FE-32): 신문을 누르면 신문만 책상에서 들려 올라오고(그림자가 길어진다), 이어서 신문 지면(S-02)이 화면 아래에서 올라온다(FE-50,
// 책상은 그 뒤에서 흐려진다 — caseListPage.js). 신문 들기는 이 연출에만 쓰는 예외다.
// 커피잔을 누르면(FE-44) 커다란 커피잔이 우상단에서 빠르게 날아와 서는 동안 책상이 마이페이지로 바뀌고, 다 바뀌면 잔이 오른쪽으로 빠져나간다(utils/coffeeDive.js).
// 노트북을 누르면(FE-47) 화면이 눈을 감듯 어두워졌다가 눈을 뜨듯 열리며 커뮤니티가 나온다 — 컴퓨터 부팅(utils/laptopBoot.js).
// 책상에는 내로(신문 위쪽, 포스트잇에 가려 있음)가 볼을 괴고 엎드려 있다(장식). 남불은 아래 집무실 안내문 종이 밑에 숨어 있다(FE-87).

// 책상 그림의 기준 좌표계(1672 × 941). 첫 화면(헤더 아래)에 책상만 꽉 차게 보이도록(FE-87) 화면 안에 들어가게 통째로 줄이고(--desk-scale),
// 남는 쪽으로 책상을 늘린다. 소품 배치 순서(왼쪽 노트북 · 엽서 · 펜, 가운데 신문, 오른쪽 커피잔 · 서류철)는 어떤 크기에서도 그대로다.
// - 화면이 기준보다 넓으면(--dx = 한쪽에 더해진 기준 px): 패드 둘레 · 패드 · 물건 사이가 고르게 넓어진다(왼쪽 0.5 · 가운데 1 · 오른쪽 1.5배).
// - 화면이 기준보다 높으면: 늘어난 높이를 먼저 신문이 흡수한다. 신문만 아래로 길어지고(--ng, 기본 높이의 1.2배까지) 다른 물건은 비율을 바꾸지 않는다.
//   그래도 남는 높이(--r)는 나무 띠보다 패드 안에 더 준다: 패드 위 · 아래 나무 0.1씩, 패드 안 0.8. 위 소품(노트북 · 커피잔)은 패드 윗변, 아래 소품(엽서 · 펜)은
//   패드 아랫변에 원래처럼 붙고, 신문(가운데) · 서류철 위아래가 0.4씩 넓어진다.
//   남는 높이가 클수록(--t, 0 ~ 1) 소품을 모양 그대로 큼직하게 키우고 신문 밖 소품은 조금 더 기울인다(landing.css).
// - 화면 폭이 좁아 책상이 작게 줄어들수록(--u, 0 ~ 1) 작은 소품(엽서 · 펜 · 서류철 · 커피잔 · 포스트잇 · 내로)을 더 키운다.
//   작은 소품은 둘 중 큰 쪽(--k = max(t, u))을 따른다. 신문 · 노트북은 --t만 따른다
const SCENE_WIDTH = 1672;
const SCENE_HEIGHT = 941;
const NEWS_GROW_MAX = 142; // 신문 높이 712 → 854(기본의 1.2배까지)
const ROOMY = 800; // 신문이 흡수하고 남은 높이가 이만큼(기준 px)이면 소품을 가장 크게 · 가장 많이 기울인다(--t = 1)
const SMALL_FROM = .85; // 책상 배율이 이보다 작아지면(약 1420px 폭 아래) 작은 소품을 키우기 시작해
const SMALL_SPAN = .35; // 배율 .5(약 840px 폭)에서 가장 크게(--u = 1)
const NEWS_LINE = 15; // 신문 기사 한 줄 높이(기준 px, 10px × 1.5). 길어진 신문은 사진 · 첫 문단 · 아래 기사가 나눠 채운다(landing.css)

// 물건(메뉴) 목록. route가 없으면 준비 중이라 누를 수 없다(낭독기에는 "준비 중"으로 읽힌다).
// 화면이 생기면 route에 화면 이름(routes.js의 name)만 넣으면 된다.
// 책상 위 물건에는 이름표(메모지)를 붙이지 않는다(FE-50). label은 낭독기 이름과 좁은 화면 메뉴 카드에 쓴다
const OBJECTS = {
  folder: { label: '판결 성향 테스트', route: 'test', mobileText: '나의 판결 성향 알아보기' },
  news: { label: '실제 사건 판결 체험', route: 'list', mobileText: '사건 목록' },
  mug: { label: '마이페이지', route: 'me', mobileText: '내 판결 기록' },
  laptop: { label: '커뮤니티', route: 'community', mobileText: '다른 사람의 생각' },
  postcard: { label: '의견 보내기', route: 'feedback' }, // FE-62: 펜 옆 엽서도 같은 창을 연다(누르는 범위만 넓힌다, 낭독기 · 키보드는 펜 하나로)
  pen: { label: '의견 보내기', route: 'feedback', mobileText: '서비스에 바라는 점' }, // FE-62: 펜 = 의견 보내기 창(화면 이동 없이 창이 뜬다, route는 켜짐 표시)
};
const ADMIN_MUG = { label: '관리자 페이지', route: homeRoute(), mobileText: '사건 검수 · 운영' }; // 관리자로 로그인하면 마이페이지 자리 (FE-53)
const SOON = '곧 열려요';
// 책상 위 내로(기준 좌표계 px, 오른쪽 끝에서 잰다 — FE-50에서 소품 배치를 좌우로 뒤집었다). 남불은 FE-87에서 아래 집무실 안내문 밑으로 옮겼다
const CHEEKS = [
  { who: 'naero', look: 'idle-sorry', height: 170, right: 'calc(644px + var(--dx, 0px) - var(--t, 0) * 37px)', top: 'calc(2px + var(--r, 0px) * .1)' }, // 패드 윗변 위 나무에(네이비 패드와 겹치지 않게)
];
let newsCache = null; // 사건 목록(API 1) 마지막 응답
const FOLD_MS = 600; // 러그 귀퉁이를 접고 펴는 시간
const FOLDED = 'rotate3d(1, -1, 0, 180deg)'; // 접힌 귀퉁이(landing.css .is-folded와 같다)
const LIFT_MS = 380; // 신문을 집어 드는 시간(landing.css .is-held와 같게). 그 뒤 지면이 아래에서 올라온다
// 6막에서 "메인으로" 올 때: 보던 지면이 줌아웃(제자리에서 .82배)과 페이드아웃을 함께 하며 책상이 드러난다
const FADE_MS = 800;
const EXIT_DELAY_MS = 1000; // 맨 아래 신문이 화면에 들어온 뒤 밀려 나오기까지

// 서비스 소개(FE-50 → FE-87 집무실 바닥): 러그 = 체험에서 내가 하는 일, 스툴 = 끝에 얻는 것(세 판결 비교 미리보기).
// 러그 위 서류는 체험에서 실제로 만나는 물건의 축소판이다(신문 스크랩 · 첫인상 엽서 · 판단 요소 파일철 · 판결문). 설명은 한 줄로 줄이고 물건이 말하게 한다.
// 양 끝은 상태(pole: 뉴스만 본 나 · 모든 사실을 본 나), 가운데 두 장은 되돌릴 수 없는 판단이다
const TRACK = [
  { pole: true, kind: 'clip', title: '뉴스만 본 나', text: '제목과 사진 한 장으로 사건을 처음 만나요.' },
  { kind: 'postcard', title: '첫인상', text: '고른 뒤에는 바꿀 수 없어요.' },
  { kind: 'folder', title: '내 판결', text: '기록을 다 읽고 판단 요소마다 무게를 정해요.' },
  { pole: true, kind: 'sheet', title: '모든 사실을 본 나', text: '형량을 적고 확정하면 바꿀 수 없어요.' },
];
// 내 판결 파일철의 판단 요소 예시(특정 사건이 아닌 일반 항목)와 고른 칸(0 무겁게 · 1 해당 없음 · 2 가볍게)
const FACTOR_SCALE = ['무겁게', '해당 없음', '가볍게'];
const FACTOR_SAMPLES = [['범행 경위', 0], ['피해 회복', 1], ['반성 여부', 2]];
// 러그 위 판결 두 장(FE-87, 스툴 대신): AI 판결은 러그 가운데, 실제 판결은 왼쪽 아래. 내 판결은 러그의 판결문(모든 사실을 본 나)이 맡는다.
// 막대는 0 ~ 10년, at = 점 자리(%). AI 판결 수치는 실제 데이터가 아니라 목데이터로 밝히고, 실제 판결은 마지막 전까지 가린다(PRODUCT.md)
const VERDICTS = [
  { key: 'ai', name: 'AI 판결', title: '같은 기록, AI 판사', at: 62, value: '징역 6년', mock: true, note: '법률 자문 아님' },
  { key: 'court', name: '실제 판결', title: '재판부의 선고', at: 40, value: '징역 ■년', sealed: '맨 마지막에 공개' },
];

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
  folderArt.lastChild.textContent = '3분 성향 테스트';

  // 신문 — 집어 들면 펼쳐지는 S-02 지면을 줄여 놓은 모습(FE-32): 정보 줄 → [귀 | 제호 | 귀] → 날짜 띠 → 유형 탭 줄 →
  // 배너 제목 → [사진 | 머리기사 단] → 추천 2건(지면 끝까지, FE-50). 사건 목록의 머리기사로 채운다(불러오기 전 · 실패 시 기본 문구)
  const news = span('news-art');
  news.setAttribute('aria-hidden', 'true');
  // 바람에 들리는 오른쪽 아래 모서리의 뒷면(FE-99, landing.css .news-curl). 맨 앞 지면에만, 장식이다
  const curlBack = () => {
    const back = deco('news-curl');
    back.append(deco('news-curl-face'));
    return back;
  };
  const newsSheets = span('news-sheets');
  newsSheets.append(span(''), span(''), span('')); // 밑에 깔린 종이 세 장(FE-99: 맨 앞 장 모서리가 들릴 때 밑에 겹이 보이게)
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
  ['전체', '살인', '사기', '상해', '강도', '절도'].forEach((label, i) => tabs.append(span(i === 0 ? 'on' : '', label)));
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

  const cheeks = CHEEKS.map(({ who, look, height, right, top }) => {
    const image = element('img', `desk-cheek cheek-${who}`);
    image.src = `/images/characters/cheek/${who}_${look}.webp`;
    image.alt = '';
    image.setAttribute('aria-hidden', 'true');
    Object.assign(image.style, { right, top, height: `${height}px` });
    image.addEventListener('error', () => image.remove(), { once: true });
    return image;
  });

  // 내로를 가린 포스트잇(FE-50, 놀이). 누르면 떼어져 날아가고 내로가 드러난다. 화면을 다시 그리면 다시 붙어 있다.
  // 떼는 동안 포커스를 잃지 않게 바로 아래 신문으로 옮긴다
  const newsButton = deskObject('news', newsSheets, news, curlBack());
  const rescue = element('button', 'desk-rescue');
  rescue.type = 'button';
  rescue.append(span('', '포스트잇을 제거해'), span('', '귀여운 고양이를'), span('', '구해주세요'));
  rescue.addEventListener('click', () => peelOff(rescue, { from: 'rotate(4deg)', to: 'translate(70px, -150px) rotate(32deg)', focusTarget: () => newsButton }));

  // 엽서(FE-50 장식 · FE-62 누를 수 있음). 펜 옆 빈자리를 채운다. 우표는 판결 체험의 첫인상 엽서(paperStage.css)와 같다
  const postcardArt = deco('desk-postcard');
  postcardArt.append(span('postcard-stamp', '내Law\n남불'), span('postcard-title', '엽 서'), span('postcard-lines'));
  const postcard = deskObject('postcard', postcardArt);
  postcard.tabIndex = -1; // 펜과 같은 동작이라 Tab · 낭독기에는 펜 하나만 남긴다
  postcard.setAttribute('aria-hidden', 'true');

  // Tab 순서는 핵심 물건부터(신문 → 서류철 → 노트북 → 커피잔 → 펜 → 포스트잇, FE-99). 그리는 순서는 DOM이 아니라 landing.css의 z-index가 정한다
  world.append(
    deco('desk-wood'), deco('desk-pad'),
    newsButton,
    deskObject('folder', folderArt),
    deskObject('laptop', laptop),
    deskObject('mug', mug),
    deskObject('pen', span('pen-art')),
    postcard,
    rescue,
    ...cheeks,
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
  // 서류철: 성향 테스트 철이 화면 아래에서 올라온다(personality.css folder-rise). 헤더 · 서비스 소개 버튼으로 가면 연출 없이 바로 간다
  const ACTIONS = { news: lift, mug: sip, laptop: openLaptop, folder: () => navigate('test', { from: 'desk' }), pen: openFeedbackDialog, postcard: openFeedbackDialog };

  // 기준 좌표계가 장면(첫 화면) 안에 들어가게 줄이고, 남는 쪽으로 책상을 늘린다(위 SCENE_WIDTH 주석)
  const fit = () => {
    const width = scene.clientWidth;
    const height = scene.clientHeight;
    if (!width || !height) return;
    const scale = Math.min(width / SCENE_WIDTH, height / SCENE_HEIGHT);
    const extra = height / scale - SCENE_HEIGHT; // 늘어난 높이(기준 px)
    const newsGrow = Math.min(extra * .75, NEWS_GROW_MAX);
    const set = (name, px) => world.style.setProperty(name, `${px}px`);
    world.style.setProperty('--desk-scale', String(scale));
    root.style.setProperty('--desk-px', String(scale)); // 아래 집무실 의자가 책상 소품과 같은 배율을 쓰도록
    set('--dx', (width / scale - SCENE_WIDTH) / 2);
    set('--ng', newsGrow);
    set('--r', extra - newsGrow);
    const roomy = Math.min(1, Math.max(0, extra - newsGrow) / ROOMY);
    const small = Math.min(1, Math.max(0, (SMALL_FROM - scale) / SMALL_SPAN));
    world.style.setProperty('--t', String(roomy));
    world.style.setProperty('--k', String(Math.max(roomy, small)));
    // 길어진 신문: 머리기사 단(사진 · 첫 문단)에 0.45, 아래 기사 사진에 0.2, 아래 기사 글에 0.35
    world.style.setProperty('--lead-lines', String(9 + Math.floor((newsGrow * .45) / NEWS_LINE)));
    world.style.setProperty('--rec-lines', String(4 + Math.floor((newsGrow * .35) / NEWS_LINE)));
  };
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
  ['news', 'folder', 'mug', 'laptop', 'pen'].forEach((key) => {
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
    else if (key === 'pen') item.addEventListener('click', openFeedbackDialog);
    else if (route) item.addEventListener('click', () => navigate(route, key === 'news' || key === 'folder' ? { from: 'desk' } : {}));
    else item.setAttribute('aria-disabled', 'true');
    mobileCounts[key] = text;
    mobileList.append(item);
  });
  // 메뉴 카드 아래 빈 곳: 책상 위 내로가 카드 밑에서 고개만 내민다(장식)
  const peek = element('div', 'desk-menu-peek');
  peek.setAttribute('aria-hidden', 'true');
  const peekCat = element('img', 'desk-menu-naero');
  peekCat.src = '/images/characters/cheek/naero_idle-sorry.webp';
  peekCat.alt = '';
  peekCat.addEventListener('error', () => peek.remove(), { once: true });
  peek.append(peekCat);
  mobile.append(mobileHead, mobileList, peek);

  // ---- 서비스 소개: 판사의 집무실 바닥 (FE-87) ----
  // 책상(위)에서 카메라가 그대로 아래로 내려가며 방바닥을 내려다본다. 2D 평면 탑뷰라 원근 없이 그린다(문은 평면도 기호: 문짝 + 열리는 원호).
  // 위에서부터: 책상 밑으로 밀어 넣은 판사 의자(등받이만 보임) · 책상이 바닥에 드리운 그림자 → 안내문 → 러그 위 서류 네 장(흐름, 왼쪽 위에서 오른쪽 아래로 흩어 놓음) (이어서 AI 판결 · 실제 판결 두 장) → 아래 벽의 열린 문(사건 목록)
  const room = element('div', 'room');
  // 안내문 종이(놀이): 왼쪽 위 귀퉁이가 늘 들썩이고, 누르면 종이가 러그 쪽(거의 아래, 살짝 오른쪽)으로 러그 위를 지나 끌려가며 치워지고 밑에 숨어 있던 남불이 드러난다.
  // 화면 제목(h2)은 종이와 따로 둔다(종이를 치워도 구역 이름은 남는다). 종이 위 글자는 장식이다
  const heading = element('h2', 'visually-hidden', '이렇게 체험해요');
  heading.id = 'feature-heading';
  const spot = element('div', 'room-note-spot');
  const nambul = element('img', 'room-nambul');
  nambul.src = '/images/characters/cheek/nambul_idle-thinking.webp';
  nambul.alt = '';
  nambul.setAttribute('aria-hidden', 'true');
  nambul.addEventListener('error', () => nambul.remove(), { once: true });
  heading.tabIndex = -1; // 안내문을 치운 뒤 키보드 포커스를 받는 자리(구역 이름을 다시 읽어 준다)
  const note = button('', () => {
    note.classList.add('is-peeling');
    peelOff(note, { from: 'rotate(-4deg)', to: 'translate(70px, 340px) rotate(8deg)', focusTarget: () => heading });
  }, 'room-note');
  note.setAttribute('aria-label', '안내문 종이 치우기');
  const paper = span('room-note-paper');
  paper.setAttribute('aria-hidden', 'true');
  paper.append(span('room-note-title', '이렇게 체험해요')); // 설명은 러그 위 서류가 대신한다
  note.append(paper);
  spot.append(nambul, note);
  const head = element('div', 'room-head');
  const chair = deco('room-chair');
  chair.append(span('chair-back'), span('chair-arm'), span('chair-arm'), span('chair-seat'));
  head.append(heading, spot);

  // 러그 위 서류: 순서가 곧 흐름이다(ol). 표시는 빈 원(모르는 나) → 마름모(되돌릴 수 없는 판단) → 꽉 찬 원(다 아는 나)
  const rug = element('ol', 'room-rug');
  const rugClip = {}; // 신문 스크랩은 책상 신문과 같은 머리기사로 채운다(paintNews)
  const box = (on) => span(`paper-box${on ? ' is-on' : ''}`);
  const PAPER_BODY = {
    clip: () => {
      rugClip.kicker = span('clip-kicker', '머리기사');
      rugClip.head = span('clip-head', '오늘의 머리기사');
      rugClip.photo = element('img', 'clip-photo');
      rugClip.photo.alt = '';
      rugClip.photo.src = fallbackImages.MURDER;
      return [rugClip.kicker, rugClip.head, rugClip.photo];
    },
    postcard: () => {
      const choices = span('paper-choices');
      choices.append(span('', ''), span('', ''));
      choices.children[0].append(box(true), '징역형');
      choices.children[1].append(box(false), '벌금형');
      return [span('paper-q', '어떤 형이 맞을까?'), choices, deco('paper-stamp')];
    },
    folder: () => {
      const table = span('factor-table');
      const head = span('factor-row is-head');
      head.append(span('', ''), ...FACTOR_SCALE.map((label) => span('', label)));
      table.append(head, ...FACTOR_SAMPLES.map(([name, pick]) => {
        const row = span('factor-row');
        row.append(span('factor-name', name), ...FACTOR_SCALE.map((_, i) => box(i === pick)));
        return row;
      }));
      return [table];
    },
    sheet: () => {
      const order = span('sheet-order');
      order.append('피고인을 징역 ', span('sheet-hand', '5'), '년에 처한다.');
      return [span('sheet-title', '판 결 문'), span('sheet-label', '주문'), order, deco('sheet-seal')];
    },
  };
  TRACK.forEach(({ pole, kind, title, text }) => {
    const paper = element('li', `room-paper paper-${kind}${pole ? ' track-pole' : ' track-stop'}`);
    const step = span('paper-step');
    step.append(deco('track-mark'), element(pole ? 'strong' : 'h3', 'paper-title', title));
    paper.append(step, ...PAPER_BODY[kind](), element('p', 'paper-cap', text));
    rug.append(paper);
  });

  // 러그 위 판결 두 장: 러그 위 서류와 같은 규칙(종이 · 기울기 · 겹침)으로 이어 놓는다
  VERDICTS.forEach(({ key, name, title, at, value, mock, note, sealed }) => {
    const paper = element('li', `room-paper paper-verdict is-${key}${sealed ? ' is-sealed' : ''}`);
    const bar = span(`verdict-bar is-${key}`); // 0 ~ 10년 막대, 실제 판결은 흐리게 가린다
    bar.setAttribute('aria-hidden', 'true');
    const dot = span('bar-dot');
    dot.style.left = `${at}%`;
    bar.append(dot);
    const ticks = span('bar-ticks');
    ticks.setAttribute('aria-hidden', 'true');
    ticks.append(span('', '0년'), span('', '5년'), span('', '10년'));
    const amount = element('p', 'verdict-value', value);
    if (mock) amount.append(' ', mockTag());
    if (note) amount.append(' ', span('verdict-note', note));
    const reading = span(`verdict-reading${sealed ? ' is-covered' : ''}`);
    reading.append(bar, ticks, amount);
    if (sealed) reading.setAttribute('aria-hidden', 'true'); // 가린 수치는 읽지 않는다
    paper.append(span(`verdict-who is-${key}`, name), element('h3', 'paper-title', title), reading);
    if (sealed) paper.append(element('p', 'verdict-seal', sealed));
    rug.append(paper);
  });

  // 러그 오른쪽 아래 귀퉁이 밑에 성향 테스트 서류철이 깔려 있다(바깥으로 조금 삐져나옴). 귀퉁이는 마우스를 올리면 들썩이고,
  // 누르면 접혀 올라가 서류철이 드러난다(다시 누르면 덮는다). 서류철을 누르면 성향 테스트
  const rugWrap = element('div', 'room-rug-wrap');
  // 접기 전에는 삐져나온 서류철도 귀퉁이를 접는 자리다(누르면 접혀 드러남). 접힌 뒤에 누르면 성향 테스트
  const stash = button('', () => (rugWrap.classList.contains('is-folded') ? navigate('test') : flap.click()), 'rug-folder');
  stash.setAttribute('aria-label', '판결 성향 테스트');
  // 접기 전에는 귀퉁이 버튼(flap)이 같은 동작을 맡으므로 Tab · 낭독기에서 빼고, 접힌 뒤에 다시 보인다(책상 엽서와 같은 방식)
  const exposeStash = (open) => {
    stash.tabIndex = open ? 0 : -1;
    if (open) stash.removeAttribute('aria-hidden'); else stash.setAttribute('aria-hidden', 'true');
  };
  exposeStash(false);
  stash.append(folderArt.cloneNode(true));
  const flap = button('', () => {
    const from = getComputedStyle(leaf).transform; // 들썩이던 중이면 그 각도에서 이어 접는다
    const folded = rugWrap.classList.toggle('is-folded');
    flap.setAttribute('aria-expanded', String(folded));
    exposeStash(folded);
    if (reducedMotion()) return;
    leaf.animate([{ transform: from }, { transform: folded ? FOLDED : 'none' }], { duration: FOLD_MS, easing: 'cubic-bezier(.65, 0, .35, 1)' });
  }, 'rug-flap');
  flap.setAttribute('aria-label', '러그 귀퉁이 들추기');
  flap.setAttribute('aria-expanded', 'false');
  const leaf = span('rug-flap-leaf'); // 접히는 귀퉁이 한 장(앞면 = 러그 무늬, 뒷면 = 러그 밑면)
  leaf.append(span('rug-flap-front'), span('rug-flap-back'));
  flap.append(span('rug-flap-hit'), leaf); // 누르는 자리는 접히지 않고 귀퉁이 자리에 남는다(들썩이는 동안 마우스가 빠지지 않게)
  rugWrap.append(stash, rug, flap);

  // 맨 아래: 아래 벽 밑으로 밀려 들어간 신문 한 부, 윗부분(전체의 20%)만 보인다. 책상 위 신문과 같은 지면이고 누르면 사건 목록
  const exit = element('div', 'room-exit');
  // 누르면 사건 목록으로 간다. 지금 보이는 끝자락을 맨 위 장식 층에 같은 자리 · 같은 기울기 그대로 본떠 두고 넘겨준다.
  // 그 한 장이 곧바로 위로 잡아당겨지며 커지고, 들어 올린 뒤 그 밑에서 함께 움직이던 사건 목록 지면으로 넘어가 화면을 채운다(utils/tailSpread.js)
  const tail = button('', () => {
    if (reducedMotion()) { navigate('list'); return; }
    const floor = exit.getBoundingClientRect().bottom - parseFloat(getComputedStyle(exit).getPropertyValue('--wall')); // 신문이 벽에 들어가는 선
    const rect = tailPose(tailSheet, tail, floor);
    const scope = element('div', 'landing-page'); // 본뜬 지면에 메인 화면 스타일이 그대로 걸리게(자리는 차지하지 않는다)
    scope.style.display = 'contents';
    const layer = element('div', 'paper-hand-layer');
    layer.setAttribute('aria-hidden', 'true');
    layer.inert = true;
    const face = element('div');
    Object.assign(face.style, { position: 'absolute', left: 0, top: 0, width: `${rect.width}px`, height: `${tailSheet.getBoundingClientRect().height}px`, transformOrigin: '0 0', ...restingStyle(rect) });
    const copy = tailSheet.cloneNode(true);
    copy.style.setProperty('--news-zoom', getComputedStyle(tail).getPropertyValue('--news-zoom'));
    face.append(copy);
    layer.append(face);
    scope.append(layer);
    document.body.append(scope);
    tailSheet.style.visibility = 'hidden';
    holdPaper({ layer: scope, card: face, rect });
    // 사건 목록은 이 화면을 뒤에 남기는데(.paper-ghost, 헤더 없이 맨 위부터 그리는 고정 층) 라우터가 스크롤을 맨 위로 올린다.
    // 넘어간 뒤 화면이 지금 보이던 자리 그대로 남도록 옮겨진 만큼 되돌린다
    const seen = root.getBoundingClientRect().top;
    navigate('list', { from: 'tail' });
    root.style.translate = `0 ${seen - root.getBoundingClientRect().top}px`;
  }, 'room-news');
  tail.setAttribute('aria-label', '사건 목록 보기');
  const tailSheet = span('room-news-sheet');
  tailSheet.setAttribute('aria-hidden', 'true');
  tailSheet.append(news.cloneNode(true)); // 책상 위 신문과 같은 지면. 머리기사가 채워지면 다시 본뜬다(paintNews)
  tail.append(tailSheet);
  exit.append(tail);
  // 끝자락은 처음엔 벽(문) 밑에 숨어 있다가, 스크롤이 닿고 1초 뒤 슥 밀려 나온다(FE-99). 움직임 줄이기 · 키보드 포커스면 바로 보인다
  const slideOut = () => { exitObserver?.disconnect(); tail.classList.add('is-out'); };
  let exitObserver = null;
  if (reducedMotion() || typeof IntersectionObserver === 'undefined') slideOut();
  else {
    exitObserver = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      exitObserver.disconnect();
      setTimeout(() => { if (root.isConnected) slideOut(); }, EXIT_DELAY_MS);
    }, { rootMargin: '0px 0px -100px 0px' }); // 문 윗변이 화면 아래에서 100px 들어오면. 비율 조건은 문이 화면보다 커지면 영영 안 나온다
    exitObserver.observe(exit);
    tail.addEventListener('focus', slideOut, { once: true });
  }

  room.append(chair, deco('room-desk-shade'), head, rugWrap, exit); // 의자는 책상 밑으로 밀어 넣어 위쪽이 책상에 가린다
  intro.append(room);

  // 본문 landmark(FE-99): 책상 · 메뉴 카드 · 서비스 소개가 본문이다. 푸터는 본문 밖에 둔다
  const main = element('main', 'landing-main');
  main.append(scene, mobile, intro);
  root.append(main, renderSiteFooter({ navigate })); // 공통 헤더는 라우터가 붙인다(FE-56)
  container.replaceChildren(root);
  fit();
  if (section === 'intro') showIntro();

  // 6막에서 왔으면(from: 'paper') 넘겨받은 지면이 화면을 덮은 채로 시작한다.
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
  // 한 번 받은 목록은 모듈에 두고 다시 올 때 처음부터 채운다(6막 "메인으로" 등에서 책상이 드러날 때 기본 사진이 사건 사진으로 튀지 않게).
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
    rugClip.head.textContent = top.headline || top.title; // 아래 집무실 러그의 신문 스크랩도 같은 머리기사
    rugClip.kicker.textContent = `머리기사 · ${top.crimeCategoryLabel}`;
    useCommonPhotoOnError(rugClip.photo, Number(top.caseId) || 0, undefined, commonCategoryOf(top));
    rugClip.photo.src = casePhoto(top);
    deck.textContent = top.deck || top.shortIntro;
    kicker.textContent = `머리기사 · ${top.crimeCategoryLabel}`;
    earCount.textContent = `법정에 오른 사건 ${summary.total}건`;
    tabCount.textContent = `총 ${summary.total}건`;
    lead.textContent = top.article?.[0] || top.shortIntro;
    useCommonPhotoOnError(photo, Number(top.caseId) || 0, undefined, commonCategoryOf(top));
    photo.src = casePhoto(top);
    recs.replaceChildren(...others.slice(0, 2).map((item) => {
      const node = span('news-rec');
      // S-02 추천 사건처럼 사진(16:9) → 제목 → 기사. 기사는 문단을 이어 붙여 정해 둔 줄 수(landing.css)만큼 채운다
      const picture = element('img', 'news-rec-photo');
      picture.alt = '';
      picture.width = 16; // 비율 힌트. 크기는 CSS가 정한다
      picture.height = 9;
      useCommonPhotoOnError(picture, Number(item.caseId) || 0, undefined, commonCategoryOf(item));
      picture.src = casePhoto(item);
      node.append(picture, element('b', '', item.headline || item.title), span('', item.article?.join(' ') || item.shortIntro));
      return node;
    }));
    mobileCounts.news.textContent = `사건 ${summary.total}건`;
    mobileNews.headline.textContent = top.headline || top.title;
    useCommonPhotoOnError(mobileNews.photo, Number(top.caseId) || 0, undefined, commonCategoryOf(top));
    mobileNews.photo.src = casePhoto(top);
    tailSheet.replaceChildren(news.cloneNode(true)); // 맨 아래 끝자락도 같은 머리기사로(눌러서 펼친 사건 목록 지면과 이어지게)
  }
  if (api?.getCases) fillNews();
}
