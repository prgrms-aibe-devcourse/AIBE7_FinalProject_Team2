// S-02 신문의 성향 테스트 · 오피니언 · 색인 면 내용 (FE-126). DOM을 쓰지 않는다(faceContent.test.js). 그리는 쪽은 featureFaces.js · caseListPage.js
import og from '../../../og/personality-types.json' with { type: 'json' };
import { TYPE_NUMBERS } from '../../utils/personalityTypes.js';

// ---- 성향 테스트 면 ----
// 관대 쪽 글자(I · N · F · P) 수 = 관대 정도(0 ~ 4). 유형 코드는 화면에 쓰지 않고 이 계산에만 쓴다(utils/personalityTypes.js)
const LENIENT = new Set(['I', 'N', 'F', 'P']);
const CODE_OF = Object.fromEntries(Object.entries(TYPE_NUMBERS).map(([code, number]) => [number, code]));
export const toleranceOf = (code) => [...code].filter((letter) => LENIENT.has(letter)).length;
const imageOf = (imageNumber) => og.imagePath.replace('{imageNumber}', String(imageNumber).padStart(2, '0'));

/**
 * 열여섯 유형을 엄격 → 관대 순서로 한 줄에 세운다(관대 정도가 같으면 유형 번호 순). 갤러리는 이 순서로 칸을 채운다.
 * 이름 · 한 줄 설명 · 공유 주소 · 그림은 서버 시드와 같은 og/personality-types.json에서 온다(목 데이터가 아니다)
 * @returns {{ shareId: string, name: string, oneLiner: string, image: string, lenient: number }[]}
 */
export function typeGallery(types = og.types) {
  return types
    .map((type) => ({ type, code: CODE_OF[type.typeNumber] }))
    .filter(({ code }) => code)
    .sort((a, b) => toleranceOf(a.code) - toleranceOf(b.code) || a.type.typeNumber - b.type.typeNumber)
    .map(({ type, code }) => ({ shareId: type.shareId, name: type.name, oneLiner: type.oneLiner, image: imageOf(type.imageNumber), lenient: toleranceOf(code) }));
}

// 성향 테스트 소개 기사. 문항 · 축 수는 personality-test-content.md와 같다. A · B 어느 쪽이 낫다고 말하지 않는다(PRODUCT 원칙 1)
export const TEST_STORY = {
  headline: '나는 엄격한 판사일까, 관대한 판사일까',
  deck: '열여섯 개의 일상 장면, 네 개의 저울로 보는 나의 판결 성향',
  body: [
    '세 번째 사과를 받아 줄지, 규칙을 어긴 친구를 봐줄지. 법정이 아니어도 우리는 매일 작은 판결을 내립니다. 판결 성향 테스트는 이런 일상 장면 열여섯 개에서 내가 무엇을 먼저 보는지 묻습니다.',
    '답을 모으면 네 개의 저울이 기웁니다. 사과를 말로 믿는지 행동으로 확인하는지, 잘잘못을 결과로 재는지 의도로 재는지, 원칙과 관계 중 어디에 무게를 두는지, 질서와 다시 한 번의 기회 중 무엇을 고르는지입니다.',
    '어느 쪽이 옳다는 답은 없습니다. 다만 사건 기록을 읽고 실제로 형량을 정해 보면, 테스트 결과와 다른 내 모습을 만나기도 합니다.',
  ],
};

// ---- 오피니언 면 ----
// 편집부 사설 다섯 편: 날마다 한 편씩 돌려 싣고(editorialOfDay), 지면의 "지난 사설"로 다른 편을 고를 수 있다.
// 서비스가 말하는 고정 문구다. 특정 사건 · 판결 · 재판부가 옳다거나 틀렸다고 말하지 않는다(PRODUCT 원칙 1)
// nambul: 같은 날 싣는 "남불의 한마디"(캐릭터 말투 — 솔직 · 감탄, 해요체). 실제 판사의 일을 단정하지 않고 남불 자신의 이야기로 한다
// image: 사설 사진으로 쓰는 마스코트 그림(연출 이미지라 읽지 않는다)
export const EDITORIALS = [
  {
    headline: '판결은 기사 제목에서 시작하지 않는다',
    quote: '처음 떠올린 숫자가 그대로 남는 경우는 생각보다 드뭅니다.',
    image: '/images/characters/mascot/trio_think.webp',
    body: [
      '기사 제목 한 줄만 읽고도 우리는 형량을 떠올립니다. "그런 짓을 했으면 무겁게 받아야지." 이 지면의 사건들도 처음에는 그렇게 읽힙니다. 체험을 시작할 때 첫인상을 먼저 적어 두게 하는 것도 그 때문입니다.',
      '제목은 사건의 가장 날카로운 부분만 잘라 보여 줍니다. 무엇이 일어났는지는 말해 주지만, 왜 그 자리에 있었는지, 그 전에 두 사람 사이에 무엇이 있었는지는 말해 주지 않습니다. 제목이 거짓인 것은 아닙니다. 다만 판결에 필요한 이야기의 일부일 뿐입니다.',
      '사건 기록을 한 장씩 넘기면 이야기가 달라집니다. 그 뒤에 무엇을 했는지, 피해를 어떻게 갚으려 했는지, 같은 일을 전에도 한 적이 있는지. 검사와 변호인의 말을 같은 분량으로 듣고 나면, 처음 떠올린 숫자가 그대로 남는 경우는 생각보다 드뭅니다.',
      '숫자가 바뀌었다고 처음 판단이 틀렸던 것도 아닙니다. 첫인상은 우리가 무엇에 먼저 분노하고 무엇에 먼저 마음이 움직이는지를 보여 줍니다. 기록을 다 읽은 뒤의 숫자는 그 마음 위에 사실을 하나씩 올려놓은 결과입니다. 둘을 나란히 놓아 보는 것만으로도 나에 대해 꽤 많은 것을 알게 됩니다.',
      '실제 재판부도, AI도, 그리고 독자 여러분도 같은 기록을 읽습니다. 셋의 판결이 다르다고 누군가 틀린 것은 아닙니다. 각자 무엇을 무겁게 봤는지가 다를 뿐입니다.',
      '편집부가 바라는 것은 정답을 맞히는 일이 아니라, 내가 무엇을 무겁게 보는 사람인지 알아 가는 일입니다. 오늘 지면의 제목 하나를 골라, 끝까지 읽어 보시길 권합니다.',
    ],
    nambul: '제목만 보고 "이건 무조건 무겁게!" 했다가 기록 읽고 슬그머니 숫자 고친 적, 저만 있는 거 아니죠? 저는 그게 부끄럽지 않아요. 끝까지 읽었다는 뜻이니까요!',
  },
  {
    headline: '같은 기록, 세 개의 판결',
    quote: '판결이 갈리는 곳에는 언제나 서로 다른 무게추가 있습니다.',
    image: '/images/characters/mascot/compare_trio-thinking.webp',
    body: [
      '이 신문의 사건은 판결이 세 번 내려집니다. 실제 재판부의 판결, 같은 기록을 읽은 AI의 판결, 그리고 독자 여러분의 판결입니다. 체험을 마치면 세 판결이 한 지면에 나란히 실립니다.',
      '세 판결이 모두 같은 날도 있습니다. 하지만 더 자주, 형의 종류나 기간이 조금씩 어긋납니다. 누군가는 집행유예를 골랐고, 누군가는 실형을 골랐습니다. 누군가는 피해 회복을 크게 봤고, 누군가는 범행 방법을 크게 봤습니다.',
      '이 어긋남을 채점으로 읽지 않기를 바랍니다. 실제 판결을 정답으로, 나머지를 오답으로 놓는 순간 판결은 퀴즈가 됩니다. 실제 재판부는 법정에서 사람을 직접 보고, 이 지면에 다 옮기지 못한 기록까지 읽었습니다. 그 차이를 인정하는 것이 먼저입니다.',
      'AI의 판결도 마찬가지입니다. AI는 양형기준과 기록을 빠짐없이 대조하는 데 능하지만, 무엇을 더 무겁게 볼지는 결국 판단의 문제입니다. AI가 내린 숫자 역시 하나의 의견입니다.',
      '그래서 이 신문은 숫자보다 판단 요소를 함께 보여 줍니다. 세 판결이 각각 무엇을 형량을 높이는 쪽으로, 무엇을 낮추는 쪽으로 봤는지. 판결이 갈리는 곳에는 언제나 서로 다른 무게추가 있습니다.',
      '다음 사건에서 내 판결이 혼자 다른 곳에 서 있다면, 그 자리를 잠시 살펴보시길 바랍니다. 그곳이 바로 내 저울이 기우는 방향입니다.',
    ],
    nambul: '세 판결을 나란히 봤는데 저만 혼자 멀리 떨어져 있어서 깜짝 놀랐어요! 그런데 이유를 다시 읽어 보니, 저는 사과를 엄청 크게 보는 사람이더라고요.',
  },
  {
    headline: '양형기준은 정답표가 아니라 자(尺)다',
    quote: '자는 길이를 재 줄 뿐, 어디서 자를지는 정해 주지 않습니다.',
    image: '/images/characters/mascot/law-scale_think.webp',
    body: [
      '판결 체험을 하다 보면 "양형기준"이라는 말을 자주 만납니다. 대법원 양형위원회가 범죄마다 만들어 둔 기준으로, 판사가 형을 정할 때 참고하는 범위를 알려 줍니다.',
      '양형기준은 대략 이렇게 쓰입니다. 먼저 사건이 어떤 유형에 해당하는지 고릅니다. 다음으로 형을 크게 움직이는 사정을 따져 감경 · 기본 · 가중 중 어느 영역에서 정할지 봅니다. 마지막으로 그 영역 안에서 다른 사정들을 더해 최종 형량을 정합니다.',
      '이 기준에는 법적 구속력이 없습니다. 판사는 기준을 벗어난 형을 선고할 수 있고, 그때는 판결문에 그 이유를 적습니다. 기준이 판결을 대신하지 않는다는 뜻입니다.',
      '그래서 양형기준은 정답표라기보다 자에 가깝습니다. 자는 길이를 재 줄 뿐, 어디서 자를지는 정해 주지 않습니다. 같은 자를 쓰고도 서로 다른 곳에 선을 긋는 일은 얼마든지 있습니다.',
      '이 신문의 체험에서 형량 범위를 보여 주는 것도 같은 이유입니다. 범위 밖으로 나가지 말라는 울타리가 아니라, 내 판단이 어디쯤 서 있는지 가늠해 보라는 눈금입니다.',
      '눈금을 보고도 범위 끝까지 가고 싶은 사건이 있다면, 그 마음이 어디서 왔는지 적어 보시길 바랍니다. 판결문에 이유를 적는 일은 판사만의 일이 아닙니다.',
    ],
    nambul: '처음엔 범위가 있으면 가운데만 고르면 되는 줄 알았어요. 근데 해 보니까 가운데로 가는 것도 이유가 있어야 하더라고요. 자는 자고, 선은 제가 긋는 거예요!',
  },
  {
    headline: '용서는 판결의 몇 퍼센트인가',
    quote: '합의서 한 장 뒤에는 저마다 다른 사정이 있습니다.',
    image: '/images/characters/mascot/law-book_think.webp',
    body: [
      '사건 기록에서 가장 오래 머무르게 되는 문장은 의외로 짧습니다. "피해자와 합의하였다." "피해자가 처벌을 원하지 않는다." 이 한 줄 앞에서 독자의 저울은 크게 흔들립니다.',
      '양형기준에서도 피해 회복과 처벌불원은 형을 정할 때 고려하는 사정으로 다뤄집니다. 피해가 얼마나 회복되었는지, 피해자가 어떤 마음인지는 형을 정하는 데 중요한 사정입니다.',
      '하지만 합의서 한 장 뒤에는 저마다 다른 사정이 있습니다. 진심으로 사과를 받아들인 합의도 있고, 하루라도 빨리 일상으로 돌아가고 싶어서 한 합의도 있습니다. 기록만으로 그 마음을 다 알 수는 없습니다.',
      '그래서 용서를 얼마나 무겁게 볼지는 읽는 사람마다 다릅니다. 피해자가 원하지 않는 처벌을 국가가 대신할 이유가 있느냐고 묻는 사람도 있고, 같은 잘못이 되풀이되지 않게 하는 것도 판결의 몫이라고 말하는 사람도 있습니다.',
      '두 생각 모두 판결에 필요한 질문입니다. 편집부는 어느 쪽이 옳다고 말하지 않겠습니다. 다만 합의라는 단어를 만났을 때 내 저울이 어느 쪽으로, 얼마나 기우는지는 한 번쯤 지켜보시길 권합니다.',
      '판결 성향 테스트의 첫 번째 저울이 사과를 묻는 것도 같은 까닭입니다. 사과를 말로 믿는지, 행동으로 확인하는지. 그 답이 사건 앞에서도 같은지 견줘 보면 흥미로운 차이를 만나게 됩니다.',
    ],
    nambul: '"처벌을 원하지 않는다"는 한 줄에 저는 늘 마음이 약해져요. 그래서 요즘은 그 줄을 읽기 전에 제 숫자를 먼저 적어 둬요. 얼마나 흔들리는지 보려고요!',
  },
  {
    headline: '엄격함과 관대함은 성적표가 아니다',
    quote: '저울은 어느 쪽으로 기울어도 고장 난 것이 아닙니다.',
    image: '/images/characters/mascot/law-gavel_think.webp',
    body: [
      '판결 성향 테스트 결과를 받아 든 독자들이 가장 많이 묻는 말이 있습니다. "엄격하게 나왔는데, 제가 너무 차가운 사람인가요?" "관대하게 나왔는데, 너무 물렁한 걸까요?"',
      '편집부의 답은 같습니다. 엄격함과 관대함은 점수가 아닙니다. 높을수록 좋은 것도, 낮을수록 좋은 것도 아닙니다. 저울은 어느 쪽으로 기울어도 고장 난 것이 아닙니다.',
      '엄격한 판단은 약속과 질서를 지키려는 마음에서 나옵니다. 규칙이 지켜져야 약한 사람이 보호받는다고 믿는 사람의 저울은 결과 쪽으로 기웁니다. 관대한 판단은 사람이 바뀔 수 있다는 믿음에서 나옵니다. 다시 한 번의 기회가 더 큰 잘못을 막는다고 믿는 사람의 저울은 의도 쪽으로 기웁니다.',
      '흥미로운 것은 같은 사람의 저울도 사건마다 다르게 기운다는 점입니다. 일상에서는 관대한 사람이 어떤 범죄 앞에서는 누구보다 엄격해지고, 원칙을 중시하는 사람이 어떤 사연 앞에서는 오래 망설입니다.',
      '이 신문이 성향 테스트와 판결 체험을 함께 두는 이유가 여기에 있습니다. 일상에서의 나와 사건 앞에서의 나를 나란히 놓으면, 하나의 유형 이름으로는 다 담기지 않는 내가 보입니다.',
      '오늘 받은 유형 이름은 결론이 아니라 출발점입니다. 그 이름을 들고 사건 하나를 판결해 보시길 바랍니다. 이름과 다른 내가 나와도 괜찮습니다.',
    ],
    nambul: '저는 테스트에선 엄청 관대하게 나왔는데요, 사기 사건 판결할 땐 제일 무겁게 매겼어요. 제 저울, 생각보다 사건을 많이 가리더라고요!',
  },
];
export const NAMBUL_TITLE = '남불의 한마디';

const DAY_MS = 24 * 60 * 60 * 1000;
/** 오늘 실을 사설 번호: 날짜(현지 기준)마다 하루에 한 편씩 차례로 돌린다. 같은 날에는 누구나 같은 사설을 본다 */
export function editorialOfDay(date = new Date(), count = EDITORIALS.length) {
  const day = Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / DAY_MS);
  return ((day % count) + count) % count;
}

export const READER_PICKS = 3; // 독자 투고 칸 수(넓은 화면 한 줄)
const EXCERPT_MAX = 90;
/** 본문 첫 문단을 한 칸에 맞게 줄인다(넘치면 말줄임) */
export function excerptOf(body = '') {
  const first = body.split(/\n+/).find((line) => line.trim()) ?? '';
  return first.length > EXCERPT_MAX ? `${first.slice(0, EXCERPT_MAX).trimEnd()}…` : first;
}
/** 독자 투고로 실을 글: 공감 많은 순으로 받은 목록에서 앞의 몇 개 */
export const readerPicks = (posts = [], count = READER_PICKS) => posts.slice(0, count).map((post) => ({ ...post, excerpt: excerptOf(post.body) }));

// ---- 색인 면 ----
/**
 * 색인의 사건 종류 버튼: 면 순서(order)대로 유형별 건수. 사건이 없는 유형은 빼고, 순서에 없는 유형은 맨 뒤에 붙인다
 * @param {{ crimeType: string }[]} cases
 * @param {string[]} order 범죄 유형 키 순서
 * @returns {{ crimeType: string, count: number }[]}
 */
export function crimeCounts(cases, order) {
  const counts = new Map(order.map((key) => [key, 0]));
  cases.forEach(({ crimeType }) => counts.set(crimeType, (counts.get(crimeType) ?? 0) + 1));
  return [...counts].filter(([, count]) => count).map(([crimeType, count]) => ({ crimeType, count }));
}

// 색인 한 장에 싣는 사건 수: 2단 × 10줄. 넘치면 장을 나눈다(아래 쪽 번호). 목록 칸은 이 줄 수만큼 높이를 미리 잡아 여백 채움이 줄어든다(caseList.css)
export const INDEX_PAGE_SIZE = 20;
/** 걸러진 건수로 장 수(최소 1)와, 원하는 장을 그 범위로 맞춘 값 */
export function indexPage(total, wanted, size = INDEX_PAGE_SIZE) {
  const pages = Math.max(1, Math.ceil(total / size));
  const page = Math.min(Math.max(1, wanted), pages);
  return { pages, page, from: (page - 1) * size, to: page * size };
}
