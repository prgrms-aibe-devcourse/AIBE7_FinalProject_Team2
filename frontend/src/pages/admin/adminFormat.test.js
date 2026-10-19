import assert from 'node:assert/strict';
import test from 'node:test';
import { describeGeneration, describeSummaryTagCandidates } from './adminFormat.js';

test('요약어 후보: 계산을 켜지 않은 요소(null)는 null', () => {
  assert.equal(describeSummaryTagCandidates(null), null);
  assert.equal(describeSummaryTagCandidates(undefined), null);
});

test('요약어 후보: 목록에 있음 · 애매함 · 제안 · 후보 없음을 구분한다', () => {
  const base = { candidates: [{ tag: '채무 다툼', score: 0.9211 }], inVocabulary: false, ambiguous: false, suggested: '채무 다툼' };
  assert.equal(describeSummaryTagCandidates({ ...base, inVocabulary: true, suggested: null }).state, 'IN_VOCABULARY');
  assert.equal(describeSummaryTagCandidates({ ...base, ambiguous: true, suggested: null }).state, 'AMBIGUOUS');
  const suggested = describeSummaryTagCandidates(base);
  assert.equal(suggested.state, 'SUGGESTED');
  assert.equal(suggested.suggested, '채무 다툼');
  assert.deepEqual(suggested.candidates, [{ tag: '채무 다툼', score: '0.9211' }]);
  assert.equal(describeSummaryTagCandidates({ candidates: [], inVocabulary: false, ambiguous: false, suggested: null }).state, 'NONE');
});

test('요약어 후보: 모양이 어긋난 후보는 걸러낸다', () => {
  const view = describeSummaryTagCandidates({ candidates: [null, { score: 1 }, { tag: '자수', score: 1 }], suggested: null });
  assert.deepEqual(view.candidates, [{ tag: '자수', score: '1.0000' }]);
});

test('생성 정보: AI 후보가 아니면(generation이 null) null', () => {
  assert.equal(describeGeneration(null), null);
});

test('생성 정보: 파이프라인 보고서를 판정 · 선택 근거 · 경고 · 참고 자료로 푼다', () => {
  const view = describeGeneration({
    modelName: 'm', promptVersion: 'v1',
    inputSnapshot: { promptVersion: 'v1', system: 'S', user: 'U', extra: 1 },
    generationReport: {
      modelSpec: 'gemini:x', servedModel: 'x-001', runIndex: 2,
      validationWarnings: ['경고 하나'],
      selection: { strategy: 'consensus', reason: '이유', score: 0.8125 },
      contamination: { verdict: 'SUSPECT', reason: '가까운 응답', counts: { EXACT: 0, CLOSE: 2 }, criteria: { minAnswered: 3 } },
      rag: { status: 'OK', precedents: [{ id: 801, score: 0.9123 }], excludedSuspects: 1, fallbackReasons: [], warnings: [] },
    },
  });
  assert.deepEqual(view.warnings, ['경고 하나']);
  assert.equal(view.contamination.level, 'warn');
  assert.deepEqual(view.contamination.rows, [['판정 근거', '가까운 응답'], ['분류별 개수', 'EXACT 0 · CLOSE 2'], ['판정 기준', 'minAnswered 3']]);
  assert.deepEqual(view.selection, [['선택 방식', '합의값에 가까운 회차'], ['선택 이유', '이유'], ['점수', '0.8125']]);
  assert.equal(view.rag.status, 'OK');
  assert.deepEqual(view.rag.rows.find(([label]) => label === '참고 판례'), ['참고 판례', '#801 (0.9123)']);
  assert.deepEqual(view.snapshot, { promptVersion: 'v1', system: 'S', user: 'U', other: { extra: 1 } });
  assert.equal(view.model.find(([label]) => label === '생성 회차')[1], '2회차');
});

test('생성 정보: 사전 학습 점검을 건너뛴 것과 의심 높음을 구분한다', () => {
  const level = (contamination) => describeGeneration({ generationReport: { contamination } }).contamination;
  assert.equal(level({ verdict: 'SKIPPED' }).level, 'none');
  assert.equal(level({ verdict: 'SKIPPED' }).label, '점검하지 않음');
  assert.equal(level({ verdict: 'CLEAN' }).level, 'ok');
  assert.equal(level({ verdict: 'CONTAMINATED' }).level, 'danger');
  assert.equal(level(undefined).label, '점검 기록 없음');
});

test('생성 정보: 보고서가 비어 있어도 던지지 않는다(사람이 검수해 넣은 행 등)', () => {
  const view = describeGeneration({ modelName: null, promptVersion: null, inputSnapshot: null, generationReport: null });
  assert.equal(view.hasReport, false);
  assert.deepEqual(view.warnings, []);
  assert.deepEqual(view.rag, { status: 'SKIPPED', rows: [] });
  assert.equal(view.snapshot.system, null);
});
