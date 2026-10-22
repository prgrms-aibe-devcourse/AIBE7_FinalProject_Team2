// 프로필 꾸미기 · 도전과제 목 서버의 표. 실제로는 서버(ProfileDecoCatalog, BE-96)가 원본이고, 화면은 id → 제목 · 문구만 갖는다 (utils/profileDeco.js).
// 아직 임시안이라 서버 표를 바꾸면 이 파일도 같이 고친다. 값은 BE-96 머지본(PR #230)의 표와 같다.
export const REWARD_TYPES = ['kinds', 'frames', 'backgrounds', 'sashStyles', 'sashColors', 'sashIcons'];

const rewards = (given) => Object.fromEntries(REWARD_TYPES.map((type) => [type, given[type] ?? []]));

// 처음부터 열린 것
export const FREE = rewards({
  kinds: ['characters/roles', 'characters/standing'], frames: ['basic'], backgrounds: ['cream'],
  sashStyles: ['none', 'short'], sashColors: ['short:wine'], sashIcons: ['none', 'star'],
});

// stat: completed(완료한 회차가 하나라도 있는 사건 수) · hasType(성향 테스트 결과가 있으면 1) · attendanceDays(누적 출석 일수) · collected(모은 일러스트, 서버가 셀 수 없다)
export const ACHIEVEMENTS = [
  { id: 'first-case', stat: 'completed', goal: 1, rewards: rewards({ kinds: ['characters/scene'], frames: ['wood'], sashStyles: ['long'], sashColors: ['long:wine'] }) },
  { id: 'personality', stat: 'hasType', goal: 1, rewards: rewards({ kinds: ['characters/personality'], backgrounds: ['tarot'], sashIcons: ['scale'] }) },
  { id: 'attend-3', stat: 'attendanceDays', goal: 3, rewards: rewards({ kinds: ['characters/icon/generated'], backgrounds: ['latte'], sashIcons: ['cup'] }) },
  { id: 'collect-5', stat: 'collected', goal: 5, rewards: rewards({ kinds: ['cases'], backgrounds: ['news'], sashIcons: ['naero', 'nambul'] }) },
  { id: 'attend-7', stat: 'attendanceDays', goal: 7, rewards: rewards({ kinds: ['characters/cheek', 'characters/cheek-slim', 'characters/deform'], frames: ['gold'], sashIcons: ['heart'] }) },
  { id: 'cases-3', stat: 'completed', goal: 3, rewards: rewards({ frames: ['stitch'], backgrounds: ['linen'], sashColors: ['short:navy', 'long:navy'], sashIcons: ['gavel'] }) },
  { id: 'collect-20', stat: 'collected', goal: 20, rewards: rewards({ kinds: ['characters/law', 'characters/adult'], sashColors: ['short:green', 'long:green'] }) },
  { id: 'attend-30', stat: 'attendanceDays', goal: 30, rewards: rewards({ kinds: ['characters/mascot'], frames: ['navy'], sashColors: ['short:gold', 'long:gold'], sashIcons: ['law'] }) },
];

// 저장 기록이 없을 때의 꾸미기 (서버 기본값)
export const DEFAULT_SERVER_DECO = {
  portrait: '/images/characters/standing/naero.webp', frame: 'basic', background: 'cream', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine',
  sashIconLeft: 'none', sashIconRight: 'none', bookmarkCharacter: 'naero', bookmarkFace: 'happy', bookmarkFaceStyle: 'generated', bookmarkColor: 'bronze',
};
