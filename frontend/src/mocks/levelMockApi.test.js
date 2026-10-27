// npm test : 레벨 · 직업 목 API(FE-114). 서버(BE-106)와 같은 곡선 · 직업 11등급 · 잠김 규칙을 따르는지 확인한다
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getLevel, mockLevelView, putLevelJob, resetMockLevel, setMockLevel, unlockMockNextJob } from './levelMockApi.js';

test('처음에는 Lv.7 · 무직이고 직업 11개 중 무직만 열려 있다', async () => {
  await resetMockLevel();
  const view = await getLevel();
  assert.equal(view.level, 7);
  assert.equal(view.job.code, 'NONE');
  assert.equal(view.jobs.length, 11);
  assert.deepEqual(view.jobs.filter((job) => job.unlocked).map((job) => job.code), ['NONE']);
  assert.equal(view.currentLevelXp <= view.xp && view.xp < view.nextLevelXp, true);
});

test('레벨 곡선: Lv1 = 0 XP, Lv100 = 10000 XP이고 최고 레벨의 nextLevelXp는 null', async () => {
  assert.equal((await setMockLevel(1)).xp, 0);
  const top = await setMockLevel(100);
  assert.equal(top.xp, 10000);
  assert.equal(top.nextLevelXp, null);
});

test('직업은 열린 가장 높은 직업이 기본이고, 고른 직업은 레벨이 올라도 유지된다', async () => {
  await resetMockLevel();
  await unlockMockNextJob(); // Lv.10 로스쿨생
  assert.equal(mockLevelView().job.code, 'LAW_STUDENT');
  await putLevelJob('NONE');
  await setMockLevel(40);
  const view = mockLevelView();
  assert.equal(view.job.code, 'NONE');
  assert.equal(view.jobs.find((job) => job.code === 'NONE').selected, true);
});

test('모르는 직업은 400, 안 열린 직업은 409 JOB_LOCKED', async () => {
  await resetMockLevel();
  await assert.rejects(() => putLevelJob('KING'), { status: 400, code: 'VALIDATION_ERROR' });
  await assert.rejects(() => putLevelJob('CHIEF_JUSTICE'), { status: 409, code: 'JOB_LOCKED' });
});

test('커뮤니티 경험치: 글 30(하루 3건) · 댓글 10 · 좋아요 2(글당 1회), 판결 공유 보너스는 사건당 1회', async () => {
  const { awardMockXp } = await import('./levelMockApi.js');
  await resetMockLevel();
  const before = mockLevelView().xp;
  assert.equal(awardMockXp('post', 1), 30);
  assert.equal(awardMockXp('post', 1), 0); // 같은 글은 한 번
  assert.equal(awardMockXp('post', 2), 30);
  assert.equal(awardMockXp('post', 3), 30);
  assert.equal(awardMockXp('post', 4), 0); // 하루 3건
  assert.equal(awardMockXp('verdictBonus', 1), 20);
  assert.equal(awardMockXp('verdictBonus', 1), 0);
  assert.equal(awardMockXp('comment', 7), 10);
  assert.equal(awardMockXp('like', 9), 2);
  assert.equal(awardMockXp('like', 9), 0);
  assert.equal(mockLevelView().xp, before + 30 * 3 + 20 + 10 + 2);
  await resetMockLevel();
});
