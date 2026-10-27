// npm test : 프로필 선택 설정 사본(FE-81). 바로 반영하고 서버에는 차례로 보내며, 거절되면 그 칸만 돌린다
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { configureProfileSettings, getProfileSettings, getCardLineSettings, hydrateProfileSettings, resetProfileSettings, updateCardLine, updateProfileSettings } from './profileSettings.js';

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

test('서버가 cardLine을 모르면(P-5에 없음) 카드 아래 줄은 서버로 가지 않는다', async () => {
  const calls = server();
  hydrateProfileSettings({ shownCard: 'current', profileCardType: 'current', bio: '' });
  assert.equal(await updateCardLine({ cardLine: 'job' }), true);
  assert.deepEqual(calls, []);
  assert.equal(getCardLineSettings().cardLine, 'job');
});

test('P-5에 cardLine이 오면 서버 값을 따르고 이후 바꾼 값은 서버로 간다', async () => {
  const calls = server({ shownCard: 'current', profileCardType: 'current', bio: '', cardLine: 'signature', cardLevel: true });
  hydrateProfileSettings({ shownCard: 'current', profileCardType: 'current', bio: '', cardLine: 'signature', cardLevel: true });
  assert.deepEqual(getCardLineSettings(), { cardLine: 'signature', cardLevel: true });
  assert.equal(await updateCardLine({ cardLevel: false }), true);
  assert.deepEqual(calls, [{ cardLevel: false }]);
  assert.equal(getCardLineSettings().cardLevel, false);
});

test('서버가 거절하면 카드 아래 줄이 이전 값으로 돌아간다', async () => {
  configureProfileSettings(async () => { throw { status: 400 }; });
  hydrateProfileSettings({ shownCard: 'current', profileCardType: 'current', bio: '', cardLine: 'type', cardLevel: false });
  assert.equal(await updateCardLine({ cardLine: 'job' }), false);
  assert.equal(getCardLineSettings().cardLine, 'type');
});

test('실패한 요청의 칸만 되돌리고 그 뒤에 바꾼 다른 칸은 지킨다', async () => {
  const pending = [];
  configureProfileSettings((patch) => new Promise((resolve, fail) => { pending.push({ resolve, fail, patch }); }));
  hydrateProfileSettings({ shownCard: 'current', profileCardType: 'current', bio: '', cardLine: 'type', cardLevel: false });
  const first = updateCardLine({ cardLine: 'job' });
  const second = updateCardLine({ cardLevel: true }); // 첫 요청이 끝나기 전에 다른 칸을 바꾼다
  await new Promise((resolve) => setImmediate(resolve)); // 첫 요청이 서버로 나간다
  pending[0].fail({ status: 400 });
  assert.equal(await first, false);
  await new Promise((resolve) => setImmediate(resolve)); // 둘째 요청이 서버로 나간다
  pending[1].resolve({ cardLine: 'type', cardLevel: true });
  assert.equal(await second, true);
  assert.deepEqual(getCardLineSettings(), { cardLine: 'type', cardLevel: true });
});

test('cardLine이 없는 응답을 받으면 서버 지원 상태를 해제하고 로컬 설정은 그대로 둔다', async () => {
  const calls = server();
  hydrateProfileSettings({ shownCard: 'current', profileCardType: 'current', bio: '', cardLine: 'job', cardLevel: true });
  hydrateProfileSettings({ shownCard: 'current', profileCardType: 'current', bio: '' }); // 서버 롤백
  assert.deepEqual(getCardLineSettings(), { cardLine: 'job', cardLevel: true });
  assert.equal(await updateCardLine({ cardLine: 'signature' }), true);
  assert.deepEqual(calls, []);
});
