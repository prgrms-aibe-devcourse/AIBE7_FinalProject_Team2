// 로그인한 사용자 (FE-52). 공통 헤더가 화면을 그릴 때 바로 읽어야 해서(api를 기다리지 않음) 이 브라우저에 닉네임만 둔다.
// 로그인 · 로그아웃은 api(login · logout)가 하고, 성공하면 화면이 여기에 적고 지운다. 지금은 개발용 목업 로그인만 있다(mocks/authMockApi.js).
// 실제 로그인(카카오 · 서버 세션)이 생기면 서버가 진짜 상태를 가지고, 이곳은 헤더용 표시값으로만 남긴다
const STORAGE_KEY = 'nlnb-session';
let unsaved; // 저장소를 못 쓸 때 이번 방문 동안의 값(undefined = 저장소를 본다)

// 로그인했으면 { nickname } (관리자면 role: 'admin'도), 아니면 null.
// role은 헤더 · 책상이 마이페이지 자리에 관리자 페이지를 보여 주려는 표시값일 뿐이다. 권한은 서버 세션(관리자 API)이 판단한다 (FE-53)
export function currentUser(storage = globalThis.localStorage) {
  if (unsaved !== undefined) return unsaved;
  try {
    const user = JSON.parse(storage?.getItem(STORAGE_KEY) ?? 'null');
    if (typeof user?.nickname !== 'string' || !user.nickname.trim()) return null;
    return user.role === 'admin' ? { nickname: user.nickname.trim(), role: 'admin' } : { nickname: user.nickname.trim() };
  } catch {
    return null;
  }
}

export function isAdmin(user = currentUser()) {
  return user?.role === 'admin';
}

export function saveUser({ nickname, role }, storage = globalThis.localStorage) {
  const user = { nickname: String(nickname).trim() };
  if (role === 'admin') user.role = 'admin';
  try {
    storage?.setItem(STORAGE_KEY, JSON.stringify(user));
    unsaved = undefined;
  } catch {
    unsaved = user; // 사생활 보호 모드 등: 이번 방문에서만 로그인 상태가 유지된다
  }
}

export function clearUser(storage = globalThis.localStorage) {
  try {
    storage?.removeItem(STORAGE_KEY);
    unsaved = undefined;
  } catch {
    unsaved = null;
  }
}
