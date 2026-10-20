import { button, element, mockTag, renderSiteFooter } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { scriptFor } from '../overview/overviewPage.js';
import { casePhoto } from '../../utils/newspaper.js';
import { newspaperMasthead } from '../../components/newspaperFront.js';
import { turnPage } from '../../utils/pageTurn.js';
import { reducedMotion } from '../../utils/motion.js';
import { peelOff } from '../../utils/peelOff.js';
import { takePaper } from '../../utils/paperHandoff.js';

// S-02 사건 목록 — 신문 1면 (FE-18, 시안 v12 + 배치 조정). 유형 필터 · 오류 처리 로직은 FE-4 · FE-11과 같다.
// 흐름 목업 ②(FE-32): 책상에서 집어 든 신문이다. 위에 남불의 안내 포스트잇(누르면 떼어짐, FE-50)이 있고, 기사를 누르면
// 신문 한 장이 넘어가며(2.5D, utils/pageTurn.js) 그 밑에서 사건 정보(S-20)가 드러난다. 유형 탭을 누르면 같은 방식으로 한 장이 넘어가 다른 유형의 기사가 드러난다 — 체험 시작 · 이어서 하기는 S-20에서 한다(API 명세 5장 S-02 확장). Esc는 책상으로.
// 목록은 처음 판단(S-03) 전 화면이라 사건 정보는 S-03 개요 범위를 넘지 않는다: 제목 · 짧은 소개 · 키워드 · 분류 · 참여 수와,
// 개요 범위 안에서 쓴 기사(headline · deck · article). S-04 이후에 공개되는 사실(다툼 기간 · 횟수 · 양형 요소 등)은 기사에도 쓰지 않는다.
// 기사 제목 · 부제 · 본문은 headline · deck · article(API 1에 아직 없는 제안 필드, S-03 개요 수준)을 쓰고, 없으면 사건 제목 · 짧은 소개와
// 사건과 무관한 고정 문구(머리기사 안내 · 알아두기)로 채운다. (논의기록 2026-10-08 S02 v11 시안 기준)
const tabs = [
  { crimeType: undefined, label: '전체' },
  { crimeType: 'MURDER', label: '살인' },
  { crimeType: 'FRAUD', label: '사기' },
  { crimeType: 'INJURY', label: '상해' },
];
const TAB_TURN_MS = 500; // 탭은 훑어보며 자주 누르므로 기사 → 상세(1초)보다 짧게
const RECOMMENDED_COUNT = 2; // 머리기사 옆 추천 사건 수. 나머지는 "이번 호 다른 사건"으로 간다

// 머리기사 단 안내 (모든 사건 공통)
const LEAD_GUIDE = [
  '이 지면의 사건은 실제 판결을 바탕으로 이름 · 장소 등을 비식별화한 사건입니다. 기사 제목만 보고 결론을 내리기는 이릅니다.',
  '체험을 시작하면 먼저 뉴스에서 볼 수 있는 정도의 사건 개요가 주어집니다. 그 정보만으로 이 사건에 어느 정도의 형량이 맞다고 보는지, 처음 생각을 남겨 두세요.',
  '이어서 사건 기록을 한 단계씩 엽니다. 상세 사실관계, 검사와 피고인 측의 주장, 적용 법률과 양형기준이 차례로 공개됩니다. 앞 단계를 확인해야 다음 단계가 열립니다.',
  '기록을 모두 읽었다면 이제 판사석입니다. 형벌의 종류와 형량을 정하고, 판단에 무게를 둔 요소를 골라 형량을 높이는 쪽인지 낮추는 쪽인지 표시합니다.',
  '판결을 확정하면 같은 기록을 읽은 AI의 판결, 그리고 실제 재판부의 판결이 차례로 공개됩니다. 처음 생각과 최종 판결이 어떻게 달라졌는지도 함께 볼 수 있습니다.',
];
// 추천 사건 단 [알아두기] — 범죄 유형별 일반 설명 (특정 사건 정보 · 형량은 쓰지 않는다)
const TYPE_NOTES = {
  MURDER: '[알아두기] 살인죄는 사람의 생명을 빼앗았을 때 성립합니다. 어떤 법률이 적용되고 어느 범위에서 형을 정할 수 있는지는 사건 기록의 적용 법률 단계에서 공개됩니다.',
  FRAUD: '[알아두기] 사기죄는 사람을 속여 재물이나 재산상 이익을 얻었을 때 성립합니다. 처음부터 속일 생각이 있었는지는 겉으로 드러나지 않아, 거래 전후의 사정을 함께 보고 판단합니다.',
  INJURY: '[알아두기] 폭행은 사람의 몸에 힘을 가하는 것만으로 성립하고, 그로 인해 건강을 해친 결과가 생기면 상해가 문제 됩니다. 어떤 죄가 적용되는지는 사건 기록의 적용 법률 단계에서 공개됩니다.',
};
const RECORD_NOTE = '사건에 어떤 사정이 있었는지는 체험을 시작하면 사건 기록에서 한 단계씩 확인할 수 있습니다.';
// 진행 상태 배지 (FE-19, REQ-010 · FR-1-6). API 1의 myStatus 기준, 체험 전(null)이면 배지 없음
const STATUS_VIEW = {
  IN_PROGRESS: { badge: '진행 중', tone: 'active' },
  COMPLETED: { badge: '완료', tone: 'done' },
};
const NOT_STARTED = { badge: null, tone: null };
// 체험 대본이 없는 사건(FE-33 전)은 "준비 중"으로 표시한다(FE-56). 기사 · 사건 정보(S-20)는 읽을 수 있고, 새 체험 시작은 S-20에서 막는다.
// 이미 하던 · 마친 체험(myStatus)이 있으면 대본과 상관없이 그 상태를 그대로 보인다(이어서 하기 · 결과 보기는 S-20에서 열린다)
const SOON = { badge: '준비 중', tone: 'soon' };
const statusViewOf = (item) => STATUS_VIEW[item.myStatus] ?? (scriptFor(item.caseId, item.title) ? NOT_STARTED : SOON);
const PICK_LABEL = '사건 펼쳐 보기';
const JUMP_LABEL = '3면에 계속'; // 신문의 이어 읽기 줄(jump line). 누르면 사건 지면(S-20, 3면)으로 간다
const NAMBUL_FACE = '/images/characters/icon/nambul_face-happy.webp';
const UNFLIP_MS = 450; // 8막에서 뒤집고 온 빈 종이 · 바탕을 걷는 시간(그동안 기사가 페이드인한다)
const RISE_WAIT_MS = 1200; // 책상에서 들고 올 때 지면이 다 그려지기를 기다리는 최대 시간(그동안 책상은 신문을 든 채로 남아 있다)
const headlineOf = (item) => item.headline || item.title;
function dense(paragraphs) {
  const node = element('div', 'dense');
  paragraphs.forEach((text) => node.append(element('p', '', text)));
  return node;
}

// S-02 사건 목록
export async function renderCaseListPage(container, { api, navigate, from }) {
  const root = element('div', 'case-list-page');
  // 메인 책상에서 신문을 들고 온 경우(FE-50): 책상 화면을 뒤에 남겨 두었다가 지면이 아래에서 올라오는 동안 흐려지며 걷는다(성향 검사 서류철과 같은 방식)
  const ghost = from === 'desk' && !reducedMotion() && container.querySelector('.landing-page') ? element('div', 'paper-ghost') : null;
  if (ghost) {
    ghost.setAttribute('aria-hidden', 'true');
    ghost.inert = true;
    ghost.append(...container.childNodes);
  }
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  let selected;
  let picking = false;
  // 책상에서 왔으면 메인 책상에서는 지면이 아래에서 올라오고, 좁은 화면 메뉴 · 비교 화면에서는 한 번 다가오며 나타난다. 새로고침 · 뒤로 가기로는 되풀이하지 않는다
  if (from === 'desk') {
    history.replaceState({ ...history.state, extras: {} }, '');
    if (!reducedMotion()) root.classList.add(ghost ? 'is-rising' : 'is-arriving');
  }
  // 8막 신문을 뒤집고 왔으면(FE-55 "다른 사건 보기") 넘겨받은 빈 뒷면(paperHandoff.js)이 화면을 덮고 있다.
  // 기사가 다 그려지면 같은 자리의 이 지면 위에 기사가 페이드인한다(unflip)
  const handed = from === 'flip' ? takePaper() : null;
  if (from === 'flip') {
    history.replaceState({ ...history.state, extras: {} }, '');
    if (!reducedMotion()) root.classList.add('is-unflipping');
    else handed?.layer.remove();
  }

  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  const paper = element('div', 'paper');
  const earRight = element('div', 'ear ear-right');
  const main = element('main', 'front');
  // 목록을 받기 전: 빈 지면 대신 상태 문구를 둔다(FE-56). 책상에서 들고 올 때는 다 그려진 뒤 올라오므로 주소로 바로 올 때만 보인다
  const loading = element('p', 'paper-loading', '지면을 펼치는 중이에요');
  loading.setAttribute('role', 'status');
  main.append(loading);
  const paperNote = element('p', 'paper-note', '이 지면의 사건과 기사는 판결 체험을 위해 재구성한 것이며, 사진은 AI로 생성한 연출 이미지입니다.');
  paper.append(newspaperMasthead({ navigate, earRight }), main, paperNote, renderSiteFooter());

  // 남불의 안내. 지면 오른쪽 위에 붙인 정사각형 포스트잇(FE-56, 지면과 함께 움직인다). 누르면 떼어져 날아간다(FE-50, 메인 책상의 내로 포스트잇과 같은 놀이).
  // 책상으로 돌아가는 길은 정보 줄의 "← 메인으로"와 Esc가 맡는다
  const tip = element('button', 'paper-tip');
  tip.type = 'button';
  const face = element('img');
  face.src = NAMBUL_FACE;
  face.alt = '';
  face.addEventListener('error', () => face.remove(), { once: true });
  const tipText = element('span', 'paper-tip-text');
  const tipLine = element('span', '', '마음이 가는 기사를 눌러 보세요!');
  tipText.append(element('b', '', '남불'), tipLine, element('span', 'visually-hidden', '(눌러서 떼기)'));
  tip.append(face, tipText);
  tip.addEventListener('click', () => peelOff(tip, {
    from: 'none',
    to: 'translate(60px, -140px) rotate(28deg)', // 오른쪽 위로 떼어져 날아간다
    // 목록을 아직 불러오는 중이면 첫 탭 대신 본문(main)이 포커스를 받는다
    focusTarget: () => main.querySelector('button') ?? Object.assign(main, { tabIndex: -1 }),
  }));
  paper.prepend(tip);
  root.append(desk, paper);
  if (ghost) root.insertBefore(ghost, paper);
  // 뒤집고 왔으면 8막 지면과 같은 폭 · 같은 자리에 지면을 놓는다(같은 신문 한 장이 이어진다). 아래로 길어지는 부분은 화면 밖이다
  if (handed && root.classList.contains('is-unflipping')) {
    const top = Math.max(0, handed.rect.top);
    Object.assign(paper.style, { width: `${handed.rect.width}px`, marginLeft: 'auto', marginRight: 'auto', marginTop: `${top}px` });
    const off = paper.getBoundingClientRect().top - top; // 바깥 여백이 겹쳐 더 내려갔으면(좁은 화면) 그만큼 끌어올린다
    if (off) paper.style.translate = `0 ${-off}px`;
  }
  // 지면은 머리기사부터 다 그려지고 첫 사진들이 준비된 뒤 한 번에 올라온다(올라오는 도중 아래 기사가 채워지며 끊기지 않게).
  // 사건 목록이 늦으면 RISE_WAIT_MS에서 기다리기를 멈추고 올린다. 다 올라오면 뒤에 남긴 책상을 걷는다
  function drawn(loaded) {
    const ready = loaded.then(() => Promise.all([...main.querySelectorAll('.front-grid img, .recs img')].map((image) => {
      image.loading = 'eager';
      return image.decode().catch(() => {});
    })));
    return Promise.race([ready, new Promise((done) => { window.setTimeout(done, RISE_WAIT_MS); })]);
  }
  async function rise(loaded) {
    await drawn(loaded);
    if (!active()) return;
    let timer = 0;
    const drop = () => { // 한 번만: 올라오는 애니메이션이 끝나면 뒤에 남긴 책상과 표시를 걷는다
      window.clearTimeout(timer);
      paper.removeEventListener('animationend', onEnd);
      ghost.remove();
      root.classList.remove('is-rising', 'is-going');
    };
    const onEnd = (event) => { if (event.target === paper && event.animationName === 'paper-rise') drop(); };
    paper.addEventListener('animationend', onEnd);
    timer = window.setTimeout(drop, 2000); // animationend가 오지 않아도 걷는다
    root.classList.add('is-going');
  }

  // 넘겨받은 빈 종이는 이 지면과 같은 자리 · 같은 크기로 덮여 있다. 기사가 다 그려지면 지면을 드러내 기사를 페이드인하고,
  // 그 위의 빈 종이와 바탕(8막 화면 색)은 함께 걷는다 — 종이는 움직이지 않고 내용만 나타난다
  async function unflip(paperHanded) {
    if (!active()) { paperHanded?.layer.remove(); return; }
    root.classList.add('is-revealing');
    if (!paperHanded) return;
    const { layer, shade, card } = paperHanded;
    const timing = { duration: UNFLIP_MS, easing: 'ease-out', fill: 'forwards' };
    await Promise.all([card, shade].map((node) => node.animate([{ opacity: 1 }, { opacity: 0 }], timing).finished)).catch(() => {});
    layer.remove();
  }

  // Esc: 신문을 내려놓고 책상으로(목업과 같은 한 단계 뒤로)
  function onKey(event) {
    if (!active()) { document.removeEventListener('keydown', onKey); return; }
    if (event.key === 'Escape' && !event.defaultPrevented && !picking) navigate('landing');
  }
  document.addEventListener('keydown', onKey);

  function showError(retry) {
    const box = element('section', 'message-panel');
    box.setAttribute('role', 'alert');
    box.append(element('p', '', '사건 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'), button('다시 시도', retry));
    main.replaceChildren(box);
  }

  // 기사 고르기: 지금 지면이 한 장 넘어가기 시작하고, 그 밑에 사건 정보(S-20)가 바로 놓인다
  function pick(item, target) {
    if (picking) return;
    picking = true;
    target.classList.add('is-picked');
    root.classList.add('is-picking');
    turnPage(paper, { scopeClass: 'case-list-page' });
    navigate('caseDetail', { caseId: item.caseId, from: 'paper' });
  }
  // 기사 전체를 누를 수 있게 한다(마우스). 키보드 · 화면 낭독기는 기사 안의 "사건 펼쳐 보기" 버튼으로 고른다
  function pickable(node, item) {
    node.classList.add('is-pickable');
    node.addEventListener('click', (event) => {
      if (event.target.closest('button')) return; // 버튼은 자기 동작으로 고른다
      pick(item, node);
    });
    return node;
  }

  // ---- 기사 조각 ----
  function kicker(item, prefix) {
    const node = element('div', 'kicker');
    if (prefix) node.append(element('span', '', prefix), element('span', '', '·'));
    const square = element('span', `type-square type-${item.crimeType}`);
    square.setAttribute('aria-hidden', 'true');
    node.append(square, element('span', '', item.crimeCategoryLabel));
    const { badge, tone } = statusViewOf(item);
    // 진행 상태 배지는 분류명 줄 오른쪽 끝에 둔다. 화면 낭독기에는 "진행 중" · "완료" 글자 그대로 읽힌다
    if (badge) node.append(element('span', `status-badge status-${tone}`, badge));
    return node;
  }

  function photo(item) {
    const image = element('img', 'photo');
    image.src = casePhoto(item); // 썸네일이 없으면 공용 재연 컷(샘플)
    image.alt = ''; // 연출 이미지라 읽지 않는다 (사건 정보는 제목 · 소개문에 있다)
    image.width = 3; // 비율 힌트(3:2). 크기는 CSS가 정한다
    image.height = 2;
    image.loading = 'lazy';
    return image;
  }

  // 사진이 없는 작은 기사: 공용 재연 컷에 인쇄 망점 필터를 씌운다(옛 신문 사진처럼). 추천 기사의 같은 컷과 겹쳐도 다른 사진처럼 보인다
  function halftonePhoto(item) {
    const frame = element('div', 'halftone');
    frame.append(photo(item));
    return frame;
  }

  function meta(item, withTags) {
    const node = element('p', 'meta');
    if (withTags && item.keywords?.length) node.append(element('span', 'tags', item.keywords.map((keyword) => `#${keyword}`).join(' ')));
    node.append(`참여 ${item.participantCount.toLocaleString('ko-KR')}명`);
    if (API_MODE === 'mock') node.append(mockTag()); // 목 참여 수는 실제 반응이 아니다(FE-56)
    return node;
  }

  // 이어 읽기 줄: 웹 버튼 대신 신문의 "(3면에 계속)". 이름은 화면 글자로 시작한다(WCAG 2.5.3)
  function startButton(item, className = 'start') {
    const node = button(`(${JUMP_LABEL})`, () => pick(item, node.closest('.article') ?? node), className);
    node.setAttribute('aria-label', `${JUMP_LABEL}, ${item.title} ${PICK_LABEL}`);
    return node;
  }

  function leadArticle(item) {
    const article = element('article', 'article lead-col');
    article.setAttribute('aria-label', '머리기사');
    const body = item.article?.length ? item.article : [item.shortIntro, ...LEAD_GUIDE];
    article.append(
      kicker(item, selected ? undefined : '머리기사'),
      element('p', 'lead-intro', item.deck || (item.headline ? item.title : item.shortIntro)),
      element('p', 'byline', '사회부'),
      dense(body),
      meta(item, true),
      startButton(item, 'start start-filled'),
    );
    return pickable(article, item);
  }

  function leadPhoto(item) {
    const figure = element('figure', 'lead-photo');
    figure.append(photo(item), element('figcaption', 'caption', '사건 이해를 돕기 위한 연출 이미지'));
    return pickable(figure, item);
  }

  function recommendedArticle(item) {
    const article = element('article', 'article');
    article.append(
      photo(item),
      element('p', 'caption', '연출 이미지'),
      kicker(item, '추천 사건'),
      element('h3', '', headlineOf(item)),
      element('p', 'byline', '사회부'),
      dense(item.article?.length ? item.article : [item.shortIntro, TYPE_NOTES[item.crimeType], RECORD_NOTE].filter(Boolean)),
      meta(item),
      startButton(item),
    );
    return pickable(article, item);
  }

  function indexArticle(item) {
    const article = element('article', 'article');
    // 사진이 있으면 사진, 없으면 공용 재연 컷에 망점 필터(작은 기사 6건에 같은 사진이 그대로 되풀이되지 않게)
    article.append(item.thumbnailUrl ? photo(item) : halftonePhoto(item), kicker(item), element('h3', '', headlineOf(item)), dense([item.article?.[0] ?? item.shortIntro]), meta(item),
      startButton(item));
    return pickable(article, item);
  }

  // ---- 지면 ----
  function renderTabs(summary) {
    const zone = element('div', 'tabs-zone');
    const bar = element('div', 'tabs');
    bar.setAttribute('role', 'group');
    bar.setAttribute('aria-label', '범죄 유형');
    tabs.forEach(({ crimeType, label }) => {
      const count = crimeType ? summary.byCrimeType[crimeType] : summary.total;
      const tab = button(label, () => switchTab(crimeType), 'tab');
      tab.setAttribute('aria-pressed', String(crimeType === selected));
      tab.setAttribute('aria-label', `${label} ${count}건`);
      bar.append(tab);
    });
    zone.append(bar, element('p', 'case-count', `총 ${summary.total}건 · ${byTypeText(summary)}`));
    return zone;
  }

  function byTypeText(summary) {
    return tabs.slice(1).map(({ crimeType, label }) => `${label} ${summary.byCrimeType[crimeType] ?? 0}`).join(' · ');
  }

  function draw({ summary, cases }) {
    tipLine.textContent = `오늘 지면에 사건이 ${summary.total}건 실렸어요. 마음이 가는 기사를 눌러 보세요!`;
    earRight.replaceChildren(element('b', '', `오늘의 사건 ${summary.total}건`), element('span', '', byTypeText(summary)));

    const parts = [renderTabs(summary)];
    if (!cases.length) {
      parts.push(element('p', 'empty', '해당하는 사건이 없어요.'));
    } else {
      // 참여 많은 순: 첫 사건이 머리기사, 다음 2건이 추천 사건, 나머지는 다른 사건
      const ordered = [...cases].sort((a, b) => b.participantCount - a.participantCount);
      const [lead, ...others] = ordered;
      const recommended = others.slice(0, RECOMMENDED_COUNT);
      const rest = others.slice(RECOMMENDED_COUNT);

      const banner = element('div', 'banner');
      banner.append(element('h2', '', headlineOf(lead)));
      pickable(banner, lead);
      // 1행: 메인 사진 | 머리기사, 2행: 추천 사건 2건(지면 전체 폭을 반반)
      const grid = element('section', 'front-grid');
      grid.setAttribute('aria-label', '머리기사');
      grid.append(leadPhoto(lead), leadArticle(lead));
      parts.push(banner, grid);
      if (recommended.length) {
        const recs = element('section', 'recs');
        recs.setAttribute('aria-label', '추천 사건');
        recommended.forEach((item) => recs.append(recommendedArticle(item)));
        parts.push(recs);
      }

      if (rest.length) {
        const index = element('section', 'index');
        index.setAttribute('aria-label', '이번 호 다른 사건');
        const head = element('div', 'index-head');
        head.append(element('h2', '', '이번 호 다른 사건'), element('span', '', `${rest.length}건 · 참여 많은 순`));
        const list = element('div', 'index-grid');
        rest.forEach((item) => list.append(indexArticle(item)));
        index.append(head, list);
        parts.push(index);
      }
    }
    main.replaceChildren(...parts);
    paper.classList.toggle('inner-page', selected !== undefined); // 전체 탭만 1면, 유형 탭(살인 · 사기 · 상해)은 안쪽 면(오른쪽 면)
  }

  async function load(crimeType) {
    selected = crimeType;
    try {
      const data = await api.getCases(crimeType);
      if (active()) draw(data);
    } catch (error) {
      if (active()) showError(() => load(crimeType));
    }
  }

  // 탭 바꾸기: 새 목록을 받으면 지금 지면을 한 장으로 떼어 넘기고, 그 밑에 새 목록을 바로 그린다(빠르게 연달아 눌러도 마지막 것만 남는다)
  let switching = 0;
  async function switchTab(crimeType) {
    if (picking || crimeType === selected) return;
    const mine = ++switching;
    selected = crimeType;
    let result = null;
    try {
      result = await api.getCases(crimeType);
    } catch (error) {
      result = null;
    }
    if (!active() || mine !== switching) return;
    turnPage(paper, { scopeClass: 'case-list-page', duration: TAB_TURN_MS });
    if (result) draw(result);
    else showError(() => load(crimeType));
  }

  const loaded = load(undefined);
  if (ghost) rise(loaded);
  // 뒤집고 왔으면 기사와 첫 사진이 다 준비된 뒤에 한 번에 페이드인한다(늦게 온 기사가 따로 튀어나오지 않게)
  // 기사 준비가 어떻게 끝나든(성공 · 실패 · 시간 초과) 덮고 있는 빈 종이는 반드시 걷는다
  if (root.classList.contains('is-unflipping')) drawn(loaded).catch(() => {}).finally(() => unflip(handed));

  await loaded;
}
