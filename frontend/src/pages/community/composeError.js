// 글쓰기(C-3) 실패를 사용자에게 고칠 방법으로 알리는 문구 (FE-137). communityParts.js에서 떼어 두어 node --test로 확인한다(composeError.test.js).
export const SESSION_EXPIRED = '로그인이 풀렸어요. 다시 로그인해 주세요.';
export const VERDICT_NOT_CONFIRMED = '판결을 확정한 뒤에 내 판결을 붙일 수 있어요.';

export function composeErrorText(failure) {
  if (failure?.code === 'UNAUTHORIZED') return SESSION_EXPIRED;
  if (failure?.code === 'VALIDATION_ERROR') {
    // 판결 공유인데 확정한 판결이 없으면(C-3 details shareVerdict · VERDICT_NOT_CONFIRMED) 제목 · 본문 탓이 아니라고 바로 알린다.
    // 화면이 미리 막아도 다른 탭에서 체험 상태가 바뀌면 서버가 거절할 수 있다
    const noVerdict = Array.isArray(failure.details) && failure.details.some((detail) => detail?.field === 'shareVerdict');
    return noVerdict ? VERDICT_NOT_CONFIRMED : '입력한 내용을 확인해 주세요.';
  }
  return '글을 올리지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}
