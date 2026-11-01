import test from 'node:test';
import assert from 'node:assert/strict';
import { paperFromCase } from './newspaperFront.js';
import { commonPhotos } from '../utils/newspaper.js';

// FE-65 (PR #298 리뷰): 사건 상세를 못 받아도 개요 정보의 분류로 기사 속 작은 사진을 고른다
test('paperFromCase: 사건 상세(data)가 null이어도 info의 범죄 분류로 발단 · 결론 공용 컷을 고른다', () => {
  const paper = paperFromCase(null, { headline: '제목' }, {}, { caseId: 2, crimeCategoryLabel: '생명범죄' });
  assert.deepEqual(paper.extraPhotos.map((photo) => photo.src), [commonPhotos.life.origin, commonPhotos.life.conclusion]);
});

test('paperFromCase: 분류를 끝내 모르면 사건 번호 seed로 대표 사진과 같은 세트를 쓴다', () => {
  const paper = paperFromCase({ caseId: 4, title: '사건' }, undefined, {});
  assert.deepEqual(paper.extraPhotos.map((photo) => photo.src), [commonPhotos.body.origin, commonPhotos.body.conclusion]);
});

// FE-144: 지면(API 17)이 없는 사건은 부제 · 본문이 모두 shortIntro라 같은 문장이 되풀이됐다. 본문은 두고 부제 · 인용구만 뺀다
test('paperFromCase: 지면 · 기사가 없는 사건은 본문(shortIntro)을 두고 부제 · 인용구를 뺀다', () => {
  const paper = paperFromCase({ caseId: 1, title: '사건', shortIntro: '피고인은 사건 당일 피해자를 만났다.' }, undefined, {});
  assert.deepEqual(paper.article, ['피고인은 사건 당일 피해자를 만났다.']);
  assert.equal(paper.deck, null);
  assert.equal(paper.quote, false);
});

test('paperFromCase: 지면 부제가 본문 단락과 같으면(공백 차이 무시) 부제를 뺀다', () => {
  const paper = paperFromCase({ caseId: 1, title: '사건' }, { headline: '제목', deck: '첫 단락이다.', article: ['첫  단락이다. ', '둘째 단락이다.'] }, {});
  assert.equal(paper.deck, null);
  assert.equal(paper.quote, undefined); // 두 단락 이상이면 인용구는 그대로
});

test('paperFromCase: 부제 · 본문이 다른 지면은 그대로 둔다', () => {
  const casePaper = { headline: '제목', deck: '부제', article: ['첫 단락이다.', '둘째 단락이다.'] };
  const paper = paperFromCase({ caseId: 1, title: '사건', shortIntro: '소개' }, casePaper, {});
  assert.equal(paper.deck, '부제');
  assert.deepEqual(paper.article, casePaper.article);
  assert.equal(paper.quote, undefined);
});

test('paperFromCase: 사건 데이터 · 지면을 모두 못 받으면 대본 지면을 손대지 않는다', () => {
  const fallback = { headline: '대본', deck: '대본 부제', article: ['대본 부제'] };
  assert.equal(paperFromCase(null, undefined, fallback), fallback);
});
