import { button, element, mockTag, renderSiteHeader } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { screenByStatus } from '../../utils/screenByStatus.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { imageOf } from '../../components/stage.js';
import { AXES, CHARACTER_NAMES } from '../../utils/personalityAxes.js';
import { axisSeries, collectionOf, trailLeft, trailSummary } from '../../utils/myPageView.js';
import { BOOKMARK_CHARACTERS, BOOKMARK_NAMES, bookmarkFace, getBookmarkCharacter, setBookmarkCharacter } from '../../utils/bookmarkCharacter.js';
import { SCRIPTS } from '../../content/scripts.js';
import { fitPortraitIn } from '../../utils/portraitFit.js';
import { clearUser, currentUser, isAdmin } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';

// S-17 마이페이지 (WBS 7.6.6). GET /my-page 한 번 호출로 받고, 기록이 없어도 404가 아니라 빈 200이다(명세 P-5).
// 메인 책상의 커피잔으로 들어온다(FE-44): 커다란 커피잔이 날아와 페이드아웃하는 사이(utils/coffeeDive.js) 이 화면(from: 'mug')으로 넘어온다.
// 첫 화면은 [프로필카드 | 닉네임 | 성향카드] + 하단 항목 줄(만난 모습 · 내 데이터베이스 · 내 정보 수정 · 환경 설정)이고, 카드나 줄을 누르면 그 화면(view)으로 들어간다.
// 프로필카드 · 내 정보 수정 · 환경 설정은 껍데기만 있다(추가 예정). 성향카드 화면에는 판결에서 드러난 성향 · 성향 변화 흐름이 들어 있다.
// 항목 화면은 주소가 같고 view만 다르다(뒤로 가기로 첫 화면에 돌아온다).
// 종합 · 변화 흐름은 체험 → 성향 저장(7.6.7, BE-64) 뒤부터 채워지므로 그 전에는 null · 빈 배열로 와서 안내만 보인다.
// 로그인 전에는 익명 ID 기준이다(REQ-108).
const STATUS_LABELS = { COMPLETED: '체험 완료' };
const VIEWS = {
  profile: '프로필카드',
  type: '성향카드',
  collection: '만난 모습',
  cases: '내 데이터베이스',
  edit: '내 정보 수정',
  settings: '환경 설정',
};
const DEFAULT_NICKNAME = '익명의 판사'; // 로그인 · 닉네임 API 전까지 임시 문구
// 닉네임 아래 프로필 수치. 레벨 · 직업 · 출석 일수는 목 데이터(API 전, data.profile이 오면 그 값을 쓴다). 완료한 사건 · 모은 일러스트는 지금 기록에서 센다
const MOCK_PROFILE = { level: 7, job: '수습 판사', attendanceDays: 12 };
const IS_MOCK = API_MODE === 'mock'; // 목 모드에서는 체험 기록 · 성향 평균도 목(mocks/personalityMockApi.js)이라 함께 표기한다(FE-56)
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

// 성향 이미지: 이미지(imageUrl)가 아직 없거나 불러오지 못하면 캐릭터 이름을 대신 둔다(personalityParts.js와 같은 규칙)
function typePortrait(type) {
  const portrait = element('div', 'my-card-window');
  const characterName = CHARACTER_NAMES[type.character] ?? type.character;
  const showName = () => {
    portrait.replaceChildren(element('strong', '', characterName));
    portrait.setAttribute('aria-label', `${characterName} 캐릭터 (이미지 준비 중)`);
  };
  if (type.imageUrl) {
    const image = element('img');
    image.alt = `${characterName} 캐릭터`;
    image.addEventListener('error', showName, { once: true });
    image.src = type.imageUrl;
    portrait.append(image);
    fitPortraitIn(portrait, image); // 이미지마다 다른 투명 여백을 걷어 대표 이미지와 비슷한 크기로 맞춘다(FE-67)
  } else showName();
  return portrait;
}

// 카드 버튼 하나: 네모난 박스. 누르면 해당 화면으로 간다
function cardButton(className, label, ...children) {
  const card = element('button', `my-card ${className}`);
  card.type = 'button';
  card.setAttribute('aria-label', label);
  card.append(...children);
  return card;
}

// 성향을 모를 때 타로카드의 빈 그림 창("?")
function emptyWindow() {
  const window_ = element('div', 'my-card-window');
  window_.append(element('strong', '', '?'));
  window_.setAttribute('aria-hidden', 'true');
  return window_;
}

// 닉네임 아래 프로필 수치(레벨 · 직업 · 출석 일수 · 완료한 사건 수 · 모은 일러스트 수)
function renderStats(data) {
  // 응답에 없거나 null이면 MOCK_PROFILE로 채우고 목데이터 표기를 붙인다(FE-56)
  const mocked = (key) => data.profile?.[key] == null;
  const profile = Object.fromEntries(Object.keys(MOCK_PROFILE).map((key) => [key, mocked(key) ? MOCK_PROFILE[key] : data.profile[key]]));
  const experiences = data.experiences ?? [];
  const completed = experiences.filter((item) => item.status === 'COMPLETED').length;
  const groups = collectionOf(experiences, SCRIPTS);
  const collected = groups.reduce((sum, group) => sum + group.cast.length + group.items.length, 0);
  const list = element('dl', 'my-stats');
  [
    ['레벨', `Lv.${profile.level}`, mocked('level')],
    ['직업', profile.job, mocked('job')],
    ['출석 일수', `${profile.attendanceDays}일`, mocked('attendanceDays')],
    ['완료한 사건', `${completed}건`, IS_MOCK],
    ['모은 일러스트', `${collected}장`, IS_MOCK],
  ].forEach(([label, value, isMock]) => {
    const item = element('div', 'my-stat');
    const dd = element('dd', '', value);
    if (isMock) dd.append(mockTag());
    item.append(element('dt', '', label), dd);
    list.append(item);
  });
  return list;
}

// 첫 화면 위쪽: [프로필카드(현재 책갈피 캐릭터 전신) | 닉네임 · 수치 | 성향카드(타로카드: 이름 · 성향 이미지 · 대사 한 줄)]
function renderTop(data, go) {
  const section = element('section', 'my-top');
  const who = getBookmarkCharacter();
  const face = element('div', 'my-card-window');
  const faceImage = picture(imageOf(who, 'role:judge'), `${BOOKMARK_NAMES[who]} 전신`, 'my-profile-face');
  face.append(faceImage);
  fitPortraitIn(face, faceImage);
  const profile = cardButton('is-profile', `프로필카드 열기: 대표 이미지 ${BOOKMARK_NAMES[who]}-판사`, element('p', 'my-card-name', '대표 이미지'), face, element('p', 'my-card-note', `${BOOKMARK_NAMES[who]}-판사`));
  profile.addEventListener('click', () => go('profile'));

  const middle = element('div', 'my-top-middle');
  middle.append(element('h2', 'my-nickname', data.nickname || DEFAULT_NICKNAME), renderStats(data));

  const type = data.personality?.type;
  const typeCardNode = type
    ? cardButton('is-type', `성향카드 열기: ${type.name}`, element('p', 'my-card-name', type.name), typePortrait(type), element('p', 'my-card-note my-type-quote', type.meme))
    : cardButton('is-type is-empty', '성향카드 열기: 아직 성향을 몰라요', element('p', 'my-card-name', '아직 몰라요'), emptyWindow(), element('p', 'my-card-note my-type-quote', '"성향 테스트로 카드를 뽑아 봐요"'));
  typeCardNode.addEventListener('click', () => go('type'));

  section.append(profile, middle, typeCardNode);
  return section;
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

// 하단 항목 줄(순서: 만난 모습 · 내 데이터베이스 · 내 정보 수정 · 환경 설정)
function renderRows(data, go) {
  const list = element('ul', 'my-rows');
  const experiences = data.experiences ?? [];

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

  list.append(row('edit', '닉네임 · 프로필을 바꿔요 (추가 예정)', null, go));
  list.append(row('settings', '알림 · 화면 설정 (추가 예정)', null, go));
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
  const hint = element('p', 'my-hint', `첫 회차 체험 ${judgment.experienceCount}건의 평균이에요.`);
  if (IS_MOCK) hint.append(mockTag());
  box.append(hint);
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

// 성향카드 화면 — 성향 이미지 · 이름 · 설명, 그 아래에 판결에서 드러난 성향 · 성향 변화 흐름
function renderTypePage(data, navigate) {
  const box = element('div', 'my-detail');
  const type = data.personality?.type;
  if (type) {
    const hero = element('section', 'my-type-hero');
    const text = element('div');
    text.append(element('h2', 'my-type-hero-name', type.name), element('p', 'my-type-hero-text', type.oneLiner));
    hero.append(typePortrait(type), text);
    box.append(hero);
  } else {
    const empty = element('div', 'my-empty');
    empty.append(element('p', '', '아직 내 판결 성향을 몰라요. 성향 테스트는 3분이면 끝나요.'), button('성향 테스트 하러 가기', () => navigate('test'), 'primary-button'));
    box.append(empty);
  }
  const judgment = element('section', 'my-type-section');
  judgment.append(element('h2', 'my-section-title', '판결에서 드러난 성향'), renderJudgment(data.judgmentPersonality));
  const trail = element('section', 'my-type-section');
  trail.append(element('h2', 'my-section-title', '성향 변화 흐름'), renderTrail(data.history ?? []));
  box.append(judgment, trail);
  return box;
}

// 아직 내용이 없는 화면(껍데기)
function renderShell(text) {
  const box = element('div', 'my-detail');
  const empty = element('div', 'my-empty');
  empty.append(element('p', '', text));
  box.append(empty);
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
    profile: () => renderShell('프로필카드 화면은 곧 열려요.'),
    type: () => renderTypePage(data, navigate),
    collection: () => renderCollection(data.experiences),
    cases: () => renderCases(data.experiences ?? [], navigate),
    edit: () => renderShell('내 정보 수정은 곧 열려요.'),
    settings: () => renderShell('환경 설정은 곧 열려요.'),
  }[view]();
  return [head, body];
}

// 맨 아래 안내. 로그인했으면(FE-52) 누구로 로그인했는지와 로그아웃. 기록은 아직 로그인과 이어지지 않아 익명 ID 기준이다
function accountNote(api, navigate) {
  const user = currentUser();
  if (!user) return element('p', 'my-note', '로그인하기 전에는 이 브라우저(익명 ID)를 기준으로 기록해요. 쿠키를 지우면 기록을 찾을 수 없어요.');
  const note = element('div', 'my-note my-account');
  note.append(
    element('p', '', `${user.nickname} 님으로 로그인했어요. 기록은 아직 이 브라우저(익명 ID)를 기준으로 남아요.`),
    button('로그아웃', async () => {
      await Promise.resolve(api.logout?.()).catch(() => {}); // 서버 쪽 로그아웃이 없거나 실패해도 이 브라우저의 표시는 지운다
      clearUser();
      navigate('landing', {}, { replace: true });
    }, 'secondary-button'),
  );
  return note;
}

export async function renderMyPage(container, { api, navigate, view, from }) {
  if (isAdmin()) { navigate(homeRoute(), {}, { replace: true }); return; } // 관리자는 마이페이지 자리가 관리자 페이지다 (FE-53)
  const root = element('div', 'my-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  root.append(renderSiteHeader({ navigate }));
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
    main.replaceChildren(title, renderTop(data, go), renderRows(data, go), accountNote(api, navigate));
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
