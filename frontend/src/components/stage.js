// 판결 체험 무대 (REQ-089, FE-27). 내로 · 남불이 대본(content/case-*/script.json)을 따라 장면을 진행하고,
// 판단에 필요한 내용(사건 개요 · 입력 폼 등)은 화면이 넘긴 카드로 띄운다. 기준: docs/user-flow.md 3-3, PRODUCT.md.
// 화면 주소 · 서버 진행 상태는 건드리지 않는다. 막은 화면이 고른 것만 보여 준다(S-03 = 1 · 2막).

export const ACT_NAMES = ['신문 속 사건', '첫인상 판단', '재판', '판결 전 정리', '당신이 판사', 'AI 판사의 합의', '실제 판결', '세 판결 비교'];
const NAMES = { naero: '내로', nambul: '남불' };
// shortcut: 캐릭터 컷은 원본 이미지 레포 PNG를 직접 읽는다, 웹 webp 변환(WBS 7.6.8) 때 public 경로로 옮긴다
const CUTS = 'https://raw.githubusercontent.com/peanutboi710/NaeLawNamBul_Images/main/character/cuts/';
const imageOf = (who, look) => (look.startsWith('role:')
  ? `/images/characters/roles/${who}_${look.slice(5)}.webp`
  : `${CUTS}${who}/base/${who}_cut_${look}.png`);
const iconOf = (who) => `${CUTS}${who}/icon/${who}_icon_face-neutral.png`;

// 대본의 막들을 한 줄로 편다. 각 장면은 앞 장면의 모습(look) · 이름표(tags)를 이어받는다.
export function flatten(acts, order) {
  const steps = [];
  order.forEach((actId) => {
    const act = acts[actId];
    let look = { ...act.look };
    let tags = { naero: '', nambul: '' };
    act.steps.forEach((step) => {
      look = { ...look, ...step.look };
      tags = { ...tags, ...step.tags };
      steps.push({ ...step, actId, scene: act.scene ?? 'news', look, tags });
    });
  });
  return steps;
}

// 대사 건너뛰기: 같은 막에서 다음 카드 장면, 없으면 막의 마지막 장면으로 간다.
export function skipTarget(steps, index) {
  const { actId } = steps[index];
  for (let i = index + 1; i < steps.length; i += 1) {
    if (steps[i].actId !== actId) return i - 1;
    if (steps[i].card) return i;
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

// script: 사건 대본, order: 이 화면이 보여 줄 막 번호, cards: { 이름: 화면이 만든 요소 }, inputCards: 입력 카드 이름(연출을 멈추고 카드만)
export function renderStage({ script, order, cards, inputCards = [] }) {
  const steps = flatten(script.acts, order);
  let index = 0;
  let typing = null;
  let lastRoles = '';

  const stage = element('section', 'stage');
  stage.setAttribute('aria-label', '판결 체험');
  const acts = element('ol', 'stage-acts');
  ACT_NAMES.forEach((name, i) => acts.append(element('li', '', `${i + 1}막 ${name}`)));

  const scene = element('div', 'stage-scene');
  const figures = {};
  const chips = {};
  ['naero', 'nambul'].forEach((who) => {
    const figure = element('img', `stage-figure is-${who}`);
    figure.alt = NAMES[who];
    figure.addEventListener('error', () => { figure.hidden = true; });
    figures[who] = figure;
    chips[who] = element('span', `stage-tag is-${who}`);
    scene.append(figure, chips[who]);
  });
  const caption = element('p', 'stage-caption');
  const flash = element('div', 'stage-flash');
  scene.append(caption, flash);

  const dialog = element('div', 'stage-dialog');
  const speaker = element('p', 'stage-speaker');
  const line = element('p', 'stage-line');
  line.setAttribute('aria-hidden', 'true'); // 타이핑 중인 글자는 읽지 않고, 아래 live 영역이 문장 전체를 한 번 읽는다
  const live = element('p', 'visually-hidden');
  live.setAttribute('aria-live', 'polite');
  const controls = element('div', 'stage-controls');
  const prev = element('button', 'secondary-button', '이전');
  const next = element('button', 'primary-button', '다음');
  const skip = element('button', 'stage-skip', '대사 건너뛰기');
  [prev, next, skip].forEach((b) => { b.type = 'button'; });
  controls.append(skip, prev, next);
  dialog.append(speaker, line, live, controls);

  const cardSlot = element('div', 'stage-card');
  cardSlot.tabIndex = -1;
  stage.append(acts, scene, dialog, cardSlot);

  function finishTyping() {
    clearInterval(typing);
    typing = null;
    line.textContent = steps[index].text ?? '';
  }
  function type(text) {
    clearInterval(typing);
    if (reducedMotion()) { line.textContent = text; return; }
    let shown = 0;
    line.textContent = '';
    typing = setInterval(() => {
      shown += 2;
      line.textContent = text.slice(0, shown);
      if (shown >= text.length) finishTyping();
    }, 24);
  }

  function render() {
    const step = steps[index];
    const input = inputCards.includes(step.card);
    stage.classList.toggle('is-input', input);
    stage.classList.toggle('is-dark', Boolean(step.blackout));
    stage.classList.toggle('is-system', Boolean(step.system));
    [...acts.children].forEach((item, i) => {
      const current = String(i + 1) === step.actId;
      item.classList.toggle('is-current', current);
      if (current) item.setAttribute('aria-current', 'step'); else item.removeAttribute('aria-current');
    });

    ['naero', 'nambul'].forEach((who) => {
      const look = step.look[who];
      figures[who].hidden = false;
      figures[who].src = imageOf(who, look);
      figures[who].classList.toggle('is-role', look.startsWith('role:'));
      figures[who].classList.toggle('is-quiet', Boolean(step.say) && step.say !== who);
      chips[who].textContent = step.tags[who];
      chips[who].hidden = !step.tags[who];
    });
    const roles = `${step.look.naero}|${step.look.nambul}`;
    if (lastRoles && roles !== lastRoles && roles.includes('role:') !== lastRoles.includes('role:') && !reducedMotion()) {
      flash.classList.remove('is-on');
      void flash.offsetWidth; // 같은 애니메이션을 다시 시작하려고 다시 그리게 한다
      flash.classList.add('is-on');
    }
    lastRoles = roles;

    caption.textContent = step.blackout ?? step.system ?? '';
    caption.hidden = !caption.textContent;

    const text = step.text ?? '';
    // 글이 없는 장면(암전 · 시스템 캡션)에도 이전 · 다음 버튼은 남긴다 — 버튼이 없으면 장면 클릭 · 키보드 말고는 넘길 방법이 없다
    dialog.hidden = input;
    dialog.classList.toggle('is-bare', !text);
    line.hidden = !text;
    if (step.say) {
      speaker.replaceChildren();
      const icon = element('img');
      icon.src = iconOf(step.say);
      icon.alt = '';
      icon.addEventListener('error', () => icon.remove(), { once: true });
      speaker.append(icon, element('strong', '', NAMES[step.say]), element('span', '', step.role ?? ''));
    }
    speaker.hidden = !step.say;
    live.textContent = text || (step.blackout ?? step.system ?? '');
    type(text);

    scene.dataset.scene = step.scene;
    const card = step.card ? cards[step.card] : null;
    cardSlot.replaceChildren(...(card ? [card] : []));
    cardSlot.hidden = !card;
    prev.disabled = index === 0;
    next.disabled = input || index === steps.length - 1;
    skip.hidden = input || Boolean(steps[index].card) || skipTarget(steps, index) === index;
    if (input) cardSlot.focus();
  }

  function go(delta) {
    if (delta > 0 && typing) { finishTyping(); return; }
    const target = index + delta;
    if (target < 0 || target >= steps.length) return;
    if (delta > 0 && inputCards.includes(steps[index].card)) return; // 입력 카드는 화면의 제출로만 넘어간다
    index = target;
    render();
  }
  prev.addEventListener('click', () => go(-1));
  next.addEventListener('click', () => go(1));
  skip.addEventListener('click', () => { finishTyping(); index = skipTarget(steps, index); render(); });
  scene.addEventListener('click', (event) => { if (!event.target.closest('.stage-card')) go(1); });

  function onKey(event) {
    if (!stage.isConnected) { document.removeEventListener('keydown', onKey); return; }
    if (event.target.closest('input, textarea, select, .stage-card')) return;
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

  render();
  return stage;
}
