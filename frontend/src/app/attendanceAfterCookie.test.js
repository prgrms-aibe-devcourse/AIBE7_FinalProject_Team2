// npm test : 쿠키가 만들어지는 요청이 처음 성공한 뒤 출석을 한 번 더 부른다 (FE-71, BE-98 전까지 임시)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { attendanceAfterCookie } from './attendanceAfterCookie.js';

const fakeApi = (overrides = {}) => {
  const calls = [];
  const api = {
    startExperience: async () => 'started',
    submitPersonality: async () => 'submitted',
    putProfileDeco: async () => 'saved',
    getCases: async () => 'cases',
    postAttendance: async () => { calls.push('attendance'); return null; },
    ...overrides,
  };
  return { api, calls };
};

test('쿠키를 만드는 요청이 처음 성공하면 출석을 한 번 더 부르고, 결과는 그대로 돌려준다', async () => {
  const { api, calls } = fakeApi();
  const wrapped = attendanceAfterCookie(api);
  assert.equal(await wrapped.startExperience(1), 'started');
  assert.deepEqual(calls, ['attendance']);
});

test('이번 방문에서는 한 번만 부른다', async () => {
  const { api, calls } = fakeApi();
  const wrapped = attendanceAfterCookie(api);
  await wrapped.startExperience(1);
  await wrapped.submitPersonality([]);
  await wrapped.putProfileDeco({});
  assert.deepEqual(calls, ['attendance']);
});

test('요청이 실패하면 부르지 않고 에러를 그대로 던진다', async () => {
  const failure = { code: 'CASE_NOT_FOUND' };
  const { api, calls } = fakeApi({ startExperience: async () => { throw failure; } });
  const wrapped = attendanceAfterCookie(api);
  await assert.rejects(wrapped.startExperience(9), failure);
  assert.deepEqual(calls, []);
  await wrapped.putProfileDeco({}); // 다음 성공에서 부른다
  assert.deepEqual(calls, ['attendance']);
});

test('출석이 실패해도 원래 요청은 성공하고, 쿠키와 상관없는 요청은 감싸지 않는다', async () => {
  const { api } = fakeApi({ postAttendance: async () => { throw new Error('down'); } });
  const wrapped = attendanceAfterCookie(api);
  assert.equal(await wrapped.submitPersonality([]), 'submitted');
  assert.equal(wrapped.getCases, undefined);
});

test('출석 API가 없는 api(일부 목)는 그냥 지나간다', async () => {
  const { api } = fakeApi({ postAttendance: undefined });
  const wrapped = attendanceAfterCookie(api);
  assert.equal(await wrapped.startExperience(1), 'started');
});
