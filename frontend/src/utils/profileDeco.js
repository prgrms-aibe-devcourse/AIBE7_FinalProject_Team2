// 대표 이미지 페이지(FE-68) — 도전과제 · 해금 · 꾸미기 저장. 화면 코드에서 떼어 npm test로 확인한다.
// 서버 API 전이라 진행도는 마이페이지 응답(체험 기록 · 성향 · 출석)에서 그때그때 계산하고 저장하지 않는다. 고른 꾸미기만 이 브라우저(localStorage)에 둔다.
// 해금은 이미지 한 장씩이 아니라 종류(폴더) 단위로 열린다. 종류 이름은 컬렉션과 같은 표(myPageView.js KIND_TITLES)를 쓴다.
import { PORTRAIT_POOL } from '../content/portraitPool.js';
import { FACE_NAMES } from './bookmarkCharacter.js';

// 카드 테두리 · 카드 배경. 모양은 myPage.css의 .deco-frame-{id} · .deco-bg-{id}
export const FRAMES = [
  { id: 'basic', name: '기본' },
  { id: 'wood', name: '원목' },
  { id: 'gold', name: '금박' },
  { id: 'navy', name: '법정 네이비' },
  { id: 'stitch', name: '바느질' },
];
export const BACKGROUNDS = [
  { id: 'cream', name: '크림' },
  { id: 'latte', name: '라떼 아트' },
  { id: 'tarot', name: '타로 밤하늘' },
  { id: 'news', name: '신문지' },
  { id: 'linen', name: '체크 리넨' },
];
// 카드 책갈피: 모양을 먼저 고르고, 색은 모양마다 따로 있는 표에서 고른다(모양마다 디자인이 다를 수 있어서). 모양은 myPage.css의 .sash-style-{모양} · .deco-sash-{색}
// short: 카드 윗변에 꽂힌 짧고 굵은 책갈피(아이콘을 달 수 있다) · long: 판사 법복의 띠처럼 카드 좌우를 따라 내려오는 얇고 긴 띠(아이콘 없음)
export const SASH_STYLES = [
  { id: 'none', name: '없음' },
  { id: 'short', name: '짧고 굵은 책갈피' },
  { id: 'long', name: '얇고 긴 띠' },
];
export const SASH_COLORS = {
  short: [
    { id: 'wine', name: '와인' },
    { id: 'navy', name: '네이비' },
    { id: 'gold', name: '금사' },
    { id: 'green', name: '법정 초록' },
  ],
  long: [
    { id: 'wine', name: '와인' },
    { id: 'navy', name: '네이비' },
    { id: 'gold', name: '금사' },
    { id: 'green', name: '법정 초록' },
  ],
};
// 해금 · 저장에 쓰는 색 키: 모양마다 따로 센다("short:wine")
export const sashColorKey = (style, color) => `${style}:${color}`;
const COLOR_FIELD = { short: 'sashColorShort', long: 'sashColorLong' };
/** 지금 모양의 색(모양이 없음이면 null) */
export const sashColorOf = (deco) => (COLOR_FIELD[deco.sashStyle] ? deco[COLOR_FIELD[deco.sashStyle]] : null);
export const sashColorField = (style) => COLOR_FIELD[style];

// 짧은 책갈피의 좌 · 우 아이콘. 그림은 myPage.js(SASH_ICON_ART)가 그린다
export const SASH_ICONS = [
  { id: 'none', name: '없음' },
  { id: 'star', name: '별' },
  { id: 'scale', name: '저울' },
  { id: 'gavel', name: '법봉' },
  { id: 'cup', name: '커피잔' },
  { id: 'heart', name: '하트' },
  { id: 'naero', name: '내로 얼굴' },
  { id: 'nambul', name: '남불 얼굴' },
  { id: 'law', name: '로(저울)' },
];

// 보상 종류: 대표 이미지 종류 · 테두리 · 배경 · 책갈피 모양 · 책갈피 색(모양:색) · 책갈피 아이콘
export const REWARD_TYPES = ['kinds', 'frames', 'backgrounds', 'sashStyles', 'sashColors', 'sashIcons'];

// 처음부터 열려 있는 것
const FREE = {
  kinds: ['characters/roles', 'characters/standing'], frames: ['basic'], backgrounds: ['cream'],
  sashStyles: ['none', 'short'], sashColors: ['short:wine'], sashIcons: ['none', 'star'],
};

// 도전과제. stat은 statsOf(마이페이지)가 세는 값: completed(완료한 사건) · collected(모은 일러스트) · attendanceDays(출석 일수) · hasType(성향 테스트, 참이면 1)
// 보상은 받는 것만 적고, 적지 않은 종류는 빈 배열로 채운다
export const ACHIEVEMENTS = [
  { id: 'first-case', title: '첫 판결', text: '사건 체험을 하나 끝까지 마쳐요', stat: 'completed', goal: 1, kinds: ['characters/scene'], frames: ['wood'], sashStyles: ['long'], sashColors: ['long:wine'] },
  { id: 'personality', title: '내 성향 카드', text: '성향 테스트로 내 판결 성향을 알아봐요', stat: 'hasType', goal: 1, kinds: ['characters/personality'], backgrounds: ['tarot'], sashIcons: ['scale'] },
  { id: 'attend-3', title: '단골 손님', text: '3일 출석해요', stat: 'attendanceDays', goal: 3, kinds: ['characters/icon/generated'], backgrounds: ['latte'], sashIcons: ['cup'] },
  { id: 'collect-5', title: '수집가', text: '일러스트를 5장 모아요', stat: 'collected', goal: 5, kinds: ['cases'], backgrounds: ['news'], sashIcons: ['naero', 'nambul'] },
  { id: 'attend-7', title: '일주일 출석', text: '7일 출석해요', stat: 'attendanceDays', goal: 7, kinds: ['characters/cheek', 'characters/cheek-slim', 'characters/deform'], frames: ['gold'], sashIcons: ['heart'] },
  { id: 'cases-3', title: '세 번째 판결', text: '사건 체험을 세 건 마쳐요', stat: 'completed', goal: 3, frames: ['stitch'], backgrounds: ['linen'], sashColors: ['short:navy', 'long:navy'], sashIcons: ['gavel'] },
  { id: 'collect-20', title: '열혈 수집가', text: '일러스트를 20장 모아요', stat: 'collected', goal: 20, kinds: ['characters/law', 'characters/adult'], sashColors: ['short:green', 'long:green'] },
  { id: 'attend-30', title: '한 달 출석', text: '30일 출석해요', stat: 'attendanceDays', goal: 30, kinds: ['characters/mascot'], frames: ['navy'], sashColors: ['short:gold', 'long:gold'], sashIcons: ['law'] },
].map((item) => ({ ...Object.fromEntries(REWARD_TYPES.map((type) => [type, []])), ...item }));

// "/images/characters/scene/a.webp" → "characters/scene"
export const kindOfPortrait = (src) => src.slice('/images/'.length, src.lastIndexOf('/'));

/** 도전과제마다 진행도. value는 목표에서 멈춘다 */
export function achievementProgress(stats) {
  return ACHIEVEMENTS.map((item) => {
    const raw = Number(stats?.[item.stat] ?? 0) || 0;
    return { ...item, value: Math.min(raw, item.goal), cleared: raw >= item.goal };
  });
}

/** 지금 열린 것. 보상 종류(REWARD_TYPES)마다 Set */
export function unlockedBy(stats) {
  const open = Object.fromEntries(REWARD_TYPES.map((type) => [type, new Set(FREE[type])]));
  achievementProgress(stats).filter((item) => item.cleared).forEach((item) => {
    REWARD_TYPES.forEach((type) => item[type].forEach((id) => open[type].add(id)));
  });
  return open;
}

/** 잠긴 항목을 여는 도전과제(없으면 null). type: REWARD_TYPES 중 하나 */
export const unlockerOf = (type, id) => ACHIEVEMENTS.find((item) => item[type].includes(id)) ?? null;

// ── 고른 꾸미기 저장 ──
const DECO_KEY = 'nlnb-profile-deco';
export const DEFAULT_DECO = { portrait: null, frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' }; // portrait null = 책갈피 캐릭터의 기본 대표 이미지

// 일부 브라우저(쿠키 · 사이트 데이터 차단)는 globalThis.localStorage를 읽기만 해도 SecurityError를 던진다.
// 기본 인수는 함수 본문의 try보다 먼저 평가되므로 접근 자체를 감싼다(bookmarkCharacter.js localStore와 같은 방식). 공용 헬퍼가 생기면 교체한다
const localStore = () => {
  try { return globalThis.localStorage; } catch { return undefined; }
};

const pickId = (list, value, fallback) => (list.some(({ id }) => id === value) ? value : fallback);
const valid = (deco) => ({
  portrait: PORTRAIT_POOL.includes(deco?.portrait) ? deco.portrait : null,
  frame: pickId(FRAMES, deco?.frame, DEFAULT_DECO.frame),
  background: pickId(BACKGROUNDS, deco?.background, DEFAULT_DECO.background),
  sashStyle: pickId(SASH_STYLES, deco?.sashStyle, DEFAULT_DECO.sashStyle),
  sashColorShort: pickId(SASH_COLORS.short, deco?.sashColorShort, DEFAULT_DECO.sashColorShort),
  sashColorLong: pickId(SASH_COLORS.long, deco?.sashColorLong, DEFAULT_DECO.sashColorLong),
  sashIconLeft: pickId(SASH_ICONS, deco?.sashIconLeft, DEFAULT_DECO.sashIconLeft),
  sashIconRight: pickId(SASH_ICONS, deco?.sashIconRight, DEFAULT_DECO.sashIconRight),
});

export function getProfileDeco(storage = localStore()) {
  try {
    return valid(JSON.parse(storage?.getItem(DECO_KEY) ?? 'null'));
  } catch {
    return { ...DEFAULT_DECO };
  }
}

/** 고른 것 일부만 바꿔 저장한다(모르는 값은 기본으로) */
export function setProfileDeco(patch, storage = localStore()) {
  const next = valid({ ...getProfileDeco(storage), ...patch });
  try {
    storage?.setItem(DECO_KEY, JSON.stringify(next));
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만 바뀐다
  }
  return next;
}

/** 화면에 쓸 꾸미기. 고른 것이 잠겨 있으면 기본으로 보여 준다(저장값은 두어서 다시 열리면 돌아온다). 아이콘은 짧은 책갈피에만 단다.
 *  고른 색이 그 모양에서 잠겨 있으면 그 모양에서 열린 첫 색으로, 열린 색이 없으면 책갈피를 없음으로 본다 */
export function shownDeco(deco, open) {
  let sashStyle = open.sashStyles.has(deco.sashStyle) ? deco.sashStyle : DEFAULT_DECO.sashStyle;
  const colorFor = (style) => {
    const field = COLOR_FIELD[style];
    if (open.sashColors.has(sashColorKey(style, deco[field]))) return deco[field];
    return SASH_COLORS[style].find(({ id }) => open.sashColors.has(sashColorKey(style, id)))?.id ?? null;
  };
  const colors = { sashColorShort: colorFor('short') ?? deco.sashColorShort, sashColorLong: colorFor('long') ?? deco.sashColorLong };
  if (COLOR_FIELD[sashStyle] && !colorFor(sashStyle)) sashStyle = 'none';
  const icon = (id) => (sashStyle === 'short' && open.sashIcons.has(id) ? id : 'none');
  return {
    portrait: deco.portrait && open.kinds.has(kindOfPortrait(deco.portrait)) ? deco.portrait : null,
    frame: open.frames.has(deco.frame) ? deco.frame : DEFAULT_DECO.frame,
    background: open.backgrounds.has(deco.background) ? deco.background : DEFAULT_DECO.background,
    sashStyle,
    ...colors,
    sashIconLeft: icon(deco.sashIconLeft),
    sashIconRight: icon(deco.sashIconRight),
  };
}

// ── 대표 이미지 이름 (카드 아래 한 줄 · 고르기 칸의 읽는 이름) ──
const WHO = { naero: '내로', nambul: '남불', 'law-scale': '로(저울)', 'law-gavel': '로(법봉)', 'law-book': '로(책)', trio: '로 셋', compare: '셋 비교' };
const WORDS = {
  judge: '판사', prosecutor: '검사', defense: '변호인', actor: 'A씨 역', 'case-1': '사건 1 배역',
  idle: '대기', think: '생각', deliver: '발표', mascot: '마스코트', ranma: '란마',
  cheer: '응원', guide: '안내', react: '반응', read: '읽기', trio: '셋',
};
const CASE_PARTS = { origin: '발단', conclusion: '결론', reenact: '재연' };
const COMMON_PARTS = { headline: '헤드라인', 'sub-1': '서브', 'detail-1': '디테일' };

export function portraitLabel(src) {
  const kind = kindOfPortrait(src);
  const file = src.slice(src.lastIndexOf('/') + 1).replace(/\.webp$/, '');
  if (kind === 'characters/personality') return `성향 ${file}`;
  if (kind === 'cases') {
    const own = /^case-(\d+)-([a-z]+)$/.exec(file);
    if (own) return `사건 ${own[1]} · ${CASE_PARTS[own[2]] ?? own[2]}`;
    const common = /^common-(\d+)$/.exec(file);
    if (common) return `공용 컷 ${common[1]}`;
    const reenact = /^reenact-common(-\d+)?_(.+)$/.exec(file);
    if (reenact) return `공용 재연${reenact[1] ? ` ${reenact[1].slice(1)}` : ''} · ${COMMON_PARTS[reenact[2]] ?? reenact[2]}`;
    return file;
  }
  const cut = file.indexOf('_');
  const who = cut < 0 ? file : file.slice(0, cut);
  const rest = cut < 0 ? '' : file.slice(cut + 1);
  const name = WHO[who] ?? who;
  if (!rest) return name;
  const face = /^face-([a-z]+)$/.exec(rest);
  const pose = /^([a-z]+)-([a-z]+)$/.exec(rest); // 장면-감정 ("react-happy" → 반응 기쁨, "cheer-cheer" → 응원)
  const feeling = pose && FACE_NAMES[pose[2]];
  const detail = face ? `얼굴 · ${FACE_NAMES[face[1]] ?? face[1]}`
    : feeling ? (WORDS[pose[1]] === feeling ? feeling : `${WORDS[pose[1]] ?? pose[1]} ${feeling}`)
      : WORDS[rest] ?? rest;
  return `${name} · ${detail}`;
}
