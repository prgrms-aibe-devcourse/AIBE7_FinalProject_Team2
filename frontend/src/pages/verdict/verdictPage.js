import { renderLawInfoBox } from '../../components/lawInfoBox.js';
import { renderRecommendedRangeBar, formatMonths } from '../../components/recommendedRangeBar.js';
import { renderStage } from '../../components/stage.js';
import { SCRIPTS } from '../../content/scripts.js';
import { restartMock, jumpActMock } from '../overview/overviewPage.js';
import { getDraft, saveDraft, clearDraft } from './verdictDraft.js';
import { markAiRevealIntro } from '../ai/aiRevealIntro.js';

const penaltyLabels = { DEATH: '사형', LIFE: '무기징역', PRISON: '징역', FINE: '벌금' };

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
  // 개월은 12 이상도 받는다(예: 30개월 = 2년 6개월). 칸을 벗어나면 년 · 개월로 나눠 다시 적는다(input의 change)
  if (year.invalid || month.invalid) return { value: null, invalid: true };
  if (year.value === null && month.value === null) return { value: null, invalid: false };
  const value = (year.value ?? 0) * 12 + (month.value ?? 0);
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

  function draw(form) {
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
    let confirming = false;
    let submitting = false;
    let payload = null;
    const inputs = {};
    const factorButtons = [];
    const header = element('header', 'case-topbar');
    const back = button('사건 기록 다시 보기', () => navigate('review'), 'back-button');
    header.append(element('strong', 'case-title', `판결 입력 · ${caseHeader.title}`), back);
    const layout = element('main', 'verdict-layout');
    const main = element('div', 'verdict-main');
    main.append(element('h1', '', '당신의 판결'), element('p', 'verdict-intro muted', '지금까지 확인한 사건 정보와 양형기준을 참고해 판결해 주세요.'));
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
      wrapper.append(name, field, element('span', '', unit));
      parent.append(wrapper);
      inputs[key] = field;
    }
    // 징역 개월 칸에 12개월 이상을 적으면 남는 개월만 두고 나머지는 년 칸으로 올린다(입력 중에는 그대로 두고 칸을 벗어날 때)
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
    const reductionAuto = element('p', 'reduction-auto');
    reductionArea.append(reductionLabel, element('p', 'muted', '판사는 참작할 사정이 있으면 형을 줄여서 선고할 수 있어요(작량감경).'), reductionChoices, reductionAuto);
    penaltyGroup.append(reductionArea);

    const sentenceGroup = group('2. 형량 · 금액');
    const chooseHint = element('p', 'muted', '먼저 형벌 종류를 선택해 주세요.');
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
    function setValue(raw, snap = false) {
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
      value = Math.max(range.min, Math.min(range.max, range.trackMax, value));
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
      setValue(valueAt(event.clientX), true);
    });
    rangeSlider.addEventListener('pointermove', (event) => {
      if (rangeSlider.hasPointerCapture(event.pointerId)) setValue(valueAt(event.clientX), true);
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
      ['0', '1,000만', '2,000만', '3,000만 원 이상'].forEach((text, i) => {
        const label = element('span', '', text);
        label.style.left = `${i / 3 * 100}%`;
        labels.append(label);
      });
      box.append(element('h3', '', '선고할 수 있는 범위'), element('p', 'range-value', `벌금 ${formatMoney(min)} 이상`),
        track, labels, element('p', 'range-lower-label', '막대는 3,000만 원까지예요. 더 큰 금액은 칸에 직접 입력해 주세요.'));
      container.append(box);
    }
    const rangeBox = element('div', 'verdict-range');
    const feedback = element('div', 'sentence-feedback');
    const fixedPenalty = element('div', 'fixed-penalty');
    const sliderHint = element('p', 'muted slider-hint', '막대를 누르거나 끌어서 형량을 고를 수 있어요. 칸에 직접 입력해도 돼요.');
    rangeSlider.append(rangeBox);
    sentenceGroup.append(chooseHint, fixedPenalty, sentenceError, sliderHint, rangeSlider, feedback);
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

    const factorGroup = group('3. 판단 요소');
    factorGroup.append(element('p', 'muted', '요소마다 형량을 무겁게 했으면 ↑, 가볍게 했으면 ↓를 눌러 주세요. 처음에는 모두 "고려 안 함"이에요.'));
    const factorList = element('ul', 'factor-list');
    for (const factor of form.factors) {
      const row = element('li', 'factor-row');
      const label = element('span', 'factor-label', factor.label);
      label.id = `verdict-${caseId}-factor-${factor.factorId}`;
      const actions = element('div', 'factor-actions');
      actions.setAttribute('role', 'group');
      actions.setAttribute('aria-labelledby', label.id);
      // 무겁게 · 고려 안 함 · 가볍게 중 하나. 고려 안 함(NONE)은 서버에 보내지 않는다(요소를 고르지 않은 것)
      for (const [direction, text] of [['UP', '↑ 무겁게'], ['NONE', '— 고려 안 함'], ['DOWN', '↓ 가볍게']]) {
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

    const footer = element('div', 'verdict-submit');
    const reason = element('p', 'muted');
    reason.id = `verdict-${caseId}-submit-reason`;
    reason.setAttribute('aria-live', 'polite');
    const openConfirmation = button('판결 확정하기', () => {
      const value = update();
      if (value.reason || confirming || submitting) return;
      payload = {
        penaltyType: draft.penaltyType,
        reducedTo: draft.reducing ? draft.reducedTo : null,
        prisonMonths: finalPenalty() === 'PRISON' ? value.sentence.value : null,
        fineAmount: finalPenalty() === 'FINE' ? value.sentence.value : null,
        suspensionMonths: draft.suspended ? value.suspension.value : null,
        factors: Object.entries(draft.factors).map(([factorId, direction]) => ({ factorId: Number(factorId), direction })),
        freeOpinion: null,
      };
      confirming = true;
      submitError.textContent = '';
      update();
      cancel.focus();
    }, 'primary-button');
    openConfirmation.setAttribute('aria-describedby', reason.id);
    footer.append(reason, openConfirmation);

    const confirmation = element('section', 'confirmation-box');
    const confirmationHeading = element('h2', '', '판결을 확정할까요?');
    confirmationHeading.id = `verdict-${caseId}-confirmation-title`;
    confirmation.setAttribute('aria-labelledby', confirmationHeading.id);
    const submitError = element('p', 'field-error');
    submitError.setAttribute('role', 'alert');
    const confirmationActions = element('div', 'confirmation-actions');
    const cancel = button('다시 검토하기', () => {
      if (submitting) return;
      confirming = false;
      payload = null;
      update();
      openConfirmation.focus();
    });
    const submit = button('확정하기', async () => {
      if (submitting || !confirming || !payload || !active()) return;
      submitting = true;
      submitError.textContent = '';
      update();
      try {
        await api.postVerdict(caseId, payload);
        clearDraft(caseId);
        markAiRevealIntro(caseId); // S-07에서 로딩 · 안내를 한 번 보여 준다 (7.7.4)
        if (active()) navigate('ai');
      } catch (error) {
        if (!active()) return;
        if (error?.code === 'INVALID_STATE') {
          showError(error);
        } else if (['OUT_OF_ALLOWED_RANGE', 'INVALID_SUSPENSION'].includes(error?.code)) {
          serverError = error.code;
          confirming = false;
          payload = null;
        } else {
          submitError.textContent = '판결을 확정하지 못했어요. 잠시 후 다시 시도해 주세요.';
        }
      } finally {
        submitting = false;
        if (active() && main.isConnected) {
          update();
          if (serverError) {
            const type = finalPenalty();
            const target = serverError === 'INVALID_SUSPENSION' && !suspensionFields.hidden && !suspensionArea.hidden
              ? yearButtons[0]?.yearButton ?? checkbox : type === 'PRISON' ? inputs.prisonYears : type === 'FINE' ? inputs.fineAmount
                : radios.querySelector('input:checked') ?? radios.querySelector('input');
            target.focus();
          } else if (confirming) submit.focus();
        }
      }
    }, 'primary-button');
    confirmationActions.append(cancel, submit);
    confirmation.append(confirmationHeading, element('p', '', '확정하면 판결을 바꿀 수 없고, AI 판결과 실제 판결이 차례로 공개돼요.'), submitError, confirmationActions);
    main.append(controls, footer, confirmation);

    const sidebar = element('aside', 'verdict-sidebar');
    sidebar.setAttribute('aria-label', '판결 참고 정보');
    sidebar.append(element('h2', '', '참고 정보'));
    const lawBox = element('div');
    renderRecommendedRangeBar(sidebar, { law, showTrack: false });
    const records = button('사건 기록 바로가기', () => navigate('review'));
    sidebar.insertBefore(lawBox, sidebar.children[1]);
    sidebar.append(records);
    layout.append(main, sidebar);
    // 대본의 제목이 사건 제목과 같을 때만 무대를 쓴다(S-03 · S-04와 같은 규칙, 사건 번호는 DB마다 다를 수 있다)
    const candidate = SCRIPTS[caseId];
    const script = candidate?.title === caseHeader.title ? candidate : undefined;
    if (script) {
      // 5막 당신이 판사(흐름 목업): 검사 의견 → 최종변론 → 최후진술을 지나 판결 장면에서 이 화면(입력 · 참고 정보)을
      // 무대 입력 카드로 띄운다. 입력 · 확정 · 오류 처리는 아래 그대로이고, 확정하면 AI 판결(S-07)로 간다
      root.classList.add('has-stage');
      const stageLayout = element('div', 'review-layout');
      const stageMain = element('div', 'review-main');
      stageMain.append(renderStage({
        script,
        order: ['5'],
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
      chooseHint.hidden = Boolean(option);
      reductionArea.hidden = !choices.length;
      reductionCheck.checked = draft.reducing;
      reductionChoices.hidden = !draft.reducing || choices.length < 2;
      reductionAuto.hidden = !draft.reducing || choices.length !== 1;
      reductionAuto.textContent = choices.length === 1 ? `${penaltyLabels[choices[0]]}으로 감경해요` : '';
      for (const { type, label, radio } of reductionRadios) {
        label.hidden = !choices.includes(type);
        radio.checked = draft.reducedTo === type;
      }
      prisonFields.hidden = !prison;
      fineFields.hidden = !fine;
      entryType.textContent = prison ? '징역' : fine ? '벌금' : '';
      const sliding = (prison || fine) && !pendingReduction;
      const trackMax = prison ? option?.allowedMax : FINE_TRACK_MAX;
      sliderHint.hidden = !sliding;
      fixedPenalty.hidden = !fixed;
      fixedPenalty.replaceChildren();
      if (fixed) fixedPenalty.append(element('strong', 'fixed-penalty-name', penaltyLabels[finalType]),
        element('p', 'muted', `형량 입력 없이 ${penaltyLabels[finalType]}으로 선고해요.`),
        element('p', 'muted', `양형기준 권고 범위(징역 ${formatMonths(form.recommended.minMonths)} ~ ${formatMonths(form.recommended.maxMonths)})는 유기징역 기준이라 ${penaltyLabels[finalType]}에는 적용되지 않아요.`));
      sentenceError.textContent = sentence.invalid
        ? prison ? '년 · 개월은 숫자로 입력해 주세요.' : '금액은 만원 단위 정수로 입력해 주세요.' : '';
      for (const key of ['prisonYears', 'prisonMonths', 'fineAmount']) inputs[key].setAttribute('aria-invalid', String(sentence.invalid));
      const key = option && !fixed ? [draft.penaltyType, draft.reducing, draft.reducedTo, pendingReduction, prison].join('|') : '';
      if (key !== rangeKey) {
        rangeKey = key;
        rangeBox.replaceChildren();
        if (key && fine) renderFineRange(rangeBox, option.allowedMin);
        else if (key) renderRecommendedRangeBar(rangeBox, {
          law,
          showTrack: !pendingReduction,
          trackMaxMonths: option.allowedMax,
          allowedMinMonths: option.allowedMin,
          penaltyType: draft.penaltyType,
          showInputLabel: false, // 형량은 막대 위 형량 칸이 보여 준다
        });
        rangeBox.querySelector('.range-track')?.append(marker);
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
      entry.hidden = !sliding;
      if (sliding) {
        const shown = present ? sentence.value : option.allowedMin;
        marker.style.left = `${Math.max(0, Math.min(100, shown / trackMax * 100))}%`;
        marker.classList.toggle('is-warning', prison && shown > trackMax);
      }
      feedback.replaceChildren();
      if ((present && !inRange) || serverError === 'OUT_OF_ALLOWED_RANGE') {
        const warning = element('div', 'range-warning');
        warning.setAttribute('role', 'alert');
        const rangeText = prison ? `징역 ${formatMonths(option.allowedMin)} ~ ${formatMonths(option.allowedMax)}` : option.text;
        const alternative = draft.penaltyType === 'PRISON' && present && sentence.value > option.allowedMax
          ? ['LIFE', 'DEATH'].map((type) => form.penaltyOptions.find((item) => item.penaltyType === type))
            .find((item) => item && (item.reducibleTo ?? []).includes('PRISON')
              && sentence.value >= item.allowedMin && sentence.value <= item.allowedMax) : null;
        const help = alternative ? ` 징역 ${formatMonths(option.allowedMax)}을 넘기려면 ${penaltyLabels[alternative.penaltyType]}을 고른 뒤 감경해서 선고할 수 있어요.` : '';
        warning.append(element('strong', '', '! 선고할 수 있는 범위를 벗어났습니다.'),
          element('p', '', `이 사건에서 선고할 수 있는 형량은 ${rangeText}입니다. 범위 안으로 형량을 조정해야 판결을 확정할 수 있어요.${help}`));
        feedback.append(warning);
      }
      // 확장: REQ-036 S-06b — 권고 범위 밖이지만 선고 가능하면 별도 경고 없음(권고 범위는 참고 기준).
      const noSuspension = ['DEATH', 'LIFE'].includes(draft.penaltyType);
      checkbox.disabled = !eligible;
      suspensionLabel.classList.toggle('is-disabled', !eligible);
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
      const disabledReason = pendingReduction ? '감경 후 형벌을 골라 주세요.'
        : !fixed && !present ? '형량을 입력해 주세요.'
        : (!fixed && !inRange) || serverError === 'OUT_OF_ALLOWED_RANGE' ? '선고할 수 있는 범위 안으로 형량을 조정해 주세요.'
          : suspensionInvalid || serverError === 'INVALID_SUSPENSION' ? '집행유예 기간을 골라 주세요.' : '';
      reason.textContent = disabledReason;
      openConfirmation.disabled = Boolean(disabledReason) || confirming;
      controls.disabled = confirming;
      back.disabled = confirming;
      records.disabled = confirming;
      confirmation.hidden = !confirming;
      cancel.disabled = submitting;
      submit.disabled = submitting;
      submit.textContent = submitting ? '확정 중…' : '확정하기';
      confirmation.setAttribute('aria-busy', String(submitting));
      lawBox.replaceChildren();
      renderLawInfoBox(lawBox, { law, penaltyType: draft.penaltyType || 'PRISON' });
      return { sentence, suspension, reason: disabledReason };
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
      const form = await api.getVerdictForm(caseId);
      if (active()) draw(form);
    } catch (error) { showError(error); }
    finally { loading = false; }
  }
  await load();
}
