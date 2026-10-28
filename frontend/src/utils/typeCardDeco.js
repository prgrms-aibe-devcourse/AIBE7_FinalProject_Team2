// 성향카드 꾸미기(FE-82): 성향카드 화면에서 고르는 테두리 · 배경 · 책갈피. 프로필카드 꾸미기(profileDeco.js)와 같은 값 목록 · 같은 해금(도전과제)을 쓰고,
// 저장만 따로다. 서버 API 전이라 이 브라우저에만 저장한다(profileCardType.js와 같다. 서버로 옮기는 것은 후속 작업)
import { BACKGROUNDS, DEFAULT_DECO, DEFAULT_PORTRAITS, FRAMES, SASH_COLORS, SASH_ICONS, SASH_STYLES, pickId, shownDeco } from './profileDeco.js';
import { localStore } from './safeStorage.js';

const STORAGE_KEY = 'nlnb-type-card-deco';
let unsaved; // 저장소를 못 쓸 때 이번 방문 동안의 값

// 성향카드 배경: 프로필카드 배경에 더해 "기본"(원래 타로카드 모양)이 있다. 기본은 처음부터 열려 있고 기본 값이다. 모양은 myPage.css의 .deco-bg-{id}
export const TYPE_BACKGROUNDS = [{ id: 'classic', name: '기본' }, ...BACKGROUNDS];
const { portrait: _portrait, ...PLAIN } = DEFAULT_DECO;
// 성향카드 캐릭터: 내로 · 남불 두 장이 둘 다 늘 열려 있다. null이면 성향 결과의 캐릭터 그대로 보인다(이미지 번호 규칙은 mocks/personalityMockData.js)
export const TYPE_CHARACTERS = ['NAERO', 'NAMBUL'];
export const DEFAULT_TYPE_DECO = { ...PLAIN, background: 'classic', character: null };

/** 저장된 값을 화면이 쓰는 모양으로. 모르는 값 · 빠진 값은 기본으로 본다. 잠겼는지는 보지 않는다(shownTypeDeco가 한다) */
export const normalizeTypeDeco = (deco) => ({
  frame: pickId(FRAMES, deco?.frame, DEFAULT_TYPE_DECO.frame),
  background: pickId(TYPE_BACKGROUNDS, deco?.background, DEFAULT_TYPE_DECO.background),
  sashStyle: pickId(SASH_STYLES, deco?.sashStyle, DEFAULT_TYPE_DECO.sashStyle),
  sashColorShort: pickId(SASH_COLORS.short, deco?.sashColorShort, DEFAULT_TYPE_DECO.sashColorShort),
  sashColorLong: pickId(SASH_COLORS.long, deco?.sashColorLong, DEFAULT_TYPE_DECO.sashColorLong),
  sashIconLeft: pickId(SASH_ICONS, deco?.sashIconLeft, DEFAULT_TYPE_DECO.sashIconLeft),
  sashIconRight: pickId(SASH_ICONS, deco?.sashIconRight, DEFAULT_TYPE_DECO.sashIconRight),
  character: TYPE_CHARACTERS.includes(deco?.character) ? deco.character : null,
});

/** 화면에 쓸 꾸미기. 잠긴 것은 기본으로 보여 준다(저장값은 두어서 다시 열리면 돌아온다). 규칙은 프로필카드(shownDeco)와 같고, 배경 "기본"만 늘 열려 있다 */
export function shownTypeDeco(deco, open) {
  const { portrait: _unused, background: _bg, ...shown } = shownDeco({ ...deco, portrait: DEFAULT_PORTRAITS[0] }, open);
  const background = deco.background === DEFAULT_TYPE_DECO.background || open.backgrounds.has(deco.background) ? deco.background : DEFAULT_TYPE_DECO.background;
  return { ...shown, background, character: deco.character ?? null };
}

export function getTypeDeco(storage = localStore()) {
  if (unsaved) return unsaved;
  try {
    return normalizeTypeDeco(JSON.parse(storage?.getItem(STORAGE_KEY)));
  } catch {
    return normalizeTypeDeco(null);
  }
}

/** 고른 칸만 바꿔 저장하고 저장한 값을 돌려준다 */
export function setTypeDeco(patch, storage = localStore()) {
  const next = normalizeTypeDeco({ ...getTypeDeco(storage), ...patch });
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(next));
    unsaved = undefined;
  } catch {
    unsaved = next;
  }
  return next;
}
