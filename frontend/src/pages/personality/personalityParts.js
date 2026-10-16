import { button, element } from '../../components/siteLayout.js';
import { imageOf } from '../../components/stage.js';
import { CHARACTER_NAMES } from '../../utils/personalityAxes.js';

// S-15 · S-16 · 공유된 결과(/test/types/{shareId})가 함께 쓰는 조각 (FE-42).
// 화면 = 메인 책상의 서류철(성향 테스트)을 펼쳐 놓은 모습: 펠트 위 크라프트 철, 색인 탭 "판결 성향 검사지", 철 안에 종이 한 장.
// 재료는 메인 책상 · 5막 파일철(FE-43)과 같다(--color-desk-kraft · -sheet). 흐름 목업 FE-26(판결성향검사지_흐름_목업)을 따른다.
// 공유 화면에는 개인 응답 · 축 점수를 넣지 않는다(FR-8-6).
// 4글자 코드(INTP 등)와 축 글자(E · I 등)는 화면에 보이지 않는다(상표 우려, 10/13). 공유 주소는 해시 값({shareId})이다.

// 서류철 틀. sheetLabel = 종이 머리 칸 오른쪽(문항지 · 결과지 등). 반환: { root, sheet, body }
export function renderFolder(container, { navigate, sheetLabel }) {
  const root = element('div', 'personality-page');
  const desk = element('div', 'personality-desk');
  desk.setAttribute('aria-hidden', 'true');
  const folder = element('div', 'test-folder');
  const tab = element('p', 'test-folder-tab');
  tab.append(element('span', '', '판결 성향 검사지'));
  const sheet = element('section', 'test-sheet');
  const head = element('header', 'test-sheet-head');
  const title = element('p', 'test-sheet-title', '판결 성향 검사지');
  const label = element('p', 'test-sheet-label', sheetLabel);
  head.append(title, label, button('책상으로', () => navigate('landing'), 'test-back'));
  const body = element('main', 'test-sheet-body');
  sheet.append(head, body);
  folder.append(tab, sheet);
  root.append(desk, folder);
  container.replaceChildren(root);
  return { root, sheet, label, body };
}

// 종이 안 상태 문구(불러오는 중 · 실패). retry가 있으면 다시 시도 버튼, action = { label, run }이면 다른 버튼
export function sheetMessage(body, text, { retry, action, alert = false } = {}) {
  const panel = element('div', 'test-message');
  panel.setAttribute('role', alert ? 'alert' : 'status');
  panel.append(element('p', '', text));
  if (retry) panel.append(button('다시 시도', retry, 'primary-button'));
  if (action) panel.append(button(action.label, action.run, 'primary-button'));
  body.replaceChildren(panel);
}

// 내로 · 남불 안내. look = 컷 이름(guide-neutral 등), line이 있으면 말풍선. 이미지는 장식이라 낭독하지 않고 말풍선만 읽는다
export function guide(who, look, line) {
  const box = element('div', `test-guide is-${who}`);
  const figure = element('img', 'test-guide-cut');
  figure.alt = '';
  figure.decoding = 'async';
  figure.width = 1122; // 원본 컷 크기(표시 크기는 CSS가 정한다)
  figure.height = 1402;
  figure.src = imageOf(who, look);
  figure.addEventListener('error', () => figure.remove(), { once: true });
  box.append(figure);
  if (line) {
    const bubble = element('p', 'test-bubble');
    bubble.append(element('b', '', who === 'naero' ? '내로' : '남불'), line);
    box.append(bubble);
  }
  return box;
}

// 결과 카드: 캐릭터 그림(종이 클립으로 결과지에 끼운 카드) + 유형명 · 한 줄 성향 · 밈 · 캐릭터 한마디
export function typeCard(type, { greeting } = {}) {
  const card = element('section', 'result-card');
  const characterName = CHARACTER_NAMES[type.character] ?? type.character;
  const portrait = element('figure', 'result-portrait');
  const caption = element('figcaption', 'result-meme', type.meme);
  const showNameplate = () => {
    portrait.classList.add('is-nameplate');
    portrait.replaceChildren(element('strong', '', characterName), caption);
  };
  if (type.imageUrl) {
    const image = element('img');
    image.alt = `${characterName} 캐릭터`;
    image.width = 800;
    image.height = 1000;
    image.addEventListener('error', showNameplate, { once: true }); // 파일이 없거나 불러오지 못하면 깨진 이미지 대신 이름표
    image.src = type.imageUrl;
    portrait.append(image, caption);
  } else {
    showNameplate();
  }
  const head = element('div', 'result-head');
  head.append(element('h1', '', type.name), element('p', 'result-oneliner', type.oneLiner));
  if (greeting) {
    const bubble = element('p', 'test-bubble result-greeting');
    bubble.append(element('b', '', characterName), greeting);
    head.append(bubble);
  }
  card.append(portrait, head);
  return card;
}

export function descriptionSections(type) {
  const daily = element('section', 'result-section');
  daily.append(element('h2', '', '일상에서는'), element('p', '', type.dailyDescription));
  const verdict = element('section', 'result-section');
  verdict.append(element('h2', '', '판결을 볼 때는'), element('p', '', type.verdictDescription));
  return [daily, verdict];
}

export function noticeBlock() {
  const notice = element('section', 'result-notice');
  notice.setAttribute('aria-label', '결과 안내');
  [
    '이 결과는 일상 가치관을 기준으로 한 비교이며, 실제 법적 판단과는 다를 수 있어요.',
    '이 판결 성향 유형은 성격 유형 검사와 별개예요. 알고 있는 성격 유형과 다르게 나올 수 있어요.',
  ].forEach((text) => notice.append(element('p', '', text)));
  return notice;
}

export const TOLERANCE_NOTE = '관용 지수는 높을수록 좋은 것이 아니에요. 잘잘못을 따질 때 어떤 가치를 더 중요하게 보는지를 나타낼 뿐이에요.';
