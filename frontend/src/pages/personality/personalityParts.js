import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { imageOf } from '../../components/stage.js';
import { CHARACTER_NAMES } from '../../utils/personalityAxes.js';
import { reducedMotion } from '../../utils/motion.js';

// S-15 · S-16 · 공유된 결과(/test/types/{shareId})가 함께 쓰는 조각 (FE-42).
// 화면 = 메인 책상의 서류철(성향 테스트)을 들어 올려 펼친 모습: 펠트 위 크라프트 철, 철 안에 종이 한 장.
//   검사지(표지 · 문항, orientation 'landscape') = 철을 왼쪽으로 눕혀 가로로 놓았다: 탭이 왼쪽 모서리, 탭 글씨는 아래에서 위로 읽힌다.
//   결과지(orientation 'portrait') = 철을 세워 세로로 놓았다: 탭이 위쪽 모서리. 제출 직후에는 눕힌 철을 돌려 세운다(personality.css folder-turn).
// 결과지의 색인 탭(결과 · 풀이)은 위쪽 모서리에 붙는다(tabs).
// 재료는 메인 책상 · 5막 파일철(FE-43)과 같다(--color-desk-kraft · -sheet). 흐름 목업 FE-26(판결성향검사지_흐름_목업)을 따른다.
// 공유 화면에는 개인 응답 · 축 점수를 넣지 않는다(FR-8-6).
// 4글자 코드(INTP 등)와 축 글자(E · I 등)는 화면에 보이지 않는다(상표 우려, 10/13). 공유 주소는 해시 값({shareId})이다.

// 서류철 틀. sheetLabel = 종이 머리 칸의 서식 이름(문항지 · 결과지 등). rise = 메인 책상의 서류철을 눌러 들고 왔는지(철이 아래에서 올라온다, FE-50). 반환: { root, tabs, label, body }
export function renderFolder(container, { navigate, sheetLabel, orientation = 'portrait', rise = false }) {
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
  root.append(desk, renderSiteHeader({ navigate }), binder);
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
  return { root, tabs, label, body };
}

// 철 돌리기: 검사지를 제출하고 결과로 넘어갈 때만 눕힌 철을 세운다(결과 주소로 바로 오거나 마이페이지에서 오면 바로 세로).
// 화면 사이 한 번만 전하는 값이라 sessionStorage에 두고, 쓸 수 없으면 연출 없이 간다.
// 눕힌 철의 자리 · 크기(페이지 좌표)를 함께 넘겨, 결과지 철이 같은 자리 · 크기에서 돌며 커지게 한다
const TURN_KEY = 'nlnb-personality-turn';
export function markFolderTurn(folder) {
  const r = folder?.getBoundingClientRect();
  const from = r ? { x: r.left + window.scrollX, y: r.top + window.scrollY, w: r.width, h: r.height } : {};
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
  const r = folder.getBoundingClientRect();
  const x = r.left + window.scrollX;
  const y = r.top + window.scrollY;
  folder.style.setProperty('--turn-x', `${from.x + from.w / 2 - (x + r.width / 2)}px`);
  folder.style.setProperty('--turn-y', `${from.y + from.h / 2 - (y + r.height / 2)}px`);
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
export function resultPages(tabs, body, pages) {
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
  function show(index, { focusPanel = false } = {}) {
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
  return { show };
}

// 다음 장으로 넘기는 줄(결과지 1장 맨 아래)
export function turnPage(label, run) {
  const row = element('div', 'result-turn');
  row.append(button(label, run, 'result-turn-button'));
  return row;
}

// 결과 카드: 캐릭터 그림(종이 클립으로 결과지에 끼운 카드) + 유형명 · 한 줄 성향 · 밈
export function typeCard(type) {
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
