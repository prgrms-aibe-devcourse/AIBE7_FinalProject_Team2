// api 객체를 감싸서, 공통 에러가 오면 화면을 맞는 곳으로 보낸다 (API 명세 1-5 · 1-6)
//
//   INVALID_STATE        → 에러의 currentStatus에 맞는 화면 (screenByStatus 표)
//   EXPERIENCE_NOT_FOUND → 사건 목록(S-02) — 체험을 시작한 적이 없거나 쿠키가 없음
//   CASE_NOT_FOUND       → 404 · 오류 화면(S-14)
//
// 화면은 이미 에러 코드별 안내를 직접 처리하고 있다. 여기서는 이동만 하고 에러는 그대로 다시 던진다.
// 화면이 이동 뒤에 자기 안내를 그려도 이미 화면에서 떼어진 뒤라 보이지 않는다.
import { screenByStatus } from '../utils/screenByStatus.js';

// 이 API는 아래 에러를 화면이 직접 처리하므로 이동하지 않는다.
// 사건 목록에서 체험을 시작할 때 사건이 없으면 목록에 머무르며 안내한다 (S-02).
const handledByPage = {
  startExperience: ['CASE_NOT_FOUND'],
};

/**
 * @param {object} api  목 또는 실제 api 객체
 * @param {{ navigate: Function, currentCaseId: () => number|undefined }} options
 */
export function withErrorRedirect(api, { navigate, currentCaseId }) {
  const wrapped = {};
  for (const [name, fn] of Object.entries(api)) {
    if (typeof fn !== 'function') continue;
    wrapped[name] = async (...args) => {
      try {
        return await fn(...args);
      } catch (error) {
        redirectIfNeeded(name, args, error, navigate, currentCaseId);
        throw error;
      }
    };
  }
  return wrapped;
}

function redirectIfNeeded(name, args, error, navigate, currentCaseId) {
  const code = error?.code;
  if (!code || handledByPage[name]?.includes(code)) return;
  // 사건 ID가 첫 인자인 API가 대부분이다 (getCases · 목 전용 함수는 제외)
  const caseId = typeof args[0] === 'number' ? args[0] : currentCaseId();

  // replace: 잘못된 주소를 기록에 남기지 않는다. 뒤로 가기를 눌렀을 때 같은 에러로 되돌아오지 않게 하기 위해서다.
  if (code === 'INVALID_STATE') {
    const screen = screenByStatus[error.currentStatus];
    if (screen) navigate(screen, { caseId }, { replace: true });
  } else if (code === 'EXPERIENCE_NOT_FOUND') {
    navigate('list', {}, { replace: true });
  } else if (code === 'CASE_NOT_FOUND') {
    navigate('notFound');
  }
}
