import { button, element, renderSiteFooter } from '../../components/siteLayout.js';
import { screenByStatus } from '../../utils/screenByStatus.js';

// S-02 사건 목록 — 신문 1면 (FE-18, 시안 v12 + 배치 조정). 체험 시작 · 유형 필터 · 오류 처리 로직은 FE-4 · FE-11과 같다.
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
// thumbnailUrl이 없을 때 쓰는 유형별 연출 이미지 (frontend/public/images)
const fallbackImages = {
  MURDER: '/images/case-murder.svg',
  FRAUD: '/images/case-fraud.svg',
  INJURY: '/images/case-injury.svg',
};
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
const headlineOf = (item) => item.headline || item.title;
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function todayLabel() {
  const now = new Date();
  return `${now.getFullYear()}년 ${now.getMonth() + 1}월 ${now.getDate()}일 ${WEEKDAYS[now.getDay()]}요일`;
}

function dense(paragraphs) {
  const node = element('div', 'dense');
  paragraphs.forEach((text) => node.append(element('p', '', text)));
  return node;
}

// S-02 사건 목록
export async function renderCaseListPage(container, { api, navigate }) {
  const root = element('div', 'case-list-page');
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  let selected;
  let starting = false;

  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  const paper = element('div', 'paper');
  const earRight = element('div', 'ear ear-right');
  const main = element('main', 'front');
  const paperNote = element('p', 'paper-note', '이 지면의 사건과 기사는 판결 체험을 위해 재구성한 것이며, 사진은 AI로 생성한 연출 이미지입니다.');
  paper.append(renderMasthead(), main, paperNote, renderSiteFooter());
  root.append(desk, paper);

  function renderMasthead() {
    const header = element('header', 'masthead');
    const infoRow = element('div', 'info-row');
    const links = element('nav', 'info-links');
    links.setAttribute('aria-label', '보조 메뉴');
    links.append(
      button('← 처음으로', () => navigate('landing'), 'paper-link'),
      button('서비스 소개', () => navigate('landing', { section: 'intro' }), 'paper-link'),
    );
    infoRow.append(element('span', '', '판결 체험 신문'), links);

    const earLeft = element('div', 'ear ear-left');
    earLeft.append('“모든 판결에는', element('br'), '이유가 있다”', element('small', '', '사건을 읽다. 판단을 묻다.'));
    const brand = element('div', 'brand');
    const title = element('h1');
    title.setAttribute('aria-label', '내Law남불 사건 목록');
    title.append(element('span', '', '내'), element('span', 'law', 'Law'), element('span', '', '남불'));
    brand.append(title);
    const mast = element('div', 'mast');
    mast.append(earLeft, brand, earRight);

    const dateline = element('div', 'dateline');
    dateline.append(element('span', '', '내Law남불'), element('span', '', todayLabel()), element('span', '', '참여 무료'));
    header.append(infoRow, mast, dateline);
    return header;
  }

  function showError(retry) {
    const box = element('section', 'message-panel');
    box.setAttribute('role', 'alert');
    box.append(element('p', '', '사건 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'), button('다시 시도', retry));
    main.replaceChildren(box);
  }

  async function start(caseId, trigger) {
    if (starting) return;
    starting = true;
    trigger.disabled = true;
    trigger.setAttribute('aria-busy', 'true');
    try {
      const experience = await api.startExperience(caseId);
      if (active()) navigate(screenByStatus[experience.status] ?? 'overview', { caseId });
    } catch (error) {
      if (!active()) return;
      trigger.disabled = false;
      trigger.removeAttribute('aria-busy');
      const notice = main.querySelector('.start-error') ?? element('p', 'start-error');
      notice.setAttribute('role', 'alert');
      notice.textContent = error?.code === 'CASE_NOT_FOUND'
        ? '사건을 찾을 수 없어요. 목록을 새로 불러와 주세요.'
        : '체험을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.';
      main.querySelector('.tabs-zone')?.after(notice);
    } finally {
      starting = false;
    }
  }

  // ---- 기사 조각 ----
  function kicker(item, prefix) {
    const node = element('div', 'kicker');
    if (prefix) node.append(element('span', '', prefix), element('span', '', '·'));
    const square = element('span', `type-square type-${item.crimeType}`);
    square.setAttribute('aria-hidden', 'true');
    node.append(square, element('span', '', item.crimeCategoryLabel));
    return node;
  }

  function photo(item) {
    const image = element('img', 'photo');
    image.src = item.thumbnailUrl || fallbackImages[item.crimeType] || fallbackImages.MURDER;
    image.alt = ''; // 연출 이미지라 읽지 않는다 (사건 정보는 제목 · 소개문에 있다)
    image.loading = 'lazy';
    return image;
  }

  function meta(item, withTags) {
    const node = element('p', 'meta');
    if (withTags && item.keywords?.length) node.append(element('span', 'tags', item.keywords.map((keyword) => `#${keyword}`).join(' ')));
    node.append(`참여 ${item.participantCount.toLocaleString('ko-KR')}명`);
    return node;
  }

  function startButton(item, className = 'start') {
    const node = button('체험 시작', () => start(item.caseId, node), className);
    node.setAttribute('aria-label', `${item.title} 체험 시작`);
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
    return article;
  }

  function leadPhoto(item) {
    const figure = element('figure', 'lead-photo');
    figure.append(photo(item), element('figcaption', 'caption', '사건 이해를 돕기 위한 연출 이미지'));
    return figure;
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
    return article;
  }

  function indexArticle(item) {
    const article = element('article', 'article');
    article.append(photo(item), kicker(item), element('h3', '', headlineOf(item)), dense([item.article?.[0] ?? item.shortIntro]), meta(item),
      startButton(item));
    return article;
  }

  // ---- 지면 ----
  function renderTabs(summary) {
    const zone = element('div', 'tabs-zone');
    const bar = element('div', 'tabs');
    bar.setAttribute('role', 'group');
    bar.setAttribute('aria-label', '범죄 유형');
    tabs.forEach(({ crimeType, label }) => {
      const count = crimeType ? summary.byCrimeType[crimeType] : summary.total;
      const tab = button(label, () => load(crimeType), 'tab');
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

  await load(undefined);
}
