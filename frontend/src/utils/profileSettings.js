// 프로필 선택 설정(FE-81): 공개 성향카드(shownCard) · 프로필카드 아래 성향(profileCardType) · 한 줄 소개(bio).
// 서버(P-8 PATCH /profile-settings, 조회는 P-5 profileSettings)가 원본이고, 이 모듈은 화면이 동기로 읽는 사본이다.
// 바꾸면 사본을 먼저 바꾸고(바로 화면에 반영) 서버에 보낸다. 보내는 요청은 차례로 하나씩 가고, 거절되면 그 칸을 이전 값으로 돌린다.
// 마이페이지를 받으면 서버 값으로 맞추되, 저장 중인 값이 있으면 오래된 응답이 덮어쓰지 않게 건너뛴다
export const SLOTS = ['initial', 'retake', 'current'];
export const BIO_MAX = 40;

const DEFAULTS = Object.freeze({ shownCard: 'current', profileCardType: 'current', bio: '' });

let values = { ...DEFAULTS };
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

/** 서버가 준 값(P-5 profileSettings)으로 맞춘다. 없으면(옛 서버) 그대로 두고, 저장 중이면 건너뛴다 */
export function hydrateProfileSettings(settings) {
  if (!settings || inFlight > 0) return;
  values = normalize(settings);
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
  send = null;
  inFlight = 0;
  queue = Promise.resolve();
}
