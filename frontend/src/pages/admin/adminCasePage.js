import { button, element } from '../../components/siteLayout.js';
import { confirmChange, labeledField } from './adminKit.js';
import {
  AXIS_LABEL, AXIS_OPTIONS, CASE_STATUS, COURT_LEVEL, CRIME_TYPE, DIFFICULTY, PUBLISH_REASON, REVEAL_STAGE, REVIEW_STATUS, SUBJECT,
  errorText, formatDateTime,
} from './adminLabels.js';
import { SUMMARY_TAG_STATE_TEXT, describeGeneration, describeSummaryTagCandidates } from './adminFormat.js';
import { renderAdminShell } from './adminShell.js';

// S-19 사건 검수 상세 (A-6 ~ A-13).
// 순서: 공개 준비(A-13) → 판결 후보(A-8 ~ A-10) → 판단 요소 가치관 축(A-12) → 사건 내용 수정(A-11) → 원본 판결문(A-7).
// 상태를 바꾼 뒤에는 A-6을 다시 불러와 화면 전체를 다시 그린다(사건 상태 · 공개 조건이 함께 바뀌기 때문).
// FE-105: 다시 그린 뒤 누른 자리(후보 · 판단 요소 줄 · 공개 · 사건 내용)로 돌아가 포커스를 두고, 펼쳐 둔 후보 상세는 다시 펼친다.
// 완료 문구는 틀의 알림줄(notify)이 맡는다. 맨 위 "바로 가기"로 원본 판결문 등 각 부분으로 건너뛴다.
// 후보 승인은 "자세히 보기"로 내용을 한 번 연 뒤에 할 수 있다(내용을 보지 않고 공개 판결로 정하지 않게)
// 모든 값은 textContent로 넣는다(원문 · 모델 출력에 HTML이 섞여 있어도 실행되지 않게).
const DIRECTION = { UP: '가중 ↑', DOWN: '감경 ↓' };
const SECTIONS = [['publish', '공개'], ['candidates', '판결 후보'], ['factors', '판단 요소'], ['content', '사건 내용'], ['sources', '원본 판결문']];
const CHECKLIST = [
  '사건 내용과 원본 판결문을 대조해 인명 · 지명 · 사건번호 등 비식별화를 확인했다',
  '공개할 AI 판결과 재판부 판결 후보의 내용을 확인했다',
  '적용 양형기준 버전과 권고 형량 범위를 확인했다',
];

// key가 있으면 맨 위 "바로 가기"가 이 부분의 제목으로 건너뛴다
function section(title, key, ...children) {
  const node = element('section', 'admin-section');
  const heading = element('h2', '', title);
  if (key) {
    node.id = `admin-case-${key}`;
    heading.tabIndex = -1;
    heading.dataset.anchor = key;
  }
  node.append(heading, ...children);
  return node;
}

function definitionList(rows) {
  const list = element('dl', 'admin-dl');
  rows.forEach(([label, value]) => {
    list.append(element('dt', '', label), element('dd', '', value === null || value === undefined || value === '' ? '—' : String(value)));
  });
  return list;
}

function jsonBlock(summary, value) {
  const details = element('details', 'admin-json');
  details.append(element('summary', '', summary), element('pre', '', JSON.stringify(value ?? null, null, 2)));
  return details;
}

function textBlock(summary, text) {
  const details = element('details', 'admin-json');
  details.append(element('summary', '', summary), element('pre', '', text));
  return details;
}

// A-8 generation: 모델 · 회차 선택 근거 · 사전 학습 점검 판정 · 검증 경고 · 참고 자료 검색 · AI에 넣은 입력
function generationView(generation) {
  const view = describeGeneration(generation);
  const wrap = element('div', 'admin-generation');
  wrap.append(element('h4', '', '생성 정보'), definitionList(view.model));
  if (view.selection) wrap.append(element('h4', '', '회차 선택 근거'), definitionList(view.selection));

  const verdict = element('p', `admin-verdict is-${view.contamination.level}`);
  verdict.append(element('strong', '', '사전 학습 점검: '), view.contamination.label);
  wrap.append(element('h4', '', '사전 학습 점검 판정'), verdict);
  if (view.contamination.rows.length) wrap.append(definitionList(view.contamination.rows));

  wrap.append(element('h4', '', `검증 경고 (${view.warnings.length})`));
  if (view.warnings.length) {
    const list = element('ul', 'admin-warnings');
    view.warnings.forEach((text) => list.append(element('li', '', text)));
    wrap.append(list);
  } else {
    wrap.append(element('p', 'admin-sub', '경고가 없어요.'));
  }

  wrap.append(element('h4', '', '참고 자료 검색 (RAG)'));
  if (view.rag.rows.length) wrap.append(definitionList(view.rag.rows));
  else wrap.append(element('p', 'admin-sub', '검색하지 않았어요. (기존 참고 자료를 그대로 썼거나 점검을 켜지 않음)'));

  wrap.append(element('h4', '', 'AI에 넣은 입력 (inputSnapshot)'));
  const { snapshot } = view;
  if (snapshot.promptVersion) wrap.append(definitionList([['입력 프롬프트 버전', snapshot.promptVersion]]));
  if (snapshot.system) wrap.append(textBlock('시스템 프롬프트', snapshot.system));
  if (snapshot.user) wrap.append(textBlock('사용자 프롬프트 (사건 내용 · 참고 자료)', snapshot.user));
  if (snapshot.other) wrap.append(jsonBlock('그 밖의 입력', snapshot.other));
  if (!snapshot.system && !snapshot.user && !snapshot.other) wrap.append(element('p', 'admin-sub', '저장된 입력이 없어요.'));

  wrap.append(jsonBlock('생성 보고서 원본 (generationReport)', generation.generationReport));
  return wrap;
}

// A-6 summaryTagCandidates: 요약어는 바꾸지 않고 제안만 보여 준다
function summaryTagView(raw, summaryTag) {
  const view = describeSummaryTagCandidates(raw);
  if (!view) return null;
  const box = element('div', 'admin-tag-candidates');
  box.append(element('span', `admin-sub is-${view.state.toLowerCase()}`, SUMMARY_TAG_STATE_TEXT[view.state]));
  if (view.state === 'SUGGESTED' && view.suggested !== summaryTag) box.append(element('strong', '', `제안: ${view.suggested}`));
  if (view.candidates.length) box.append(element('span', 'admin-sub', `후보 ${view.candidates.map((item) => `${item.tag} (${item.score})`).join(' · ')}`));
  return box;
}

export function renderAdminCasePage(container, { caseId, navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'cases',
    title: `사건 #${caseId} 검수`,
    draw: async ({ api, main, active, unauthorized, setTitle, notify }) => {
      const opened = new Set(); // 상세를 펼쳐 둔 후보(judgmentId). 다시 그려도 펼친 채로 둔다
      const details = new Map(); // 후보별로 그려 둔 상세 노드. 다시 그릴 때 요청 없이 그대로 복원한다(A-8을 또 부르지 않고 깜빡이지도 않게)
      const viewed = new Set(); // 한 번이라도 상세를 연 후보. 승인은 이 후보만 할 수 있다

      function handleFailure(failure, fallback, target) {
        if (!active()) return;
        if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
        target.textContent = errorText(failure, fallback);
      }

      // 상태를 바꾸는 동작: 버튼을 잠그고, 성공하면 A-6을 다시 불러와 그린 뒤 anchor 자리로 돌아가 알린다
      async function mutate(trigger, errorTarget, action, okText, anchor) {
        trigger.disabled = true;
        errorTarget.textContent = '';
        try {
          await action();
          await load();
          if (!active()) return;
          returnTo(anchor);
          notify(okText);
        } catch (failure) {
          handleFailure(failure, '요청을 처리하지 못했어요.', errorTarget);
          if (active()) trigger.disabled = false;
        }
      }

      // 다시 그린 화면에서 누른 자리를 찾아 보이게 하고 포커스를 둔다. 자리가 사라졌으면(예: 공개 뒤) 그 부분 제목으로
      function returnTo(anchor) {
        const [key] = anchor.split(':');
        const target = main.querySelector(`[data-anchor="${anchor}"]`) ?? main.querySelector(`[data-anchor="${key}"]`);
        if (!target) return;
        target.scrollIntoView?.({ block: 'nearest' });
        target.focus({ preventScroll: true });
      }

      function errorLine() {
        const line = element('p', 'admin-error');
        line.setAttribute('role', 'alert');
        return line;
      }

      // ── 공개 준비 (A-13)
      function publishSection(data) {
        const { status, publishReadiness, factors } = data;
        const body = [];
        if (status === 'PUBLISHED') {
          body.push(element('p', 'admin-ok', '공개한 사건이에요. 판결 교체 · 내용 수정은 막혀 있고, 반려 · 가치관 축 확정은 할 수 있어요.'));
          return section('공개', 'publish', ...body);
        }
        const reasons = element('ul', 'admin-reasons');
        if (publishReadiness.ready) reasons.append(element('li', 'admin-ok', '공개 조건을 모두 채웠어요.'));
        publishReadiness.reasons.forEach((code) => reasons.append(element('li', '', PUBLISH_REASON[code] ?? code)));
        body.push(reasons);
        const axisAuto = factors.filter((factor) => factor.valueAxisStatus === 'AUTO').length;
        if (axisAuto) body.push(element('p', 'admin-warn', `가치관 축을 아직 확정하지 않은 판단 요소가 ${axisAuto}개 있어요. 공개는 막지 않지만 확인을 권해요.`));

        const checks = element('fieldset', 'admin-checklist');
        checks.append(element('legend', '', '공개 전 확인'));
        const boxes = CHECKLIST.map((text) => {
          const label = element('label');
          const box = element('input');
          box.type = 'checkbox';
          label.append(box, element('span', '', text));
          checks.append(label);
          return box;
        });
        const error = errorLine();
        const publish = button('사건 공개하기', () => {
          if (!confirmChange('이 사건을 사용자에게 공개할까요?', '공개한 뒤에는 판결 교체 · 내용 수정을 할 수 없어요.')) return;
          mutate(publish, error, () => api.publishCase(caseId), '사건을 공개했어요.', 'publish');
        }, 'primary-button');
        const sync = () => { publish.disabled = !publishReadiness.ready || boxes.some((box) => !box.checked); };
        boxes.forEach((box) => box.addEventListener('change', sync));
        sync();
        body.push(checks, error, publish);
        return section('공개 준비', 'publish', ...body);
      }

      // ── 판결 후보 (A-8 ~ A-10)
      function judgmentDetail(detail) {
        const wrap = element('div', 'admin-judgment');
        const { judgment, generation } = detail;
        wrap.append(definitionList([
          ['요약', judgment?.summary],
          ['판단 이유', judgment?.reasoning],
          ['참고 태그', (judgment?.referenceTags ?? []).join(', ')],
          ['검수', detail.reviewedBy ? `${detail.reviewedBy} · ${formatDateTime(detail.reviewedAt)}` : null],
          ['반려 사유', detail.reviewNote],
        ]));
        const factors = element('ul', 'admin-factor-reasons');
        (judgment?.factors ?? []).forEach((factor) => {
          const item = element('li');
          item.append(element('strong', '', `${DIRECTION[factor.direction] ?? factor.direction} `), `${factor.label}`);
          if (factor.evidence) item.append(element('span', 'admin-sub', factor.evidence));
          factors.append(item);
        });
        if (factors.childElementCount) wrap.append(element('h4', '', '판단 요소별 방향'), factors);
        if (generation) wrap.append(generationView(generation));
        return wrap;
      }

      function candidateRow(candidate, caseStatus) {
        const item = element('li', 'admin-candidate');
        const head = element('div', 'admin-candidate-head');
        const badges = element('span', 'admin-badges');
        badges.append(element('span', `admin-badge is-${candidate.reviewStatus.toLowerCase()}`, REVIEW_STATUS[candidate.reviewStatus] ?? candidate.reviewStatus));
        if (candidate.isPublished) badges.append(element('span', 'admin-badge is-published', '공개 중'));
        const id = candidate.judgmentId;
        const name = element('strong', '', `#${id} · ${candidate.penaltyLabel}`);
        name.tabIndex = -1; // 처리 뒤 이 후보로 돌아올 자리
        name.dataset.anchor = `candidates:${id}`;
        head.append(
          name,
          badges,
          element('span', 'admin-sub', `${candidate.modelName ? `${candidate.modelName} · ` : ''}${formatDateTime(candidate.createdAt)}`),
        );

        const panel = element('div', 'admin-candidate-panel');
        panel.hidden = true;
        const error = errorLine();
        let approve = null;
        let gate = null;
        const setOpen = (open) => {
          panel.hidden = !open;
          toggle.textContent = open ? '접기' : '자세히 보기';
          toggle.setAttribute('aria-label', `#${id} 후보 ${open ? '접기' : '자세히 보기'}`);
          toggle.setAttribute('aria-expanded', String(open));
          if (open) { opened.add(id); viewed.add(id); } else opened.delete(id);
          if (approve) approve.disabled = !viewed.has(id);
          if (gate) gate.hidden = viewed.has(id);
        };
        const toggle = button('자세히 보기', async () => {
          if (!panel.hidden) { setOpen(false); return; }
          toggle.disabled = true;
          try {
            const detail = await api.getJudgment(id);
            if (!active()) return;
            const node = judgmentDetail(detail);
            details.set(id, node);
            panel.replaceChildren(node);
            setOpen(true);
          } catch (failure) {
            handleFailure(failure, '후보를 불러오지 못했어요.', error);
          } finally { toggle.disabled = false; }
        });
        toggle.setAttribute('aria-expanded', 'false');
        toggle.setAttribute('aria-label', `#${id} 후보 자세히 보기`);
        const actions = element('div', 'admin-actions');
        actions.append(toggle);
        const parts = [head, actions];

        const rejected = candidate.reviewStatus === 'REJECTED';
        if (!rejected && !candidate.isPublished && caseStatus !== 'PUBLISHED') {
          approve = button(`#${id} 승인 (공개 판결로 정하기)`, () => {
            if (!confirmChange(`#${id} 후보를 공개 판결로 정할까요?`, '같은 종류의 기존 공개 판결은 비공개로 바뀌어요.')) return;
            mutate(approve, error, () => api.approveJudgment(id), `#${id} 후보를 승인했어요.`, `candidates:${id}`);
          }, 'primary-button');
          gate = element('p', 'admin-sub admin-approve-gate', '자세히 보기로 판결 내용을 확인하면 승인할 수 있어요.');
          gate.id = `admin-approve-gate-${id}`;
          approve.setAttribute('aria-describedby', gate.id);
          approve.disabled = !viewed.has(id);
          gate.hidden = viewed.has(id);
          actions.append(approve);
          parts.push(gate);
        }
        if (!rejected && !candidate.isPublished) {
          const reject = button(`#${id} 반려`, () => {
            form.hidden = !form.hidden;
            if (!form.hidden) reason.focus();
          });
          const form = element('form', 'admin-reject');
          form.hidden = true;
          form.noValidate = true; // 빈 사유는 아래 문구로 알린다
          const reason = element('textarea');
          Object.assign(reason, { rows: 2, maxLength: 500, required: true });
          const reasonLabel = labeledField('반려 사유 (1 ~ 500자, 되돌릴 수 없어요)', reason);
          const send = element('button', 'secondary-button', '반려하기');
          send.type = 'submit';
          form.append(reasonLabel, send);
          form.addEventListener('submit', (event) => {
            event.preventDefault();
            if (!reason.value.trim()) { error.textContent = '반려 사유를 입력해 주세요.'; reason.focus(); return; }
            if (!confirmChange(`#${id} 후보를 반려할까요?`, '반려는 되돌릴 수 없어요.')) return;
            mutate(send, error, () => api.rejectJudgment(id, reason.value.trim()), `#${id} 후보를 반려했어요.`, `candidates:${id}`);
          });
          actions.append(reject);
          parts.push(form);
        }
        item.append(...parts, error, panel);
        if (opened.has(id) && details.has(id)) { panel.replaceChildren(details.get(id)); setOpen(true); } // 다시 그리기 전에 펼쳐 두었던 후보
        return item;
      }

      function candidatesSection(data) {
        const blocks = ['AI', 'COURT'].map((subject) => {
          const list = data.candidates[subject] ?? [];
          const block = element('div', 'admin-candidates');
          block.append(element('h3', '', `${SUBJECT[subject]} (${list.length})`));
          if (!list.length) block.append(element('p', 'admin-sub', '후보가 없어요. 파이프라인으로 적재해 주세요.'));
          const ul = element('ul');
          list.forEach((candidate) => ul.append(candidateRow(candidate, data.status)));
          block.append(ul);
          return block;
        });
        return section('판결 후보', 'candidates', ...blocks);
      }

      // ── 판단 요소 가치관 축 (A-12)
      function factorsSection(data) {
        if (!data.factors.length) return section('판단 요소', 'factors', element('p', 'admin-sub', '판단 요소가 없어요.'));
        const table = element('table', 'admin-table');
        const head = element('tr');
        ['#', '판단 요소', '공개 단계', '가치관 축', '자동 분류 투표', '확정'].forEach((text) => head.append(element('th', '', text)));
        const thead = element('thead');
        thead.append(head);
        const tbody = element('tbody');
        data.factors.forEach((factor) => {
          const row = element('tr');
          const labelCell = element('td');
          labelCell.append(element('span', '', factor.label));
          if (factor.summaryTag) labelCell.append(element('span', 'admin-sub', `요약어: ${factor.summaryTag}`));
          const tagView = summaryTagView(factor.summaryTagCandidates, factor.summaryTag);
          if (tagView) labelCell.append(tagView);
          const axisCell = element('td');
          axisCell.append(
            element('span', '', factor.valueAxis ? AXIS_LABEL[factor.valueAxis] ?? factor.valueAxis : '어느 축에도 맞지 않음'),
            element('span', `admin-badge ${factor.valueAxisStatus === 'CONFIRMED' ? 'is-approved' : 'is-pending'}`, factor.valueAxisStatus === 'CONFIRMED' ? '확정' : '자동'),
          );
          const votes = factor.valueAxisVotes;
          const voteText = votes
            ? `${Object.entries(votes.counts ?? {}).map(([key, count]) => `${key === 'NONE' ? '없음' : AXIS_LABEL[key] ?? key} ${count}`).join(' · ')} / ${votes.runs}회${votes.needsReview ? ' · 갈림' : ''}`
            : '—';
          const select = element('select');
          select.setAttribute('aria-label', `${factor.label} 가치관 축`);
          AXIS_OPTIONS.forEach((option) => {
            const node = element('option', '', option.label);
            node.value = option.value;
            node.selected = option.value === (factor.valueAxis ?? '');
            select.append(node);
          });
          const error = errorLine();
          select.dataset.anchor = `factors:${factor.factorId}`; // 확정 뒤 이 줄로 돌아온다
          const save = button('확정', () => mutate(save, error, () => api.setValueAxis(factor.factorId, select.value || null), `"${factor.label}" 가치관 축을 확정했어요.`, `factors:${factor.factorId}`));
          save.setAttribute('aria-label', `${factor.label} 가치관 축 확정`);
          const confirmCell = element('td');
          const confirmBox = element('div', 'admin-axis-confirm');
          confirmBox.append(select, save);
          confirmCell.append(confirmBox, error);
          row.append(
            element('td', '', String(factor.displayOrder)),
            labelCell,
            element('td', '', REVEAL_STAGE[factor.revealStage] ?? factor.revealStage),
            axisCell,
            (() => { const cell = element('td'); cell.append(element('span', 'admin-sub', voteText)); return cell; })(),
            confirmCell,
          );
          if (votes?.needsReview && factor.valueAxisStatus === 'AUTO') row.classList.add('needs-review'); // 투표가 갈렸고 아직 확정 전
          tbody.append(row);
        });
        table.append(thead, tbody);
        const wrap = element('div', 'admin-table-wrap');
        wrap.append(table);
        return section('판단 요소 · 가치관 축', 'factors', wrap);
      }

      // ── 사건 내용 (A-11)
      function contentSection(data) {
        const { content } = data;
        const keywords = content.keywords ?? []; // ERD상 NULL 가능 (키워드 없이 적재된 사건)
        const readOnly = definitionList([
          ['범죄 유형 · 죄명', `${CRIME_TYPE[content.crimeType] ?? content.crimeType} · ${content.chargeName}`],
          ['적용 법조문', content.appliedLaw],
          ['법정형', content.statutoryPenaltyText],
          ['사건 발생일', content.incidentDate],
          ['양형기준', content.guideline ? content.guideline.versionName : null],
          ['비식별화 항목', (content.deidentifiedItems ?? []).join(', ')],
        ]);
        const note = element('p', 'admin-sub', '죄명 · 적용 법조문 · 법정형 · 판단 요소 구성 · 형벌 규칙은 여기서 바꾸지 않아요(파이프라인 · SQL 경로).');
        if (data.status === 'PUBLISHED') {
          return section('사건 내용', 'content', readOnly, definitionList([
            ['제목', content.title], ['짧은 소개', content.shortIntro], ['개요', content.overview], ['키워드', keywords.join(', ')],
            ['난이도', DIFFICULTY[content.difficulty]], ['예상 시간(분)', content.estimatedMinutes], ['썸네일 주소', content.thumbnailUrl],
            ['권고 형량(개월)', `${content.recommendedMinMonths ?? '—'} ~ ${content.recommendedMaxMonths ?? '—'}`], ['권고 근거', content.recommendedBasis],
          ]), element('p', 'admin-sub', '공개한 사건이라 수정할 수 없어요.'));
        }

        const form = element('form', 'admin-form');
        form.noValidate = true;
        const fields = {};
        function field(name, label, control, hint) {
          fields[name] = control;
          form.append(labeledField(label, control, hint));
        }
        function input(type, value, extra = {}) {
          const node = element('input');
          Object.assign(node, { type, value: value ?? '', ...extra });
          return node;
        }
        function textarea(value, rows) {
          const node = element('textarea');
          Object.assign(node, { value: value ?? '', rows });
          return node;
        }
        field('title', '제목 (필수, 100자 이하)', input('text', content.title, { maxLength: 100 }));
        field('shortIntro', '짧은 소개 (필수, 200자 이하)', input('text', content.shortIntro, { maxLength: 200 }));
        field('overview', '개요 (필수)', textarea(content.overview, 5));
        field('keywords', '키워드', input('text', keywords.join(', ')), '쉼표로 나눠요. 비우면 키워드를 모두 지워요');
        const difficulty = element('select');
        [['', '정하지 않음'], ...Object.entries(DIFFICULTY)].forEach(([value, label]) => {
          const option = element('option', '', label);
          option.value = value;
          option.selected = value === (content.difficulty ?? '');
          difficulty.append(option);
        });
        field('difficulty', '난이도', difficulty);
        field('estimatedMinutes', '예상 시간(분)', input('number', content.estimatedMinutes, { min: 1 }));
        field('thumbnailUrl', '썸네일 주소 (300자 이하)', input('text', content.thumbnailUrl, { maxLength: 300 }));
        field('recommendedMinMonths', '권고 형량 하한(개월)', input('number', content.recommendedMinMonths, { min: 1 }));
        field('recommendedMaxMonths', '권고 형량 상한(개월)', input('number', content.recommendedMaxMonths, { min: 1 }));
        field('recommendedBasis', '권고 근거', textarea(content.recommendedBasis, 2));

        const error = errorLine();
        const save = element('button', 'primary-button', '바뀐 내용 저장');
        save.type = 'submit';
        save.dataset.anchor = 'content:save';
        form.append(error, save);

        // 바뀐 필드만 보낸다. 선택 필드를 비우면 null(지움), 키워드를 비우면 [] (A-11)
        function changes() {
          const out = {};
          const text = (name) => fields[name].value.trim();
          const optionalText = (name) => text(name) || null;
          const optionalNumber = (name) => (text(name) === '' ? null : Number(text(name)));
          const next = {
            title: text('title'),
            shortIntro: text('shortIntro'),
            overview: text('overview'),
            keywords: text('keywords') ? text('keywords').split(',').map((word) => word.trim()).filter(Boolean) : [],
            difficulty: fields.difficulty.value || null,
            estimatedMinutes: optionalNumber('estimatedMinutes'),
            thumbnailUrl: optionalText('thumbnailUrl'),
            recommendedMinMonths: optionalNumber('recommendedMinMonths'),
            recommendedMaxMonths: optionalNumber('recommendedMaxMonths'),
            recommendedBasis: optionalText('recommendedBasis'),
          };
          const before = { ...content, keywords }; // 키워드 NULL은 []와 같다고 보고 비교한다
          Object.entries(next).forEach(([key, value]) => {
            if (JSON.stringify(value) !== JSON.stringify(before[key] ?? null)) out[key] = value;
          });
          return out;
        }

        function validate(body) {
          if ('title' in body && !body.title) return '제목을 입력해 주세요.';
          if ('shortIntro' in body && !body.shortIntro) return '짧은 소개를 입력해 주세요.';
          if ('overview' in body && !body.overview) return '개요를 입력해 주세요.';
          for (const key of ['estimatedMinutes', 'recommendedMinMonths', 'recommendedMaxMonths']) {
            if (key in body && body[key] !== null && !(Number.isInteger(body[key]) && body[key] >= 1)) return '시간 · 형량은 1 이상의 정수로 입력해 주세요.';
          }
          const min = 'recommendedMinMonths' in body ? body.recommendedMinMonths : content.recommendedMinMonths;
          const max = 'recommendedMaxMonths' in body ? body.recommendedMaxMonths : content.recommendedMaxMonths;
          if (min != null && max != null && min > max) return '권고 형량 하한이 상한보다 클 수 없어요.';
          return '';
        }

        form.addEventListener('submit', (event) => {
          event.preventDefault();
          const body = changes();
          if (!Object.keys(body).length) { error.textContent = '바뀐 내용이 없어요.'; return; }
          const invalid = validate(body);
          if (invalid) { error.textContent = invalid; return; }
          mutate(save, error, () => api.updateCase(caseId, body), '사건 내용을 저장했어요.', 'content:save');
        });
        return section('사건 내용', 'content', readOnly, note, form);
      }

      // ── 원본 판결문 (A-7) — 열 때마다 감사 로그가 남는다
      function sourcesSection(data) {
        if (!data.sources.length) return section('원본 판결문', 'sources', element('p', 'admin-sub', '연결된 원본 판결문이 없어요.'));
        const list = element('ul', 'admin-sources');
        data.sources.forEach((source) => {
          const item = element('li');
          item.append(definitionList([
            ['심급', COURT_LEVEL[source.courtLevel] ?? source.courtLevel],
            ['법원 · 사건번호', [source.courtName, source.caseNumber].filter(Boolean).join(' ')],
            ['선고일', [source.decidedAt, source.isFinal ? '(확정)' : null].filter(Boolean).join(' ')],
            ['출처', source.sourceOrg],
            ['메모', source.note],
          ]));
          const viewer = element('pre', 'admin-original');
          viewer.hidden = true;
          const error = errorLine();
          if (source.hasOriginalText) {
            const open = button('원문 열기 (열람 기록이 남아요)', async () => {
              if (!viewer.hidden) { viewer.hidden = true; viewer.textContent = ''; open.textContent = '원문 열기 (열람 기록이 남아요)'; return; }
              open.disabled = true;
              try {
                const { originalText } = await api.getSourceText(caseId, source.sourceId);
                if (!active()) return;
                viewer.textContent = originalText;
                viewer.hidden = false;
                open.textContent = '원문 닫기';
              } catch (failure) {
                handleFailure(failure, '원문을 불러오지 못했어요.', error);
              } finally { open.disabled = false; }
            });
            item.append(open);
          } else {
            item.append(element('p', 'admin-sub', '원문이 저장돼 있지 않아요.'));
          }
          item.append(error, viewer);
          list.append(item);
        });
        return section('원본 판결문', 'sources', list);
      }

      function drawCase(data) {
        setTitle(`#${data.caseId} ${data.content.title}`);
        const top = element('div', 'admin-case-top');
        const back = button('← 목록', () => navigate('admin'), 'admin-back');
        const heading = element('h1', '', data.content.title);
        top.append(back, heading, element('span', `admin-badge is-${data.status.toLowerCase()}`, CASE_STATUS[data.status] ?? data.status));
        // 바로 가기: 3천 px가 넘는 화면이라 대조할 원본 판결문 등으로 바로 건너뛴다(주소 해시 대신 포커스 이동)
        const jump = element('nav', 'admin-jump');
        jump.setAttribute('aria-label', '이 사건 바로 가기');
        jump.append(element('span', 'admin-sub', '바로 가기'));
        SECTIONS.forEach(([key, label]) => jump.append(button(label, () => {
          const heading = main.querySelector(`[data-anchor="${key}"]`);
          heading?.scrollIntoView({ block: 'start' });
          heading?.focus({ preventScroll: true });
        }, 'admin-jump-link')));
        main.replaceChildren(top, jump, publishSection(data), candidatesSection(data), factorsSection(data), contentSection(data), sourcesSection(data));
      }

      async function load() {
        const data = await api.getCase(caseId);
        if (active()) drawCase(data);
      }

      const loading = element('p', 'admin-message', '사건을 불러오는 중이에요.');
      loading.setAttribute('role', 'status');
      main.replaceChildren(loading);
      try {
        await load();
      } catch (failure) {
        if (!active()) return;
        if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
        const panel = element('div', 'admin-message');
        panel.setAttribute('role', 'alert');
        const text = failure?.code === 'CASE_NOT_FOUND' ? '사건을 찾을 수 없어요.' : errorText(failure, '사건을 불러오지 못했어요.');
        panel.append(element('p', '', text), button('목록으로', () => navigate('admin')));
        main.replaceChildren(element('h1', '', `사건 #${caseId}`), panel);
      }
    },
  });
}
