import { button, element, mockTag, renderSiteHeader } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { screenByStatus } from '../../utils/screenByStatus.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { imageOf } from '../../components/stage.js';
import { AXES, CHARACTER_NAMES } from '../../utils/personalityAxes.js';
import { KIND_TITLES, axisSeries, collectionOf, imageKinds, trailLeft, trailSummary } from '../../utils/myPageView.js';
import { BACKGROUNDS, FRAMES, achievementProgress, getProfileDeco, kindOfPortrait, portraitLabel, setProfileDeco, shownDeco, unlockedBy, unlockerOf } from '../../utils/profileDeco.js';
import { PORTRAIT_POOL } from '../../content/portraitPool.js';
import { BOOKMARK_CHARACTERS, BOOKMARK_FACES, BOOKMARK_LAWS, BOOKMARK_NAMES, BOOKMARK_STYLES, FACE_NAMES, STYLE_NAMES, getBookmarkCharacter, getBookmarkFaceName, getBookmarkStyle, bookmarkFace, setBookmarkCharacter, setBookmarkFace } from '../../utils/bookmarkCharacter.js';
import { SCRIPTS } from '../../content/scripts.js';
import { fitPortraitIn } from '../../utils/portraitFit.js';
import { clearUser, currentUser, isAdmin } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';

// S-17 마이페이지 (WBS 7.6.6). GET /my-page 한 번 호출로 받고, 기록이 없어도 404가 아니라 빈 200이다(명세 P-5).
// 메인 책상의 커피잔으로 들어온다(FE-44): 커다란 커피잔이 날아와 페이드아웃하는 사이(utils/coffeeDive.js) 이 화면(from: 'mug')으로 넘어온다.
// 첫 화면은 [프로필카드 | 닉네임 | 성향카드] + 하단 항목 줄(만난 모습 · 내 데이터베이스 · 내 정보 수정 · 환경 설정)이고, 카드나 줄을 누르면 그 화면(view)으로 들어간다.
// 내 정보 수정 · 환경 설정은 껍데기만 있다(추가 예정). 성향카드 화면에는 판결에서 드러난 성향 · 성향 변화 흐름이 들어 있다.
// 프로필카드 화면(FE-68)은 꾸미기를 모은 곳이다: 대표 이미지 · 카드 테두리 · 카드 배경 · 책갈피를 고르고, 도전과제를 깨면 잠긴 것이 열린다.
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
// 개발 서버 + 목 모드에서만 public/images의 webp 전부를 컬렉션 아래에 펼친다(FE-67). 빌드에서는 이 분기가 통째로 빠진다
const ALL_WEBP = import.meta.env?.DEV ? Object.keys(import.meta.glob('/public/images/**/*.webp', { query: '?url', import: 'default' })).map((key) => key.replace(/^\/public/, '')) : [];
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

// 프로필 수치와 도전과제 판정(FE-68)이 함께 쓰는 값. 응답에 없거나 null이면 MOCK_PROFILE로 채우고, 목인 값은 mocked에 표시한다(FE-56)
function statsOf(data) {
  const missing = (key) => data.profile?.[key] == null;
  const profile = Object.fromEntries(Object.keys(MOCK_PROFILE).map((key) => [key, missing(key) ? MOCK_PROFILE[key] : data.profile[key]]));
  const experiences = data.experiences ?? [];
  const groups = collectionOf(experiences, SCRIPTS);
  return {
    ...profile,
    completed: experiences.filter((item) => item.status === 'COMPLETED').length,
    collected: groups.reduce((sum, group) => sum + group.cast.length + group.items.length, 0),
    hasType: Boolean(data.personality?.type),
    mocked: { level: missing('level'), job: missing('job'), attendanceDays: missing('attendanceDays'), completed: IS_MOCK, collected: IS_MOCK, hasType: IS_MOCK },
  };
}

// 닉네임 아래 프로필 수치(레벨 · 직업 · 출석 일수 · 완료한 사건 수 · 모은 일러스트 수)
function renderStats(stats) {
  const list = element('dl', 'my-stats');
  [
    ['레벨', `Lv.${stats.level}`, stats.mocked.level],
    ['직업', stats.job, stats.mocked.job],
    ['출석 일수', `${stats.attendanceDays}일`, stats.mocked.attendanceDays],
    ['완료한 사건', `${stats.completed}건`, stats.mocked.completed],
    ['모은 일러스트', `${stats.collected}장`, stats.mocked.collected],
  ].forEach(([label, value, isMock]) => {
    const item = element('div', 'my-stat');
    const dd = element('dd', '', value);
    if (isMock) dd.append(mockTag());
    item.append(element('dt', '', label), dd);
    list.append(item);
  });
  return list;
}

// 대표 이미지를 고르지 않았으면 책갈피 캐릭터의 판사 전신(로는 역할 의상 컷이 없어 대기 자세 마스코트)
function defaultPortrait() {
  const who = getBookmarkCharacter();
  const isLaw = BOOKMARK_LAWS.includes(who);
  return {
    src: isLaw ? `/images/characters/mascot/${who}_idle.webp` : imageOf(who, 'role:judge'),
    caption: isLaw ? BOOKMARK_NAMES[who] : `${BOOKMARK_NAMES[who]}-판사`,
  };
}

// 지금 보여 줄 꾸미기: 고른 것 중 잠긴 것은 기본으로(profileDeco.js shownDeco)
const decoFor = (stats) => shownDeco(getProfileDeco(), unlockedBy(stats));
const nameOf = (list, id) => list.find((item) => item.id === id)?.name ?? id;

// 대표 이미지 카드의 속: 위 "대표 이미지" · 그림 창 · 아래 이름. 테두리 · 배경은 클래스(.deco-frame-* · .deco-bg-*)로 입힌다
function profileCardParts(deco) {
  const { src, caption } = deco.portrait ? { src: deco.portrait, caption: portraitLabel(deco.portrait) } : defaultPortrait();
  const face = element('div', 'my-card-window');
  const faceImage = picture(src, `${caption} 대표 이미지`, 'my-profile-face');
  face.append(faceImage);
  fitPortraitIn(face, faceImage);
  return {
    caption,
    classes: `is-profile deco-frame-${deco.frame} deco-bg-${deco.background}`,
    children: [element('p', 'my-card-name', '대표 이미지'), face, element('p', 'my-card-note', caption)],
  };
}

// 첫 화면 위쪽: [프로필카드(대표 이미지 · 꾸미기) | 닉네임 · 수치 | 성향카드(타로카드: 이름 · 성향 이미지 · 대사 한 줄)]
function renderTop(data, go) {
  const section = element('section', 'my-top');
  const stats = statsOf(data);
  const parts = profileCardParts(decoFor(stats));
  const profile = cardButton(parts.classes, `프로필카드 열기: 대표 이미지 ${parts.caption}`, ...parts.children);
  profile.addEventListener('click', () => go('profile'));

  const middle = element('div', 'my-top-middle');
  middle.append(element('h2', 'my-nickname', data.nickname || DEFAULT_NICKNAME), renderStats(stats));

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
  list.append(row('collection', total > 0 ? `${total}가지를 모았어요` : '체험을 마치면 만난 모습이 모여요', thumbs, go));

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

// 책갈피 캐릭터 고르기(프로필카드 화면, FE-68에서 만난 모습에서 옮김): 얼굴 라디오. 고르면 바로 저장되고 판결 체험 책갈피 · 기본 대표 이미지에 쓰인다
function renderBookmarkPicker(onPick) {
  const field = element('fieldset', 'my-bookmark');
  field.append(element('legend', '', '책갈피 캐릭터'));
  const current = { who: getBookmarkCharacter(), face: getBookmarkFaceName(), style: getBookmarkStyle() };
  BOOKMARK_STYLES.forEach((style) => {
    const group = element('div', 'my-bookmark-group');
    group.append(element('h3', 'my-bookmark-style', STYLE_NAMES[style]));
    BOOKMARK_CHARACTERS.forEach((who) => {
      const row = element('div', 'my-bookmark-row');
      row.append(element('span', 'my-bookmark-name', BOOKMARK_NAMES[who]));
      BOOKMARK_FACES.forEach((face) => {
        const name = `${STYLE_NAMES[style]} · ${BOOKMARK_NAMES[who]} · ${FACE_NAMES[face]}`;
        const label = element('label', 'my-bookmark-option');
        label.title = name;
        const input = element('input');
        input.type = 'radio';
        input.name = 'bookmark-face';
        input.value = `${style}:${who}:${face}`;
        input.checked = style === current.style && who === current.who && face === current.face;
        input.setAttribute('aria-label', name);
        input.addEventListener('change', () => { setBookmarkFace(who, face, style); onPick?.(); });
        label.append(input, picture(bookmarkFace(who, face, style), ''));
        row.append(label);
      });
      group.append(row);
    });
    field.append(group);
  });
  const lawGroup = element('div', 'my-bookmark-group');
  lawGroup.append(element('h3', 'my-bookmark-style', '로 (표정 없이 아이콘 한 장씩)'));
  const lawRow = element('div', 'my-bookmark-row');
  lawRow.append(element('span', 'my-bookmark-name', '로'));
  BOOKMARK_LAWS.forEach((who) => {
    const label = element('label', 'my-bookmark-option');
    label.title = BOOKMARK_NAMES[who];
    const input = element('input');
    input.type = 'radio';
    input.name = 'bookmark-face';
    input.value = who;
    input.checked = who === current.who;
    input.setAttribute('aria-label', BOOKMARK_NAMES[who]);
    input.addEventListener('change', () => { setBookmarkCharacter(who); onPick?.(); });
    label.append(input, picture(bookmarkFace(who), ''));
    lawRow.append(label);
  });
  lawGroup.append(lawRow);
  field.append(lawGroup);
  field.append(element('p', 'my-hint', '판결 체험에서 읽던 자리를 표시하는 책갈피에 이 얼굴이 꽂혀요.'));
  return field;
}

// 컬렉션 카드를 누르면 그림을 크게 띄운다. 바깥 · 닫기 · Esc로 닫는다(dialog가 포커스도 가둔다)
// 마이페이지의 커피색 역할 토큰이 적용되도록 .my-page 안에 붙이고, 닫으면 지운다
function zoomIn(src, alt, label) {
  const dialog = element('dialog', 'collection-zoom');
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  dialog.addEventListener('close', () => dialog.remove());
  dialog.append(picture(src, alt), element('p', 'collection-label', label), button('닫기', () => dialog.close(), 'secondary-button'));
  (document.querySelector('.my-page') ?? document.body).append(dialog);
  dialog.showModal();
}

function collectionCard(className, src, alt, label) {
  const card = element('li', `collection-card ${className}`.trim());
  const open = element('button', 'collection-open');
  open.type = 'button';
  open.setAttribute('aria-label', `${label} 크게 보기`);
  const frame = element('div', 'collection-window'); // 그림 틀. 캐릭터가 틀에서 같은 크기로 서도록 맞춘다(FE-67)
  const image = picture(src, '');
  frame.append(image);
  fitPortraitIn(frame, image, { top: 20, bottom: 20, side: 0.84, center: true });
  const caption = element('p', 'collection-label', label);
  caption.title = label; // 길어서 줄여 보일 때 전체 이름
  open.append(frame, caption); // 이름은 캡션이 읽는다
  open.addEventListener('click', () => zoomIn(src, alt, label));
  card.append(open);
  return card;
}

// 컬렉션 — 이미지를 사건별이 아니라 종류별(역할 의상 · 얼굴 아이콘 · 장면 컷 …)로 보여 준다. 사건 고유 이미지는 "사건 이미지" 안에서 사건별로 나눈다
// 실제 모드: 체험을 마친 사건에서 만난 모습(역할 의상)과 사건 일러스트만 모은다
// 목 모드(FE-67): public/images의 webp를 전부 펼쳐 어떻게 모이는지 미리 본다
function collectedKinds(experiences) {
  if (IS_MOCK && ALL_WEBP.length > 0) return imageKinds(ALL_WEBP, { caseNames: caseNamesOf() });
  const groups = collectionOf(experiences, SCRIPTS);
  const labels = {};
  const paths = [];
  groups.forEach((group) => {
    group.cast.forEach(({ who, role, label }) => { const src = imageOf(who, `role:${role}`); paths.push(src); labels[src] = label; });
    group.items.forEach(({ src, label }) => { paths.push(src); labels[src] = `${group.title} · ${label}`; });
  });
  return imageKinds(paths, { labels, caseNames: caseNamesOf() });
}

const caseNamesOf = () => Object.fromEntries(Object.values(SCRIPTS).map(({ caseId, title }) => [caseId, title]));

function renderCollection(experiences) {
  const box = element('div', 'my-detail');
  const kinds = collectedKinds(experiences);
  if (kinds.length === 0) {
    box.append(element('p', 'my-hint', '사건 체험을 마치면 그 사건에서 만난 모습과 일러스트가 여기에 모여요.'));
    return box;
  }
  const cards = (items) => {
    const grid = element('ul', 'collection-grid');
    items.forEach((item) => grid.append(collectionCard('', item.src, item.label, item.label)));
    return grid;
  };
  kinds.forEach((kind) => {
    const part = element('section', 'collection-group');
    const title = element('h2', 'collection-title', `${kind.title} (${kind.items.length})`);
    if (IS_MOCK) title.append(mockTag());
    part.append(title);
    if (kind.groups) kind.groups.forEach((group) => part.append(element('h3', 'collection-subtitle', `${group.title} (${group.items.length})`), cards(group.items)));
    else part.append(cards(kind.items));
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

// ── 프로필카드 화면(FE-68): 꾸미기 · 도전과제 ──

const lockIcon = () => svg('M4.5 7V5a3.5 3.5 0 0 1 7 0v2M3.5 7h9v6.5h-9z', 'my-lock');

// 라디오 칸 하나(그림 · 견본). 잠겼으면 고를 수 없고 무엇을 깨면 열리는지 이름에 붙인다
function decoOption({ group, value, checked, locked, name, lockedBy, onChange }, ...look) {
  const label = element('label', `my-deco-option${locked ? ' is-locked' : ''}`);
  const fullName = locked ? `${name} (잠김: ${lockedBy} 달성 시 열려요)` : name;
  label.title = fullName;
  const input = element('input');
  input.type = 'radio';
  input.name = group;
  input.value = value;
  input.checked = checked;
  input.disabled = locked;
  input.setAttribute('aria-label', fullName);
  input.addEventListener('change', onChange);
  label.append(input, ...look);
  if (locked) label.append(lockIcon());
  return label;
}

// 대표 이미지: 기본(책갈피 캐릭터) + 후보 130장을 종류별로. 잠긴 종류는 실루엣으로만 보인다
function portraitPicker(open, shownPortrait, onPick) {
  const field = element('fieldset', 'my-deco');
  field.append(element('legend', '', '대표 이미지'), element('p', 'my-hint', '종류마다 도전과제를 깨면 열려요. 고르면 바로 마이페이지 카드에 쓰여요.'));
  const thumb = (src) => {
    const box = element('span', 'my-deco-thumb');
    box.append(picture(src, ''));
    return box;
  };
  const pick = (portrait) => () => { setProfileDeco({ portrait }); onPick(); };
  const base = defaultPortrait();
  const first = element('div', 'my-deco-grid my-deco-default');
  first.append(decoOption({ group: 'profile-portrait', value: 'default', checked: shownPortrait === null, locked: false, name: `기본: ${base.caption} (책갈피 캐릭터를 따라가요)`, onChange: pick(null) }, thumb(base.src)));
  field.append(element('h3', 'my-deco-kind', '기본'), first);
  imageKinds(PORTRAIT_POOL).forEach((kind) => {
    const locked = !open.kinds.has(kind.key);
    const by = unlockerOf('kinds', kind.key)?.title ?? '';
    const head = element('h3', 'my-deco-kind', `${kind.title} (${kind.items.length})`);
    head.append(element('span', `my-deco-state${locked ? '' : ' is-open'}`, locked ? `잠김 · ${by}` : '열림'));
    const grid = element('div', 'my-deco-grid');
    kind.items.forEach(({ src }) => grid.append(decoOption({
      group: 'profile-portrait', value: src, checked: src === shownPortrait, locked, name: portraitLabel(src), lockedBy: by, onChange: pick(src),
    }, thumb(src))));
    field.append(head, grid);
  });
  return field;
}

// 카드 테두리 · 배경 견본. type: 'frame' | 'background'
function swatchPicker(type, open, shown, onPick) {
  const list = type === 'frame' ? FRAMES : BACKGROUNDS;
  const opened = type === 'frame' ? open.frames : open.backgrounds;
  const field = element('fieldset', 'my-deco');
  field.append(element('legend', '', type === 'frame' ? '카드 테두리' : '카드 배경'));
  const row = element('div', 'my-deco-swatches');
  list.forEach(({ id, name }) => {
    const locked = !opened.has(id);
    const by = unlockerOf(type === 'frame' ? 'frames' : 'backgrounds', id)?.title ?? '';
    const sample = element('span', `my-mini-card deco-frame-${type === 'frame' ? id : 'basic'} deco-bg-${type === 'frame' ? 'cream' : id}`);
    const caption = element('span', 'my-deco-name', name);
    if (locked) caption.append(element('small', '', by));
    row.append(decoOption({
      group: `profile-${type}`, value: id, checked: id === shown, locked, name, lockedBy: by,
      onChange: () => { setProfileDeco({ [type]: id }); onPick(); },
    }, sample, caption));
  });
  field.append(row);
  return field;
}

// 보상을 한 줄로: "장면 컷 16장 · 원목 테두리"
function rewardText(item) {
  const count = (kind) => PORTRAIT_POOL.filter((src) => kindOfPortrait(src) === kind).length;
  return [
    ...item.kinds.map((kind) => `${KIND_TITLES[kind]} ${count(kind)}장`),
    ...item.frames.map((id) => `${nameOf(FRAMES, id)} 테두리`),
    ...item.backgrounds.map((id) => `${nameOf(BACKGROUNDS, id)} 배경`),
  ].join(' · ');
}

function achievementList(progress, mocked) {
  const section = element('section', 'my-achievements-section');
  section.id = 'my-achievements';
  const done = progress.filter((item) => item.cleared).length;
  section.append(element('h2', 'my-section-title', `도전과제 ${done} / ${progress.length}`));
  const list = element('ol', 'my-achievements');
  progress.forEach((item) => {
    const line = element('li', `my-achievement${item.cleared ? ' is-cleared' : ''}`);
    const head = element('div', 'my-achievement-head');
    const state = element('span', 'my-achievement-state', item.cleared ? '달성' : `${item.value} / ${item.goal}`);
    if (mocked[item.stat]) state.append(mockTag());
    head.append(element('strong', '', item.title), state);
    const bar = element('div', 'my-achievement-bar');
    bar.setAttribute('role', 'progressbar');
    bar.setAttribute('aria-label', `${item.title} 진행도`);
    bar.setAttribute('aria-valuemin', '0');
    bar.setAttribute('aria-valuemax', String(item.goal));
    bar.setAttribute('aria-valuenow', String(item.value));
    const fill = element('span');
    fill.style.width = `${Math.round((item.value / item.goal) * 100)}%`;
    bar.append(fill);
    line.append(head, element('p', 'my-achievement-text', item.text), bar, element('p', 'my-achievement-reward', `보상: ${rewardText(item)}`));
    list.append(line);
  });
  section.append(list);
  return section;
}

function renderProfilePage(data) {
  const box = element('div', 'my-detail');
  const stats = statsOf(data);
  const open = unlockedBy(stats);
  const progress = achievementProgress(stats);

  // 위: 미리 보기 카드 + 열린 개수. 무엇을 고르든 미리 보기를 다시 그린다
  const hero = element('section', 'my-profile-hero');
  const preview = element('div', 'my-profile-preview');
  const paintPreview = () => {
    const deco = decoFor(stats);
    const parts = profileCardParts(deco);
    const card = element('div', `my-card ${parts.classes} is-preview`);
    card.setAttribute('role', 'img');
    card.setAttribute('aria-label', `미리 보기: 대표 이미지 ${parts.caption}, ${nameOf(FRAMES, deco.frame)} 테두리, ${nameOf(BACKGROUNDS, deco.background)} 배경`);
    card.append(...parts.children);
    preview.replaceChildren(card);
  };
  paintPreview();
  const summary = element('div', 'my-profile-summary');
  const counts = element('dl', 'my-stats');
  [
    ['대표 이미지', `${PORTRAIT_POOL.filter((src) => open.kinds.has(kindOfPortrait(src))).length} / ${PORTRAIT_POOL.length}`],
    ['카드 테두리', `${open.frames.size} / ${FRAMES.length}`],
    ['카드 배경', `${open.backgrounds.size} / ${BACKGROUNDS.length}`],
    ['도전과제', `${progress.filter((item) => item.cleared).length} / ${progress.length}`],
  ].forEach(([label, value]) => {
    const item = element('div', 'my-stat');
    const dd = element('dd', '', value);
    if (IS_MOCK) dd.append(mockTag());
    item.append(element('dt', '', label), dd);
    counts.append(item);
  });
  const jump = button('도전과제 보기', () => box.querySelector('#my-achievements')?.scrollIntoView({ block: 'start' }), 'secondary-button');
  summary.append(element('p', 'my-hint', '도전과제를 깨면 대표 이미지 · 테두리 · 배경이 열려요. 고른 것은 이 브라우저에 저장돼요.'), counts, jump);
  hero.append(preview, summary);

  const shown = decoFor(stats);
  box.append(
    hero,
    portraitPicker(open, shown.portrait, paintPreview),
    swatchPicker('frame', open, shown.frame, paintPreview),
    swatchPicker('background', open, shown.background, paintPreview),
    renderBookmarkPicker(() => {
      // 기본 대표 이미지는 책갈피 캐릭터를 따라가므로 "기본" 칸 그림도 바꾼다
      const base = box.querySelector('.my-deco-default img');
      if (base) base.src = defaultPortrait().src;
      paintPreview();
    }),
    achievementList(progress, stats.mocked),
  );
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
    profile: () => renderProfilePage(data),
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
