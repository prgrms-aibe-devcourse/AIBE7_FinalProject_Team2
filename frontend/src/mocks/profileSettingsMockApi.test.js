// npm test : 프로필 선택 설정 목 API(P-8) — 서버와 같은 규칙 (FE-81, BE-104)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mockProfileSettings, patchProfileSettings } from './profileDecoMockApi.js';

test('저장한 적이 없으면 최근 · 최근 · 소개 없음', () => {
  assert.deepEqual(mockProfileSettings(), { shownCard: 'current', profileCardType: 'current', bio: '' });
});

test('보낸 칸만 바꾸고 응답은 세 값 전체, 소개는 앞뒤 공백을 뺀다', async () => {
  assert.deepEqual(await patchProfileSettings({ shownCard: 'retake' }), { shownCard: 'retake', profileCardType: 'current', bio: '' });
  assert.deepEqual(await patchProfileSettings({ bio: '  판결은 신중하게  ' }), { shownCard: 'retake', profileCardType: 'current', bio: '판결은 신중하게' });
  assert.equal((await patchProfileSettings({ bio: '' })).bio, ''); // 지우기
  assert.equal(mockProfileSettings().shownCard, 'retake'); // 앞서 저장한 칸 유지
});

test('빈 객체 · 모르는 칸 · null · 문자열 아님 · 세 값 밖 · 40자 초과 · 제어 문자는 400', async () => {
  const bad = [{}, [], null, { other: 'x' }, { shownCard: null }, { shownCard: 1 }, { profileCardType: 'last' }, { bio: '가'.repeat(41) }, { bio: '두\n줄' }, { bio: '탭\t소개' }, { bio: 'a\u0000b' }];
  for (const body of bad) await assert.rejects(patchProfileSettings(body), { code: 'VALIDATION_ERROR', status: 400 }, JSON.stringify(body));
  assert.equal((await patchProfileSettings({ bio: '가'.repeat(40) })).bio.length, 40);
});
