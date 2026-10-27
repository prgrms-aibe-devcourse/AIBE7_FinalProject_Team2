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
