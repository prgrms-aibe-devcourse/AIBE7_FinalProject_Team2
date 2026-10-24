import { TYPES, imageNumberOf } from './personalityMockData.js';
import { shareIdOfCode } from '../utils/personalityTypes.js';
import { DEFAULT_DECO } from '../utils/profileDeco.js';

// 커뮤니티(S-18, FE-47) 목 데이터. 실제 사용자 글이 아니다 — 화면은 "목데이터"로 표기한다.
// 판결 공유 글에는 글쓴이 자신의 판결만 담는다(AI 판결 · 실제 판결은 넣지 않는다, 앵커링 방지).
// 사건 1(가상 살인)만 체험할 수 있어 판결 공유 글은 사건 1에만 둔다. 메인 첫 화면처럼 특정 판결 방향을 정답처럼 말하는 글은 쓰지 않는다
export const seedPosts = [
  {
    postId: 108, category: 'VERDICT', caseId: 1,
    author: { nickname: '새벽의 배심원', character: 'naero' },
    title: '처음엔 무겁게 봤는데, 다 읽고 나니 생각이 바뀌었어요',
    body: '첫인상 판단과 최종 판결이 꽤 달랐어요. 기사 제목만 봤을 때는 무조건 무겁게 가야 한다고 생각했는데, 양측 주장을 차례로 읽고 나니 고려할 게 생각보다 많더라고요.\n\n어떤 요소에서 생각이 바뀌었는지 적어 봅니다. 다들 첫인상이랑 최종 판결이 얼마나 달랐는지 궁금해요.',
    images: [],
    verdict: { penaltyType: 'PRISON', reducedTo: null, prisonMonths: 144, fineAmount: null, suspensionMonths: null, upCount: 3, downCount: 1 },
    likeCount: 24, createdAt: '2026-10-19T21:14:00+09:00',
  },
  {
    postId: 107, category: 'QUESTION', caseId: 1,
    author: { nickname: '밤샘 방청객', character: 'nambul' },
    title: "양형에서 '진지한 반성'은 어떻게 판단하나요?",
    body: '법정 순서 재판에서 변호인이 반성을 강조하던데, 실제 재판에서는 반성을 무엇으로 판단하는지 궁금해요. 반성문만으로 되는 건지, 피해 회복 노력까지 봐야 하는 건지요.',
    images: [], verdict: null,
    likeCount: 11, createdAt: '2026-10-18T13:02:00+09:00',
  },
  {
    postId: 106, category: 'FREE', caseId: null,
    author: { nickname: '말없는 판사', character: 'naero' },
    title: '신문 지면 넘기는 연출이 좋네요',
    body: '사건 목록이 신문으로 펼쳐지는 게 기억에 남아요. 기사 하나를 골라 오려 내는 느낌이라 사건에 더 집중하게 돼요.',
    images: ['/images/cases/common-2.webp'], verdict: null,
    likeCount: 7, createdAt: '2026-10-17T19:40:00+09:00',
  },
  {
    postId: 105, category: 'VERDICT', caseId: 1,
    author: { nickname: '오후의 서기', character: 'nambul' },
    title: '감경 요소를 너무 많이 고른 걸까요',
    body: '판결 전 정리에서 가볍게 볼 요소를 여럿 골랐는데, 다 고르고 보니 제가 너무 피고인 쪽에서만 본 건 아닌지 싶어요. 다른 분들은 몇 개쯤 고르셨나요?',
    images: [],
    verdict: { penaltyType: 'PRISON', reducedTo: null, prisonMonths: 108, fineAmount: null, suspensionMonths: null, upCount: 1, downCount: 3 },
    likeCount: 19, createdAt: '2026-10-17T09:21:00+09:00',
  },
  {
    postId: 104, category: 'FREE', caseId: 1,
    author: { nickname: '창가의 방청석', character: 'naero' },
    title: '변호인 최후 변론이 의외로 설득력 있었어요',
    body: '검사 쪽 이야기만 들었을 때랑 변호인 이야기까지 들었을 때 느낌이 달랐어요. 같은 분량으로 양쪽을 들으니 한쪽으로 쏠리지 않게 되네요.',
    images: [], verdict: null,
    likeCount: 9, createdAt: '2026-10-16T22:48:00+09:00',
  },
  {
    postId: 103, category: 'QUESTION', caseId: null,
    author: { nickname: '첫 재판', character: 'nambul' },
    title: '권고 범위 밖으로 형을 정하면 어떻게 되나요?',
    body: '판결 입력에서 권고 범위 밖은 안내가 뜨던데, 실제 재판에서도 권고 범위를 벗어나는 경우가 있는지 궁금합니다.',
    images: [], verdict: null,
    likeCount: 5, createdAt: '2026-10-16T11:05:00+09:00',
  },
  {
    postId: 102, category: 'FREE', caseId: null,
    author: { nickname: '조용한 저울', character: 'naero' },
    title: '성향 검사 결과랑 판결할 때 제 모습이 달라서 신기했어요',
    body: '일상 질문에서는 관용적인 편으로 나왔는데, 막상 사건 기록을 읽고 판결할 때는 꽤 엄격하게 보더라고요. 다른 분들도 그런지 궁금해요.',
    images: [], verdict: null,
    likeCount: 14, createdAt: '2026-10-15T20:30:00+09:00',
  },
  {
    postId: 101, category: 'FREE', caseId: null,
    author: { nickname: '종이 냄새', character: 'nambul' },
    title: '판결은 정답이 없다는 말이 계속 생각나요',
    body: '세 판결을 나란히 놓고 보니 누가 맞았다기보다, 각자 무엇을 무겁게 봤는지가 보였어요.',
    images: [], verdict: null,
    likeCount: 3, createdAt: '2026-10-14T08:12:00+09:00',
  },
  // 커뮤니티 메인(FE-89)의 사건별 글. 사건 2 ~ 6은 아직 체험할 수 없어 기사를 읽은 질문 · 이야기만 둔다(판결 공유 글 없음)
  ...[
    [109, 'QUESTION', 2, '오후의 서기', '선입금 사기에서 피해 금액은 어디까지 봐야 하나요?', '기사에는 수십만 원대라고만 나오는데, 같은 판매글로 여러 사람에게 받았다면 금액을 다 합쳐서 보는지 궁금해요.', 9, '2026-10-23T09:12:00+09:00'],
    [110, 'QUESTION', 2, '창가의 방청석', '피해를 돌려줬는지가 형량에 얼마나 영향을 줄까요', '물건을 보내지 않은 뒤에 돈을 돌려줬다면 판단이 많이 달라지는지 궁금합니다.', 6, '2026-10-22T21:30:00+09:00'],
    [111, 'QUESTION', 2, '밤샘 방청객', '중고 거래 사기, 처음이면 얼마나 가볍게 보나요?', '전에 비슷한 일이 없었다는 게 판결에서 어느 정도 무게를 갖는지 알고 싶어요.', 4, '2026-10-22T13:05:00+09:00'],
    [112, 'FREE', 3, '말없는 판사', '동업 관계라 믿음을 저버린 게 더 크게 느껴져요', '모르는 사람에게 속은 것과 함께 가게를 열자던 사람에게 속은 것은 느낌이 다르네요. 판결에서도 그 차이를 볼지 궁금합니다.', 14, '2026-10-23T08:40:00+09:00'],
    [113, 'FREE', 3, '종이 냄새', '출자금을 어디에 썼는지가 계속 걸려요', '기사에는 사업과 무관한 곳이라고만 나와서, 그 쓰임새가 밝혀지면 생각이 또 바뀔 것 같아요.', 5, '2026-10-22T19:22:00+09:00'],
    [114, 'QUESTION', 3, '조용한 저울', '갚을 생각이 있었는지는 어떻게 따지나요?', '처음부터 가로챌 생각이었는지, 나중에 갚으려 했는지를 무엇으로 판단하는지 궁금해요.', 8, '2026-10-21T22:10:00+09:00'],
    [115, 'QUESTION', 4, '조용한 저울', '층간소음 갈등이 오래됐다는 사정은 어떻게 보나요?', '몇 달 동안 쌓인 갈등이었다는 게 판단에 영향을 주는지, 아니면 그날 일만 보는지 궁금합니다.', 17, '2026-10-21T10:15:00+09:00'],
    [116, 'QUESTION', 4, '첫 재판', '우발적인 몸싸움은 어떻게 판단하나요?', '복도에서 마주쳐 말다툼이 번진 경우라면 미리 마음먹은 것과 어떻게 구분하는지 알고 싶어요.', 3, '2026-10-20T18:44:00+09:00'],
    [117, 'FREE', 5, '새벽의 배심원', '술에 취했다는 사정은 어떻게 보는지 찾아봤어요', '취해 있었다는 것만으로 가볍게 보지는 않는다는 이야기를 읽었어요. 사건 기록이 열리면 직접 확인해 보고 싶네요.', 12, '2026-10-20T23:02:00+09:00'],
    [118, 'QUESTION', 5, '늦은 오후 판사', '처음 본 사이의 시비도 계획성이 문제가 되나요?', '모르는 사람과 순간적으로 붙은 시비라면 계획성은 따지지 않는 건지 궁금합니다.', 4, '2026-10-20T12:30:00+09:00'],
    [119, 'QUESTION', 6, '오후의 서기', '업무 갈등이 쌓인 기간을 어떻게 볼지 궁금해요', '오래 쌓인 갈등이 원인이었다면 그 사정을 판단에 넣는지, 넣는다면 어느 쪽으로 보는지 궁금합니다.', 7, '2026-10-19T16:48:00+09:00'],
    [120, 'FREE', 6, '서류 넘기는 손', '직장 사건 기사는 남 일 같지 않네요', '업무 분담으로 다투는 일은 흔한데, 그게 폭행으로 번졌다는 게 무겁게 다가와요.', 2, '2026-10-19T09:20:00+09:00'],
  ].map(([postId, category, caseId, nickname, title, body, likeCount, createdAt], index) => ({
    postId, category, caseId,
    author: { nickname, character: index % 2 ? 'naero' : 'nambul' },
    title, body, images: [], verdict: null, likeCount, createdAt,
  })),
];


// 글쓴이 명함(FE-88): 글 · 댓글의 author에 붙는 프로필카드 꾸미기 + 공개 성향카드. 실제 API(커뮤니티)가 생기면 같은 모양으로 내려받을 값이다.
// profileCard = 프로필카드 꾸미기(P-7과 같은 칸), shownType = 공개 도장이 찍힌 성향카드 한 장(slot: initial · retake · current, type = P-4 유형 모양, deco = 성향카드 꾸미기).
// level · job · completedCount는 마이페이지(MOCK_PROFILE)처럼 아직 서버 값이 없어 목이다
const typeOf = (code) => {
  const { name, meme, character } = TYPES[code];
  return { name, meme, character, shareId: shareIdOfCode(code), imageUrl: `/images/characters/personality/${String(imageNumberOf(code)).padStart(2, '0')}.webp` };
};
const BASIC_TYPE_DECO = { frame: 'basic', background: 'classic', sashStyle: 'none', sashColorShort: 'wine', sashColorLong: 'wine', sashIconLeft: 'none', sashIconRight: 'none' };
const card = (portrait, patch) => ({ ...DEFAULT_DECO, portrait, ...patch });
const shown = (slot, code, patch = {}) => ({ slot, type: typeOf(code), deco: { ...BASIC_TYPE_DECO, ...patch } });

export const seedAuthors = {
  '새벽의 배심원': { intro: '첫인상은 늘 한 번 의심해 봐요.', level: 7, job: '수습 판사', completedCount: 12,
    profileCard: card('/images/characters/roles/naero_judge.webp', { frame: 'gold', sashStyle: 'short', sashColorShort: 'wine', sashIconLeft: 'scale', sashIconRight: 'star' }),
    shownType: shown('current', 'ENFJ') },
  '밤샘 방청객': { intro: '질문이 많은 사람입니다.', level: 2, job: '방청객', completedCount: 1,
    profileCard: card('/images/characters/scene/nambul_guide-happy.webp', { frame: 'stitch', background: 'linen', sashStyle: 'short', sashColorShort: 'green', sashIconLeft: 'cup', sashIconRight: 'heart' }),
    shownType: shown('current', 'INFP', { sashStyle: 'long', sashColorLong: 'wine' }) },
  '말없는 판사': { intro: '말은 적게, 기록은 꼼꼼히.', level: 9, job: '부장 판사', completedCount: 21,
    profileCard: card('/images/characters/roles/naero_prosecutor.webp', { frame: 'navy', background: 'news' }),
    shownType: shown('initial', 'ISTJ', { frame: 'gold' }) },
  '오후의 서기': { intro: '기록은 끝까지 읽고 말하는 편이에요.', level: 4, job: '서기', completedCount: 5,
    profileCard: card('/images/characters/scene/nambul_read-thinking.webp', { frame: 'wood', background: 'latte', sashStyle: 'long', sashColorLong: 'navy' }),
    shownType: shown('initial', 'INTP', { frame: 'gold', background: 'tarot' }) },
  '창가의 방청석': { intro: '양쪽 다 들어 보고 정할게요.', level: 3, job: '방청객', completedCount: 3,
    profileCard: card('/images/characters/scene/naero_react-surprised.webp', { frame: 'navy', background: 'news' }),
    shownType: shown('retake', 'ENTJ', { frame: 'navy', sashStyle: 'short', sashColorShort: 'gold', sashIconLeft: 'gavel' }) },
  '첫 재판': { intro: '오늘이 첫 재판이에요.', level: 1, job: '수습 판사', completedCount: 1,
    profileCard: card('/images/characters/roles/nambul_defense.webp'),
    shownType: shown('current', 'ESFJ') },
  '조용한 저울': { intro: '저울이 기울기 전에 한 번 더.', level: 6, job: '판사', completedCount: 9,
    profileCard: card('/images/characters/scene/naero_guide-happy.webp', { frame: 'gold', background: 'latte' }),
    shownType: shown('current', 'INFJ', { background: 'cream', frame: 'wood' }) },
  '종이 냄새': { intro: '신문 지면 넘기는 소리가 좋아요.', level: 5, job: '서기', completedCount: 7,
    profileCard: card('/images/characters/scene/nambul_idle-neutral.webp', { background: 'news', sashStyle: 'long', sashColorLong: 'gold' }),
    shownType: shown('retake', 'ISFP') },
  '늦은 오후 판사': { intro: '결론은 천천히 내려요.', level: 8, job: '부장 판사', completedCount: 16,
    profileCard: card('/images/characters/roles/nambul_judge.webp', { frame: 'wood', background: 'latte' }),
    shownType: shown('current', 'ESTJ', { frame: 'navy' }) },
  '서류 넘기는 손': { intro: '기사부터 꼼꼼히 읽는 편이에요.', level: 2, job: '방청객', completedCount: 2,
    profileCard: card('/images/characters/scene/naero_read-thinking.webp', { frame: 'stitch' }),
    shownType: null },
};

export const seedComments = {
  108: [
    { commentId: 1, author: { nickname: '오후의 서기', character: 'nambul' }, body: '저도 첫인상이랑 최종 판결이 달랐어요. 사실관계를 읽고 나서 바뀌더라고요.', createdAt: '2026-10-19T22:01:00+09:00' },
    { commentId: 2, author: { nickname: '창가의 방청석', character: 'naero' }, body: '어떤 요소에서 바뀌셨는지 궁금해요!', createdAt: '2026-10-19T22:40:00+09:00' },
  ],
  107: [
    { commentId: 3, author: { nickname: '조용한 저울', character: 'naero' }, body: '사건 정보 확인의 법률 안내에 양형 요소 설명이 있었던 것 같아요.', createdAt: '2026-10-18T15:20:00+09:00' },
  ],
  105: [
    { commentId: 4, author: { nickname: '새벽의 배심원', character: 'naero' }, body: '많이 골랐다고 틀린 건 아니라고 생각해요. 왜 골랐는지가 중요한 것 같아요.', createdAt: '2026-10-17T10:02:00+09:00' },
  ],
  109: [
    { commentId: 5, author: { nickname: '말없는 판사', character: 'naero' }, body: '피해자가 여럿이면 따로 보는지도 궁금하네요.', createdAt: '2026-10-23T10:01:00+09:00' },
    { commentId: 6, author: { nickname: '종이 냄새', character: 'nambul' }, body: '저도 같은 부분이 궁금했어요.', createdAt: '2026-10-23T11:20:00+09:00' },
  ],
  110: [
    { commentId: 7, author: { nickname: '오후의 서기', character: 'nambul' }, body: '돌려준 시점도 중요할 것 같아요.', createdAt: '2026-10-22T22:05:00+09:00' },
  ],
  111: [
    { commentId: 8, author: { nickname: '첫 재판', character: 'nambul' }, body: '사건 기록이 열리면 같이 확인해 봐요.', createdAt: '2026-10-22T14:40:00+09:00' },
  ],
  112: [
    { commentId: 9, author: { nickname: '창가의 방청석', character: 'naero' }, body: '저도 믿음을 저버렸다는 쪽이 더 크게 다가왔어요.', createdAt: '2026-10-23T09:30:00+09:00' },
    { commentId: 10, author: { nickname: '늦은 오후 판사', character: 'nambul' }, body: '자금을 누가 관리했는지도 같이 봐야 할 것 같아요.', createdAt: '2026-10-23T12:15:00+09:00' },
  ],
  114: [
    { commentId: 11, author: { nickname: '새벽의 배심원', character: 'naero' }, body: '돈을 쓴 뒤의 행동을 보는 경우가 많다고 들었어요.', createdAt: '2026-10-22T08:50:00+09:00' },
  ],
  115: [
    { commentId: 12, author: { nickname: '밤샘 방청객', character: 'nambul' }, body: '그날 누가 먼저 손을 댔는지도 궁금해요.', createdAt: '2026-10-21T12:00:00+09:00' },
    { commentId: 13, author: { nickname: '서류 넘기는 손', character: 'naero' }, body: '갈등 기간이 길면 양쪽 사정을 다 들어 봐야겠네요.', createdAt: '2026-10-21T15:34:00+09:00' },
  ],
  117: [
    { commentId: 14, author: { nickname: '조용한 저울', character: 'naero' }, body: '찾아보신 내용 공유해 주셔서 고마워요.', createdAt: '2026-10-21T08:10:00+09:00' },
  ],
};
