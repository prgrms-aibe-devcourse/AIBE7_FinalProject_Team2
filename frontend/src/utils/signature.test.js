// npm test : 서명 생성(FE-94) — 같은 글 = 같은 서명, 한 획, 시간 0.8 ~ 1.2초, 좌표 정상, 앞에서부터 쓰는 선
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { anonSeed, fullPath, makeSignature, pathAt, signatureOf } from './signature.js';

const NAMES = ['해송', '해성', '해송이', '모래시계', '법봉', '새벽의 배심원', '오후의 서기', 'judge77', 'A', '가', '😀😀', '  ', ''];

test('같은 닉네임은 늘 같은 서명이고, 다른 닉네임은 다른 서명이다', () => {
  assert.equal(fullPath(makeSignature('해송')), fullPath(makeSignature('해송')));
  assert.notEqual(fullPath(makeSignature('해송')), fullPath(makeSignature('해성')));
  assert.notEqual(fullPath(makeSignature('모래시계')), fullPath(makeSignature('법봉')));
});

test('서명은 한 획이다: 이동(M)이 한 번뿐이고 모든 좌표가 숫자다', () => {
  for (const name of NAMES) {
    const { points } = makeSignature(name);
    const d = fullPath(makeSignature(name));
    assert.equal((d.match(/M/g) ?? []).length, 1, `${JSON.stringify(name)}: 선이 끊기면 안 된다`);
    assert.ok(points.length > 10);
    assert.ok(points.every(([x, y]) => Number.isFinite(x) && Number.isFinite(y)), `${JSON.stringify(name)}: 좌표가 숫자가 아니다`);
  }
});

test('쓰는 시간은 0.8 ~ 1.2초이고, 긴 서명이 더 오래 걸린다', () => {
  for (const name of NAMES) {
    const { durationMs } = makeSignature(name);
    assert.ok(durationMs >= 800 && durationMs <= 1200, `${JSON.stringify(name)}: ${durationMs}ms`);
  }
  assert.ok(makeSignature('새벽의 배심원들').durationMs > makeSignature('가').durationMs);
});

test('닉네임이 길면 서명도 길다', () => {
  assert.ok(makeSignature('새벽의 배심원').len > makeSignature('법봉').len);
});

test('pathAt: 0은 점, 1은 전체이고, 진행할수록 앞에서부터 길어진다', () => {
  const signature = makeSignature('새벽의 배심원');
  assert.equal(pathAt(signature, 1).replace(/L[\d. ]+$/, ''), fullPath(signature).replace(/L[\d. ]+$/, ''));
  assert.ok(pathAt(signature, 0).length < 40);
  let before = 0;
  for (const p of [0.1, 0.3, 0.5, 0.7, 0.9, 1]) {
    const d = pathAt(signature, p);
    assert.ok(d.length > before, `진행 ${p}에서 선이 늘어나야 한다`);
    assert.equal((d.match(/M/g) ?? []).length, 1);
    before = d.length;
  }
  // 진행한 선은 전체 선의 앞부분과 같다(같은 선을 앞에서부터 쓴다)
  const half = pathAt(signature, 0.5).split('L');
  const all = fullPath(signature).split('L');
  half.slice(0, -1).forEach((piece, index) => assert.equal(piece, all[index]));
});

test('signatureOf: 공백 · 빈 값은 기본 서명으로, 같은 값은 한 번만 만든다', () => {
  assert.equal(signatureOf('  '), signatureOf(''));
  assert.equal(signatureOf('해송'), signatureOf(' 해송 '));
});

test('anonSeed: 저장소가 있으면 같은 값을 다시 읽고, 없어도 값을 돌려준다', () => {
  const data = new Map();
  const storage = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
  const first = anonSeed(storage);
  assert.equal(anonSeed(storage), first);
  assert.ok(anonSeed(undefined).length > 0);
});
