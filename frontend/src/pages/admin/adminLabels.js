// S-19 화면 문구 (API 명세 8-2의 코드 → 한국어)
import { AXES } from '../../utils/personalityAxes.js';

export const CASE_STATUS = { DRAFT: '적재됨 (손대기 전)', REVIEW: '검수 중', PUBLISHED: '공개됨' };
export const REVIEW_STATUS = { PENDING: '검수 대기', APPROVED: '승인', REJECTED: '반려' };
export const SUBJECT = { AI: 'AI 판결 후보', COURT: '재판부 판결 후보' };
export const CRIME_TYPE = { MURDER: '살인', FRAUD: '사기', INJURY: '상해' };
export const DIFFICULTY = { LOW: '쉬움', MID: '보통', HIGH: '어려움' };
export const REVEAL_STAGE = { OVERVIEW: '개요(사전 판단)', DETAIL: '상세' };
export const COURT_LEVEL = { FIRST: '1심', APPEAL: '2심', SUPREME: '대법원' };

// 파이프라인 generation_report 값 (tools/ai-judgment)
export const CONTAMINATION_VERDICT = {
  CLEAN: '통과 (사전 학습 의심 없음)',
  SUSPECT: '의심 (팀 확인 필요)',
  INSUFFICIENT: '응답 부족 (판정 불가)',
  CONTAMINATED: '사전 학습 의심 높음',
  SKIPPED: '점검하지 않음',
};
export const SELECT_STRATEGY = { consensus: '합의값에 가까운 회차', 'first-valid': '처음 유효한 회차', 'fewest-warnings': '경고가 가장 적은 회차', manual: '직접 지정' };

// A-14 · A-15 사건 등록(파이프라인 실행)
export const IMPORT_STATUS = { QUEUED: '대기 중', RUNNING: '실행 중', SUCCEEDED: '완료', FAILED: '실패' };
export const IMPORT_STAGE = {
  extract: '비식별화 · 구조화', court: '재판부 판결 정리', contamination: '사전 학습 점검', rag: '참고 자료 검색',
  generate: 'AI 판결 생성', select: '회차 선택', axis: '가치관 축 분류', load: 'DB 적재',
};

// A-13 공개 조건 코드
export const PUBLISH_REASON = {
  NO_PUBLISHED_AI: '공개할 AI 판결을 승인해야 해요.',
  NO_PUBLISHED_COURT: '공개할 재판부 판결을 승인해야 해요.',
  NO_FACTORS: '판단 요소가 1개 이상 있어야 해요.',
  NO_OVERVIEW_FACTOR: '사전 판단에 쓸 개요 단계 판단 요소가 있어야 해요.',
  NO_PENALTY_RULE: '형벌 규칙이 1개 이상 있어야 해요. (SQL 경로로 추가)',
};

// INVALID_STATE details 사유 코드
export const STATE_REASON = {
  CASE_PUBLISHED: '이미 공개한 사건이라 바꿀 수 없어요.',
  CANDIDATE_REJECTED: '이미 반려한 후보예요.',
  CANDIDATE_PUBLISHED: '공개 중인 후보는 반려할 수 없어요. 먼저 다른 후보를 승인해 교체해 주세요.',
};

export const AXIS_LABEL = Object.fromEntries(AXES.map((axis) => [axis.key, axis.label]));
export const AXIS_OPTIONS = [...AXES.map((axis) => ({ value: axis.key, label: axis.label })), { value: '', label: '어느 축에도 맞지 않음' }];

/** 관리자 API 에러 → 화면 문구 */
export function errorText(error, fallback = '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.') {
  const code = error?.code;
  if (code === 'INVALID_STATE') return STATE_REASON[error.details?.[0]?.reason] ?? '지금 상태에서는 할 수 없어요.';
  if (code === 'PUBLISH_NOT_READY') return (error.details ?? []).map((item) => PUBLISH_REASON[item.reason] ?? item.reason).join(' ') || '공개 조건을 채우지 못했어요.';
  if (code === 'DUPLICATE_TITLE') return '다른 사건과 제목이 겹쳐요.';
  if (code === 'DUPLICATE_SOURCE') return '이미 등록한 사건번호예요.';
  if (code === 'IMPORT_IN_PROGRESS') return '다른 사건을 등록하는 중이에요. 끝난 뒤 다시 시도해 주세요.';
  if (code === 'IMPORT_NOT_FOUND') return '등록 작업을 찾을 수 없어요.';
  if (code === 'VALIDATION_ERROR') return error.message || '입력값을 확인해 주세요.';
  if (code === 'FORBIDDEN') return '권한이 없거나 보안 토큰이 만료됐어요. 새로고침 뒤 다시 시도해 주세요.';
  if (code === 'NETWORK_ERROR') return error.message;
  return fallback;
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
