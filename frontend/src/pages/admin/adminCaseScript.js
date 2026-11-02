import { button, element } from '../../components/siteLayout.js';
import { confirmChange, labeledField } from './adminKit.js';
import { REVIEW_STATUS, SCRIPT_SIDE, formatDateTime } from './adminLabels.js';

// 사건 대본 검수 (A-13-10 ~ A-13-13, FE-106). 사건 검수 상세에 붙는 부분이다.
//
// **담는 것은 증거조사 대사뿐이다** — 1막 기사 해설 · 피고인신문 · 최종의견은 파이프라인 범위 밖이다
// (COMMON-65 · FE-33). 공개 체험이 이 대사를 받아 쓰는 연결은 BE-109 뒤 별도 이슈다.
//
// 검수 칸(reviewStatus · isPublished · reviewedBy · reviewedAt · reviewNote)은 판결 후보 A-8과 이름 ·
// 형식이 같아서, 후보 검수와 같은 모양으로 그린다.
//
// 승인을 막는 자리가 둘이다. publishable이 false면 버튼을 잠그고(서버도 409로 막는다), 그 이유를 둘로
// 나눠 안내한다 — 끝내 만들지 못한 줄이 있으면 "고치거나 다시 생성", 호출 한도로 멈췄으면(stopped)
// **고쳐도 올라가지 않으므로** "다시 생성"만 길이다(BE-108).
const MAX_CHARS = 100;	// 파이프라인 MAX_CHARS와 같은 값
const MAX_LINES = 2;

/** 줄바꿈을 뺀 글자 수. 파이프라인이 세는 방식과 같다(sum(len(line))) */
function countChars(text) {
  return text.split('\n').filter((part) => part.trim()).reduce((sum, part) => sum + part.length, 0);
}

function lineViolation(text) {
  const lines = text.split('\n').filter((part) => part.trim());
  if (!lines.length) return '대사를 입력해 주세요.';
  if (lines.length > MAX_LINES) return `대사는 ${MAX_LINES}줄까지 쓸 수 있어요.`;
  const chars = countChars(text);
  if (chars > MAX_CHARS) return `대사는 줄바꿈을 빼고 ${MAX_CHARS}자까지 쓸 수 있어요. (지금 ${chars}자)`;
  return null;
}

/** 왜 승인할 수 없는지. 관리자가 다음에 할 일이 갈리므로 둘을 나눈다 */
function blockedReason(script) {
  if (script.publishable) return null;
  if (script.stopped) {
    return '호출 한도로 중간에 멈춘 대본이에요. 빠진 대사는 줄 자체가 없어서 고칠 수 없어요 — 다시 생성해야 해요.';
  }
  const missing = script.evidence.filter((line) => !line.text).length;
  return `끝내 만들지 못한 대사가 ${missing}줄 있어요. 그 줄을 직접 채우면 승인할 수 있어요.`;
}

/** 대사 한 줄. 고치기를 펼치면 그 자리에서 수정한다(A-13-13) */
function lineItem(ctx, script, line) {
  const { api, caseId, after, handleFailure } = ctx;
  const item = element('li', 'admin-script-line');
  const manual = line.validation?.manual === true;
  const warnings = line.validation?.warnings ?? [];

  const head = element('div', 'admin-candidate-head');
  const name = element('strong', '', `${SCRIPT_SIDE[line.role] ?? line.role}`);
  name.tabIndex = -1;	// 고친 뒤 이 줄로 돌아올 자리
  name.dataset.anchor = `script:${line.factorId}`;
  head.append(name, element('span', 'admin-sub', line.character ?? ''));
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
  const form = element('form', 'admin-inline-form');
  form.hidden = true;
  form.noValidate = true;
  const input = element('textarea');
  Object.assign(input, { rows: 2, value: line.text ?? '' });
  const counter = element('p', 'admin-sub');
  const sync = () => {
    const chars = countChars(input.value);
    counter.textContent = `${chars} / ${MAX_CHARS}자 · ${MAX_LINES}줄까지`;
    counter.className = chars > MAX_CHARS ? 'admin-error' : 'admin-sub';
  };
  input.addEventListener('input', sync);
  sync();
  const send = element('button', 'primary-button', '이 대사 저장');
  send.type = 'submit';
  form.append(labeledField('대사', input), counter, send);

  const edit = button(line.text ? '고치기' : '직접 채우기', () => {
    form.hidden = !form.hidden;
    edit.setAttribute('aria-expanded', String(!form.hidden));
    if (!form.hidden) input.focus();
  });
  edit.setAttribute('aria-expanded', 'false');
  edit.setAttribute('aria-label', `${line.label ?? `요소 ${line.factorId}`} 대사 ${line.text ? '고치기' : '직접 채우기'}`);

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    error.textContent = '';
    const value = input.value.trim();
    const violation = lineViolation(value);
    if (violation) { error.textContent = violation; input.focus(); return; }
    // 고치면 검수 기록이 지워져 다시 대기가 된다 — 승인해 둔 대본이면 공개가 내려가므로 먼저 묻는다
    const approved = script.reviewStatus === 'APPROVED';
    if (approved && !confirmChange('이 대사를 고칠까요?', '승인한 대본이라 고치면 검수 대기로 돌아가고 공개가 내려가요.')) return;
    send.disabled = true;
    api.updateCaseScriptLine(caseId, line.factorId, value)
      .then(() => after('대사를 고쳤어요. 다시 검수해 주세요.', `script:${line.factorId}`))
      .catch((failure) => {
        handleFailure(failure, '대사를 고치지 못했어요.', error);
        send.disabled = false;
      });
  });

  const actions = element('div', 'admin-actions');
  actions.append(edit);
  item.append(head, label, text, actions, form, error);
  // 줄별 경고는 그 줄에 붙여 둔다 — 어느 대사에 대한 경고인지 화면이 따로 잇지 않아도 된다
  if (warnings.length) {
    const list = element('ul', 'admin-warnings');
    warnings.forEach((warning) => list.append(element('li', '', warning)));
    item.append(list);
  }
  return item;
}

/**
 * 대본 검수 부분을 그린다.
 *
 * @param ctx.media.script  A-13-10 응답(없으면 null — 기존 사건은 모두 이 경우다)
 */
export function scriptSection(ctx, data) {
  const { api, caseId, section, media, after, errorLine, handleFailure } = ctx;
  const script = media.script;

  if (!script) {
    return section('대본 검수', 'script',
      element('p', 'admin-sub', '이 사건에는 대본이 없어요. 파이프라인으로 등록한 사건에만 대본이 생겨요.'),
      element('p', 'admin-sub', '대본이 없어도 사건은 공개할 수 있어요 — 체험은 기존 화면으로 진행돼요.'));
  }

  const error = errorLine();
  const parts = [];

  // 검수 상태 줄 — 판결 후보(A-8)와 같은 모양
  const head = element('div', 'admin-candidate-head');
  const state = element('span', `admin-badge is-${(script.reviewStatus ?? 'PENDING').toLowerCase()}`,
    REVIEW_STATUS[script.reviewStatus] ?? script.reviewStatus);
  head.append(element('strong', '', '증거조사 대사'), state);
  if (script.isPublished) head.append(element('span', 'admin-badge is-published', '공개 중'));
  head.append(element('span', 'admin-sub', `${script.model ?? ''} · 만든 때 ${formatDateTime(script.generatedAt)}`));
  parts.push(head);

  if (script.reviewedBy) {
    parts.push(element('p', 'admin-sub', `${script.reviewedBy} · ${formatDateTime(script.reviewedAt)}`));
  }
  if (script.reviewNote) parts.push(element('p', 'admin-warn', `반려 사유: ${script.reviewNote}`));

  // 대본 전체에 대한 경고(분량 불균형)는 줄별 경고와 섞이지 않게 따로 둔다
  if (script.balanceWarning) {
    parts.push(element('p', 'admin-warn', `${script.balanceWarning} (대사를 고쳤다면 고치기 전 기준이에요)`));
  }
  const cast = script.cast ?? {};
  parts.push(element('p', 'admin-sub', `배역 — 검사 ${cast.prosecutor ?? '—'} · 변호인 ${cast.defense ?? '—'}`));

  const blocked = blockedReason(script);
  if (blocked) parts.push(element('p', 'admin-warn', blocked));

  const list = element('ul', 'admin-script-lines');
  script.evidence.forEach((line) => list.append(lineItem(ctx, script, line)));
  parts.push(list);

  // ── 승인 · 반려 (대본 전체 한 번. 다시 생성하면 검수 기록이 지워져 줄 단위 기록은 의미를 잃는다)
  const actions = element('div', 'admin-actions');
  const approve = button('승인 (공개 대본으로 정하기)', () => {
    if (!confirmChange('이 대본을 공개할까요?', '승인하면 공개 대본으로 정해져요. 사용자 체험 연결은 BE-109 이후예요.')) return;
    approve.disabled = true;
    error.textContent = '';
    api.approveCaseScript(caseId)
      .then(() => after('대본을 승인했어요.', 'script'))
      .catch((failure) => {
        handleFailure(failure, '승인하지 못했어요.', error);
        approve.disabled = false;
      });
  }, 'primary-button');
  approve.disabled = !script.publishable || script.reviewStatus === 'APPROVED';
  if (!script.publishable) approve.setAttribute('aria-describedby', 'admin-script-blocked');

  const rejectForm = element('form', 'admin-reject');
  rejectForm.hidden = true;
  rejectForm.noValidate = true;
  const reason = element('textarea');
  Object.assign(reason, { rows: 2, maxLength: 500 });
  const rejectSend = element('button', 'primary-button', '반려하기');
  rejectSend.type = 'submit';
  rejectForm.append(labeledField('반려 사유 (1 ~ 500자)', reason), rejectSend);
  const reject = button('반려', () => {
    rejectForm.hidden = !rejectForm.hidden;
    if (!rejectForm.hidden) reason.focus();
  });
  rejectForm.addEventListener('submit', (event) => {
    event.preventDefault();
    error.textContent = '';
    const note = reason.value.trim();
    if (!note) { error.textContent = '반려 사유를 입력해 주세요.'; reason.focus(); return; }
    // 승인한 대본을 반려로 내리면 공개가 내려간다 — 사용자가 보던 것을 거두는 작업이라 묻는다
    if (script.isPublished && !confirmChange('공개 중인 대본을 반려할까요?', '공개 대본 자리에서 바로 빠져요.')) return;
    rejectSend.disabled = true;
    api.rejectCaseScript(caseId, note)
      .then(() => after('대본을 반려했어요.', 'script'))
      .catch((failure) => {
        handleFailure(failure, '반려하지 못했어요.', error);
        rejectSend.disabled = false;
      });
  });

  actions.append(approve, reject);
  parts.push(actions, rejectForm, error);

  if (blocked) {
    const note = element('p', 'admin-sub admin-approve-gate', blocked);
    note.id = 'admin-script-blocked';
    note.hidden = true;	// 화면에는 위 경고로 이미 보인다. 승인 버튼의 설명으로만 잇는다
    parts.push(note);
  }

  parts.push(element('p', 'admin-sub', '대본 검수는 사건 공개와 별개예요. 승인하지 않아도 사건은 공개할 수 있어요.'));
  return section('대본 검수', 'script', ...parts);
}
