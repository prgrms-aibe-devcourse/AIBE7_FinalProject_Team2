// 로그인 전 마이페이지 (FE-138, BE-140 선행). 로그인하지 않았으면 서버를 부르지 않고 첫 화면의 틀만 보여 준다.
// 내 레벨 · 직업 · 닉네임 · 수치는 없고, 왼쪽은 샘플 프로필카드가 돌아가며 보이고 오른쪽 성향카드 자리는 기본 디자인의 \"?\" 카드(누르면 로그인)이다. 항목 화면(view)으로는 들어가지 않는다.
// 로그인 전에도 사건 체험과 성향 테스트는 할 수 있다 — 처음 로그인할 때 이 브라우저의 체험 기록과 첫인상 엽서는 계정으로 가져올 수 있고,
// 성향 테스트 결과는 가져오지 않는다(BE-140 결정). 서버가 마이페이지를 로그인 전용(401)으로 바꿔도 이 화면은 서버를 부르지 않아 그대로다
import { element } from '../../components/siteLayout.js';
import { emptyWindow, fitOptions, profileFace, typeDecoClasses } from '../../components/personCards.js';
import { fitPortraitIn } from '../../utils/portraitFit.js';
import { DEFAULT_TYPE_DECO } from '../../utils/typeCardDeco.js';
import { GUEST_CARDS, GUEST_CARD_INTERVAL_MS, GUEST_TYPE_IMAGES, GUEST_TYPE_INTERVAL_MS, caseTotalOf, illustrationTotalOf, nextCardIndex } from './guestCards.js';

// 왼쪽 자리: 샘플 프로필카드(guestCards.js)를 0.6초마다 무작위로 바꿔 보여 준다. 누르면 로그인 화면으로 간다(오른쪽 성향카드 자리와 같다).
// 그림은 미리 받아 두어 바뀔 때 비지 않게 한다. 다른 화면으로 가 자리가 떨어지면 스스로 멈추고, 탭이 가려져 있는 동안에는 바꾸지 않는다.
// 움직임 줄이기 설정에서는 한 장만 고정한다. 카드는 바뀌는 보여주기용이라 읽는 이름은 버튼이 맡는다
function cardSlot(navigate) {
  const slot = element('button', 'my-guest-slot');
  slot.type = 'button';
  slot.setAttribute('aria-label', '프로필카드 자리. 누르면 로그인 화면으로 가요');
  slot.addEventListener('click', () => navigate('login'));
  GUEST_CARDS.forEach(({ deco }) => { new Image().src = deco.portrait; });
  const draw = (index) => {
    const { nickname, level, line, deco } = GUEST_CARDS[index];
    const card = profileFace(deco, nickname, line, { level: `Lv.${level}`, lineLabel: '직업' });
    card.setAttribute('aria-hidden', 'true');
    slot.replaceChildren(card);
    return index;
  };
  let shown = draw(nextCardIndex(-1));
  if (globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return slot;
  const timer = setInterval(() => {
    if (!slot.isConnected) clearInterval(timer);
    else if (!document.hidden) shown = draw(nextCardIndex(shown));
  }, GUEST_CARD_INTERVAL_MS);
  return slot;
}

// 창 안에서 성향 카드 그림(32장, 내로 · 남불 번갈아 순서대로)이 뿌옇고 흐리게 비치다가 다음 그림으로 겹쳐 바뀐다.
// 그림은 다른 카드와 같은 맞춤(utils/portraitFit.js fitPortraitIn)으로 창을 가로 · 세로 꽉 채우고 가운데에 둔다(캐릭터의 투명 여백을 걷어 그림마다 크기가 같다).
// 바뀔 때는 다음 그림을 새로 만들어 맞춘 뒤 불투명도만 올리고(합성만 쓰는 값), 앞 그림은 내리고 나서 뗀다.
// 다음 한 장씩 미리 받고(32장을 한꺼번에 받지 않는다), 자리가 떨어지면 스스로 멈추며, 탭이 가려져 있는 동안에는 바꾸지 않는다. 움직임 줄이기에서는 한 장만 비춘다
function hazeLayers(window_) {
  const preload = (index) => { new Image().src = GUEST_TYPE_IMAGES[index % GUEST_TYPE_IMAGES.length]; };
  let shown = 0;
  let current = null;
  const show = (index) => {
    const layer = element('img', 'my-guest-haze');
    layer.alt = '';
    layer.setAttribute('aria-hidden', 'true');
    layer.src = GUEST_TYPE_IMAGES[index];
    const stopFit = fitPortraitIn(window_, layer, fitOptions({ center: true, side: 1 })); // 맞춤이 먼저(decode 순서), 그다음 보이기
    window_.prepend(layer); // "?" 아래에 깐다
    const previous = current;
    current = { layer, stopFit };
    const reveal = () => requestAnimationFrame(() => {
      layer.classList.add('is-on');
      if (!previous) return;
      previous.layer.classList.remove('is-on');
      setTimeout(() => { previous.layer.remove(); previous.stopFit(); }, HAZE_FADE_MS + 200);
    });
    layer.decode().then(reveal, reveal);
  };
  show(shown);
  if (globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
  preload(shown + 1);
  const timer = setInterval(() => {
    if (!window_.isConnected) { clearInterval(timer); return; }
    if (document.hidden) return;
    shown = (shown + 1) % GUEST_TYPE_IMAGES.length;
    show(shown);
    preload(shown + 1);
  }, GUEST_TYPE_INTERVAL_MS);
}

const HAZE_FADE_MS = 1200; // myPage.css .my-guest-haze의 transition과 같다

// 오른쪽 자리: 성향카드의 기본 디자인(타로카드, 꾸미기 없음) 그대로 아직 못 뽑은 "?" 카드를 두고, 창 안에는 성향 카드 그림이 뿌옇게 바뀌어 비친다.
// 위 이름 · 아래 한 줄 자리에는 "카드를 눌러 / 로그인 하세요". 누르면 로그인 화면으로 간다
function typeCardSlot(navigate) {
  const card = element('button', `my-card is-type is-empty my-guest-card ${typeDecoClasses(DEFAULT_TYPE_DECO)}`);
  card.type = 'button';
  card.setAttribute('aria-label', `성향카드 자리. ${CALL_TO_LOGIN.join(' ')} 누르면 로그인 화면으로 가요`);
  const window_ = emptyWindow(); // 가운데 "?"
  hazeLayers(window_);
  card.append(element('p', 'my-card-name', CALL_TO_LOGIN[0]), window_, element('p', 'my-card-note my-type-quote', CALL_TO_LOGIN[1]));
  card.addEventListener('click', () => navigate('login'));
  return card;
}

const CALL_TO_LOGIN = ['카드를 눌러', '로그인 하세요'];

export const GUEST_NOTE = '로그인하지 않아도 사건 체험과 성향 테스트는 할 수 있어요. 처음 로그인할 때 이 브라우저에서 한 체험 기록과 첫인상 엽서를 계정으로 가져올 수 있어요. 성향 테스트 결과는 가져오지 않으니, 로그인한 뒤 새로 하면 돼요.';

/** 첫 화면: [샘플 프로필카드(누르면 로그인) | 로그인 안내 | 성향카드 자리("카드를 눌러 로그인 하세요")] + 아래 안내 한 단락 */
export function renderGuestTop(navigate, api) {
  const section = element('section', 'my-top is-guest');
  const middle = element('div', 'my-top-middle my-guest');
  // 제목은 임시 문구다(10/29)
  const title = element('h2', 'my-guest-title', '판사가 되어 당신만의 판결을 내려보세요.');
  const list = element('ul', 'my-guest-list');
  ['프로필카드를 꾸미고 레벨을 올려요', '체험에서 만난 캐릭터를 모아요', '커뮤니티에 글과 댓글을 남겨요'].forEach((text) => list.append(element('li', '', text)));
  // 사건 · 일러스트 총 개수는 서버에서 받는다: 공개 사건 목록(API 1)의 summary.total, 일러스트 총 개수 API(I-1)의 total.
  // 받기 전 · 한쪽이라도 못 받았을 때는 숫자 줄을 보이지 않는다(틀린 숫자를 보이지 않는다)
  const stats = element('p', 'my-guest-stats');
  stats.hidden = true;
  const received = (call, read) => Promise.resolve(call?.()).then(read, () => null);
  Promise.all([received(api?.getCases, caseTotalOf), received(api?.getIllustrationSummary, illustrationTotalOf)]).then(([cases, illustrations]) => {
    if (cases === null || illustrations === null || !stats.isConnected) return;
    [['공개된 사건 ', `${cases}개`], ['일러스트 ', `${illustrations}장`]].forEach(([label, value]) => {
      const line = element('span', 'my-guest-stat', label);
      line.append(element('b', '', value));
      stats.append(line);
    });
    stats.hidden = false;
  });
  middle.append(title, list, stats);
  section.append(cardSlot(navigate), middle, typeCardSlot(navigate));
  return [section, element('p', 'my-note my-guest-note', GUEST_NOTE)];
}
