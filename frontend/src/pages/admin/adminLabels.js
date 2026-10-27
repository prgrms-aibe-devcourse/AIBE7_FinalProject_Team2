// S-19 화면 문구 (API 명세 8-2의 코드 → 한국어)
import { AXES } from '../../utils/personalityAxes.js';

export const CASE_STATUS = { DRAFT: '적재됨 (검수 전)', REVIEW: '검수 중', PUBLISHED: '공개됨' };
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
  generate: 'AI 판결 생성', select: '회차 선택', axis: '가치관 축 분류', script: '증거조사 대사 생성', load: '사건으로 만들기 (생성이 끝난 뒤 따로 눌러요)',
};
// 단계 표시: 기호만으로는 "건너뜀(–)"과 "대기(·)"가 구분되지 않아 글로도 적는다 (FE-105)
export const IMPORT_STAGE_STATUS = {
  DONE: ['✓', '완료'], RUNNING: ['…', '진행 중'], FAILED: ['✕', '실패'], SKIPPED: ['–', '건너뜀'], PENDING: ['·', '대기'],
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
  // 운영 메뉴
  ALREADY_SUSPENDED: '이미 정지된 회원이에요.',
  MEMBER_WITHDRAWN: '탈퇴한 회원은 정지할 수 없어요.',
  NOT_SUSPENDED: '정지 상태가 아닌 회원이에요.',
  NOT_VISIBLE: '공개 중이 아니에요.',
  NOT_HIDDEN: '숨김 상태가 아니에요.',
  ALREADY_DELETED: '이미 삭제했어요.',
  ALREADY_HANDLED: '이미 처리한 신고예요.',
  SELF_DEACTIVATE: '자기 자신의 계정은 비활성화할 수 없어요.',
};

// 운영 메뉴(회원 · 게시글 · 신고 · 관리자 계정 · 공지) 문구
export const MEMBER_STATUS = { ACTIVE: '정상', SUSPENDED: '정지', WITHDRAWN: '탈퇴' };
export const POST_STATUS = { VISIBLE: '공개', HIDDEN: '숨김', DELETED: '삭제' };
export const REPORT_STATUS = { PENDING: '처리 대기', RESOLVED: '처리 완료', REJECTED: '기각' };
export const REPORT_REASON = { SPAM: '광고 · 도배', ABUSE: '욕설 · 비방', ADULT: '선정적 내용', OTHER: '기타' };
export const TARGET_TYPE = { POST: '게시글', COMMENT: '댓글', MEMBER: '회원' };
export const REPORT_ACTION = { HIDE: '숨김 처리', SUSPEND: '회원 정지 (7일)', WARN: '경고' };
export const ADMIN_ROLE = { SUPER: '총괄', REVIEWER: '검수' };
export const AUDIT_ACTION = {
  LOGIN_SUCCESS: '로그인', LOGIN_FAILURE: '로그인 실패', LOGOUT: '로그아웃', SOURCE_VIEW: '원본 판결문 열람', JUDGMENT_APPROVE: '판결 후보 승인',
  JUDGMENT_REJECT: '판결 후보 반려', CASE_UPDATE: '사건 내용 수정', VALUE_AXIS_CONFIRM: '가치관 축 확정', CASE_PUBLISH: '사건 공개',
};

export const AXIS_LABEL = Object.fromEntries(AXES.map((axis) => [axis.key, axis.label]));
export const AXIS_OPTIONS = [...AXES.map((axis) => ({ value: axis.key, label: axis.label })), { value: '', label: '어느 축에도 맞지 않음' }];

/** 관리자 API 에러 → 화면 문구 */
export function errorText(error, fallback = '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.') {
  const code = error?.code;
  if (code === 'INVALID_STATE') return STATE_REASON[error.details?.[0]?.reason] ?? '지금 상태에서는 할 수 없어요.';
  if (code === 'PUBLISH_NOT_READY') return (error.details ?? []).map((item) => PUBLISH_REASON[item.reason] ?? item.reason).join(' ') || '공개 조건을 채우지 못했어요.';
  if (code === 'DUPLICATE_TITLE') return '다른 사건과 제목이 겹쳐요.';
  if (code === 'DUPLICATE_EMAIL') return '이미 등록된 이메일이에요.';
  if (code === 'DUPLICATE_WORD') return '이미 있는 금칙어예요.';
  if (code === 'DUPLICATE_SOURCE') return '이미 등록한 사건번호예요.';
  if (code === 'IMPORT_IN_PROGRESS') return '다른 사건을 등록하는 중이에요. 끝난 뒤 다시 시도해 주세요.';
  if (code === 'IMPORT_NOT_FOUND') return '등록 작업을 찾을 수 없어요.';
  // 적재(A-14-1, FE-72). IMPORT_RESULT_INVALID의 details[].field는 생성 결과에서 비었거나 잘못된 값의 경로다(예: case.factors[2].label)
  if (code === 'IMPORT_NOT_READY') return error.message || '아직 적재할 수 없어요. 생성이 끝났는지 확인해 주세요. 이미 적재한 등록일 수도 있어요.'; // 서버가 사유를 주면 그 문구가 더 정확하다(예: 생성 결과가 사라짐, PR #285 리뷰)
  if (code === 'IMPORT_RESULT_INVALID') {
    const fields = (error.details ?? []).map((item) => item.field).filter(Boolean);
    return `생성 결과를 사건으로 만들 수 없어요. 다시 생성해 주세요.${fields.length ? ` (문제가 된 값: ${fields.join(', ')})` : ''}`;
  }
  if (code === 'IMPORT_UNAVAILABLE') return '사건 등록 기능을 지금 쓸 수 없어요. 파이프라인 서비스 상태를 확인한 뒤 다시 시도해 주세요.';
  if (code?.endsWith('_NOT_FOUND') && error.message) return error.message; // 구체적인 _NOT_FOUND 분기보다 뒤에 둔다
  if (code === 'VALIDATION_ERROR') return error.message || '입력값을 확인해 주세요.';
  if (code === 'FORBIDDEN') return '권한이 없거나 보안 토큰이 만료됐어요. 새로고침 뒤 다시 시도해 주세요.';
  if (code === 'NETWORK_ERROR') return error.message;
  return fallback;
}

// 관리자 화면의 날짜 표시는 이 한 곳에서 만든다(빈 값 "—", 읽을 수 없는 값은 그대로)
function formatStamp(value, withTime) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (n) => String(n).padStart(2, '0');
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return withTime ? `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}` : day;
}
export const formatDate = (value) => formatStamp(value, false);
export const formatDateTime = (value) => formatStamp(value, true);
