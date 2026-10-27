import { renderProgressSidebar } from '../review/progressSidebar.js';
import { renderStage } from '../../components/stage.js';
import { SCRIPTS } from '../../content/scripts.js';
import { restartMock, jumpActMock } from '../../mocks/mockStageButtons.js';
import courtFacts from '../../content/courtFacts.json';
import { caseNewspaper, paperFromCase } from '../../components/newspaperFront.js';
import { commonCategoryOf, evidencePhotosFromPaper, fetchCasePaper } from '../../utils/newspaper.js';
import { reducedMotion } from '../../utils/motion.js';

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function button(text, action, className = 'secondary-button') {
  const node = element('button', className, text);
  node.type = 'button';
  node.addEventListener('click', action);
  return node;
}

// 사건 번호(legal_case.id)는 DB마다 다를 수 있어 번호만으로 고르지 않는다. 대본의 제목이 API 제목과 같을 때만 무대를 쓴다
export function scriptFor(caseId, title) {
  const candidate = SCRIPTS[caseId];
  return candidate?.title === title ? candidate : undefined;
}

// 체험 화면 윗줄: ← 목록 · 사건 제목 · 죄명 · 진행(1 / 6). S-03과 S-20(대본 있는 사건)이 같은 줄을 쓴다
export function caseTopbar(info, navigate) {
  const header = element('header', 'case-topbar');
  const progress = element('div', 'top-progress');
  const meter = element('progress');
  meter.max = 6;
  meter.value = 1;
  meter.setAttribute('aria-label', '진행 상황');
  progress.append(meter, element('span', '', '1 / 6'));
  header.append(button('← 목록', () => navigate('list'), 'back-button'),
    element('strong', 'case-title', info.title),
    ...(info.chargeName ? [element('span', 'case-badge', info.chargeName)] : []), progress);
  return header;
}

// 1막 무대를 화면에 놓는다. S-03과 S-20이 같은 짜임으로 그려 두 화면이 똑같이 보이게 한다(FE-32).
// 화면 전체가 S-02와 같은 신문 지면이다: 책상(펠트) 위에 지면 한 장, 그 위에 무대(캐릭터 · 대사창은 화면 아래 고정)
export function mountStagePage(root, { stage }) {
  root.className = 'case-list-page case-paper-page overview-page';
  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  root.replaceChildren(desk, stage);
}

// 모든 사건 1막의 첫 대사(내로가 말을 건다). 사건마다의 다음 대사는 대본이 잇는다
const INTRO_STEP = { say: 'naero', text: '오늘 신문에 실린 사건이에요. 기사에 나온 내용만 같이 읽어 볼게요.', focus: 'head' };

// 1막 무대(흐름 목업). 1막 엽서 보드는 사전 판단 폼이고, 폼은 API 4 응답(형량 구간)이 오면 setOverview로 채운다.
// parked: S-20(체험 전) — 지면만 보이고 캐릭터 · 대사는 stage.release()로 시작한다. earRight: 지면 오른쪽 귀(S-20 "이어서 하기")
// paper: 지면에 실을 기사(paperFromCase — 메인 · S-02와 같은 사건 데이터). 없으면 대본 지면
// evidencePhotos: 증거조사 두 칸 사진(FE-64, evidencePhotosFromPaper — API 17에서 연결). 없으면 대본의 고정 사진(script.evidencePhotos)을 그대로 쓴다
// prompt: S-20에서 하던 · 마친 체험을 "시작하기"로 열 때 맨 앞에서 내로가 묻는 장면 { text, choices }
export function caseStage({ caseId, api, navigate, script, info, paper = script.paper, evidencePhotos, parked = false, earRight, prompt }) {
  const cards = {};
  const first = prompt ? [{ say: 'naero', text: prompt.text, choices: 'prompt', focus: 'all' }, INTRO_STEP] : [INTRO_STEP];
  const acts = { ...script.acts, '1a': { ...script.acts['1a'], steps: [...first, ...script.acts['1a'].steps] } };
  const sheet = caseNewspaper({ paper, info, navigate, ...(earRight ? { earRight } : {}) });
  const stage = renderStage({
    script: { ...script, acts, commonCategory: commonCategoryOf(info), ...(evidencePhotos ? { evidencePhotos } : {}) }, order: ['1a', '1b'], cards, inputCards: ['preJudgment'], sheet, layout: 'paper', parked,
    choices: prompt ? { prompt: prompt.choices } : {},
    onRestart: restartMock(caseId), onJump: jumpActMock(caseId),
  });
  let submitting = false;
  let labels = {};

  async function submit(rangeOptionId, trigger, notice) {
    if (submitting) return;
    submitting = true;
    trigger.disabled = true;
    trigger.setAttribute('aria-busy', 'true');
    notice.textContent = '';
    try {
      // MVP는 작용 요소(factorIds)를 보내지 않는다. (REQ-093 확장)
      await api.postPreJudgment(caseId, { rangeOptionId });
      await postmark(); // 접수 도장을 찍고 잠깐 보여 준 뒤 넘어간다(보낸 순간이 남게)
      // 제출 뒤 사진 속으로 들어가는 연출을 보여 주고 S-04로 간다
      if (stage.isConnected) await stage.enterPhoto();
      if (stage.isConnected) await stage.interlude(courtFacts);
      if (stage.isConnected) navigate('review', { caseId });
    } catch (error) {
      if (!stage.isConnected) return;
      trigger.disabled = false;
      trigger.removeAttribute('aria-busy');
      notice.textContent = '제출하지 못했어요. 잠시 후 다시 시도해 주세요.';
    } finally {
      submitting = false;
    }
  }

  // 보낸 엽서에 접수 도장(소인)을 찍는다. 움직임 줄이기면 도장만 바로 찍히고 기다리지 않는다
  let sendingCard = null;
  async function postmark() {
    if (!sendingCard) return;
    const mark = element('div', 'postcard-postmark');
    mark.setAttribute('aria-hidden', 'true');
    mark.append(element('b', '', '접수 완료'), element('small', '', '내Law남불 신문 편집부'));
    sendingCard.append(mark);
    sendingCard.classList.add('is-sent');
    if (!reducedMotion()) await new Promise((resolve) => setTimeout(resolve, 900));
  }

  // 첫인상 엽서 머리(제목 · 받는 곳 · 우표)
  function postcardHead() {
    const head = element('div', 'postcard-head');
    const title = element('div');
    title.append(element('h2', '', '첫인상 엽서'), element('p', '', '받는 곳: 내Law남불 신문 편집부 · 기사만 읽고 남기는 첫인상'));
    const stamp = element('span', 'postcard-stamp', '내Law\n남불');
    stamp.setAttribute('aria-hidden', 'true');
    stamp.style.whiteSpace = 'pre-line';
    head.append(title, stamp);
    return head;
  }

  // 이미 사전 판단을 낸 사건을 1막부터 다시 볼 때(S-20 "시작하기"): 엽서 차례에 "이미 보낸 엽서"를 보여 주고 저장된 단계로 잇는다.
  // MVP는 사건당 1회 체험 · 판단 수정 불가(IA 결정 5)라 다시 고르게 하지 않는다.
  // 보낸 형량(sent)은 완료한 사건만 받는다. 진행 중에는 최종 판단 전이라 사전 형량을 다시 보여 주지 않고 제출 완료 안내만 한다(BE-77)
  function setAlreadySent(onContinue, sent) {
    const card = element('div', 'postcard');
    const go = element('button', 'primary-button', '이어서 하기 ▶');
    go.type = 'button';
    go.addEventListener('click', onContinue);
    const actions = element('div', 'section-actions');
    actions.append(go);
    const written = element('div', 'postcard-sent');
    if (sent) written.append(element('p', 'postcard-sent-q', '이 사건, 어느 정도의 형량이 맞다고 생각하나요?'), element('p', 'postcard-sent-a', sent));
    card.append(postcardHead(), ...(sent ? [written] : []), element('p', 'guide-box', '이 엽서는 이미 보냈어요. 보낸 판단은 바꿀 수 없어요. 남긴 단계부터 이어서 볼게요.'), actions);
    cards.preJudgment = card;
    stage.refresh('preJudgment');
  }

  function setOverview({ rangeOptions }) {
    labels = Object.fromEntries(rangeOptions.map(({ rangeOptionId, label }) => [rangeOptionId, label]));
    // 첫인상 엽서(신문 지면 무대의 팝업): 엽서 머리(제목 · 받는 곳 · 우표) + 안내 + 형량 고르기.
    // 되돌릴 수 없는 판단이라 두 박자: 고르기 → 엽서 안 확인("보낼까요?") → 보내기(접수 도장)
    const card = element('div', 'postcard');
    sendingCard = card;
    const drawForm = (selectedId) => {
      card.replaceChildren(postcardHead(), preJudgmentGuide(), preJudgmentForm(rangeOptions, askConfirm, { submitLabel: '이 판단으로 정하기 ▶', pendingLabel: '형량을 하나 골라 주세요', selectedId }));
    };
    function askConfirm(rangeOptionId) {
      const written = element('div', 'postcard-sent');
      written.append(element('p', 'postcard-sent-q', '이 사건, 어느 정도의 형량이 맞다고 생각하나요?'), element('p', 'postcard-sent-a', labels[rangeOptionId]));
      const notice = element('p', 'submit-error');
      notice.setAttribute('role', 'alert');
      const back = element('button', 'secondary-button', '다시 고르기');
      back.type = 'button';
      back.addEventListener('click', () => { drawForm(rangeOptionId); card.querySelector('input:checked')?.focus(); });
      const send = element('button', 'primary-button', '엽서 보내고 재판 보러 가기 ▶');
      send.type = 'button';
      send.addEventListener('click', () => submit(rangeOptionId, send, notice).then(() => { back.disabled = send.disabled; }));
      const actions = element('div', 'section-actions');
      actions.append(back, send);
      card.replaceChildren(postcardHead(), written, element('p', 'submit-warning', '이 판단으로 보낼까요? 보내면 바꿀 수 없어요.'), notice, actions);
      send.focus();
    }
    drawForm();
    cards.preJudgment = card;
    stage.refresh('preJudgment');
  }
  return { stage, setOverview, setAlreadySent };
}

const preJudgmentGuide = () => element('p', 'guide-box', '지금은 뉴스에서 볼 수 있는 정도의 정보만 있어요. 기사로 이 사건을 접했다고 생각하고 판단해 보세요.');

// pendingLabel: 아직 고르지 않았을 때 버튼 글자(엽서: "형량을 하나 골라 주세요"). 없으면 처음부터 submitLabel
function preJudgmentForm(rangeOptions, submit, { submitLabel = '판단 제출하고 사건 자세히 보기', pendingLabel, selectedId } = {}) {
  const form = element('form', 'pre-judgment-form');
  form.noValidate = true;
  const fieldset = element('fieldset');
  fieldset.append(element('legend', '', '이 사건, 어느 정도의 형량이 맞다고 생각하나요?'));
  const options = element('div', 'range-options');
  options.style.setProperty('--rows', String(Math.ceil(rangeOptions.length / 2))); // 두 열일 때 위 → 아래로 읽게(가벼운 형량 → 무거운 형량)
  const warning = element('p', 'submit-warning', '제출하면 이 판단은 바꿀 수 없어요.');
  const notice = element('p', 'submit-error');
  notice.setAttribute('role', 'alert');
  const submitButton = element('button', 'primary-button', pendingLabel ?? submitLabel);
  const ready = () => { submitButton.disabled = false; submitButton.textContent = submitLabel; };
  submitButton.type = 'submit';
  submitButton.disabled = true;
  rangeOptions.forEach(({ rangeOptionId, label }) => {
    const item = element('label', 'range-option');
    const input = element('input');
    input.type = 'radio';
    input.name = 'rangeOptionId';
    input.value = String(rangeOptionId);
    input.addEventListener('change', ready);
    if (rangeOptionId === selectedId) { input.checked = true; ready(); }
    item.append(input, element('span', '', label));
    options.append(item);
  });
  fieldset.append(options);
  const actions = element('div', 'section-actions');
  actions.append(submitButton);
  form.append(fieldset, warning, notice, actions);
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const checked = form.querySelector('input[name="rangeOptionId"]:checked');
    if (checked) submit(Number(checked.value), submitButton, notice);
  });
  return form;
}

// S-03 첫인상(사건 개요 · 사전 판단)
export async function renderOverviewPage(container, { caseId, api, navigate }) {
  const root = document.createElement('div');
  root.className = 'review-page overview-page';
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  let loading = false;
  let submitting = false;

  // 상태 불일치(INVALID_STATE, 예: 이미 제출함)는 withErrorRedirect가 현재 상태의 화면으로 먼저 이동시킨다
  // (app/errorRedirect.js, API 명세 1-6 · REQ-103). 이동한 뒤에는 active()가 false라 아래 안내를 그리지 않는다.
  function showError(error) {
    if (!active()) return;
    const box = element('section', 'message-panel');
    box.setAttribute('role', 'alert');
    if (error?.code === 'CASE_NOT_FOUND') {
      box.append(element('p', '', '사건을 찾을 수 없어요.'), button('사건 목록으로', () => navigate('list')));
    } else if (error?.code === 'INVALID_STATE') {
      // 여기 오는 것은 래퍼가 이동하지 못한 경우다 — 화면 표(screenByStatus)에 없는 상태값이거나,
      // 이미 이 화면이라 라우터가 이동을 건너뛴 경우. 다시 시도해도 같은 에러라 S-05 ~ S-08처럼 안내만 둔다.
      box.append(element('p', '', `지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`), button('사건 목록으로', () => navigate('list')));
      console.info('현재 상태:', error.currentStatus);
    } else {
      box.append(element('p', '', '정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'), button('다시 시도', load));
    }
    root.replaceChildren(box);
  }
  // 대본이 없는 사건(MVP 화면)의 제출: 제출하면 S-04로 간다
  async function submit(rangeOptionId, trigger, notice) {
    if (submitting) return;
    submitting = true;
    trigger.disabled = true;
    trigger.setAttribute('aria-busy', 'true');
    notice.textContent = '';
    try {
      await api.postPreJudgment(caseId, { rangeOptionId });
      if (active()) navigate('review', { caseId });
    } catch (error) {
      if (!active()) return;
      trigger.disabled = false;
      trigger.removeAttribute('aria-busy');
      notice.textContent = '제출하지 못했어요. 잠시 후 다시 시도해 주세요.';
    } finally {
      submitting = false;
    }
  }
  function draw(data, detail, casePaper) {
    const { case: info, rangeOptions } = data;
    const script = scriptFor(caseId, info.title);
    if (script) {
      // 1막 무대(신문 지면). 진행 표시는 지면 탭 줄의 막 칩이 맡는다. S-20(대본 있는 사건)과 같은 화면이다
      const { stage, setOverview } = caseStage({
        caseId, api, navigate, script, info: { ...(detail ?? {}), ...info, caseId },
        paper: paperFromCase(detail, casePaper, script.paper), evidencePhotos: evidencePhotosFromPaper(casePaper),
      });
      setOverview(data);
      mountStagePage(root, { stage });
      return;
    }
    const main = element('div', 'review-main');
    main.append(element('h1', 'page-heading', '첫인상'));
    const overview = element('section', 'overview-box');
    const facts = element('dl', 'overview-facts');
    [['죄명', info.chargeName], ['범죄 분류', info.crimeCategoryLabel]].forEach(([label, value]) => {
      const row = element('div');
      row.append(element('dt', '', label), element('dd', '', value));
      facts.append(row);
    });
    overview.append(element('h2', '', '사건 개요'), facts, element('p', 'overview-text', info.overview));
    main.append(overview, preJudgmentGuide(), preJudgmentForm(rangeOptions, submit));
    const layout = element('main', 'review-layout');
    layout.append(main);
    renderProgressSidebar(layout, { lastReviewedStep: 0, openStep: 1 });
    root.replaceChildren(caseTopbar(info, navigate), layout);
  }
  async function load() {
    if (loading || !active()) return;
    loading = true;
    const message = element('p', 'message-panel', '사건 정보를 불러오는 중…');
    message.setAttribute('role', 'status');
    root.replaceChildren(message);
    try {
      const data = await api.getOverview(caseId);
      // 대본이 있는 사건은 지면 기사 · 증거조사 사진을 S-20과 같은 사건 데이터(API 16 · 17)로 싣는다. 못 받으면 대본 지면으로(FE-64)
      const hasScript = scriptFor(caseId, data.case.title);
      const [detail, casePaper] = hasScript
        ? await Promise.all([api.getCaseDetail(caseId).catch(() => null), fetchCasePaper(api, caseId)])
        : [null, null];
      if (active()) draw(data, detail, casePaper);
    } catch (error) { showError(error); }
    finally { loading = false; }
  }
  await load();
}
