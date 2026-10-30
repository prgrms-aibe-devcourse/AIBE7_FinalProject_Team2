import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
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

// PR #360 리뷰: 화면이 넘기는 section이 메뉴 key와 어긋나면(오타 등) 그 화면은 조용히 준비된 것으로 열려 404 요청이 다시 나간다.
// 화면 파일마다 기대하는 section을 고정하고, renderAdminShell을 부르는 파일은 모두 이 표에 있어야 한다(새 화면을 더하면 여기도 더한다)
const EXPECTED_SECTION = {
  'adminDashboardPage.js': 'dashboard',
  'adminListPage.js': 'cases',
  'adminCasePage.js': 'cases',
  'adminRegisterPage.js': 'register',
  'adminMembersPage.js': 'members',
  'adminPostsPage.js': 'posts',
  'adminReportsPage.js': 'reports',
  'adminAccountsPage.js': 'accounts',
  'adminNoticesPage.js': 'notices',
};

test('관리자 화면 파일마다 renderAdminShell에 넘기는 section이 그 화면의 메뉴 key다', () => {
  const keys = new Set(ADMIN_MENU.flatMap((group) => group.items).map((item) => item.key));
  const dir = new URL('./', import.meta.url);
  const shellPages = readdirSync(dir)
    .filter((name) => name.endsWith('.js') && !name.endsWith('.test.js') && name !== 'adminShell.js')
    .filter((name) => readFileSync(new URL(name, dir), 'utf8').includes('renderAdminShell('));
  assert.deepEqual(shellPages.sort(), Object.keys(EXPECTED_SECTION).sort(), 'renderAdminShell을 부르는 화면이 표와 다르다');
  shellPages.forEach((name) => {
    const source = readFileSync(new URL(name, dir), 'utf8');
    const calls = source.split('renderAdminShell(').length - 1;
    // 따옴표 종류와 상관없이 section 값을 읽는다. 호출마다 section이 하나씩 있어야 한다
    const sections = [...source.matchAll(/\bsection:\s*(['"`])([^'"`]*)\1/g)].map((match) => match[2]);
    assert.equal(sections.length, calls, `${name}: renderAdminShell 호출 ${calls}번에 section ${sections.length}개`);
    sections.forEach((section) => {
      assert.ok(keys.has(section), `${name}: section '${section}'이 메뉴 key가 아니다`);
      assert.equal(section, EXPECTED_SECTION[name], `${name}: section이 '${EXPECTED_SECTION[name]}'이 아니라 '${section}'이다`);
    });
  });
});
