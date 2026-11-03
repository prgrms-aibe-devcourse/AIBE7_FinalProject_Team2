import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ACHIEVEMENTS, FREE, REWARD_TYPES } from './profileDecoMockData.js';
import { BACKGROUNDS, FRAMES } from '../utils/profileDeco.js';

// 서버 표(ProfileDecoCatalog.java)가 원본이다. 목 해금표 · 화면 칸 목록이 어긋나면 "화면엔 있는데 열 수 없는 칸"이 생기므로 자바 파일을 읽어 맞춰 본다(FE-162)
const catalog = readFileSync(new URL('../../../backend/src/main/java/com/team2/project/profile/domain/ProfileDecoCatalog.java', import.meta.url), 'utf8');

const strings = (text) => [...text.matchAll(/"([^"]*)"/g)].map((match) => match[1]);
const listOf = (name) => strings(new RegExp(`${name} = List\\.of\\(([^;]*?)\\);`, 's').exec(catalog)[1]);

// 보상 빌더 호출(.frames("a", "b") …)을 종류별 배열로 읽는다. 목 표와 같이 없는 종류는 빈 배열
function rewardsOf(text) {
  return Object.fromEntries(REWARD_TYPES.map((type) => {
    const call = new RegExp(`\\.${type}\\(([^)]*)\\)`).exec(text);
    return [type, call ? strings(call[1]) : []];
  }));
}

const serverAchievements = catalog.split('new Achievement(').slice(1).map((chunk) => ({
  id: /^"([^"]+)"/.exec(chunk)[1],
  goal: Number(/,\s*(\d+),/.exec(chunk)[1]),
  rewards: rewardsOf(chunk.split(/\n\s*new Achievement\(/)[0]),
}));

test('서버 FRAMES · BACKGROUNDS가 화면 칸 목록과 같다', () => {
  assert.deepEqual(listOf('FRAMES'), FRAMES.map((frame) => frame.id));
  assert.deepEqual(listOf('BACKGROUNDS'), BACKGROUNDS.map((background) => background.id));
});

test('서버 도전과제의 id · 목표 · 보상이 목 표와 같다', () => {
  assert.equal(serverAchievements.length, ACHIEVEMENTS.length);
  serverAchievements.forEach((server, index) => {
    const mock = ACHIEVEMENTS[index];
    assert.equal(mock.id, server.id);
    assert.equal(mock.goal, server.goal, `${server.id} 목표`);
    assert.deepEqual(mock.rewards, server.rewards, `${server.id} 보상`);
  });
});

test('처음부터 열린 것이 서버 FREE와 같다', () => {
  const free = /FREE = Rewards\.builder\(\)([^;]*);/s.exec(catalog)[1];
  assert.deepEqual(rewardsOf(free), FREE);
});
