import { button, element, mockTag, renderSiteFooter } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { scriptFor } from '../overview/overviewPage.js';
import { casePhoto, commonCategoryOf, commonPhotoAt, useCommonPhotoOnError } from '../../utils/newspaper.js';
import { newspaperMasthead } from '../../components/newspaperFront.js';
import { turnPage } from '../../utils/pageTurn.js';
import { liftArticle } from '../../utils/articleZoom.js';
import { createFillerDeck, emptySlots, faceIndexAfter, FILL_GAP, FRONT_LIMIT, layoutCases, planFiller, swipeStep, visibleFaces } from '../../utils/newspaperFaces.js';
import courtFacts from '../../content/courtFacts.json';
import { TYPE_NOTES } from '../../content/typeNotes.js';
import { reducedMotion } from '../../utils/motion.js';
import { peelOff } from '../../utils/peelOff.js';
import { takePaper } from '../../utils/paperHandoff.js';
import { spreadFromTail } from '../../utils/tailSpread.js';

// S-02 사건 목록 — 신문 1면 (FE-18, 시안 v12 + 배치 조정). 유형 필터 · 오류 처리 로직은 FE-4 · FE-11과 같다.
// 흐름 목업 ②(FE-32): 책상에서 집어 든 신문이다. 위에 남불의 안내 포스트잇(누르면 떼어짐, FE-50)이 있고, 기사를 누르면
// 그 기사의 제목이 사건 정보(S-20)의 큰 제목 자리로 커지며 날아간다(utils/articleZoom.js, FE-118) — 체험 시작 · 이어서 하기는 S-20에서 한다(API 명세 5장 S-02 확장). Esc는 책상으로.
// 면 넘기기(FE-118): 유형 탭 하나가 신문 한 면이다(전체 = 1면, 그다음 살인 · 사기 · 상해 · 강도 · 절도). 탭 · 키보드 ← → · 손가락 밀기로(종이 귀 · 양옆 화살표는 FE-119에서 뺐다)
// 한 장이 넘어간다(2.5D, utils/pageTurn.js). 뒤 면으로는 오른쪽에서 왼쪽으로, 앞 면으로는 거꾸로 넘어온다. 기사를 누르는 것은 언제나 사건 정보로 가는 길이고, 넘김은 위의 조작으로만 한다.
// 목록은 처음 판단(S-03) 전 화면이라 사건 정보는 S-03 개요 범위를 넘지 않는다: 제목 · 짧은 소개 · 키워드 · 분류 · 참여 수와,
// 개요 범위 안에서 쓴 기사(headline · deck · article). S-04 이후에 공개되는 사실(다툼 기간 · 횟수 · 양형 요소 등)은 기사에도 쓰지 않는다.
// 기사 제목 · 부제 · 본문은 headline · deck · article(API 1에 아직 없는 제안 필드, S-03 개요 수준)을 쓰고, 없으면 사건 제목 · 짧은 소개와
// 사건과 무관한 고정 문구(머리기사 안내 · 알아두기)로 채운다. (논의기록 2026-10-08 S02 v11 시안 기준)
// 탭 하나가 신문 한 면이다(FE-119). 1면(헤드, 9건만) → 범죄 유형 면(사건이 0건이면 숨김) → 색인(전체 제목 목록 · 검색) → 성향 테스트 · 오피니언(껍데기).
// crimeType은 면의 열쇠이기도 하다: 범죄 유형이 아닌 면(special)은 API에 유형으로 보내지 않고 전체 목록을 받는다
const tabs = [
  { crimeType: undefined, label: '1면' },
  { crimeType: 'MURDER', label: '살인' },
  { crimeType: 'FRAUD', label: '사기' },
  { crimeType: 'INJURY', label: '상해' },
  { crimeType: 'ROBBERY', label: '강도' },
  { crimeType: 'THEFT', label: '절도' },
  { crimeType: 'INDEX', label: '색인', special: true },
  { crimeType: 'TEST', label: '성향 테스트', special: true },
  { crimeType: 'OPINION', label: '오피니언', special: true },
];
const CRIME_TABS = tabs.filter(({ crimeType, special }) => crimeType && !special);
const isCrimeFace = (key) => CRIME_TABS.some(({ crimeType }) => crimeType === key);
const apiTypeOf = (key) => (isCrimeFace(key) ? key : undefined); // 범죄 유형이 아닌 면은 전체 목록을 받는다
const TAB_TURN_MS = 500; // 면은 훑어보며 자주 넘기므로 한 장 넘기기 기본값(1초)보다 짧게
const faceName = ({ crimeType, label }) => (isCrimeFace(crimeType) ? `${label}면` : label);
const MORE_LABEL = '이 면 더 보기';

// 머리기사 단 안내 (모든 사건 공통)
const LEAD_GUIDE = [
  '이 지면의 사건은 실제 판결을 바탕으로 이름 · 장소 등을 비식별화한 사건입니다. 기사 제목만 보고 결론을 내리기는 이릅니다.',
  '체험을 시작하면 먼저 뉴스에서 볼 수 있는 정도의 사건 개요가 주어집니다. 그 정보만으로 이 사건에 어느 정도의 형량이 맞다고 보는지, 처음 생각을 남겨 두세요.',
  '이어서 사건 기록을 한 단계씩 엽니다. 상세 사실관계, 검사와 피고인 측의 주장, 적용 법률과 양형기준이 차례로 공개됩니다. 앞 단계를 확인해야 다음 단계가 열립니다.',
  '기록을 모두 읽었다면 이제 판사석입니다. 형벌의 종류와 형량을 정하고, 판단에 무게를 둔 요소를 골라 형량을 높이는 쪽인지 낮추는 쪽인지 표시합니다.',
  '판결을 확정하면 같은 기록을 읽은 AI의 판결, 그리고 실제 재판부의 판결이 차례로 공개됩니다. 처음 생각과 최종 판결이 어떻게 달라졌는지도 함께 볼 수 있습니다.',
];
const RECORD_NOTE = '사건에 어떤 사정이 있었는지는 체험을 시작하면 사건 기록에서 한 단계씩 확인할 수 있습니다.';
// 빈 칸 채움 지면(FE-118): 기사가 모자라 비는 칸(추천 사건 반쪽 · 다른 사건 줄의 남는 칸 · 기사가 적은 면의 아래)을 사건이 아닌 작은 지면으로 채운다.
// 법정 상식은 1막 막간과 같은 문장(content/courtFacts.json), 광고는 이 서비스의 판결 성향 테스트. 누르면 사건으로 가지 않는다(광고만 테스트로)
const FILLER_STRIP = 3; // 추천 사건이 한 건뿐일 때 비는 반쪽에 쌓는 작은 지면 수
const PHOTO_CATEGORIES = ['life', 'body', 'property']; // 사진 지면: 공용 재연 컷 분류 · 자리를 돌려 가며 쓴다
const PHOTO_ROLES = ['reenact', 'origin', 'conclusion'];
// 진행 상태 표시 (FE-19, REQ-010 · FR-1-6). API 1의 myStatus 기준, 체험 전(null)이면 표시 없음
const STATUS_VIEW = {
  IN_PROGRESS: { badge: '진행 중', tone: 'active' },
  COMPLETED: { badge: '완료', tone: 'done' },
};
const NOT_STARTED = { badge: null, tone: null };
// FE-119: 진행 상태는 글자 배지가 아니라 지면에 찍힌 작은 도장으로 구분한다. 진행 중 · 완료만 찍고, 체험 전 · 준비 중 사건에는 아무것도 찍지 않는다(준비 중은 S-20 도장에서 알린다).
// 체험 대본이 없는 사건(FE-33 전)은 "준비 중"으로 치되(SOON, 정렬에만 쓴다 — 머리기사 · 추천에 앞서지 않게), 기사 · 사건 정보(S-20)는 읽을 수 있고 새 체험 시작은 S-20에서 막는다(FE-56).
// 이미 하던 · 마친 체험(myStatus)이 있으면 대본과 상관없이 그 상태를 그대로 보인다(이어서 하기 · 결과 보기는 S-20에서 열린다)
const SOON = { badge: '준비 중', tone: 'soon' };
const stampOf = (item) => STATUS_VIEW[item.myStatus] ?? null;
function stampMark(item) {
  const { badge, tone } = stampOf(item);
  return element('span', `status-stamp status-${tone}`, badge);
}
const statusViewOf = (item) => STATUS_VIEW[item.myStatus] ?? (scriptFor(item.caseId, item.title) ? NOT_STARTED : SOON);
const isSoon = (item) => statusViewOf(item) === SOON;
// 지금 눌러서 체험을 시작할 수 있는 사건 수에 맞춘 포스트잇 문구(FE-102 — 모두 열린 것처럼 "눌러 보세요"만 권하지 않는다)
function tipMessage(total, playable) {
  const base = `오늘 지면에 사건이 ${total}건 실렸어요.`;
  if (playable >= total) return `${base} 마음이 가는 기사를 눌러 보세요!`;
  if (playable > 0) return `${base} 지금 체험할 수 있는 기사는 ${playable}건이에요.`;
  return `${base} 체험은 곧 열려요. 기사는 미리 읽어 볼 수 있어요.`;
}
const typeFromUrl = () => {
  const found = new URLSearchParams(window.location.search).get('type');
  return tabs.some(({ crimeType }) => crimeType && crimeType === found) ? found : undefined; // 면 열쇠(범죄 유형 · 색인 · 성향 테스트 · 오피니언)
};
// 탭 선택을 주소(?type=)에 남긴다. 사건 정보에 다녀오거나 새로고침해도 같은 면으로 돌아온다(화면을 다시 그리지 않는 replaceState)
function saveTypeToUrl(crimeType) {
  const url = new URL(window.location.href);
  if (crimeType) url.searchParams.set('type', crimeType); else url.searchParams.delete('type');
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
}
const PICK_LABEL = '사건 펼쳐 보기';
const JUMP_LABEL = '3면에 계속'; // 신문의 이어 읽기 줄(jump line). 누르면 사건 지면(S-20, 3면)으로 간다
const NAMBUL_FACE = '/images/characters/icon/generated/nambul_face-happy.webp';
const UNFLIP_MS = 450; // 6막에서 뒤집고 온 빈 종이 · 바탕을 걷는 시간(그동안 기사가 페이드인한다)
const RISE_WAIT_MS = 1200; // 책상에서 들고 올 때 지면이 다 그려지기를 기다리는 최대 시간(그동안 책상은 신문을 든 채로 남아 있다)
const headlineOf = (item) => item.headline || item.title;
function dense(paragraphs) {
  const node = element('div', 'dense');
  paragraphs.forEach((text) => node.append(element('p', '', text)));
  return node;
}

// S-02 사건 목록
export async function renderCaseListPage(container, { api, navigate, from, openAt }) {
  const root = element('div', 'case-list-page is-loading'); // 목록을 받기 전: 아래 푸터 · 안내를 감춰 둔다(받은 뒤 아래로 밀려 CLS 0.12가 났다, FE-102)
  // 메인 책상에서 신문을 들고 온 경우(FE-50): 책상 화면을 뒤에 남겨 두었다가 지면이 아래에서 올라오는 동안 흐려지며 걷는다(성향 검사 서류철과 같은 방식)
  // 맨 아래 신문 끝자락에서 왔으면(from: 'tail', FE-87) 같은 방식으로 메인을 뒤에 남기고, 지면은 끝자락 자리에서 커지며 나온다
  const ghost = (from === 'desk' || from === 'tail') && !reducedMotion() && container.querySelector('.landing-page') ? element('div', 'paper-ghost') : null;
  if (ghost) {
    ghost.setAttribute('aria-hidden', 'true');
    ghost.inert = true;
    ghost.append(...container.childNodes);
  }
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  // openAt: 다른 화면에서 특정 면으로 열 때(S-20 지면 목차의 8면 색인, FE-119). 주소 ?type=에도 남기고, 새로고침으로 되풀이되지 않게 기록의 넘김값은 비운다
  const openFace = tabs.some(({ crimeType }) => crimeType && crimeType === openAt) ? openAt : undefined;
  if (openAt) history.replaceState({ ...history.state, extras: {} }, '');
  if (openFace) saveTypeToUrl(openFace);
  let selected = openFace ?? typeFromUrl();
  let picking = false;
  let lastSummary = null; // 마지막으로 받은 건수. 오류 · 로딩에서도 탭을 그리는 데 쓴다(FE-102)
  const mates = new Map(); // 사건 번호 → 같은 사건을 가리키는 누르는 영역들(호버를 함께 보이게)
  // 책상에서 왔으면 메인 책상에서는 지면이 아래에서 올라오고, 좁은 화면 메뉴 · 비교 화면에서는 한 번 다가오며 나타난다. 새로고침 · 뒤로 가기로는 되풀이하지 않는다
  if (from === 'desk') {
    history.replaceState({ ...history.state, extras: {} }, '');
    if (!reducedMotion()) root.classList.add(ghost ? 'is-rising' : 'is-arriving');
  }
  const tail = from === 'tail' ? takePaper() : null; // 메인이 본떠 넘긴 끝자락 한 장과 그 자리
  if (from === 'tail') {
    history.replaceState({ ...history.state, extras: {} }, '');
    if (ghost && tail) root.classList.add('is-spreading');
    else { tail?.layer.remove(); ghost?.remove(); } // 넘겨받은 한 장이 없으면 연출을 포기하고 뒤에 남긴 메인도 바로 걷는다(아래 rise()가 돌지 않는 길)
  }
  // 6막 신문을 뒤집고 왔으면(FE-55 "다른 사건 보기") 넘겨받은 빈 뒷면(paperHandoff.js)이 화면을 덮고 있다.
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
  main.append(renderTabs(null, { disabled: true }), loading); // 탭은 처음부터 보이되 목록을 받기 전에는 누를 수 없다(FE-102)
  const paperNote = element('p', 'paper-note', '이 지면의 사건과 기사는 판결 체험을 위해 재구성한 것이며, 사진은 AI로 생성한 연출 이미지입니다.');
  // 양옆 화살표(FE-118, 넓은 화면): 마우스용 지름길. 키보드 ← → 와 같은 동작이라 화면 낭독기 · 탭 순서에서는 뺀다. 면을 그릴 때마다 앞 · 뒤 면에 맞춰 다시 단다(syncEdges)
  const edges = element('div', 'face-edges');
  paper.append(newspaperMasthead({ navigate, earRight }), main, paperNote, renderSiteFooter());

  // 남불의 안내. 지면 오른쪽 위에 붙인 정사각형 포스트잇(FE-56, 지면과 함께 움직인다). 누르면 떼어져 날아간다(FE-50, 메인 책상의 내로 포스트잇과 같은 놀이).
  // 책상으로 돌아가는 길은 정보 줄의 "← 메인으로"와 Esc가 맡는다
  const tip = element('button', 'paper-tip');
  tip.type = 'button';
  tip.disabled = true; // 감춰 둔 동안은 키보드 포커스도 받지 않는다(draw · showError에서 푼다)
  tip.classList.add('is-waiting'); // 건수를 받기 전에는 감춰 두었다가 한 번에 나타난다(문구가 바뀌며 깜빡이지 않게, FE-102)
  const face = element('img');
  face.src = NAMBUL_FACE;
  face.alt = '';
  face.addEventListener('error', () => face.remove(), { once: true });
  const tipText = element('span', 'paper-tip-text');
  const tipLine = element('span', '', ''); // 문구는 draw · showError가 채운다(처음 값이 ?type=로 들어온 첫 화면에 남지 않게)
  tipText.append(element('b', '', '남불'), tipLine, element('span', 'visually-hidden', '(눌러서 떼기)'));
  tip.append(face, tipText);
  tip.addEventListener('click', () => { root.classList.add('tip-off'); peelOff(tip, {
    from: 'none',
    to: 'translate(60px, -140px) rotate(28deg)', // 오른쪽 위로 떼어져 날아간다
    // 목록을 아직 불러오는 중이면 첫 탭 대신 본문(main)이 포커스를 받는다
    focusTarget: () => main.querySelector('button') ?? Object.assign(main, { tabIndex: -1 }),
  }); });
  paper.prepend(tip);
  root.append(desk, paper, edges);
  if (ghost) root.insertBefore(ghost, paper);
  // 뒤집고 왔으면 6막 지면과 같은 폭 · 같은 자리에 지면을 놓는다(같은 신문 한 장이 이어진다). 아래로 길어지는 부분은 화면 밖이다
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

  // 끝자락에서 왔으면 메인이 본뜬 끝자락이 곧바로 위로 잡아당겨지며 커지고, 들어 올린 뒤 그 밑에서 함께 움직이던 이 지면으로 넘어간다(utils/tailSpread.js).
  // 넘어가기 직전까지 기사와 첫 사진이 다 준비되지 않았으면(최대 RISE_WAIT_MS) 그 자리에서 기다린다. 뒤에 남긴 메인은 함께 흐려진다
  async function spread(loaded) {
    if (!active()) { tail.layer.remove(); return; } // 기다리는 사이 다른 화면으로 갔으면 연출하지 않는다(rise · unflip과 같다)
    root.classList.add('is-going');
    // 연출 도중 다른 화면으로 가면(Esc · 헤더) 맨 위 층에 본뜬 끝자락이 새 화면 위에 남지 않게 바로 걷는다
    const watch = window.setInterval(() => { if (!active()) tail.layer.remove(); }, 100);
    try {
      await spreadFromTail({ paper, face: tail.card, from: tail.rect, backdrop: ghost, ready: drawn(loaded) });
    } catch {
      // 연출이 실패해도 화면 전환은 막지 않는다(아래에서 연출 층을 걷고 지면을 그대로 둔다)
    } finally {
      window.clearInterval(watch);
      tail.layer.remove();
      ghost.remove();
      if (active()) root.classList.remove('is-spreading', 'is-going'); // 그 사이 다른 화면으로 갔으면 이 지면은 이미 버려졌다
    }
  }

  // 넘겨받은 빈 종이는 이 지면과 같은 자리 · 같은 크기로 덮여 있다. 기사가 다 그려지면 지면을 드러내 기사를 페이드인하고,
  // 그 위의 빈 종이와 바탕(6막 화면 색)은 함께 걷는다 — 종이는 움직이지 않고 내용만 나타난다
  async function unflip(paperHanded) {
    if (!active()) { paperHanded?.layer.remove(); return; }
    root.classList.add('is-revealing');
    if (!paperHanded) return;
    const { layer, shade, card } = paperHanded;
    const timing = { duration: UNFLIP_MS, easing: 'ease-out', fill: 'forwards' };
    await Promise.all([card, shade].map((node) => node.animate([{ opacity: 1 }, { opacity: 0 }], timing).finished)).catch(() => {});
    layer.remove();
  }

  // Esc: 신문을 내려놓고 책상으로(목업과 같은 한 단계 뒤로). ← → : 앞 · 다음 면(글자를 쓰는 칸에서는 그 칸의 커서 이동이 먼저다)
  function onKey(event) {
    if (!active()) { document.removeEventListener('keydown', onKey); return; }
    if (event.defaultPrevented || picking) return;
    if (event.key === 'Escape') navigate('landing');
    const arrow = { ArrowLeft: -1, ArrowRight: 1 }[event.key];
    if (arrow && !event.altKey && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.target.closest?.('input, textarea, select, [contenteditable]')) turnFace(arrow);
  }
  document.addEventListener('keydown', onKey);

  // 손가락 밀기: 왼쪽으로 밀면 다음 면, 오른쪽이면 앞 면. 세로 스크롤은 그대로 두려고 지켜보기만 한다(passive). 두 손가락은 확대라 무시
  let touch = null;
  paper.addEventListener('touchstart', (event) => {
    touch = event.touches.length === 1 ? { x: event.touches[0].clientX, y: event.touches[0].clientY, at: event.timeStamp } : null;
  }, { passive: true });
  paper.addEventListener('touchend', (event) => {
    const start = touch;
    touch = null;
    if (!start || event.touches.length) return;
    const end = event.changedTouches[0];
    const step = swipeStep({ dx: end.clientX - start.x, dy: end.clientY - start.y, ms: event.timeStamp - start.at });
    if (step) turnFace(step);
  }, { passive: true });
  paper.addEventListener('touchcancel', () => { touch = null; }, { passive: true });

  // 지금 보이는 면(0건 면은 숨김)을 순서대로. 키보드 · 밀기 넘기기가 이 목록을 따른다
  const shownFaces = () => visibleFaces(tabs, lastSummary?.byCrimeType, selected);
  function syncEdges() {
    edges.replaceChildren();
    const faces = shownFaces();
    [-1, 1].forEach((step) => {
      const index = faceIndexAfter(faces.map(({ crimeType }) => crimeType), selected, step);
      if (index < 0) return; // 1면 앞 · 마지막 면 뒤에는 넘길 장이 없다
      const edge = button(step < 0 ? '‹' : '›', () => turnFace(step), `face-edge ${step < 0 ? 'is-prev' : 'is-next'}`);
      edge.tabIndex = -1;
      edge.setAttribute('aria-hidden', 'true');
      edge.title = `${faceName(faces[index])} 넘기기`;
      edges.append(edge);
    });
  }
  function turnFace(step) {
    const faces = shownFaces();
    const index = faceIndexAfter(faces.map(({ crimeType }) => crimeType), selected, step);
    if (index >= 0) switchTab(faces[index].crimeType, { turned: true });
  }

  // 오류에서도 탭은 그대로 둔다(예전에는 main 전체를 바꿔 다른 유형으로 갈 수 없었다, FE-102). 목록 자리에만 오류 상자를 둔다
  function showError(retry) {
    const box = element('section', 'message-panel');
    box.setAttribute('role', 'alert');
    box.append(element('p', '', '사건 목록을 불러오지 못했어요.'), element('p', 'muted', '네트워크 연결을 확인하고 다시 시도해 주세요.'), button('다시 시도', retry));
    main.replaceChildren(renderTabs(lastSummary), box);
    syncEdges(); // 오류 면에서도 앞 · 뒤 면으로 넘어갈 수 있다
    tipLine.textContent = '지면을 다시 불러오면 마음이 가는 기사를 눌러 볼 수 있어요.';
    tip.disabled = false;
    tip.classList.remove('is-waiting');
    root.classList.remove('is-loading');
  }

  // 기사 고르기: 지금 지면을 본뜬 한 장을 띄워 두고 사건 정보(S-20)로 간다. 사건 정보가 그려지면 이 기사의 제목이 그 제목 자리로 날아간다(articleZoom.js)
  function pick(item, target) {
    if (picking) return;
    picking = true;
    target.classList.add('is-picked');
    root.classList.add('is-picking');
    liftArticle(flyingTitle(target, item), { page: paper, scopeClass: 'case-list-page' });
    navigate('caseDetail', { caseId: item.caseId, from: 'paper' });
  }
  // 날아갈 제목: 누른 기사 안의 제목, 없으면(머리기사 단 · 사진) 같은 사건의 머리기사 배너 제목
  function flyingTitle(target, item) {
    return target.querySelector('h2, h3') ?? (mates.get(item.caseId) ?? []).map((node) => node.querySelector('h2, h3')).find(Boolean) ?? null;
  }
  // 기사 전체를 누를 수 있게 한다(마우스). 키보드 · 화면 낭독기는 기사 안의 "사건 펼쳐 보기" 버튼으로 고른다
  function pickable(node, item) {
    node.classList.add('is-pickable');
    // 같은 사건의 머리기사 제목 · 사진 · 기사 단은 한 건이므로 어느 쪽에 올려도 함께 강조한다(FE-102)
    const group = mates.get(item.caseId) ?? [];
    group.push(node);
    mates.set(item.caseId, group);
    const lightMates = () => group.forEach((other) => { if (other !== node) other.classList.add('is-mate-hover'); });
    const dimMates = () => group.forEach((other) => other.classList.remove('is-mate-hover'));
    node.addEventListener('mouseenter', lightMates);
    node.addEventListener('mouseleave', dimMates);
    node.addEventListener('focusin', lightMates); // 키보드로 제목에 가도 같은 사건이 함께 강조된다
    node.addEventListener('focusout', dimMates);
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
    // 진행 상태 도장은 분류명 줄 오른쪽 끝에 둔다. 화면 낭독기에는 "진행 중" · "완료" 글자 그대로 읽힌다
    if (stampOf(item)) node.append(stampMark(item));
    return node;
  }

  function photo(item) {
    const image = element('img', 'photo');
    useCommonPhotoOnError(image, Number(item?.caseId) || 0, undefined, commonCategoryOf(item)); // 썸네일 로딩 실패 시 범죄 분류에 맞는 공용 컷 (src보다 먼저)
    image.src = casePhoto(item); // 썸네일이 없으면 범죄 분류에 맞는 공용 재연 컷
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
    figure.append(photo(item), element('figcaption', 'caption', `${item.title} · 연출 이미지`));
    return pickable(figure, item);
  }

  function recommendedArticle(item) {
    const article = element('article', 'article');
    article.append(
      photo(item),
      element('p', 'caption', `${item.title} · 연출 이미지`),
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

  // ---- 빈 칸 채움 지면 (FE-118 · FE-119) ----
  // 한 면을 그리는 동안 쓰는 덱(utils/newspaperFaces.js createFillerDeck): 기사 칸 안의 빈 칸 · 면 아래 세로 여백이 같은 덱에서 뽑아 같은 지면이 되풀이되지 않는다
  let deck = createFillerDeck(0);
  function fillerOf({ kind, n }) {
    if (kind === 'ad') {
      const box = element('aside', 'filler filler-ad');
      box.setAttribute('aria-label', '광고');
      box.append(element('p', 'filler-label', '광고'), element('h3', 'filler-title', '나는 엄격한 판사일까, 관대한 판사일까?'),
        element('p', 'filler-text', '몇 가지 사건 질문에 답하면 나의 판결 성향을 알려 드려요.'),
        button('판결 성향 테스트 →', () => navigate('testPaper', { from: 'paper' }), 'start filler-go'));
      return box;
    }
    if (kind === 'photo') { // 사진 지면: 공용 재연 컷에 인쇄 망점을 씌운 큰 칸(높은 줄에만, 읽을거리 없이 그림 한 장)
      const box = element('figure', 'filler filler-photo');
      const frame = element('div', 'halftone');
      const image = element('img', 'photo');
      image.src = commonPhotoAt(n, PHOTO_ROLES[n % PHOTO_ROLES.length], PHOTO_CATEGORIES[n % PHOTO_CATEGORIES.length]);
      image.alt = '';
      image.loading = 'lazy';
      image.addEventListener('error', () => box.remove(), { once: true });
      frame.append(image);
      box.append(frame, element('figcaption', 'caption', '재연 일러스트 · 연출 이미지'));
      return box;
    }
    const box = element('aside', 'filler filler-fact');
    box.setAttribute('aria-label', '법정 상식');
    box.append(element('p', 'filler-label', '법정 상식'), element('p', 'filler-text', courtFacts[n % courtFacts.length]));
    return box;
  }
  // 기사 칸 안의 빈 칸 count개(둘 이상이면 맨 앞이 광고)
  const fillers = (count) => deck.take(count).map(fillerOf);

  // 면 맨 아래 세로 여백(FE-119): 최소 높이(신문 폭의 1.4배 · 화면보다 길게)보다 기사가 짧으면 남는 높이를 읽을거리 줄로 채운다.
  // 여백 칸(.face-fill)은 내용과 상관없이 남는 높이만큼만 차지하므로(flex-basis 0) 높이 · 폭을 재서 줄 수 · 칸 수를 정하고(planFiller),
  // 사진이 늦게 와 기사가 길어지거나 창 크기가 바뀌면 다시 짠다(ResizeObserver). 줄 높이는 남는 높이를 나눈 값이라 빈틈이 없다
  let fillWatch = null;
  function mountFill(fill, base) {
    fillWatch?.disconnect();
    fillWatch = null;
    if (typeof ResizeObserver === 'undefined') return;
    let last = '';
    const plan = () => {
      if (!active() || !fill.isConnected) { fillWatch?.disconnect(); return; }
      const space = fill.clientHeight - FILL_GAP; // 위 간격만큼 빼고 잰다
      const key = `${Math.round(space)}x${Math.round(fill.clientWidth)}`;
      if (key === last) return;
      last = key;
      const result = planFiller({ space, width: fill.clientWidth });
      if (!result) { fill.replaceChildren(); return; }
      const mine = base.fork(); // 다시 짜도 같은 지면이 나오게 그릴 때의 덱 상태에서 이어 뽑는다
      const grid = element('div', 'face-fill-grid');
      grid.style.gridTemplateColumns = `repeat(${result.columns}, minmax(0, 1fr))`;
      grid.style.gridTemplateRows = `repeat(${result.rows}, minmax(0, 1fr))`;
      result.kinds.forEach((kind) => grid.append(fillerOf(mine.next(kind))));
      fill.replaceChildren(grid);
    };
    fillWatch = new ResizeObserver(() => window.requestAnimationFrame(plan));
    fillWatch.observe(fill);
    window.requestAnimationFrame(plan);
  }

  // ---- 지면 ----
  // summary가 없으면(불러오는 중 · 처음부터 실패) 건수 없이 탭만 그린다. disabled면 누를 수 없다
  function renderTabs(summary, { disabled = false } = {}) {
    const zone = element('div', 'tabs-zone');
    const bar = element('div', 'tabs');
    bar.setAttribute('role', 'group');
    bar.setAttribute('aria-label', '신문 면');
    visibleFaces(tabs, summary?.byCrimeType, selected).forEach(({ crimeType, label, special }) => {
      // 1면은 9건만 싣고, 색인 · 성향 테스트 · 오피니언은 사건 수가 아니라 건수를 달지 않는다
      let count = null;
      if (summary && !special) count = crimeType ? summary.byCrimeType[crimeType] ?? 0 : Math.min(summary.total, FRONT_LIMIT);
      const tab = button(label, () => switchTab(crimeType), 'tab');
      tab.dataset.type = crimeType ?? '';
      if (count !== null) { // 건수를 글자로도 보인다(모바일 포함, 예전에는 낭독 이름에만 있었다)
        tab.append(element('span', 'tab-count', String(count)));
        tab.setAttribute('aria-label', `${label} ${count}건`);
      }
      tab.setAttribute('aria-pressed', String(crimeType === selected));
      if (disabled) { tab.disabled = true; }
      bar.append(tab);
    });
    zone.append(bar);
    return zone;
  }

  // 한 줄 6칸 격자 + 마지막 줄의 빈 칸 채움. 넓은 화면(6칸) 기준으로 만들고, 태블릿(3칸) · 모바일(2칸)에서 남는 칸은 CSS가 뒤에서부터 숨긴다(fill-3 · fill-2만 남긴다)
  function indexGrid(items) {
    const list = element('div', 'index-grid');
    items.forEach((item) => list.append(indexArticle(item)));
    const { six, three, two } = emptySlots(items.length);
    fillers(six).forEach((node, i) => {
      node.classList.add('article'); // 다른 사건 칸과 같은 칸 간격 · 구분선
      if (i < three) node.classList.add('fill-3');
      if (i < two) node.classList.add('fill-2');
      list.append(node);
    });
    return list;
  }

  // "이 면 더 보기": 같은 종이가 아래로 펼쳐지며 남은 기사가 나타난다(펼침, 가로 넘김과 방향이 다르다). 신문 최소 높이가 화면보다 길어(caseList.css) 늘어나는 모습은 대개 화면 밖이다
  function moreFold(items) {
    const fold = element('section', 'more-fold');
    const toggle = button('', () => {
      const open = !fold.classList.contains('is-open');
      fold.classList.toggle('is-open', open);
      toggle.setAttribute('aria-expanded', String(open));
      toggle.querySelector('.more-label').textContent = open ? '접기' : `${MORE_LABEL} · ${items.length}건`;
    }, 'more-toggle');
    toggle.append(element('span', 'more-label', `${MORE_LABEL} · ${items.length}건`), element('span', 'more-arrow', '▾'));
    toggle.setAttribute('aria-expanded', 'false');
    const body = element('div', 'more-body');
    body.id = `more-${selected ?? 'front'}`;
    toggle.setAttribute('aria-controls', body.id);
    const inner = element('div', 'more-inner');
    inner.append(indexGrid(items));
    body.append(inner);
    fold.append(toggle, body);
    return fold;
  }

  // 색인면: 전체 사건을 제목만 주르륵(제목 · 키워드로 거른다). 누르면 다른 면의 기사처럼 사건 정보로 간다
  function indexFace(cases) {
    const wrap = element('section', 'face-index');
    wrap.setAttribute('aria-label', '전체 사건 색인');
    const search = element('input', 'face-search');
    search.type = 'search';
    search.placeholder = '제목 · 키워드로 찾기';
    search.setAttribute('aria-label', '사건 제목 · 키워드 검색');
    const status = element('p', 'face-search-count');
    status.setAttribute('role', 'status');
    const list = element('ol', 'face-index-list');
    const rows = cases.map((item) => {
      const row = element('li', 'index-row');
      const square = element('span', `type-square type-${item.crimeType}`);
      square.setAttribute('aria-hidden', 'true');
      const stamp = stampOf(item);
      // 제목이 곧 사건으로 가는 버튼이다(키보드 · 낭독기). 줄 어디를 눌러도 같다. 이어 읽기 줄 "(3면에 계속)"은 색인에 두지 않는다
      const title = element('h3');
      const open = button(headlineOf(item), () => pick(item, row), 'row-title');
      open.setAttribute('aria-label', `${headlineOf(item)}, ${PICK_LABEL}`);
      title.append(open);
      row.append(square, element('span', 'row-category', item.crimeCategoryLabel), title);
      if (stamp) row.append(stampMark(item));
      row.dataset.search = [headlineOf(item), item.title, ...(item.keywords ?? []), item.crimeCategoryLabel].join(' ').toLowerCase();
      list.append(row);
      return pickable(row, item);
    });
    const showCount = (shown) => { status.textContent = shown ? `${shown}건` : '찾는 사건이 없어요.'; };
    showCount(rows.length);
    search.addEventListener('input', () => {
      const query = search.value.trim().toLowerCase();
      let shown = 0;
      rows.forEach((row) => { const match = !query || row.dataset.search.includes(query); row.hidden = !match; if (match) shown += 1; });
      showCount(shown);
    });
    wrap.append(search, status, list);
    return wrap;
  }

  // 성향 테스트 · 오피니언 면: 자리와 링크만 있는 껍데기(내용은 후속)
  function stubFace(face) {
    const box = element('section', `face-stub face-stub-${face.toLowerCase()}`);
    if (face === 'TEST') {
      box.setAttribute('aria-label', '판결 성향 테스트');
      box.append(element('p', 'filler-label', '성향 테스트'), element('h2', '', '나는 엄격한 판사일까, 관대한 판사일까?'),
        element('p', '', '몇 가지 사건 질문에 답하면 나의 판결 성향을 알려 드려요. 이 면에는 곧 테스트 소개가 실려요.'),
        button('판결 성향 테스트 하러 가기 →', () => navigate('testPaper', { from: 'paper' }), 'secondary-button'));
    } else {
      box.setAttribute('aria-label', '오피니언');
      box.append(element('p', 'filler-label', '오피니언'), element('h2', '', '편집부 사설 · 독자 의견'), element('p', '', '이 면은 준비 중이에요. 편집부 사설과 독자 의견이 실릴 자리예요.'));
    }
    return box;
  }

  function draw({ summary, cases }) {
    lastSummary = summary;
    root.classList.remove('is-loading');
    mates.clear(); // 아래에서 다시 만드는 영역으로 새로 모은다
    // 포스트잇 문구: 1면은 체험 가능 건수까지, 그 밖의 면은 그 면 사건만 받는 면이 있어 체험 가능 건수를 셀 수 없으니 전체 건수만. 오른쪽 귀는 어느 면에서나 "오늘의 사건 n건"만 채운다(포스트잇을 떼면 드러난다)
    tipLine.textContent = selected === undefined || !isCrimeFace(selected)
      ? tipMessage(summary.total, cases.filter((item) => !isSoon(item)).length)
      : `오늘 지면에 사건이 ${summary.total}건 실렸어요.`;
    earRight.replaceChildren(element('b', '', `오늘의 사건 ${summary.total}건`));
    tip.disabled = false;
    tip.classList.remove('is-waiting');

    const parts = [renderTabs(summary)];
    // 지금 체험할 수 있는 사건이 먼저, 그다음 참여 많은 순: 첫 사건이 머리기사, 다음 2건이 추천 사건, 나머지는 다른 사건
    // (눌러 보라고 권하는 자리인 머리기사 · 추천에 "준비 중" 사건이 앞서지 않게, FE-102)
    const ordered = [...cases].sort((a, b) => Number(isSoon(a)) - Number(isSoon(b)) || b.participantCount - a.participantCount);
    deck = createFillerDeck(tabs.findIndex(({ crimeType }) => crimeType === selected) * 3); // 면마다 다른 법정 상식 · 사진부터
    if (selected === 'INDEX') {
      parts.push(indexFace(ordered));
    } else if (selected === 'TEST' || selected === 'OPINION') {
      parts.push(stubFace(selected));
    } else if (!cases.length) {
      parts.push(element('p', 'empty', '해당하는 사건이 없어요.'));
    } else {
      const { lead, recommended, rest, more } = layoutCases(ordered, { front: selected === undefined }); // 1면은 9건만, 유형 면은 넘치는 기사를 더보기로

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
        if (recommended.length < 2) { // 추천이 한 건뿐이면 비는 반쪽에 작은 지면 세 개를 세로로 쌓는다
          const column = element('div', 'article filler-column');
          column.append(...fillers(FILLER_STRIP));
          recs.append(column);
        }
        parts.push(recs);
      }

      if (rest.length) {
        const index = element('section', 'index');
        index.setAttribute('aria-label', '이번 호 다른 사건');
        const head = element('div', 'index-head');
        head.append(element('h2', '', '이번 호 다른 사건'), element('span', '', `${rest.length + more.length}건 · 참여 많은 순`));
        index.append(head, indexGrid(rest));
        if (more.length) index.append(moreFold(more));
        parts.push(index);
      }
    }
    const fill = element('div', 'face-fill'); // 면 아래 세로 여백 칸(내용은 mountFill이 높이를 재서 채운다)
    fill.setAttribute('aria-label', '읽을거리');
    main.replaceChildren(...parts, fill);
    mountFill(fill, deck.fork());
    syncEdges();
    paper.classList.toggle('inner-page', selected !== undefined); // 1면만 1면, 나머지 면(유형 면 · 색인 · 성향 테스트 · 오피니언)은 안쪽 면(오른쪽 면)
  }

  let ready = false; // 처음 목록을 받은 뒤에야 탭이 눌린다(받는 도중 누르면 두 요청이 엇갈린다)
  let switching = 0; // load · switchTab이 함께 쓰는 요청 번호: 더 새 요청이 있으면 늦게 온 응답은 버린다(재시도 중 탭을 눌러도 이전 유형이 덮지 않게)
  async function load(crimeType) {
    const mine = ++switching;
    selected = crimeType;
    try {
      const data = await api.getCases(apiTypeOf(crimeType));
      if (active() && mine === switching) draw(data);
    } catch (error) {
      if (active() && mine === switching) showError(() => load(crimeType));
    } finally {
      ready = true;
    }
  }

  // 면 바꾸기: 새 목록을 받으면 지금 지면을 한 장으로 떼어 넘기고, 그 밑에 새 목록을 바로 그린다(빠르게 연달아 눌러도 마지막 것만 남는다).
  // 뒤 면으로는 앞으로 넘기고, 앞 면으로는 거꾸로 넘어온다(back). turned: 탭이 아니라 키보드 · 밀기로 넘겼다 — 새 면은 맨 위부터 읽는다
  async function switchTab(crimeType, { turned = false } = {}) {
    if (!ready || picking || crimeType === selected) return;
    const mine = ++switching;
    const order = shownFaces().map((face) => face.crimeType);
    const back = order.indexOf(crimeType) < order.indexOf(selected);
    selected = crimeType;
    saveTypeToUrl(crimeType); // 새로고침 · 사건 정보에 다녀오기에서도 같은 면(FE-102)
    // 키보드로 탭을 눌렀으면 새로 그린 같은 탭으로 포커스를 돌려준다
    const keepFocus = document.activeElement?.classList?.contains('tab');
    let result = null;
    try {
      result = await api.getCases(apiTypeOf(crimeType));
    } catch (error) {
      result = null;
    }
    if (!active() || mine !== switching) return;
    turnPage(paper, { scopeClass: 'case-list-page', duration: TAB_TURN_MS, back, draw: () => {
      if (turned) window.scrollTo(0, 0); // 넘기는 한 장이 화면을 덮고 있는 동안 올린다
      if (result) draw(result);
      else showError(() => load(crimeType));
    } });
    if (keepFocus) main.querySelector(`.tab[data-type="${crimeType ?? ''}"]`)?.focus({ preventScroll: true });
  }

  const loaded = load(selected);
  if (root.classList.contains('is-spreading')) spread(loaded);
  else if (ghost) rise(loaded);
  // 뒤집고 왔으면 기사와 첫 사진이 다 준비된 뒤에 한 번에 페이드인한다(늦게 온 기사가 따로 튀어나오지 않게)
  // 기사 준비가 어떻게 끝나든(성공 · 실패 · 시간 초과) 덮고 있는 빈 종이는 반드시 걷는다
  if (root.classList.contains('is-unflipping')) drawn(loaded).catch(() => {}).finally(() => unflip(handed));

  await loaded;
}
