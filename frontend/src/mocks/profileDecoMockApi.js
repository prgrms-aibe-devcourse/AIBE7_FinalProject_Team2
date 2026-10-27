import { BACKGROUNDS, DEFAULT_PORTRAITS, FRAMES, SASH_COLORS, SASH_ICONS, SASH_STYLES, kindOfPortrait, sashColorKey } from '../utils/profileDeco.js';
import { BOOKMARK_CHARACTERS, BOOKMARK_COLORS, BOOKMARK_FACES, BOOKMARK_LAWS, BOOKMARK_STYLES } from '../utils/bookmarkCharacter.js';
import { ACHIEVEMENTS, DEFAULT_SERVER_DECO, FREE, REWARD_TYPES } from './profileDecoMockData.js';

// 프로필 꾸미기 저장(P-7) · 출석 기록(P-6) 목 API와 마이페이지(P-5)에 붙는 꾸미기 · 도전과제 응답 (BE-96 · BE-97, 명세 9장).
// 서버가 하는 일을 흉내 낸다: 진행도는 저장하지 않고 그때그때 계산, 값 목록 · 형식 → 400, 잠김 → 409. 저장값과 출석 날짜는 목 전용으로 localStorage에 둔다.

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const DECO_KEY = 'nlnb-mock-profile-deco';
const ATTENDANCE_KEY = 'nlnb-mock-attendance';
const SETTINGS_KEY = 'nlnb-mock-profile-settings';
const UNLOCK_ALL_KEY = 'nlnb-mock-unlock-all'; // 개발 전용: 도전과제를 전부 깬 것으로 본다
const EARLIER_ATTENDANCE = 11; // 목: 이전에 11일 출석했다고 친다. 오늘 한 번 부르면 12일이 된다(예전 목 값)

const read = (key, fallback) => {
  try {
    return JSON.parse(localStorage.getItem(key)) ?? fallback;
  } catch {
    return fallback;
  }
};
const write = (key, value) => {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만 바뀐다
  }
};

const MESSAGES = {
  VALIDATION_ERROR: '꾸미기 값이 올바르지 않습니다.',
  DECO_LOCKED: '아직 열리지 않은 꾸미기입니다.',
};
function fail(code, details) {
  throw { code, message: MESSAGES[code], currentStatus: null, details, status: code === 'DECO_LOCKED' ? 409 : 400 };
}

// 오늘 날짜(YYYY-MM-DD). 하루는 한국 시간 자정에서 바뀐다
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });

/** P-6 출석 기록: 오늘 날짜를 한 번만 남긴다. 항상 204(null) */
export async function postAttendance() {
  await delay();
  const days = read(ATTENDANCE_KEY, []);
  if (!days.includes(today())) write(ATTENDANCE_KEY, [...days, today()]);
  return null;
}

const attendanceDays = () => EARLIER_ATTENDANCE + read(ATTENDANCE_KEY, []).length;

/**
 * 개발 전용(실제 API에는 없다): 도전과제를 전부 깬 것으로 보게 하거나 되돌린다. 마이페이지 프로필카드 화면의 "모두 해금" 버튼이 쓴다.
 * 수집 도전과제(PENDING)도 깬 것으로 보므로 잠긴 보상까지 모두 확인해 볼 수 있다
 */
export async function setMockUnlockAll(on) {
  await delay();
  write(UNLOCK_ALL_KEY, Boolean(on));
  return null;
}

/** 도전과제 진행도. collected는 서버가 셀 수 없어 PENDING · value null · 잠김 (개발 전용 모두 해금이면 전부 달성) */
export function mockAchievements(stats) {
  const unlockAll = read(UNLOCK_ALL_KEY, false);
  return ACHIEVEMENTS.map((item) => {
    if (unlockAll) return { ...item, value: item.goal, cleared: true, status: 'CLEARED' };
    const raw = stats[item.stat];
    if (raw == null) return { ...item, value: null, cleared: false, status: 'PENDING' };
    const cleared = raw >= item.goal;
    return { ...item, value: Math.min(raw, item.goal), cleared, status: cleared ? 'CLEARED' : 'IN_PROGRESS' };
  });
}

/** 지금 열린 것: 처음부터 열린 것 + 달성한 도전과제의 보상 */
export function mockUnlocked(achievements) {
  const open = Object.fromEntries(REWARD_TYPES.map((type) => [type, [...FREE[type]]]));
  achievements.filter((item) => item.cleared).forEach((item) => REWARD_TYPES.forEach((type) => {
    item.rewards[type].forEach((id) => { if (!open[type].includes(id)) open[type].push(id); });
  }));
  return open;
}

/** 마이페이지(P-5)에 붙는 부분. stats: { completed, hasType } 마이페이지 목이 가진 기록에서 센 값 */
export function mockProfileView(stats) {
  const achievements = mockAchievements({ ...stats, attendanceDays: attendanceDays(), collected: null });
  return {
    profile: { attendanceDays: attendanceDays() },
    profileDeco: { ...DEFAULT_SERVER_DECO, ...read(DECO_KEY, {}) },
    achievements,
    unlocked: mockUnlocked(achievements),
  };
}

const PORTRAIT = /^\/images\/[A-Za-z0-9_-]+(\/[A-Za-z0-9_-]+)*\/[A-Za-z0-9_-]+\.webp$/;
const oneOf = (list) => (value) => list.includes(value);
const IDS = (list) => list.map(({ id }) => id);

/** P-7 형식 · 값 목록 검사(400) */
function checkValues(deco) {
  const rules = {
    portrait: (value) => typeof value === 'string' && PORTRAIT.test(value),
    frame: oneOf(IDS(FRAMES)),
    background: oneOf(IDS(BACKGROUNDS)),
    sashStyle: oneOf(IDS(SASH_STYLES)),
    sashColorShort: oneOf(IDS(SASH_COLORS.short)),
    sashColorLong: oneOf(IDS(SASH_COLORS.long)),
    sashIconLeft: oneOf(IDS(SASH_ICONS)),
    sashIconRight: oneOf(IDS(SASH_ICONS)),
    bookmarkCharacter: oneOf([...BOOKMARK_CHARACTERS, ...BOOKMARK_LAWS]),
    bookmarkFace: oneOf(BOOKMARK_FACES),
    bookmarkFaceStyle: oneOf(BOOKMARK_STYLES),
    bookmarkColor: oneOf(IDS(BOOKMARK_COLORS)),
  };
  const errors = Object.entries(rules).flatMap(([field, ok]) => {
    if (deco?.[field] == null) return [{ field, reason: 'REQUIRED' }];
    return ok(deco[field]) ? [] : [{ field, reason: field === 'portrait' ? 'INVALID_FORMAT' : 'INVALID_VALUE' }];
  });
  if (errors.length) fail('VALIDATION_ERROR', errors);
}

// 저장값에서 이 칸이 화면에 쓰이는지 (잠김 검사 대상이었는지)
const activeIn = (deco, field) => {
  if (['sashColorShort', 'sashIconLeft', 'sashIconRight'].includes(field)) return deco.sashStyle === 'short';
  if (field === 'sashColorLong') return deco.sashStyle === 'long';
  return true;
};

/**
 * P-7 잠김 검사(409): 지금 화면에 쓰이는 칸만 본다(고른 모양의 색, 짧은 책갈피일 때 아이콘).
 * 이미 저장해 둔 값은 다시 거절하지 않는다(BE-98): 저장값과 같고 저장할 때도 쓰이던 칸은 건너뛰고 바꾼 칸만 본다
 */
function checkLocks(deco, open, saved) {
  const has = (type, id) => open[type].includes(id);
  const locked = [];
  const lock = (field) => {
    if (saved && activeIn(saved, field) && saved[field] === deco[field]) return;
    locked.push(field);
  };
  if (!DEFAULT_PORTRAITS.includes(deco.portrait) && !has('kinds', kindOfPortrait(deco.portrait))) lock('portrait');
  if (!has('frames', deco.frame)) lock('frame');
  if (!has('backgrounds', deco.background)) lock('background');
  if (!has('sashStyles', deco.sashStyle)) lock('sashStyle');
  if (deco.sashStyle === 'short') {
    if (!has('sashColors', sashColorKey('short', deco.sashColorShort))) lock('sashColorShort');
    if (!has('sashIcons', deco.sashIconLeft)) lock('sashIconLeft');
    if (!has('sashIcons', deco.sashIconRight)) lock('sashIconRight');
  } else if (deco.sashStyle === 'long' && !has('sashColors', sashColorKey('long', deco.sashColorLong))) {
    lock('sashColorLong');
  }
  if (locked.length) fail('DECO_LOCKED', locked.map((field) => ({ field, reason: 'DECO_LOCKED' })));
}

// 열린 것을 판정하는 값(완료한 사건 수 · 성향 테스트 여부)은 마이페이지 목(personalityMockApi)이 가진 기록에서 센다.
// 그쪽이 이 파일을 불러오므로, 거꾸로 불러 순환하지 않게 마이페이지 목이 계산기를 등록해 둔다
let readStats = () => ({ completed: 0, hasType: 0 });
export const setStatsReader = (reader) => { readStats = reader; };

/** P-7 꾸미기 전체 저장 */
export async function putProfileDeco(deco) {
  await delay();
  checkValues(deco);
  const view = mockProfileView(readStats());
  checkLocks(deco, view.unlocked, read(DECO_KEY, null));
  const saved = Object.fromEntries(Object.keys(DEFAULT_SERVER_DECO).map((field) => [field, deco[field]]));
  write(DECO_KEY, saved);
  return structuredClone(saved);
}

// ── P-8 프로필 선택 설정 부분 저장 (FE-81, BE-104) ──
// 서버와 같은 규칙: 보낸 칸만 바꾸고 응답은 세 값 전체. 객체가 아님 · 빈 객체 · 모르는 칸 · null · 문자열이 아님 · 세 값 밖 · 소개 40자 초과(앞뒤 공백을 뺀 코드 포인트) · 제어 문자(탭 · 줄바꿈 등) → 400
const SETTING_SLOTS = ['initial', 'retake', 'current'];
const CARD_LINES = ['type', 'signature', 'job'];
const DEFAULT_SETTINGS = { shownCard: 'current', profileCardType: 'current', bio: '', cardLine: 'type', cardLevel: false }; // cardLine · cardLevel(FE-116): 서버가 아직 모르는 칸이라 프론트 준비용(서버 확장은 BE 요청 중)
let savedSettings = null; // 저장소를 못 쓰는 환경(테스트 등)에서도 이번 방문 동안 값을 지킨다

/** P-5 profileSettings: 저장한 적이 없으면 최근 · 최근 · 소개 없음 */
export const mockProfileSettings = () => ({ ...DEFAULT_SETTINGS, ...(savedSettings ?? read(SETTINGS_KEY, {})) });

function settingsError(field) {
  throw { code: 'VALIDATION_ERROR', message: '요청 값이 올바르지 않습니다.', currentStatus: null, details: field ? [{ field }] : null, status: 400 };
}

export async function patchProfileSettings(patch) {
  await delay();
  if (patch === null || typeof patch !== 'object' || Array.isArray(patch) || Object.keys(patch).length === 0) settingsError();
  const next = {};
  Object.entries(patch).forEach(([field, value]) => {
    if (!(field in DEFAULT_SETTINGS)) settingsError(field);
    if (field !== 'cardLevel' && typeof value !== 'string') settingsError(field);
    if (field === 'cardLevel') {
      if (typeof value !== 'boolean') settingsError(field);
      next.cardLevel = value;
    } else if (field === 'cardLine') {
      if (!CARD_LINES.includes(value)) settingsError(field);
      next.cardLine = value;
    } else if (field === 'bio') {
      const bio = value.trim();
      if ([...bio].length > 40 || /[\u0000-\u001F\u007F-\u009F\u2028\u2029]/.test(bio)) settingsError(field); // 서버(isForbidden)와 같다: 제어 문자 전체 + 줄 · 문단 구분자
      next.bio = bio;
    } else {
      if (!SETTING_SLOTS.includes(value)) settingsError(field);
      next[field] = value;
    }
  });
  savedSettings = { ...mockProfileSettings(), ...next };
  write(SETTINGS_KEY, savedSettings);
  return { ...savedSettings };
}
