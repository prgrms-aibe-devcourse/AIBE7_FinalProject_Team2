import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  SPOKEN_PATHS, addedSensitiveFlags, admits, basisText, blockApproveGate, blockingStaleUnits, isSpoken, issuesAt, leafLabel, opensToUsers,
  statusLabel, takeDownConfirm, textLeaves, textViolation,
} from './adminScriptBlocks.js';

// 파이프라인 · 백엔드와 같은 계약 파일 (정본은 파이썬). 화면 규칙이 어긋나면 이 파일과 비교하는 테스트가 깨진다
const CONTRACT = JSON.parse(readFileSync(new URL('../../../../tools/ai-judgment/contracts/script_blocks.json', import.meta.url), 'utf8'));

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

test('SPOKEN_PATHS · isSpoken: 말하는 대사 자리가 계약 파일과 같고, 설계도 · 카드 · 신문 답은 대사가 아니다 (BE-153)', () => {
  assert.deepEqual(SPOKEN_PATHS, CONTRACT.spokenPaths);
  assert.equal(isSpoken('U1', '/lines/12'), true);
  assert.equal(isSpoken('U2', '/questions/0/q'), true);
  assert.equal(isSpoken('U2', '/questions/0/a'), false, '신문 답');
  assert.equal(isSpoken('U2', '/cards/issues/ul/1'), false, '카드 글');
  assert.equal(isSpoken('U2', '/indict/x'), false, "'*'는 순번만");
  assert.equal(isSpoken('U4', '/defense'), true);
  assert.equal(isSpoken('U0', '/questions/0/q'), false, '설계도');
  assert.equal(isSpoken('U3', '/0/text'), true, 'U3 대사는 늘 대사');
});

test('textViolation: 대사가 아닌 자리는 줄 수 · 100자를 보지 않고 500자까지만 본다', () => {
  assert.equal(textViolation(`${'가'.repeat(150)}\n나\n다`, false), null);
  assert.match(textViolation('가'.repeat(501), false), /500자/);
  assert.match(textViolation('  ', false), /입력/);
});

test('admits: 계약 파일의 인정 여부 사례가 모두 같다 (서버 · 파이프라인과 같은 규칙)', () => {
  const mismatches = CONTRACT.admits.filter((example) => admits(example.answer) !== example.expected);
  assert.deepEqual(mismatches, []);
  assert.ok(CONTRACT.admits.length >= 5, '계약 파일을 제대로 읽었는지');
});

test('statusLabel: 나간 적 없는 READY는 "승인 완료 · 사건 공개 대기"다 (초안 사건에서 승인만 함)', () => {
  assert.equal(statusLabel({ scriptStatus: 'READY', releasedAt: null }), '승인 완료 · 사건 공개 대기');
  assert.equal(statusLabel({ scriptStatus: 'READY', releasedAt: '2026-11-02T10:00:00+09:00' }), '사용자에게 공개 중');
  assert.equal(statusLabel({ scriptStatus: 'MAINTENANCE', releasedAt: '2026-11-02T10:00:00+09:00' }), '점검 중');
});

test('addedSensitiveFlags: 블록을 만든 뒤 사건에 새로 생긴 플래그만 (빠진 플래그는 확인으로 충분, 근거가 없으면 모름)', () => {
  const block = { basis: { sensitiveFlags: ['SELF_HARM'] } };
  assert.deepEqual(addedSensitiveFlags(block, ['SELF_HARM', 'MENTAL_HEALTH']), ['MENTAL_HEALTH']);
  assert.deepEqual(addedSensitiveFlags(block, []), [], '빠진 플래그');
  assert.deepEqual(addedSensitiveFlags({ basis: {} }, ['MENTAL_HEALTH']), [], '근거에 플래그가 없으면 모름');
  assert.deepEqual(addedSensitiveFlags(block, null), [], '분류 전 사건');
});

test('blockApproveGate: fix: unit 오류는 문장 수정으로 풀리지 않아 다시 생성으로 안내한다', () => {
  const unitError = block('U1', { publishable: false, review: [{ severity: 'error', fix: 'unit', code: 'LINE_MISSING' }] });
  assert.match(blockApproveGate(clean, unitError), /다시 생성/);
  const lineError = block('U1', { publishable: false, review: [{ severity: 'error', fix: 'line', code: 'BANNED_WORD' }] });
  assert.match(blockApproveGate(clean, lineError), /오류 자리를 고치면/);
});
