// npm test : 성향 테스트 목 API(명세 9장 P-1 · P-2 · P-4) 계약과 성향 이미지 01 ~ 32 연결 (FE-42)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { getPersonalityQuestions, getPersonalityType, submitPersonality } from './personalityMockApi.js';
import { imageNumberOf, TYPES } from './personalityMockData.js';

const publicDir = fileURLToPath(new URL('../../public', import.meta.url));
const ogTypes = JSON.parse(readFileSync(fileURLToPath(new URL('../../og/personality-types.json', import.meta.url)), 'utf8')).types;
const imagePath = (number) => `/images/characters/personality/${String(number).padStart(2, '0')}.webp`;

test('P-1 문항에는 축 · 글자를 싣지 않는다', async () => {
  const { questions } = await getPersonalityQuestions();
  assert.equal(questions.length, 16);
  questions.forEach((question) => {
    assert.deepEqual(Object.keys(question).sort(), ['content', 'id', 'optionA', 'optionB', 'scenario']);
  });
});

test('P-2 빠진 문항이 있으면 VALIDATION_ERROR', async () => {
  const { questions } = await getPersonalityQuestions();
  const answers = questions.slice(1).map((question) => ({ questionId: question.id, value: 2 }));
  await assert.rejects(submitPersonality(answers), { code: 'VALIDATION_ERROR' });
  await assert.rejects(submitPersonality(questions.map((question) => ({ questionId: question.id, value: 5 }))), { code: 'VALIDATION_ERROR' });
});

test('P-2 결과에는 4글자 코드가 없고, 이미지는 public에 있는 번호 파일이다', async () => {
  const { questions } = await getPersonalityQuestions();
  for (const value of [0, 2, 4]) {
    const result = await submitPersonality(questions.map((question) => ({ questionId: question.id, value })));
    assert.equal(JSON.stringify(result).match(/"[EI][SN][TF][JP]"/), null, '응답 어디에도 4글자 코드가 없다');
    assert.match(result.type.imageUrl, /^\/images\/characters\/personality\/\d{2}\.webp$/);
    assert.ok(existsSync(publicDir + result.type.imageUrl), `${result.type.imageUrl} 파일이 있다`);
    Object.values(result.axisScores).forEach((score) => assert.ok(score >= 0 && score <= 1));
  }
});

test('성향 이미지 01 ~ 32가 모두 있고, 유형마다 보이는 이미지가 OG · 시드 번호와 같다', () => {
  for (let number = 1; number <= 32; number += 1) assert.ok(existsSync(publicDir + imagePath(number)), `${imagePath(number)} 없음`);
  const byName = Object.fromEntries(ogTypes.map((type) => [type.name, type.imageNumber]));
  Object.keys(TYPES).forEach((code) => {
    assert.equal(imageNumberOf(code), byName[TYPES[code].name], `${TYPES[code].name} 이미지 번호`);
    // 내로 01 ~ 16 · 남불 17 ~ 32
    assert.equal(imageNumberOf(code) > 16, TYPES[code].character === 'NAMBUL');
  });
});

test('P-4 모르는 shareId · 상속 속성 이름은 PERSONALITY_TYPE_NOT_FOUND', async () => {
  await assert.rejects(getPersonalityType('nope0000'), { code: 'PERSONALITY_TYPE_NOT_FOUND' });
  await assert.rejects(getPersonalityType('constructor'), { code: 'PERSONALITY_TYPE_NOT_FOUND' });
});
