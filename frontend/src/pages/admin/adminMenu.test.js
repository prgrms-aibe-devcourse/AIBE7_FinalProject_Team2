import test from 'node:test';
import assert from 'node:assert/strict';
import { ADMIN_MENU, isMenuReady, isSectionReady } from './adminMenu.js';

// FE-143: 실제 모드에서 서버 API가 없는 화면은 draw(= API 호출)를 하지 않는다. 어떤 화면이 막히는지 고정한다
const NOT_READY = ['dashboard', 'members', 'posts', 'reports', 'accounts', 'notices'];

test('실제 모드: 운영 메뉴 6개는 준비 중, 사건 검수 · 사건 등록은 열린다', () => {
  NOT_READY.forEach((key) => assert.equal(isSectionReady(key, 'real'), false, key));
  assert.equal(isSectionReady('cases', 'real'), true);
  assert.equal(isSectionReady('register', 'real'), true);
});

test('목 모드: 모든 화면이 열린다', () => {
  ADMIN_MENU.flatMap((group) => group.items).forEach((item) => {
    assert.equal(isMenuReady(item, 'mock'), true, item.key);
    assert.equal(isSectionReady(item.key, 'mock'), true, item.key);
  });
});

test('메뉴에 없는 화면(section 없음 등)은 준비된 것으로 본다', () => {
  assert.equal(isSectionReady(undefined, 'real'), true);
  assert.equal(isSectionReady('unknown', 'real'), true);
});
