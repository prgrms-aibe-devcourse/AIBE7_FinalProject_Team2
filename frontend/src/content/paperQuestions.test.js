import test from 'node:test';
import assert from 'node:assert/strict';
import { PAPER_QUESTIONS, toPaperQuestions } from './paperQuestions.js';
import { QUESTIONS } from '../mocks/personalityMockData.js';

// 지면 문구는 문항 id로 서버 답과 이어지므로, 바꾼 문구의 엄격 쪽(A/B)이 원래 문항과 같아야 점수 뜻이 같다
// 아래 표는 paperQuestions.js 문구를 쓸 때 정한 엄격 쪽과 그 선택지 문구다(A = 행동 확인 · 결과 · 원칙 · 질서 쪽이 A인 문항)
// 문구를 함께 적어 두므로 A/B 문구를 맞바꾸거나 문구를 고치면 이 표도 같이 고쳐야 통과한다
const PAPER_STRICT = {
  1: { side: 'A', text: '수리비를 실제로 보내 주는 걸 보고 나서 마음을 풀겠다' },
  2: { side: 'B', text: '좋은 뜻이었어도 세 집이 물에 잠긴 건 큰 잘못이다' },
  3: { side: 'A', text: '다른 손님이었어도 알렸을 테니, 상가 가게들에도 알린다' },
  4: { side: 'A', text: '몇 시까지, 어느 정도 소리까지인지 규칙부터 정해 붙여 둔다' },
  5: { side: 'B', text: '다음부터 인도에서는 내려서 끄는지 보고 사과를 받아 준다' },
  6: { side: 'A', text: '구조대까지 출동했으니 그만큼 잘못한 일이다' },
  7: { side: 'A', text: '규칙은 누구에게나 같으니 보고한다' },
  8: { side: 'B', text: '바꿀 때까지는 골목 입구에서 내려 걸어오게 하는 규칙을 정한다' },
  9: { side: 'A', text: '후기를 지우고 바로잡는 글을 올리는지 보고 판단한다' },
  10: { side: 'A', text: '장난이었어도 일이 이렇게 커졌으니 크게 잘못한 일이다' },
  11: { side: 'B', text: '다른 집이었어도 경고했을 테니 똑같이 경고해야 한다' },
  12: { side: 'A', text: '처음일 때 마감 확인 순서를 확실히 정해 둬야 반복되지 않는다' },
  13: { side: 'B', text: '다음 주에도 입구를 비워 두는지 보고 판단한다' },
  14: { side: 'B', text: '좋은 뜻이어도 돈이 빠져나가게 한 건 분명한 잘못이다' },
  15: { side: 'B', text: '그래도 총무로서 한 잘못은 모두에게 알리고 짚어야 한다' },
  16: { side: 'B', text: '다시 생기지 않게 문 앞 사진 확인 같은 절차부터 만든다' },
};
const STRICT = new Set(['E', 'S', 'T', 'J']);

test('지면 문구: 16문항 모두 있고, 엄격 쪽이 원래 문항(시드와 같은 목)과 같다', () => {
  assert.equal(Object.keys(PAPER_QUESTIONS).length, QUESTIONS.length);
  QUESTIONS.forEach(({ id, a }) => {
    assert.ok(PAPER_QUESTIONS[id], `문항 ${id}`);
    const { side, text } = PAPER_STRICT[id];
    assert.equal(side, STRICT.has(a.letter) ? 'A' : 'B', `문항 ${id}`);
    assert.equal(PAPER_QUESTIONS[id][side === 'A' ? 'optionA' : 'optionB'], text, `문항 ${id}`);
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
