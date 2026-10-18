import { button, element } from '../../components/siteLayout.js';
import { parseJudgmentText } from './adminJudgmentParse.js';
import { COURT_LEVEL, CRIME_TYPE, IMPORT_STAGE, errorText } from './adminLabels.js';
import { PdfReadError } from './adminPdf.js';
import { renderAdminShell } from './adminShell.js';
import sampleUrl from './samples/sample-judgment.pdf?url';

// S-19 사건 등록 (A-14 · A-15). 원본 판결문을 넣으면 서버의 파이프라인이 비식별화 · 구조화 → AI 판결 생성 → 적재를 하고,
// 끝나면 DRAFT 사건이 검수 목록에 생긴다. 실행은 오래 걸리므로 A-15를 2초마다 물어 단계별 진행을 보여 준다.
// 화면을 벗어나면(active()가 false) 폴링은 다음 확인에서 멈춘다. 원문은 화면 메모리에만 두고 브라우저 저장소에는 넣지 않는다. 등록이 시작되면 입력칸에서도 지운다.
// 판결문은 파일(PDF · txt)을 올리거나, 원문 칸에 붙여넣거나 직접 입력한다. 어느 쪽이든 원문에서 항목(사건번호 등)을 읽어 아래 칸을 채운다
const AUTO_FILL_DELAY_MS = 400; // 타자 칠 때마다 읽지 않고 멈춘 뒤에 읽는다
const MAX_TEXT_FILE_BYTES = 5 * 1024 * 1024;
const MAX_SOURCES = 3;
const MAX_ORIGINAL_LENGTH = 200000;
const POLL_MS = 2000;
const POLL_ERROR_LIMIT = 3; // 연속으로 이만큼 실패하면 멈추고 다시 확인하게 한다

export function renderAdminRegisterPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    title: '사건 등록',
    draw: async ({ api, main, active, unauthorized }) => {
      const top = element('div', 'admin-case-top');
      top.append(button('← 목록', () => navigate('admin'), 'admin-back'), element('h1', '', '사건 등록'));
      const formArea = element('div', 'admin-register-form');
      const progressArea = element('div', 'admin-register-progress');
      progressArea.hidden = true;
      main.replaceChildren(top, formArea, progressArea);

      // ── 입력 폼
      const sourceBoxes = [];
      const sourcesWrap = element('div', 'admin-sources-input');
      const addSource = button('+ 판결문 추가 (심급이 다른 판결문)', () => addSourceBox(), 'secondary-button');
      const formError = element('p', 'admin-error');
      formError.setAttribute('role', 'alert');

      function labeled(text, control, hint) {
        const label = element('label', 'admin-field');
        label.append(element('span', '', text), control);
        if (hint) label.append(element('small', '', hint));
        return label;
      }
      const textInput = (extra) => Object.assign(element('input'), { type: 'text', ...extra });
      function select(options, value) {
        const node = element('select');
        options.forEach(([optionValue, text]) => {
          const option = element('option', '', text);
          option.value = optionValue;
          option.selected = optionValue === value;
          node.append(option);
        });
        return node;
      }

      const crimeType = select(Object.entries(CRIME_TYPE), 'MURDER');

      function addSourceBox() {
        const box = element('fieldset', 'admin-source-box');
        const used = new Set(sourceBoxes.map((item) => item.courtLevel.value));
        const level = ['FIRST', 'APPEAL', 'SUPREME'].find((key) => !used.has(key)) ?? 'FIRST';
        const fields = {
          courtLevel: select(Object.entries(COURT_LEVEL), level),
          caseNumber: textInput({ maxLength: 50, placeholder: '예: 2099고합000' }),
          courtName: textInput({ maxLength: 50 }),
          decidedAt: Object.assign(element('input'), { type: 'date' }),
          isFinal: Object.assign(element('input'), { type: 'checkbox', checked: true }),
          sourceOrg: textInput({ maxLength: 50, placeholder: '예: 법제처' }),
          note: textInput({ maxLength: 200 }),
          originalText: Object.assign(element('textarea'), { rows: 10, maxLength: MAX_ORIGINAL_LENGTH }),
        };
        const count = element('small', '', `0 / ${MAX_ORIGINAL_LENGTH.toLocaleString()}자`);
        const updateCount = () => { count.textContent = `${fields.originalText.value.length.toLocaleString()} / ${MAX_ORIGINAL_LENGTH.toLocaleString()}자`; };
        const finalLabel = element('label', 'admin-check');
        finalLabel.append(fields.isFinal, element('span', '', '확정된 판결'));
        // 자동으로 채운 칸은 초록 표시. 사람이 직접 고친 칸(touched)은 다음 자동 채우기가 덮어쓰지 않는다
        const autoControls = [fields.caseNumber, fields.courtName, fields.decidedAt, fields.courtLevel];
        [...autoControls, crimeType].forEach((control) => control.addEventListener('input', () => {
          control.classList.remove('is-auto');
          control.dataset.touched = '1';
        }));
        const status = element('p', 'admin-sub admin-import-status');
        status.setAttribute('role', 'status');

        // 원문에서 읽은 항목을 폼에 채운다. 읽지 못한 항목은 건드리지 않는다.
        // announce: 파일을 올린 직후처럼 결과를 꼭 알려야 하면 true, 타자 중에는 채운 게 있을 때만 알린다
        function applyParsed({ prefix = '', announce = false, resetTouched = false } = {}) {
          if (resetTouched) [...autoControls, crimeType].forEach((control) => { delete control.dataset.touched; });
          const parsed = parseJudgmentText(fields.originalText.value);
          const filled = [];
          const fill = (control, label, value) => {
            if (value === undefined || control.dataset.touched === '1') return;
            if (control.value !== value) filled.push(label);
            control.value = value;
            control.classList.add('is-auto');
          };
          fill(fields.caseNumber, '사건번호', parsed.caseNumber);
          fill(fields.courtName, '법원명', parsed.courtName);
          fill(fields.decidedAt, '선고일', parsed.decidedAt);
          fill(fields.courtLevel, '심급', parsed.courtLevel);
          if (sourceBoxes.indexOf(fields) <= 0) fill(crimeType, '범죄 유형', parsed.crimeType); // 범죄 유형은 첫 판결문 기준
          if (filled.length) status.textContent = `${prefix}자동으로 채운 항목: ${filled.join(', ')}. 맞는지 확인해 주세요.`;
          else if (announce) status.textContent = `${prefix}사건번호 · 법원명 · 선고일을 원문에서 찾지 못했어요. 아래 칸에 직접 입력해 주세요.`;
        }

        let typingTimer;
        fields.originalText.addEventListener('input', () => {
          updateCount();
          clearTimeout(typingTimer);
          typingTimer = setTimeout(() => { if (active()) applyParsed(); }, AUTO_FILL_DELAY_MS); // 붙여넣기도 input 이벤트가 온다
        });

        function replaceText(text, prefix) {
          if (fields.originalText.value.trim() && !window.confirm('입력해 둔 원문을 지우고 새로 읽은 내용으로 바꿀까요?')) { status.textContent = '원문을 바꾸지 않았어요.'; return; }
          fields.originalText.value = text;
          updateCount();
          applyParsed({ prefix, announce: true, resetTouched: true });
        }

        async function readTextFile(file) {
          if (file.size > MAX_TEXT_FILE_BYTES) throw new PdfReadError('TOO_BIG', '파일이 너무 커요. 5MB 이하만 읽을 수 있어요.');
          const bytes = await file.arrayBuffer();
          try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); } catch { return new TextDecoder('euc-kr').decode(bytes); } // 옛 판결문 txt는 EUC-KR이 많다
        }

        // 파일 하나로 PDF와 txt를 모두 받는다. PDF는 브라우저에서 글자만 뽑고 파일 자체는 서버로 보내지 않는다
        const fileInput = Object.assign(element('input'), { type: 'file', accept: '.pdf,.txt,application/pdf,text/plain' });
        fileInput.setAttribute('aria-label', '판결문 파일 (PDF 또는 txt)');
        fileInput.addEventListener('change', async () => {
          const file = fileInput.files?.[0];
          if (!file) return;
          const name = file.name.toLowerCase();
          const isPdf = name.endsWith('.pdf') || file.type === 'application/pdf';
          const isTxt = name.endsWith('.txt') || file.type === 'text/plain';
          fileInput.disabled = true;
          try {
            if (!isPdf && !isTxt) throw new PdfReadError('NOT_SUPPORTED', 'PDF 또는 txt 파일만 올릴 수 있어요.');
            status.textContent = isPdf ? 'PDF를 읽는 중이에요…' : '파일을 읽는 중이에요…';
            if (isPdf) {
              const { readPdfFile } = await import('./adminPdfLoader.js'); // 처음 고를 때만 pdf.js를 불러온다
              const { text, pages } = await readPdfFile(file);
              if (active()) replaceText(text, `${pages}쪽을 읽었어요. `);
            } else {
              const text = await readTextFile(file);
              if (active()) replaceText(text, '파일을 읽었어요. ');
            }
          } catch (failure) {
            console.error('판결문 파일 읽기 실패', failure);
            if (active()) status.textContent = failure instanceof PdfReadError ? failure.message : `파일을 읽지 못했어요. (${failure?.name ?? '오류'}) 원문 칸에 붙여넣거나 직접 입력해 주세요.`;
          } finally {
            fileInput.disabled = false;
            fileInput.value = '';
          }
        });
        const sample = Object.assign(element('a', 'admin-sample-link', '예시 PDF 내려받기 (가상 판결문)'), { href: sampleUrl, download: '가상_판결문_예시.pdf' });
        const fileLabel = element('div', 'admin-field');
        fileLabel.append(
          element('span', '', '판결문 파일 (PDF 또는 txt, 선택)'),
          fileInput,
          element('small', '', '파일을 올리면 원문과 사건번호 · 법원명 · 선고일을 자동으로 읽어요. 글자가 들어 있는 PDF만 읽을 수 있어요(스캔한 이미지 PDF는 불가). PDF 파일 자체는 서버로 보내지 않고, 읽은 글만 원문 칸에 들어가요.'),
          sample,
        );
        const textLabel = labeled('판결문 원문 (필수) — 직접 입력하거나 붙여넣어도 아래 항목이 자동으로 채워져요', fields.originalText);
        textLabel.append(count);

        const legend = element('legend', '', `판결문 ${sourceBoxes.length + 1}`);
        const remove = button('이 판결문 빼기', () => {
          sourceBoxes.splice(sourceBoxes.indexOf(fields), 1);
          box.remove();
          refreshSources();
        }, 'secondary-button');
        box.append(
          legend,
          fileLabel, status, textLabel,
          labeled('심급 (판결문마다 달라야 해요)', fields.courtLevel),
          labeled('사건번호 (필수, 50자 이하)', fields.caseNumber),
          labeled('법원명', fields.courtName),
          labeled('선고일', fields.decidedAt),
          finalLabel,
          labeled('출처', fields.sourceOrg),
          labeled('메모', fields.note),
          remove,
        );
        fields.box = box;
        fields.legend = legend;
        fields.remove = remove;
        sourceBoxes.push(fields);
        sourcesWrap.append(box);
        refreshSources();
      }

      function refreshSources() {
        sourceBoxes.forEach((item, index) => {
          item.legend.textContent = `판결문 ${index + 1}`;
          item.remove.hidden = sourceBoxes.length === 1;
        });
        addSource.hidden = sourceBoxes.length >= MAX_SOURCES;
      }

      function collect() {
        const sources = sourceBoxes.map((item) => ({
          courtLevel: item.courtLevel.value,
          caseNumber: item.caseNumber.value.trim(),
          courtName: item.courtName.value.trim() || null,
          decidedAt: item.decidedAt.value || null,
          isFinal: item.isFinal.checked,
          sourceOrg: item.sourceOrg.value.trim() || null,
          note: item.note.value.trim() || null,
          originalText: item.originalText.value,
        }));
        return { crimeType: crimeType.value, sources };
      }

      function validate({ sources }) {
        if (new Set(sources.map((source) => source.courtLevel)).size !== sources.length) return '심급이 같은 판결문이 둘 이상이에요. 심급마다 한 건씩만 넣어 주세요.';
        for (const [index, source] of sources.entries()) {
          const name = `판결문 ${index + 1}`;
          if (!source.caseNumber) return `${name}의 사건번호를 입력해 주세요.`;
          if (!source.originalText.trim()) return `${name}의 원문을 입력해 주세요.`;
          if (source.originalText.length > MAX_ORIGINAL_LENGTH) return `${name}의 원문이 너무 길어요. ${MAX_ORIGINAL_LENGTH.toLocaleString()}자 이하로 줄여 주세요.`;
        }
        return '';
      }

      const form = element('form', 'admin-form admin-register');
      form.noValidate = true;
      const submit = element('button', 'primary-button', '등록하고 파이프라인 실행');
      submit.type = 'submit';
      form.append(
        element('p', 'admin-sub', '원본 판결문을 넣으면 서버가 비식별화 · 구조화하고 AI 판결 후보를 만들어 "적재됨" 상태로 검수 목록에 올려요. 몇 분 걸릴 수 있어요. 원문은 이 화면을 벗어나면 남지 않아요.'),
        labeled('범죄 유형', crimeType),
        sourcesWrap, addSource, formError, submit,
      );
      formArea.append(form);
      addSourceBox();

      form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (submit.disabled) return;
        const body = collect();
        const invalid = validate(body);
        if (invalid) { formError.textContent = invalid; return; }
        submit.disabled = true;
        formError.textContent = '';
        try {
          const { importId } = await api.createImport(body);
          if (!active()) return;
          sourceBoxes.forEach((item) => { item.originalText.value = ''; }); // 시작된 뒤에는 원문을 화면에도 남기지 않는다
          watch(importId);
        } catch (failure) {
          if (!active()) return;
          if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
          formError.textContent = errorText(failure, '등록을 시작하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
          submit.disabled = false;
        }
      });

      // ── 진행 상황 (A-15)
      function watch(importId) {
        formArea.hidden = true;
        progressArea.hidden = false;
        let failures = 0;

        async function poll() {
          try {
            const data = await api.getImport(importId);
            if (!active()) return;
            failures = 0;
            draw(data);
            if (data.status === 'QUEUED' || data.status === 'RUNNING') setTimeout(poll, POLL_MS);
          } catch (failure) {
            if (!active()) return;
            if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
            failures += 1;
            if (failures < POLL_ERROR_LIMIT && failure?.code === 'NETWORK_ERROR') { setTimeout(poll, POLL_MS); return; }
            // 서버 작업은 계속 돌고 있을 수 있다. 중복 등록을 막으려고 폼으로 돌려보내지 않고 다시 확인만 하게 한다
            const panel = element('div', 'admin-message');
            panel.setAttribute('role', 'alert');
            panel.append(
              element('p', '', errorText(failure, '진행 상황을 확인하지 못했어요. 서버에서는 등록이 계속 진행 중일 수 있어요.')),
              button('다시 확인', () => { failures = 0; poll(); }),
              button('목록으로', () => navigate('admin'), 'secondary-button'),
            );
            progressArea.replaceChildren(panel);
          }
        }

        function draw(data) {
          const list = element('ol', 'admin-stages');
          data.stages.forEach((stage) => {
            const item = element('li', `admin-stage is-${stage.status.toLowerCase()}`);
            item.append(element('span', 'admin-stage-mark', { DONE: '✓', RUNNING: '…', FAILED: '✕', SKIPPED: '–' }[stage.status] ?? '·'), IMPORT_STAGE[stage.name] ?? stage.name);
            list.append(item);
          });
          const parts = [element('h2', '', data.status === 'SUCCEEDED' ? '등록을 마쳤어요' : data.status === 'FAILED' ? '등록에 실패했어요' : '파이프라인을 실행하는 중이에요'), list];
          if (data.status === 'RUNNING' || data.status === 'QUEUED') {
            const note = element('p', 'admin-sub', '이 화면을 닫아도 서버 실행은 계속돼요. 끝나면 검수 목록의 "검수할 사건" 탭에 올라와요.');
            note.setAttribute('role', 'status');
            parts.push(note);
          }
          if (data.status === 'SUCCEEDED') {
            parts.push(element('p', 'admin-ok', `사건 #${data.caseId}을 "적재됨" 상태로 등록했어요. 내용을 검수해 주세요.`), button('검수 화면으로', () => navigate('adminCase', { caseId: data.caseId }), 'primary-button'));
          }
          if (data.status === 'FAILED') {
            const error = element('p', 'admin-error', data.message || '파이프라인 실행 중 오류가 났어요.');
            error.setAttribute('role', 'alert');
            parts.push(error, button('입력으로 돌아가기', () => { progressArea.hidden = true; formArea.hidden = false; submit.disabled = false; }));
          }
          progressArea.replaceChildren(...parts);
        }

        progressArea.replaceChildren(element('p', 'admin-message', '등록을 시작했어요.'));
        poll();
      }
    },
  });
}
