import test from 'node:test';
import assert from 'node:assert/strict';
import { PAPER_QUESTIONS, toPaperQuestions } from './paperQuestions.js';
import { QUESTIONS } from '../mocks/personalityMockData.js';

// 지면 문구는 문항 id로 서버 답과 이어지므로, 바꾼 문구의 엄격 쪽(A/B)이 원래 문항과 같아야 점수 뜻이 같다
// 아래 표는 paperQuestions.js 문구를 쓸 때 정한 엄격 쪽이다(A = 행동 확인 · 결과 · 원칙 · 질서 쪽이 A인 문항)
const PAPER_STRICT_SIDE = { 1: 'A', 2: 'B', 3: 'A', 4: 'A', 5: 'B', 6: 'A', 7: 'A', 8: 'B', 9: 'A', 10: 'A', 11: 'B', 12: 'A', 13: 'B', 14: 'B', 15: 'B', 16: 'B' };
const STRICT = new Set(['E', 'S', 'T', 'J']);

test('지면 문구: 16문항 모두 있고, 엄격 쪽이 원래 문항(시드와 같은 목)과 같다', () => {
  assert.equal(Object.keys(PAPER_QUESTIONS).length, QUESTIONS.length);
  QUESTIONS.forEach(({ id, a }) => {
    assert.ok(PAPER_QUESTIONS[id], `문항 ${id}`);
    assert.equal(PAPER_STRICT_SIDE[id], STRICT.has(a.letter) ? 'A' : 'B', `문항 ${id}`);
  });
});

test('지면 문구: 법률 용어를 쓰지 않는다(FR-8-2)', () => {
  const words = ['양형', '합의', '처벌', '반성', '전과'];
  Object.entries(PAPER_QUESTIONS).forEach(([id, { content, optionA, optionB, line }]) => {
    words.forEach((word) => assert.ok(![content, optionA, optionB, line].join(' ').includes(word), `문항 ${id}: ${word}`));
  });
});

test('toPaperQuestions: 문구만 바꾸고 id · 나머지 값은 그대로, 모르는 id는 받은 그대로', () => {
  const [first] = toPaperQuestions([{ id: 1, scenario: 'FRIEND', content: '원래', optionA: 'a', optionB: 'b' }]);
  assert.deepEqual(first, { id: 1, scenario: 'FRIEND', content: PAPER_QUESTIONS[1].content, optionA: PAPER_QUESTIONS[1].optionA, optionB: PAPER_QUESTIONS[1].optionB });
  const unknown = { id: 99, content: '그대로', optionA: 'a', optionB: 'b' };
  assert.deepEqual(toPaperQuestions([unknown]), [unknown]);
});
