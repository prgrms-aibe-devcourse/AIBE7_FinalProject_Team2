import { button, element } from '../../components/siteLayout.js';
import { imageOf } from '../../components/stage.js';
import { CHARACTER_NAMES } from '../../utils/personalityAxes.js';
import { reducedMotion } from '../../utils/motion.js';
import { holdDeskScroll } from '../../utils/deskScroll.js';
import { folderBookmark, paperFold } from '../../components/folderParts.js';

// S-15 · S-16 · 공유된 결과(/test/types/{shareId})가 함께 쓰는 조각 (FE-42).
// 화면 = 메인 책상의 서류철(성향 테스트)을 들어 올려 펼친 모습: 펠트 위 크라프트 철, 철 안에 종이 한 장.
//   검사지(표지 · 문항, orientation 'landscape') = 철을 왼쪽으로 눕혀 가로로 놓았다: 탭이 왼쪽 모서리, 탭 글씨는 아래에서 위로 읽힌다.
//   결과지(orientation 'portrait') = 철을 세워 세로로 놓았다: 탭이 위쪽 모서리. 제출 직후에는 눕힌 철을 돌려 세운다(personality.css folder-turn).
// 결과지의 색인 탭(결과 · 풀이)은 위쪽 모서리에 붙는다(tabs).
// 재료는 메인 책상 · 3막 파일철(FE-43)과 같다(--color-desk-kraft · -sheet). 흐름 목업 FE-26(판결성향검사지_흐름_목업)을 따른다.
// 공유 화면에는 개인 응답 · 축 점수를 넣지 않는다(FR-8-6).
// 4글자 코드(INTP 등)와 축 글자(E · I 등)는 화면에 보이지 않는다(상표 우려, 10/13). 공유 주소는 해시 값({shareId})이다.

// 서류철 틀. sheetLabel = 종이 머리 칸의 서식 이름(문항지 · 결과지 등). rise = 메인 책상의 서류철을 눌러 들고 왔는지(철이 아래에서 올라온다, FE-50) · deskY = 그때 책상의 스크롤 자리. 반환: { root, tabs, label, body }
export function renderFolder(container, { navigate, sheetLabel, orientation = 'portrait', rise = false, deskY = 0 }) {
  const root = element('div', `personality-page is-${orientation}`);
  const desk = element('div', 'personality-desk');
  desk.setAttribute('aria-hidden', 'true');
  const binder = element('div', 'test-binder');
  const tabs = element('div', 'test-folder-tabs');
  const folder = element('div', 'test-folder');
  const sheet = element('section', 'test-sheet');
  const label = element('p', 'visually-hidden', sheetLabel); // 머리 칸은 없애고, 지금 어느 장인지는 낭독기용으로만 남긴다
  const body = element('main', 'test-sheet-body');
  sheet.append(label, body);
  folder.append(sheet);
  binder.append(tabs, folder);
  root.append(desk, binder); // 공통 헤더는 라우터가 붙인다(FE-56)
  // 책상(메인)에서 검사지를 들고 온 경우: 책상 화면을 뒤에 남겨 두었다가 철이 올라오는 동안 흐려지며 걷는다. 헤더 등으로 오면(rise 없음) 연출 없이 바로 보인다
  const ghost = rise && orientation === 'landscape' && !reducedMotion() && container.querySelector('.landing-page')
    ? element('div', 'personality-ghost') : null;
  if (ghost) {
    ghost.setAttribute('aria-hidden', 'true');
    ghost.inert = true;
    ghost.append(...container.childNodes);
    root.insertBefore(ghost, binder);
    root.classList.add('is-rising');
    const drop = () => ghost.remove();
    ghost.addEventListener('animationend', drop);
    window.setTimeout(drop, 2000); // animationend가 오지 않아도 걷는다
  }
  container.replaceChildren(root);
  holdDeskScroll(ghost, deskY);
  return { root, tabs, label, body };
}

// 철 돌리기는 데스크톱(721px 이상)에서만 한다. 모바일은 철이 화면 폭을 꽉 채워 도는 모양이 어색하고, 연출 끄기 · 동작 줄이기에서도 건너뛴다
export function canTurnFolder() {
  return !reducedMotion() && window.matchMedia('(min-width: 721px)').matches;
}

// 철 돌리기: 검사지를 제출하고 결과로 넘어갈 때만 눕힌 철을 세운다(결과 주소로 바로 오거나 마이페이지에서 오면 바로 세로).
// 화면 사이 한 번만 전하는 값이라 sessionStorage에 두고, 쓸 수 없으면 연출 없이 간다.
// 떠나는 철의 자리 · 크기(페이지 좌표)와 그때의 스크롤 위치를 함께 넘겨, 새 철이 화면에서 보이던 같은 자리 · 크기에서 돌게 한다
const TURN_KEY = 'nlnb-personality-turn';
export function markFolderTurn(folder) {
  const r = folder?.getBoundingClientRect();
  const from = r ? { x: r.left + window.scrollX, y: r.top + window.scrollY, w: r.width, h: r.height, scrollX: window.scrollX, scrollY: window.scrollY } : {};
  try { sessionStorage.setItem(TURN_KEY, JSON.stringify(from)); } catch { /* 연출만 빠진다 */ }
}
// 돌릴 일이 없으면 null, 있으면 눕힌 철의 자리(없으면 빈 객체)
export function takeFolderTurn() {
  try {
    const raw = sessionStorage.getItem(TURN_KEY);
    sessionStorage.removeItem(TURN_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

// 세운 철(folder)이 눕힌 철 자리(from)에서 시작하도록 처음 모양을 CSS 변수로 준다(personality.css folder-turn).
// 철을 -90도 돌리면 철의 높이가 가로, 폭이 세로가 되므로 가로 · 세로 배율을 서로 바꿔 맞춘다
export function setFolderTurnFrom(folder, from) {
  if (!from?.w) return; // 자리를 모르면 CSS 기본값(예전 연출)으로 돈다
  // 새 화면은 맨 위(스크롤 0)에서 시작하므로, 떠나던 철이 화면에서 보이던 자리(페이지 좌표 − 그때의 스크롤)에서 출발해야 눈에 같은 자리로 이어진다
  const r = folder.getBoundingClientRect();
  const fromLeft = from.x - (from.scrollX ?? 0);
  const fromTop = from.y - (from.scrollY ?? 0);
  folder.style.setProperty('--turn-x', `${fromLeft + from.w / 2 - (r.left + r.width / 2)}px`);
  folder.style.setProperty('--turn-y', `${fromTop + from.h / 2 - (r.top + r.height / 2)}px`);
  folder.style.setProperty('--turn-sx', String(from.h / r.width));
  folder.style.setProperty('--turn-sy', String(from.w / r.height));
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

// 내로 · 남불 안내 컷(look = guide-neutral 등), line이 있으면 말풍선. 그림은 장식이라 낭독하지 않고 말풍선만 읽는다
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
    bubble.append(element('b', '', CHARACTER_NAMES[who.toUpperCase()]), line);
    box.append(bubble);
  }
  return box;
}

// 결과지 두 장(탭 · 패널). pages = [{ label, nodes }]. 철 위 탭을 누르거나 ← → 키로 넘긴다(WAI-ARIA tabs)
// onSwitch: 처음 그린 뒤 장이 바뀔 때마다(탭 · 키보드 · 귀) 부른다. 결과 공개 연출을 끝내는 데 쓴다(FE-95 리뷰 반영)
export function resultPages(tabs, body, pages, { onSwitch } = {}) {
  const list = element('div', 'folder-tablist');
  list.setAttribute('role', 'tablist');
  list.setAttribute('aria-label', '결과지');
  const panels = pages.map(({ nodes }, i) => {
    const panel = element('div', 'result-page');
    panel.id = `result-page-${i}`;
    panel.setAttribute('role', 'tabpanel');
    panel.setAttribute('aria-labelledby', `result-tab-${i}`);
    panel.tabIndex = -1;
    panel.append(...nodes);
    return panel;
  });
  const buttons = pages.map(({ label }, i) => {
    const tab = element('button', 'folder-tab', label);
    tab.type = 'button';
    tab.id = `result-tab-${i}`;
    tab.setAttribute('role', 'tab');
    tab.setAttribute('aria-controls', panels[i].id);
    tab.addEventListener('click', () => show(i));
    tab.addEventListener('keydown', (event) => {
      const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key];
      if (!step) return;
      event.preventDefault();
      const next = (i + step + pages.length) % pages.length;
      show(next);
      buttons[next].focus();
    });
    return tab;
  });
  // 진행 책갈피(판결 체험 파일철과 같다): 펼쳐 본 가장 뒤 장(reached)의 탭 바로 뒤에 끼운 리본 + 마이페이지에서 고른 얼굴(장식).
  // 잠금이 아니라 진행 표시라 늘 보이고, 앞 장으로 돌아와도 자리를 지킨다. 리본 색 · 얼굴은 마이페이지 책갈피 설정(FE-67 · FE-68)
  const bookmark = folderBookmark();
  let reached = 0;
  function placeBookmark() {
    const before = buttons[reached + 1] ?? null; // 마지막 장이면 탭 줄 끝
    if (bookmark.parentNode === list && bookmark.nextSibling === before) return;
    const moved = bookmark.parentNode === list;
    list.insertBefore(bookmark, before);
    if (moved && !reducedMotion()) bookmark.animate([{ translate: '0 -40%', opacity: 0 }, { translate: '0 0', opacity: 1 }], { duration: 240, easing: 'cubic-bezier(.2, .8, .2, 1)' });
  }
  let ready = false;
  let current = -1;
  function show(index, { focusPanel = false } = {}) {
    if (ready && index !== current) onSwitch?.(index); // 같은 탭을 다시 눌러서는 장이 바뀐 게 아니다
    current = index;
    reached = Math.max(reached, index);
    placeBookmark();
    buttons.forEach((tab, i) => {
      tab.setAttribute('aria-selected', String(i === index));
      tab.tabIndex = i === index ? 0 : -1;
      panels[i].hidden = i !== index;
    });
    if (focusPanel) panels[index].focus({ preventScroll: true });
  }
  list.append(...buttons);
  tabs.replaceChildren(list);
  body.replaceChildren(...panels);
  show(0);
  ready = true;
  return { show };
}

// 장을 넘기는 종이 귀(결과지 아래 모서리, 접힌 귀퉁이 + 글자). 다음 장 = 오른쪽 아래, 이전 장(prev) = 왼쪽 아래. 줄 자체는 내용이 귀에 가려지지 않게 자리만 잡는다
export function turnPage(label, run, { prev = false } = {}) {
  const row = element('div', `result-turn${prev ? ' is-prev' : ''}`);
  const turn = button('', run, 'result-turn-button');
  turn.append(element('span', 'turn-label', label), paperFold({ flipped: prev }));
  row.append(turn);
  return row;
}

// 같은 유형의 다른 캐릭터 그림. 이미지 번호는 내로 01 ~ 16 · 남불 17 ~ 32라 ±16이 짝이다. 번호를 못 읽으면 이미지 없이 이름표로 간다
const OTHER_CHARACTER = { NAERO: 'NAMBUL', NAMBUL: 'NAERO' };
export function withOtherCharacter(type) {
  const other = OTHER_CHARACTER[type.character];
  const match = type.imageUrl?.match(/^(.*\/)(\d{2})(\.webp)$/);
  if (!other || !match) return { ...type, character: other ?? type.character, imageUrl: null };
  const number = Number(match[2]) + (type.character === 'NAERO' ? 16 : -16);
  return { ...type, character: other, imageUrl: `${match[1]}${String(number).padStart(2, '0')}${match[3]}` };
}

// 종이 클립으로 끼운 캐릭터 카드의 안쪽: 그림(없거나 못 불러오면 이름표) + 밈
function fillPortrait(portrait, caption, characterName, imageUrl) {
  const showNameplate = () => {
    portrait.classList.add('is-nameplate');
    portrait.replaceChildren(element('strong', '', characterName), caption);
  };
  portrait.classList.remove('is-nameplate');
  if (!imageUrl) {
    showNameplate();
    return;
  }
  const image = element('img');
  image.alt = `${characterName} 캐릭터`;
  image.width = 800;
  image.height = 1000;
  image.addEventListener('error', showNameplate, { once: true }); // 파일이 없거나 불러오지 못하면 깨진 이미지 대신 이름표
  image.src = imageUrl;
  portrait.replaceChildren(image, caption);
}

// 유형의 두 캐릭터 그림 { NAERO, NAMBUL }
function imagesOf(type) {
  const other = withOtherCharacter(type).imageUrl;
  return type.character === 'NAERO' ? { NAERO: type.imageUrl, NAMBUL: other } : { NAERO: other, NAMBUL: type.imageUrl };
}

// 공유 화면의 그림 두 장: 같은 크기의 카드 두 장을 겹쳐 놓는다. 앞은 내로로 시작하고, 뒤(남불)는 비스듬히 삐죽 나와 있다.
// 앞 카드든 뒤 카드든 누르면 두 카드의 앞뒤가 바뀐다(CSS가 자리를 옮긴다). 두 카드 모두 그림과 밈을 미리 받아 둔다
function portraitPair(type) {
  const images = imagesOf(type);
  const set = element('div', 'result-portrait-set');
  const cards = {};
  Object.keys(images).forEach((code) => {
    const card = element('figure', 'result-portrait');
    fillPortrait(card, element('figcaption', 'result-meme', type.meme), CHARACTER_NAMES[code], images[code]);
    cards[code] = card;
  });
  const swap = element('button', 'result-swap'); // 키보드 · 낭독기용 투명 버튼(뒤 카드를 덮는다). 앞뒤가 바뀌면 새 뒤 카드로 옮겨 간다. 누름은 카드의 click으로 올라가 한 번만 처리된다
  swap.type = 'button';
  let front = 'NAERO';
  const place = () => {
    const back = OTHER_CHARACTER[front];
    cards[front].classList.remove('is-back');
    cards[back].classList.add('is-back');
    cards[back].append(swap);
    swap.setAttribute('aria-label', `${CHARACTER_NAMES[back]} 캐릭터를 앞으로 보기`);
  };
  const flip = () => {
    front = OTHER_CHARACTER[front];
    place();
    swap.focus({ preventScroll: true });
  };
  Object.values(cards).forEach((card) => card.addEventListener('click', flip));
  place();
  set.append(cards.NAERO, cards.NAMBUL);
  return set;
}

// 결과 카드: 캐릭터 그림(종이 클립으로 결과지에 끼운 카드) + 유형명 · 한 줄 성향 · 밈
// other = 풀이 장: 같은 유형의 다른 캐릭터 그림 / pair = 공유 화면: 내로 고정 + 남불 작은 카드(누르면 자리 바꿈)
export function typeCard(source, { other = false, pair = false } = {}) {
  const type = other ? withOtherCharacter(source) : source;
  const card = element('section', 'result-card');
  const head = element('div', 'result-head');
  head.append(element('h1', '', type.name), element('p', 'result-oneliner', type.oneLiner));
  if (pair) {
    card.append(portraitPair(type), head);
    return card;
  }
  const characterName = CHARACTER_NAMES[type.character] ?? type.character;
  const portrait = element('figure', 'result-portrait');
  fillPortrait(portrait, element('figcaption', 'result-meme', type.meme), characterName, type.imageUrl);
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
