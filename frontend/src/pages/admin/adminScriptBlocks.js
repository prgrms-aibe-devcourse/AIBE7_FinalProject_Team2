// 대본 블록 검수의 순수 함수 (FE-148). 화면(adminCaseScript.js)과 목 API가 함께 쓰고, 테스트(adminScriptBlocks.test.js)가 있다.
//
// 서버 계약: API 명세 A-13-10(블록 · 낡음 · scriptStatus · releasedAt), A-13-18 ~ 21(블록 승인 · 반려 · 확인했음 · 문장 수정).
// 블록 모양은 파이프라인 계약(`tools/ai-judgment/prompts/script/README.md` "저장 블록")과 같고, 검수 결과의 path는 블록 값 안의
// JSON Pointer다 — 화면은 블록 모양을 따로 알지 않고 문자열 자리를 path로 찾아 그린다(블록 모양이 바뀌어도 화면을 고치지 않게).

export const SCRIPT_UNITS = ['U0', 'U1', 'U2', 'U3', 'U4'];

export const UNIT_LABEL = {
  U0: '설계도 (신문 질문 · 답)',
  U1: '1막 해설',
  U2: '재판 도입부',
  U3: '증거조사 대사',
  U4: '3막 의견',
};

/** 사용자 쪽 대본 상태 (API 1 · 16과 같은 값, BE-126 2차-2) */
export const SCRIPT_STATUS = { READY: '사용자에게 공개 중', PREPARING: '준비 중', MAINTENANCE: '점검 중' };

/** 낡음 근거 키 → 화면 문구 (A-13-10 staleReasons · unknownBasis) */
export const BASIS_LABEL = {
  U0: '설계도(U0)',
  U2: '재판 도입부(U2)',
  case: '사건 내용',
  paper: '기사',
  cast: '배역',
  mood: '분류 · 분위기',
  issue: '분류 · 쟁점',
  victim: '분류 · 피해자 측 입장',
  thirdParty: '분류 · 제3자',
  sensitiveFlags: '민감 플래그',
};

/** 권장 수정 순서 (A-13-10). 낡음이 여럿이면 앞에서부터 고친다 */
export const FIX_ORDER = '분류 · 배역 → 기사 → U0 → U1 · U2 → U3 → U4';

// 대사 · 문장 길이 규칙. 파이프라인 MAX_CHARS · MAX_LINES와 같고, 서버도 같은 규칙으로 거절한다(A-13-13 · 21)
export const MAX_CHARS = 100;
export const MAX_LINES = 2;

/** 줄바꿈을 뺀 글자 수. 파이프라인이 세는 방식과 같다(sum(len(line))) */
export function countChars(text) {
  return text.split('\n').filter((part) => part.trim()).reduce((sum, part) => sum + part.length, 0);
}

/** 길이 규칙 위반 문구. 지키면 null */
export function textViolation(text) {
  const lines = text.split('\n').filter((part) => part.trim());
  if (!lines.length) return '문장을 입력해 주세요.';
  if (lines.length > MAX_LINES) return `${MAX_LINES}줄까지 쓸 수 있어요.`;
  const chars = countChars(text);
  if (chars > MAX_CHARS) return `줄바꿈을 빼고 ${MAX_CHARS}자까지 쓸 수 있어요. (지금 ${chars}자)`;
  return null;
}

/** JSON Pointer 한 토큰 (RFC 6901: ~ → ~0, / → ~1) */
const escapeToken = (token) => String(token).replaceAll('~', '~0').replaceAll('/', '~1');

/**
 * 블록 값 안의 문자열 자리를 모두 꺼낸다 — 고칠 수 있는 자리(A-13-21은 이미 문자열이 있는 자리만 고친다).
 *
 * @returns [{ path, text }] 블록 값의 순서 그대로
 */
export function textLeaves(content, path = '') {
  if (typeof content === 'string') return [{ path, text: content }];
  if (Array.isArray(content)) return content.flatMap((item, index) => textLeaves(item, `${path}/${index}`));
  if (content && typeof content === 'object') {
    return Object.entries(content).flatMap(([key, value]) => textLeaves(value, `${path}/${escapeToken(key)}`));
  }
  return [];
}

// path의 마지막 이름 → 화면 문구. 모르는 이름은 path를 그대로 보인다(검수 결과 path와 대조할 수 있게)
const KEY_LABEL = {
  q: '질문', a: '답', lines: '해설', issues: '쟁점', issues_narr: '쟁점 해설', indict: '공소 요지', plea: '인부',
  prosecutor: '검사 의견', defense: '최종변론', law: '적용 법조', cards: '카드', ul: '항목', b: '강조', p: '문단',
};

/** path → 읽을 이름. 예: /questions/0/q → "질문 1 · 질문", /lines/4 → "해설 5" */
export function leafLabel(path) {
  const tokens = path.split('/').slice(1);
  const parts = [];
  tokens.forEach((token, index) => {
    if (/^\d+$/.test(token)) {
      const owner = tokens[index - 1];
      const name = owner === 'questions' ? '질문' : (KEY_LABEL[owner] ?? owner ?? '');
      parts.push(`${name} ${Number(token) + 1}`.trim());
      return;
    }
    // 배열 이름은 뒤의 번호와 합쳐 보이므로 따로 적지 않는다
    if (/^\d+$/.test(tokens[index + 1] ?? '')) return;
    parts.push(KEY_LABEL[token] ?? token);
  });
  return parts.join(' · ') || '블록 전체';
}

/** 검수 결과 중 그 자리 것 (path가 같은 것) */
export function issuesAt(review, path) {
  return (review ?? []).filter((issue) => (issue.path ?? '') === path);
}

const hasError = (review) => (review ?? []).some((issue) => issue.severity === 'error');

/** 낡음 근거 키 목록 → "사건 내용 · 기사" */
export function basisText(keys) {
  return (keys ?? []).map((key) => BASIS_LABEL[key] ?? key).join(' · ');
}

/**
 * 블록을 승인할 수 없는 이유 (A-13-18과 같은 조건, 서버도 409로 막는다). 승인할 수 있으면 null.
 * 대본 조건(멈춤 · 만들지 못한 것 · 대본 전체 검수 오류)을 먼저 본다 — 그때는 블록을 고쳐도 풀리지 않는다.
 */
export function blockApproveGate(script, block) {
  if (script.stopped) return '생성이 중간에 멈춘 대본이라 블록을 승인할 수 없어요. 다시 생성해 주세요.';
  if ((script.pending ?? []).length) {
    return `만들지 못한 것(${script.pending.join(' · ')})이 있어 블록을 승인할 수 없어요. 다시 생성해 주세요.`;
  }
  if (hasError(script.review)) return '대본 전체 검수가 돌지 못했어요(REVIEW_FAILED). 다시 생성해 주세요.';
  if (!block.publishable) return '이 블록에 검수 오류가 있어요. 오류 자리를 고치면 승인할 수 있어요.';
  if (block.staleBlocksApproval) {
    return `만든 뒤 ${basisText(block.staleReasons)}이(가) 바뀌었어요. 다시 생성하거나 "확인했음"으로 표시해 주세요.`;
  }
  return null;
}

/** 승인을 막는 낡음이 있는 블록 단위 (통승인도 이것 때문에 막힌다) */
export function blockingStaleUnits(script) {
  return (script.blocks ?? []).filter((block) => block.staleBlocksApproval).map((block) => block.unit);
}

/**
 * 승인을 푸는 요청(블록 반려 · 문장 수정 · 대사 수정 · 통거절) 전에 물을 문구. 사용자에게 나가 있는 대본(releasedAt)이 지금 공개
 * 중(READY)이면 이 요청으로 점검 중이 된다 — 오탈자 하나로 사건 체험이 잠기는 실수를 막는다(PR #384 리뷰 1번, FE-148).
 * 이미 점검 중이거나 나간 적 없는 대본은 사용자가 보던 것을 거두지 않으므로 묻지 않는다(DESIGN.md 확인 기준).
 *
 * @returns [무엇을, 결과] 또는 null
 */
export function takeDownConfirm(script, what) {
  if (!script.releasedAt || script.scriptStatus !== 'READY') return null;
  return [what, '공개 중인 대본이에요. 바꾸면 다시 승인할 때까지 사용자에게 "점검 중"으로 보이고 이 사건 체험이 잠겨요.'];
}

/**
 * 이 블록 승인으로 대본이 사용자에게 새로 공개되는지 — 다른 네 블록이 승인돼 있고 사건이 공개 중이면 이 승인이 마지막이다.
 * 사용자에게 새로 공개하는 작업이라 묻는다(DESIGN.md 확인 기준)
 */
export function opensToUsers(script, unit, caseStatus) {
  if (caseStatus !== 'PUBLISHED') return false;
  const blocks = script.blocks ?? [];
  if (SCRIPT_UNITS.some((name) => !blocks.some((block) => block.unit === name))) return false;
  return blocks.every((block) => block.unit === unit || block.reviewStatus === 'APPROVED');
}
