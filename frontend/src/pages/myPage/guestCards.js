// 로그인 전 마이페이지 왼쪽 자리에 돌려 보여 주는 샘플 프로필카드 10종(FE-138). 서버 값이 아니라 보여 주기용이다.
// 대표 이미지 · 테두리 · 배경 · 책갈피를 서로 다르게 짜서 "로그인하면 이런 카드를 꾸밀 수 있다"를 보인다. 닉네임 · 레벨 · 직업은 지어낸 값이다
import { DEFAULT_DECO } from '../../utils/profileDeco.js';

export const GUEST_CARD_INTERVAL_MS = 600;
export const GUEST_TYPE_INTERVAL_MS = 1800; // 오른쪽 성향카드 창의 뿌연 그림이 바뀌는 간격(바뀌는 데 걸리는 시간은 CSS .my-guest-haze의 transition)

const deco = (portrait, frame, background, sash = {}) => ({ ...DEFAULT_DECO, portrait, frame, background, ...sash });
const short = (color, left = 'none', right = 'none') => ({ sashStyle: 'short', sashColorShort: color, sashIconLeft: left, sashIconRight: right });
const long = (color) => ({ sashStyle: 'long', sashColorLong: color });

export const GUEST_CARDS = [
  { nickname: '내로팬', level: 2, line: '로스쿨생', deco: deco('/images/characters/standing/naero.webp', 'basic', 'cream') },
  { nickname: '남불러', level: 5, line: '연수원생', deco: deco('/images/characters/standing/nambul.webp', 'wood', 'latte', long('wine')) },
  { nickname: '판결러', level: 12, line: '수습 판사', deco: deco('/images/characters/roles/naero_judge.webp', 'gold', 'tarot', short('gold', 'star', 'scale')) },
  { nickname: '검사님', level: 8, line: '연수원생', deco: deco('/images/characters/roles/nambul_prosecutor.webp', 'navy', 'news', long('navy')) },
  { nickname: '깜짝판사', level: 4, line: '로스쿨생', deco: deco('/images/characters/scene/naero_react-surprised.webp', 'stitch', 'linen', short('green', 'gavel', 'heart')) },
  { nickname: '저울지기', level: 20, line: '판사', deco: deco('/images/characters/standing/law-scale.webp', 'gold', 'cream', long('gold')) },
  { nickname: '커피한잔', level: 6, line: '수습 판사', deco: deco('/images/characters/scene/nambul_guide-happy.webp', 'wood', 'linen', short('wine', 'cup', 'cup')) },
  { nickname: '사건수집', level: 9, line: '연수원생', deco: deco('/images/cases/case-1-reenact.webp', 'basic', 'news') },
  { nickname: '삼총사', level: 15, line: '판사', deco: deco('/images/characters/mascot/trio_idle.webp', 'navy', 'tarot', short('navy', 'naero', 'nambul')) },
  { nickname: '방청객', level: 1, line: '무직', deco: deco('/images/characters/personality/07.webp', 'stitch', 'latte', long('green')) },
];

/** 다음에 보일 카드 번호: 무작위이되 바로 앞과 같은 카드는 건너뛴다. random은 [0, 1) 값을 주는 함수(시험용으로 바꿀 수 있다) */
export function nextCardIndex(previous, count = GUEST_CARDS.length, random = Math.random) {
  if (count < 2) return 0;
  const pick = Math.floor(random() * (count - 1));
  return pick >= previous ? pick + 1 : pick;
}

// 오른쪽 성향카드 자리 창에 뿌옇게 비치는 성향 카드 그림: 성향 결과 그림 32장 전부, 내로(01 ~ 16) · 남불(17 ~ 32)를 번갈아 순서대로(01, 17, 02, 18, …)
export const GUEST_TYPE_IMAGES = Array.from({ length: 32 }, (_, i) => {
  const number = (i % 2 === 0 ? 1 : 17) + Math.floor(i / 2);
  return `/images/characters/personality/${String(number).padStart(2, '0')}.webp`;
});

/** 사건 총 개수: 공개 사건 목록(API 1)의 summary.total. 받지 못했거나 숫자가 아니면 null(안내를 보이지 않는다) */
export const caseTotalOf = (response) => (Number.isInteger(response?.summary?.total) && response.summary.total >= 0 ? response.summary.total : null);

/** 일러스트 총 개수: 일러스트 API(I-1)의 total. 받지 못했거나 숫자가 아니면 null */
export const illustrationTotalOf = (response) => (Number.isInteger(response?.total) && response.total >= 0 ? response.total : null);
