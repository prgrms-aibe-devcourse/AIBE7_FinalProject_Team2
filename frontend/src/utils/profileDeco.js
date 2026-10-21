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

// 처음부터 열려 있는 것
const FREE = { kinds: ['characters/roles', 'characters/standing'], frames: ['basic'], backgrounds: ['cream'] };

// 도전과제. stat은 statsOf(마이페이지)가 세는 값: completed(완료한 사건) · collected(모은 일러스트) · attendanceDays(출석 일수) · hasType(성향 테스트, 참이면 1)
export const ACHIEVEMENTS = [
  { id: 'first-case', title: '첫 판결', text: '사건 체험을 하나 끝까지 마쳐요', stat: 'completed', goal: 1, kinds: ['characters/scene'], frames: ['wood'], backgrounds: [] },
  { id: 'personality', title: '내 성향 카드', text: '성향 테스트로 내 판결 성향을 알아봐요', stat: 'hasType', goal: 1, kinds: ['characters/personality'], frames: [], backgrounds: ['tarot'] },
  { id: 'attend-3', title: '단골 손님', text: '3일 출석해요', stat: 'attendanceDays', goal: 3, kinds: ['characters/icon/generated'], frames: [], backgrounds: ['latte'] },
  { id: 'collect-5', title: '수집가', text: '일러스트를 5장 모아요', stat: 'collected', goal: 5, kinds: ['cases'], frames: [], backgrounds: ['news'] },
  { id: 'attend-7', title: '일주일 출석', text: '7일 출석해요', stat: 'attendanceDays', goal: 7, kinds: ['characters/cheek', 'characters/cheek-slim', 'characters/deform'], frames: ['gold'], backgrounds: [] },
  { id: 'cases-3', title: '세 번째 판결', text: '사건 체험을 세 건 마쳐요', stat: 'completed', goal: 3, kinds: [], frames: ['stitch'], backgrounds: ['linen'] },
  { id: 'collect-20', title: '열혈 수집가', text: '일러스트를 20장 모아요', stat: 'collected', goal: 20, kinds: ['characters/law', 'characters/adult'], frames: [], backgrounds: [] },
  { id: 'attend-30', title: '한 달 출석', text: '30일 출석해요', stat: 'attendanceDays', goal: 30, kinds: ['characters/mascot'], frames: ['navy'], backgrounds: [] },
];

// "/images/characters/scene/a.webp" → "characters/scene"
export const kindOfPortrait = (src) => src.slice('/images/'.length, src.lastIndexOf('/'));

/** 도전과제마다 진행도. value는 목표에서 멈춘다 */
export function achievementProgress(stats) {
  return ACHIEVEMENTS.map((item) => {
    const raw = Number(stats?.[item.stat] ?? 0) || 0;
    return { ...item, value: Math.min(raw, item.goal), cleared: raw >= item.goal };
  });
}

/** 지금 열린 종류 · 테두리 · 배경 */
export function unlockedBy(stats) {
  const open = { kinds: new Set(FREE.kinds), frames: new Set(FREE.frames), backgrounds: new Set(FREE.backgrounds) };
  achievementProgress(stats).filter((item) => item.cleared).forEach((item) => {
    item.kinds.forEach((kind) => open.kinds.add(kind));
    item.frames.forEach((id) => open.frames.add(id));
    item.backgrounds.forEach((id) => open.backgrounds.add(id));
  });
  return open;
}

/** 잠긴 항목을 여는 도전과제(없으면 null). type: 'kinds' · 'frames' · 'backgrounds' */
export const unlockerOf = (type, id) => ACHIEVEMENTS.find((item) => item[type].includes(id)) ?? null;

// ── 고른 꾸미기 저장 ──
const DECO_KEY = 'nlnb-profile-deco';
export const DEFAULT_DECO = { portrait: null, frame: 'basic', background: 'cream' }; // portrait null = 책갈피 캐릭터의 기본 대표 이미지

const valid = (deco) => ({
  portrait: PORTRAIT_POOL.includes(deco?.portrait) ? deco.portrait : null,
  frame: FRAMES.some(({ id }) => id === deco?.frame) ? deco.frame : DEFAULT_DECO.frame,
  background: BACKGROUNDS.some(({ id }) => id === deco?.background) ? deco.background : DEFAULT_DECO.background,
});

export function getProfileDeco(storage = globalThis.localStorage) {
  try {
    return valid(JSON.parse(storage?.getItem(DECO_KEY) ?? 'null'));
  } catch {
    return { ...DEFAULT_DECO };
  }
}

/** 고른 것 일부만 바꿔 저장한다(모르는 값은 기본으로) */
export function setProfileDeco(patch, storage = globalThis.localStorage) {
  const next = valid({ ...getProfileDeco(storage), ...patch });
  try {
    storage?.setItem(DECO_KEY, JSON.stringify(next));
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만 바뀐다
  }
  return next;
}

/** 화면에 쓸 꾸미기. 고른 것이 잠겨 있으면 기본으로 보여 준다(저장값은 두어서 다시 열리면 돌아온다) */
export function shownDeco(deco, open) {
  return {
    portrait: deco.portrait && open.kinds.has(kindOfPortrait(deco.portrait)) ? deco.portrait : null,
    frame: open.frames.has(deco.frame) ? deco.frame : DEFAULT_DECO.frame,
    background: open.backgrounds.has(deco.background) ? deco.background : DEFAULT_DECO.background,
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
