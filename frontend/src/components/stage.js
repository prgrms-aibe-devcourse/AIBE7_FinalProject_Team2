// 판결 체험 무대 (REQ-089, FE-27 · 28). 흐름 목업(Claude outputs/mockups/메인책상_신문_사건정보_판결체험_흐름_목업)의
// 게임 화면을 바탕으로: 장면 · 캐릭터 · 이름표 칩 · 장면 안 대사창 · 보드(카드) · 막 칩 · 재판 순서.
// 1 · 2막은 신문 지면 자체가 장면이고(메인 책상의 신문과 같은 짜임), 내로 · 남불이 지면 위를 옮겨 다니며 설명한다.
// 앱 쪽에서 바꾼 것은 연결뿐이다 — 막은 화면(S-03 · S-04 …)이 고르고, 입력은 화면의 폼, 서버 "읽었습니다"는 onConfirm.

import { scrollArea } from './scrollArea.js';

export const ACT_NAMES = ['신문 속 사건', '첫인상 판단', '재판', '판결 전 정리', '당신이 판사', 'AI 판사의 합의', '실제 판결', '세 판결 비교'];
const NAMES = { naero: '내로', nambul: '남불' };
const ROLE_LABEL = { actor: '사건 속 인물', prosecutor: '검사', defense: '변호인', judge: '법률 안내' };
const ROLE_COLOR = { '법정 진행': 'narrator', 'A씨 역': 'actor', 'B씨 역': 'actor', '사건 속 인물': 'actor', 검사: 'prosecutor', 변호인: 'defense', '법률 안내': 'law' };
// shortcut: 캐릭터 컷은 원본 이미지 레포 PNG를 직접 읽는다, 웹 webp 변환(WBS 7.6.8) 때 public 경로로 옮긴다
const CUTS = 'https://raw.githubusercontent.com/peanutboi710/NaeLawNamBul_Images/main/character/cuts/';
const imageOf = (who, look) => (look.startsWith('role:')
  ? `/images/characters/roles/${who}_${look.slice(5)}.webp`
  : `${CUTS}${who}/base/${who}_cut_${look}.png`);
const FACES = ['neutral', 'happy', 'surprised', 'thinking', 'sorry', 'cheer'];
const iconOf = (who, look) => {
  const face = look.startsWith('role:') ? 'neutral' : look.split('-')[1];
  return `${CUTS}${who}/icon/${who}_icon_face-${FACES.includes(face) ? face : 'neutral'}.png`;
};
// 이름표는 역할 의상을 입었을 때만 단다(평상복이면 없음)
const chipText = (look, role) => {
  if (!look.startsWith('role:')) return '';
  const label = ROLE_LABEL[look.slice(5)];
  return role && (role === label || role.endsWith('역')) ? role : label;
};

// 대본의 막들을 한 줄로 편다. 각 장면은 앞 장면의 모습(look) · 자리(spots) · 이름표(tags) · 재판 순서(proc)를 이어받는다.
export function flatten(acts, order) {
  const steps = [];
  order.forEach((actId) => {
    const act = acts[actId];
    let look = { ...act.look };
    let spots = { ...act.spots };
    let tags = { naero: '', nambul: '' };
    let proc = null;
    const said = { naero: '', nambul: '' };
    act.steps.forEach((step) => {
      look = { ...look, ...step.look };
      spots = { ...spots, ...step.spots };
      tags = { ...tags, ...step.tags };
      proc = step.proc ?? proc;
      if (said[step.say] !== undefined && step.role) said[step.say] = step.role;
      const chips = { naero: tags.naero || chipText(look.naero, said.naero), nambul: tags.nambul || chipText(look.nambul, said.nambul) };
      steps.push({ ...step, actId, scene: act.scene ?? 'news', procedures: act.procedures, proc, look, spots, chips });
    });
  });
  return steps;
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

const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function picture(src, className, alt = '') {
  const image = element('img', className);
  image.src = src;
  image.alt = alt;
  image.addEventListener('error', () => image.remove(), { once: true });
  return image;
}

// 1 · 2막 장면: 메인 책상의 신문과 같은 짜임(정보 줄 · 제호 · 날짜 띠 · 큰 제목 · 사진 + 부제 · 본문 두 단)
function paperPage(paper) {
  const page = element('article', 'paper');
  const info = element('div', 'paper-info');
  info.append(element('span', '', '판결 체험 신문'), element('span', '', paper.desk));
  const brand = element('div', 'paper-brand');
  brand.append('내', element('span', 'law', 'Law'), '남불');
  const date = element('div', 'paper-date');
  date.append(element('span', '', new Intl.DateTimeFormat('ko-KR', { dateStyle: 'full' }).format(new Date())), element('span', '', '참여 무료'));
  const front = element('div', 'paper-front');
  const left = element('div', 'paper-col');
  const figure = element('figure', 'paper-figure');
  figure.append(picture(paper.photo, 'paper-photo'), element('figcaption', '', paper.caption));
  left.append(figure, element('p', 'paper-deck', paper.deck));
  const right = element('div', 'paper-col paper-body');
  paper.article.forEach((line) => right.append(element('p', '', line)));
  front.append(left, right);
  page.append(info, brand, date, element('h2', 'paper-headline', paper.headline), front);
  return page;
}

// 3막 법정(재판장 시점): 사용자가 판사석에 앉아 법정을 내려다본다. 판사석은 그리지 않는다 — 앉은 사람이 사용자다.
// 벽 · 방청석 난간 · 피고인석 · 검사석 · 변호인석을 캐릭터 뒤에 둔다
function courtSet() {
  const court = element('div', 'court');
  court.setAttribute('aria-hidden', 'true');
  court.append(element('div', 'court-wall'), element('div', 'court-rail'), element('div', 'court-stand'),
    element('div', 'court-desk p'), element('div', 'court-desk d'));
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
    if (script.evidencePhotos?.[side]) column.append(picture(script.evidencePhotos[side], 'evd-photo'));
    column.append(element('h4', '', `${side} 측 증거`));
    const list = element('ol', 'evd-list');
    script.evidence.forEach((item, i) => {
      if (item.side !== side || i > at) return;
      const row = element('li', i === at ? 'now' : '', item.text);
      if (i === at) row.setAttribute('aria-current', 'true');
      list.append(row);
    });
    column.append(scrollArea(list));
    board.append(column);
  });
  board.append(element('p', 'evd-count', `증거조사 ${at + 1} / ${script.evidence.length}`));
  return board;
}

// script: 사건 대본, order: 이 화면이 보여 줄 막, cards: 화면이 주는 카드 { 이름: 요소 },
// inputCards: 입력 카드 이름(보드를 넓게 띄우고 화면의 제출로만 넘어간다), start(step): 재진입 장면,
// onConfirm(n): confirm 장면을 떠날 때 화면이 서버에 "읽었습니다"를 보낸다(false면 넘어가지 않는다),
// onRestart: 있으면 "처음부터 다시" 버튼을 단다(목 데이터 전용). 반환한 요소의 enterPhoto()는 신문 사진 속으로 들어가는 연출
export function renderStage({ script, order, cards = {}, inputCards = [], start, onConfirm, onRestart }) {
  const steps = flatten(script.acts, order);
  let index = Math.max(0, start ? steps.findIndex(start) : 0);
  let typing = null;
  let busy = false;
  let lastLook = '';

  const wrap = element('section', 'stage-wrap');
  wrap.setAttribute('aria-label', '판결 체험');
  const acts = element('ol', 'acts');
  ACT_NAMES.forEach((name, i) => acts.append(element('li', 'act', `${i + 1}막 ${name}`)));

  const stage = element('div', 'stage');
  const scene = element('div', 'scene');
  const bg = element('div', 'bg');
  const page = script.paper ? paperPage(script.paper) : null;
  const figures = {};
  const chips = {};
  ['naero', 'nambul'].forEach((who) => {
    const short = who === 'naero' ? 'n' : 'b';
    figures[who] = element('img', `spr ${short}`);
    figures[who].alt = NAMES[who];
    figures[who].addEventListener('error', () => { figures[who].hidden = true; });
    chips[who] = element('div', `chip ${short}`);
  });
  const procs = element('div', 'proc');
  procs.setAttribute('aria-label', '재판 순서');
  const sys = element('div', 'sysline');
  const flash = element('div', 'flash');
  const court = courtSet();
  scene.append(bg, court, ...(page ? [page] : []), figures.naero, figures.nambul, chips.naero, chips.nambul, procs, sys, flash);

  const board = element('div', 'board');
  board.tabIndex = -1;
  const dialog = element('div', 'dlg');
  const name = element('div', 'name');
  const text = element('div', 'txt');
  text.setAttribute('aria-hidden', 'true'); // 타이핑 중인 글자는 읽지 않고, 아래 live 영역이 문장 전체를 한 번 읽는다
  const hint = element('div', 'nxt', '▶ 클릭 · Space');
  hint.setAttribute('aria-hidden', 'true');
  dialog.append(name, text, hint);
  stage.append(scene, board, dialog);

  const live = element('p', 'visually-hidden');
  live.setAttribute('aria-live', 'polite');
  const controls = element('div', 'ctrl');
  const prev = element('button', 'secondary-button', '◀ 이전');
  const next = element('button', 'primary-button', '다음 ▶');
  const skip = element('button', 'stage-skip', '대사 건너뛰기');
  const status = element('span', 'sp');
  [prev, next, skip].forEach((b) => { b.type = 'button'; });
  controls.append(skip, status, prev, next);
  if (onRestart) {
    const restart = element('button', 'stage-restart', '처음부터 다시 (목 데이터)');
    restart.type = 'button';
    restart.addEventListener('click', onRestart);
    controls.append(restart);
  }
  wrap.append(acts, stage, controls, live);

  function finishTyping() {
    clearInterval(typing);
    typing = null;
    text.textContent = steps[index].text ?? '';
  }
  function type(line) {
    clearInterval(typing);
    if (reducedMotion()) { text.textContent = line; return; }
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
    if (page) page.hidden = step.scene !== 'paper';
    court.hidden = step.scene !== 'court' || dark;
    [...acts.children].forEach((item, i) => {
      const current = String(i + 1) === step.actId;
      item.classList.toggle('on', current);
      item.classList.toggle('done', i + 1 < Number(step.actId));
      if (current) item.setAttribute('aria-current', 'step'); else item.removeAttribute('aria-current');
    });

    stage.classList.toggle('bmode', input);
    const small = Boolean(entry) && !input && step.card !== 'evidence' && !cards[step.card]; // 대본 카드: 칠판보다 조금 크게
    stage.classList.toggle('card', Boolean(entry) && !input && !small); // 큰 판일 때만 캐릭터가 양 끝으로 비켜 선다
    stage.classList.toggle('hasproc', Boolean(step.procedures));
    ['naero', 'nambul'].forEach((who) => {
      const look = step.look[who];
      figures[who].hidden = false;
      figures[who].src = imageOf(who, look);
      figures[who].classList.toggle('role', look.startsWith('role:'));
      figures[who].classList.toggle('dim', Boolean(step.say) && step.say !== who);
      figures[who].classList.toggle('hide', dark);
      if (step.spots[who]) figures[who].dataset.spot = step.spots[who]; else delete figures[who].dataset.spot;
      chips[who].textContent = step.chips[who];
      chips[who].dataset.role = ROLE_COLOR[step.chips[who]] ?? 'actor';
      if (step.spots[who]) chips[who].dataset.spot = step.spots[who]; else delete chips[who].dataset.spot;
      chips[who].classList.toggle('hide', dark || !step.chips[who]);
    });
    const looks = `${step.look.naero}|${step.look.nambul}`;
    if (lastLook && looks !== lastLook && (looks.includes('role:') || lastLook.includes('role:')) && !reducedMotion()) {
      flash.classList.remove('on');
      void flash.offsetWidth; // 같은 애니메이션을 다시 시작하려고 다시 그리게 한다
      flash.classList.add('on');
    }
    lastLook = looks;

    procs.hidden = !step.procedures;
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
    sys.classList.toggle('on-dark', dark);

    // 증거판은 칸마다 따로 스크롤하고, 나머지 카드는 카드 전체가 스크롤된다
    board.replaceChildren(...(entry ? [step.card === 'evidence' ? entry : scrollArea(entry)] : []));
    board.hidden = !entry;
    board.classList.toggle('wide', input);
    board.classList.toggle('fill', step.card === 'evidence'); // 증거판은 보드를 꽉 채운다(가운데 정렬 없음)
    board.classList.toggle('small', small);
    const latest = board.querySelector('.evd-list li.now');
    if (latest) { const list = latest.closest('.scroll-body'); list.scrollTop = list.scrollHeight; }

    const line = step.text ?? '';
    dialog.hidden = input || !line;
    if (line) {
      const role = step.say === 'narrator' ? '법정 진행' : step.role ?? '';
      name.replaceChildren();
      name.dataset.role = ROLE_COLOR[role] ?? 'guide';
      if (step.say !== 'narrator') name.append(picture(iconOf(step.say, step.look[step.say])), NAMES[step.say]);
      if (role) name.append(element('i', '', ` ${role}`));
    }
    live.textContent = line || caption;
    type(line);

    prev.disabled = index === 0;
    next.textContent = step.next ? `${step.next} ▶` : '다음 ▶';
    next.disabled = input || (index === steps.length - 1 && !step.confirm); // 마지막 장면의 confirm은 화면이 다음 화면으로 보낸다
    skip.hidden = input || skipTarget(steps, index) === index;
    status.textContent = input ? '선택하고 제출하면 다음으로 넘어가요' : '';
    if (input) board.focus();
  }

  async function go(delta) {
    if (busy) return;
    if (delta > 0 && typing) { finishTyping(); return; }
    const target = index + delta;
    if (target < 0 || (target >= steps.length && !(delta > 0 && steps[index].confirm))) return;
    if (delta > 0 && inputCards.includes(steps[index].card)) return; // 입력 카드는 화면의 제출로만 넘어간다
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
  prev.addEventListener('click', () => go(-1));
  next.addEventListener('click', () => go(1));
  skip.addEventListener('click', () => { if (busy) return; finishTyping(); index = skipTarget(steps, index); render(); });
  dialog.addEventListener('click', () => go(1));
  scene.addEventListener('click', () => go(1));

  function onKey(event) {
    if (!wrap.isConnected) { document.removeEventListener('keydown', onKey); return; }
    if (event.target.closest('input, textarea, select, .board')) return;
    if (event.key === 'ArrowRight' || event.key === ' ' || event.key === 'Enter') {
      if (event.target.closest('button') && event.key !== 'ArrowRight') return; // 버튼 위의 Space · Enter는 버튼이 처리한다
      event.preventDefault();
      go(1);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      go(-1);
    }
  }
  document.addEventListener('keydown', onKey);

  // 2막 → 3막: 보드를 걷고 신문 사진이 무대를 가득 채울 때까지 확대한다(사진 속으로 들어가 재판이 시작된다)
  wrap.enterPhoto = () => new Promise((resolve) => {
    const photo = page?.querySelector('.paper-photo');
    if (!photo || reducedMotion()) { resolve(); return; }
    stage.classList.add('entering');
    board.hidden = true;
    dialog.hidden = true;
    const from = photo.getBoundingClientRect();
    const box = stage.getBoundingClientRect();
    const zoom = picture(photo.src, 'zoom');
    Object.assign(zoom.style, { left: `${from.left - box.left}px`, top: `${from.top - box.top}px`, width: `${from.width}px`, height: `${from.height}px` });
    stage.append(zoom);
    requestAnimationFrame(() => requestAnimationFrame(() => {
      Object.assign(zoom.style, { left: '0px', top: '0px', width: `${box.width}px`, height: `${box.height}px` });
      zoom.classList.add('in');
    }));
    setTimeout(resolve, 750);
  });
  // 사진 → 재판 사이의 아이캐치: 사진은 사건 당시, 재판은 그 뒤라서 시간이 흐른 것을 한 장면 보여 준다.
  // 법정 상식 하나를 무작위로 띄우고, 정해진 시간이 지나거나 클릭 · Space · Enter면 끝난다
  wrap.interlude = (facts = []) => new Promise((resolve) => {
    const card = element('div', 'eyecatch');
    card.setAttribute('role', 'status');
    const brand = element('p', 'ec-brand');
    brand.append('내', element('span', 'law', 'Law'), '남불');
    const fact = element('div', 'ec-fact');
    if (facts.length) fact.append(element('p', 'ec-k', '법정 상식'), element('p', '', facts[Math.floor(Math.random() * facts.length)]));
    const bar = element('div', 'ec-bar');
    bar.append(element('i'));
    const cast = element('div', 'ec-cast');
    cast.append(picture(imageOf('naero', 'role:defense'), 'ec-n'), picture(imageOf('nambul', 'role:prosecutor'), 'ec-b'));
    card.append(cast, brand, element('p', 'ec-title', '그로부터 몇 달 뒤, 법정'), element('p', 'ec-sub', '기사 속 사건은 지나갔고, 이제 그 사건의 재판이 열려요.'), fact, bar, element('p', 'ec-skip', '클릭하면 바로 넘어가요'));
    stage.append(card);
    let timer = null;
    const done = () => {
      clearTimeout(timer);
      card.removeEventListener('click', done);
      document.removeEventListener('keydown', onKey);
      resolve();
    };
    const onKey = (event) => { if (event.key === ' ' || event.key === 'Enter') { event.preventDefault(); done(); } };
    card.addEventListener('click', done);
    document.addEventListener('keydown', onKey);
    timer = setTimeout(done, 3600);
  });
  // 재판(3막)을 처음부터 시작할 때: 사진 속에서 법정이 나타나는 느낌으로 한 번 들어온다
  if (index === 0 && steps[0].scene === 'court' && !reducedMotion()) stage.classList.add('arrive');

  render();
  return wrap;
}
