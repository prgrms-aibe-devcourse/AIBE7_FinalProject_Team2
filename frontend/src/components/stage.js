// 판결 체험 무대 (REQ-089, FE-27 · 28). 흐름 목업(Claude outputs/mockups/메인책상_신문_사건정보_판결체험_흐름_목업)의
// 게임 화면을 바탕으로: 장면 · 캐릭터 · 이름표 칩 · 장면 안 대사창 · 보드(카드) · 막 칩 · 재판 순서.
// 1막은 신문 지면 자체가 장면이고(메인 책상의 신문과 같은 짜임), 내로 · 남불이 지면 위를 옮겨 다니며 설명한다.
// 앱 쪽에서 바꾼 것은 연결뿐이다 — 막은 화면(S-03 · S-04 …)이 고르고, 입력은 화면의 폼, 서버 "읽었습니다"는 onConfirm.

import { scrollArea } from './scrollArea.js';
import { sheetCamera } from './sheetCamera.js';
import { reducedMotion } from '../utils/motion.js';
import { commonPhotoAt, useCommonPhotoOnError } from '../utils/newspaper.js';

// 화면에 보이는 막(6막)과 대본 키(FE-59). 1막 첫인상은 기사(1a) · 엽서(1b) 두 부분으로 둔다
// (부분 사이에서 건너뛰기가 멈추고 모습 · 이름표 · 지면 초점이 새로 시작된다)
export const ACT_GROUPS = [
  { name: '첫인상', ids: ['1a', '1b'] },
  { name: '재판', ids: ['2'] },
  { name: '내 판결', ids: ['3'] },
  { name: 'AI 판결', ids: ['4'] },
  { name: '실제 판결', ids: ['5'] },
  { name: '세 판결 비교', ids: ['6'] },
];
// 로 합의부는 판결 화면에서 이름 대신 "AI 판사"로 부른다(직책 재판장 · 배석판사는 이름표 옆에 단다)
const NAMES = { naero: '내로', nambul: '남불', 'law-scale': 'AI 판사', 'law-gavel': 'AI 판사', 'law-book': 'AI 판사' };
const ENTER_MS = 1000; // paperStage.css의 들어오는 연출(.55초 + 남불 .12초 지연, 대사창 .35초 지연 + .3초)이 모두 끝나는 시간
const CAMEO_LABEL = '볼따구'; // 해설을 맡는 작은 내로 · 남불(배역이 아니라 데포르메 모습)
const CAMEO_LOOK = 'cheek:idle-thinking';
const DEFORM_LABEL = { ranma: '란마' }; // 데포르메 모습 이름(look "deform:ranma" → 이름표 "남불 란마") // 볼따구 컷은 지금 idle-thinking · idle-sorry 두 표정만 있다
const SLIM_CHEEK_LABEL = '슬림볼따구'; // look "cheek-slim:…"의 모습 이름(이름표 "남불 슬림볼따구")
const ROLE_LABEL = { actor: '사건 속 인물', prosecutor: '검사', defense: '변호인', judge: '판사' };
const ROLE_COLOR = { 'AI 판사': 'law', '법정 진행': 'narrator', 볼따구: 'narrator', 판사: 'law', 'A씨 역': 'actor', 'B씨 역': 'actor', '사건 속 인물': 'actor', 검사: 'prosecutor', 변호인: 'defense', 재판장: 'law', 배석판사: 'law' };
// 로 합의부(4막 AI 판사): 로울(저울) · 로봉(법봉) · 로북(법전). LAWS는 판단 요소를 맡는 순서(저울 → 법봉 → 법전),
// 서는 자리는 로울이 가운데, 로봉 · 로북이 좌우(LAW_SPOTS). 직책은 로울 재판장, 로봉 · 로북 배석판사.
// 셋은 대등하지만 로울이 합의를 이끄는 일종의 리더(재판장) 역할을 한다(10/15 결정). 캐릭터 문서 4-3에도 반영됨(COMMON-37)
export const LAWS = ['law-scale', 'law-gavel', 'law-book'];
const LAW_SPOTS = ['law-gavel', 'law-scale', 'law-book'];
export const LAW_TITLES = { 'law-scale': '재판장', 'law-gavel': '배석판사', 'law-book': '배석판사' };
// 로 이미지: 대기 · 생각(합의) · 발표 세 자세와 얼굴 아이콘(마스코트 버전). 원본 레포 컷에서 투명 여백을 잘라 키(발 위치)를 맞춰 public에 둔다
const LAW_POSES = ['idle', 'think', 'deliver'];
const LAW_FADE_MS = 350; // 판사 페이드 시간 하나로: 첫 등장 · 자세 바뀜 · 사라짐(stage.css --law-fade와 같게)
const IMAGES = '/images/characters/'; // 원본 PNG는 이미지 레포(NaeLawNamBul_Images), 웹용 webp는 public에 둔다
const lawImage = (who, pose) => `${IMAGES}mascot/${who}_${pose}.webp`;
export const imageOf = (who, look) => (look.startsWith('role:') ? `${IMAGES}roles/${who}_${look.slice(5)}.webp`
  : look.startsWith('cheek-slim:') ? `${IMAGES}cheek-slim/${who}_${look.slice(11)}.webp` // 슬림볼따구
    : look.startsWith('cheek:') ? `${IMAGES}cheek/${who}_${look.slice(6)}.webp` // 볼따구(기다림)
    : look.startsWith('deform:') ? `${IMAGES}deform/${who}_${look.slice(7)}.webp` // 데포르메(란마 등, 시트에서 잘라 public에 둔다)
    : `${IMAGES}scene/${who}_${look}.webp`);
// 법정 자리는 역할 의상(look "role:…")이 정한다: 검사 · 판사 = 왼쪽(검사편, 가운데는 카드 · 판이 많이 떠서 판사도 왼쪽이 기본) · 변호인 = 오른쪽 변호인석.
// 판사를 가운데에 세우려면 대본 spots로 준다. 그 밖의 의상은 캐릭터 기본 자리(내로 왼쪽 · 남불 오른쪽)
const SEAT = { prosecutor: 'left', judge: 'left', defense: 'right' };
const seatOf = (look) => (look.startsWith('role:') ? SEAT[look.slice(5)] : undefined);
let chipsOff = false; // 개발 전용: 역할 이름표 끄기(이름표가 필요한지 판단용). 화면을 다시 그려도 유지한다
const FACES = ['neutral', 'happy', 'surprised', 'thinking', 'sorry', 'cheer'];
const iconOf = (who, look) => {
  if (who.startsWith('law-')) return `${IMAGES}icon/cropped/${who}_icon.webp`;
  const face = look.startsWith('role:') ? 'neutral' : look.split('-')[1];
  return `${IMAGES}icon/generated/${who}_face-${FACES.includes(face) ? face : 'neutral'}.webp`;
};
// 이름표는 역할 의상을 입었을 때만 단다(평상복이면 없음)
const chipText = (look, role) => {
  if (!look.startsWith('role:')) return '';
  const label = ROLE_LABEL[look.slice(5)];
  return role && (role === label || role.endsWith('역')) ? role : label;
};

// 배역이 의상(look "role:…")과 맞는지: 이름이 의상 이름과 같거나, 사건 속 인물 의상에서 "A씨 역" · "B씨 역"처럼 역으로 끝나는 배역
const fitsLook = (look, role) => Boolean(look?.startsWith('role:')) && (role === ROLE_LABEL[look.slice(5)] || (look === 'role:actor' && role.endsWith('역')));

// 대본의 막들을 한 줄로 편다. 각 장면은 앞 장면의 모습(look) · 자리(spots) · 이름표(tags) · 재판 순서(proc) · 지면 기사(focus)를 이어받는다.
export function flatten(acts, order) {
  const steps = [];
  order.forEach((actId) => {
    const act = acts[actId];
    let look = { ...act.look };
    let spots = { ...act.spots };
    let tags = { naero: '', nambul: '' };
    let proc = null;
    let focus = 'all';
    // 이름표는 막 시작 의상에 맞는 첫 배역으로 시작한다(대사 전에 "사건 속 인물"이 떴다가 "A씨 역"으로 바뀌지 않게).
    // 시작 의상과 다른 배역의 대사가 먼저 나오면(예: 변호인 의상으로 서 있다가 첫 대사가 "A씨 역") 그 배역은 건너뛴다
    const firstRole = (who) => act.steps.find((step) => step.say === who && step.role && fitsLook(act.look?.[who], step.role))?.role ?? '';
    const said = { naero: firstRole('naero'), nambul: firstRole('nambul') };
    act.steps.forEach((step) => {
      look = { ...look, ...step.look };
      spots = { ...spots, ...step.spots };
      tags = { ...tags, ...step.tags };
      proc = step.proc ?? proc;
      focus = step.focus ?? focus;
      if (said[step.say] !== undefined && step.role) said[step.say] = step.role;
      const chips = { naero: tags.naero || chipText(look.naero, said.naero), nambul: tags.nambul || chipText(look.nambul, said.nambul) };
      steps.push({ ...step, actId, scene: act.scene ?? 'news', procedures: act.procedures, cast: act.cast, proc, look, spots, chips, focus });
    });
  });
  return steps;
}

// 민감 사건 안내 (FE-129): 2막 시작 장면에 지문(system 캡션) 한 줄을 단다. 캐릭터 대사가 아니라 화면 아래 지문이고,
// 그 장면에 이미 지문 · 암전 문구가 있으면 건드리지 않는다. 안내할 내용이 없으면(notice 빈 값) 그대로 돌려준다
export function attachNotice(steps, notice, actId = '2') {
  if (!notice) return steps;
  const at = steps.findIndex((step) => step.actId === actId);
  if (at < 0 || steps[at].system || steps[at].blackout) return steps;
  return steps.map((step, i) => (i === at ? { ...step, system: notice, notice: true } : step));
}

// 대사 건너뛰기: 같은 막에서 다음 보드 장면, 없으면 막의 마지막 장면으로 간다.
// 서버 확인(confirm)이 걸린 장면은 건너뛰지 않는다 — 건너뛰면 "읽었습니다" 순서가 어긋난다.
export function skipTarget(steps, index) {
  const { actId } = steps[index];
  if (steps[index].confirm) return index;
  for (let i = index + 1; i < steps.length; i += 1) {
    if (steps[i].actId !== actId) return i - 1;
    if (steps[i].card || steps[i].confirm) return i;
  }
  return steps.length - 1;
}

// 막 버튼으로 바로 가기(테스트 기간): 화면이 넘어갈 때 가려던 막을 sessionStorage에 한 번 남긴다
export const ACT_JUMP_KEY = 'nlnb-act-jump';
function takeJump() {
  try {
    const act = sessionStorage.getItem(ACT_JUMP_KEY);
    sessionStorage.removeItem(ACT_JUMP_KEY);
    return act;
  } catch { return null; }
}


// 신문 지면 무대(layout 'paper')의 카메라: 지면이 곧 화면이라 옮기지 않고, 고른 기사만 또렷하게 두고 그 기사가 보이게 화면을 굴린다.
// 아래쪽에 고정된 대사창(dock)이 가리는 부분을 빼고 그 위 공간 가운데에 맞춘다(기사가 그 공간보다 길면 기사 위쪽에 맞춘다)
function paperCamera(sheet, dockTop, byKeyboard = () => false) {
  let at = null;
  function show(target) {
    requestAnimationFrame(() => {
      const box = target.getBoundingClientRect();
      const head = Math.max(0, document.querySelector('#route-header .site-header')?.getBoundingClientRect().bottom ?? 0); // 위에 붙은 공통 헤더(FE-56)가 가리는 만큼 뺀다
      const room = Math.max(0, dockTop() - head - 24); // 헤더가 두 줄로 커져도 음수가 되지 않게
      const top = box.height < room ? box.top - head - (room - box.height) / 2 : box.top - head - 24;
      const y = window.scrollY + top;
      window.scrollTo({ top: y < 200 ? 0 : y, // 지면 맨 위 근처 기사는 머리글(제호)까지 보이게 맨 위로
        behavior: reducedMotion() || byKeyboard() ? 'auto' : 'smooth' }); // 키보드로 넘길 때는 즉시(연달아 눌러도 화면이 따라오느라 느려지지 않게)
    });
  }
  function go(name) {
    sheet.querySelectorAll('.is-on').forEach((node) => node.classList.remove('is-on'));
    const target = sheet.querySelector(`[data-focus="${name}"]`);
    sheet.classList.toggle('spot', Boolean(target));
    if (target) target.classList.add('is-on');
    if (target && at !== name) show(target);
    at = name;
  }
  return { go, show };
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
// commonSeed를 주면(사건 사진) 불러오지 못할 때 공용 컷으로 바꾸고, 안 주면(아이콘 · 캐릭터) 그냥 뺀다
function picture(src, className, alt = '', commonSeed, role, category) {
  const image = element('img', className);
  if (commonSeed === undefined) image.addEventListener('error', () => image.remove(), { once: true });
  else useCommonPhotoOnError(image, commonSeed, role, category);
  image.src = src;
  image.alt = alt;
  return image;
}

// 1막 사건 기사 지면(카메라로 읽는다): 정보 줄 · 제호 · 날짜 띠 / 큰 제목 · 부제 / 사진 / 본문 두 단.
// 카메라가 맞추는 기사(data-focus): head(제목 · 부제) · photo(사진, 1 → 2막 "사진 속으로"도 여기서) · body(본문)
function caseSheet(paper) {
  const sheet = element('article', 'sheet sheet-case');
  sheet.setAttribute('aria-label', '신문 기사');
  const mast = element('header', 'sheet-mast');
  const info = element('div', 'sheet-info');
  info.append(element('span', '', '판결 체험 신문'), element('span', '', paper.desk));
  const brand = element('div', 'sheet-brand');
  brand.append('내', element('span', 'law', 'Law'), '남불');
  const date = element('div', 'sheet-date');
  date.append(element('span', '', new Intl.DateTimeFormat('ko-KR', { dateStyle: 'full' }).format(new Date())), element('span', '', '참여 무료'));
  mast.append(info, brand, date);
  const head = element('section', 'sheet-story sheet-head-block');
  head.dataset.focus = 'head';
  head.append(element('h2', 'sheet-headline', paper.headline), element('p', 'sheet-deck', paper.deck));
  const figure = element('figure', 'sheet-figure sheet-story');
  figure.dataset.focus = 'photo';
  figure.append(picture(paper.photo, 'sheet-case-photo', '', 0), element('figcaption', '', paper.caption));
  const body = element('section', 'sheet-story sheet-body');
  body.dataset.focus = 'body';
  paper.article.forEach((line) => body.append(element('p', 'sheet-text', line)));
  sheet.append(mast, head, figure, body);
  return sheet;
}

// 2막 법정(재판장 시점): 사용자가 판사석에 앉아 법정을 내려다본다. 판사석은 그리지 않는다 — 앉은 사람이 사용자다.
// 벽 · 방청석 난간 · 피고인석 · 검사석 · 변호인석을 캐릭터 뒤에 둔다(6막 신문 사진도 이 법정을 쓴다)
// FE-32: 신문의 법정 스케치(재판 취재 삽화)처럼 잉크 선으로 그린 벡터 장면. 자리(%)는 예전 상자 배경과 같아 캐릭터 위치는 그대로다.
// 좌표계 100 × 56.25(16:9), 선 굵기는 화면 크기와 상관없이 일정(non-scaling-stroke). 색은 stage.css · courtSketch.css가 칠한다
const COURT_SVG = `
<svg class="court-sketch" viewBox="0 0 100 56.25" preserveAspectRatio="none" aria-hidden="true" focusable="false">
  <rect class="cs-wall" x="0" y="0" width="100" height="23.6"/>
  <rect class="cs-bench-back" x="35" y="0" width="30" height="23.6"/>
  <g class="cs-ink cs-thin">
    <path d="M8 0V21.4M16 0V21.4M24 0V21.4M76 0V21.4M84 0V21.4M92 0V21.4"/>
    <path d="M35 0V23.6M65 0V23.6M38 3H62V18H38Z"/>
  </g>
  <rect class="cs-floor" x="0" y="25.9" width="100" height="30.35"/>
  <g class="cs-ink cs-faint">
    <path d="M50 25.9L-30 56.25M50 25.9L10 56.25M50 25.9L37 56.25M50 25.9L63 56.25M50 25.9L90 56.25M50 25.9L130 56.25"/>
    <path d="M0 33H100M0 42H100"/>
  </g>
  <rect class="cs-wood cs-rail" x="0" y="21.4" width="100" height="4.5"/>
  <g class="cs-ink"><path d="M0 21.4H100M0 25.9H100M0 22.6H100"/></g>
  <g class="cs-ink cs-thin"><path d="M4 22.6V25.9M12 22.6V25.9M20 22.6V25.9M28 22.6V25.9M72 22.6V25.9M80 22.6V25.9M88 22.6V25.9M96 22.6V25.9"/></g>
  <path class="cs-wood cs-ink" d="M45 30.9V18.9Q45 18.6 45.3 18.6H54.7Q55 18.6 55 18.9V30.9Z"/>
  <g class="cs-ink cs-thin"><path d="M45 20.1H55M47 22.6H53"/></g>
  <path class="cs-wood cs-ink" d="M0 28.1L26 29.9V36.6H0Z"/>
  <path class="cs-wood cs-ink" d="M100 28.1L74 29.9V36.6H100Z"/>
  <g class="cs-ink cs-thin"><path d="M0 30.3L26 31.6M100 30.3L74 31.6"/></g>
</svg>`;
export function courtSet() {
  const court = element('div', 'court');
  court.setAttribute('aria-hidden', 'true');
  court.innerHTML = COURT_SVG;
  court.querySelectorAll('path, rect').forEach((node) => node.setAttribute('vector-effect', 'non-scaling-stroke'));
  return court;
}

// 대본 카드(목업의 CARDS): { k, h3, big, p: [문장 | { b: 굵게, t: 보통 }], ul: [문장], note } — 적은 순서대로 그린다
function scriptCard(spec) {
  const card = element('div', 'card-body');
  Object.entries(spec).forEach(([key, value]) => {
    if (key === 'k') card.append(element('p', 'k', value));
    if (key === 'h3') card.append(element('h3', '', value));
    if (key === 'big') card.append(element('div', 'big', value));
    if (key === 'note') card.append(element('p', 'note', value));
    if (key === 'ul') {
      const list = element('ul');
      value.forEach((line) => list.append(element('li', '', line)));
      card.append(list);
    }
    if (key === 'p') {
      value.forEach((line) => {
        const paragraph = element('p');
        if (typeof line === 'string') paragraph.textContent = line;
        else Object.entries(line).forEach(([kind, part], i) => { // { b, t } · { t, b } 적은 순서대로
          if (i) paragraph.append(' ');
          paragraph.append(kind === 'b' ? element('b', '', part) : part);
        });
        card.append(paragraph);
      });
    }
  });
  return card;
}

// 증거조사: 검사 측 · 변호인 측 두 칸. 칸 위에 사진을 고정하고, 사실관계는 자기 칸에 하나씩 등장해 쌓인다(지금 것 강조)
function evidenceBoard(script, at) {
  const board = element('div', 'evd');
  ['검사', '변호인'].forEach((side) => {
    const column = element('section', `evd-side ${side === '검사' ? 'p' : 'd'}`);
    // 변호인 측 = 발단사진, 검사 측 = 결론사진(기사 지면 작은 사진과 같은 그림). 사진이 없으면 그 자리의 공용 컷
    const role = side === '변호인' ? 'origin' : 'conclusion';
    const seed = side === '검사' ? 1 : 2;
    column.append(picture(script.evidencePhotos?.[side] ?? commonPhotoAt(seed, role, script.commonCategory), 'evd-photo', '', seed, role, script.commonCategory));
    column.append(element('h4', '', `${side} 측 증거`));
    const list = element('ol', 'evd-list');
    script.evidence.forEach((item, i) => {
      if (item.side !== side || i > at) return;
      const row = element('li', i === at ? 'now' : '', item.text);
      if (i === at) row.setAttribute('aria-current', 'true');
      list.append(row);
    });
    column.append(scrollArea(list, `${side} 측 증거`));
    board.append(column);
  });
  board.append(element('p', 'evd-count', `증거조사 ${at + 1} / ${script.evidence.length}`));
  return board;
}

// script: 사건 대본, order: 이 화면이 보여 줄 막, cards: 화면이 주는 카드 { 이름: 요소 },
// inputCards: 입력 카드 이름(보드를 넓게 띄우고 화면의 제출로만 넘어간다), start(step): 재진입 장면,
// onConfirm(n): confirm 장면을 떠날 때 화면이 서버에 "읽었습니다"를 보낸다(false면 넘어가지 않는다),
// onRestart: 있으면 "처음부터 다시" 버튼을 단다(목 데이터 전용), onJump(막 번호): 있으면 막 칩을 눌러 그 막으로 바로 간다
// (테스트 기간 · 목 데이터 전용, 이 화면에 없는 막은 화면이 넘겨 준다). 반환한 요소의 enterPhoto()는 신문 사진 속으로 들어가는 연출.
// choices: 대사창 안 선택지 { 이름: [{ label, action(note), primary }] } — 대본 step.choices로 고른다(타이핑이 끝나면 뜬다, note는 결과 안내 줄).
// sheet: 대본 scene "sheet"에서 쓰는 큰 지면(6막은 화면이 넘겨 주고, 없으면 대본 paper로 1막 사건 기사를 만든다).
// notice: 민감 사건 안내 한 줄(FE-129) — 2막 시작 장면에 지문으로 단다(attachNotice).
// 장면의 focus 기사로 카메라가 옮겨 가고, 첫 장면의 from 요소가 무대를 채운 데서 빠져나온다.
// fromRect: 첫 장면의 from 요소를 이 화면 사각형(앞 막 무대 자리)에서 시작한다(6막). cameraFill · cameraMinScale · cameraMaxUnit은 sheetCamera 옵션.
// layout 'paper': 무대 상자 없이 지면(sheet)이 화면 전체가 된다(S-20 · 1막, S-02 신문과 같은 짜임). 막 칩 · 보드는 지면의
// 막 칩은 숨기고(지면 탭 줄은 신문 목차), 카드(1막 엽서의 사전 판단)는 지면 위에 첫인상 엽서 팝업으로 뜬다 — 남불이 가져와 건네는 연출.
// 캐릭터 · 대사창 · 조작 줄은 화면 아래에 고정된다. 카메라는 화면을 굴린다(paperCamera)
// parked: 지면만 보여 주고 캐릭터 · 대사창 · 조작 줄은 숨긴 채 기다린다(S-20 사건 정보 — 체험 시작 전). 화면이 wrap.release()로 풀면
// 내로 · 남불이 들어오며 첫 대사가 시작된다.
export function renderStage({ script, order, cards = {}, inputCards = [], choices = {}, start, onConfirm, onRestart, onJump, sheet: given, layout = 'box', parked: parkedAtStart = false, cameraFill = false, cameraMinScale = 0, cameraMaxUnit = 0, fromRect, notice = '' }) {
  const paperMode = layout === 'paper' && Boolean(given);
  let parked = parkedAtStart;
  let again = false; // 이미 지나온 체험을 다시 보는 중(S-20 "시작하기"): 대본 textAgain이 있으면 그 문장을 쓴다
  const lineOf = (step) => (again && step.textAgain) || step.text || '';
  const asks = (step) => Boolean(step.choices && choices[step.choices]); // 선택지로만 넘어가는 장면
  const steps = attachNotice(flatten(script.acts, order), notice);
  let index = Math.max(0, start ? steps.findIndex(start) : 0);
  const jumpAct = takeJump();
  const jumped = steps.findIndex((step) => step.actId === jumpAct);
  if (jumped >= 0) index = jumped;
  let typing = null;
  let busy = false;
  let lastLook = '';
  let lastLaw = '';
  let lastDelta = 1;
  let autoTimer = null;
  let enterTimer = null;
  let lastFocus = null;
  let hadCard = false; // 앞 장면에도 카드가 있었는지(엽서 건네기는 카드가 처음 뜰 때만)

  const wrap = element('section', paperMode ? 'stage-wrap paper-mode' : 'stage-wrap');
  wrap.classList.toggle('parked', parked);
  wrap.setAttribute('aria-label', '판결 체험');
  const acts = element('ol', 'acts');
  ACT_GROUPS.forEach(({ name, ids }, i) => {
    const label = `${i + 1}막 ${name}`;
    const item = element('li', 'act', onJump ? undefined : label);
    if (onJump) {
      const jump = element('button', 'act-jump', label);
      jump.type = 'button';
      jump.title = '테스트용: 이 막으로 바로 가기';
      jump.addEventListener('click', () => jumpTo(ids[0]));
      item.append(jump);
    }
    acts.append(item);
  });

  const stage = element('div', 'stage');
  const scene = element('div', 'scene');
  const bg = element('div', 'bg');
  const sheet = given ?? (script.paper && order.some((actId) => script.acts[actId]?.scene === 'sheet') ? caseSheet(script.paper) : null);
  // 캐릭터: 그림 + 발밑 역할 이름표를 한 묶음으로 움직인다(자리 이동 · 점프에 이름표가 함께 따라간다).
  // 그림을 못 불러오면 그림만 숨기고 이름표는 남긴다
  const figures = {};
  const sprites = {};
  const chips = {};
  ['naero', 'nambul'].forEach((who) => {
    figures[who] = element('div', `spr ${who === 'naero' ? 'n' : 'b'}`);
    sprites[who] = element('img', 'spr-img');
    sprites[who].alt = NAMES[who];
    sprites[who].addEventListener('error', () => { sprites[who].hidden = true; });
    chips[who] = element('div', 'chip');
    figures[who].append(sprites[who], chips[who]);
  });
  const procs = element('div', 'proc');
  procs.setAttribute('aria-label', '재판 순서');
  const sys = element('div', 'sysline');
  const flash = element('div', 'flash');
  const court = courtSet();
  // 4막 AI 판사 셋: 자리는 고정(로봉 · 로울 · 로북). 자리마다 세 자세 그림을 겹쳐 두고 보일 그림만 켠다(0.3초 겹쳐 바뀜).
  // 대본 law: trio(셋 다 대기) · three(말하는 판사 강조) · think(합의, 셋 다 생각) ·
  // deliver(발표하는 판사만 남는다 — 나머지는 자리를 지킨 채 사라지고, 발표자는 가운데로, 발표 카드가 뜨면 왼쪽으로)
  const lawCast = element('div', 'laws');
  lawCast.setAttribute('aria-hidden', 'true');
  const lawSlots = Object.fromEntries(LAW_SPOTS.map((who) => {
    const slot = element('div', `law-slot ${who}`);
    LAW_POSES.forEach((pose) => {
      const image = element('img', `pose ${pose}`);
      image.alt = '';
      image.src = lawImage(who, pose);
      slot.append(image);
    });
    lawCast.append(slot);
    return [who, slot];
  }));
  function lawsEnter() { // 처음 등장할 때 페이드인
    if (reducedMotion()) return;
    lawCast.classList.remove('entering');
    void lawCast.offsetWidth; // 같은 애니메이션을 다시 시작하려고 다시 그리게 한다
    lawCast.classList.add('entering');
  }
  // 신문 지면: 틀(frame)은 기사를 맞출 자리만 정하는 빈 요소(CSS가 위치를 정한다)
  const frame = sheet && !paperMode ? element('div', 'sheet-frame') : null;
  let byKeyboard = false; // 지금 넘기는 입력이 키보드인가(Space · → · Enter, 키보드로 누른 버튼)
  const camera = paperMode ? paperCamera(sheet, () => (dialog.hidden ? controls.getBoundingClientRect().top : dialog.getBoundingClientRect().top), () => byKeyboard)
    : sheet ? sheetCamera(sheet, frame, { fill: cameraFill, minScale: cameraMinScale, maxUnit: cameraMaxUnit }) : null; // cameraFill: 지면이 언제나 장면을 빈틈없이 덮는다(6막 화면 가득)
  if (frame) frame.setAttribute('aria-hidden', 'true');
  scene.append(bg, court, ...(sheet ? [sheet] : []), ...(frame ? [frame] : []), figures.naero, figures.nambul, lawCast, procs, sys, flash);

  const board = element('div', 'board');
  board.tabIndex = -1;
  const dialog = element('div', 'dlg');
  const name = element('div', 'name');
  const text = element('div', 'txt');
  text.setAttribute('aria-hidden', 'true'); // 타이핑 중인 글자는 읽지 않고, 아래 live 영역이 문장 전체를 한 번 읽는다
  const hintBase = window.matchMedia('(hover: none)').matches ? '▶ 탭' : '▶ 클릭 · Space'; // 터치 기기는 Space가 없다
  const hint = element('div', 'nxt', hintBase);
  hint.setAttribute('aria-hidden', 'true');
  const choiceRow = element('div', 'dlg-choices');
  choiceRow.hidden = true;
  choiceRow.addEventListener('click', (event) => event.stopPropagation()); // 선택지를 눌러도 대사창 클릭(다음 장면)으로 번지지 않게
  dialog.append(name, text, choiceRow, hint);
  // 볼따구(데포르메): 해설 장면(대본 cameo: naero · nambul)에서 작은 내로 · 남불이 대사창 위 모서리에 뿅 하고 나와 진행을 설명한다.
  // 배역이 아니라 모습 구분이라 이름표는 "내로 볼따구"처럼 이름 옆에 작게 단다(같은 배역 규칙에서 빠진다)
  const cameo = element('img', 'cameo');
  cameo.alt = '';
  cameo.hidden = true;
  cameo.addEventListener('error', () => { cameo.hidden = true; });
  // 볼따구 등장 · 퇴장: 빠른 뿅(커졌다 자리 잡기 0.22초 · 작아지며 사라지기 0.16초)
  function popCameo(who) {
    cameo.getAnimations().forEach((animation) => animation.cancel());
    cameo.src = imageOf(who, CAMEO_LOOK);
    cameo.className = `cameo cameo-${who}`;
    cameo.hidden = false;
    if (!reducedMotion()) cameo.animate([{ scale: '0' }, { scale: '1.12', offset: .7 }, { scale: '1' }], { duration: 220, easing: 'cubic-bezier(.2, .8, .2, 1)' }); // 0 크기에서 뿅
  }
  function hideCameo() {
    if (cameo.hidden) return;
    if (reducedMotion()) { cameo.hidden = true; return; }
    cameo.animate([{ scale: '1' }, { scale: '0' }], { duration: 160, easing: 'cubic-bezier(.4, 0, 1, 1)' })
      .finished.then(() => { if (!cameo.dataset.who) cameo.hidden = true; }).catch(() => {});
  }
  stage.append(scene, board, cameo, dialog);

  const live = element('p', 'visually-hidden');
  live.setAttribute('aria-live', 'polite');
  const controls = element('div', 'ctrl');
  const prev = element('button', 'secondary-button', '◀ 이전');
  const next = element('button', 'primary-button', '다음 ▶');
  const skip = element('button', 'stage-skip', '대사 건너뛰기');
  const status = element('span', 'sp');
  [prev, next, skip].forEach((b) => { b.type = 'button'; });
  controls.append(skip, status, prev, next);
  if (import.meta.env?.DEV) {
    const chipToggle = element('button', 'stage-skip', '');
    chipToggle.type = 'button';
    const syncChips = () => {
      wrap.classList.toggle('chips-off', chipsOff);
      chipToggle.textContent = chipsOff ? '이름표 켜기 (개발)' : '이름표 끄기 (개발)';
    };
    chipToggle.addEventListener('click', () => { chipsOff = !chipsOff; syncChips(); });
    syncChips();
    skip.after(chipToggle);
  }
  if (onRestart) {
    const restart = element('button', 'stage-restart', '처음부터 다시 (목 데이터)');
    restart.type = 'button';
    restart.addEventListener('click', onRestart);
    controls.append(restart);
  }
  // 카드가 무대 전체를 덮는 장면(대본 board: "full", 3막 판결)에서는 캐릭터가 카드 밖으로 나온다:
  // 남불은 위 막 버튼 줄 끝, 내로는 아래 이전 · 다음 버튼 옆
  const peeks = { nambul: element('img', 'peek b'), naero: element('img', 'peek n') };
  Object.values(peeks).forEach((image) => { image.alt = ''; });
  const peekSlot = element('li', 'peek-slot');
  peekSlot.setAttribute('aria-hidden', 'true');
  peekSlot.append(peeks.nambul);
  acts.append(peekSlot);
  peeks.naero.setAttribute('aria-hidden', 'true');
  controls.insertBefore(peeks.naero, prev);
  wrap.append(acts, stage, controls, live);
  // 신문 지면 무대: 막 칩은 지면의 탭 줄로 옮긴다
  // 신문 지면 무대에는 막 칩이 없다(지면 탭 줄은 신문의 다른 면 목차, newspaperFront.js). 막 칩은 상자 무대(2 ~ 6막)에서만 보인다
  if (paperMode) acts.hidden = true;
  // 신문 지면 무대의 카드(엽서): 남불이 앞으로 나서며 건네고, 엽서가 남불 손에서 화면 가운데로 날아와 펼쳐진다
  function deliver() {
    const from = sprites.nambul.getBoundingClientRect();
    const to = board.getBoundingClientRect();
    board.style.setProperty('--from-x', `${from.left + from.width * 0.3 - (to.left + to.width / 2)}px`);
    board.style.setProperty('--from-y', `${from.top + from.height * 0.45 - (to.top + to.height / 2)}px`);
    [board, figures.nambul].forEach((node) => { node.classList.remove('delivering'); void node.offsetWidth; node.classList.add('delivering'); });
  }

  function finishTyping() {
    clearInterval(typing);
    typing = null;
    text.textContent = lineOf(steps[index]);
    choiceRow.hidden = !steps[index].choices; // 선택지는 대사를 다 읽은 뒤에 뜬다
    // 키보드로 여기까지 온 사람은 선택지로 포커스를 옮겨 준다(FE-97). 예전에는 엔딩이 나와도 포커스가 대사창 밖에 남아 메인으로까지 Tab을 일곱 번 눌러야 했다
    if (byKeyboard && asks(steps[index])) choiceRow.querySelector('button')?.focus({ preventScroll: true });
  }
  function type(line) {
    clearInterval(typing);
    if (reducedMotion()) { finishTyping(); return; } // 한 번에 보여 주되 선택지 · 포커스 이동까지 같은 끝처리를 한다(리뷰 반영 — 안 하면 선택지만 있는 장면이 숨은 채 남는다)
    let shown = 0;
    text.textContent = '';
    typing = setInterval(() => {
      shown += 2;
      text.textContent = line.slice(0, shown);
      if (shown >= line.length) finishTyping();
    }, 22);
  }

  function render() {
    const step = steps[index];
    const input = inputCards.includes(step.card);
    const entry = step.card === 'evidence' ? evidenceBoard(script, step.ev)
      : step.card && cards[step.card] ? cards[step.card]
        : step.card && script.cards?.[step.card] ? scriptCard(script.cards[step.card]) : null;
    const dark = Boolean(step.blackout);

    stage.dataset.scene = step.scene;
    bg.className = `bg ${dark ? 'dark' : step.scene}`;
    court.hidden = step.scene !== 'court' || dark;
    const onSheet = Boolean(sheet) && step.scene === 'sheet';
    if (sheet) sheet.hidden = !onSheet;
    if (onSheet && !parked) { // 기다리는 동안은 지면 전체를 그대로 둔다
      // 첫 장면(앞 막에서 넘어옴)은 from 요소가 무대를 채운 데서 시작한다. 캐릭터는 카메라가 빠져나온 뒤 지면에 뛰어든다
      const from = lastFocus === null && index === 0 ? step.from : undefined;
      camera.go(step.focus, { from, fromRect: from ? fromRect : undefined, onStart: from ? () => stage.classList.add('arrive-sheet') : undefined });
      // 기사가 바뀌면 둘 다 다음 기사 옆으로 뛰어간다(작은 점프 두 번)
      if (lastFocus !== null && lastFocus !== step.focus && !reducedMotion()) {
        ['naero', 'nambul'].forEach((who) => { figures[who].classList.remove('hop'); void figures[who].offsetWidth; figures[who].classList.add('hop'); });
      }
      lastFocus = step.focus;
    }
    const now = ACT_GROUPS.findIndex(({ ids }) => ids.includes(step.actId));
    acts.querySelectorAll(':scope > .act').forEach((item, i) => { // 막 칩만(끝에 붙는 캐릭터 엿보기 자리는 빼고)
      const current = i === now;
      item.classList.toggle('on', current);
      item.classList.toggle('done', i < now);
      if (current) item.setAttribute('aria-current', 'step'); else item.removeAttribute('aria-current');
    });

    stage.classList.toggle('bmode', input);
    const small = Boolean(entry) && !input && step.card !== 'evidence'; // 읽기 카드(대본): 칠판보다 조금 크게
    stage.classList.toggle('card', Boolean(entry) && !input && !small); // 큰 판일 때만 캐릭터가 양 끝으로 비켜 선다
    stage.classList.toggle('hasproc', Boolean(step.procedures));
    ['naero', 'nambul'].forEach((who) => {
      const look = step.look[who];
      sprites[who].hidden = false;
      sprites[who].src = imageOf(who, look);
      figures[who].classList.toggle('role', look.startsWith('role:'));
      figures[who].classList.toggle('dim', Boolean(step.say) && step.say !== who);
      figures[who].classList.toggle('near', onSheet && step.say === who); // 지면에서는 말하는 쪽이 기사에 다가선다
      const offCast = Boolean(step.cast) && !step.cast.includes(who); // 대본 cast: 이 막에 나오는 캐릭터만(5막은 남불만)
      figures[who].classList.toggle('hide', dark || Boolean(step.law) || offCast || Boolean(step.hideCast)); // 대본 hideCast: 빈 무대(2막 시작)
      // spots 값: 이름 자리("stand") 또는 슬롯 { slot: 0~60, row: "front" | "back" }
      const spot = step.spots[who];
      const at = spot && typeof spot === 'object' ? spot : null;
      if (spot && !at) figures[who].dataset.spot = spot; else delete figures[who].dataset.spot;
      if (at && Number.isFinite(at.slot)) { figures[who].dataset.slot = ''; figures[who].style.setProperty('--slot', at.slot); } else { delete figures[who].dataset.slot; figures[who].style.removeProperty('--slot'); }
      if (at?.row) figures[who].dataset.row = at.row; else delete figures[who].dataset.row;
      const seat = seatOf(look);
      if (seat) figures[who].dataset.seat = seat; else delete figures[who].dataset.seat;
      chips[who].textContent = step.chips[who];
      chips[who].dataset.role = ROLE_COLOR[step.chips[who]] ?? 'actor';
      chips[who].hidden = !step.chips[who];
    });
    lawCast.hidden = !step.law;
    lawCast.classList.toggle('resolving', lastLaw === 'think' && step.law === 'deliver');
    if (step.law && !lastLaw) lawsEnter();
    stage.classList.toggle('lawcard', Boolean(step.law) && Boolean(entry));
    stage.classList.toggle('lawdeliver', step.law === 'deliver');
    if (step.law) LAW_SPOTS.forEach((who) => {
      const slot = lawSlots[who];
      const lead = step.say === who;
      const deliver = step.law === 'deliver';
      const pose = deliver ? (lead ? 'deliver' : 'think') : step.law === 'think' ? 'think' : 'idle';
      slot.querySelectorAll('.pose').forEach((image) => image.classList.toggle('on', image.classList.contains(pose)));
      slot.classList.toggle('gone', deliver && !lead);
      slot.classList.toggle('lead', step.law === 'three' && lead);
      slot.classList.toggle('dim', step.law === 'three' && !lead);
      slot.dataset.place = deliver && lead ? (entry ? 'side' : 'center') : 'home';
    });
    const looks = `${step.look.naero}|${step.look.nambul}`;
    if (lastLook && looks !== lastLook && (looks.includes('role:') || lastLook.includes('role:')) && !reducedMotion()) {
      flash.classList.remove('on');
      void flash.offsetWidth; // 같은 애니메이션을 다시 시작하려고 다시 그리게 한다
      flash.classList.add('on');
    }
    lastLook = looks;

    procs.hidden = !step.procedures || step.board === 'full'; // 무대를 덮는 카드 위에는 재판 순서를 띄우지 않는다
    if (step.procedures) {
      procs.replaceChildren(...step.procedures.map((label, i) => {
        const item = element('span', i === step.proc ? 'on' : i < step.proc ? 'done' : '', `${i + 1} ${label}`);
        if (i === step.proc) item.setAttribute('aria-current', 'step');
        return item;
      }));
    }

    const caption = step.blackout ?? step.system ?? '';
    sys.textContent = caption;
    sys.hidden = !caption;
    sys.classList.toggle('notice', Boolean(step.notice) && !step.blackout); // 민감 사건 안내(FE-129)는 캐릭터에 가리지 않게 위쪽에
    sys.classList.toggle('on-dark', dark);

    // 증거판은 칸마다 따로 스크롤하고, 나머지 카드는 카드 전체가 스크롤된다
    board.replaceChildren(...(entry ? [step.card === 'evidence' ? entry : scrollArea(entry, '카드 내용')] : []));
    board.hidden = !entry;
    // 신문 지면 무대의 엽서(입력 카드)는 대화창이다: 열려 있는 동안 뒤 지면은 비활성(포커스 · 읽기 제외)
    if (paperMode) {
      if (input) { board.setAttribute('role', 'dialog'); board.setAttribute('aria-modal', 'true'); board.setAttribute('aria-label', '첫인상 엽서'); }
      else { board.removeAttribute('role'); board.removeAttribute('aria-modal'); board.removeAttribute('aria-label'); }
      sheet.inert = Boolean(input);
      controls.inert = Boolean(input); // 조작부(◀ 이전 등)로 Tab 포커스가 빠져나가지 않게(aria-modal만으로는 가둬지지 않는다)
      const siteHeader = document.getElementById('route-header'); // 무대 밖 공통 헤더(FE-56)도 같이 막는다. 화면이 바뀌면 라우터가 푼다
      if (siteHeader) siteHeader.inert = Boolean(input);
    }
    board.classList.toggle('wide', input);
    board.classList.toggle('fill', step.card === 'evidence'); // 증거판은 보드를 꽉 채운다(가운데 정렬 없음)
    board.classList.toggle('reading', step.board === 'reading'); // 읽기 카드(5막 판결문): 두 캐릭터 사이 가운데
    const full = step.board === 'full';
    board.classList.toggle('full', full);
    wrap.classList.toggle('peeking', full);
    if (full) ['naero', 'nambul'].forEach((who) => { peeks[who].src = imageOf(who, step.look[who]); });
    board.classList.toggle('small', small);
    const latest = board.querySelector('li.now'); // 쌓이는 목록(증거조사 · 판단 요소)은 지금 줄이 보이게 끝까지 내린다
    if (latest) { const list = latest.closest('.scroll-body'); list.scrollTop = list.scrollHeight; }
    if (paperMode && entry && !hadCard) deliver();
    hadCard = Boolean(entry);

    const line = lineOf(step);
    dialog.hidden = input || !line;
    dialog.classList.toggle('fade-in', lastLaw === 'think' && step.law === 'deliver');
    lastLaw = step.law ?? '';
    // 대본 auto(ms): 앞으로 넘어와 이 장면에 닿으면 저절로 다음 장면(4막 합의 → 발표).
    // 판사 자세가 바뀌는 장면은 바뀜(0.3초)이 다 끝난 뒤부터 센다
    clearTimeout(autoTimer);
    const settle = step.law && !reducedMotion() ? LAW_FADE_MS : 0;
    if (step.auto && lastDelta > 0) autoTimer = setTimeout(() => { if (steps[index] === step && wrap.isConnected) go(1); }, settle + step.auto);
    const cameoWho = line && step.say === 'narrator' && NAMES[step.cameo] ? step.cameo : '';
    if (line) {
      const role = cameoWho ? CAMEO_LABEL : step.say === 'narrator' ? step.role ?? '법정 진행' : step.role ?? (step.say === 'law' ? 'AI 판사' : ''); // 해설은 역할 이름만(예: 실제 판결). 합의부 발언은 AI 판사
      name.replaceChildren();
      name.dataset.role = ROLE_COLOR[role] ?? 'guide';
      // 말풍선 꼬리 방향(말하는 캐릭터 쪽). 볼따구는 대사창 모서리에 나온 작은 캐릭터 쪽
      dialog.dataset.speaker = cameoWho ? `cameo-${cameoWho}` : step.say === 'naero' || step.say === 'nambul' ? step.say : 'none';
      // 말하는 캐릭터가 가운데 피고인석(stand)에 서 있으면 꼬리도 가운데로
      dialog.dataset.spot = !cameoWho && (step.say === 'naero' || step.say === 'nambul') ? (typeof step.spots[step.say] === 'string' ? step.spots[step.say] : '') : '';
      const who = cameoWho || step.say;
      const deform = !cameoWho && step.look[who]?.startsWith('cheek-slim:') ? SLIM_CHEEK_LABEL : !cameoWho && DEFORM_LABEL[step.look[who]?.split(':')[1]] && step.look[who].startsWith('deform:') ? DEFORM_LABEL[step.look[who].split(':')[1]] : '';
      if (NAMES[who]) name.append(picture(iconOf(who, cameoWho ? 'idle-thinking' : step.look[who])), NAMES[who]); // AI 판사는 이름 · 얼굴 없이 역할만
      // 데포르메(란마 등)는 배역이 아니라 모습이라 역할 대신 모습 이름을 단다(예: 남불 란마)
      const label = deform && (!role || role === '안내') ? deform : role;
      if (label) name.append(element('i', '', ` ${label}`));
    }
    const enterDelay = step.delay && lastDelta > 0 && !reducedMotion() ? step.delay : 0; // 대본 delay(ms): 빈 무대에서 잠깐 뒤 등장(2막 시작)
    clearTimeout(enterTimer);
    if (cameoWho !== (cameo.dataset.who ?? '')) {
      cameo.dataset.who = cameoWho;
      if (cameoWho) { if (enterDelay) cameo.hidden = true; else popCameo(cameoWho); } else hideCameo();
    }
    // 스크린 리더 알림: 대사가 없으면 지문을 읽는다. 민감 사건 안내(FE-129)는 대사가 있는 장면(2막 시작)에 붙으므로 안내를 먼저 읽고 대사를 잇는다
    live.textContent = parked ? '' : step.notice && line ? `${caption} ${line}` : line || caption;
    const options = choices[step.choices] ?? [];
    const note = element('p', 'dlg-note');
    note.setAttribute('role', 'status');
    choiceRow.replaceChildren(...options.map(({ label, action, primary }) => {
      const choice = element('button', primary ? 'primary-button' : 'secondary-button', label);
      choice.type = 'button';
      choice.addEventListener('click', () => action(note));
      return choice;
    }), ...(options.length ? [note] : []));
    choiceRow.hidden = true;
    dialog.classList.toggle('has-choices', options.length > 0);
    if (parked) text.textContent = '';
    else if (enterDelay) {
      dialog.style.visibility = 'hidden';
      text.textContent = '';
      enterTimer = setTimeout(() => {
        if (steps[index] !== step || !wrap.isConnected) return;
        dialog.style.visibility = '';
        if (cameoWho) popCameo(cameoWho);
        type(line);
      }, enterDelay);
    } else { dialog.style.visibility = ''; type(line); }

    prev.disabled = index === 0;
    next.textContent = step.next ? `${step.next} ▶` : '다음 ▶';
    next.disabled = input || asks(step) || (index === steps.length - 1 && !step.confirm); // 마지막 장면의 confirm은 화면이 다음 화면으로 보낸다
    skip.hidden = input || asks(step) || skipTarget(steps, index) === index;
    status.classList.remove('is-blocked');
    status.textContent = input ? (again ? '엽서를 확인하고 "이어서 하기"를 눌러요' : '선택하고 제출하면 다음으로 넘어가요') : '';
    hint.textContent = `${index + 1} / ${steps.length} · ${hintBase}`; // 이 화면의 몇 번째 장면인지(FE-103: 몇 번 더 넘기면 되는지 알린다). 조작 줄이 아니라 대사창에 둬 모바일 줄 구성은 그대로
    syncNext();
    if (input) (board.querySelector('[data-autofocus]') ?? board).focus(); // 입력 카드가 정한 첫 포커스(판결 파일철: 지금 장의 탭)
  }

  async function go(delta) {
    if (parked || busy || stage.querySelector('.eyecatch')) return; // 기다리는 동안 · 아이캐치가 떠 있는 동안은 장면을 넘기지 않는다
    lastDelta = delta;
    if (delta > 0 && typing) { finishTyping(); return; }
    const target = index + delta;
    if (target < 0 || (target >= steps.length && !(delta > 0 && steps[index].confirm))) return;
    if (delta > 0 && inputCards.includes(steps[index].card)) return; // 입력 카드는 화면의 제출로만 넘어간다
    if (delta > 0 && asks(steps[index])) return; // 선택지가 있는 장면은 선택지로만 넘어간다
    if (delta > 0 && steps[index].confirm && onConfirm) {
      busy = true;
      next.disabled = true;
      next.setAttribute('aria-busy', 'true');
      const ok = await onConfirm(steps[index].confirm);
      busy = false;
      next.removeAttribute('aria-busy');
      if (!wrap.isConnected) return;
      if (!ok) { render(); return; }
    }
    if (target >= steps.length) return;
    index = target;
    render();
  }
  // 이 화면의 막이면 그 막 첫 장면으로, 아니면 화면에 맡긴다(서버 확인 없이 건너뛰므로 테스트 기간 · 목 데이터 전용)
  function jumpTo(actId) {
    if (busy) return;
    if (parked) { onJump(actId); return; } // 기다리는 중에는 그 막의 화면을 새로 연다
    const first = steps.findIndex((step) => step.actId === actId);
    if (first < 0) { onJump(actId); return; }
    finishTyping();
    index = first;
    render();
  }
  // 버튼의 click은 키보드(Space · Enter)로 눌러도 오지만 detail이 0이다
  prev.addEventListener('click', (event) => { byKeyboard = event.detail === 0; go(-1); });
  // 입력 카드가 stageNext({ label, hint, enabled, run })를 주면 하단 "다음"은 장면을 넘기지 않고 카드를 조작한다(판결 파일철: 다음 장 · 서명)
  const nextDriver = () => (inputCards.includes(steps[index].card) ? cards[steps[index].card]?.stageNext ?? null : null);
  function syncNext() {
    const driver = nextDriver();
    if (!driver) return;
    next.textContent = driver.label();
    next.disabled = driver.enabled ? !driver.enabled() : false;
    status.textContent = driver.hint();
    status.classList.toggle('is-blocked', Boolean(driver.blocked?.())); // 막힌 이유는 보통 안내보다 눈에 띄게(FE-103: 막힌 사용자가 이유를 아는 유일한 자리였다)
  }
  inputCards.forEach((name) => cards[name]?.addEventListener('stagenextchange', syncNext));
  next.addEventListener('click', (event) => {
    byKeyboard = event.detail === 0;
    const driver = nextDriver();
    if (driver) driver.run(); else go(1);
  });
  skip.addEventListener('click', () => { if (busy) return; finishTyping(); index = skipTarget(steps, index); render(); });
  dialog.addEventListener('click', () => { byKeyboard = false; go(1); });
  // 장면 클릭 = 다음. 지면 안의 버튼 · 링크 · 카드(신문 지면 무대의 막 칩 · 메뉴 · 독자 의견 칸)는 제 동작만 한다
  // 신문 지면 무대(paperMode)에서는 지면을 눌러도 넘어가지 않는다(글을 읽다 누르는 경우가 많다). 대사창 · 다음 버튼 · Space로만 진행
  scene.addEventListener('click', (event) => { if (!paperMode && !event.target.closest('button, a, input, label, .board')) go(1); });

  function onKey(event) {
    if (!wrap.isConnected) { document.removeEventListener('keydown', onKey); return; }
    // 다른 곳(판결 카드의 형량 막대 등)이 이미 처리한 키, 입력칸 · 슬라이더 위의 키는 무대가 받지 않는다
    if (event.defaultPrevented || event.target.closest('input, textarea, select, [role="slider"]')) return;
    // 보드를 클릭하면 보드가 포커스를 받는다. 입력 카드는 제출로만 넘어가니 이전(←)만 받고, 다른 보드에서는 Space만 스크롤에 양보한다
    if (event.target.closest('.board') && ((stage.classList.contains('bmode') && event.key !== 'ArrowLeft') || event.key === ' ')) return;
    if (event.key === 'ArrowRight' || event.key === ' ' || event.key === 'Enter') {
      if (event.target.closest('button') && event.key !== 'ArrowRight') return; // 버튼 위의 Space · Enter는 버튼이 처리한다
      event.preventDefault();
      byKeyboard = true;
      go(1);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      byKeyboard = true;
      go(-1);
    }
  }
  document.addEventListener('keydown', onKey);

  // 1막 → 2막: 보드를 걷고 신문 사진이 무대를 가득 채울 때까지 확대한다(사진 속으로 들어가 재판이 시작된다)
  wrap.enterPhoto = () => new Promise((resolve) => {
    const photo = sheet?.querySelector('.sheet-case-photo');
    if (!photo || reducedMotion()) { resolve(); return; }
    stage.classList.add('entering');
    board.hidden = true;
    dialog.hidden = true;
    const from = photo.getBoundingClientRect();
    // 신문 지면 무대는 무대 상자가 없으니 사진이 화면 전체를 채운다
    const box = paperMode ? { left: 0, top: 0, width: window.innerWidth, height: window.innerHeight } : stage.getBoundingClientRect();
    const zoom = picture(photo.src, 'zoom');
    if (paperMode) zoom.classList.add('fixed');
    Object.assign(zoom.style, { left: `${from.left - box.left}px`, top: `${from.top - box.top}px`, width: `${from.width}px`, height: `${from.height}px` });
    stage.append(zoom);
    requestAnimationFrame(() => requestAnimationFrame(() => {
      Object.assign(zoom.style, { left: '0px', top: '0px', width: `${box.width}px`, height: `${box.height}px` });
      zoom.classList.add('in');
    }));
    setTimeout(resolve, 750);
  });
  // 아이캐치: 장면 사이에 한 장면을 덮어 보여 주고, 정해진 시간이 지나거나 클릭 · Space · Enter면 끝난다.
  // 기본(배열을 넘기면)은 사진 → 재판 사이(시간이 흐른 것 + 무작위 법정 상식 하나).
  // 객체를 넘기면 { title, sub, k, lines(모두 보여 줌), cast: [[who, look], [who, look]], ms }로 바꿔 쓴다(4막 AI 판결 기다림)
  wrap.interlude = (options = []) => new Promise((resolve) => {
    const facts = Array.isArray(options) ? options : [];
    const {
      title = '그로부터 몇 달 뒤, 법정', sub = '기사 속 사건은 지나갔고, 이제 그 사건의 재판이 열려요.', k = '법정 상식',
      lines = facts.length ? [facts[Math.floor(Math.random() * facts.length)]] : [],
      cast: people = [['naero', 'role:defense'], ['nambul', 'role:prosecutor']], ms = 3600,
    } = Array.isArray(options) ? {} : options;
    const card = element('div', 'eyecatch');
    card.setAttribute('role', 'status');
    card.style.setProperty('--ec-ms', `${ms}ms`);
    const brand = element('p', 'ec-brand');
    brand.append('내', element('span', 'law', 'Law'), '남불');
    const fact = element('div', 'ec-fact');
    if (lines.length) fact.append(element('p', 'ec-k', k), ...lines.map((line) => element('p', '', line)));
    const bar = element('div', 'ec-bar');
    bar.append(element('i'));
    const cast = element('div', 'ec-cast');
    cast.append(picture(imageOf(...people[0]), 'ec-n'), picture(imageOf(...people[1]), 'ec-b'));
    card.append(cast, brand, element('p', 'ec-title', title), element('p', 'ec-sub', sub), fact, bar, element('p', 'ec-skip', '클릭하면 바로 넘어가요'));
    stage.append(card);
    let timer = null;
    const done = () => {
      clearTimeout(timer);
      card.removeEventListener('click', done);
      document.removeEventListener('keydown', onKey, true);
      card.remove();
      if (steps[index].law) lawsEnter(); // 아이캐치에 가려 있던 판사 등장(페이드인)을 걷힌 뒤에 보여 준다
      resolve();
    };
    // 캡처 단계에서 먼저 받아 무대의 키 처리(장면 넘기기)까지 가지 않게 한다
    const onKey = (event) => {
      if (!card.isConnected || !wrap.isConnected) { done(); return; } // 아이캐치 도중 화면을 떠났으면 키를 가로채지 않고 정리만
      if (event.key === ' ' || event.key === 'Enter') { event.preventDefault(); event.stopPropagation(); done(); }
    };
    card.addEventListener('click', done);
    document.addEventListener('keydown', onKey, true);
    timer = setTimeout(done, ms);
  });
  // 기다림(parked)을 풀고 첫 장면을 시작한다: 내로 · 남불이 들어오고 대사가 시작된다
  wrap.release = () => {
    if (!parked) return;
    parked = false;
    wrap.classList.remove('parked');
    wrap.classList.add('entering'); // 움직임 줄이기에서는 CSS가 움직임 없이 페이드만 건다(paperStage.css)
    // 들어오는 연출이 끝나면 걷는다. 남겨 두면 이 클래스의 animation(우선순위가 더 높다)이 남불의 "엽서 건네기"(.delivering) animation을 덮어
    // 사건 디테일에서 시작한 뒤에는 남불이 움직이지 않는다
    setTimeout(() => wrap.classList.remove('entering'), ENTER_MS);
    render();
    // 시작 버튼이 사라지므로 포커스를 옮긴다: 선택지 장면이면 첫 선택지(대사를 바로 다 보여 준다), 아니면 대사 진행
    if (asks(steps[index])) { finishTyping(); choiceRow.querySelector('button')?.focus({ preventScroll: true }); }
    else next.focus({ preventScroll: true });
  };
  // 다시 보기로 바꾼다(대본 textAgain 문장 · 엽서 안내). release 전에 부른다
  wrap.replay = () => { again = true; };
  // 선택지 장면 등을 건너 다음 장면으로(화면이 선택지 처리 뒤에 부른다).
  // dropCurrent: 지금 장면을 대사 목록에서 뺀다 — 한 번 고른 질문(S-20 "이어서 할래요?")이 ◀ 이전으로 다시 나오지 않게
  wrap.advance = ({ dropCurrent = false } = {}) => {
    if (index >= steps.length - 1) return;
    finishTyping();
    if (dropCurrent) steps.splice(index, 1);
    else index += 1;
    render();
    next.focus({ preventScroll: true });
  };
  // 무대를 멈추고 지면만 남긴다(6막 "여기 남아있기"): 장면 넘기기 · 키 입력을 받지 않고, 기사 흐림을 걷는다
  wrap.freeze = () => {
    parked = true;
    clearTimeout(autoTimer);
    clearTimeout(enterTimer);
    clearInterval(typing);
    sheet?.classList.remove('spot');
    sheet?.querySelectorAll('.is-on').forEach((node) => node.classList.remove('is-on'));
  };
  // 화면이 카드(cards)를 나중에 채웠을 때, 지금 그 카드 장면이면 다시 그린다(예: S-20에서 체험을 시작한 뒤 받아 온 1막 엽서의 사전 판단 폼)
  wrap.refresh = (card) => { if (wrap.isConnected && steps[index].card === card) render(); };
  // 재판(2막)을 처음부터 시작할 때: 사진 속에서 법정이 나타나는 느낌으로 한 번 들어온다
  if (index === 0 && steps[0].scene === 'court' && !reducedMotion()) stage.classList.add('arrive');

  render();
  return wrap;
}
