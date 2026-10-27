// 프로필 선택 설정(FE-81): 공개 성향카드(shownCard) · 프로필카드 아래 성향(profileCardType) · 한 줄 소개(bio).
// 서버(P-8 PATCH /profile-settings, 조회는 P-5 profileSettings)가 원본이고, 이 모듈은 화면이 동기로 읽는 사본이다.
// 바꾸면 사본을 먼저 바꾸고(바로 화면에 반영) 서버에 보낸다. 보내는 요청은 차례로 하나씩 가고, 거절되면 그 칸을 이전 값으로 돌린다.
// 마이페이지를 받으면 서버 값으로 맞추되, 저장 중인 값이 있으면 오래된 응답이 덮어쓰지 않게 건너뛴다
import { localStore } from './safeStorage.js';
export const SLOTS = ['initial', 'retake', 'current'];
export const BIO_MAX = 40;
// 프로필카드 아래 줄(성향 · 서명 · 직업 중 하나, cardLine)과 레벨 접두(cardLevel).
// 서버(P-8)가 이 두 칸을 아직 모르면 이 브라우저에만 저장하고 서버로 보내지 않는다(모르는 칸을 보내면 400).
// 서버가 마이페이지(P-5 profileSettings)에 cardLine을 실어 보내기 시작하면 그것을 서버 지원 신호로 보고 서버 값을 따르며 PATCH에도 함께 보낸다
export const CARD_LINES = ['type', 'signature', 'job'];
const LOCAL_KEY = 'naelaw:profile-card-line';

const DEFAULTS = Object.freeze({ shownCard: 'current', profileCardType: 'current', bio: '' });
const LOCAL_DEFAULTS = Object.freeze({ cardLine: 'type', cardLevel: false });

function readLocal() {
  try {
    const saved = JSON.parse(localStore()?.getItem(LOCAL_KEY) ?? 'null');
    return { cardLine: CARD_LINES.includes(saved?.cardLine) ? saved.cardLine : LOCAL_DEFAULTS.cardLine, cardLevel: saved?.cardLevel === true };
  } catch { return { ...LOCAL_DEFAULTS }; }
}

let values = { ...DEFAULTS };
let local = readLocal();
let serverCardLine = false; // P-5 profileSettings에 cardLine이 오면 true(서버가 두 칸을 안다)
let send = null; // (patch) => Promise<세 값 전체>. api가 없으면 null이라 이번 방문 동안만 바뀐다
let inFlight = 0;
let queue = Promise.resolve();

const pickSlot = (value) => (SLOTS.includes(value) ? value : DEFAULTS.shownCard);
const normalize = (settings) => ({
  shownCard: pickSlot(settings?.shownCard),
  profileCardType: pickSlot(settings?.profileCardType),
  bio: typeof settings?.bio === 'string' ? settings.bio : '',
});

export function configureProfileSettings(sender) {
  send = typeof sender === 'function' ? sender : null;
}

export const getProfileSettings = () => values;
export const getCardLineSettings = () => local;

/** 서버가 준 값(P-5 profileSettings)으로 맞춘다. 없으면(옛 서버) 그대로 두고, 저장 중이면 건너뛴다 */
export function hydrateProfileSettings(settings) {
  if (!settings || inFlight > 0) return;
  values = normalize(settings);
  if (settings.cardLine !== undefined) {
    serverCardLine = true;
    setLocal(pickLine(settings));
  }
}

const pickLine = (settings) => ({ cardLine: CARD_LINES.includes(settings?.cardLine) ? settings.cardLine : LOCAL_DEFAULTS.cardLine, cardLevel: settings?.cardLevel === true });
function setLocal(next) {
  local = next;
  try { localStore()?.setItem(LOCAL_KEY, JSON.stringify(local)); } catch { /* 저장소를 못 쓰면 이번 방문 동안만 */ }
}

/**
 * 카드 아래 줄 설정을 바꾼다. 서버가 두 칸을 아는 경우에만 서버에 보내고(차례 · 거절 시 이전 값 복귀는 updateProfileSettings와 같다),
 * 모르면 이 브라우저에만 저장한다. 저장되면 true, 거절되면 false
 */
export function updateCardLine(patch) {
  const before = local;
  setLocal({ ...local, ...patch });
  if (!send || !serverCardLine) return Promise.resolve(true);
  inFlight += 1;
  const run = queue.then(async () => {
    try {
      const saved = await send(patch);
      if (inFlight === 1 && saved?.cardLine !== undefined) setLocal(pickLine(saved));
      return true;
    } catch {
      const keep = Object.keys(patch).filter((key) => local[key] !== patch[key]);
      setLocal({ ...before, ...Object.fromEntries(keep.map((key) => [key, local[key]])) }); // 그 사이 새로 바꾼 칸은 건드리지 않는다
      return false;
    } finally {
      inFlight -= 1;
    }
  });
  queue = run;
  return run;
}

/** 보낸 칸만 바꾼다. 서버에 저장되면 true, 거절되면 그 칸을 이전 값으로 돌리고 false */
export function updateProfileSettings(patch) {
  const keys = Object.keys(patch);
  const before = Object.fromEntries(keys.map((key) => [key, values[key]]));
  values = { ...values, ...patch };
  if (!send) return Promise.resolve(true);
  inFlight += 1;
  const run = queue.then(async () => {
    try {
      const saved = await send(patch);
      if (inFlight === 1 && saved) values = normalize(saved); // 뒤에 기다리는 저장이 없을 때만 서버가 정리한 값을 따른다
      return true;
    } catch {
      const revert = Object.fromEntries(keys.filter((key) => values[key] === patch[key]).map((key) => [key, before[key]]));
      values = { ...values, ...revert }; // 그 사이 새로 바꾼 칸은 건드리지 않는다
      return false;
    } finally {
      inFlight -= 1;
    }
  });
  queue = run;
  return run;
}

/** 테스트 전용: 사본과 대기열을 처음 상태로 */
export function resetProfileSettings() {
  values = { ...DEFAULTS };
  local = { ...LOCAL_DEFAULTS };
  try { localStore()?.removeItem(LOCAL_KEY); } catch { /* 무시 */ }
  send = null;
  inFlight = 0;
  serverCardLine = false;
  queue = Promise.resolve();
}
