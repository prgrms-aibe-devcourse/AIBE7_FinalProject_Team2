// 연출 켜기 · 끄기 (FE-50). 사이트 전체의 화면 전환 · 연출이 이 한 곳을 본다.
// 연출이 꺼지는 경우: 공통 헤더의 "연출" 스위치를 끈 경우(이 브라우저에 저장) 또는 기기의 "동작 줄이기"(prefers-reduced-motion)가 켜진 경우.
// JS 연출(element.animate · 지연 · 유령 층)은 reducedMotion()으로 건너뛰고,
// CSS 애니메이션 · 전환은 <html data-motion="off">에서 style.css가 길이를 0으로 만든다(끝 상태는 그대로, animationend도 온다).
// data-motion도 기기 설정을 함께 본다(reduced-motion 미대응 CSS도 멈추도록)
const STORAGE_KEY = 'nlnb-motion';
export const MOTION_EVENT = 'nlnb:motion'; // 스위치 값이 바뀌었다는 알림(document)
const OS_QUERY = '(prefers-reduced-motion: reduce)';
let unsaved = null; // 저장소에 쓰지 못했을 때 이번 방문 동안의 스위치 값

// localStorage는 접근만 해도 예외가 날 수 있다(쿠키 차단 등). 못 쓰면 null
function safeStorage() {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

// 기기 설정으로 꺼져 있는지
export function systemReducesMotion() {
  return Boolean(globalThis.matchMedia?.(OS_QUERY).matches);
}

// 스위치 설정(기기 설정과 별개). 저장소를 못 쓰면 켜짐으로 본다
export function motionSwitchOn(storage = safeStorage()) {
  if (unsaved !== null) return unsaved;
  try {
    return storage?.getItem(STORAGE_KEY) !== 'off';
  } catch {
    return true;
  }
}

// 지금 연출을 건너뛰어야 하는지
export function reducedMotion(storage = safeStorage()) {
  return !motionSwitchOn(storage) || systemReducesMotion();
}

// <html data-motion>을 지금 설정에 맞춘다. 앱 시작과 스위치를 바꿀 때 부른다. 기기 설정이 바뀌면 따라간다
let watching = false;
export function applyMotion(storage = safeStorage()) {
  const root = globalThis.document?.documentElement;
  if (root) root.dataset.motion = reducedMotion(storage) ? 'off' : 'on';
  if (!watching) {
    watching = true;
    globalThis.matchMedia?.(OS_QUERY).addEventListener?.('change', () => applyMotion());
  }
}

export function setMotionSwitch(on, storage = safeStorage()) {
  try {
    if (!storage) throw new Error('no storage');
    storage.setItem(STORAGE_KEY, on ? 'on' : 'off');
    unsaved = null;
  } catch {
    unsaved = Boolean(on); // 저장소를 못 쓰면(사생활 보호 모드 등) 이번 방문에서만 바뀐다
  }
  applyMotion(storage);
  globalThis.document?.dispatchEvent?.(new Event(MOTION_EVENT)); // 헤더 스위치 · 설정 화면이 같이 따라가도록
}
