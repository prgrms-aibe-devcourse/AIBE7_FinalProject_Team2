import { button, element } from '../../components/siteLayout.js';
import { confirmChange, labeledField } from './adminKit.js';
import { CAST_NAME, REVIEW_STATUS, SCRIPT_SIDE, formatDateTime } from './adminLabels.js';
import {
  FIX_ORDER, MAX_CHARS, MAX_LINES, SCRIPT_STATUS, SCRIPT_UNITS, UNIT_LABEL, basisText, blockApproveGate, blockingStaleUnits,
  countChars, issuesAt, leafLabel, opensToUsers, takeDownConfirm, textLeaves, textViolation,
} from './adminScriptBlocks.js';

// 사건 대본 검수 (A-13-10 ~ 13 · A-13-18 ~ 21, FE-106 · FE-148). 사건 검수 상세에 붙는 부분이다.
//
// 대본은 단위 블록 U0 ~ U4로 저장된다(BE-126). 처음 검수는 통승인 · 통거절이고, 반려 · 재생성 뒤에는 블록마다 승인한다
// (화면 흐름 — 서버는 둘 다 받는다). 블록 값은 모양을 따로 알지 않고 문자열 자리(JSON Pointer)로 그린다 — 검수 결과 path와 같은
// 자리라 오류 줄에서 바로 고칠 수 있다(A-13-21). U3(증거조사 대사)만 대사 수정(A-13-13)으로 고친다.
//
// 승인을 막는 자리는 서버와 같은 조건을 미리 보여 주는 것이다(서버도 409로 막는다): 대본 조건(멈춤 · 만들지 못한 것 · 대본 전체
// 검수 오류) → 블록 오류 → 승인을 막는 낡음(사건 내용 · 기사 · 민감 플래그). 낡음은 표시가 기본이고 "확인했음"(A-13-20)으로 둘 수 있다.
//
// **공개 중 수정 = 점검** (BE-126 2차-2): 사용자에게 나간 대본(releasedAt)의 승인을 푸는 요청은 서버가 막지 않고 대본이 점검 중이
// 된다(API 18 503). 오탈자 하나로 체험이 잠기는 실수를 막으려고 그런 요청 전에는 확인을 받는다(PR #384 리뷰 1번).

/** 고치기 폼 하나 — 대사(U3) · 블록 문장(U0 · U1 · U2 · U4)이 함께 쓴다. 저장하면 onSave(value)를 부른다 */
function editForm({ label, value, saveText, onSave, error }) {
  const form = element('form', 'admin-inline-form');
  form.hidden = true;
  form.noValidate = true;
  const input = element('textarea');
  Object.assign(input, { rows: 2, value: value ?? '' });
  const counter = element('p', 'admin-sub');
  const sync = () => {
    const chars = countChars(input.value);
    counter.textContent = `${chars} / ${MAX_CHARS}자 · ${MAX_LINES}줄까지`;
    counter.className = chars > MAX_CHARS ? 'admin-error' : 'admin-sub';
  };
  input.addEventListener('input', sync);
  sync();
  const send = element('button', 'primary-button', saveText);
  send.type = 'submit';
  form.append(labeledField(label, input), counter, send);
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    error.textContent = '';
    const text = input.value.trim();
    const violation = textViolation(text);
    if (violation) { error.textContent = violation; input.focus(); return; }
    send.disabled = true;
    onSave(text, () => { send.disabled = false; });
  });
  return { form, input };
}

/** 펼치기 버튼 — 누르면 폼을 열고 첫 입력칸으로 간다 */
function toggleButton(text, ariaLabel, form, focusTarget) {
  const toggle = button(text, () => {
    form.hidden = !form.hidden;
    toggle.setAttribute('aria-expanded', String(!form.hidden));
    if (!form.hidden) focusTarget.focus();
  });
  toggle.setAttribute('aria-expanded', 'false');
  toggle.setAttribute('aria-label', ariaLabel);
  return toggle;
}

/** 검수 결과 목록 (오류는 빨강 계열, 경고는 주의 계열). 없으면 null */
function issueList(issues) {
  if (!issues.length) return null;
  const list = element('ul', 'admin-warnings');
  issues.forEach((issue) => {
    const item = element('li', issue.severity === 'error' ? 'admin-error' : '',
      `${issue.severity === 'error' ? '오류' : '경고'} · ${issue.message ?? issue.code}`);
    list.append(item);
  });
  return list;
}

/** 승인을 푸는 요청 전에 묻는다. 묻지 않아도 되거나 관리자가 계속하기로 하면 true */
function confirmTakeDown(script, what) {
  const question = takeDownConfirm(script, what);
  return !question || confirmChange(...question);
}

/** 증거조사 대사 한 줄 (U3). 고치기를 펼치면 그 자리에서 수정한다(A-13-13) */
function lineItem(ctx, script, line, index, block) {
  const { api, caseId, after, handleFailure } = ctx;
  const item = element('li', 'admin-script-line');
  const manual = line.validation?.manual === true;
  const warnings = line.validation?.warnings ?? [];

  const head = element('div', 'admin-candidate-head');
  const name = element('strong', '', `${SCRIPT_SIDE[line.role] ?? line.role}`);
  name.tabIndex = -1;	// 고친 뒤 이 줄로 돌아올 자리
  name.dataset.anchor = `script:${line.factorId}`;
  head.append(name, element('span', 'admin-sub', CAST_NAME[line.character] ?? line.character ?? ''));
  if (manual) head.append(element('span', 'admin-badge is-approved', '사람이 고침'));
  if (line.editedAt) head.append(element('span', 'admin-sub', `고친 때 ${formatDateTime(line.editedAt)}`));
  if ((line.attempts ?? 1) > 1) head.append(element('span', 'admin-sub', `${line.attempts}번 만들어 봄`));

  // 어느 사실에 대한 대사인지. 서버가 붙여 주므로 화면이 판단 요소를 따로 찾지 않는다
  const label = element('p', 'admin-sub', line.label ?? '(지워진 판단 요소예요)');
  const text = line.text
    ? element('p', 'admin-script-text', line.text)
    : element('p', 'admin-warn', '이 줄은 끝내 만들지 못했어요. 직접 채워 주세요.');

  const error = element('p', 'admin-error');
  error.setAttribute('role', 'alert');
  const { form, input } = editForm({
    label: '대사', value: line.text, saveText: '이 대사 저장', error,
    onSave: (value, release) => {
      if (!confirmTakeDown(script, `${line.label ?? `요소 ${line.factorId}`} 대사를 고칠까요?`)) { release(); return; }
      api.updateCaseScriptLine(caseId, line.factorId, value)
        .then(() => after('대사를 고쳤어요. U3 블록을 다시 검수해 주세요.', `script:${line.factorId}`))
        .catch((failure) => { handleFailure(failure, '대사를 고치지 못했어요.', error); release(); });
    },
  });
  const edit = toggleButton(line.text ? '고치기' : '직접 채우기',
    `${line.label ?? `요소 ${line.factorId}`} 대사 ${line.text ? '고치기' : '직접 채우기'}`, form, input);

  const actions = element('div', 'admin-actions');
  actions.append(edit);
  item.append(head, label, text, actions, form, error);
  // 줄별 경고 · 그 줄의 검수 결과는 그 줄에 붙여 둔다 — 어느 대사에 대한 것인지 화면이 따로 잇지 않아도 된다
  if (warnings.length) {
    const list = element('ul', 'admin-warnings');
    warnings.forEach((warning) => list.append(element('li', '', warning)));
    item.append(list);
  }
  const issues = issueList(issuesAt(block.review, `/${index}`));
  if (issues) item.append(issues);
  return item;
}

/** 블록 문장 한 자리 (U0 · U1 · U2 · U4). 그 자리의 검수 결과를 붙이고 고치기를 펼친다(A-13-21) */
function leafItem(ctx, script, block, leaf) {
  const { api, caseId, after, handleFailure } = ctx;
  const item = element('li', 'admin-script-line');
  const anchor = `script:${block.unit}:${leaf.path}`;
  const head = element('div', 'admin-candidate-head');
  const name = element('strong', '', leafLabel(leaf.path));
  name.tabIndex = -1;
  name.dataset.anchor = anchor;
  head.append(name, element('code', 'admin-sub', leaf.path));
  const text = leaf.text
    ? element('p', 'admin-script-text', leaf.text)
    : element('p', 'admin-warn', '비어 있는 자리예요.');

  const error = element('p', 'admin-error');
  error.setAttribute('role', 'alert');
  const { form, input } = editForm({
    label: leafLabel(leaf.path), value: leaf.text, saveText: '이 문장 저장', error,
    onSave: (value, release) => {
      if (!confirmTakeDown(script, `${block.unit} ${leafLabel(leaf.path)}을(를) 고칠까요?`)) { release(); return; }
      api.updateScriptBlockText(caseId, block.unit, leaf.path, value)
        .then(() => after(`${block.unit} 문장을 고쳤어요. 이 블록을 다시 검수해 주세요.`, anchor))
        .catch((failure) => { handleFailure(failure, '문장을 고치지 못했어요.', error); release(); });
    },
  });
  const edit = toggleButton('고치기', `${block.unit} ${leafLabel(leaf.path)} 고치기`, form, input);
  const actions = element('div', 'admin-actions');
  actions.append(edit);
  item.append(head, text);
  const issues = issueList(issuesAt(block.review, leaf.path));
  if (issues) item.append(issues);
  item.append(actions, form, error);
  return item;
}

/** 자동으로 고친 기록 (BE-122 2차 repairs). 고치기 전 · 후 문장을 관리자가 본다 */
function repairsDetails(repairs) {
  if (!repairs?.length) return null;
  const details = element('details', 'admin-script-repairs');
  details.append(element('summary', '', `자동으로 고친 기록 ${repairs.length}건`));
  const list = element('ul', 'admin-warnings');
  repairs.forEach((repair) => {
    const where = repair.action === 'unit' ? '블록 전체' : leafLabel(repair.path ?? '');
    const result = repair.ok ? '고침' : '못 고침';
    const change = repair.before || repair.after ? ` — "${repair.before ?? ''}" → "${repair.after ?? ''}"` : '';
    list.append(element('li', '', `${where} · ${(repair.codes ?? []).join(' · ')} · ${result}${change}`));
  });
  details.append(list);
  return details;
}

/** 블록 하나: 머리줄(검수 상태 · 판 · 낡음) → 낡음 안내 → 내용 → 자동 고친 기록 → 승인 · 반려 · 확인했음 */
function blockItem(ctx, script, block, caseStatus) {
  const { api, caseId, after, handleFailure } = ctx;
  const item = element('li', 'admin-script-block');
  const error = element('p', 'admin-error');
  error.setAttribute('role', 'alert');

  const head = element('div', 'admin-candidate-head');
  const title = element('strong', '', `${block.unit} ${UNIT_LABEL[block.unit] ?? ''}`);
  title.tabIndex = -1;	// 승인 · 반려 · 확인했음 뒤 이 블록으로 돌아올 자리
  title.dataset.anchor = `script:${block.unit}`;
  const status = block.reviewStatus ?? 'PENDING';
  head.append(title, element('span', `admin-badge is-${status.toLowerCase()}`, REVIEW_STATUS[status] ?? status),
    element('span', 'admin-sub', `${block.revision}판`));
  if (block.stale) {
    head.append(element('span', `admin-badge ${block.staleBlocksApproval ? 'is-rejected' : 'is-pending'}`,
      block.staleBlocksApproval ? '낡음 · 승인 막힘' : '낡음'));
  }
  item.append(head);
  if (block.reviewedBy) item.append(element('p', 'admin-sub', `${block.reviewedBy} · ${formatDateTime(block.reviewedAt)}`));
  if (block.reviewNote) item.append(element('p', 'admin-warn', `반려 사유: ${block.reviewNote}`));

  if (block.stale) {
    const reasons = basisText(block.staleReasons);
    item.append(element('p', block.staleBlocksApproval ? 'admin-warn' : 'admin-sub', block.staleBlocksApproval
      ? `만든 뒤 바뀐 값: ${reasons}. 옛 값을 보고 만든 블록이라 승인이 막혀요 — 다시 생성하거나, 그대로 둬도 되면 "확인했음"으로 표시해 주세요.`
      : `만든 뒤 바뀐 값: ${reasons}. 표시만 해요 — 다시 만들지 그대로 둘지 골라 주세요.`));
  }
  if (block.unknownBasis?.length) {
    item.append(element('p', 'admin-sub', `근거가 없어 비교하지 못한 값: ${basisText(block.unknownBasis)} (이관 · 증거조사만 만든 대본)`));
  }
  const blockIssues = issueList(issuesAt(block.review, ''));
  if (blockIssues) item.append(blockIssues);
  if (block.warnings?.length) {
    const list = element('ul', 'admin-warnings');
    block.warnings.forEach((warning) => list.append(element('li', '', typeof warning === 'string' ? warning : JSON.stringify(warning))));
    item.append(list);
  }

  // 내용: 승인한 블록은 접어 둔다(검수할 것만 펼친다). 펼쳐서 고칠 수 있다
  const content = element('details', 'admin-script-content');
  content.open = status !== 'APPROVED';
  const list = element('ul', 'admin-script-lines');
  if (block.unit === 'U3') {
    (Array.isArray(block.content) ? block.content : []).forEach((line, index) => list.append(lineItem(ctx, script, line, index, block)));
    content.append(element('summary', '', `대사 ${list.childElementCount}줄`), list);
  } else {
    const leaves = textLeaves(block.content);
    leaves.forEach((leaf) => list.append(leafItem(ctx, script, block, leaf)));
    content.append(element('summary', '', `문장 ${leaves.length}자리`), list);
  }
  item.append(content);
  const repairs = repairsDetails(block.repairs);
  if (repairs) item.append(repairs);

  // ── 블록 승인 · 반려 · 확인했음 (A-13-18 ~ 20)
  const actions = element('div', 'admin-actions');
  const gate = blockApproveGate(script, block);
  const approve = button(`${block.unit} 승인`, () => {
    if (opensToUsers(script, block.unit, caseStatus)
      && !confirmChange(`${block.unit} 블록을 승인할까요?`, '마지막 블록이라 승인하면 바로 사용자에게 이 대본으로 체험이 열려요.')) return;
    approve.disabled = true;
    error.textContent = '';
    api.approveScriptBlock(caseId, block.unit)
      .then(() => after(`${block.unit} 블록을 승인했어요.`, `script:${block.unit}`))
      .catch((failure) => { handleFailure(failure, '블록을 승인하지 못했어요.', error); approve.disabled = false; });
  }, 'primary-button');
  approve.disabled = Boolean(gate) || status === 'APPROVED';
  if (gate && status !== 'APPROVED') {
    const note = element('p', 'admin-sub', gate);
    note.id = `admin-script-gate-${block.unit}`;
    // 대본 조건(위 대본 안내) · 낡음(위 낡음 안내)은 화면에 이미 보인다 — 블록 오류일 때만 보이고, 나머지는 버튼 설명으로만 잇는다
    note.hidden = block.publishable;
    approve.setAttribute('aria-describedby', note.id);
    item.append(note);
  }

  const rejectForm = element('form', 'admin-reject');
  rejectForm.hidden = true;
  rejectForm.noValidate = true;
  const reason = element('textarea');
  Object.assign(reason, { rows: 2, maxLength: 500 });
  const rejectSend = element('button', 'primary-button', `${block.unit} 반려하기`);
  rejectSend.type = 'submit';
  rejectForm.append(labeledField('반려 사유 (1 ~ 500자)', reason), rejectSend);
  const reject = toggleButton(`${block.unit} 반려`, `${block.unit} ${UNIT_LABEL[block.unit] ?? ''} 반려`, rejectForm, reason);
  rejectForm.addEventListener('submit', (event) => {
    event.preventDefault();
    error.textContent = '';
    const note = reason.value.trim();
    if (!note) { error.textContent = '반려 사유를 입력해 주세요.'; reason.focus(); return; }
    if (!confirmTakeDown(script, `${block.unit} 블록을 반려할까요?`)) return;
    rejectSend.disabled = true;
    api.rejectScriptBlock(caseId, block.unit, note)
      .then(() => after(`${block.unit} 블록을 반려했어요.`, `script:${block.unit}`))
      .catch((failure) => { handleFailure(failure, '블록을 반려하지 못했어요.', error); rejectSend.disabled = false; });
  });
  actions.append(approve, reject);

  if (block.stale) {
    // 운영 기록(근거)만 바뀌는 작업이라 묻지 않는다(DESIGN.md 확인 기준). 새로 생긴 민감 플래그는 서버가 409로 막는다
    const acknowledge = button('확인했음 (그대로 두기)', () => {
      acknowledge.disabled = true;
      error.textContent = '';
      api.acknowledgeScriptBlockStale(caseId, block.unit)
        .then(() => after(`${block.unit} 블록을 확인했음으로 표시했어요.`, `script:${block.unit}`))
        .catch((failure) => { handleFailure(failure, '확인했음으로 표시하지 못했어요.', error); acknowledge.disabled = false; });
    });
    acknowledge.setAttribute('aria-label', `${block.unit} ${UNIT_LABEL[block.unit] ?? ''} 낡음 확인했음`);
    actions.append(acknowledge);
  }
  item.append(actions, rejectForm, error);
  return item;
}

/** 대본 상태 줄 — 사용자 쪽 상태(scriptStatus)와 처음 나간 때. 점검 중이면 무엇이 잠겼고 어떻게 푸는지 알린다 */
function statusParts(script) {
  const parts = [];
  const status = script.scriptStatus;
  if (status === 'MAINTENANCE') {
    parts.push(element('p', 'admin-warn', '점검 중이에요 — 사용자에게 이 사건 체험이 잠겨 있어요(대본 API 503). '
      + '승인이 풀린 블록을 다시 승인해 다섯 블록이 모두 승인되면 풀려요.'));
  } else if (status === 'READY' && script.releasedAt) {
    parts.push(element('p', 'admin-sub', '사용자에게 공개 중인 대본이에요. 블록을 반려하거나 고치면 다시 승인할 때까지 점검 중이 돼요.'));
  }
  if (script.releasedAt) parts.push(element('p', 'admin-sub', `처음 사용자에게 공개된 때 ${formatDateTime(script.releasedAt)}`));
  return parts;
}

/**
 * 대본 검수 부분을 그린다.
 *
 * @param ctx.media.script  A-13-10 응답(없으면 null — 대본 없이 등록한 사건)
 * @param data              A-6 사건 상세 (사건 상태로 "마지막 블록 승인 = 사용자에게 공개"를 판단한다)
 */
export function scriptSection(ctx, data) {
  const { api, caseId, section, media, after, errorLine, handleFailure } = ctx;
  const script = media.script;

  if (!script) {
    return section('대본 검수', 'script',
      element('p', 'admin-sub', '이 사건에는 대본이 없어요. 대본 생성을 켠 서버로 등록한 사건에만 대본이 생겨요.'),
      element('p', 'admin-sub', '대본이 없어도 사건은 공개할 수 있어요 — 사용자에게는 "준비 중"으로 보여요.'));
  }

  const error = errorLine();
  const parts = [];

  // 검수 상태 줄 — 판결 후보(A-8)와 같은 모양에 사용자 쪽 상태를 더한다
  const head = element('div', 'admin-candidate-head');
  const state = element('span', `admin-badge is-${(script.reviewStatus ?? 'PENDING').toLowerCase()}`,
    REVIEW_STATUS[script.reviewStatus] ?? script.reviewStatus);
  head.append(element('strong', '', '대본'), state);
  if (script.scriptStatus) {
    const tone = { READY: 'is-published', MAINTENANCE: 'is-rejected', PREPARING: 'is-pending' }[script.scriptStatus] ?? '';
    head.append(element('span', `admin-badge ${tone}`, SCRIPT_STATUS[script.scriptStatus] ?? script.scriptStatus));
  }
  head.append(element('span', 'admin-sub', `${script.model ?? ''} · 만든 때 ${formatDateTime(script.generatedAt)}`));
  parts.push(head, ...statusParts(script));

  if (script.reviewedBy) {
    parts.push(element('p', 'admin-sub', `마지막 검수 ${script.reviewedBy} · ${formatDateTime(script.reviewedAt)}`));
  }
  // 대본 전체에 대한 경고(분량 불균형)는 블록 · 줄별 경고와 섞이지 않게 따로 둔다
  if (script.balanceWarning) {
    parts.push(element('p', 'admin-warn', `${script.balanceWarning} (대사를 고쳤다면 고치기 전 기준이에요)`));
  }
  const cast = script.meta?.cast ?? script.cast ?? {};
  const castName = (code) => CAST_NAME[code] ?? code ?? '—';
  parts.push(element('p', 'admin-sub', `배역 — 검사 ${castName(cast.prosecutor)} · 변호인 ${castName(cast.defense)}`
    + `${script.meta?.issue ? ` · 쟁점 ${script.meta.issue}` : ''}`
    + `${script.meta?.admits == null ? '' : ` · 혐의 ${script.meta.admits ? '인정' : '부인'}`}`));

  // 대본 전체에 걸린 막힘: 멈춤 · 만들지 못한 것 · 대본 전체 검수 오류 — 블록을 고쳐도 풀리지 않아 "다시 생성"이 길이다
  if (script.stopped) parts.push(element('p', 'admin-warn', '호출 한도로 중간에 멈춘 대본이에요. 빠진 대사는 줄 자체가 없어서 고칠 수 없어요 — 다시 생성해야 해요.'));
  if (script.pending?.length) {
    const names = script.pending.map((unit) => (UNIT_LABEL[unit] ? `${unit} ${UNIT_LABEL[unit]}` : unit === 'PAPER' ? '기사(PAPER)' : unit));
    parts.push(element('p', 'admin-warn', `만들지 못한 것: ${names.join(' · ')}. 다시 생성해야 승인할 수 있어요.`));
  }
  const scriptIssues = issueList(script.review ?? []);
  if (scriptIssues) parts.push(scriptIssues);
  if ((script.blocks ?? []).some((block) => block.stale)) {
    parts.push(element('p', 'admin-sub', `낡은 블록이 여럿이면 앞에서부터 고쳐요: ${FIX_ORDER}`));
  }

  // ── 블록 (U0 → U4). 만들지 못한 단위는 자리만 보인다
  const blocks = element('ul', 'admin-script-blocks');
  SCRIPT_UNITS.forEach((unit) => {
    const block = (script.blocks ?? []).find((entry) => entry.unit === unit);
    if (block) { blocks.append(blockItem(ctx, script, block, data?.status)); return; }
    const missing = element('li', 'admin-script-block is-missing');
    missing.append(element('strong', '', `${unit} ${UNIT_LABEL[unit]}`), element('p', 'admin-sub', '만들지 못한 블록이에요.'));
    blocks.append(missing);
  });
  parts.push(blocks);

  // ── 통승인 · 통거절 (A-13-11 · 12). 처음 검수는 이것으로 하고, 반려 · 재생성 뒤에는 블록마다 승인한다
  const allApproved = script.reviewStatus === 'APPROVED';
  const stale = blockingStaleUnits(script);
  const actions = element('div', 'admin-actions');
  const approve = button('모든 블록 승인', () => {
    const effect = data?.status === 'PUBLISHED'
      ? '공개 중인 사건이라 승인하면 바로 사용자에게 이 대본으로 체험이 열려요.'
      : '사건을 공개하면 이 대본으로 체험이 열려요.';
    if (!confirmChange('대본의 모든 블록을 승인할까요?', effect)) return;
    approve.disabled = true;
    error.textContent = '';
    api.approveCaseScript(caseId)
      .then(() => after('대본을 승인했어요.', 'script'))
      .catch((failure) => { handleFailure(failure, '승인하지 못했어요.', error); approve.disabled = false; });
  }, 'primary-button');
  approve.disabled = !script.publishable || allApproved || stale.length > 0;
  const gateText = !script.publishable
    ? '만들지 못한 것 · 검수 오류가 남아 있어 모두 승인할 수 없어요. 블록마다 안내를 확인해 주세요.'
    : stale.length ? `승인을 막는 낡음이 있는 블록(${stale.join(' · ')})을 먼저 풀어 주세요.` : null;
  if (gateText && !allApproved) {
    const note = element('p', 'admin-sub', gateText);
    note.id = 'admin-script-blocked';
    approve.setAttribute('aria-describedby', note.id);
    parts.push(note);
  }

  const rejectForm = element('form', 'admin-reject');
  rejectForm.hidden = true;
  rejectForm.noValidate = true;
  const reason = element('textarea');
  Object.assign(reason, { rows: 2, maxLength: 500 });
  const rejectSend = element('button', 'primary-button', '모든 블록 반려하기');
  rejectSend.type = 'submit';
  rejectForm.append(labeledField('반려 사유 (1 ~ 500자)', reason), rejectSend);
  const reject = toggleButton('모든 블록 반려', '대본의 모든 블록 반려', rejectForm, reason);
  rejectForm.addEventListener('submit', (event) => {
    event.preventDefault();
    error.textContent = '';
    const note = reason.value.trim();
    if (!note) { error.textContent = '반려 사유를 입력해 주세요.'; reason.focus(); return; }
    if (!confirmTakeDown(script, '대본의 모든 블록을 반려할까요?')) return;
    rejectSend.disabled = true;
    api.rejectCaseScript(caseId, note)
      .then(() => after('대본을 반려했어요.', 'script'))
      .catch((failure) => { handleFailure(failure, '반려하지 못했어요.', error); rejectSend.disabled = false; });
  });

  actions.append(approve, reject);
  parts.push(actions, rejectForm, error);
  parts.push(element('p', 'admin-sub', '대본 검수는 사건 공개와 별개예요. 승인하지 않아도 사건은 공개할 수 있고, 그동안 사용자에게는 "준비 중"으로 보여요.'));
  return section('대본 검수', 'script', ...parts);
}
