import assert from 'node:assert/strict';
import test, { beforeEach } from 'node:test';
import * as api from './adminMockApi.js';

// 목은 로그인 여부를 sessionStorage에 둔다. node에는 없어서 메모리 저장소로 대신한다
const store = new Map();
globalThis.sessionStorage = {
  getItem: (key) => store.get(key) ?? null,
  setItem: (key, value) => store.set(key, String(value)),
  removeItem: (key) => store.delete(key),
};

const source = (overrides = {}) => ({ courtLevel: 'FIRST', caseNumber: '2099고합900', courtName: '가상지방법원', decidedAt: '2025-03-01', isFinal: true, sourceOrg: '법제처', note: null, originalText: '가상 원문', ...overrides });
const request = (overrides = {}) => ({ crimeType: 'MURDER', sources: [source()], ...overrides });
const code = (promise) => promise.then(() => null, (failure) => failure.code);

async function runToEnd(importId) {
  for (let i = 0; i < 20; i += 1) {
    const view = await api.getImport(importId);
    if (view.status === 'SUCCEEDED' || view.status === 'FAILED') return view;
  }
  throw new Error('등록이 끝나지 않았다');
}

beforeEach(async () => {
  store.clear();
  api.resetAdminMock();
  await api.login('admin@example.com', 'pw');
});

test('로그인하지 않으면 등록할 수 없다', async () => {
  store.clear();
  assert.equal(await code(api.createImport(request())), 'UNAUTHORIZED');
  assert.equal(await code(api.getImport(1)), 'UNAUTHORIZED');
});

test('등록 요청 검증: 범죄 유형 · 판결문 수 · 심급 중복 · 사건번호 · 원문', async () => {
  assert.equal(await code(api.createImport(request({ crimeType: 'ARSON' }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source(), source({ caseNumber: 'a' })] }))), 'VALIDATION_ERROR'); // 같은 심급
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '' })] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source({ originalText: '  ' })] }))), 'VALIDATION_ERROR');
  assert.equal(await code(api.createImport(request({ sources: [source({ decidedAt: '2025/03/01' })] }))), 'VALIDATION_ERROR');
});

test('이미 등록한 사건번호는 거절한다', async () => {
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '2099고합000' })] }))), 'DUPLICATE_SOURCE');
});

test('등록하면 단계가 차례로 끝나지만 생성만으로는 사건이 생기지 않는다', async () => {
  const { importId, status } = await api.createImport(request());
  assert.equal(status, 'QUEUED');
  const first = await api.getImport(importId);
  assert.equal(first.status, 'RUNNING');
  assert.equal(first.stages[0].status, 'RUNNING');
  assert.equal(first.stages.at(-1).status, 'SKIPPED'); // load는 서버가 A-14-1로 한다

  const done = await runToEnd(importId);
  assert.equal(done.status, 'SUCCEEDED');
  // 늘 건너뛰는 세 단계(contamination · rag · load)를 빼면 모두 끝나 있다
  const skipped = done.stages.filter((stage) => stage.status === 'SKIPPED').map((stage) => stage.name);
  assert.deepEqual(skipped, ['contamination', 'rag', 'load']);
  assert.ok(done.stages.filter((stage) => !skipped.includes(stage.name)).every((stage) => stage.status === 'DONE'));
  assert.equal(done.caseId, null); // 적재(A-14-1) 전에는 사건이 없다
});

test('적재하면 DRAFT 사건과 판결 후보가 생기고 caseId가 채워진다', async () => {
  const { importId } = await api.createImport(request());
  await runToEnd(importId);
  const loaded = await api.loadImport(importId);
  assert.equal(loaded.status, 'SUCCEEDED');
  assert.ok(loaded.caseId);
  const created = await api.getCase(loaded.caseId);
  assert.equal(created.status, 'DRAFT');
  assert.equal(created.sources[0].caseNumber, '2099고합900');
  assert.equal(created.candidates.AI.length, 1);
  // 조회(A-15)도 같은 caseId를 그대로 돌려준다
  assert.equal((await api.getImport(importId)).caseId, loaded.caseId);
});

test('생성이 끝나지 않았거나 이미 적재한 등록은 IMPORT_NOT_READY', async () => {
  const { importId } = await api.createImport(request());
  assert.equal(await code(api.loadImport(importId)), 'IMPORT_NOT_READY'); // 아직 생성 중
  await runToEnd(importId);
  await api.loadImport(importId);
  assert.equal(await code(api.loadImport(importId)), 'IMPORT_NOT_READY'); // 두 번 적재하지 않는다
});

test('적재 거절: 제목이 겹치면 DUPLICATE_TITLE, 생성 결과가 잘못되면 IMPORT_RESULT_INVALID', async () => {
  const dup = await api.createImport(request({ sources: [source({ originalText: '[제목중복] 원문' })] }));
  await runToEnd(dup.importId);
  assert.equal(await code(api.loadImport(dup.importId)), 'DUPLICATE_TITLE');

  const bad = await api.createImport(request({ sources: [source({ caseNumber: '2099고합902', originalText: '[적재실패] 원문' })] }));
  await runToEnd(bad.importId);
  const failure = await api.loadImport(bad.importId).catch((error) => error);
  assert.equal(failure.code, 'IMPORT_RESULT_INVALID');
  assert.ok(failure.details?.[0]?.field); // 어느 값이 문제인지 화면에 그대로 쓴다
});

test('적재 뒤 다시 적재하면 IMPORT_NOT_READY지만 조회에는 caseId가 남는다 (화면이 완료로 잇는 근거, PR #285 리뷰)', async () => {
  const { importId } = await api.createImport(request());
  await runToEnd(importId);
  const { caseId } = await api.loadImport(importId);
  assert.equal(await code(api.loadImport(importId)), 'IMPORT_NOT_READY');
  assert.equal((await api.getImport(importId)).caseId, caseId);
});

test('생성 결과가 사라지면 IMPORT_NOT_READY를 사유(message)와 함께 돌려준다', async () => {
  const { importId } = await api.createImport(request({ sources: [source({ originalText: '[결과소실] 원문' })] }));
  await runToEnd(importId);
  const failure = await api.loadImport(importId).catch((error) => error);
  assert.equal(failure.code, 'IMPORT_NOT_READY');
  assert.match(failure.message, /다시 등록/);
  assert.equal((await api.getImport(importId)).caseId, null); // 적재도 안 됐고 생성은 끝난 상태
});

test('없는 등록 작업은 적재도 IMPORT_NOT_FOUND', async () => {
  assert.equal(await code(api.loadImport(999)), 'IMPORT_NOT_FOUND');
});

test('로그인하지 않으면 적재할 수 없다', async () => {
  const { importId } = await api.createImport(request());
  await runToEnd(importId);
  store.clear();
  assert.equal(await code(api.loadImport(importId)), 'UNAUTHORIZED');
});

test('실행 중에는 다른 등록을 시작할 수 없다', async () => {
  await api.createImport(request());
  assert.equal(await code(api.createImport(request({ sources: [source({ caseNumber: '2099고합901' })] }))), 'IMPORT_IN_PROGRESS');
});

test('원문에 [실패]가 있으면 비식별화 단계에서 실패한다', async () => {
  const { importId } = await api.createImport(request({ sources: [source({ originalText: '[실패] 원문' })] }));
  const view = await runToEnd(importId);
  assert.equal(view.status, 'FAILED');
  assert.equal(view.stages[0].status, 'FAILED');
  assert.equal(view.caseId, null);
  assert.ok(view.message);
});

test('없는 등록 작업은 IMPORT_NOT_FOUND', async () => {
  assert.equal(await code(api.getImport(999)), 'IMPORT_NOT_FOUND');
});

// ── 사진 · 신문 지면 · 대본 검수 (A-13-1 ~ A-13-13, FE-106)
// 12번은 검수 거리가 있는 DRAFT 사건, 13번은 이미 공개한 사건이다(목 기본 상태)
const imageFile = (overrides = {}) => ({ type: 'image/png', size: 1024, name: 'photo.png', ...overrides });

test('A-13-3: 대표사진을 올리면 사건 썸네일이 바뀌고, 공개한 사건은 409', async () => {
  const before = await api.getCase(12);
  assert.equal(before.content.thumbnailUrl, null);

  const after = await api.uploadRepresentativePhoto(12, imageFile());
  assert.ok(after.content.thumbnailUrl, '썸네일 주소가 채워진다');
  assert.equal(after.status, 'REVIEW', '처음 손대면 DRAFT → REVIEW');

  assert.equal(await code(api.uploadRepresentativePhoto(13, imageFile())), 'INVALID_STATE');
});

test('A-13-3 · A-13-4: 형식 · 크기가 맞지 않으면 INVALID_IMAGE_FILE', async () => {
  assert.equal(await code(api.uploadRepresentativePhoto(12, imageFile({ type: 'application/pdf' }))), 'INVALID_IMAGE_FILE');
  assert.equal(await code(api.uploadRepresentativePhoto(12, imageFile({ size: 11 * 1024 * 1024 }))), 'INVALID_IMAGE_FILE');
  assert.equal(await code(api.uploadRepresentativePhoto(12, null)), 'INVALID_IMAGE_FILE');
});

test('A-13-4: 같은 역할로 다시 올리면 새 사진이 아니라 교체다 (역할별 한 장)', async () => {
  const first = await api.uploadIncidentPhoto(12, { file: imageFile(), role: 'ORIGIN', caption: '처음' });
  const second = await api.uploadIncidentPhoto(12, { file: imageFile(), role: 'ORIGIN', caption: '다시' });
  assert.equal(second.id, first.id, 'id가 유지된다(교체)');
  assert.equal(second.caption, '다시');

  const list = await api.getIncidentPhotos(12);
  assert.equal(list.length, 1, '행이 늘지 않는다');
  assert.equal(list[0].role, 'ORIGIN');
});

test('A-13-4: 역할이 없거나 모르는 값이면 400, 설명은 200자까지', async () => {
  assert.equal(await code(api.uploadIncidentPhoto(12, { file: imageFile(), role: 'MIDDLE' })), 'VALIDATION_ERROR');
  assert.equal(await code(api.uploadIncidentPhoto(12, { file: imageFile() })), 'VALIDATION_ERROR');
  assert.equal(await code(api.uploadIncidentPhoto(12, { file: imageFile(), role: 'ORIGIN', caption: '가'.repeat(201) })), 'VALIDATION_ERROR');
});

test('A-13-5 · A-13-9: 목록은 발단 → 결론 순, 없는 자리를 지우면 404', async () => {
  await api.uploadIncidentPhoto(12, { file: imageFile(), role: 'CONCLUSION' });
  await api.uploadIncidentPhoto(12, { file: imageFile(), role: 'ORIGIN' });
  const list = await api.getIncidentPhotos(12);
  assert.deepEqual(list.map((photo) => photo.role), ['ORIGIN', 'CONCLUSION']);

  await api.deleteIncidentPhoto(12, 'ORIGIN');
  assert.deepEqual((await api.getIncidentPhotos(12)).map((photo) => photo.role), ['CONCLUSION']);
  assert.equal(await code(api.deleteIncidentPhoto(12, 'ORIGIN')), 'INCIDENT_PHOTO_NOT_FOUND');
  assert.equal(await code(api.deleteIncidentPhoto(13, 'ORIGIN')), 'INVALID_STATE', '공개한 사건은 못 지운다');
});

test('A-13-6 ~ 8: 지면이 없으면 PAPER_NOT_FOUND, 만들면 조회 · 수정이 된다', async () => {
  assert.equal(await code(api.getCasePaper(12)), 'PAPER_NOT_FOUND');

  const made = await api.generateCasePaper(12);
  assert.ok(made.headline);
  assert.ok(Array.isArray(made.article) && made.article.length);

  const fixed = await api.updateCasePaper(12, { headline: '고친 헤드라인', deck: null });
  assert.equal(fixed.headline, '고친 헤드라인');
  assert.equal(fixed.deck, null, 'null은 지우기다');

  assert.equal(await code(api.updateCasePaper(12, { headline: '' })), 'VALIDATION_ERROR');
  assert.equal(await code(api.updateCasePaper(12, { article: [] })), 'VALIDATION_ERROR');
  assert.equal(await code(api.generateCasePaper(13)), 'INVALID_STATE', '공개한 사건은 못 바꾼다');
});

test('A-13-6: 지면에는 대표사진 · 발단 · 결론 사진이 함께 붙는다 (따로 저장하지 않는다)', async () => {
  await api.uploadRepresentativePhoto(12, imageFile());
  await api.uploadIncidentPhoto(12, { file: imageFile(), role: 'ORIGIN', caption: '발단' });
  const paper = await api.generateCasePaper(12);
  assert.ok(paper.photo, '대표사진은 사건 썸네일을 그대로 쓴다');
  assert.equal(paper.extraPhotos.length, 1);
  assert.equal(paper.extraPhotos[0].role, 'ORIGIN');
});

test('A-13-10: 대본이 없는 사건은 SCRIPT_NOT_FOUND, A-6 요약도 exists false', async () => {
  const { caseId } = await api.getCase(12);
  assert.equal(caseId, 12);
  await api.deleteIncidentPhoto(12, 'ORIGIN').catch(() => null);

  // 목 기본 상태에서 12번에는 대본이 있다. 대본이 없는 사건을 만들어 확인한다
  const { importId } = await api.createImport(request({ sources: [source({ caseNumber: '2099고합950' })] }));
  await runToEnd(importId);
  const loaded = await api.loadImport(importId);
  const fresh = await api.getCase(loaded.caseId);
  assert.deepEqual(fresh.script, { exists: false, reviewStatus: null, publishable: null });
  assert.equal(await code(api.getCaseScript(loaded.caseId)), 'SCRIPT_NOT_FOUND');
});

test('A-13-10: 대사마다 label · 측 · 경고가 붙고, 만들지 못한 줄은 text가 null이다', async () => {
  const script = await api.getCaseScript(12);
  assert.equal(script.evidence.length, 3);
  assert.ok(script.evidence[0].label, '판단 요소 문구가 붙어 있다');
  assert.equal(script.evidence[0].role, 'PROSECUTOR');
  assert.equal(script.evidence[1].text, null, '끝내 만들지 못한 줄');
  assert.deepEqual(script.evidence[1].validation.warnings, ['증거 카드에 없는 사실을 말했습니다']);
  assert.equal(script.publishable, false);
  assert.equal(script.reviewStatus, 'PENDING');
  assert.deepEqual((await api.getCase(12)).script, { exists: true, reviewStatus: 'PENDING', publishable: false });
});

test('A-13-11: publishable이 false면 승인은 409. 빈 줄을 채우면 승인할 수 있다', async () => {
  assert.equal(await code(api.approveCaseScript(12)), 'INVALID_STATE');

  const fixed = await api.updateCaseScriptLine(12, 2, '피고인은 흉기를 미리 준비하지 않았습니다.');
  assert.equal(fixed.publishable, true, '빈 줄이 없어지면 다시 올라간다');
  assert.equal(fixed.evidence[1].validation.manual, true, '사람이 고침으로 표시된다');
  assert.ok(fixed.evidence[1].editedAt);
  assert.deepEqual(fixed.evidence[1].validation.warnings, ['증거 카드에 없는 사실을 말했습니다'], '경고는 남긴다');

  const approved = await api.approveCaseScript(12);
  assert.equal(approved.reviewStatus, 'APPROVED');
  assert.equal(approved.isPublished, true);
  assert.equal((await api.getCase(12)).status, 'DRAFT', '대본 검수는 사건 상태를 바꾸지 않는다');
});

test('A-13-13: 고치면 검수 기록이 지워져 다시 대기가 된다', async () => {
  const after = await api.updateCaseScriptLine(13, 1, '소음 문제로 다툼이 반복됐습니다. 고쳤어요.');
  assert.equal(after.reviewStatus, 'PENDING');
  assert.equal(after.isPublished, false);
  assert.equal(after.reviewedBy, null);
});

test('A-13-13: 길이는 줄바꿈을 빼고 센다 (50자 + 50자 두 줄은 통과)', async () => {
  const twoLines = `${'가'.repeat(50)}\n${'나'.repeat(50)}`;
  const ok = await api.updateCaseScriptLine(12, 1, twoLines);
  assert.equal(ok.evidence[0].text, twoLines);

  assert.equal(await code(api.updateCaseScriptLine(12, 1, `${'가'.repeat(51)}\n${'나'.repeat(50)}`)), 'VALIDATION_ERROR');
  assert.equal(await code(api.updateCaseScriptLine(12, 1, '한 줄\n두 줄\n세 줄')), 'VALIDATION_ERROR');
  assert.equal(await code(api.updateCaseScriptLine(12, 1, '   ')), 'VALIDATION_ERROR');
  assert.equal(await code(api.updateCaseScriptLine(12, 99, '없는 요소')), 'SCRIPT_LINE_NOT_FOUND');
});

test('A-13-12: 승인한 대본도 반려로 내릴 수 있다. 사유는 1 ~ 500자', async () => {
  assert.equal((await api.getCaseScript(13)).reviewStatus, 'APPROVED');
  assert.equal(await code(api.rejectCaseScript(13, '  ')), 'VALIDATION_ERROR');
  assert.equal(await code(api.rejectCaseScript(13, '가'.repeat(501))), 'VALIDATION_ERROR');

  const rejected = await api.rejectCaseScript(13, '검사 쪽 대사가 너무 깁니다.');
  assert.equal(rejected.reviewStatus, 'REJECTED');
  assert.equal(rejected.isPublished, false);
  assert.equal(rejected.reviewNote, '검사 쪽 대사가 너무 깁니다.');
});
