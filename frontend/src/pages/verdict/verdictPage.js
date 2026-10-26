import { renderLawInfoBox } from '../../components/lawInfoBox.js';
import { renderRecommendedRangeBar, formatMonths } from '../../components/recommendedRangeBar.js';
import { renderStage } from '../../components/stage.js';
import { SCRIPTS } from '../../content/scripts.js';
import { restartMock, jumpActMock } from '../../mocks/mockStageButtons.js';
import { getDraft, saveDraft, clearDraft } from './verdictDraft.js';
import { markAiRevealIntro } from '../ai/aiRevealIntro.js';
import { reducedMotion } from '../../utils/motion.js';
import { getBookmarkColor, selectedBookmarkFace } from '../../utils/bookmarkCharacter.js';

const penaltyLabels = { DEATH: '사형', LIFE: '무기징역', PRISON: '징역', FINE: '벌금' };
// 해금 책갈피의 얼굴(내로)
const SIGN_MS = 900; // 서명 획이 다 그려지는 시간(verdict.css .sign-ink와 같게). 그다음 확정을 보낸다
const FILED_MS = 900; // 확정된 뒤 "판결 접수" 도장을 보여 주는 시간(verdict.css .sign-filed). 그다음 AI 판결로 넘어간다(FE-97)

// 원문을 유지해 빈 칸, 0, 잘못 입력한 값을 구별한다.
function parseInteger(raw) {
  const text = raw.trim();
  if (!text) return { value: null, invalid: false };
  const value = Number(text);
  return /^\d+$/.test(text) && Number.isSafeInteger(value)
    ? { value, invalid: false } : { value: null, invalid: true };
}

function parsePeriod(years, months) {
  const year = parseInteger(years);
  const month = parseInteger(months);
  if (year.invalid || month.invalid) return { value: null, invalid: true };
  if (year.value === null && month.value === null) return { value: null, invalid: false };
  // 개월 칸에 12 이상을 적으면 그 값이 형량 전체다(예: 년 1 · 개월 15 → 15개월 = 1년 3개월). 이미 적힌 년 칸은 더하지 않는다.
  // 칸을 벗어나면 년 · 개월로 나눠 다시 적는다(input의 change)
  const value = month.value >= 12 ? month.value : (year.value ?? 0) * 12 + (month.value ?? 0);
  return Number.isSafeInteger(value) ? { value, invalid: false } : { value: null, invalid: true };
}

function formatMoney(amount) {
  const units = [[100000000, '억'], [10000, '만']];
  const parts = [];
  let rest = amount;
  for (const [unit, label] of units) {
    const count = Math.floor(rest / unit);
    if (count) parts.push(`${count.toLocaleString('ko-KR')}${label}`);
    rest %= unit;
  }
  if (rest || !parts.length) parts.push(rest.toLocaleString('ko-KR'));
  return `${parts.join(' ')} 원`;
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function picture(src) {
  const image = element('img');
  image.src = src;
  image.alt = '';
  image.addEventListener('error', () => image.remove(), { once: true });
  return image;
}

function button(text, action, className = 'secondary-button') {
  const node = element('button', className, text);
  node.type = 'button';
  node.addEventListener('click', action);
  return node;
}

export async function renderVerdictPage(container, { caseId, caseHeader, api, navigate }) {
  const root = element('div', 'verdict-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  let loading = false;

  function showError(error) {
    if (!active()) return;
    const panel = element('section', 'message-panel');
    panel.setAttribute('role', 'alert');
    // 보통은 withErrorRedirect가 currentStatus에 맞는 화면으로 먼저 이동시킨다(app/errorRedirect.js).
    // 여기 오는 것은 래퍼가 이동하지 못한 경우다 — 화면 표(screenByStatus)에 없는 상태값이거나,
    // 이미 이 화면이라 라우터가 이동을 건너뛴 경우(예: 이 화면에서 보낸 요청이 지금 상태를 그대로 돌려줌).
    if (error?.code === 'INVALID_STATE') {
      panel.append(element('p', '', `지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`));
      console.info('현재 상태:', error.currentStatus);
    } else {
      panel.append(element('p', '', '정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'), button('다시 시도', load));
    }
    root.replaceChildren(panel);
  }

  function draw(form, review) {
    const draft = { reducing: false, reducedTo: null, ...(getDraft(caseId) ?? {
      penaltyType: '', prisonYears: '', prisonMonths: '', fineAmount: '',
      suspended: false, suspensionYears: '', suspensionMonths: '', factors: {},
    }) };
    const law = {
      statutoryPenaltyText: form.statutoryPenaltyText, allowedRanges: form.penaltyOptions,
      allowedRangeNote: form.allowedRangeNote, recommended: form.recommended,
    };
    const rule = form.suspensionRule;
    let serverError = null;
    let submitting = false; // 서명 뒤 확정 요청 중: 모든 입력을 잠근다
    const inputs = {};
    const factorButtons = [];
    const header = element('header', 'case-topbar');
    const back = button('사건 기록 다시 보기', () => navigate('review'), 'back-button');
    const caseTitle = element('strong', 'case-title', `판결 입력 · ${caseHeader.title}`);
    caseTitle.setAttribute('role', 'heading'); // 쪽(탭)마다 보이는 h1이 없던 것을 쪽 밖 머리글이 맡는다(FE-97). 모양은 그대로
    caseTitle.setAttribute('aria-level', '1');
    header.append(caseTitle, back);
    // 파일철(FE-43 · FE-48): 위로 튀어나온 색인 탭 다섯 장(사건 요약 → 참고 정보 → 판단 요소 → 최종 판결 → 확정). 한 번에 한 장만 보인다.
    // 다음 장은 종이 오른쪽 아래 말린 모서리로만 넘기고, 탭으로는 이미 펼쳐 본 장까지만 돌아갈 수 있다. 확정은 마지막 장의 서명으로 한다
    const layout = element('main', 'verdict-layout verdict-folder');
    const main = element('section', 'verdict-main folder-sheet');
    main.append(element('h2', 'folder-title', '당신의 판결'), element('p', 'verdict-intro muted', '지금까지 확인한 사건 정보와 양형기준을 참고해 판결해 주세요.'));
    // 형량 칸(공통): 징역이면 "징역 [ ]년 [ ]개월", 벌금이면 "벌금 [ ]만원"으로 바뀐다. 징역은 범위 막대 바로 위, 벌금은 벌금 슬라이더 위에 둔다
    const entry = element('div', 'sentence-entry');
    const entryType = element('strong', 'sentence-entry-type');
    entry.append(entryType);
    const controls = element('fieldset', 'verdict-controls');
    const controlsLegend = element('legend', 'visually-hidden', '판결 입력');
    controls.append(controlsLegend);

    function group(title) {
      const section = element('fieldset', 'verdict-group');
      section.append(element('legend', '', title));
      controls.append(section);
      return section;
    }
    function input(parent, key, label, unit, descriptionId) {
      const wrapper = element('label', 'number-field');
      const name = element('span', 'visually-hidden', label);
      const field = element('input');
      field.type = 'text';
      field.inputMode = 'numeric';
      field.autocomplete = 'off';
      field.name = key;
      field.value = draft[key];
      field.setAttribute('aria-describedby', descriptionId);
      field.addEventListener('input', () => {
        draft[key] = field.value;
        changed(key);
      });
      if (key === 'prisonMonths') field.addEventListener('change', carryMonths);
      const unitText = element('span', '', unit);
      unitText.setAttribute('aria-hidden', 'true'); // 이름("징역 년")에 단위가 이미 있어 "징역 년년"으로 읽히던 것을 막는다(FE-97)
      wrapper.append(name, field, unitText);
      parent.append(wrapper);
      inputs[key] = field;
    }
    // 징역 개월 칸에 12개월 이상을 적으면 년 칸 값은 버리고 개월 값만으로 년 · 개월을 다시 적는다(입력 중에는 그대로 두고 칸을 벗어날 때)
    function carryMonths() {
      const period = parsePeriod(draft.prisonYears, draft.prisonMonths);
      if (period.invalid || period.value === null || parseInteger(draft.prisonMonths).value < 12) return;
      draft.prisonYears = String(Math.floor(period.value / 12));
      draft.prisonMonths = String(period.value % 12);
      inputs.prisonYears.value = draft.prisonYears;
      inputs.prisonMonths.value = draft.prisonMonths;
      changed('prisonMonths');
    }
    function clearSuspension() {
      draft.suspended = false;
      draft.suspensionYears = '';
      draft.suspensionMonths = '';
    }
    function changed(key) {
      const sentenceKeys = ['penaltyType', 'reducing', 'reducedTo', 'prisonYears', 'prisonMonths', 'fineAmount'];
      if (serverError === 'OUT_OF_ALLOWED_RANGE' && sentenceKeys.includes(key)) serverError = null;
      if (serverError === 'INVALID_SUSPENSION'
        && [...sentenceKeys, 'suspended', 'suspensionYears', 'suspensionMonths'].includes(key)) serverError = null;
      update();
      saveDraft(caseId, draft);
    }

    function clearSentence() {
      for (const key of ['prisonYears', 'prisonMonths', 'fineAmount']) {
        draft[key] = '';
        inputs[key].value = '';
      }
      clearSuspension();
    }

    function finalPenalty() {
      return draft.reducing ? draft.reducedTo : draft.penaltyType;
    }

    const penaltyGroup = group('1. 형벌 종류');
    const radios = element('div', 'penalty-options');
    for (const option of form.penaltyOptions) {
      const label = element('label', 'penalty-option');
      const radio = element('input');
      radio.type = 'radio';
      radio.name = `verdict-penalty-${caseId}`;
      radio.value = option.penaltyType;
      radio.checked = draft.penaltyType === option.penaltyType;
      radio.addEventListener('change', () => {
        if (!radio.checked) return;
        draft.penaltyType = option.penaltyType;
        draft.reducing = false;
        draft.reducedTo = null;
        clearSentence();
        changed('penaltyType');
      });
      label.append(radio, element('span', '', penaltyLabels[option.penaltyType]));
      radios.append(label);
    }
    penaltyGroup.append(radios);

    const reductionArea = element('div', 'reduction-area');
    const reductionLabel = element('label', 'reduction-toggle');
    const reductionCheck = element('input');
    reductionCheck.type = 'checkbox';
    reductionCheck.addEventListener('change', () => {
      draft.reducing = reductionCheck.checked;
      const choices = form.penaltyOptions.find((item) => item.penaltyType === draft.penaltyType)?.reducibleTo ?? [];
      draft.reducedTo = draft.reducing && choices.length === 1 ? choices[0] : null;
      clearSentence();
      changed('reducing');
    });
    reductionLabel.append(reductionCheck, element('span', '', '감경해서 선고하기'));
    const reductionChoices = element('fieldset', 'reduction-choices');
    reductionChoices.append(element('legend', 'visually-hidden', '감경 후 형벌'));
    const reductionRadios = [];
    for (const type of [...new Set(form.penaltyOptions.flatMap((option) => option.reducibleTo ?? []))]) {
      const label = element('label', 'reduction-option');
      const radio = element('input');
      radio.type = 'radio';
      radio.name = `verdict-reduced-${caseId}`;
      radio.value = type;
      radio.addEventListener('change', () => {
        if (!radio.checked) return;
        draft.reducedTo = type;
        clearSentence();
        changed('reducedTo');
      });
      label.append(radio, element('span', '', `${penaltyLabels[type]}으로`));
      reductionChoices.append(label);
      reductionRadios.push({ type, label, radio });
    }
    // 감경 줄은 늘 보인다: 감경할 수 없는 형벌(징역 · 벌금 · 고르기 전)이면 체크박스 · 감경 후 형벌이 회색으로 눌리지 않는다
    reductionArea.append(reductionLabel, element('p', 'muted', '판사는 참작할 사정이 있으면 형을 줄여서 선고할 수 있어요(작량감경).'), reductionChoices);
    penaltyGroup.append(reductionArea);

    const sentenceGroup = group('2. 형량 · 금액');
    const prisonFields = element('div', 'number-fields');
    const fineFields = element('div', 'number-fields');
    const sentenceError = element('p', 'field-error');
    sentenceError.id = `verdict-${caseId}-sentence-error`;
    sentenceError.setAttribute('aria-live', 'polite');
    input(prisonFields, 'prisonYears', '징역 년', '년', sentenceError.id);
    input(prisonFields, 'prisonMonths', '징역 개월', '개월', sentenceError.id);
    // 벌금은 만원 단위로 입력한다(draft.fineAmount = 만원 수, 서버에는 원으로 보낸다)
    input(fineFields, 'fineAmount', '벌금 금액(만원)', '만원', sentenceError.id);
    entry.append(prisonFields, fineFields);
    function option() {
      return form.penaltyOptions.find((item) => item.penaltyType === draft.penaltyType);
    }
    const prisonOption = form.penaltyOptions.find((item) => item.penaltyType === 'PRISON'); // 형량을 고를 수 없을 때 회색으로 보여 줄 막대
    // 범위 막대 = 형량 조절 막대(누르기 · 끌기 · 방향키). 슬라이더 역할(role="slider")은 막대(.range-track)에만 준다 —
    // 같은 상자 안의 형량 칸(입력)이 슬라이더의 일부로 읽히거나 방향키를 빼앗기지 않게.
    // 막대는 형벌 종류가 바뀔 때만 다시 그려지므로 누르기 · 끌기는 다시 그려지지 않는 바깥 상자(rangeSlider)가 받는다.
    // 징역은 개월 단위, 벌금은 원 단위(막대 10만 원 단위 · 500만 원 눈금에 잘 붙음 · 칸에는 1만 원 단위로 직접 입력)
    const FINE_TRACK_MAX = 30000000; // 벌금은 상한이 없어 막대를 3,000만 원까지만 그리고, 넘는 금액은 칸에 직접 입력한다
    const FINE_STEP = 100000;
    const FINE_SNAP = 5000000;
    const FINE_SNAP_RANGE = 300000;
    const FINE_REARM = 1000000;
    // 500만 원 붙임(자석): 붙은 자리(예 1,000만)에서 붙임 범위를 벗어나면(960만) 그 자리의 붙임을 끈다 —
    // 970 · 980 · … · 1,090만처럼 그 자리를 지나가도 붙지 않는다. 끈 자리에서 100만 원 이상 멀어지면(1,100만) 다시 켠다
    let snapAt = null; // 지금 붙어 있는 자리
    let snapOff = null; // 붙임을 잠시 끈 자리
    const rangeSlider = element('div', 'range-slider');
    function scale() {
      const { allowedMin, allowedMax } = option();
      return finalPenalty() === 'PRISON'
        ? { prison: true, min: allowedMin, max: allowedMax, trackMax: allowedMax, step: 1, page: 12 }
        : { prison: false, min: allowedMin, max: allowedMax ?? Infinity, trackMax: FINE_TRACK_MAX, step: FINE_STEP, page: FINE_SNAP };
    }
    function currentValue() {
      if (finalPenalty() === 'PRISON') return parsePeriod(draft.prisonYears, draft.prisonMonths).value;
      const manwon = parseInteger(draft.fineAmount).value;
      return manwon === null ? null : manwon * 10000;
    }
    // beyond: 벌금 막대를 끌다가 막대 끝(3,000만 원)을 넘어가면, 표시선은 끝에 두고 넘어간 거리만큼 금액을 계속 올린다.
    // 막대를 다시 누르면 누른 자리 금액으로 처음부터 다시 정한다(누를 때마다 새로 계산)
    function setValue(raw, snap = false, beyond = false) {
      const range = scale();
      let value = Math.round(raw / range.step) * range.step;
      if (snap && !range.prison) {
        if (snapOff !== null && Math.abs(raw - snapOff) >= FINE_REARM) snapOff = null;
        const near = Math.round(raw / FINE_SNAP) * FINE_SNAP;
        if (Math.abs(raw - near) < FINE_SNAP_RANGE) {
          if (near !== snapOff) { value = near; snapAt = near; snapOff = null; } // 500만 원 단위는 더 잘 잡히게
        } else if (snapAt !== null) {
          snapOff = snapAt;
          snapAt = null;
        }
      }
      value = Math.max(range.min, Math.min(range.max, beyond && !range.prison ? Infinity : range.trackMax, value));
      if (range.prison) {
        draft.prisonYears = String(Math.floor(value / 12));
        draft.prisonMonths = String(value % 12);
        inputs.prisonYears.value = draft.prisonYears;
        inputs.prisonMonths.value = draft.prisonMonths;
        changed('prisonYears');
      } else {
        draft.fineAmount = String(Math.round(value / 10000));
        inputs.fineAmount.value = draft.fineAmount;
        changed('fineAmount');
      }
    }
    function valueAt(clientX) {
      const rect = rangeSlider.querySelector('.range-track').getBoundingClientRect();
      return (clientX - rect.left) / rect.width * scale().trackMax;
    }
    const sliderTrack = () => rangeSlider.querySelector('.range-track[role="slider"]');
    rangeSlider.addEventListener('pointerdown', (event) => {
      const track = sliderTrack();
      if (!track || controls.disabled || event.target.closest('input, button')) return;
      const rect = track.getBoundingClientRect();
      if (event.clientY < rect.top - 14 || event.clientY > rect.bottom + 14) return; // 막대 근처를 누를 때만
      event.preventDefault();
      track.focus();
      rangeSlider.setPointerCapture(event.pointerId);
      setValue(valueAt(event.clientX), true, true);
    });
    rangeSlider.addEventListener('pointermove', (event) => {
      if (rangeSlider.hasPointerCapture(event.pointerId)) setValue(valueAt(event.clientX), true, true);
    });
    rangeSlider.addEventListener('keydown', (event) => {
      if (event.target !== sliderTrack() || controls.disabled) return; // 막대에 포커스가 있을 때만(형량 칸의 방향키는 칸이 쓴다)
      const range = scale();
      const now = currentValue() ?? range.min;
      const step = { ArrowRight: range.step, ArrowUp: range.step, ArrowLeft: -range.step, ArrowDown: -range.step, PageUp: range.page, PageDown: -range.page }[event.key];
      const target = step !== undefined ? now + step : event.key === 'Home' ? range.min : event.key === 'End' ? Math.min(range.max, range.trackMax) : null;
      if (target === null) return;
      event.preventDefault();
      setValue(target);
    });
    // 벌금 막대: 선고할 수 있는 범위 상자(징역의 범위 막대와 같은 모양). 눈금은 100만 원 작게 · 500만 원 중간 · 1,000만 원 크게
    function renderFineRange(container, min) {
      const box = element('section', 'recommended-range');
      const track = element('div', 'range-track fine-track');
      for (let amount = 1000000; amount < FINE_TRACK_MAX; amount += 1000000) {
        const size = amount % 10000000 === 0 ? 'major' : amount % 5000000 === 0 ? 'mid' : 'minor';
        const tick = element('span', `fine-tick ${size}`);
        tick.style.left = `${amount / FINE_TRACK_MAX * 100}%`;
        tick.setAttribute('aria-hidden', 'true');
        track.append(tick);
      }
      // 아래 글자는 1,000만 원 눈금 자리에 맞춘다
      const labels = element('div', 'fine-labels');
      labels.setAttribute('aria-hidden', 'true');
      ['0', '1,000만', '2,000만', '3,000만'].forEach((text, i) => {
        const label = element('span', '', text);
        label.style.left = `${i / 3 * 100}%`;
        labels.append(label);
      });
      // 징역 막대 상자와 같은 짜임(제목 · 범위 · 권고 범위 줄 · 눈금 막대 · 아래 안내)이라 상자 크기 · 막대 위치가 같다
      track.classList.add('range-track-with-ticks');
      box.append(element('h3', '', '선고할 수 있는 범위'), element('p', 'range-value', `벌금 ${formatMoney(min)} 이상`),
        element('p', 'range-recommendation', '양형기준 권고 범위는 징역에만 해당해요'),
        track, labels, element('p', 'range-lower-label', '막대는 3,000만 원까지'));
      container.append(box);
    }
    const rangeBox = element('div', 'verdict-range');
    const feedback = element('div', 'sentence-feedback');
    // 형량 칸 머리: 고른 형벌 이름 + 안내 두 줄. 형벌마다(고르기 전 · 사형 · 무기징역 · 징역 · 벌금) 같은 자리 · 같은 높이라 아래 막대가 움직이지 않는다
    const fixedPenalty = element('div', 'fixed-penalty penalty-head');
    rangeSlider.append(rangeBox);
    sentenceGroup.append(fixedPenalty, sentenceError, rangeSlider, feedback);
    // 범위 막대는 형벌 종류가 바뀔 때만 다시 그린다(입력할 때마다 그리면 막대 안 형량 칸의 입력 커서가 빠진다).
    // 입력값 표시선(손잡이)은 여기서 직접 옮긴다. 형량이 없으면(처음 진입) 최소값 자리에 둔다
    let rangeKey = null;
    const marker = element('span', 'range-marker');
    marker.setAttribute('aria-hidden', 'true');

    const suspensionArea = element('div', 'suspension-area');
    const suspensionLabel = element('label', 'suspension-toggle');
    const checkbox = element('input');
    checkbox.type = 'checkbox';
    checkbox.addEventListener('change', () => {
      draft.suspended = checkbox.checked;
      if (!draft.suspended) clearSuspension();
      changed('suspended');
    });
    suspensionLabel.append(checkbox, element('span', '', '집행유예'));
    // 집행유예 줄: 체크박스는 늘 보이고(붙일 수 없으면 회색), 조건은 같은 줄에 이어서 보여 준다
    const suspensionRow = element('div', 'suspension-row');
    // 집행유예 기간: 1년 단위로만 선고하므로 년 버튼(규칙의 최소 ~ 최대)
    const suspensionFields = element('div', 'suspension-years');
    suspensionFields.setAttribute('role', 'group');
    suspensionFields.setAttribute('aria-label', '집행유예 기간');
    const suspensionError = element('p', 'field-error');
    suspensionError.id = `verdict-${caseId}-suspension-error`;
    suspensionError.setAttribute('aria-live', 'polite');
    const yearButtons = [];
    for (let year = Math.ceil(rule.minMonths / 12); year <= Math.floor(rule.maxMonths / 12); year += 1) {
      const yearButton = button(`${year}년`, () => {
        draft.suspensionYears = String(year);
        draft.suspensionMonths = '0';
        changed('suspensionYears');
      }, 'factor-button');
      yearButton.setAttribute('aria-describedby', suspensionError.id);
      yearButtons.push({ yearButton, year });
      suspensionFields.append(yearButton);
    }
    const suspensionHint = element('p', 'muted suspension-hint');
    suspensionRow.append(suspensionLabel, suspensionHint);
    suspensionArea.append(suspensionRow, suspensionFields, suspensionError);
    sentenceGroup.append(suspensionArea);

    // 판단 요소는 따로 한 장(2페이지). 확정 확인 중에는 이 장의 버튼도 잠근다
    const factorPage = element('section', 'verdict-factors folder-sheet');
    const factorGroup = element('fieldset', 'verdict-controls verdict-group');
    factorGroup.append(element('legend', 'visually-hidden', '판단 요소'));
    factorPage.append(element('h2', 'folder-title', '판단 요소'), factorGroup);
    factorGroup.append(element('p', 'muted', '요소마다 형량을 무겁게 했으면 "무겁게", 가볍게 했으면 "가볍게"를 눌러 주세요. 처음에는 모두 "고려 안 함"이에요.'));
    const factorList = element('ul', 'factor-list');
    for (const factor of form.factors) {
      const row = element('li', 'factor-row');
      const label = element('span', 'factor-label', factor.label);
      label.id = `verdict-${caseId}-factor-${factor.factorId}`;
      const actions = element('div', 'factor-actions');
      actions.setAttribute('role', 'group');
      actions.setAttribute('aria-labelledby', label.id);
      // 무겁게 · 고려 안 함 · 가볍게 중 하나. 고려 안 함(NONE)은 서버에 보내지 않는다(요소를 고르지 않은 것)
      for (const [direction, text] of [['UP', '무겁게'], ['NONE', '고려 안 함'], ['DOWN', '가볍게']]) {
        const toggle = button(text, () => {
          if (direction === 'NONE') delete draft.factors[factor.factorId];
          else draft.factors[factor.factorId] = direction;
          changed('factors');
        }, 'factor-button');
        toggle.dataset.direction = direction; // 무겁게 · 가볍게를 고르면 색으로도 구분한다
        factorButtons.push({ toggle, factorId: factor.factorId, direction, row });
        actions.append(toggle);
      }
      row.append(label, actions);
      factorList.append(row);
    }
    factorGroup.append(factorList);

    main.append(controls);

    // ---- 마지막 장: 판결 확정(서명) ----
    // 정한 내용을 한 번 더 보여 주고, 서명란을 누르면 서명이 그려진 뒤 확정한다(되돌릴 수 없다). 확정할 수 없으면 사유를 보여 주고 서명란이 움찔한다
    const signPage = element('section', 'verdict-sign folder-sheet');
    const signSummary = element('dl', 'sign-summary');
    const cautions = element('ul', 'sign-cautions');
    ['서명하면 판결을 바꿀 수 없어요.', '서명하면 AI 판결과 실제 판결이 차례로 공개돼요.', '양형기준 권고 범위는 정답이 아니라 참고 기준이에요.']
      .forEach((text) => cautions.append(element('li', '', text)));
    const reason = element('p', 'sign-reason');
    reason.id = `verdict-${caseId}-sign-reason`;
    reason.setAttribute('aria-live', 'polite');
    const submitError = element('p', 'field-error');
    submitError.setAttribute('role', 'alert');
    const signature = element('button', 'sign-line');
    signature.type = 'button';
    signature.setAttribute('aria-describedby', reason.id);
    const signHint = element('span', 'sign-hint', '여기를 눌러 서명');
    // 서명 획(장식): 손으로 흘려 쓴 한 줄. 눌렀을 때 획이 그려진다(움직임 줄이기면 바로 나타난다)
    signature.insertAdjacentHTML('beforeend', `<svg class="sign-ink" viewBox="0 0 240 64" aria-hidden="true" focusable="false"><path pathLength="1" d="M8 44 C 22 14, 34 10, 36 30 S 30 58, 46 40 S 70 12, 74 30 S 72 52, 92 36 C 104 26, 110 22, 116 34 S 128 50, 140 30 C 150 16, 160 18, 158 34 S 170 52, 190 30 C 200 20, 214 22, 232 26"/></svg>`);
    signature.prepend(signHint);
    const signRow = element('div', 'sign-row');
    // 판결 접수 도장(FE-97): 확정되면 서명 옆에 찍히고 한 박자 뒤 AI 판결로 넘어간다. 서명 직후 곧바로 "AI 판결을 기다리는 중"으로 바뀌어 내 결정이 로더에 묻혔다
    const filed = element('span', 'sign-filed', '판결 접수');
    filed.setAttribute('aria-hidden', 'true');
    const filedNote = element('p', 'visually-hidden');
    filedNote.setAttribute('role', 'status');
    signRow.append(element('span', 'sign-role', '재판장'), signature, element('span', 'sign-seal', '(서명)'), filed);
    signPage.append(element('h2', 'folder-title', '판결 확정'),
      element('p', 'muted', '정한 내용을 확인하고 서명해 주세요.'), signSummary,
      element('h3', 'sign-subtitle', '주의사항'), cautions, reason, signRow, filedNote, submitError);
    signature.addEventListener('click', sign);
    let signed = false;
    async function sign() {
      if (submitting || signed || !active()) return;
      const value = update();
      if (value.reason) { nudge(signRow); signature.focus(); return; }
      const payload = {
        penaltyType: draft.penaltyType,
        reducedTo: draft.reducing ? draft.reducedTo : null,
        prisonMonths: finalPenalty() === 'PRISON' ? value.sentence.value : null,
        fineAmount: finalPenalty() === 'FINE' ? value.sentence.value : null,
        suspensionMonths: draft.suspended ? value.suspension.value : null,
        factors: Object.entries(draft.factors).map(([factorId, direction]) => ({ factorId: Number(factorId), direction })),
        freeOpinion: null,
      };
      signed = true;
      submitting = true;
      submitError.textContent = '';
      // 서명하는 동안 카드 안쪽 스크롤을 지금 자리에 묶어 둔다(정한 내용을 다시 그리거나 상태가 바뀌어도 위로 튀지 않게)
      const scroller = layout.closest('.scroll-body');
      const keep = scroller?.scrollTop ?? 0;
      const hold = () => { if (scroller && submitting && Math.abs(scroller.scrollTop - keep) > 1) scroller.scrollTop = keep; };
      scroller?.addEventListener('scroll', hold);
      signRow.classList.add('is-signed');
      update();
      hold();
      requestAnimationFrame(hold);
      // 서명이 다 그려진 뒤에 확정을 보낸다(되돌릴 수 없는 일이라 서명하는 순간을 눈으로 보게 한다)
      if (!reducedMotion()) await new Promise((resolve) => { setTimeout(resolve, SIGN_MS); });
      try {
        await api.postVerdict(caseId, payload);
        clearDraft(caseId);
        markAiRevealIntro(caseId); // S-06에서 로딩 · 안내를 한 번 보여 준다 (7.7.4)
        if (active()) {
          signRow.classList.add('is-filed');
          filedNote.textContent = '판결이 접수되었어요. AI 판결로 넘어가요.';
          if (!reducedMotion()) await new Promise((resolve) => { setTimeout(resolve, FILED_MS); });
        }
        if (active()) navigate('ai', {}, { keepScroll: true }); // 서명한 자리 그대로 AI 판결 무대로(맨 위로 튀지 않게)
      } catch (error) {
        if (!active()) return;
        signed = false;
        signRow.classList.remove('is-signed'); // 확정하지 못했으면 서명을 지운다
        if (error?.code === 'INVALID_STATE') {
          showError(error);
        } else if (['OUT_OF_ALLOWED_RANGE', 'INVALID_SUSPENSION'].includes(error?.code)) {
          serverError = error.code;
        } else {
          submitError.textContent = '판결을 확정하지 못했어요. 잠시 후 다시 시도해 주세요.';
        }
      } finally {
        submitting = false;
        scroller?.removeEventListener('scroll', hold);
        if (active() && main.isConnected) {
          update();
          if (serverError) {
            showPage(3);
            const type = finalPenalty();
            const target = serverError === 'INVALID_SUSPENSION' && !suspensionFields.hidden && !suspensionArea.hidden
              ? yearButtons[0]?.yearButton ?? checkbox : type === 'PRISON' ? inputs.prisonYears : type === 'FINE' ? inputs.fineAmount
                : radios.querySelector('input:checked') ?? radios.querySelector('input');
            target.focus();
          } else if (!signed) signature.focus();
        }
      }
    }
    // 정한 내용 정리(서명 장): 형벌 · 형량 · 집행유예 · 무겁게 / 가볍게 본 요소
    function renderSignSummary(value) {
      const type = finalPenalty();
      const sentence = value.sentence.value;
      const penalty = !draft.penaltyType ? '아직 정하지 않았어요'
        : ['DEATH', 'LIFE'].includes(type) ? penaltyLabels[type]
          : sentence === null || value.sentence.invalid ? `${penaltyLabels[type] ?? penaltyLabels[draft.penaltyType]} (형량을 아직 정하지 않았어요)`
            : type === 'PRISON' ? `징역 ${formatMonths(sentence)}` : `벌금 ${formatMoney(sentence)}`;
      const reduced = draft.reducing && draft.reducedTo ? `${penaltyLabels[draft.penaltyType]}에서 감경` : '';
      const suspension = draft.suspended && value.suspension.value ? `${formatMonths(value.suspension.value)}` : '붙이지 않음';
      const labels = (direction) => form.factors.filter((factor) => draft.factors[factor.factorId] === direction).map((factor) => factor.label);
      const rows = [['형벌 · 형량', penalty], ...(reduced ? [['감경', reduced]] : []), ['집행유예', suspension],
        ['무겁게 본 요소', labels('UP')], ['가볍게 본 요소', labels('DOWN')]];
      // 내용이 그대로면 다시 그리지 않는다(통째로 갈아끼우면 브라우저의 스크롤 기준점이 사라져 카드가 튈 수 있다)
      const key = JSON.stringify(rows);
      if (signSummary.dataset.key === key) return;
      signSummary.dataset.key = key;
      signSummary.replaceChildren(...rows.flatMap(([term, detail]) => {
        const dd = element('dd');
        if (Array.isArray(detail)) {
          if (detail.length) { const list = element('ul'); detail.forEach((text) => list.append(element('li', '', text))); dd.append(list); } else dd.textContent = '없음';
        } else dd.textContent = detail;
        return [element('dt', '', term), dd];
      }));
    }

    // ---- 첫 장: 사건 요약(없앤 판결 전 정리 화면의 내용) — 사건 개요 · 핵심 사실(API 6). 못 받으면 이 장만 안내하고 나머지는 그대로 쓴다 ----
    const summaryPage = element('section', 'verdict-summary folder-sheet');
    summaryPage.append(element('h2', 'folder-title', '사건 요약'));
    const overview = review?.sections?.find(({ step }) => step === 1)?.items ?? [];
    if (overview.length || review?.summary?.length) {
      if (overview.length) {
        const block = element('section', 'summary-block');
        block.append(element('h3', 'sign-subtitle', '사건 개요'), ...overview.map((item) => element('p', '', item.content)));
        summaryPage.append(block);
      }
      if (review?.summary?.length) {
        const block = element('section', 'summary-block');
        const list = element('ul', 'summary-facts');
        review.summary.forEach((item) => list.append(element('li', '', item)));
        block.append(element('h3', 'sign-subtitle', '핵심 사실'), list);
        summaryPage.append(block);
      }
    } else summaryPage.append(element('p', 'muted', '사건 요약을 불러오지 못했어요. 참고 정보와 사건 기록 다시 보기로 이어서 판결할 수 있어요.'));

    const sidebar = element('section', 'verdict-sidebar folder-sheet');
    sidebar.append(element('h2', 'visually-hidden', '참고 정보'));
    const lawBox = element('div');
    renderRecommendedRangeBar(sidebar, { law, showTrack: false });
    sidebar.insertBefore(lawBox, sidebar.children[1]);
    // 색인 탭(ARIA 탭): 누르거나 ←→ · Home · End로 장을 고른다. 펼쳐 본 장(reached)과 그 바로 다음 장까지 열린다.
    // 마지막 확정 장은 판결이 다 정해졌을 때(ready)만 열리고, 안 되면 탭이 움찔하며 빠진 칸이 반짝인다
    const tabList = element('div', 'folder-tabs');
    tabList.setAttribute('role', 'tablist');
    tabList.setAttribute('aria-label', '판결 파일철');
    const denied = element('p', 'visually-hidden');
    denied.setAttribute('aria-live', 'polite');
    let reached = 0; // 펼쳐 본 가장 뒤 장
    let ready = false; // 확정 장을 열 수 있는지: 형벌 · 형량(범위 안) · 감경 후 형벌 · 집행유예 기간이 다 정해졌는지(update가 정한다)
    let missing = null; // 확정을 막는 칸(반짝일 곳)
    let missingReason = '';
    const LAST = 4;
    // 진행 책갈피: 펼쳐 본 가장 뒤 장(reached)의 탭 바로 뒤에 끼워 둔 내로 얼굴 책갈피(장식). 잠금이 아니라 진행을 표시하므로
    // 늘 보이고, 뒤로 넘겨도 자리를 지킨다(마지막 장까지 오면 탭 줄 끝). 얼굴은 마이페이지 "만난 모습"에서 고른 것(FE-67)
    const bookmark = element('span', `folder-bookmark bookmark-color-${getBookmarkColor()}`);
    bookmark.setAttribute('aria-hidden', 'true');
    bookmark.append(picture(selectedBookmarkFace()));
    const pages = [[summaryPage, '사건 요약'], [sidebar, '참고 정보'], [factorPage, '판단 요소'], [main, '최종 판결'], [signPage, '확정']].map(([panel, title], i) => {
      const tab = element('button', 'folder-tab');
      tab.type = 'button';
      tab.id = `verdict-${caseId}-tab-${i + 1}`;
      panel.id = `verdict-${caseId}-page-${i + 1}`;
      tab.setAttribute('role', 'tab');
      tab.setAttribute('aria-controls', panel.id);
      panel.setAttribute('role', 'tabpanel');
      panel.setAttribute('aria-labelledby', tab.id);
      tab.append(element('span', 'folder-tab-no', String(i + 1)), element('span', '', title));
      tab.addEventListener('click', () => goTo(i));
      tabList.append(tab);
      return { tab, panel, title };
    });
    tabList.addEventListener('keydown', (event) => {
      const now = pages.findIndex(({ tab }) => tab === document.activeElement);
      const next = { ArrowRight: now + 1, ArrowLeft: now - 1, Home: 0, End: frontier() }[event.key];
      if (now < 0 || next === undefined) return;
      event.preventDefault();
      goTo(Math.max(0, Math.min(pages.length - 1, next)), true);
    });
    const canOpen = (i) => (i === LAST ? ready && reached >= LAST - 1 : i <= reached + 1);
    const frontier = () => pages.reduce((far, page, i) => (canOpen(i) ? i : far), 0); // 지금 열 수 있는 가장 뒤 장
    function goTo(index, focus = false) {
      if (!canOpen(index)) {
        nudge(pages[index].tab);
        if (index === LAST && reached >= LAST - 1) {
          flash(missing);
          denied.textContent = `확정 장은 아직 열 수 없어요. ${missingReason}`;
        } else denied.textContent = `${pages[index].title} 장은 아직 열 수 없어요. 앞 장을 먼저 펼쳐 주세요.`;
        return;
      }
      showPage(index, focus);
    }
    // 반짝임: 확정을 막는 칸(예: 1. 형벌 종류)을 두 번 밝혀 어디를 채워야 하는지 보여 준다
    function flash(node) {
      if (!node) return;
      node.classList.remove('is-flash');
      void node.offsetWidth;
      node.classList.add('is-flash');
      if (reducedMotion()) setTimeout(() => node.classList.remove('is-flash'), 1400); // 움직임 줄이기: 반짝임 대신 테두리만 잠시
      else node.addEventListener('animationend', () => node.classList.remove('is-flash'), { once: true });
    }
    // 탭 잠금 · 책갈피를 지금 상태에 맞춘다(장을 넘길 때 · 판결 값이 바뀔 때)
    function refreshTabs() {
      pages.forEach(({ tab }, i) => tab.setAttribute('aria-disabled', String(!canOpen(i))));
      layout.dispatchEvent(new Event('stagenextchange')); // 무대 하단 버튼의 글자 · 눌림을 지금 장에 맞춘다
      const before = pages[reached + 1]?.tab ?? null; // 마지막 장이면 탭 줄 끝
      if (bookmark.parentNode !== tabList || bookmark.nextSibling !== before) {
        const moved = bookmark.parentNode === tabList;
        tabList.insertBefore(bookmark, before);
        if (moved && !reducedMotion()) bookmark.animate([{ translate: '0 -40%', opacity: 0 }, { translate: '0 0', opacity: 1 }], { duration: 240, easing: 'cubic-bezier(.2, .8, .2, 1)' });
      }
    }
    // 움찔: 안 되는 동작(아직 안 본 장 · 확정할 수 없는 서명)을 짧게 흔들어 알린다
    function nudge(node) {
      if (reducedMotion()) return;
      node.classList.remove('is-denied');
      void node.offsetWidth; // 연달아 눌러도 다시 흔들리게
      node.classList.add('is-denied');
      node.addEventListener('animationend', () => node.classList.remove('is-denied'), { once: true });
    }
    function showPage(index, focus = false) {
      reached = Math.max(reached, index);
      const moving = pages[index].panel.hidden && pages.some(({ panel }) => !panel.hidden);
      pages.forEach(({ tab, panel }, i) => {
        tab.setAttribute('aria-selected', String(i === index));
        tab.tabIndex = i === index ? 0 : -1;
        tab.toggleAttribute('data-autofocus', i === index); // 무대가 판결 카드를 열 때 이 탭에 포커스를 준다(←→ · Space가 바로 먹게)
        panel.hidden = i !== index;
      });
      refreshTabs();
      denied.textContent = '';
      if (focus) pages[index].tab.focus({ preventScroll: true });
      // 장을 넘기면 새 장의 맨 위부터 보인다: 무대 카드 안 스크롤을 처음으로 올리고, 파일철 머리가 화면 위로 지나갔으면 화면도 올린다
      if (moving) {
        const scroller = layout.closest('.scroll-body');
        if (scroller) scroller.scrollTop = 0;
        if (layout.getBoundingClientRect().top < 0) layout.scrollIntoView({ block: 'start' });
      }
    }
    // 말린 모서리: 마지막 장을 뺀 모든 장의 오른쪽 아래. 눌러서 다음 장으로 넘긴다(다음 장을 여는 유일한 방법)
    pages.slice(0, -1).forEach(({ panel }, i) => {
      const curl = element('button', 'folder-curl');
      curl.type = 'button';
      curl.setAttribute('aria-label', `다음 장으로 넘기기: ${pages[i + 1].title}`);
      curl.addEventListener('click', () => goTo(i + 1, true)); // 최종 판결 → 확정도 판결이 다 정해져야 넘어간다
      panel.append(curl);
    });
    // 무대 하단 "다음" 버튼이 이 파일철을 조작한다(stage.js nextDriver): 다음 장으로 넘기고, 마지막 장에서는 서명(제출)을 한다.
    // 못 넘길 때는 말린 모서리와 같이 움찔 · 반짝이며 이유를 알린다(goTo). 종이 귀는 같은 일을 하는 보조 손잡이다
    const currentPage = () => pages.findIndex(({ panel }) => !panel.hidden);
    layout.stageNext = {
      label: () => (currentPage() === LAST ? '서명하고 제출 ▶' : '다음 장 ▶'),
      hint: () => {
        const now = currentPage();
        if (now === LAST) return '서명하면 판결이 제출돼요';
        return now === LAST - 1 && !ready ? `확정 장으로 가려면 ${missingReason}` : `다음 장: ${pages[now + 1].title}`;
      },
      enabled: () => !submitting,
      run: () => { if (currentPage() === LAST) sign(); else goTo(currentPage() + 1, true); },
    };
    showPage(0);
    const folderBody = element('div', 'folder-body');
    folderBody.append(...pages.map(({ panel }) => panel));
    layout.append(tabList, denied, folderBody);
    // 대본의 제목이 사건 제목과 같을 때만 무대를 쓴다(S-03 · S-04와 같은 규칙, 사건 번호는 DB마다 다를 수 있다)
    const candidate = SCRIPTS[caseId];
    const script = candidate?.title === caseHeader.title ? candidate : undefined;
    if (script) {
      // 3막 당신이 판사(흐름 목업): 검사 의견 → 최종변론 → 최후진술을 지나 판결 장면에서 이 화면(입력 · 참고 정보)을
      // 무대 입력 카드로 띄운다. 입력 · 확정 · 오류 처리는 아래 그대로이고, 확정하면 AI 판결(S-06)으로 간다
      root.classList.add('has-stage');
      const stageLayout = element('div', 'review-layout');
      const stageMain = element('div', 'review-main');
      stageMain.append(renderStage({
        script,
        order: ['3'],
        cards: { verdict: layout },
        inputCards: ['verdict'],
        onRestart: restartMock(caseId),
        onJump: jumpActMock(caseId),
      }));
      stageLayout.append(stageMain);
      root.replaceChildren(header, stageLayout);
    } else root.replaceChildren(header, layout);

    function update() {
      const option = form.penaltyOptions.find((item) => item.penaltyType === draft.penaltyType);
      const choices = option?.reducibleTo ?? [];
      const pendingReduction = draft.reducing && !choices.includes(draft.reducedTo);
      const finalType = finalPenalty();
      const prison = finalType === 'PRISON';
      const fine = finalType === 'FINE';
      const fixed = ['DEATH', 'LIFE'].includes(finalType) && !pendingReduction;
      const fineManwon = parseInteger(draft.fineAmount);
      const sentence = prison ? parsePeriod(draft.prisonYears, draft.prisonMonths)
        : fine ? { ...fineManwon, value: fineManwon.value === null ? null : fineManwon.value * 10000 } : { value: null, invalid: false };
      const present = Boolean(option) && !pendingReduction && sentence.value !== null && !sentence.invalid;
      const inRange = present && sentence.value >= option.allowedMin && sentence.value <= (option.allowedMax ?? Infinity); // 벌금은 상한이 없다
      const eligible = inRange && option.suspensionAllowed
        && sentence.value <= (prison ? rule.maxPrisonMonths : rule.maxFineAmount);
      if (!eligible) clearSuspension();
      const suspension = parsePeriod(draft.suspensionYears, draft.suspensionMonths);
      const suspensionInvalid = draft.suspended && (suspension.invalid || suspension.value === null
        || suspension.value < rule.minMonths || suspension.value > rule.maxMonths);
      reductionCheck.checked = draft.reducing;
      reductionCheck.disabled = !choices.length;
      reductionLabel.classList.toggle('is-disabled', !choices.length);
      for (const { type, label, radio } of reductionRadios) {
        radio.disabled = !draft.reducing || !choices.includes(type); // 감경 후 형벌이 하나뿐이면 체크할 때 자동으로 골라진다
        radio.checked = draft.reducedTo === type;
        label.classList.toggle('is-disabled', radio.disabled);
      }
      // 형량 칸은 늘 보인다(상자 크기를 같게): 고를 수 없을 때는 징역 칸을 회색으로 잠근다
      const fineEntry = fine && !pendingReduction;
      prisonFields.hidden = fineEntry;
      fineFields.hidden = !fineEntry;
      entryType.textContent = fineEntry ? '벌금' : '징역';
      const sliding = (prison || fine) && !pendingReduction;
      const trackMax = prison ? option?.allowedMax : FINE_TRACK_MAX;
      const [headName, ...headLines] = !option ? ['선택 전', '먼저 형벌 종류를 선택해 주세요.', '형벌을 고르면 형량 · 금액을 정할 수 있어요.']
        : pendingReduction ? ['감경', '감경 후 형벌을 골라 주세요.', '고르면 그 형벌의 형량을 정할 수 있어요.']
          : fixed ? [penaltyLabels[finalType], `형량 입력 없이 ${penaltyLabels[finalType]}으로 선고해요.`,
            `양형기준 권고 범위는 징역 기준이라 ${penaltyLabels[finalType]}에는 해당하지 않아요.`]
            : prison ? ['징역', '막대를 누르거나 끌어서 형량을 고를 수 있어요.', '칸에 년 · 개월을 직접 입력해도 돼요.']
              : ['벌금', '막대를 누르거나 끌어서 금액을 고를 수 있어요.', '3,000만 원이 넘으면 칸에 만원 단위로 입력해 주세요.'];
      fixedPenalty.classList.toggle('is-empty', !option || pendingReduction);
      fixedPenalty.replaceChildren(element('strong', 'fixed-penalty-name', headName), ...headLines.map((line) => element('p', 'muted', line)));
      sentenceError.textContent = sentence.invalid
        ? prison ? '년 · 개월은 숫자로 입력해 주세요.' : '금액은 만원 단위 정수로 입력해 주세요.' : '';
      for (const key of ['prisonYears', 'prisonMonths', 'fineAmount']) inputs[key].setAttribute('aria-invalid', String(sentence.invalid));
      // 막대는 늘 보인다. 형량을 고를 수 없을 때(형벌 고르기 전 · 사형 · 무기징역 · 감경 후 형벌 고르기 전)는 징역 막대를 회색으로 눌리지 않게 둔다
      const key = sliding ? [draft.penaltyType, draft.reducing, draft.reducedTo, prison].join('|') : 'off';
      if (key !== rangeKey) {
        rangeKey = key;
        rangeBox.replaceChildren();
        const bar = sliding ? option : prisonOption;
        if (sliding && fine) renderFineRange(rangeBox, option.allowedMin);
        else if (bar) renderRecommendedRangeBar(rangeBox, {
          law,
          trackMaxMonths: bar.allowedMax,
          allowedMinMonths: bar.allowedMin,
          penaltyType: sliding ? draft.penaltyType : 'PRISON',
          showInputLabel: false, // 형량은 막대 위 형량 칸이 보여 준다
          showBasis: false, // 권고 범위 근거는 참고 정보 탭에 있다
        });
        if (sliding) rangeBox.querySelector('.range-track')?.append(marker);
        // 형량 칸: "선고할 수 있는 범위" 설명과 같은 줄 오른쪽
        // "선고할 수 있는 범위" 아래 두 줄(범위 · 권고 범위)과 같은 높이 오른쪽에
        rangeBox.querySelector('.recommended-range h3')?.after(entry);
      }
      // 막대를 새로 그린 뒤에 슬라이더 역할 · 값을 단다(형벌을 바꾼 직후에도 막대가 바로 조작되게)
      if (sliding) {
        const shown = present ? sentence.value : option.allowedMin;
        rangeSlider.classList.add('is-sliding');
        const track = rangeSlider.querySelector('.range-track');
        if (track) {
          track.setAttribute('role', 'slider');
          track.tabIndex = 0;
          track.setAttribute('aria-label', prison ? '형량 조절 막대' : '벌금 조절 막대');
          track.setAttribute('aria-valuemin', String(option.allowedMin));
          track.setAttribute('aria-valuemax', String(prison ? option.allowedMax : FINE_TRACK_MAX));
          track.setAttribute('aria-valuenow', String(Math.min(shown, trackMax)));
          track.setAttribute('aria-valuetext', present ? (prison ? `징역 ${formatMonths(sentence.value)}` : `벌금 ${formatMoney(sentence.value)}`) : '입력 전');
        }
      } else {
        rangeSlider.classList.remove('is-sliding');
      }
      rangeSlider.classList.toggle('is-off', !sliding);
      entry.classList.toggle('is-off', !sliding);
      for (const key of ['prisonYears', 'prisonMonths', 'fineAmount']) {
        inputs[key].disabled = !sliding;
        // 잠긴 칸에는 앞서 넣은 숫자를 보이지 않게 비운다(draft 값은 그대로라 다시 고르면 되살아난다)
        inputs[key].placeholder = sliding ? '' : '—';
        const shownValue = sliding ? draft[key] : '';
        if (inputs[key].value !== shownValue) inputs[key].value = shownValue;
      }
      if (sliding) {
        const shown = present ? sentence.value : option.allowedMin;
        marker.style.left = `${Math.max(0, Math.min(100, shown / trackMax * 100))}%`;
        marker.classList.toggle('is-warning', prison && shown > trackMax);
      }
      feedback.replaceChildren();
      const outOfRange = (present && !inRange) || serverError === 'OUT_OF_ALLOWED_RANGE';
      if (outOfRange) for (const key of ['prisonYears', 'prisonMonths', 'fineAmount']) inputs[key].setAttribute('aria-invalid', 'true'); // 범위 초과도 잘못된 입력(FE-97)
      if (outOfRange) {
        const warning = element('div', 'range-warning');
        warning.setAttribute('role', 'alert');
        const rangeText = prison ? `징역 ${formatMonths(option.allowedMin)} ~ ${formatMonths(option.allowedMax)}` : option.text;
        const alternative = draft.penaltyType === 'PRISON' && present && sentence.value > option.allowedMax
          ? ['LIFE', 'DEATH'].map((type) => form.penaltyOptions.find((item) => item.penaltyType === type))
            .find((item) => item && (item.reducibleTo ?? []).includes('PRISON')
              && sentence.value >= item.allowedMin && sentence.value <= item.allowedMax) : null;
        const help = alternative ? ` 징역 ${formatMonths(option.allowedMax)}을 넘기려면 ${penaltyLabels[alternative.penaltyType]}을 고른 뒤 감경해서 선고할 수 있어요.` : '';
        warning.append(element('strong', '', '선고할 수 있는 범위를 벗어났습니다.'),
          element('p', '', `이 사건에서 선고할 수 있는 형량은 ${rangeText}입니다. 범위 안으로 형량을 조정해야 판결을 확정할 수 있어요.${help}`));
        feedback.append(warning);
      }
      // 확장: REQ-036 S-05b — 권고 범위 밖이지만 선고 가능하면 별도 경고 없음(권고 범위는 참고 기준).
      const noSuspension = ['DEATH', 'LIFE'].includes(draft.penaltyType);
      checkbox.disabled = !eligible;
      suspensionLabel.classList.toggle('is-disabled', !eligible);
      suspensionArea.classList.toggle('is-off', !eligible); // 붙일 수 없으면 줄 전체를 회색으로 잠근다(감경 · 형량 막대와 같은 표시)
      suspensionHint.textContent = noSuspension ? '사형 · 무기징역을 고르면 감경해도 집행유예를 붙일 수 없어요.'
        : `징역 ${formatMonths(rule.maxPrisonMonths)} 이하 또는 벌금 ${formatMoney(rule.maxFineAmount)} 이하일 때 붙일 수 있어요.`;
      checkbox.checked = draft.suspended;
      suspensionFields.hidden = !eligible || !draft.suspended;
      suspensionError.textContent = serverError === 'INVALID_SUSPENSION' ? '집행유예 조건을 다시 확인해 주세요.' : '';
      for (const { yearButton, year } of yearButtons) {
        yearButton.setAttribute('aria-pressed', String(draft.suspended && suspension.value === year * 12));
      }
      for (const { toggle, factorId, direction, row } of factorButtons) {
        toggle.setAttribute('aria-pressed', String((draft.factors[factorId] ?? 'NONE') === direction));
        row.classList.toggle('is-selected', Boolean(draft.factors[factorId]));
      }
      const disabledReason = !option ? '형벌 종류를 골라 주세요.' : pendingReduction ? '감경 후 형벌을 골라 주세요.'
        : !fixed && !present ? '형량을 입력해 주세요.'
        : (!fixed && !inRange) || serverError === 'OUT_OF_ALLOWED_RANGE' ? '선고할 수 있는 범위 안으로 형량을 조정해 주세요.'
          : suspensionInvalid || serverError === 'INVALID_SUSPENSION' ? '집행유예 기간을 골라 주세요.' : '';
      reason.textContent = disabledReason ? `확정할 수 없어요. ${disabledReason}` : '';
      controls.disabled = submitting;
      factorGroup.disabled = submitting;
      back.disabled = submitting;
      // disabled로 바꾸면 눌린 서명란이 포커스를 잃고 카드가 맨 위로 튄다 — 누름은 sign()이 막고, 상태만 알린다
      signature.setAttribute('aria-disabled', String(submitting));
      signHint.textContent = submitting ? '확정하는 중…' : '여기를 눌러 서명';
      signPage.setAttribute('aria-busy', String(submitting));
      lawBox.replaceChildren();
      renderLawInfoBox(lawBox, { law, penaltyType: draft.penaltyType || 'PRISON' });
      ready = !disabledReason;
      missingReason = disabledReason;
      missing = !option ? penaltyGroup : pendingReduction ? reductionArea
        : (!fixed && (!present || !inRange)) || serverError === 'OUT_OF_ALLOWED_RANGE' ? sentenceGroup : disabledReason ? suspensionArea : null;
      refreshTabs();
      const value = { sentence, suspension, reason: disabledReason };
      renderSignSummary(value);
      return value;
    }
    update();
  }

  async function load() {
    if (loading || !active()) return;
    loading = true;
    const message = element('p', 'message-panel', '판결 입력 정보를 불러오는 중…');
    message.setAttribute('role', 'status');
    root.replaceChildren(message);
    try {
      // 사건 요약 장(API 6)은 못 받아도 판결은 할 수 있게 따로 받는다
      const [form, review] = await Promise.all([api.getVerdictForm(caseId), api.getReview(caseId).catch(() => null)]);
      if (active()) draw(form, review);
    } catch (error) { showError(error); }
    finally { loading = false; }
  }
  await load();
}
