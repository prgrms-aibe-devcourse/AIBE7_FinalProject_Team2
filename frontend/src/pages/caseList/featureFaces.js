import { button, element, mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { AXES } from '../../utils/personalityAxes.js';
import { boxLink } from './hoverBox.js';
import { EDITORIALS, editorialOfDay, NAMBUL_TITLE, readerPicks, TEST_STORY, typeGallery } from './faceContent.js';

// S-02 신문의 성향 테스트 · 오피니언 면 본문 (FE-126). caseListPage.js의 FACE_VIEWS가 부른다.
// 사건 기사는 없지만 누를 수 있는 칸(유형 · 독자 투고)은 기사처럼 칸 전체에 호버 상자를 띄운다. 서버에서 받는 것(내 성향 결과 · 독자 투고)은 지면을 먼저 그린 뒤 채우고, 못 받으면 그 칸만 빠진다
const NAMBUL_FACE = '/images/characters/icon/generated/nambul_face-happy.webp';
const STORY_IMAGE = '/images/characters/mascot/trio_idle.webp';

function dense(paragraphs) {
  const node = element('div', 'dense');
  paragraphs.forEach((text) => node.append(element('p', '', text)));
  return node;
}

// 연출 그림(읽지 않는다). 못 받으면 칸에서 뺀다
function picture(src, className, { width, height, lazy = true } = {}) {
  const image = element('img', className);
  image.src = src;
  image.alt = '';
  if (width) { image.width = width; image.height = height; }
  if (lazy) image.loading = 'lazy';
  image.addEventListener('error', () => image.remove(), { once: true });
  return image;
}

// 성향 테스트 면: 소개 기사(그림) | 네 개의 저울 + 입구 광고 · 열여섯 유형 갤러리(엄격 → 관대). 다른 면과 같은 신문 최소 높이 안에 들도록 넓은 화면 8열 · 중간 4열(가로 칸)
// ctx: { navigate, api, active } — active()가 거짓이면(다른 면 · 화면으로 갔으면) 늦게 온 결과로 지면을 고치지 않는다
export function testFace({ navigate, api, active }) {
  const goTest = () => navigate('testPaper', { from: 'paper' });

  const banner = element('div', 'banner pf-banner');
  banner.append(element('h2', '', TEST_STORY.headline));

  const story = element('article', 'article pf-story');
  const kicker = element('div', 'kicker');
  kicker.append(element('span', '', '판결 성향 특집'));
  const figure = element('figure', 'pf-figure');
  figure.append(picture(STORY_IMAGE, 'pf-figure-image', { width: 16, height: 9, lazy: false }));
  const jump = button('(성향 테스트 지면에서 계속)', goTest, 'start start-filled');
  jump.setAttribute('aria-label', '성향 테스트 지면에서 계속, 판결 성향 테스트 하러 가기');
  story.append(kicker, figure, element('p', 'lead-intro', TEST_STORY.deck), element('p', 'byline', '편집부'), dense(TEST_STORY.body), jump);

  // 네 개의 저울: 축마다 엄격 쪽 이름 ↔ 관대 쪽 이름
  const scales = element('aside', 'pf-scales');
  scales.setAttribute('aria-label', '네 개의 저울');
  scales.append(element('p', 'filler-label', '네 개의 저울'));
  const list = element('dl', 'pf-scale-list');
  AXES.forEach(({ label, strict, lenient }) => {
    const row = element('div', 'pf-scale-row');
    const ends = element('dd', 'pf-scale-ends');
    const beam = element('span', 'pf-scale-beam');
    beam.setAttribute('aria-hidden', 'true');
    ends.append(element('span', 'is-strict', strict.name), beam, element('span', 'is-lenient', lenient.name));
    row.append(element('dt', '', label), ends);
    list.append(row);
  });
  scales.append(list, element('p', 'pf-scale-note', '왼쪽은 엄격한 쪽, 오른쪽은 관대한 쪽이에요. 어느 쪽이 옳다는 답은 없어요.'));

  const top = element('section', 'front-grid pf-top');
  top.setAttribute('aria-label', '판결 성향 테스트 소개');
  const side = element('div', 'pf-side');

  // 열여섯 유형 갤러리: 엄격 → 관대 순서로 8열 → 4열(1100px 이하, 가로 칸) → 2열(768px 이하). 칸마다 그림 · 관대 눈금 · 이름 · 한 줄. 칸을 누르면 그 유형 설명(공유 지면)
  const board = element('section', 'index pf-board');
  board.setAttribute('aria-label', '열여섯 판사 유형');
  const head = element('div', 'index-head');
  head.append(element('h2', '', '열여섯 판사 유형'), element('span', '', '엄격한 쪽부터 관대한 쪽으로'));
  const grid = element('ul', 'pf-gallery');
  const cards = new Map(); // shareId → 유형 칸(내 유형 표시)
  typeGallery().forEach(({ shareId, name, oneLiner, image, lenient }) => {
    const card = element('li', 'pf-card');
    const open = () => navigate('testType', { shareId });
    const meter = element('p', 'pf-meter');
    const pips = element('span', 'pf-pips');
    pips.setAttribute('aria-hidden', 'true');
    for (let i = 0; i < 4; i += 1) pips.append(element('span', i < lenient ? 'is-on' : ''));
    meter.append(pips, element('span', '', lenient === 0 ? '엄격 넷' : lenient === 4 ? '관대 넷' : `엄격 ${4 - lenient} · 관대 ${lenient}`));
    const title = element('h3');
    title.append(button(name, open, 'pf-card-name'));
    card.append(picture(image, 'pf-card-image', { width: 4, height: 5 }), meter, title, element('p', 'pf-card-line', oneLiner));
    grid.append(boxLink(card, open));
    cards.set(shareId, card);
  });
  board.append(head, grid);

  // 입구 광고: 결과가 없으면 테스트 권유, 있으면 내 유형과 결과 다시 보기(아래에서 받으면 바꾼다)
  const ad = boxLink(element('aside', 'filler filler-ad pf-ad'), () => (ad.dataset.mine ? navigate('testPaperResult') : goTest()));
  ad.setAttribute('aria-label', '광고');
  ad.append(element('p', 'filler-label', '광고'), element('h3', 'filler-title', '열여섯 장면에 답하면 나의 판사 유형이 나와요'),
    element('p', 'filler-text', '5분이면 충분해요. 결과는 사건을 판결할 때의 내 모습과 견줘 볼 수 있어요.'),
    button('판결 성향 테스트 하러 가기', goTest, 'start filler-go'));

  side.append(scales, ad);
  top.append(story, side);

  // 내 성향 결과(P-3): 있으면 갤러리에 "내 유형" 도장을 찍고 광고를 결과 안내로 바꾼다. 없거나(404) 로그인 전이면 그대로 둔다
  api.getMyPersonality?.().then((result) => {
    if (!active() || !result?.type) return;
    const card = cards.get(result.type.shareId);
    if (card) {
      card.classList.add('is-mine');
      card.append(element('span', 'status-stamp status-done pf-mine', '내 유형'));
    }
    ad.dataset.mine = 'true'; // 칸을 누르면 테스트 대신 내 결과로
    ad.replaceChildren(element('p', 'filler-label', '내 판결 성향'), element('h3', 'filler-title', result.type.name),
      element('p', 'filler-text', result.type.oneLiner ?? ''),
      button('내 결과 다시 보기', () => navigate('testPaperResult'), 'start filler-go'));
  }).catch(() => {});

  return [banner, top, board];
}

// 오피니언 면: 사설 제목(배너) | 사설 본문(그림 · 인용) + 남불의 한마디 · 지난 사설 | 독자 투고(커뮤니티 공감 많은 글) + 편집부에 보내기
export function opinionFace({ navigate, api, active }) {
  const banner = element('div', 'banner op-banner');
  const headline = element('h2');
  banner.append(element('p', 'op-label', '사설'), headline);

  const editorial = element('article', 'article op-editorial');
  editorial.setAttribute('aria-label', '사설 본문');
  const columnLine = element('p', 'op-column-line');

  // 사설 한 편을 지면에 싣는다(배너 제목 · 본문 · 남불의 한마디가 함께 바뀐다)
  const shelf = element('ol', 'op-shelf');
  function show(index) {
    const { headline: text, quote, image, body, nambul } = EDITORIALS[index];
    headline.textContent = text;
    const figure = element('figure', 'op-figure');
    figure.append(picture(image, 'op-figure-image'));
    const story = dense(body);
    const pull = element('blockquote', 'op-quote', quote);
    story.insertBefore(pull, story.children[2] ?? null); // 둘째 문단 뒤에 인용구
    story.prepend(figure); // 그림은 첫 단 맨 위(본문과 같은 단 흐름)
    editorial.replaceChildren(element('p', 'byline', `내Law남불 신문 편집부 · 사설 ${index + 1}/${EDITORIALS.length}`), story);
    columnLine.textContent = nambul;
    shelf.querySelectorAll('button').forEach((item, i) => {
      if (i === index) item.setAttribute('aria-current', 'true'); else item.removeAttribute('aria-current');
    });
  }

  const column = element('aside', 'op-column');
  column.setAttribute('aria-label', NAMBUL_TITLE);
  column.append(element('p', 'filler-label', NAMBUL_TITLE), picture(NAMBUL_FACE, 'op-column-face', { width: 72, height: 72, lazy: false }), columnLine);

  // 지난 사설: 다섯 편 제목. 오늘 사설은 날마다 차례로 돌아가고, 누르면 그 편을 이 자리에 싣는다
  const archive = element('nav', 'op-archive');
  archive.setAttribute('aria-label', '지난 사설');
  EDITORIALS.forEach(({ headline: text }, index) => {
    const item = element('li');
    item.append(button(text, () => show(index), 'op-shelf-item'));
    shelf.append(item);
  });
  archive.append(element('p', 'filler-label', '지난 사설'), shelf);

  const side = element('div', 'op-side');
  side.append(column, archive);
  const top = element('section', 'front-grid op-top');
  top.setAttribute('aria-label', '사설');
  top.append(editorial, side);
  show(editorialOfDay());

  // 독자 투고: 커뮤니티 글(API가 없는 실제 모드에서는 이 칸을 그리지 않는다). 받기 전에는 칸 자리만 둔다
  const letters = element('section', 'index op-letters');
  letters.setAttribute('aria-label', '독자 투고');
  const head = element('div', 'index-head');
  const note = element('span', '', '커뮤니티 · 공감 많은 순');
  if (API_MODE === 'mock') note.append(mockTag()); // 커뮤니티 글은 아직 목 데이터다(FE-56 표기 규칙)
  head.append(element('h2', '', '독자 투고'), note);
  const grid = element('div', 'op-letter-grid');
  letters.append(head, grid);

  const send = element('aside', 'filler filler-ad op-send');
  send.setAttribute('aria-label', '독자 투고 안내');
  send.append(element('p', 'filler-label', '독자 투고'), element('h3', 'filler-title', '편집부에 내 생각을 보내 주세요'),
    element('p', 'filler-text', '판결해 본 사건, 궁금한 양형 이야기, 무엇이든 커뮤니티에 남기면 이 면에 실릴 수 있어요.'),
    button('커뮤니티로 가기', () => navigate('communityPosts'), 'start filler-go'));
  grid.append(boxLink(send, () => navigate('communityPosts')));

  if (api.getCommunityPosts) {
    api.getCommunityPosts({ sort: 'LIKES' }).then(({ posts }) => {
      if (!active()) return;
      readerPicks(posts).forEach((post) => {
        const open = () => navigate('communityPost', { postId: post.postId });
        const card = element('article', 'article op-letter');
        const title = element('h3');
        title.append(button(post.title, open, 'op-letter-title'));
        const by = element('p', 'byline', `${post.author?.nickname ?? '독자'} · 공감 ${post.likeCount}`);
        card.append(title, element('p', 'op-letter-text', post.excerpt), by);
        grid.insertBefore(boxLink(card, open), send);
      });
    }).catch(() => {});
  } else {
    letters.hidden = true;
  }

  return [banner, top, letters];
}
