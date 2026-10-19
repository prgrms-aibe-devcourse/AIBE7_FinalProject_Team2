// 책갈피 캐릭터 (FE-44). 마이페이지 "만난 모습"에서 내로 · 남불 중 하나를 고르고, 마이페이지 프로필 · 판결 체험의 진행 책갈피(FE-48)가 그 얼굴을 쓴다.
// 서버 저장은 없다(BE-83에서 수집 상태와 함께 검토). 이 브라우저에만 두고, 저장소를 못 쓰면 기본값(내로)으로 본다.
const STORAGE_KEY = 'nlnb-bookmark-character';
const DEFAULT = 'naero';
export const BOOKMARK_CHARACTERS = ['naero', 'nambul'];
export const BOOKMARK_NAMES = { naero: '내로', nambul: '남불' };

// shortcut: 원본 이미지 레포 PNG를 직접 읽는다(FE-48 책갈피와 같음), 웹 webp 변환(WBS 7.6.8) 때 public 경로로 옮긴다
const FACES = 'https://raw.githubusercontent.com/peanutboi710/NaeLawNamBul_Images/main/character/cuts/';
export const bookmarkFace = (who) => `${FACES}${who}/icon/${who}_icon_face-happy.png`;

export function getBookmarkCharacter(storage = globalThis.localStorage) {
  try {
    const saved = storage?.getItem(STORAGE_KEY);
    return BOOKMARK_CHARACTERS.includes(saved) ? saved : DEFAULT;
  } catch {
    return DEFAULT;
  }
}

export function setBookmarkCharacter(who, storage = globalThis.localStorage) {
  if (!BOOKMARK_CHARACTERS.includes(who)) return;
  try {
    storage?.setItem(STORAGE_KEY, who);
  } catch {
    // 저장소를 못 쓰면(사생활 보호 모드 등) 이번 화면에서만 바뀐다
  }
}
