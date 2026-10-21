// 책갈피 캐릭터 (FE-44). 마이페이지 "만난 모습" 위쪽에서 내로 · 남불 중 하나를 고르고, 마이페이지 프로필 · 판결 체험의 진행 책갈피(FE-48)가 그 얼굴을 쓴다.
// FE-67: 얼굴 아이콘 중에서 골라 책갈피로 쓴다. 새로 생성한 얼굴(characters/icon/generated)과 장면 컷에서 잘라 만든 얼굴(icon/cropped) 각 12장이 있고,
// 캐릭터 · 표정 · 종류를 따로 저장한다(표정 기본 happy, 종류 기본 generated).
// 서버 저장은 없다(BE-83에서 수집 상태와 함께 검토). 이 브라우저에만 두고, 저장소를 못 쓰면 기본값(내로)으로 본다.
const STORAGE_KEY = 'nlnb-bookmark-character';
const FACE_KEY = 'nlnb-bookmark-face';
const STYLE_KEY = 'nlnb-bookmark-face-style';
const DEFAULT = 'naero';
const DEFAULT_FACE = 'happy';
const DEFAULT_STYLE = 'generated';
// localStorage 속성 접근 자체가 예외를 던지는 환경(저장소 차단)이 있어, 기본 인수에서도 터지지 않게 한 곳에서 감싼다
const localStore = () => {
  try { return globalThis.localStorage; } catch { return undefined; }
};

export const BOOKMARK_CHARACTERS = ['naero', 'nambul'];
// 로(마스코트)도 책갈피가 된다. 표정 없이 잘라 만든 아이콘 한 장씩이다(icon/cropped/law-*_icon.webp)
export const BOOKMARK_LAWS = ['law-scale', 'law-gavel', 'law-book'];
const ALL_WHO = [...BOOKMARK_CHARACTERS, ...BOOKMARK_LAWS];
export const BOOKMARK_NAMES = { naero: '내로', nambul: '남불', 'law-scale': '로 · 저울', 'law-gavel': '로 · 법봉', 'law-book': '로 · 책' };

export const BOOKMARK_FACES = ['cheer', 'happy', 'neutral', 'sorry', 'surprised', 'thinking'];
export const FACE_NAMES = { cheer: '응원', happy: '기쁨', neutral: '보통', sorry: '미안', surprised: '놀람', thinking: '생각' };

export const BOOKMARK_STYLES = ['generated', 'cropped'];
export const STYLE_NAMES = { generated: '생성한 얼굴', cropped: '잘라 만든 얼굴' };

export const bookmarkFace = (who, face = DEFAULT_FACE, style = DEFAULT_STYLE) => (BOOKMARK_LAWS.includes(who)
  ? `/images/characters/icon/cropped/${who}_icon.webp`
  : `/images/characters/icon/${style}/${who}_face-${face}.webp`);

export function getBookmarkCharacter(storage = localStore()) {
  try {
    const saved = storage?.getItem(STORAGE_KEY);
    return ALL_WHO.includes(saved) ? saved : DEFAULT;
  } catch {
    return DEFAULT;
  }
}

export function setBookmarkCharacter(who, storage = localStore()) {
  if (!ALL_WHO.includes(who)) return;
  try {
    storage?.setItem(STORAGE_KEY, who);
  } catch {
    // 저장소를 못 쓰면(사생활 보호 모드 등) 이번 화면에서만 바뀐다
  }
}

export function getBookmarkFaceName(storage = localStore()) {
  try {
    const saved = storage?.getItem(FACE_KEY);
    return BOOKMARK_FACES.includes(saved) ? saved : DEFAULT_FACE;
  } catch {
    return DEFAULT_FACE;
  }
}

export function getBookmarkStyle(storage = localStore()) {
  try {
    const saved = storage?.getItem(STYLE_KEY);
    return BOOKMARK_STYLES.includes(saved) ? saved : DEFAULT_STYLE;
  } catch {
    return DEFAULT_STYLE;
  }
}

// 얼굴 아이콘 하나를 책갈피로 정한다(캐릭터도 그 얼굴의 캐릭터로 바뀐다)
export function setBookmarkFace(who, face, style = DEFAULT_STYLE, storage = localStore()) {
  if (!BOOKMARK_CHARACTERS.includes(who) || !BOOKMARK_FACES.includes(face) || !BOOKMARK_STYLES.includes(style)) return;
  setBookmarkCharacter(who, storage);
  try {
    storage?.setItem(FACE_KEY, face);
    storage?.setItem(STYLE_KEY, style);
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만 바뀐다
  }
}

// 지금 책갈피로 쓰는 얼굴 그림 경로
export const selectedBookmarkFace = (storage = localStore()) => bookmarkFace(getBookmarkCharacter(storage), getBookmarkFaceName(storage), getBookmarkStyle(storage));
