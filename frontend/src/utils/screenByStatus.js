// API 명세 1-6 "보낼 화면" 표. 체험 상태별로 이어서 볼 화면이다.
export const screenByStatus = {
  STARTED: 'overview', PRE_JUDGED: 'review', REVIEWING: 'review', REVIEWED: 'summary',
  VERDICT_CONFIRMED: 'ai', AI_REVEALED: 'court', COMPLETED: 'comparison',
};
