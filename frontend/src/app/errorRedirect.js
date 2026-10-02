// api 객체를 감싸서, 공통 에러가 오면 화면을 맞는 곳으로 보낸다 (API 명세 1-5 · 1-6)
//
//   INVALID_STATE        → 에러의 currentStatus에 맞는 화면 (screenByStatus 표)
//   EXPERIENCE_NOT_FOUND → 사건 목록(S-02) — 체험을 시작한 적이 없거나 쿠키가 없음
//   CASE_NOT_FOUND       → 404 · 오류 화면(S-14)
//
// 화면은 이미 에러 코드별 안내를 직접 처리하고 있다. 여기서는 이동만 하고 에러는 그대로 다시 던진다.
// 화면이 이동 뒤에 자기 안내를 그려도 이미 화면에서 떼어진 뒤라 보이지 않는다.
//
// 요청을 시작한 뒤 사용자가 다른 화면으로 이동했다면(요청 중에도 목록 이동 등이 가능하다) 그 요청의 에러로는 이동하지 않는다.
// 요청 시작 시점의 이동 번호(navigationToken)를 저장해 두고, 에러가 왔을 때 지금 번호와 같을 때만 이동한다.
// 같은 화면에서 동시에 보낸 요청이 여러 개 실패해도 처음 실패한 요청만 이동시키고 나머지는 건너뛴다.
//
// INVALID_STATE로 이동할 때는 replace를 써서 지금 기록 하나만 바꾼다. 하지만 그보다 앞선 기록들은
// (목록 → 사건정보 → 최종정리 → 판결입력 순으로 쌓인 것들) 그대로 남아 있어서, 뒤로 가기를 거듭 누르면
// 그 기록들을 하나씩 다시 지나가며 매번 같은 화면으로 또 보내진다. 실제로 "뒤로 가기"로 시작된 조회(get*)가
// 직전과 같은 화면 · 같은 사건으로 또 보내졌다면 "뒤로 가기를 거듭 누른 것"으로 보고, 기록을 한 걸음 더
// 건너뛴다. 앞으로 가기 · 코드로 부른 이동(navigate())이나 다른 사건으로의 이동은 여기 해당하지 않는다 —
// 앞으로 가기에서 건너뛰면 사용자가 앞으로 가기를 눌렀는데 오히려 뒤로 돌아가 버리고, 목록에서 서로 다른
// 두 사건을 연달아 열었는데 둘 다 같은 화면으로 리다이렉트돼도 그건 뒤로 가기가 아니다
// (리뷰 반영, isBackNav · caseId로 구분. isBackNav는 router.js가 기록 순번을 비교해서 판정한다).
// 제출(post*)은 사용자가 직접 누른 동작이라 건너뛰지 않는다 — 잘못 건드려 뒤로 보내면 더 혼란스럽다.
import { screenByStatus } from '../utils/screenByStatus.js';

// 이 API는 아래 에러를 화면이 직접 처리하므로 이동하지 않는다.
// 사건 목록에서 체험을 시작할 때 사건이 없으면 목록에 머무르며 안내한다 (S-02).
const handledByPage = {
  startExperience: ['CASE_NOT_FOUND'],
};

// 직전 INVALID_STATE 이동을 기억해 둔다. 뒤로 가기 연타 감지용. screen · caseId가 모두 같아야 "반복"으로 본다
const repeatWindow = { screen: null, caseId: null, at: 0 };
const REPEAT_MS = 4000;

/**
 * @param {object} api  목 또는 실제 api 객체
 * @param {{ navigate: Function, currentCaseId: () => number|undefined, navigationToken: () => number, isBackNav: () => boolean }} options
 */
export function withErrorRedirect(api, { navigate, currentCaseId, navigationToken, isBackNav }) {
  const wrapped = {};
  for (const [name, fn] of Object.entries(api)) {
    if (typeof fn !== 'function') continue;
    wrapped[name] = async (...args) => {
      const startedAt = navigationToken();
      try {
        return await fn(...args);
      } catch (error) {
        // 요청을 시작한 화면이 아직 보일 때만 이동한다 (이미 다른 화면이면 이전 요청의 에러가 새 화면을 덮어쓰지 않게)
        if (navigationToken() === startedAt) {
          redirectIfNeeded(name, args, error, navigate, currentCaseId, isBackNav);
        }
        throw error;
      }
    };
  }
  return wrapped;
}

function redirectIfNeeded(name, args, error, navigate, currentCaseId, isBackNav) {
  const code = error?.code;
  if (!code || handledByPage[name]?.includes(code)) return;
  // 사건 ID가 첫 인자인 API가 대부분이다 (getCases · 목 전용 함수는 제외)
  const caseId = typeof args[0] === 'number' ? args[0] : currentCaseId();
  // navigate() 호출 자체가 show()를 실행시켜 "지금 보여 주는 화면" 정보를 곧바로 덮어쓴다.
  // isBackNav()는 그 전에 미리 읽어 둬야, 뒤로 가기로 시작된 조회였는지를 정확히 알 수 있다.
  const cameFromBackNav = isBackNav();

  // replace: 잘못된 주소를 기록에 남기지 않는다. 뒤로 가기를 눌렀을 때 같은 에러로 되돌아오지 않게 하기 위해서다.
  if (code === 'INVALID_STATE') {
    const screen = screenByStatus[error.currentStatus];
    if (!screen) return;
    navigate(screen, { caseId }, { replace: true });
    // "뒤로 가기"로 시작된 조회에서만 반복을 추적한다. 앞으로 가기 · 코드로 부른 이동은 여기 해당하지 않는다.
    if (name.startsWith('get') && cameFromBackNav) {
      const now = Date.now();
      const isBackButtonRepeat = screen === repeatWindow.screen
        && caseId === repeatWindow.caseId
        && now - repeatWindow.at < REPEAT_MS;
      repeatWindow.screen = screen;
      repeatWindow.caseId = caseId;
      repeatWindow.at = now;
      if (isBackButtonRepeat) {
        history.go(-1); // 기록을 한 걸음 더 건너뛴다 (뒤로 가기 연타 대응)
      }
    }
  } else if (code === 'EXPERIENCE_NOT_FOUND') {
    navigate('list', {}, { replace: true });
  } else if (code === 'CASE_NOT_FOUND') {
    navigate('notFound');
  }
}
