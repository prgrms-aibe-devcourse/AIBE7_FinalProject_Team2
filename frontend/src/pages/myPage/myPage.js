import { button, element, mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { imageOf } from '../../components/stage.js';
import { AXES, CHARACTER_NAMES } from '../../utils/personalityAxes.js';
import { KIND_TITLES, lenientPercent, axisChange, axisTrend, collectionOf, imageKinds, leanText, retakeAfterOrder } from '../../utils/myPageView.js';
import { renderTrend, trendsOf } from '../../components/trendGraph.js';
import { BACKGROUNDS, DEFAULT_DECO, DEFAULT_PORTRAITS, FRAMES, SASH_COLORS, SASH_ICONS, SASH_STYLES, achievementView, normalizeDeco, sashColorField, sashColorKey, sashColorOf, kindOfPortrait, portraitLabel, shownDeco, unlockedSets, unlockerIn } from '../../utils/profileDeco.js';
import { PORTRAIT_POOL } from '../../content/portraitPool.js';
import { BOOKMARK_CHARACTERS, BOOKMARK_FACES, BOOKMARK_LAWS, BOOKMARK_NAMES, BOOKMARK_STYLES, FACE_NAMES, STYLE_NAMES, applyBookmark, getBookmarkCharacter, getBookmarkFaceName, getBookmarkStyle, bookmarkFace, BOOKMARK_COLORS, getBookmarkColor, selectedBookmarkFace } from '../../utils/bookmarkCharacter.js';
import { SCRIPTS } from '../../content/scripts.js';
import { fitPortraitIn } from '../../utils/portraitFit.js';
import { reducedMotion } from '../../utils/motion.js';
import { clearUser, currentUser, isAdmin } from '../../utils/session.js';
import { homeRoute } from '../admin/adminMenu.js';
import { openCaseReviewPopup } from '../caseReview/caseReviewPopup.js';
import { renderProfileEdit } from './myProfileEdit.js';
import { renderSettings } from './mySettings.js';
import { RETAKE_NOTES, openRetakeDialog, retakeAfter, retakeState } from './baselineRetake.js';
import { SHOWN_SLOTS, setShownCard, shownSlot } from '../../utils/shownCard.js';
import { selectedChoice, setProfileCardType, typeChoices } from '../../utils/profileCardType.js';
import { configureProfileSettings, hydrateProfileSettings } from '../../utils/profileSettings.js';

// S-17 마이페이지 (WBS 7.6.6). GET /my-page 한 번 호출로 받고, 기록이 없어도 404가 아니라 빈 200이다(명세 P-5).
// 메인 책상의 커피잔으로 들어온다(FE-44): 커다란 커피잔이 날아와 페이드아웃하는 사이(utils/coffeeDive.js) 이 화면(from: 'mug')으로 넘어온다.
// 첫 화면은 [프로필카드 | 닉네임 | 성향카드] + 하단 항목 줄(만난 모습 · 내가 체험한 사건 · 내 정보 수정 · 환경 설정)이고, 카드나 줄을 누르면 그 화면(view)으로 들어간다.
// 환경 설정은 화면 · 알림 틀이 있다(FE-83, mySettings.js). 내 정보 수정은 FE-77. 성향카드 화면에는 판결에서 드러난 성향 · 성향 변경폭 · 성향 변화 흐름(FE-70)이 들어 있다.
// 프로필카드 화면(FE-68)은 꾸미기를 모은 곳이다: 대표 이미지 · 카드 테두리 · 카드 배경 · 책갈피를 고르고, 도전과제를 깨면 잠긴 것이 열린다.
// 꾸미기 값 · 도전과제 진행도 · 열린 항목은 서버가 원본이다(FE-71, BE-96). 고르면 바로 화면에 쓰고 PUT /profile-deco로 저장하며, 거절(409 DECO_LOCKED 등)되면 서버 값으로 되돌린다.
// 항목 화면은 주소가 같고 view만 다르다(뒤로 가기로 첫 화면에 돌아온다).
// 종합 · 변화 흐름은 체험 → 성향 저장(7.6.7, BE-64) 뒤부터 채워지므로 그 전에는 null · 빈 배열로 와서 안내만 보인다.
// 로그인 전에는 익명 ID 기준이다(REQ-108).
const STATUS_LABELS = { COMPLETED: '체험 완료' };
// 목 모드의 "내가 체험한 사건" 목록에서만 체험 중으로 보여 줄 사건(체험 중 모양 확인용). 첫 화면 수치 · 컬렉션 · 성향 기록은 그대로 완료로 센다
const MOCK_IN_PROGRESS = new Set([3, 6, 8]);
const VIEWS = {
  profile: '프로필카드',
  type: '성향카드',
  collection: '만난 모습',
  cases: '내가 체험한 사건',
  edit: '내 정보 수정',
  settings: '환경 설정',
  achievements: '도전과제',
  bookmark: '책갈피 설정',
};
// 프로필카드에서 들어가는 화면은 뒤로 가기가 프로필카드로 간다(FE-68)
const PARENTS = { achievements: 'profile', bookmark: 'profile' };
export const DEFAULT_NICKNAME = '익명의 판사'; // 로그인 · 닉네임 API 전까지 임시 문구
// 닉네임 아래 프로필 수치. 레벨 · 직업은 목 데이터(API 전, data.profile이 오면 그 값을 쓴다). 출석 일수는 서버 값(P-5 profile.attendanceDays)이다. 완료한 사건 · 모은 일러스트는 지금 기록에서 센다
const MOCK_PROFILE = { level: 7, job: '수습 판사', attendanceDays: 0 };
const IS_MOCK = API_MODE === 'mock'; // 목 모드에서는 체험 기록 · 성향 평균도 목(mocks/personalityMockApi.js)이라 함께 표기한다(FE-56)
// 개발 서버 + 목 모드에서만 public/images의 webp 전부를 컬렉션 아래에 펼친다(FE-67). 빌드에서는 이 분기가 통째로 빠진다
const ALL_WEBP = import.meta.env?.DEV ? Object.keys(import.meta.glob('/public/images/**/*.webp', { query: '?url', import: 'default' })).map((key) => key.replace(/^\/public/, '')) : [];
let cache = null; // 마지막 응답. 항목 화면을 오갈 때 바로 그리고 뒤에서 새로 받는다
let apiRef = null; // 꾸미기 저장에 쓰는 api(화면을 그릴 때마다 맞춘다)
let refresh = null; // 저장이 거절됐을 때 서버 값으로 화면을 다시 그리는 함수(renderMyPage가 정한다)
let saveQueue = Promise.resolve(); // 저장은 고른 순서대로 한 번에 하나씩 보낸다(PUT은 전체를 덮어쓰므로 순서가 바뀌면 안 된다)
let saveEpoch = 0; // 거절되면 올린다. 거절 전에 줄 선 저장은 건너뛴다
let decoVersion = 0; // 꾸미기를 고를 때마다 올린다. 받아 둔 서버 값이 그 사이에 고른 것을 덮어쓰지 않게 비교한다

// 지금 고른 꾸미기(서버 값 + 방금 고른 것). 잠겼는지는 보지 않는다(shownDeco가 한다)
const currentDeco = () => normalizeDeco(cache?.profileDeco);
// 서버가 알려 준 열린 항목 · 잠긴 항목을 여는 도전과제
const openOf = (data) => unlockedSets(data.unlocked);
const unlockerOf = (type, id) => unlockerIn(cache?.achievements, type, id);

const LOCKED_FIELD_NAMES = {
  portrait: '대표 이미지', frame: '카드 테두리', background: '카드 배경', sashStyle: '카드 책갈피 모양',
  sashColorShort: '카드 책갈피 색', sashColorLong: '카드 책갈피 색', sashIconLeft: '책갈피 아이콘', sashIconRight: '책갈피 아이콘',
};
// 공개 카드 · 카드 아래 성향 저장이 거절되면(P-8) 서버 값으로 다시 그리고 알린다
const saveSettingsOr = (saving) => saving.then((saved) => { if (!saved) refresh?.('선택을 저장하지 못했어요. 저장된 값으로 다시 보여 드려요.'); });

function saveFailureText(error) {
  if (error?.code === 'DECO_LOCKED') {
    const names = [...new Set((error.details ?? []).map(({ field }) => LOCKED_FIELD_NAMES[field]).filter(Boolean))];
    return `아직 열리지 않은 꾸미기가 있어서 저장하지 못했어요${names.length ? `(${names.join(' · ')})` : ''}. 열린 것으로 다시 보여 드려요.`;
  }
  if (error?.code === 'VALIDATION_ERROR') return '꾸미기를 저장하지 못했어요. 저장된 값으로 다시 보여 드려요.';
  return '꾸미기를 저장하지 못했어요. 잠시 뒤 다시 골라 주세요.';
}

/**
 * 꾸미기를 바꾼다: 고른 것을 바로 화면 값(cache)에 반영하고 서버에 저장한다. 서버는 전체를 한 번에 받으므로 지금 값 전체를 보낸다.
 * 거절되면 서버 값으로 되돌리고 안내한다(409면 마이페이지를 다시 불러온다, 명세 P-7)
 */
function saveDeco(patch) {
  const next = normalizeDeco({ ...currentDeco(), ...patch });
  if (cache) cache.profileDeco = next;
  applyBookmark(next); // 판결 체험 화면이 바로 읽는 거울
  const epoch = saveEpoch;
  decoVersion += 1;
  const version = decoVersion;
  saveQueue = saveQueue.then(async () => {
    if (epoch !== saveEpoch) return;
    try {
      const saved = await apiRef.putProfileDeco(next);
      // 서버가 정리해 돌려준 값을 따른다. 그 사이 새로 골랐으면 그 선택이 최신이므로 건드리지 않는다
      if (version === decoVersion && cache && saved) {
        cache.profileDeco = normalizeDeco(saved);
        applyBookmark(cache.profileDeco);
      }
    } catch (error) {
      saveEpoch += 1;
      await refresh?.(saveFailureText(error));
    }
  });
  return next;
}

/**
 * 마이페이지를 받는다. 저장이 끝나길 기다린 뒤 요청하고, 받는 동안 꾸미기를 새로 골랐으면 받은 꾸미기 대신 화면 값을 지킨다
 * (오래된 서버 값이 방금 고른 것을 덮어쓰지 않게). 거절 뒤 서버 값으로 되돌릴 때는 쓰지 않는다
 */
async function loadMyPage(api) {
  await saveQueue;
  const version = decoVersion;
  const data = await api.getMyPage();
  hydrateProfileSettings(data.profileSettings);
  return version === decoVersion || !cache ? data : { ...data, profileDeco: cache.profileDeco };
}

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

// 프로필 수치로 보여 주는 값. 응답에 없거나 null이면 MOCK_PROFILE로 채우고, 목인 값은 mocked에 표시한다(FE-56). 도전과제 판정은 서버가 한다
function statsOf(data) {
  const missing = (key) => data.profile?.[key] == null;
  const profile = Object.fromEntries(Object.keys(MOCK_PROFILE).map((key) => [key, missing(key) ? MOCK_PROFILE[key] : data.profile[key]]));
  const experiences = data.experiences ?? [];
  const groups = collectionOf(experiences, SCRIPTS);
  return {
    ...profile,
    completed: experiences.filter((item) => item.status === 'COMPLETED').length,
    collected: groups.reduce((sum, group) => sum + group.cast.length + group.items.length, 0),
    mocked: { level: missing('level'), job: missing('job'), attendanceDays: IS_MOCK || missing('attendanceDays'), completed: IS_MOCK, collected: IS_MOCK },
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

// 지금 보여 줄 꾸미기: 고른 것 중 잠긴 것은 기본으로(profileDeco.js shownDeco)
const decoFor = (data) => shownDeco(currentDeco(), openOf(data));
const nameOf = (list, id) => list.find((item) => item.id === id)?.name ?? id;

// 카드 책갈피(FE-68): 카드 좌우에 걸리는 띠 두 줄. 색은 .deco-sash-*, 모양은 .sash-style-*(짧고 굵은 책갈피 · 얇고 긴 띠)가 정한다.
// 짧은 책갈피에는 좌 · 우 아이콘을 따로 단다. 장식이라 스크린리더에는 숨긴다(카드의 읽는 이름에 적는다)
const SASH_ICON_ART = {
  star: { path: 'M8 1.5l1.8 4.7 4.7 1.8-4.7 1.8L8 14.5l-1.8-4.7L1.5 8l4.7-1.8z' },
  scale: { path: 'M8 2v12M4 14h8M3 5h10M3 5l-2 4h4zM13 5l-2 4h4z' },
  gavel: { path: 'M9.5 2.5l4 4M7.5 4.5l4 4M8.5 3.5l-4 4 2 2 4-4M5.5 8.5L2 12M9 14h5' },
  cup: { path: 'M3 6h8v4a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3zM11 7h1.5a1.5 1.5 0 0 1 0 3H11M5 2.5c0 1 1 1 1 2M8 2.5c0 1 1 1 1 2' },
  heart: { path: 'M8 13.5S2.5 10 2.5 6.3A2.8 2.8 0 0 1 8 5a2.8 2.8 0 0 1 5.5 1.3C13.5 10 8 13.5 8 13.5z' },
  naero: { src: '/images/characters/icon/generated/naero_face-happy.webp' },
  nambul: { src: '/images/characters/icon/generated/nambul_face-cheer.webp' },
  law: { src: '/images/characters/icon/cropped/law-scale_icon.webp' },
};
function sashIcon(id) {
  const art = SASH_ICON_ART[id];
  if (!art) return null;
  return art.path ? svg(art.path, 'my-sash-icon') : picture(art.src, '', 'my-sash-icon');
}
function sashes(deco) {
  if (deco.sashStyle === 'none') return [];
  return [['left', deco.sashIconLeft], ['right', deco.sashIconRight]].map(([side, icon]) => {
    const band = element('span', `my-card-sash is-${side}`);
    band.setAttribute('aria-hidden', 'true');
    const art = deco.sashStyle === 'short' ? sashIcon(icon) : null;
    if (art) band.append(art);
    return band;
  });
}
const sashClasses = (deco) => `deco-sash-${sashColorOf(deco) ?? 'none'} sash-style-${deco.sashStyle}`;
const sashNote = (deco) => {
  if (deco.sashStyle === 'none') return '';
  const icons = deco.sashStyle === 'short' ? [deco.sashIconLeft, deco.sashIconRight].filter((id) => id !== 'none').map((id) => nameOf(SASH_ICONS, id)) : [];
  return `, ${nameOf(SASH_COLORS[deco.sashStyle], sashColorOf(deco))} ${nameOf(SASH_STYLES, deco.sashStyle)}${icons.length ? `(${icons.join(' · ')})` : ''}`;
};

// 대표 이미지 카드의 속: 위 닉네임 · 그림 창(대표 이미지) · 아래 성향 테스트 결과. 테두리 · 배경 · 책갈피는 클래스(.deco-frame-* · .deco-bg-* · .deco-sash-*)로 입힌다
function profileCardParts(deco, data) {
  const src = deco.portrait;
  const caption = portraitLabel(src);
  const face = element('div', 'my-card-window');
  const faceImage = picture(src, `${caption} 대표 이미지`, 'my-profile-face');
  face.append(faceImage);
  fitPortraitIn(face, faceImage, { centerWide: true }); // 가로로 긴 그림(사건 사진 등)은 창 세로 가운데에, 서 있는 캐릭터는 발을 아래에
  const nickname = data.nickname || DEFAULT_NICKNAME;
  const typeName = selectedChoice(data)?.type.name ?? currentTypeOf(data)?.name; // 카드 아래 성향: 카드 꾸미기에서 고른 것(기본 최근)
  const result = element('p', `my-card-note${typeName ? '' : ' is-empty'}`, typeName ?? '성향 테스트 전');
  return {
    caption,
    note: `${nickname}, 대표 이미지 ${caption}, 성향 ${typeName ?? '테스트 전'}${sashNote(deco)}`,
    classes: `is-profile deco-frame-${deco.frame} deco-bg-${deco.background} ${sashClasses(deco)}`,
    children: [element('p', 'my-card-name', nickname), face, result, ...sashes(deco)],
  };
}

// 성향카드가 보여 줄 현재 유형: 현재 판결 성향(최근 체험 평균). 네 축이 다 모이지 않아 없으면 성향 테스트 유형. 첫 화면 · 성향카드 화면이 같이 쓴다
const currentTypeOf = (data) => data.judgmentPersonality?.type ?? data.personality?.type;

// 프로필카드 · 성향카드(타로카드) 한 쌍. 첫 화면 위쪽과 내 정보 수정 화면이 같이 쓴다
function profileCardOf(data, go) {
  const parts = profileCardParts(decoFor(data), data);
  const profile = cardButton(parts.classes, `프로필카드 열기: ${parts.note}`, ...parts.children);
  profile.addEventListener('click', () => go('profile'));
  return profile;
}

function profileCardPair(data, go) {
  const profile = profileCardOf(data, go);
  const type = currentTypeOf(data);
  const typeCardNode = type
    ? cardButton('is-type', `성향카드 열기: ${type.name}`, element('p', 'my-card-name', type.name), typePortrait(type), element('p', 'my-card-note my-type-quote', type.meme))
    : cardButton('is-type is-empty', '성향카드 열기: 아직 성향을 몰라요', element('p', 'my-card-name', '아직 몰라요'), emptyWindow(), element('p', 'my-card-note my-type-quote', '"성향 테스트로 카드를 뽑아 봐요"'));
  typeCardNode.addEventListener('click', () => go('type'));
  return [profile, typeCardNode];
}

// 첫 화면 위쪽: [프로필카드(대표 이미지 · 꾸미기) | 닉네임 · 수치 | 성향카드(타로카드: 이름 · 성향 이미지 · 대사 한 줄)]
function renderTop(data, go) {
  const section = element('section', 'my-top');
  const stats = statsOf(data);
  const [profile, typeCardNode] = profileCardPair(data, go);

  const middle = element('div', 'my-top-middle');
  middle.append(element('h2', 'my-nickname', data.nickname || DEFAULT_NICKNAME), renderStats(stats));

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

// 하단 항목 줄(순서: 만난 모습 · 내가 체험한 사건 · 내 정보 수정 · 환경 설정)
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
  list.append(row('collection', total > 0 ? `체험한 사건에서 만난 캐릭터 ${total}가지를 모았어요` : '체험을 마치면 사건에서 만난 캐릭터가 모여요', thumbs, go));

  const latest = [...experiences].sort((a, b) => String(b.updatedAt ?? '').localeCompare(String(a.updatedAt ?? '')))[0];
  list.append(row('cases', latest ? `최근: ${latest.title}` : '아직 체험한 사건이 없어요', element('span', 'my-count', `${experiences.length}건`), go, `${experiences.length}건`));

  list.append(row('edit', '닉네임 · 프로필을 바꿔요 (추가 예정)', null, go));
  list.append(row('settings', '화면 · 알림을 내 취향대로 바꿔요', null, go));
  return list;
}

// ── 항목 화면 ──

// head: 안내(첫 회차 체험 n건의 평균)를 제목 옆에 붙일 자리
function renderJudgment(judgment, head) {
  const box = element('div', 'my-detail');
  if (!judgment) {
    box.classList.add('is-empty'); // 성향카드 옆(테스트만 하고 체험 전)이면 카드 높이의 빈 자리 가운데에 둔다
    box.append(element('p', 'my-hint', '사건을 체험하면 판결에서 무엇을 무겁게 봤는지 여기에 쌓여요.'));
    return box;
  }
  // 현재 판결 성향 = 최근 체험 3건 평균(API 명세 P-5). 유형은 성향 변경폭 아래 "지금" 카드가 보여 준다
  const hint = element('p', 'my-hint', `최근 체험 ${judgment.experienceCount}건의 평균이에요.`);
  if (IS_MOCK) hint.append(mockTag());
  head.append(hint);
  const list = element('ul', 'axis-list');
  AXES.forEach((axis) => list.append(renderAxisRow(axis, judgment.axisScores?.[axis.key])));
  box.append(list);
  return box;
}

// 성향카드를 다시 정했으면 그 테스트(FE-77): { result, after(그 전에 마친 체험 수), title }. 아니면 null
function retakeOf(data) {
  const result = data.baselineRetake?.result;
  if (!result) return null;
  const after = retakeAfterOrder(data.history, result.testedAt);
  return { result, after, title: ['재심', retakeAfter(result)].filter(Boolean).join(' · ') };
}

// 다시 정한 뒤 → 지금 변경폭: 다시 정한 테스트 점에서 시작해 그 뒤 체험만 잇는다
function retakeTrendsOf(data, retake) {
  const history = data.history ?? [];
  return AXES.map((axis) => {
    const ratio = retake.result.axisScores?.[axis.key];
    const start = typeof ratio === 'number' ? [{ order: 'retake', ratio, label: '다시', title: retake.title, isTest: true }] : [];
    const points = [...start, ...axisTrend(history, axis.key).filter((point) => point.order > retake.after)];
    return { axis, points, change: axisChange(points, axis, data.judgmentPersonality?.axisScores?.[axis.key]) };
  });
}

const movedText = (change) => (change.steady ? '거의 그대로' : `+${Math.abs(change.moved)}%p ${change.toward}`); // 늘어난 쪽과 그 양(함유량의 말)

// 성향 변경폭 — 영역 다툼. 막대는 판결에서 드러난 성향과 같은 "지금" 함유량 막대(왼쪽 엄격 남색 | 오른쪽 관대 갈색)이고,
// 처음 → 지금 경계 사이(늘어난 몫)는 늘어난 쪽 색 빗금 화살표(머리 끝이 지금 경계, 늘어난 쪽이 상대 쪽으로 밀고 들어간 방향)이고, 양 끝 경계에 같은 검정 세로선.
// 위 글자는 양쪽 비중 처음 → 지금(늘어난 쪽만 그 색 · 굵게), 오른쪽 글자는 늘어난 양. 축 이름은 막대 왼쪽
function changeRow({ axis, points, change }) {
  const item = element('li', 'axis-row change-row');
  item.append(element('p', 'axis-label', axis.label));
  if (points.length === 0) {
    item.append(element('p', 'axis-empty', '체험 자료가 아직 없어요'));
    return item;
  }
  const from = points[0];
  const to = change?.to ?? points[points.length - 1];
  const fromStrict = 100 - lenientPercent(from.ratio);
  const toStrict = 100 - lenientPercent(to.ratio);
  const moving = change && !change.steady;
  const grew = moving ? (change.moved > 0 ? 'lenient' : 'strict') : null; // 늘어난 쪽
  const ends = element('div', 'axis-ends');
  const side = (name, fromPct, toPct, key) => {
    const text = change ? (key === 'strict' ? `${name} ${fromPct} → ${toPct}%` : `${fromPct} → ${toPct}% ${name}`) : (key === 'strict' ? `${name} ${toPct}%` : `${toPct}% ${name}`);
    const major = moving ? grew === key : !change && (key === 'strict' ? toStrict > 50 : toStrict < 50);
    return element('span', `is-${key}${major ? ' is-major' : ''}`, text);
  };
  ends.append(side(axis.strict.name, fromStrict, toStrict, 'strict'), side(axis.lenient.name, 100 - fromStrict, 100 - toStrict, 'lenient'));

  const track = element('div', 'change-track');
  track.setAttribute('role', 'img');
  track.setAttribute('aria-label', change
    ? `${axis.strict.name} ${fromStrict}%에서 ${toStrict}%, ${axis.lenient.name} ${100 - fromStrict}%에서 ${100 - toStrict}%. ${movedText(change)}`
    : `${axis.strict.name} ${toStrict}%, ${axis.lenient.name} ${100 - toStrict}%`);
  const bar = element('div', 'change-bar');
  const strictPart = element('span', 'is-strict');
  const lenientPart = element('span', 'is-lenient');
  strictPart.style.flexGrow = String(toStrict);
  lenientPart.style.flexGrow = String(100 - toStrict);
  bar.append(strictPart, lenientPart);
  track.append(bar);
  if (moving) {
    // 늘어난 몫(처음 경계 ~ 지금 경계)을 몸통 · 머리 한 덩어리 빗금 화살표로. 엄격이 늘면 경계가 오른쪽으로 가서 오른쪽을 가리킨다
    const gain = element('span', `change-gain is-${grew}`);
    gain.style.left = `${Math.min(fromStrict, toStrict)}%`;
    gain.style.width = `${Math.abs(toStrict - fromStrict)}%`;
    // 화살표 뒤(처음 ~ 지금 경계)의 막대는 비운다(.change-bar 마스크)
    bar.style.setProperty('--gap-a', gain.style.left);
    bar.style.setProperty('--gap-b', `${Math.max(fromStrict, toStrict)}%`);
    track.append(gain);
  }
  // 경계 세로선: 지금 경계(막대의 두 색 사이)와, 바뀌었으면 처음 경계. 화살표의 양 끝이 된다. 끝(0 · 100%)에 붙은 경계는 긋지 않는다
  const edges = change ? [fromStrict, toStrict] : [toStrict];
  edges.filter((edge, index) => edge > 0 && edge < 100 && edges.indexOf(edge) === index).forEach((edge) => {
    const line = element('span', 'change-edge');
    line.style.left = `${edge}%`;
    track.append(line);
  });
  const detail = change ? movedText(change) : leanText(from.ratio, axis); // 점이 하나뿐이면 그 비중
  item.append(ends, track, element('p', `change-detail${moving ? ` is-moved is-${grew}` : ''}`, detail));
  return item;
}

// head: 안내(막대 · 빗금 화살표)를 제목 옆에 붙일 자리. retakeTrends: 다시 정한 뒤 → 지금(FE-77), 다시 정하지 않았으면 null
function renderChanges(trends, hasHistory, head, retakeTrends) {
  const box = element('div', 'my-detail');
  if (!hasHistory) {
    box.append(element('p', 'my-hint', '사건을 하나 마치면 성향 테스트 때와 비교해 얼마나 움직였는지 보여요.'));
    return box;
  }
  const changes = trends.filter((trend) => trend.change);
  // 움직인 축이 있으면 머리 문장 없이 막대만 보인다
  const lead = changes.length === 0 ? '한 건을 더 체험하면 움직인 폭이 보여요.' : changes.every((trend) => trend.change.steady) ? '네 축 모두 초심과 거의 같아요.' : null;
  if (lead) box.append(element('p', 'change-lead', lead));
  const hint = element('p', 'my-hint', '막대는 최근(최근 체험 3건의 평균)이에요. 빗금 화살표는 초심(성향 테스트, 없으면 첫 체험)에서 최근까지 늘어난 몫이에요.');
  if (IS_MOCK) hint.append(mockTag());
  head.append(hint);
  const list = element('ul', 'axis-list change-list');
  trends.forEach((trend) => list.append(changeRow(trend)));
  box.append(list);
  if (retakeTrends) {
    box.append(element('h3', 'change-sub-title', '재심 → 최근'));
    if (retakeTrends.some((trend) => trend.change)) {
      const after = element('ul', 'axis-list change-list');
      retakeTrends.forEach((trend) => after.append(changeRow(trend)));
      box.append(after);
    } else {
      box.append(element('p', 'my-hint', '재심 뒤에 사건을 마치면 그때부터 움직인 폭이 보여요.'));
    }
  }
  return box;
}

// 책갈피 캐릭터 고르기(프로필카드 화면, FE-68에서 만난 모습에서 옮김): 얼굴 라디오. 고르면 바로 저장되고 판결 체험 책갈피 · 기본 대표 이미지에 쓰인다
function renderBookmarkPicker(onPick) {
  const field = element('fieldset', 'my-bookmark');
  field.id = 'my-bookmark-picker';
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
        input.addEventListener('change', () => { saveDeco({ bookmarkCharacter: who, bookmarkFace: face, bookmarkFaceStyle: style }); onPick?.(); });
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
    input.addEventListener('change', () => { saveDeco({ bookmarkCharacter: who }); onPick?.(); });
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
  const caption = element('p', 'collection-label', label);
  caption.id = `collection-zoom-label-${Date.now()}`;
  dialog.setAttribute('aria-labelledby', caption.id); // 스크린리더가 "대화상자"만 읽지 않게 캡션을 이름으로 쓴다
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  dialog.addEventListener('close', () => dialog.remove());
  dialog.append(picture(src, alt === label ? '' : alt), caption, button('닫기', () => dialog.close(), 'secondary-button'));
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
  box.append(element('p', 'my-hint', '체험한 사건에서 만난 캐릭터 모습과 일러스트를 모아 두는 곳이에요.'));
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

const CASES_PER_PAGE = 10; // 내가 체험한 사건은 한 페이지에 이만큼만 보여 준다

// 사건 한 줄: [제목(링크) · 상태 | 마친 사건은 버튼 둘, 체험 중이면 안내 문구]
function caseLine(item, navigate, api) {
  const status = IS_MOCK && MOCK_IN_PROGRESS.has(item.caseId) ? 'REVIEWED' : item.status; // 목: 이 화면에서만 몇 건을 체험 중으로 보여 준다(위 상수)
  const line = element('li', 'my-case');
  const info = element('div');
  // 사건 제목이 사건 디테일(기사 페이지)로 가는 링크다. 거기서 이어서 · 결과만 · 1막부터를 고른다(FE-62). 새 탭 · 가운데 클릭이 되도록 진짜 링크로 두고, 보통 클릭만 화면 안에서 이동한다
  const title = element('p', 'my-case-title');
  const link = element('a', 'my-case-link', item.title);
  link.href = `/cases/${item.caseId}`;
  link.addEventListener('click', (event) => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    navigate('caseDetail', { caseId: item.caseId });
  });
  title.append(link);
  info.append(title, element('p', 'my-case-status', STATUS_LABELS[status] ?? '체험 중'));
  const actions = element('div', 'my-case-actions');
  if (status === 'COMPLETED') {
    // 내 결과 보기(FE-75): 6막 지면 모양의 내 결과 지면(처음 판단 → 최종 판결 · 이 사건으로 달라진 성향 · 이 사건의 판결 성향)
    // hasResult: false는 목의 페이지 확인용 추가 줄(결과가 사건 번호로 찾아져 원래 사건 결과가 나오므로 숨긴다). 실제 API에는 없는 값이라 늘 보인다
    if (item.hasResult !== false) actions.append(button('내 결과 보기', () => navigate('myResult', { caseId: item.caseId }), 'secondary-button'));
    actions.append(
      button('독자 엽서 쓰기', (event) => openCaseReviewPopup({ caseId: item.caseId, api, navigate, from: event.currentTarget }), 'secondary-button'),
    );
  } else {
    actions.append(element('p', 'my-case-hint', '사건 제목을 눌러 이어서 체험해요'));
  }
  line.append(info, actions);
  return line;
}

// 페이지 이동 줄: 이전 · 번호 · 다음. 지금 페이지는 aria-current. 한 페이지를 넘을 때만 보인다
function casePager(pageCount, current, onGo) {
  const nav = element('nav', 'my-pager');
  nav.setAttribute('aria-label', '체험한 사건 페이지');
  const step = (label, to, disabled) => {
    const node = button(label, () => onGo(to), 'secondary-button my-pager-step');
    node.disabled = disabled;
    return node;
  };
  nav.append(step('이전', current - 1, current === 1));
  for (let page = 1; page <= pageCount; page += 1) {
    const node = button(String(page), () => onGo(page), `secondary-button my-pager-page${page === current ? ' is-current' : ''}`);
    node.setAttribute('aria-label', `${page}페이지`);
    if (page === current) node.setAttribute('aria-current', 'page');
    nav.append(node);
  }
  nav.append(step('다음', current + 1, current === pageCount));
  return nav;
}

function renderCases(experiences, navigate, api) {
  const box = element('div', 'my-detail');
  if (experiences.length === 0) {
    const empty = element('div', 'my-empty');
    empty.append(element('p', '', '아직 체험한 사건이 없어요.'), button('사건 목록 보기', () => navigate('list'), 'secondary-button'));
    box.append(empty);
    return box;
  }
  const list = element('ul', 'my-cases');
  const pageCount = Math.ceil(experiences.length / CASES_PER_PAGE);
  const pager = element('div', 'my-pager-host');
  function show(page, moved) {
    list.replaceChildren(...experiences.slice((page - 1) * CASES_PER_PAGE, page * CASES_PER_PAGE).map((item) => caseLine(item, navigate, api)));
    pager.replaceChildren(...(pageCount > 1 ? [casePager(pageCount, page, (to) => show(to, true))] : []));
    if (!moved) return;
    pager.querySelector('[aria-current="page"]')?.focus({ preventScroll: true }); // 눌렀던 번호 버튼은 새로 그려져 없어지므로 키보드 초점을 지금 페이지 번호로 옮긴다
    list.scrollIntoView({ block: 'start', behavior: reducedMotion() ? 'auto' : 'smooth' }); // 페이지를 넘기면 목록 맨 위부터 보이게
  }
  show(1, false);
  box.append(list, pager);
  return box;
}

// 성향 설명(성향 테스트 결과 유형): 성향 막대 아래에 둔다. 이름은 왼쪽 카드에 있어 설명만
const typeDescription = (type) => element('p', 'my-type-desc', type.oneLiner);

// 첫 화면 성향카드와 같은 타로카드 모양(이름 · 성향 이미지 · 캐릭터 대사). 누르는 카드가 아니라 div. type이 없으면 빈 그림 창
// 내 결과 지면(FE-75)도 이 사건의 성향카드로 쓴다
export function typeFace(type, emptyNote = '"성향 테스트로 카드를 뽑아 봐요"') {
  const card = element('div', `my-card is-type is-detail${type ? '' : ' is-empty'}`);
  card.setAttribute('role', 'group');
  card.setAttribute('aria-label', type ? `성향카드: ${type.name}` : '성향카드: 아직 몰라요');
  card.append(element('p', 'my-card-name', type ? type.name : '아직 몰라요'), type ? typePortrait(type) : emptyWindow(), element('p', 'my-card-note my-type-quote', type ? type.meme : emptyNote));
  return card;
}

// 결재 도장 "공개"(판사의 책상 은유). 버튼 이름(aria-label)이 공개 여부를 읽어 주므로 도장은 읽지 않는다
function shownStamp() {
  const stamp = element('span', 'my-stamp', '공개');
  stamp.setAttribute('aria-hidden', 'true');
  return stamp;
}

// 처음 → 다시 정함 → 지금 줄(FE-77). 카드를 누르면 그 카드에 "공개" 도장을 찍는다 — 도장 찍힌 카드가 커뮤니티에서 다른 사람에게 보이는 성향카드다.
// 처음은 initialPersonality(첫 테스트, 테스트 전이면 첫 판결). 서버가 아직 주지 않으면(실제 모드, 후속 BE) 테스트 현재 결과로 둔다.
// 가운데는 다시 정했으면 그 결과 카드(공개 후보), 아니면 카드 크기의 점선 빈 칸이 "다시 정하기" 버튼이다
// 초심 · 재심 · 최근 세 칸의 카드(없으면 null). 성향카드 화면의 줄과 내 정보 수정의 겹친 카드가 같이 쓴다
function typeSlotsOf(data) {
  const initial = data.initialPersonality === undefined ? data.personality : data.initialPersonality;
  const tested = Boolean(initial?.type) && initial.source !== 'JUDGMENT'; // 첫 테스트를 했는지(처음 카드 · 다시 정하기는 첫 테스트가 있어야 한다)
  const retakeDone = retakeState(data) === 'done';
  return {
    initial,
    tested,
    retakeDone,
    slots: {
      initial: tested ? { type: initial.type, caption: '초심 · 성향 테스트' } : null,
      retake: retakeDone ? { type: data.baselineRetake.result.type, caption: ['재심', retakeAfter(data.baselineRetake.result)].filter(Boolean).join(' · ') } : null,
      current: data.judgmentPersonality?.type ? { type: data.judgmentPersonality.type, caption: `최근 · 체험 ${data.judgmentPersonality.experienceCount ?? 3}건 평균` } : null,
    },
  };
}

// 내 정보 수정: 성향카드를 겹쳐 놓고(y는 같고 x만 다르다) 오른쪽에 버튼을 둔다. 고른 카드가 맨 왼쪽 · 맨 앞이다.
// 오른쪽: 초심 · 재심 · 최근 버튼 세 개(고른 카드가 커뮤니티에 보이는 공개 카드, 성향카드 화면의 "공개" 도장과 같은 값),
// 첫 성향 테스트 하기 · 재심 청구하기(할 수 없어도 숨기지 않고 비활성으로 둔다. 끝나면 문구만 바뀐다), 성향카드 보기
// 내 정보 수정의 칸 하나: 제목 → 조작 → 설명(있으면). myProfileEdit.css의 .edit-field와 같은 모양
function editGroup(title, control, hint) {
  const group = element('div', 'edit-field');
  group.append(element('span', 'edit-label', title), control, ...(hint ? [element('p', 'edit-hint', hint)] : []));
  return group;
}

function typeFan(data, navigate, go) {
  const { slots, tested, retakeDone } = typeSlotsOf(data);
  const names = { initial: '초심', retake: '재심', current: '최근' };
  const box = element('div', 'edit-fan');
  const stack = element('div', 'edit-fan-stack');
  const side = element('div', 'edit-fan-side');
  const bar = element('div', 'edit-actions');
  const buttons = new Map();
  const paint = () => {
    const shown = shownSlot(slots);
    const order = shown ? [shown, ...SHOWN_SLOTS.filter((key) => key !== shown && slots[key])] : [];
    stack.style.setProperty('--n', String(Math.max(order.length, 1)));
    stack.replaceChildren(...order.map((key, index) => {
      const card = element('button', 'edit-fan-card');
      card.type = 'button';
      card.style.setProperty('--i', String(index));
      card.style.zIndex = String(order.length - index);
      card.setAttribute('aria-pressed', String(key === shown));
      card.setAttribute('aria-label', `${names[key]}: ${slots[key].type.name}${key === shown ? ', 공개 카드' : ''}. 누르면 이 카드를 맨 앞에 두고 커뮤니티에 보여요`);
      const face = typeFace(slots[key].type);
      face.removeAttribute('role'); // 버튼 이름(aria-label)이 대신한다
      card.append(face);
      if (key === shown) card.append(shownStamp());
      card.addEventListener('click', () => pick(key));
      return card;
    }));
    buttons.forEach((node, key) => node.setAttribute('aria-pressed', String(key === shown)));
  };
  const pick = (key) => {
    if (key === shownSlot(slots)) return;
    saveSettingsOr(setShownCard(key));
    paint();
  };
  SHOWN_SLOTS.forEach((key) => {
    const node = element('button', 'secondary-button edit-choice', names[key]);
    node.type = 'button';
    node.disabled = !slots[key];
    if (!slots[key]) node.setAttribute('aria-label', `${names[key]}: 아직 카드가 없어요`);
    node.addEventListener('click', () => pick(key));
    buttons.set(key, node);
    bar.append(node);
  });
  // 첫 테스트 · 재심 청구: 상태에 따라 문구와 눌림만 바뀐다
  const state = retakeState(data);
  const firstTest = button(tested ? '첫 성향 테스트 완료' : '첫 성향 테스트 하기', () => navigate('test'), 'secondary-button');
  firstTest.disabled = tested;
  const claim = button(retakeDone ? '재심 완료' : '재심 청구하기', () => openRetakeDialog({ data, navigate, from: claim }), 'secondary-button');
  claim.disabled = state !== 'ready';
  // 재심을 마친 뒤의 "체험 n건 뒤" 안내는 성향카드 화면에만 둔다
  const note = retakeDone ? null
    : tested ? `성향 테스트를 한 번 더 해서 성향카드를 다시 정해요. ${RETAKE_NOTES[state]}` : '재심은 첫 성향 테스트 뒤에 청구할 수 있어요.';
  const tests = element('div', 'edit-actions'); // 첫 테스트 옆에 재심
  tests.append(firstTest, claim);
  paint();
  // 오른쪽은 내 정보 수정의 다른 칸과 같은 묶음(제목 → 조작 → 설명)을 세 개 쌓는다
  side.append(
    editGroup('커뮤니티에 보일 성향카드', bar, '고른 카드가 맨 앞에 놓이고 "공개" 도장이 찍혀요. 프로필카드와 성향카드는 커뮤니티에서 항상 다른 사람에게 보여요.'),
    editGroup('성향 테스트 하러 가기', tests, note),
    editGroup('성향카드 확인 및 꾸미기', button('성향카드에서 보기', () => go('type'), 'secondary-button')),
  );
  box.append(stack, side);
  return box;
}

function typeShift(data, navigate) {
  const box = element('div', 'my-type-shift-wrap');
  const row = element('div', 'my-type-shift');
  const { initial, tested, retakeDone, slots } = typeSlotsOf(data);
  const shown = shownSlot(slots);
  const side = (caption, node) => {
    const figure = element('figure', 'my-type-shift-side');
    figure.append(node, element('figcaption', '', caption));
    return figure;
  };
  // 카드가 있는 칸은 누르는 카드(공개 카드 고르기), 없는 칸은 빈 카드
  const pickable = (key, fallbackCaption, emptyNote) => {
    const slot = slots[key];
    if (!slot) return side(fallbackCaption, typeFace(null, emptyNote));
    const pick = element('button', 'my-shown-pick');
    pick.type = 'button';
    pick.dataset.slot = key;
    pick.setAttribute('aria-pressed', String(key === shown));
    pick.setAttribute('aria-label', `${slot.caption}: ${slot.type.name}${key === shown ? ', 공개 카드' : ''}. 누르면 이 카드를 커뮤니티에 보여요`);
    const face = typeFace(slot.type);
    face.removeAttribute('role'); // 버튼 이름(aria-label)이 대신한다
    pick.append(face);
    if (key === shown) pick.append(shownStamp());
    pick.addEventListener('click', () => {
      if (key === shown) return;
      saveSettingsOr(setShownCard(key));
      const next = typeShift(data, navigate);
      box.replaceWith(next);
      next.querySelector(`[data-slot="${key}"]`)?.focus();
    });
    return side(slot.caption, pick);
  };
  // 카드 크기의 점선 빈 칸 버튼(첫 테스트 하기 · 다시 정하기)
  const dashed = (title, note, onClick, disabled = false) => {
    const slot = element('button', 'my-retake-slot');
    slot.type = 'button';
    slot.disabled = disabled;
    slot.append(svg('M8 3v10M3 8h10', 'my-retake-plus'), element('strong', '', title), element('span', 'my-retake-note', note));
    slot.addEventListener('click', () => onClick(slot));
    return slot;
  };
  const firstTest = () => navigate('test'); // 첫 테스트는 일반 응시다(다시 정하기 아님)
  const arrow = () => svg('M2 8h11M9 4l4 4-4 4', 'my-type-shift-arrow');
  // 처음: 첫 테스트를 했으면 그 카드. 안 했으면 점선 칸이 첫 테스트로 이어진다(첫 판결 유형은 안내로만 보인다)
  const first = tested
    ? pickable('initial', '초심 · 성향 테스트')
    : side('초심 · 첫 테스트 전', dashed('첫 성향 테스트 하기', initial?.type ? `첫 판결로는 ${initial.type.name}` : '초심 카드가 여기 놓여요', firstTest));
  let middle;
  if (retakeDone) {
    middle = pickable('retake', '재심');
  } else {
    const state = retakeState(data);
    middle = state === 'needTest' || (state === 'soon' && !tested)
      ? side('재심 · 1회', dashed('첫 성향 테스트 하기', '재심은 첫 테스트 뒤에 청구할 수 있어요.', firstTest))
      : side('재심 · 1회', dashed('재심 청구하기', RETAKE_NOTES[state], (from) => openRetakeDialog({ data, navigate, from }), state !== 'ready'));
  }
  // 지금: 판결 체험이 한 건도 없으면 점선 칸이 사건 목록(신문 지면)으로 이어진다
  const hasJudged = (data.history ?? []).length > 0;
  const now = hasJudged
    ? pickable('current', '최근 · 체험 3건 평균', '"네 축이 모두 모이면 보여요"')
    : side('최근 · 판결 체험 전', dashed('판결 체험하러 가기', '사건을 마치면 최근 카드가 놓여요', () => navigate('list')));
  row.append(
    first,
    arrow(),
    middle,
    arrow(),
    now,
  );
  box.append(row, element('p', 'my-shown-hint', '"공개" 도장이 찍힌 카드가 커뮤니티에서 다른 사람에게 보여요. 카드를 눌러 바꿀 수 있어요.'));
  return box;
}

// 성향카드 화면 — 왼쪽에 지금 유형 타로카드(이름 · 성향 이미지 · 캐릭터 대사), 오른쪽에 판결에서 드러난 성향, 그 아래에 성향 변경폭 · 성향 변화 흐름
function renderTypePage(data, navigate) {
  const box = element('div', 'my-detail');
  const judgment = element('section', 'my-type-section');
  const judgmentHead = element('div', 'my-type-head');
  judgmentHead.append(element('h2', 'my-section-title', '판결에서 드러난 성향'));
  judgment.append(judgmentHead, renderJudgment(data.judgmentPersonality, judgmentHead));
  // 왼쪽 카드 · 설명은 옆 막대(현재 판결 성향)와 같은 유형이고, 첫 화면 성향카드와도 같다
  const heroType = currentTypeOf(data);
  if (heroType) {
    const hero = element('section', 'my-type-hero');
    const card = typeFace(heroType);
    judgment.append(typeDescription(heroType)); // 성향 설명은 성향 막대 아래로
    hero.append(card, judgment);
    box.append(hero);
  } else {
    const empty = element('div', 'my-empty');
    empty.append(element('p', '', '아직 내 판결 성향을 몰라요. 성향 테스트는 3분이면 끝나요.'), button('성향 테스트 하러 가기', () => navigate('test'), 'primary-button'));
    box.append(empty, judgment);
  }
  const history = data.history ?? [];
  const trends = trendsOf(data);
  const changes = element('section', 'my-type-section');
  const changesHead = element('div', 'my-type-head');
  changesHead.append(element('h2', 'my-section-title', '성향 변경폭'));
  const retake = retakeOf(data);
  changes.append(changesHead, renderChanges(trends, history.length > 0, changesHead, retake ? retakeTrendsOf(data, retake) : null));
  changes.append(typeShift(data, navigate)); // 체험 전에도 처음 → 다시 정함 → 지금 줄은 보인다(빈 칸은 점선 버튼)
  const trail = element('section', 'my-type-section');
  trail.append(element('h2', 'my-section-title', '성향 변화 흐름'), renderTrend(trends, history, data.judgmentPersonality, { mock: IS_MOCK, retake }));
  box.append(changes, trail);
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

// 대표 이미지: 기본 제공(내로 · 남불 스탠딩) + 나머지 후보를 종류별로. 잠긴 종류는 실루엣으로만 보인다.
// 기본 제공 두 장은 "서 있는 컷"에도 들어 있지만 같은 칸이 두 번 나오지 않게 기본에만 둔다
function portraitPicker(open, shownPortrait, onPick) {
  const field = element('fieldset', 'my-deco');
  field.append(element('legend', '', '대표 이미지'), element('p', 'my-hint', '종류마다 도전과제를 깨면 열려요. 고르면 바로 마이페이지 카드에 쓰여요.'));
  const thumb = (src) => {
    const box = element('span', 'my-deco-thumb');
    box.append(picture(src, ''));
    return box;
  };
  const pick = (portrait) => () => { saveDeco({ portrait }); onPick(); };
  const first = element('div', 'my-deco-grid');
  DEFAULT_PORTRAITS.forEach((src) => first.append(decoOption({
    group: 'profile-portrait', value: src, checked: src === shownPortrait, locked: false, name: `기본 · ${portraitLabel(src)} 스탠딩`, onChange: pick(src),
  }, thumb(src))));
  const head = element('h3', 'my-deco-kind', `기본 (${DEFAULT_PORTRAITS.length})`);
  head.append(element('span', 'my-deco-state is-open', '기본 제공'));
  field.append(head, first);
  imageKinds(PORTRAIT_POOL.filter((src) => !DEFAULT_PORTRAITS.includes(src))).forEach((kind) => {
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

// 카드 테두리 · 배경 견본, 책갈피 색 · 모양 견본. 견본은 그 요소만 바꾼 작은 카드다.
// base: 견본에 깔 나머지 꾸미기(모양 견본은 그 모양에서 지금 고른 색으로 그린다)
const SWATCH_TYPES = {
  frame: { list: FRAMES, set: 'frames', title: '카드 테두리', group: 'profile-frame' },
  background: { list: BACKGROUNDS, set: 'backgrounds', title: '카드 배경', group: 'profile-background' },
  sashStyle: { list: SASH_STYLES, set: 'sashStyles', title: '모양', group: 'profile-sash-style' },
};
const { portrait: _portrait, ...PLAIN_DECO } = DEFAULT_DECO;
const miniCard = (look) => {
  const sample = element('span', `my-mini-card deco-frame-${look.frame} deco-bg-${look.background} ${sashClasses(look)}`);
  sample.append(...sashes(look));
  return sample;
};
// 해금 요소 하나의 라디오 칸: 잠김 여부 · 잠금 사유 · 이름 캡션을 한곳에서 만든다. reason: 캡션에 잠금 사유를 덧붙일지(칩 모양은 좁아서 뺀다)
function unlockOption(open, set, key, { group, id, name, checked, art, onChange, reason = true }) {
  const locked = !open[set].has(key);
  const by = unlockerOf(set, key)?.title ?? '';
  const caption = element('span', 'my-deco-name', name);
  if (locked && reason) caption.append(element('small', '', by));
  return decoOption({ group, value: id, checked, locked, name, lockedBy: by, onChange }, art, caption);
}
function swatchPicker(type, open, shown, onPick, base = PLAIN_DECO, legendClass = '') {
  const { list, set, title, group } = SWATCH_TYPES[type];
  const field = element('fieldset', `my-deco${legendClass ? ` ${legendClass}` : ''}`);
  field.append(element('legend', '', title));
  const row = element('div', 'my-deco-swatches');
  list.forEach(({ id, name }) => {
    row.append(unlockOption(open, set, id, {
      group, id, name, checked: id === shown, art: miniCard({ ...base, [type]: id }),
      onChange: () => { saveDeco({ [type]: id }); onPick(); },
    }));
  });
  field.append(row);
  return field;
}

// 짧은 책갈피의 한쪽(좌 · 우) 아이콘 고르기
function sashIconPicker(side, open, deco, onPick) {
  const key = side === 'left' ? 'sashIconLeft' : 'sashIconRight';
  const field = element('fieldset', 'my-deco is-sub');
  field.append(element('legend', '', side === 'left' ? '왼쪽 아이콘' : '오른쪽 아이콘'));
  const row = element('div', 'my-deco-pills');
  SASH_ICONS.forEach(({ id, name }) => {
    const chip = element('span', `my-icon-chip deco-sash-${sashColorOf(deco) ?? 'wine'}`);
    const art = sashIcon(id);
    if (art) chip.append(art);
    const option = unlockOption(open, 'sashIcons', id, {
      group: `profile-${key}`, id, name, checked: id === deco[key], art: chip, reason: false,
      onChange: () => { saveDeco({ [key]: id }); onPick(); },
    });
    option.classList.add('is-pill');
    row.append(option);
  });
  field.append(row);
  return field;
}

// 지금 모양의 색 고르기. 색 표는 모양마다 따로 있고(SASH_COLORS), 해금도 "모양:색"으로 따로 센다
function sashColorPicker(open, deco, onPick) {
  const style = deco.sashStyle;
  const field = element('fieldset', 'my-deco is-sub');
  field.append(element('legend', '', `색 · ${nameOf(SASH_STYLES, style)}`));
  const row = element('div', 'my-deco-swatches');
  SASH_COLORS[style].forEach(({ id, name }) => {
    const key = sashColorKey(style, id);
    row.append(unlockOption(open, 'sashColors', key, {
      group: `profile-sash-color-${style}`, id, name, checked: id === sashColorOf(deco),
      art: miniCard({ ...PLAIN_DECO, sashStyle: style, [sashColorField(style)]: id }),
      onChange: () => { saveDeco({ [sashColorField(style)]: id }); onPick(); },
    }));
  });
  field.append(row);
  return field;
}

// 카드 책갈피 묶음: 모양(맨 위) → 그 모양의 색 → (짧은 책갈피면) 좌우 아이콘. 고른 것에 따라 아래 칸이 바뀌므로 묶음을 다시 그리고 포커스를 되돌린다
function sashSection(open, onPick) {
  const section = element('section', 'my-deco-section');
  const paint = (focusName, focusValue) => {
    const deco = shownDeco(currentDeco(), open);
    const repaint = () => {
      const active = document.activeElement;
      paint(active?.name, active?.value);
      onPick();
    };
    const parts = [
      element('h2', 'my-deco-title', '카드 책갈피'),
      element('p', 'my-hint', '카드 윗변 좌우에 거는 띠예요. 모양을 먼저 고르고, 색은 모양마다 따로 골라요. 책갈피 설정(얼굴 · 리본)과는 별개예요.'),
      swatchPicker('sashStyle', open, deco.sashStyle, repaint, deco, 'is-sub'),
    ];
    if (deco.sashStyle !== 'none') parts.push(sashColorPicker(open, deco, repaint));
    if (deco.sashStyle === 'short') parts.push(sashIconPicker('left', open, deco, repaint), sashIconPicker('right', open, deco, repaint));
    else if (deco.sashStyle === 'long') parts.push(element('p', 'my-hint', '아이콘은 짧고 굵은 책갈피에만 달 수 있어요.'));
    section.replaceChildren(...parts);
    if (focusName) [...section.querySelectorAll(`input[name="${focusName}"]`)].find((input) => input.value === focusValue)?.focus({ preventScroll: true });
  };
  paint();
  return section;
}

// 보상을 한 줄로: "장면 컷 16장 · 원목 테두리"
function rewardText({ rewards }) {
  const count = (kind) => PORTRAIT_POOL.filter((src) => kindOfPortrait(src) === kind).length;
  const given = (type) => rewards?.[type] ?? [];
  return [
    ...given('kinds').map((kind) => `${KIND_TITLES[kind] ?? kind} ${count(kind)}장`),
    ...given('frames').map((id) => `${nameOf(FRAMES, id)} 테두리`),
    ...given('backgrounds').map((id) => `${nameOf(BACKGROUNDS, id)} 배경`),
    ...given('sashStyles').map((id) => `책갈피 모양 ${nameOf(SASH_STYLES, id)}`),
    ...given('sashColors').map((key) => {
      const [style, color] = key.split(':');
      return `${nameOf(SASH_STYLES, style)} ${nameOf(SASH_COLORS[style] ?? [], color)}`;
    }),
    ...given('sashIcons').map((id) => `책갈피 아이콘 ${nameOf(SASH_ICONS, id)}`),
  ].join(' · ');
}

// 서버가 내려 준 도전과제(P-5 achievements)를 제목 · 문구와 함께 그린다. PENDING(수집 도전과제, BE-83 전)은 아직 셀 수 없어 "준비 중"으로 둔다
function achievementList(achievements) {
  const progress = achievements.map(achievementView);
  const section = element('section', 'my-achievements-section');
  section.id = 'my-achievements';
  const done = progress.filter((item) => item.cleared).length;
  section.append(element('h2', 'my-section-title', `달성 ${done} / ${progress.length}`));
  const list = element('ol', 'my-achievements');
  progress.forEach((item) => {
    const line = element('li', `my-achievement${item.cleared ? ' is-cleared' : ''}`);
    const head = element('div', 'my-achievement-head');
    const pending = item.status === 'PENDING' || item.value == null;
    const value = pending ? 0 : item.value;
    const state = element('span', 'my-achievement-state', pending ? '준비 중' : item.cleared ? '달성' : `${value} / ${item.goal}`);
    if (IS_MOCK) state.append(mockTag());
    head.append(element('strong', '', item.title), state);
    const bar = element('div', 'my-achievement-bar');
    bar.setAttribute('role', 'progressbar');
    bar.setAttribute('aria-label', `${item.title} 진행도`);
    bar.setAttribute('aria-valuemin', '0');
    bar.setAttribute('aria-valuemax', String(item.goal));
    bar.setAttribute('aria-valuenow', String(value));
    if (pending) bar.setAttribute('aria-valuetext', '아직 판정할 수 없어요');
    const fill = element('span');
    fill.style.width = `${Math.round((value / item.goal) * 100)}%`;
    bar.append(fill);
    line.append(head, element('p', 'my-achievement-text', item.text), bar, element('p', 'my-achievement-reward', `보상: ${rewardText(item)}`));
    list.append(line);
  });
  section.append(list);
  return section;
}

// 미리 보기 카드(첫 화면 카드와 같은 모양, 누를 수 없음)
function previewCard(data, deco = decoFor(data)) {
  const parts = profileCardParts(deco, data);
  const card = element('div', `my-card ${parts.classes} is-preview`);
  card.setAttribute('role', 'img');
  card.setAttribute('aria-label', `미리 보기: ${parts.note}, ${nameOf(FRAMES, deco.frame)} 테두리, ${nameOf(BACKGROUNDS, deco.background)} 배경`);
  card.append(...parts.children);
  return card;
}

// 카드 아래 성향 고르기(FE-77): 초심 · 재심 · 최근 중 하나를 글자 라디오로. 같은 유형은 한 칸으로 묶인다. 공개 성향카드와는 별개의 값이다
function typeChoiceSection(data, onPick) {
  const field = element('fieldset', 'my-deco');
  field.append(element('legend', '', '카드 아래 성향'));
  const choices = typeChoices(data);
  if (choices.length === 0) {
    field.append(element('p', 'my-hint', '성향 테스트를 하면 카드 아래에 보여 줄 성향을 고를 수 있어요.'));
    return field;
  }
  field.append(element('p', 'my-hint', '프로필카드 아래에 보일 성향이에요. 기본은 최근이고, 같은 유형은 한 칸으로 묶여요.'));
  const selected = selectedChoice(data);
  const list = element('div', 'my-type-choices');
  choices.forEach((choice, index) => {
    const label = element('label', 'my-type-choice');
    const input = element('input');
    input.type = 'radio';
    input.name = 'profile-card-type';
    input.value = choice.pick;
    input.checked = choice === selected || (!selected && index === 0);
    input.addEventListener('change', () => { saveSettingsOr(setProfileCardType(choice.pick)); onPick(); });
    label.append(input, element('span', '', choice.label));
    list.append(label);
  });
  field.append(list);
  return field;
}

// 카드 꾸미기 창: 테두리 · 배경 · 카드 책갈피 · 카드 아래 성향을 고른다. 창 안에도 미리 보기를 두고, 고를 때마다 화면 쪽 미리 보기(onPick)도 다시 그린다.
// 마이페이지 커피색 토큰이 적용되도록 .my-page 안에 붙이고, 닫으면 지우고 연 버튼으로 포커스를 돌려준다(Esc · 바깥 · 닫기)
function openDecoDialog({ open, data, onPick, opener }) {
  const dialog = element('dialog', 'my-deco-dialog');
  const title = element('h2', 'my-deco-dialog-title', '카드 꾸미기');
  title.id = `my-deco-dialog-title-${Date.now()}`;
  dialog.setAttribute('aria-labelledby', title.id);
  const head = element('div', 'my-deco-dialog-head');
  head.append(title, button('닫기', () => dialog.close(), 'secondary-button'));
  const shown = decoFor(data);
  const preview = element('div', 'my-deco-dialog-preview');
  const paint = () => {
    preview.replaceChildren(previewCard(data));
    onPick();
  };
  const body = element('div', 'my-deco-dialog-body');
  const pickers = element('div', 'my-deco-dialog-pickers');
  pickers.append(
    element('p', 'my-hint', '도전과제를 깨면 테두리 · 배경 · 카드 책갈피가 더 열려요. 고르면 바로 저장돼요.'),
    swatchPicker('frame', open, shown.frame, paint),
    swatchPicker('background', open, shown.background, paint),
    sashSection(open, paint),
    typeChoiceSection(data, paint),
  );
  body.append(preview, pickers);
  dialog.append(head, body);
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  dialog.addEventListener('close', () => { dialog.remove(); opener?.focus(); });
  preview.replaceChildren(previewCard(data, shown));
  (document.querySelector('.my-page') ?? document.body).append(dialog);
  dialog.showModal();
}

// 요약 표의 "열린 수 / 전체": 분자 · 빗금 · 분모를 칸으로 나눠 줄마다 빗금 위치가 맞게 한다
function countValue(have, total) {
  const dd = element('dd', 'my-count-row');
  const cell = element('span', 'my-count-cell');
  cell.setAttribute('aria-label', `${have} / ${total}`);
  cell.append(element('span', 'is-have', String(have)), element('span', 'is-sep', '/'), element('span', 'is-total', String(total)));
  dd.append(cell);
  if (IS_MOCK) dd.append(mockTag());
  return dd;
}

function renderProfilePage(data, go) {
  const box = element('div', 'my-detail');
  const open = openOf(data);
  const progress = data.achievements ?? [];

  // 위: 미리 보기 카드 + 열린 개수 + 버튼(카드 꾸미기 창 · 도전과제 화면 · 책갈피 설정 화면)
  const hero = element('section', 'my-profile-hero');
  const preview = element('div', 'my-profile-preview');
  const deco = decoFor(data);
  const paintPreview = () => preview.replaceChildren(previewCard(data));
  preview.replaceChildren(previewCard(data, deco));
  const summary = element('div', 'my-profile-summary');
  const counts = element('dl', 'my-stats my-profile-counts');
  const sashTotal = SASH_STYLES.length + Object.values(SASH_COLORS).reduce((sum, list) => sum + list.length, 0) + SASH_ICONS.length;
  [
    ['대표 이미지', PORTRAIT_POOL.filter((src) => open.kinds.has(kindOfPortrait(src))).length, PORTRAIT_POOL.length],
    ['카드 테두리', open.frames.size, FRAMES.length],
    ['카드 배경', open.backgrounds.size, BACKGROUNDS.length],
    ['카드 책갈피', open.sashStyles.size + open.sashColors.size + open.sashIcons.size, sashTotal],
    ['도전과제', progress.filter((item) => item.cleared).length, progress.length],
  ].forEach(([label, have, total]) => {
    const item = element('div', 'my-stat');
    item.append(element('dt', '', label), countValue(have, total));
    counts.append(item);
  });
  const actions = element('div', 'my-profile-actions');
  const decorate = button('카드 꾸미기', () => openDecoDialog({ open, data, onPick: paintPreview, opener: decorate }), 'primary-button');
  actions.append(decorate, button('책갈피 설정', () => go('bookmark'), 'secondary-button'), button('도전과제 보기', () => go('achievements'), 'secondary-button'));
  // 개발 전용: 목 API에서 도전과제를 전부 깬 것으로 본다(잠긴 꾸미기를 확인할 때). 개발 서버 + 목 모드에서만 보인다
  if (import.meta.env?.DEV && apiRef?.setMockUnlockAll) {
    const allCleared = progress.length > 0 && progress.every((item) => item.cleared);
    actions.append(button(allCleared ? '개발용: 해금 되돌리기' : '개발용: 모두 해금', async () => {
      await apiRef.setMockUnlockAll(!allCleared);
      await refresh?.();
    }, 'secondary-button is-dev'));
  }
  summary.append(element('p', 'my-hint', '도전과제를 깨면 대표 이미지 · 테두리 · 배경 · 카드 책갈피가 열려요. 고른 것은 바로 저장돼요.'), counts, actions);
  hero.append(preview, summary);

  box.append(hero, portraitPicker(open, deco.portrait, paintPreview));
  return box;
}

// 도전과제 화면(FE-68): 진행도 · 보상 목록
function renderAchievementsPage(data) {
  const box = element('div', 'my-detail');
  box.append(element('p', 'my-hint', '도전과제를 깨면 프로필카드의 대표 이미지 · 카드 꾸미기가 열려요.'), achievementList(data.achievements ?? []));
  return box;
}

// 책갈피 설정 예시: 판결 체험 파일철처럼 색인 탭 사이에 고른 얼굴 · 리본 색의 책갈피가 끼워진 모습(펼쳐 본 장 = 2번 탭 뒤)
const EXAMPLE_TABS = ['사건 요약', '참고 정보', '판단 요소', '최종 판결'];
function bookmarkExample() {
  const figure = element('figure', 'my-bm-example');
  const art = element('div', 'my-bm-art');
  art.setAttribute('role', 'img');
  art.setAttribute('aria-label', `예시: 판결 체험 서류철의 색인 탭 2번과 3번 사이에 ${nameOf(BOOKMARK_COLORS, getBookmarkColor())} 리본 책갈피가 끼워진 모습`);
  const tabs = element('div', 'my-bm-tabs');
  EXAMPLE_TABS.forEach((label, index) => {
    const tab = element('span', `my-bm-tab${index === 1 ? ' is-current' : ''}`);
    tab.append(element('b', '', String(index + 1)), element('span', '', label));
    tabs.append(tab);
    if (index === 1) {
      const ribbon = element('span', `my-bm-ribbon bookmark-color-${getBookmarkColor()}`);
      ribbon.append(picture(selectedBookmarkFace(), ''));
      tabs.append(ribbon);
    }
  });
  const folder = element('div', 'my-bm-folder');
  const sheet = element('div', 'my-bm-sheet');
  sheet.append(element('span', 'my-bm-line'), element('span', 'my-bm-line'), element('span', 'my-bm-line is-short'));
  folder.append(sheet);
  art.append(tabs, folder);
  figure.append(art, element('figcaption', 'my-hint', '판결 체험에서 펼쳐 본 장의 색인 탭 뒤에 이 책갈피가 끼워져요.'));
  return figure;
}

// 리본 색 고르기(처음부터 다 열려 있음)
function bookmarkColorPicker(onPick) {
  const field = element('fieldset', 'my-deco');
  field.append(element('legend', '', '리본 색'));
  const row = element('div', 'my-deco-swatches');
  const current = getBookmarkColor();
  BOOKMARK_COLORS.forEach(({ id, name }) => {
    const sample = element('span', 'my-bm-swatch-box'); // 리본은 clip-path라 고른 표시(테두리선)를 감싼 상자에 그린다
    sample.append(element('span', `my-bm-swatch bookmark-color-${id}`));
    row.append(decoOption({
      group: 'bookmark-color', value: id, checked: id === current, locked: false, name,
      onChange: () => { saveDeco({ bookmarkColor: id }); onPick(); },
    }, sample, element('span', 'my-deco-name', name)));
  });
  field.append(row);
  return field;
}

// 책갈피 설정 화면(FE-68): 예시 · 리본 색 · 얼굴. 고를 때마다 예시를 다시 그린다
function renderBookmarkPage() {
  const box = element('div', 'my-detail');
  const slot = element('div', 'my-bm-slot');
  const paint = () => slot.replaceChildren(bookmarkExample());
  paint();
  box.append(slot, bookmarkColorPicker(paint), renderBookmarkPicker(paint));
  return box;
}

function renderView(view, data, navigate, go, api) {
  const head = element('div', 'my-view-head');
  const back = element('button', 'my-back');
  back.type = 'button';
  const parent = PARENTS[view] ?? null;
  back.append(backArrow(), parent ? VIEWS[parent] : '마이페이지');
  back.addEventListener('click', () => go(parent));
  const title = element('h1', '', VIEWS[view]);
  title.tabIndex = -1;
  head.append(back, title);
  const body = {
    profile: () => renderProfilePage(data, go),
    achievements: () => renderAchievementsPage(data),
    bookmark: () => renderBookmarkPage(),
    type: () => renderTypePage(data, navigate),
    collection: () => renderCollection(data.experiences),
    cases: () => renderCases(data.experiences ?? [], navigate, api),
    edit: () => renderProfileEdit({ api, navigate, go, data, card: () => profileCardOf(data, go), fan: () => typeFan(data, navigate, go) }),
    settings: () => renderSettings(),
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
  container.replaceChildren(root); // 헤더는 라우터가 붙인 공통 헤더다(FE-66)
  const active = () => container.contains(root);
  const main = element('main', 'my-main');
  root.append(main);
  const shown = VIEWS[view] ? view : null;
  const go = (next) => navigate('me', next ? { view: next } : {});
  apiRef = api;
  configureProfileSettings(api.patchProfileSettings ? (settings) => api.patchProfileSettings(settings) : null); // 공개 카드 · 카드 아래 성향 · 한 줄 소개 저장(P-8)

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
      main.replaceChildren(...renderView(shown, data, navigate, go, api));
      return;
    }
    const title = element('h1', 'visually-hidden', '마이페이지');
    title.tabIndex = -1;
    main.replaceChildren(title, renderTop(data, go), renderRows(data, go), accountNote(api, navigate));
  };

  // 꾸미기 저장이 거절되면 서버 값으로 되돌려 다시 그리고 이유를 알린다. 열려 있는 꾸미기 창은 옛 선택을 들고 있으므로 닫는다
  refresh = async (message) => {
    if (!active()) return;
    try {
      const fresh = await api.getMyPage();
      if (!active()) return;
      hydrateProfileSettings(fresh.profileSettings);
      cache = fresh;
      applyBookmark(fresh.profileDeco);
      document.querySelector('.my-deco-dialog')?.close();
      paint(fresh);
    } catch {
      // 다시 받지 못하면 고른 그대로 둔다
    }
    if (!active() || !message) return;
    const notice = element('p', 'message-panel my-save-notice', message);
    notice.setAttribute('role', 'alert');
    main.prepend(notice);
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
    data = await loadMyPage(api);
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
  applyBookmark(data.profileDeco); // 서버에 저장된 책갈피 설정을 판결 체험 화면이 읽는 거울에 맞춘다
  if (changed) {
    paint(data);
    if (shown && !from) main.querySelector('h1')?.focus({ preventScroll: true });
  }
  reveal();
}
