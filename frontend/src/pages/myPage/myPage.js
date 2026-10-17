import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { renderToleranceScale } from '../../components/toleranceScale.js';
import { screenByStatus } from '../../utils/screenByStatus.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { imageOf } from '../../components/stage.js';
import { AXES } from '../../utils/personalityAxes.js';
import { axisSeries, biggestMove, collectionOf, trailLeft, trailSummary } from '../../utils/myPageView.js';
import { BOOKMARK_CHARACTERS, BOOKMARK_NAMES, bookmarkFace, getBookmarkCharacter, setBookmarkCharacter } from '../../utils/bookmarkCharacter.js';
import { SCRIPTS } from '../../content/scripts.js';

// S-17 마이페이지 (WBS 7.6.6). GET /my-page 한 번 호출로 받고, 기록이 없어도 404가 아니라 빈 200이다(명세 P-5).
// 메인 책상의 커피잔으로 들어온다(FE-44): 커다란 커피잔이 날아와 페이드아웃하는 사이(utils/coffeeDive.js) 이 화면(from: 'mug')으로 넘어온다.
// 첫 화면은 프로필(책갈피 캐릭터 · 내 성향) + 네 줄(판결 성향 · 변화 흐름 · 만난 모습 · 체험한 사건)이고, 줄을 누르면 그 항목 화면(view)으로 들어간다.
// 항목 화면은 주소가 같고 view만 다르다(뒤로 가기로 첫 화면에 돌아온다).
// 종합 · 변화 흐름은 체험 → 성향 저장(7.6.7, BE-64) 뒤부터 채워지므로 그 전에는 null · 빈 배열로 와서 안내만 보인다.
// 로그인 전에는 익명 ID 기준이다(REQ-108).
const STATUS_LABELS = { COMPLETED: '체험 완료' };
const VIEWS = {
  judgment: '판결에서 드러난 성향',
  trail: '성향 변화 흐름',
  collection: '만난 모습',
  cases: '체험한 사건',
};
let cache = null; // 마지막 응답. 항목 화면을 오갈 때 바로 그리고 뒤에서 새로 받는다

const svg = (path, className) => {
  const node = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  node.setAttribute('viewBox', '0 0 16 16');
  node.setAttribute('aria-hidden', 'true');
  node.setAttribute('focusable', 'false');
  node.classList.add(className);
  const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  line.setAttribute('d', path);
  node.append(line);
  return node;
};
const chevron = () => svg('M6 3l5 5-5 5', 'my-chevron');
const backArrow = () => svg('M10 3L5 8l5 5', 'my-chevron');

function picture(src, alt, className = '') {
  const image = element('img', className);
  image.src = src;
  image.alt = alt;
  image.loading = 'lazy';
  image.addEventListener('error', () => image.classList.add('is-missing'), { once: true });
  return image;
}

// ── 첫 화면 ──

// 프로필: 잔 테두리 안의 책갈피 캐릭터 + 내 성향(테스트 결과). 결과가 없으면 테스트를 권한다
function renderProfile(data, navigate, go) {
  const section = element('section', 'my-profile');
  const who = getBookmarkCharacter();
  const cup = element('div', 'my-cup');
  cup.append(picture(bookmarkFace(who), `책갈피 캐릭터 ${BOOKMARK_NAMES[who]}`, 'my-cup-face'));
  const change = button('책갈피 바꾸기', () => go('collection'), 'my-cup-change');
  const side = element('div', 'my-cup-wrap');
  side.append(cup, change);

  const text = element('div', 'my-profile-text');
  if (data.personality) {
    const { type } = data.personality;
    text.append(
      element('h2', 'my-type-name', type.name),
      element('p', 'my-type-oneliner', type.oneLiner),
      element('p', 'my-type-tolerance', '관용 지수'),
      renderToleranceScale(type.tolerance),
    );
    const actions = element('div', 'my-actions');
    actions.append(
      button('결과 자세히 보기', () => navigate('testResult'), 'primary-button'),
      button('다시 하기', () => navigate('test'), 'secondary-button'),
    );
    text.append(actions);
  } else {
    text.append(
      element('h2', 'my-type-name', '아직 내 판결 성향을 몰라요'),
      element('p', 'my-type-oneliner', '성향 테스트는 3분이면 끝나요. 잘잘못을 따질 때 내가 무엇을 중요하게 보는지 알려 줘요.'),
    );
    const actions = element('div', 'my-actions');
    actions.append(button('성향 테스트 하러 가기', () => navigate('test'), 'primary-button'));
    text.append(actions);
  }
  section.append(side, text);
  return section;
}

// 미리보기용 작은 막대 한 줄(점 하나 또는 번호 점 여러 개)
function miniTrack(points, numbered) {
  const track = element('span', 'my-mini-track');
  points.forEach((point, index) => {
    const dot = element('span', numbered && index === points.length - 1 ? 'my-mini-dot is-latest' : 'my-mini-dot', numbered ? String(point.order) : undefined);
    if (numbered) dot.classList.add('is-numbered');
    dot.style.left = `${trailLeft(point.ratio)}%`;
    track.append(dot);
  });
  return track;
}

// spoken: 미리보기에만 보이는 값(건수 등)을 스크린리더에도 읽어 준다(미리보기는 aria-hidden)
function row(view, sub, preview, go, spoken) {
  const item = element('li');
  const open = element('button', 'my-row');
  open.type = 'button';
  const text = element('span', 'my-row-text');
  text.append(element('strong', '', VIEWS[view]), element('span', 'my-row-sub', sub));
  if (spoken) text.append(element('span', 'visually-hidden', `, ${spoken}`));
  const look = element('span', 'my-row-preview');
  look.setAttribute('aria-hidden', 'true');
  if (preview) look.append(preview);
  open.append(text, look, chevron());
  open.addEventListener('click', () => go(view));
  item.append(open);
  return item;
}

function renderRows(data, go) {
  const list = element('ul', 'my-rows');
  const judgment = data.judgmentPersonality;
  const history = data.history ?? [];
  const experiences = data.experiences ?? [];

  let judgmentPreview = null;
  if (judgment) {
    judgmentPreview = element('span', 'my-mini-bars');
    AXES.forEach((axis) => {
      const ratio = judgment.axisScores?.[axis.key];
      judgmentPreview.append(miniTrack(typeof ratio === 'number' ? [{ ratio }] : [], false));
    });
  }
  list.append(row('judgment', judgment ? `체험 ${judgment.experienceCount}건의 평균` : '사건을 체험하면 판결에서 무엇을 무겁게 봤는지 쌓여요', judgmentPreview, go));

  const move = biggestMove(history, AXES);
  const trailSub = move ? `${move.axis.label}: ${trailSummary(move.points, move.axis)}`
    : history.length === 0 ? '사건을 하나 마치면 첫 점이 찍혀요' : '한 건을 더 체험하면 움직임이 보여요';
  list.append(row('trail', trailSub, move ? miniTrack(move.points, true) : null, go));

  const groups = collectionOf(experiences, SCRIPTS);
  const total = groups.reduce((sum, group) => sum + group.cast.length + group.items.length, 0);
  let thumbs = null;
  if (total > 0) {
    thumbs = element('span', 'my-thumbs');
    const firsts = groups.flatMap((group) => [
      ...group.cast.map(({ who, role }) => ({ src: imageOf(who, `role:${role}`), cast: true })),
      ...group.items.map(({ src }) => ({ src, cast: false })),
    ]).slice(0, 4);
    firsts.forEach(({ src, cast }) => thumbs.append(picture(src, '', cast ? 'is-cast' : '')));
  }
  list.append(row('collection', total > 0 ? `${total}가지를 모았어요. 책갈피 캐릭터도 여기서 골라요` : '체험을 마치면 만난 모습이 모여요. 책갈피 캐릭터도 여기서 골라요', thumbs, go));

  const latest = [...experiences].sort((a, b) => String(b.updatedAt ?? '').localeCompare(String(a.updatedAt ?? '')))[0];
  list.append(row('cases', latest ? `최근: ${latest.title}` : '아직 체험한 사건이 없어요', element('span', 'my-count', `${experiences.length}건`), go, `${experiences.length}건`));
  return list;
}

// ── 항목 화면 ──

function renderJudgment(judgment) {
  const box = element('div', 'my-detail');
  if (!judgment) {
    box.append(element('p', 'my-hint', '사건을 체험하면 판결에서 무엇을 무겁게 봤는지 여기에 쌓여요.'));
    return box;
  }
  if (judgment.type) box.append(element('p', 'my-judgment-type', judgment.type.name));
  box.append(element('p', 'my-hint', `첫 회차 체험 ${judgment.experienceCount}건의 평균이에요.`));
  const list = element('ul', 'axis-list');
  AXES.forEach((axis) => list.append(renderAxisRow(axis, judgment.axisScores?.[axis.key])));
  box.append(list);
  return box;
}

// 성향 변화 흐름 — 축마다 체험 번호가 붙은 점이 엄격 ↔ 관대 막대 위를 옮겨 간다
function trailRow(axis, points) {
  const item = element('li', 'axis-row trail-row');
  item.append(element('p', 'axis-label', axis.label));
  if (points.length === 0) {
    item.append(element('p', 'axis-empty', '체험 자료가 아직 없어요'));
    return item;
  }
  const ends = element('div', 'axis-ends');
  ends.append(element('span', '', axis.strict.name), element('span', '', axis.lenient.name));
  const track = element('div', 'trail-track');
  track.setAttribute('role', 'img');
  track.setAttribute('aria-label', points.map((point) => `${point.order}번째 체험 ${trailLeft(point.ratio)}%`).join(', ') + ` (0%는 ${axis.strict.name}, 100%는 ${axis.lenient.name})`);
  points.forEach((point, index) => {
    const dot = element('span', index === points.length - 1 ? 'trail-dot is-latest' : 'trail-dot', String(point.order));
    dot.style.left = `${trailLeft(point.ratio)}%`;
    track.append(dot);
  });
  item.append(ends, track);
  const summary = trailSummary(points, axis);
  if (summary) item.append(element('p', 'trail-summary', summary));
  return item;
}

function renderTrail(history) {
  const box = element('div', 'my-detail');
  if (history.length === 0) {
    box.append(element('p', 'my-hint', '사건을 하나 마치면 첫 점이 찍혀요.'));
    return box;
  }
  box.append(element('p', 'my-hint', history.length === 1 ? '한 건을 더 체험하면 움직임이 보여요. 번호는 체험한 순서예요.' : '번호는 체험한 순서이고, 가장 최근 점이 진하게 보여요.'));
  const list = element('ul', 'axis-list');
  AXES.forEach((axis) => list.append(trailRow(axis, axisSeries(history, axis.key))));
  box.append(list);
  return box;
}

// 책갈피 캐릭터 고르기: 라디오 두 개. 고르면 바로 저장되고 프로필 · 판결 체험 책갈피에 쓰인다
function renderBookmarkPicker() {
  const field = element('fieldset', 'my-bookmark');
  field.append(element('legend', '', '책갈피 캐릭터'));
  const current = getBookmarkCharacter();
  BOOKMARK_CHARACTERS.forEach((who) => {
    const label = element('label', 'my-bookmark-option');
    const input = element('input');
    input.type = 'radio';
    input.name = 'bookmark-character';
    input.value = who;
    input.checked = who === current;
    input.addEventListener('change', () => setBookmarkCharacter(who));
    label.append(input, picture(bookmarkFace(who), ''), element('span', '', BOOKMARK_NAMES[who]));
    field.append(label);
  });
  field.append(element('p', 'my-hint', '판결 체험에서 읽던 자리를 표시하는 책갈피에 이 얼굴이 꽂혀요.'));
  return field;
}

// 컬렉션 — 체험을 마친 사건마다 만난 모습(역할 의상)과 사건 일러스트. 체험하지 않은 사건은 나오지 않는다
function renderCollection(experiences) {
  const box = element('div', 'my-detail');
  box.append(renderBookmarkPicker());
  const groups = collectionOf(experiences, SCRIPTS);
  if (groups.length === 0) {
    box.append(element('p', 'my-hint', '사건 체험을 마치면 그 사건에서 만난 모습과 일러스트가 여기에 모여요.'));
    return box;
  }
  groups.forEach((group) => {
    const part = element('section', 'collection-group');
    part.append(element('h2', 'collection-title', group.title));
    const grid = element('ul', 'collection-grid');
    group.cast.forEach(({ who, role, label }) => {
      const card = element('li', 'collection-card is-cast');
      card.append(picture(imageOf(who, `role:${role}`), ''), element('p', 'collection-label', label)); // 이름은 캡션이 읽는다
      grid.append(card);
    });
    group.items.forEach((item) => {
      const card = element('li', 'collection-card');
      card.append(picture(item.src, `${group.title} ${item.label} 일러스트`), element('p', 'collection-label', item.label));
      grid.append(card);
    });
    part.append(grid);
    box.append(part);
  });
  return box;
}

function renderCases(experiences, navigate) {
  const box = element('div', 'my-detail');
  if (experiences.length === 0) {
    const empty = element('div', 'my-empty');
    empty.append(element('p', '', '아직 체험한 사건이 없어요.'), button('사건 목록 보기', () => navigate('list'), 'secondary-button'));
    box.append(empty);
    return box;
  }
  const list = element('ul', 'my-cases');
  experiences.forEach((item) => {
    const line = element('li', 'my-case');
    const info = element('div');
    info.append(element('p', 'my-case-title', item.title), element('p', 'my-case-status', STATUS_LABELS[item.status] ?? '체험 중'));
    const label = item.status === 'COMPLETED' ? '결과 다시 보기' : '이어서 하기';
    line.append(info, button(label, () => navigate(screenByStatus[item.status] ?? 'list', { caseId: item.caseId }), 'secondary-button'));
    list.append(line);
  });
  box.append(list);
  return box;
}

function renderView(view, data, navigate, go) {
  const head = element('div', 'my-view-head');
  const back = element('button', 'my-back');
  back.type = 'button';
  back.append(backArrow(), '마이페이지');
  back.addEventListener('click', () => go(null));
  const title = element('h1', '', VIEWS[view]);
  title.tabIndex = -1;
  head.append(back, title);
  const body = {
    judgment: () => renderJudgment(data.judgmentPersonality),
    trail: () => renderTrail(data.history ?? []),
    collection: () => renderCollection(data.experiences),
    cases: () => renderCases(data.experiences ?? [], navigate),
  }[view]();
  return [head, body];
}

export async function renderMyPage(container, { api, navigate, view, from }) {
  const root = element('div', 'my-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }));
  const main = element('main', 'my-main');
  root.append(main);
  const shown = VIEWS[view] ? view : null;
  const go = (next) => navigate('me', next ? { view: next } : {});

  // 커피잔에서 왔으면 표시를 지운다(새로고침 · 뒤로 가기로는 되풀이하지 않는다). 눌렀던 커피잔이 사라졌으니 화면이 그려진 뒤 포커스를 제목으로 옮긴다
  let arrived = false;
  if (from === 'mug') {
    history.replaceState({ ...history.state, extras: shown ? { view: shown } : {} }, '');
    arrived = true;
  }
  const reveal = () => {
    if (!arrived) return;
    arrived = false;
    main.querySelector('h1')?.focus({ preventScroll: true });
  };

  const paint = (data) => {
    if (shown) {
      main.replaceChildren(...renderView(shown, data, navigate, go));
      return;
    }
    const title = element('h1', 'visually-hidden', '마이페이지');
    title.tabIndex = -1;
    const note = element('p', 'my-note', '로그인하기 전에는 이 브라우저(익명 ID)를 기준으로 기록해요. 쿠키를 지우면 기록을 찾을 수 없어요.');
    main.replaceChildren(title, renderProfile(data, navigate, go), renderRows(data, go), note);
  };

  if (cache) paint(cache);
  else {
    const loading = element('p', 'message-panel', '불러오는 중이에요.');
    loading.setAttribute('role', 'status');
    main.append(loading);
  }
  if (shown && !from) {
    window.scrollTo(0, 0);
    main.querySelector('h1')?.focus({ preventScroll: true });
  }

  let data;
  try {
    data = await api.getMyPage();
  } catch {
    if (!active()) return;
    reveal();
    if (cache) return; // 지난 기록은 그대로 보여 준다
    const panel = element('div', 'message-panel');
    panel.setAttribute('role', 'alert');
    panel.append(element('p', '', '마이페이지를 불러오지 못했어요.'), button('다시 시도', () => navigate('me', shown ? { view: shown } : {}, { replace: true }), 'primary-button'));
    main.replaceChildren(panel);
    return;
  }
  if (!active()) return;
  const changed = JSON.stringify(cache) !== JSON.stringify(data);
  cache = data;
  if (changed) {
    paint(data);
    if (shown && !from) main.querySelector('h1')?.focus({ preventScroll: true });
  }
  reveal();
}
