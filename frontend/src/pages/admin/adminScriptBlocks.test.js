import assert from 'node:assert/strict';
import test from 'node:test';
import {
  basisText, blockApproveGate, blockingStaleUnits, issuesAt, leafLabel, opensToUsers, takeDownConfirm, textLeaves, textViolation,
} from './adminScriptBlocks.js';

const clean = { stopped: false, pending: [], review: [] };
const block = (unit, extra = {}) => ({ unit, publishable: true, reviewStatus: null, staleBlocksApproval: false, staleReasons: [], ...extra });

test('textLeaves: 블록 값의 문자열 자리를 순서대로 JSON Pointer와 함께 꺼낸다 (숫자 · 불리언은 빼고, ~ · /는 이스케이프)', () => {
  const content = { plea: '인정', questions: [{ q: '첫 질문?', a: '예' }], admits: true, n: 3, 'a/b': 'x', 'c~d': 'y' };
  assert.deepEqual(textLeaves(content), [
    { path: '/plea', text: '인정' },
    { path: '/questions/0/q', text: '첫 질문?' },
    { path: '/questions/0/a', text: '예' },
    { path: '/a~1b', text: 'x' },
    { path: '/c~0d', text: 'y' },
  ]);
  assert.deepEqual(textLeaves({ lines: ['하나', '둘'] }).map((leaf) => leaf.path), ['/lines/0', '/lines/1']);
  assert.deepEqual(textLeaves('통째 문자열'), [{ path: '', text: '통째 문자열' }]);
});

test('leafLabel: 검수 결과 path를 읽을 이름으로 바꾼다 (번호는 1부터, 모르는 이름은 그대로)', () => {
  assert.equal(leafLabel('/questions/0/q'), '질문 1 · 질문');
  assert.equal(leafLabel('/lines/4'), '해설 5');
  assert.equal(leafLabel('/defense'), '최종변론');
  assert.equal(leafLabel('/cards/issues/ul/1'), '카드 · 쟁점 · 항목 2');
  assert.equal(leafLabel('/unknown'), 'unknown');
  assert.equal(leafLabel(''), '블록 전체');
});

test('issuesAt: 같은 path의 검수 결과만 고른다 (블록 전체는 빈 path)', () => {
  const review = [{ path: '/lines/0', code: 'A' }, { path: '', code: 'B' }, { path: '/lines/1', code: 'C' }];
  assert.deepEqual(issuesAt(review, '/lines/0').map((issue) => issue.code), ['A']);
  assert.deepEqual(issuesAt(review, '').map((issue) => issue.code), ['B']);
  assert.deepEqual(issuesAt(null, '/x'), []);
});

test('textViolation: 줄바꿈을 빼고 100자 · 2줄까지 (A-13-13 · 21과 같은 규칙)', () => {
  assert.equal(textViolation(`${'가'.repeat(50)}\n${'나'.repeat(50)}`), null);
  assert.match(textViolation('가'.repeat(101)), /100자/);
  assert.match(textViolation('하나\n둘\n셋'), /2줄/);
  assert.match(textViolation('   '), /입력/);
});

test('blockApproveGate: 대본 조건(멈춤 · 만들지 못한 것 · 대본 전체 오류)을 블록 조건보다 먼저 알린다', () => {
  assert.equal(blockApproveGate(clean, block('U1')), null);
  assert.match(blockApproveGate({ ...clean, stopped: true }, block('U1')), /멈춘/);
  assert.match(blockApproveGate({ ...clean, pending: ['PAPER'] }, block('U1')), /PAPER/);
  assert.match(blockApproveGate({ ...clean, review: [{ severity: 'error', code: 'REVIEW_FAILED' }] }, block('U1')), /REVIEW_FAILED/);
  assert.match(blockApproveGate(clean, block('U1', { publishable: false })), /검수 오류/);
  assert.match(blockApproveGate(clean, block('U1', { staleBlocksApproval: true, staleReasons: ['paper'] })), /기사.*확인했음/);
  // 경고만 있는 대본 전체 검수 결과는 막지 않는다
  assert.equal(blockApproveGate({ ...clean, review: [{ severity: 'warning' }] }, block('U1')), null);
});

test('blockingStaleUnits · basisText: 승인을 막는 낡음 단위와 근거 문구', () => {
  const script = { blocks: [block('U0'), block('U1', { staleBlocksApproval: true }), block('U2', { staleReasons: ['U0'] })] };
  assert.deepEqual(blockingStaleUnits(script), ['U1']);
  assert.equal(basisText(['case', 'sensitiveFlags', 'mystery']), '사건 내용 · 민감 플래그 · mystery');
});

test('takeDownConfirm: 사용자에게 나간 대본이 지금 공개 중일 때만 묻는다 — 점검 중 · 나간 적 없는 대본은 묻지 않는다', () => {
  const [what, effect] = takeDownConfirm({ releasedAt: '2026-11-02T10:00:00+09:00', scriptStatus: 'READY' }, 'U2 블록을 반려할까요?');
  assert.equal(what, 'U2 블록을 반려할까요?');
  assert.match(effect, /점검 중/);
  assert.equal(takeDownConfirm({ releasedAt: '2026-11-02T10:00:00+09:00', scriptStatus: 'MAINTENANCE' }, 'x'), null);
  assert.equal(takeDownConfirm({ releasedAt: null, scriptStatus: 'READY' }, 'x'), null, '초안 사건에서 승인만 한 대본');
});

test('opensToUsers: 공개 사건에서 나머지 네 블록이 승인돼 있으면 이 승인이 대본을 연다', () => {
  const units = ['U0', 'U1', 'U2', 'U3', 'U4'];
  const allButU2 = { blocks: units.map((unit) => block(unit, { reviewStatus: unit === 'U2' ? null : 'APPROVED' })) };
  assert.equal(opensToUsers(allButU2, 'U2', 'PUBLISHED'), true);
  assert.equal(opensToUsers(allButU2, 'U2', 'DRAFT'), false, '사건이 공개 전이면 사용자에게 열리지 않는다');
  assert.equal(opensToUsers(allButU2, 'U3', 'PUBLISHED'), false, 'U2가 아직 남았다');
  const missing = { blocks: allButU2.blocks.filter((item) => item.unit !== 'U4') };
  assert.equal(opensToUsers(missing, 'U2', 'PUBLISHED'), false, '다섯 단위가 모두 있어야 한다');
});
