import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FACES, faceKeyOf, faceOf, tipMessage } from './faces.js';
import { visibleFaces } from '../../utils/newspaperFaces.js';

const summary = { total: 11, byCrimeType: { MURDER: 2, FRAUD: 3, INJURY: 0, ROBBERY: 4, THEFT: 2 } };
const CRIMES = ['MURDER', 'FRAUD', 'INJURY', 'ROBBERY', 'THEFT'];

test('면 순서: 1면 → 범죄 유형 다섯 면 → 색인 → 성향 테스트 → 오피니언', () => {
  assert.deepEqual(FACES.map(({ key }) => key), [undefined, ...CRIMES, 'INDEX', 'TEST', 'OPINION']);
  assert.deepEqual(FACES.map(({ label }) => label), ['1면', '살인', '사기', '상해', '강도', '절도', '색인', '성향 테스트', '오피니언']);
});

test('범죄 유형 면만 API에 유형을 보내고, 나머지 면은 전체 목록을 받는다', () => {
  FACES.forEach((face) => assert.equal(face.apiType, CRIMES.includes(face.key) ? face.key : undefined, face.label));
});

test('화살표 · 귀 이름: 범죄 유형 면은 "살인면"처럼, 나머지는 탭 이름 그대로', () => {
  assert.deepEqual(FACES.map(({ name }) => name), ['1면', '살인면', '사기면', '상해면', '강도면', '절도면', '색인', '성향 테스트', '오피니언']);
});

test('탭 건수: 1면은 9건까지, 범죄 유형 면은 그 유형 건수, 색인 · 성향 테스트 · 오피니언은 달지 않는다', () => {
  assert.deepEqual(FACES.map((face) => face.count(summary)), [9, 2, 3, 0, 4, 2, null, null, null]);
  assert.equal(faceOf(undefined).count({ total: 4, byCrimeType: {} }), 4);
  assert.equal(faceOf('MURDER').count({ total: 0, byCrimeType: {} }), 0); // 유형 키가 빠져도 0건
});

test('0건이면 숨기는 것은 범죄 유형 면뿐이다', () => {
  assert.deepEqual(visibleFaces(FACES, summary, undefined).map(({ key }) => key), [undefined, 'MURDER', 'FRAUD', 'ROBBERY', 'THEFT', 'INDEX', 'TEST', 'OPINION']);
  const empty = { total: 0, byCrimeType: Object.fromEntries(CRIMES.map((key) => [key, 0])) };
  assert.deepEqual(visibleFaces(FACES, empty, undefined).map(({ key }) => key), [undefined, 'INDEX', 'TEST', 'OPINION']);
});

test('포스트잇 문구는 체험 가능 건수에 맞춘다', () => {
  assert.equal(tipMessage(3, 3), '오늘 지면에 사건이 3건 실렸어요. 마음이 가는 기사를 눌러 보세요!');
  assert.equal(tipMessage(3, 1), '오늘 지면에 사건이 3건 실렸어요. 지금 체험할 수 있는 기사는 1건이에요.');
  assert.equal(tipMessage(3, 0), '오늘 지면에 사건이 3건 실렸어요. 체험은 곧 열려요. 기사는 미리 읽어 볼 수 있어요.');
});

test('1면만 front이고, 지금은 모든 면이 세로 여백을 채운다(FE-119 결정)', () => {
  assert.deepEqual(FACES.filter(({ front }) => front).map(({ key }) => key), [undefined]);
  assert.ok(FACES.every(({ fill }) => fill === true));
});

test('본문 모양: 1면 · 범죄 유형 면은 기사, 색인 · 성향 테스트 · 오피니언은 각자', () => {
  assert.deepEqual(FACES.map(({ view }) => view), ['cases', 'cases', 'cases', 'cases', 'cases', 'cases', 'index', 'test', 'opinion']);
});

test('면 열쇠 확인: 아는 열쇠만 받고, 모르는 값 · 빈 값은 1면(undefined)', () => {
  assert.equal(faceKeyOf('INDEX'), 'INDEX');
  assert.equal(faceKeyOf('MURDER'), 'MURDER');
  assert.equal(faceKeyOf('BOGUS'), undefined);
  assert.equal(faceKeyOf(null), undefined);
  assert.equal(faceKeyOf(''), undefined);
  assert.equal(faceOf(undefined).label, '1면');
  assert.equal(faceOf('BOGUS'), undefined);
});
