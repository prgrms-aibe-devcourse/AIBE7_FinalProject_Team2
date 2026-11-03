// 내 글 삭제(C-8) 실패를 사용자에게 알리는 문구 (FE-165). communityParts.js에서 떼어 두어 node --test로 확인한다(deleteError.test.js).
export const DELETE_ERRORS = {
  UNAUTHORIZED: '로그인이 풀렸어요. 다시 로그인해 주세요.',
  FORBIDDEN: '내 글만 지울 수 있어요.',
  POST_NOT_FOUND: '이미 지워진 글이에요. 목록으로 돌아가 주세요.',
};
export const DELETE_FAILED = '글을 지우지 못했어요. 잠시 뒤 다시 시도해 주세요.';

export const deleteErrorText = (failure) => (Object.hasOwn(DELETE_ERRORS, failure?.code) ? DELETE_ERRORS[failure.code] : DELETE_FAILED);
