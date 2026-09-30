import { button, element, renderSiteFooter, renderSiteHeader } from '../../components/siteLayout.js';

const chips = [
  { crimeType: undefined, label: '전체' },
  { crimeType: 'MURDER', label: '살인' },
  { crimeType: 'FRAUD', label: '사기' },
  { crimeType: 'INJURY', label: '상해' },
];
const difficultyLabels = { LOW: '쉬움', MID: '보통', HIGH: '어려움' };
// API 명세 1-6 "보낼 화면" 표. 새로 시작하면 S-03, 진행 중이면 현재 단계, 완료면 S-09.
const screenByStatus = {
  STARTED: 'overview', PRE_JUDGED: 'review', REVIEWING: 'review', REVIEWED: 'summary',
  VERDICT_CONFIRMED: 'ai', AI_REVEALED: 'court', COMPLETED: 'comparison',
};

// S-02 사건 목록
export async function renderCaseListPage(container, { api, navigate }) {
  const root = element('div', 'case-list-page');
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  let selected;
  let starting = false;

  const main = element('main', 'list-main');
  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }), main, renderSiteFooter());

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
      main.querySelector('.case-grid')?.before(notice);
    } finally {
      starting = false;
    }
  }

  function renderCard(item) {
    const card = element('li', 'case-card');
    const thumb = element('div', `case-thumb thumb-${item.crimeType}`);
    if (item.thumbnailUrl) {
      const image = element('img');
      image.src = item.thumbnailUrl;
      image.alt = '';
      thumb.append(image);
    } else {
      thumb.append(element('span', '', item.crimeCategoryLabel));
    }
    const badges = element('div', 'badges');
    badges.append(
      element('span', 'badge badge-type', item.crimeCategoryLabel),
      element('span', 'badge', `난이도 ${difficultyLabels[item.difficulty] ?? item.difficulty}`),
      element('span', 'badge', `약 ${item.estimatedMinutes}분`),
    );
    const keywords = element('ul', 'keywords');
    keywords.setAttribute('aria-label', '키워드');
    item.keywords.forEach((keyword) => keywords.append(element('li', '', `#${keyword}`)));
    const footer = element('div', 'card-footer');
    const startButton = button('체험 시작', () => start(item.caseId, startButton), 'primary-button');
    startButton.setAttribute('aria-label', `${item.title} 체험 시작`);
    footer.append(element('span', 'participants', `참여 ${item.participantCount.toLocaleString('ko-KR')}명`), startButton);
    const body = element('div', 'case-body');
    body.append(badges, element('h3', '', item.title), element('p', 'case-intro', item.shortIntro), keywords, footer);
    card.append(thumb, body);
    return card;
  }

  function draw({ summary, cases }) {
    const heading = element('div', 'list-heading');
    heading.append(element('h1', '', '사건 목록'), element('p', 'muted', `체험할 수 있는 사건 ${summary.total}건`));
    const chipBar = element('div', 'chip-bar');
    chipBar.setAttribute('role', 'group');
    chipBar.setAttribute('aria-label', '범죄 유형');
    chips.forEach(({ crimeType, label }) => {
      const count = crimeType ? summary.byCrimeType[crimeType] : summary.total;
      const chip = button(`${label} ${count}`, () => load(crimeType), 'chip');
      chip.setAttribute('aria-pressed', String(crimeType === selected));
      chipBar.append(chip);
    });
    const list = element('ul', 'case-grid');
    cases.forEach((item) => list.append(renderCard(item)));
    if (!cases.length) list.append(element('li', 'empty', '해당하는 사건이 없어요.'));
    main.replaceChildren(heading, chipBar, list);
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
