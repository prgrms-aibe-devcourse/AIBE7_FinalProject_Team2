// 로그인 전 마이페이지 (FE-138, BE-140 선행). 로그인하지 않았으면 서버를 부르지 않고 첫 화면의 틀만 보여 준다.
// 레벨 · 직업 · 닉네임 · 수치는 없고, 프로필카드 · 성향카드 자리는 빈 틀(점선 빈 칸과 같은 테두리)이다. 항목 화면(view)으로는 들어가지 않는다.
// 로그인 전에도 사건 체험과 성향 테스트는 할 수 있다 — 처음 로그인할 때 이 브라우저의 체험 기록과 첫인상 엽서는 계정으로 가져올 수 있고,
// 성향 테스트 결과는 가져오지 않는다(BE-140 결정). 서버가 마이페이지를 로그인 전용(401)으로 바꿔도 이 화면은 서버를 부르지 않아 그대로다
import { button, element } from '../../components/siteLayout.js';
import { emptyWindow } from '../../components/personCards.js';

// 카드 한 장 자리의 빈 틀: 위 이름 · 가운데 그림 창 · 아래 한 줄 칸을 흐린 자리로만 둔다. 장식이라 낭독기에는 숨긴다(뜻은 가운데 안내가 말한다)
function cardFrame(withMark) {
  const frame = element('div', 'my-guest-frame');
  frame.setAttribute('aria-hidden', 'true');
  const window_ = emptyWindow();
  if (!withMark) window_.replaceChildren(); // 프로필카드 자리는 물음표 없이 창만
  frame.append(element('span', 'my-guest-line'), window_, element('span', 'my-guest-line is-short'));
  return frame;
}

export const GUEST_NOTE = '로그인하지 않아도 사건 체험과 성향 테스트는 할 수 있어요. 처음 로그인할 때 이 브라우저에서 한 체험 기록과 첫인상 엽서를 계정으로 가져올 수 있어요. 성향 테스트 결과는 가져오지 않으니, 로그인한 뒤 새로 하면 돼요.';

/** 첫 화면: [프로필카드 틀 | 로그인 안내 | 성향카드 틀] + 아래 안내 한 단락 */
export function renderGuestTop(navigate) {
  const section = element('section', 'my-top is-guest');
  const middle = element('div', 'my-top-middle my-guest');
  const title = element('h2', 'my-guest-title', '로그인하면 내\u00a0카드가 생겨요') // "내 카드가"는 한 덩어리로 줄을 넘긴다(나눠 끊기면 "내 / 카드가"로 읽힌다);
  const list = element('ul', 'my-guest-list');
  ['프로필카드를 꾸미고 레벨을 올려요', '체험에서 만난 캐릭터를 모아요', '커뮤니티에 글과 댓글을 남겨요'].forEach((text) => list.append(element('li', '', text)));
  const actions = element('div', 'my-guest-actions');
  actions.append(button('로그인', () => navigate('login'), 'primary-button'), button('회원가입', () => navigate('signup'), 'secondary-button'));
  middle.append(title, list, actions);
  section.append(cardFrame(false), middle, cardFrame(true));
  return [section, element('p', 'my-note my-guest-note', GUEST_NOTE)];
}
