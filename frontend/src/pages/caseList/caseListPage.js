import { button, element, renderSiteFooter } from '../../components/siteLayout.js';
import { casePhoto } from '../../utils/newspaper.js';
import { newspaperMasthead } from '../../components/newspaperFront.js';
import { turnPage } from '../../utils/pageTurn.js';

// S-02 사건 목록 — 신문 1면 (FE-18, 시안 v12 + 배치 조정). 유형 필터 · 오류 처리 로직은 FE-4 · FE-11과 같다.
// 흐름 목업 ②(FE-32): 책상에서 집어 든 신문이다. 위에 남불의 안내와 "신문 내려놓기"(S-01)가 있고, 기사를 누르면
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
const statusViewOf = (item) => STATUS_VIEW[item.myStatus] ?? NOT_STARTED;
const PICK_LABEL = '사건 펼쳐 보기';
const JUMP_LABEL = '3면에 계속'; // 신문의 이어 읽기 줄(jump line). 누르면 사건 지면(S-20, 3면)으로 간다
// shortcut: 캐릭터 얼굴은 원본 이미지 레포 PNG를 직접 읽는다(무대와 같음), 웹 webp 변환(WBS 7.6.8) 때 public 경로로 옮긴다
const NAMBUL_FACE = 'https://raw.githubusercontent.com/peanutboi710/NaeLawNamBul_Images/main/character/cuts/nambul/icon/nambul_icon_face-happy.png';
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const headlineOf = (item) => item.headline || item.title;
function dense(paragraphs) {
  const node = element('div', 'dense');
  paragraphs.forEach((text) => node.append(element('p', '', text)));
  return node;
}

// S-02 사건 목록
export async function renderCaseListPage(container, { api, navigate, from }) {
  const root = element('div', 'case-list-page');
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  let selected;
  let picking = false;
  // 책상에서 신문을 집어 들고 왔으면 지면이 한 번 다가오며 나타난다. 새로고침 · 뒤로 가기로는 되풀이하지 않는다
  if (from === 'desk') {
    history.replaceState({ ...history.state, extras: {} }, '');
    if (!reducedMotion()) root.classList.add('is-arriving');
  }

  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  const paper = element('div', 'paper');
  const earRight = element('div', 'ear ear-right');
  const main = element('main', 'front');
  const paperNote = element('p', 'paper-note', '이 지면의 사건과 기사는 판결 체험을 위해 재구성한 것이며, 사진은 AI로 생성한 연출 이미지입니다.');
  paper.append(newspaperMasthead({ navigate, earRight }), main, paperNote, renderSiteFooter());

  // 남불의 안내 + 신문 내려놓기(책상으로). 지면 위쪽 가장자리에 붙인 메모지
  const tip = element('div', 'paper-tip');
  const face = element('img');
  face.src = NAMBUL_FACE;
  face.alt = '';
  face.addEventListener('error', () => face.remove(), { once: true });
  const tipText = element('p', 'paper-tip-text');
  const tipLine = element('span', '', '마음이 가는 기사를 눌러 보세요!');
  tipText.append(element('b', '', '남불'), tipLine);
  tip.append(face, tipText, button('신문 내려놓기', () => navigate('landing'), 'paper-tip-back'));
  paper.prepend(tip); // 남불의 안내는 지면 위쪽에 붙인 메모(지면과 함께 움직인다)
  root.append(desk, paper);

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

  // 사진이 없는 작은 기사: 회색 임시 그림 대신 인쇄 망점 위에 키워드를 크게 얹은 칸(유형 색으로 망점을 물들인다)
  function keywordPanel(item) {
    const panel = element('div', `photo keyword-panel type-${item.crimeType}`);
    panel.setAttribute('aria-hidden', 'true'); // 키워드는 기사 아래 줄에서 읽힌다
    panel.append(element('span', 'keyword-panel-word', item.keywords?.[0] ?? item.crimeCategoryLabel));
    return panel;
  }

  function meta(item, withTags) {
    const node = element('p', 'meta');
    if (withTags && item.keywords?.length) node.append(element('span', 'tags', item.keywords.map((keyword) => `#${keyword}`).join(' ')));
    node.append(`참여 ${item.participantCount.toLocaleString('ko-KR')}명`);
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
    // 사진이 있으면 사진, 없으면 키워드 망점 칸(작은 기사 6건에 같은 공용 사진이 되풀이되지 않게)
    article.append(item.thumbnailUrl ? photo(item) : keywordPanel(item), kicker(item), element('h3', '', headlineOf(item)), dense([item.article?.[0] ?? item.shortIntro]), meta(item),
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
    earRight.replaceChildren(element('b', '', '오늘의 지면'),
      `법정에 오른 사건 ${summary.total}건. ${byTypeText(summary)}. 형량은 체험을 마친 뒤 공개됩니다.`);

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

  await load(undefined);
}
