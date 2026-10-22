// npm test : 프로필 선택 설정 사본(FE-81). 바로 반영하고 서버에는 차례로 보내며, 거절되면 그 칸만 돌린다
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { configureProfileSettings, getProfileSettings, hydrateProfileSettings, resetProfileSettings, updateProfileSettings } from './profileSettings.js';

beforeEach(() => resetProfileSettings());

const server = (state = { shownCard: 'current', profileCardType: 'current', bio: '' }) => {
  const calls = [];
  configureProfileSettings(async (patch) => {
    calls.push(patch);
    Object.assign(state, patch);
    return { ...state };
  });
  return calls;
};

test('기본값은 최근 · 최근 · 소개 없음', () => {
  assert.deepEqual(getProfileSettings(), { shownCard: 'current', profileCardType: 'current', bio: '' });
});

test('바꾸면 바로 보이고, 서버에는 보낸 칸만 간다', async () => {
  const calls = server();
  const saving = updateProfileSettings({ shownCard: 'retake' });
  assert.equal(getProfileSettings().shownCard, 'retake'); // 응답 전에도
  assert.equal(await saving, true);
  assert.deepEqual(calls, [{ shownCard: 'retake' }]);
});

test('거절되면 그 칸만 이전 값으로 돌아간다', async () => {
  configureProfileSettings(async () => { throw { code: 'NETWORK_ERROR' }; });
  hydrateProfileSettings({ shownCard: 'initial', profileCardType: 'retake', bio: '안녕' });
  assert.equal(await updateProfileSettings({ bio: '바뀐 소개' }), false);
  assert.deepEqual(getProfileSettings(), { shownCard: 'initial', profileCardType: 'retake', bio: '안녕' });
});

test('저장 중에는 오래된 마이페이지 응답이 덮어쓰지 않는다', async () => {
  server();
  const saving = updateProfileSettings({ shownCard: 'initial' });
  hydrateProfileSettings({ shownCard: 'current', profileCardType: 'current', bio: '' });
  assert.equal(getProfileSettings().shownCard, 'initial');
  await saving;
  hydrateProfileSettings({ shownCard: 'retake', profileCardType: 'current', bio: '' }); // 저장이 끝난 뒤에는 서버 값을 따른다
  assert.equal(getProfileSettings().shownCard, 'retake');
});

test('서버가 값을 주지 않으면(옛 서버) 그대로 두고, 모르는 값은 기본으로 바로잡는다', () => {
  hydrateProfileSettings({ shownCard: 'retake', profileCardType: 'initial', bio: 'a' });
  hydrateProfileSettings(undefined);
  assert.equal(getProfileSettings().shownCard, 'retake');
  hydrateProfileSettings({ shownCard: 'x', profileCardType: null, bio: 3 });
  assert.deepEqual(getProfileSettings(), { shownCard: 'current', profileCardType: 'current', bio: '' });
});

test('연속으로 바꾸면 차례로 한 번씩 보내고, 첫 요청이 거절돼도 둘째 칸은 지킨다', async () => {
  let n = 0;
  const calls = [];
  configureProfileSettings(async (patch) => {
    calls.push(patch);
    n += 1;
    if (n === 1) throw { code: 'INTERNAL_ERROR' };
    return { shownCard: 'current', profileCardType: 'retake', bio: '' };
  });
  const first = updateProfileSettings({ shownCard: 'initial' });
  const second = updateProfileSettings({ profileCardType: 'retake' });
  assert.deepEqual([await first, await second], [false, true]);
  assert.deepEqual(calls, [{ shownCard: 'initial' }, { profileCardType: 'retake' }]);
  assert.deepEqual(getProfileSettings(), { shownCard: 'current', profileCardType: 'retake', bio: '' });
});
