import { button, element, renderSiteFooter, renderSiteHeader } from '../../components/siteLayout.js';
import { casePhoto, fallbackImages, todayLabel } from '../../utils/newspaper.js';

// S-01 메인 — 판사의 책상 (FE-18, 시안 "B안 일러스트 · C안 구도", 논의기록 2026-10-12 S01 메인 책상 시안 결정)
// 책상 위 물건이 곧 메뉴다: 신문 = 사건 목록(S-02), 서류철 = 판결 성향 테스트(S-15), 휴대폰 = 마이페이지(S-17), 노트북 = 커뮤니티.
// 그림은 CSS · 인라인 SVG로만 그린다(이미지 파일 없음). 색은 style.css의 --color-desk-* 역할 토큰만 쓴다.
// 2D 평면 일러스트라 물건은 평소에 들리거나 기울지 않는다(팀 결정). 720px 이하에서는 책상 대신 메뉴 카드를 보여 준다.
// 흐름 목업 ①(FE-32): 신문을 누르면 신문만 책상에서 들려 올라오고(그림자가 길어진다), 이어서 책상이 신문 쪽으로 다가가며 사라진 뒤
// 신문 지면(S-02)이 이어진다(신문을 집어 든다). 신문 들기는 이 연출에만 쓰는 예외다.
// 책상에는 내로(커피잔 옆) · 남불(휴대폰 아래)이 볼을 괴고 엎드려 있다(장식).

// 책상 그림의 기준 좌표계. 화면 너비에 맞춰 통째로 줄인다(--desk-scale)
const SCENE_WIDTH = 1672;

// 물건(메뉴) 목록. route가 없으면 "곧 열려요"로 표시하고 누를 수 없다.
// S-15 · S-17이 생기면 route에 화면 이름(routes.js의 name)만 넣으면 된다.
const OBJECTS = {
  folder: { label: '판결 성향 테스트', readyNote: '3분이면 끝 →', route: 'test', mobileText: '나의 판결 성향 알아보기' },
  news: { label: '실제 사건 판결 체험', readyNote: '오늘의 사건', route: 'list', mobileText: '오늘의 사건' },
  phone: { label: '마이페이지', readyNote: '내 판결 기록', route: 'me', mobileText: '내 판결 기록' },
  laptop: { label: '커뮤니티', readyNote: '', route: null, mobileText: '다른 사람의 생각' },
};
const SOON = '곧 열려요';
// 책상 위 내로 · 남불(기준 좌표계 px). shortcut: 원본 이미지 레포 PNG를 직접 읽는다, 웹 webp 변환(WBS 7.6.8) 때 public 경로로 옮긴다
const CUTS = 'https://raw.githubusercontent.com/peanutboi710/NaeLawNamBul_Images/main/character/cuts/';
const CHEEKS = [
  { who: 'naero', look: 'idle-sorry', left: 250, top: 2, height: 170 },
  { who: 'nambul', look: 'idle-thinking', left: 1300, top: 782, height: 158 },
];
const LIFT_MS = 1000; // 신문을 집어 드는 시간 = 신문 들림 .38초 + 책상 다가옴 .72초(landing.css .is-lifting · .is-held와 같게, 일부 겹침)
const PUT_DOWN_MS = 560; // 책상이 멀어지는 동안 신문은 들린 채 있다가 끝 무렵 내려앉는다(.desk-scene 전환 .72초)
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const features = [
  { title: '먼저 판단하기', text: '사건 정보를 보기 전에 내 생각을 먼저 고릅니다.' },
  { title: '직접 판결하기', text: '사건 기록과 법률 정보를 확인하고 판결을 내립니다.' },
  { title: '세 판결 비교', text: '내 판결, AI 판결, 실제 판결을 나란히 비교합니다.' },
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

  const headline = element('div', 'desk-headline');
  const h1 = element('h1', '', '다 알고 나서도, 같은 판단일까?');
  h1.id = 'desk-heading';
  headline.append(h1, element('p', '', '뉴스만 본 나와 모든 사실을 본 나. 책상 위 물건을 하나 집어 보세요.'));

  const deco = (className) => {
    const node = span(className);
    node.setAttribute('aria-hidden', 'true');
    return node;
  };

  // 메모지(포스트잇). 물건이 준비 중이면 "곧 열려요"
  const notes = {};
  function note(key) {
    const { label, readyNote, route } = OBJECTS[key];
    const node = span(`desk-note note-${key}${route ? '' : ' is-soon'}`);
    node.setAttribute('aria-hidden', 'true');
    const em = element('em', '', route ? readyNote : SOON);
    node.append(element('strong', '', label), em);
    notes[key] = em;
    return node;
  }

  // 물건 = 버튼. 준비 중이면 aria-disabled로 두고 누르지 않는다(포커스는 받아 "준비 중"을 읽게 한다)
  function deskObject(key, ...art) {
    const { label, route } = OBJECTS[key];
    const node = element('button', `desk-object obj-${key}${route ? '' : ' is-soon'}`);
    node.type = 'button';
    node.setAttribute('aria-label', route ? label : `${label} — 준비 중`);
    if (route) node.addEventListener('click', () => (key === 'news' ? lift(node) : navigate(route)));
    else node.setAttribute('aria-disabled', 'true');
    node.append(...art, note(key));
    return node;
  }

  // 서류철
  const folderArt = span('folder-art');
  folderArt.innerHTML = FOLDER_SVG;
  folderArt.append(deco('folder-label'));
  folderArt.lastChild.textContent = '판결 성향 검사지';

  // 신문 — 집어 들면 펼쳐지는 S-02 지면을 줄여 놓은 모습(FE-32): 정보 줄 → [귀 | 제호 | 귀] → 날짜 띠 → 유형 탭 줄 →
  // 배너 제목 → [사진 | 머리기사 단] → 추천 2건 → 다른 사건 제목 줄. 사건 목록의 머리기사로 채운다(불러오기 전 · 실패 시 기본 문구)
  const news = span('news-art');
  news.setAttribute('aria-hidden', 'true');
  const newsSheets = span('news-sheets');
  newsSheets.append(span(''), span(''));
  const info = span('news-info');
  info.append(span('', '판결 체험 신문'), span('', '← 처음으로 · 서비스 소개'));
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
  const index = span('news-index');
  news.append(info, mast, date, tabs, banner, front, recs, index);

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

  const phone = span('phone-art');
  phone.setAttribute('aria-hidden', 'true');
  phone.append(span('phone-glass'));

  const cheeks = CHEEKS.map(({ who, look, left, top, height }) => {
    const image = element('img', `desk-cheek cheek-${who}`);
    image.src = `${CUTS}${who}/base/${who}_cut-cheek_${look}.png`;
    image.alt = '';
    image.setAttribute('aria-hidden', 'true');
    Object.assign(image.style, { left: `${left}px`, top: `${top}px`, height: `${height}px` });
    image.addEventListener('error', () => image.remove(), { once: true });
    return image;
  });

  world.append(
    deco('desk-wood'), deco('desk-pad'), deco('desk-mug'), deco('desk-pen'),
    headline,
    deskObject('folder', folderArt),
    deskObject('news', newsSheets, news),
    deskObject('laptop', laptop),
    deskObject('phone', phone),
    ...cheeks,
    deco('desk-grain'),
  );

  // 신문 집어 들기: 책상이 신문 가운데를 기준으로 커지며 사라진 뒤 지면(S-02)으로 간다. 움직임 줄이기면 바로 간다
  let lifting = false;
  const aimAt = (target) => {
    const box = scene.getBoundingClientRect();
    const spot = target.getBoundingClientRect();
    scene.style.transformOrigin = `${((spot.left + spot.width / 2 - box.left) / box.width) * 100}% ${((spot.top + spot.height / 2 - box.top) / box.height) * 100}%`;
  };
  function lift(target) {
    if (lifting) return;
    if (reducedMotion()) { navigate('list', { from: 'desk' }); return; }
    lifting = true;
    aimAt(target);
    target.classList.add('is-held');
    scene.classList.add('is-lifting');
    setTimeout(() => { if (root.isConnected) navigate('list', { from: 'desk' }); }, LIFT_MS);
  }

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
  ['news', 'folder', 'phone', 'laptop'].forEach((key) => {
    const { label, route, mobileText } = OBJECTS[key];
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
    } else item.append(element('strong', '', label), text);
    if (route) item.addEventListener('click', () => navigate(route, key === 'news' ? { from: 'desk' } : {}));
    else item.setAttribute('aria-disabled', 'true');
    mobileCounts[key] = text;
    mobileList.append(item);
  });
  mobile.append(mobileHead, mobileList);

  // ---- 서비스 소개 ----
  const heading = element('h2', '', '이렇게 체험해요');
  heading.id = 'feature-heading';
  const cards = element('ol', 'feature-cards');
  features.forEach(({ title, text }, i) => {
    const card = element('li', 'feature-card');
    card.append(span('feature-step', String(i + 1)), element('h3', '', title), element('p', '', text));
    cards.append(card);
  });
  const start = button('사건 목록 보기', () => navigate('list'), 'primary-button');
  const introActions = element('div', 'feature-actions');
  introActions.append(start);
  intro.append(heading, cards, introActions);

  root.append(renderSiteHeader({ navigate, onIntro: showIntro }), scene, mobile, intro, renderSiteFooter());
  container.replaceChildren(root);
  fit();
  if (section === 'intro') showIntro();

  // 신문 덮기(8막)에서 왔으면 거꾸로: 들고 있던 신문에서 책상이 멀어지고, 신문이 책상에 내려앉는다.
  // 새로고침 · 뒤로 가기로는 되풀이하지 않는다. 움직임 줄이기면 연출 없이 바로 책상
  if (from === 'paper') {
    history.replaceState({ ...history.state, extras: {} }, '');
    if (!reducedMotion()) putDown(world.querySelector('.obj-news'));
  }
  function putDown(news) {
    if (!news) return;
    aimAt(news);
    root.classList.add('is-returning');
    news.classList.add('is-held');
    scene.classList.add('is-lifting');
    void scene.offsetWidth; // 들린 상태를 먼저 그려 두고 거기서 되돌린다
    root.classList.remove('is-returning');
    scene.classList.remove('is-lifting');
    setTimeout(() => news.classList.remove('is-held'), PUT_DOWN_MS);
    mobile.classList.add('is-returning'); // 좁은 화면: 메뉴의 신문 카드가 위에서 내려앉는다(CSS 애니메이션)
  }

  // ---- 신문 지면 채우기 (사건 목록 API 1). 실패해도 책상은 그대로 쓴다 ----
  async function fillNews() {
    try {
      const { summary, cases } = await api.getCases();
      if (!root.isConnected || !cases?.length) return;
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
        node.append(element('b', '', item.headline || item.title), span('', item.article?.[0] || item.shortIntro));
        return node;
      }));
      index.replaceChildren(...others.slice(2, 5).map((item) => span('', item.headline || item.title)));
      notes.news.textContent = `오늘의 사건 ${summary.total}건!`;
      mobileCounts.news.textContent = `오늘의 사건 ${summary.total}건`;
      mobileNews.headline.textContent = top.headline || top.title;
      mobileNews.photo.src = casePhoto(top);
    } catch (error) {
      console.warn('메인 신문 지면을 채우지 못했어요:', error);
    }
  }
  if (api?.getCases) fillNews();
}
