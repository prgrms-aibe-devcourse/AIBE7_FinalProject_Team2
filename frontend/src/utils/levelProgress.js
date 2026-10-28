// 경험치 진행률(FE-116 · FE-120): 지금 레벨에 닿은 XP부터 다음 레벨에 필요한 XP까지. 서버 L-1의 xp · currentLevelXp · nextLevelXp를 쓴다.
// 최고 레벨(nextLevelXp가 null)이면 막대가 가득 찬다. 마이페이지 첫 화면의 막대와 경험치 페이지가 같이 쓴다
export function progressOf(level) {
  const top = level.nextLevelXp == null;
  const span = top ? 1 : level.nextLevelXp - level.currentLevelXp;
  const done = top ? 1 : Math.min(span, Math.max(0, level.xp - level.currentLevelXp));
  return { top, span, done, percent: Math.round((done / span) * 100) };
}
