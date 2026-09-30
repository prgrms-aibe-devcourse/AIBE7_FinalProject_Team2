import { renderLawInfoBox } from '../../components/lawInfoBox.js';
import { renderRecommendedRangeBar, formatMonths } from '../../components/recommendedRangeBar.js';
import { getDraft, saveDraft, clearDraft } from './verdictDraft.js';

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
  if (year.invalid || month.invalid || month.value > 11) return { value: null, invalid: true };
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
    if (error?.code === 'INVALID_STATE') {
      panel.append(element('p', '', `지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`));
      console.info('현재 상태:', error.currentStatus);
      // TODO: FE-2 라우터 연결 시 currentStatus에 해당하는 화면으로 이동한다.
    } else {
      panel.append(element('p', '', '정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'), button('다시 시도', load));
    }
    root.replaceChildren(panel);
  }

  function draw(form) {
    const draft = getDraft(caseId) ?? {
      penaltyType: '', prisonYears: '', prisonMonths: '', fineAmount: '',
      suspended: false, suspensionYears: '', suspensionMonths: '', factors: {},
    };
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
      wrapper.append(name, field, element('span', '', unit));
      parent.append(wrapper);
      inputs[key] = field;
    }
    function clearSuspension() {
      draft.suspended = false;
      draft.suspensionYears = '';
      draft.suspensionMonths = '';
      inputs.suspensionYears.value = '';
      inputs.suspensionMonths.value = '';
    }
    function changed(key) {
      const sentenceKeys = ['penaltyType', 'prisonYears', 'prisonMonths', 'fineAmount'];
      if (serverError === 'OUT_OF_ALLOWED_RANGE' && sentenceKeys.includes(key)) serverError = null;
      if (serverError === 'INVALID_SUSPENSION'
        && [...sentenceKeys, 'suspended', 'suspensionYears', 'suspensionMonths'].includes(key)) serverError = null;
      update();
      saveDraft(caseId, draft);
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
        for (const key of ['prisonYears', 'prisonMonths', 'fineAmount']) {
          draft[key] = '';
          inputs[key].value = '';
        }
        clearSuspension();
        changed('penaltyType');
      });
      label.append(radio, element('span', '', option.penaltyType === 'PRISON' ? '징역' : '벌금'));
      radios.append(label);
    }
    penaltyGroup.append(radios);

    const sentenceGroup = group('2. 형량 · 금액');
    const chooseHint = element('p', 'muted', '먼저 형벌 종류를 선택해 주세요.');
    const prisonFields = element('div', 'number-fields');
    const fineFields = element('div', 'number-fields');
    const sentenceError = element('p', 'field-error');
    sentenceError.id = `verdict-${caseId}-sentence-error`;
    sentenceError.setAttribute('aria-live', 'polite');
    input(prisonFields, 'prisonYears', '징역 년', '년', sentenceError.id);
    input(prisonFields, 'prisonMonths', '징역 개월', '개월', sentenceError.id);
    input(fineFields, 'fineAmount', '벌금 금액', '원', sentenceError.id);
    const money = element('output', 'money-readable');
    fineFields.append(money);
    const rangeBox = element('div', 'verdict-range');
    const feedback = element('div', 'sentence-feedback');
    sentenceGroup.append(chooseHint, prisonFields, fineFields, sentenceError, rangeBox, feedback);

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
    const suspensionFields = element('div', 'number-fields');
    const suspensionError = element('p', 'field-error');
    suspensionError.id = `verdict-${caseId}-suspension-error`;
    suspensionError.setAttribute('aria-live', 'polite');
    input(suspensionFields, 'suspensionYears', '집행유예 년', '년', suspensionError.id);
    input(suspensionFields, 'suspensionMonths', '집행유예 개월', '개월', suspensionError.id);
    const suspensionHint = element('p', 'muted', `징역 ${formatMonths(rule.maxPrisonMonths)} 이하 또는 벌금 ${formatMoney(rule.maxFineAmount)} 이하일 때 집행유예를 붙일 수 있어요.`);
    suspensionArea.append(suspensionLabel, suspensionFields, suspensionError, suspensionHint);
    sentenceGroup.append(suspensionArea);

    const factorGroup = group('3. 판단 요소');
    factorGroup.append(element('p', 'muted', '판결에 중요하게 고려한 요소를 고르고, 형량을 무겁게 했으면 ↑, 가볍게 했으면 ↓를 눌러 주세요.'));
    const factorList = element('ul', 'factor-list');
    for (const factor of form.factors) {
      const row = element('li', 'factor-row');
      const label = element('span', 'factor-label', factor.label);
      label.id = `verdict-${caseId}-factor-${factor.factorId}`;
      const actions = element('div', 'factor-actions');
      actions.setAttribute('role', 'group');
      actions.setAttribute('aria-labelledby', label.id);
      for (const [direction, text] of [['UP', '↑ 무겁게'], ['DOWN', '↓ 가볍게']]) {
        const toggle = button(text, () => {
          if (draft.factors[factor.factorId] === direction) delete draft.factors[factor.factorId];
          else draft.factors[factor.factorId] = direction;
          changed('factors');
        }, 'factor-button');
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
        prisonMonths: draft.penaltyType === 'PRISON' ? value.sentence.value : null,
        fineAmount: draft.penaltyType === 'FINE' ? value.sentence.value : null,
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
            const target = serverError === 'INVALID_SUSPENSION' ? inputs.suspensionYears
              : draft.penaltyType === 'PRISON' ? inputs.prisonYears : inputs.fineAmount;
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
    root.replaceChildren(header, layout);

    function update() {
      const prison = draft.penaltyType === 'PRISON';
      const option = form.penaltyOptions.find((item) => item.penaltyType === draft.penaltyType);
      const sentence = prison ? parsePeriod(draft.prisonYears, draft.prisonMonths) : parseInteger(draft.fineAmount);
      const present = Boolean(option) && sentence.value !== null && !sentence.invalid;
      const inRange = present && sentence.value >= option.allowedMin && sentence.value <= option.allowedMax;
      const eligible = inRange && option.suspensionAllowed
        && sentence.value <= (prison ? rule.maxPrisonMonths : rule.maxFineAmount);
      if (!eligible) clearSuspension();
      const suspension = parsePeriod(draft.suspensionYears, draft.suspensionMonths);
      const suspensionInvalid = draft.suspended && (suspension.invalid || suspension.value === null
        || suspension.value < rule.minMonths || suspension.value > rule.maxMonths);
      chooseHint.hidden = Boolean(option);
      prisonFields.hidden = !prison;
      fineFields.hidden = draft.penaltyType !== 'FINE';
      sentenceError.textContent = sentence.invalid
        ? prison ? '숫자로 입력해 주세요. 개월은 0 ~ 11입니다.' : '금액은 0 이상의 정수로 입력해 주세요.' : '';
      for (const key of ['prisonYears', 'prisonMonths', 'fineAmount']) inputs[key].setAttribute('aria-invalid', String(sentence.invalid));
      money.textContent = !prison && present ? formatMoney(sentence.value) : '';
      rangeBox.replaceChildren();
      if (option) renderRecommendedRangeBar(rangeBox, { law, markerMonths: prison && present ? sentence.value : null });
      feedback.replaceChildren();
      if ((present && !inRange) || serverError === 'OUT_OF_ALLOWED_RANGE') {
        const warning = element('div', 'range-warning');
        warning.setAttribute('role', 'alert');
        warning.append(element('strong', '', '! 선고할 수 있는 범위를 벗어났습니다.'),
          element('p', '', `이 사건에서 선고할 수 있는 형량은 ${option.text}입니다. 범위 안으로 형량을 조정해야 판결을 확정할 수 있어요.`));
        feedback.append(warning);
      } else if (prison && inRange && sentence.value >= form.recommended.minMonths && sentence.value <= form.recommended.maxMonths) {
        feedback.append(element('p', 'range-reference', '입력한 형량은 권고 범위 안에 있습니다.'));
      }
      // 확장: REQ-036 S-06b — 권고 범위 밖이지만 선고 가능하면 별도 경고 없음.
      suspensionArea.hidden = !inRange;
      suspensionLabel.hidden = !eligible;
      checkbox.checked = draft.suspended;
      suspensionFields.hidden = !eligible || !draft.suspended;
      suspensionHint.hidden = eligible;
      suspensionError.textContent = serverError === 'INVALID_SUSPENSION' ? '집행유예 조건을 다시 확인해 주세요.'
        : suspensionInvalid ? `집행유예 기간은 ${formatMonths(rule.minMonths)} ~ ${formatMonths(rule.maxMonths)} 사이로 입력해 주세요.` : '';
      inputs.suspensionYears.setAttribute('aria-invalid', String(suspensionInvalid || serverError === 'INVALID_SUSPENSION'));
      inputs.suspensionMonths.setAttribute('aria-invalid', String(suspensionInvalid || serverError === 'INVALID_SUSPENSION'));
      for (const { toggle, factorId, direction, row } of factorButtons) {
        toggle.setAttribute('aria-pressed', String(draft.factors[factorId] === direction));
        row.classList.toggle('is-selected', Boolean(draft.factors[factorId]));
      }
      const disabledReason = !present ? '형량을 입력해 주세요.'
        : !inRange || serverError === 'OUT_OF_ALLOWED_RANGE' ? '선고할 수 있는 범위 안으로 형량을 조정해 주세요.'
          : suspensionInvalid || serverError === 'INVALID_SUSPENSION' ? '집행유예 기간을 확인해 주세요.' : '';
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
