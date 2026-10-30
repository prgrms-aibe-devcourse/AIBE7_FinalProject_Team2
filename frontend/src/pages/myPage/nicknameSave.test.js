// npm test : 내 정보 수정의 닉네임 저장 · 회원 정보 반영 규칙 (FE-137 리뷰 반영)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NICKNAME_REJECTED, NICKNAME_TAKEN, SESSION_EXPIRED, isNicknameChanged, nicknameFailure, planMeSync, saveNickname, saveStatus } from './nicknameSave.js';

const fakeApi = (put) => { const calls = []; return { calls, putNickname: async (nickname) => { calls.push(nickname); return put(nickname); } }; };

test('바뀐 닉네임만 서버에 저장한다 — 성공하면 서버가 돌려준 값을 쓴다', async () => {
  const api = fakeApi((nickname) => ({ nickname }));
  assert.deepEqual(await saveNickname({ api, wanted: ' 내로판사 ', saved: '' }), { changed: true, ok: true, nickname: '내로판사' });
  assert.deepEqual(api.calls, ['내로판사']);
  // 저장한 값으로 갱신했으면 다시 저장하지 않는다
  assert.deepEqual(await saveNickname({ api, wanted: '내로판사', saved: '내로판사' }), { changed: false });
  assert.equal(api.calls.length, 1);
});

test('아직 닉네임을 정하지 않은 회원이 칸을 비운 채 저장하면 서버를 부르지 않는다', async () => {
  const api = fakeApi(() => ({}));
  assert.equal(isNicknameChanged('', ''), false);
  assert.equal(isNicknameChanged('  ', ''), false);
  assert.deepEqual(await saveNickname({ api, wanted: '', saved: '' }), { changed: false });
  assert.equal(api.calls.length, 0);
});

test('중복(409)은 닉네임 칸 아래 안내, 한 줄 소개는 저장했다고 함께 알린다', async () => {
  const api = fakeApi(() => { throw { code: 'DUPLICATE_NICKNAME', status: 409 }; });
  const result = await saveNickname({ api, wanted: '판사', saved: '' });
  assert.deepEqual(result, { changed: true, ok: false, fieldMessage: NICKNAME_TAKEN, statusText: '닉네임을 저장하지 못했어요.' });
  assert.equal(saveStatus({ nicknameStatusText: result.statusText, bioSaved: true }), '닉네임을 저장하지 못했어요. 한 줄 소개는 저장했어요.');
  assert.equal(saveStatus({ nicknameStatusText: result.statusText, bioSaved: false }), '닉네임을 저장하지 못했어요. 한 줄 소개도 저장하지 못했어요.');
});

test('형식(400) 거절은 칸 아래, 로그인이 풀리면(401) 다시 로그인, 연결 오류는 다시 시도로 안내한다', () => {
  assert.equal(nicknameFailure({ code: 'VALIDATION_ERROR' }).fieldMessage, NICKNAME_REJECTED);
  assert.deepEqual(nicknameFailure({ code: 'UNAUTHORIZED', status: 401 }), { fieldMessage: '', statusText: SESSION_EXPIRED });
  assert.deepEqual(nicknameFailure({ code: 'NETWORK_ERROR' }), { fieldMessage: '', statusText: '닉네임을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.' });
});

test('닉네임 저장이 없으면 한 줄 소개 결과만 알린다', () => {
  assert.equal(saveStatus({ nicknameStatusText: '', bioSaved: true }), '저장했어요.');
  assert.equal(saveStatus({ nicknameStatusText: '', bioSaved: false }), '한 줄 소개를 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
});

test('저장한 뒤 회원 정보가 예전 값으로 늦게 도착해도 닉네임 칸과 로그인 표시를 되돌리지 않는다 (리뷰 필수 1)', () => {
  const late = { nickname: '옛닉네임', displayName: '옛닉네임' };
  assert.deepEqual(planMeSync({ me: late, inputValue: '새닉네임', savedNickname: '새닉네임', touched: true, currentNickname: '새닉네임' }), {});
  // 저장을 시도했지만 실패한 경우에도(touched) 늦은 응답으로 사용자가 입력한 칸을 덮지 않는다
  assert.deepEqual(planMeSync({ me: late, inputValue: '입력중', savedNickname: '옛닉네임', touched: true, currentNickname: '옛닉네임' }), {});
});

test('저장하지 않았으면 다른 곳에서 바꾼 닉네임을 아직 고치지 않은 칸에만 맞춘다', () => {
  const me = { nickname: '바뀐닉', displayName: '바뀐닉' };
  assert.deepEqual(planMeSync({ me, inputValue: '옛닉', savedNickname: '옛닉', touched: false, currentNickname: '옛닉' }),
    { savedNickname: '바뀐닉', inputValue: '바뀐닉', sessionNickname: '바뀐닉' });
  // 사용자가 이미 칸을 고치고 있으면 칸은 두고 로그인 표시만 맞춘다
  assert.deepEqual(planMeSync({ me, inputValue: '고치는중', savedNickname: '옛닉', touched: false, currentNickname: '옛닉' }), { sessionNickname: '바뀐닉' });
});

test('로그인 표시는 화면을 그릴 때의 값이 아니라 지금 값과 비교한다', () => {
  const me = { nickname: '지금닉', displayName: '지금닉' };
  // 화면을 그린 뒤 로그인 표시가 이미 지금닉으로 바뀌어 있으면 다시 쓰지 않는다
  assert.deepEqual(planMeSync({ me, inputValue: '지금닉', savedNickname: '지금닉', touched: false, currentNickname: '지금닉' }), {});
});

test('닉네임이 없는 카카오 회원은 칸을 비워 두고 표시 이름(방청객 {회원 ID})으로 로그인 표시를 맞춘다', () => {
  const me = { nickname: null, displayName: '방청객 1024' };
  assert.deepEqual(planMeSync({ me, inputValue: '', savedNickname: '', touched: false, currentNickname: '카카오 회원' }), { sessionNickname: '방청객 1024' });
});
