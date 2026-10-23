import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lockScroll, unlockScroll } from './scrollLock.js';

function fakeDocument(clientWidth) {
  const classes = () => {
    const set = new Set();
    return {
      add: (name) => set.add(name),
      remove: (name) => set.delete(name),
      toggle: (name, force) => { if (force) set.add(name); else set.delete(name); },
      contains: (name) => set.has(name),
    };
  };
  return { documentElement: { clientWidth, classList: classes() }, body: { classList: classes() } };
}

test('스크롤바가 있던 화면이면 잠글 때 자리를 비워 둔다', () => {
  const doc = fakeDocument(985);
  lockScroll('popup-open', 'popup-gutter', { innerWidth: 1000 }, doc);
  assert.ok(doc.body.classList.contains('popup-open'));
  assert.ok(doc.documentElement.classList.contains('popup-gutter'));
});

test('스크롤바가 없던 화면은 자리를 건드리지 않는다', () => {
  const doc = fakeDocument(1000);
  lockScroll('popup-open', 'popup-gutter', { innerWidth: 1000 }, doc);
  assert.ok(doc.body.classList.contains('popup-open'));
  assert.ok(!doc.documentElement.classList.contains('popup-gutter'));
});

test('풀면 두 클래스가 모두 사라진다', () => {
  const doc = fakeDocument(985);
  lockScroll('popup-open', 'popup-gutter', { innerWidth: 1000 }, doc);
  unlockScroll('popup-open', 'popup-gutter', doc);
  assert.ok(!doc.body.classList.contains('popup-open'));
  assert.ok(!doc.documentElement.classList.contains('popup-gutter'));
});
