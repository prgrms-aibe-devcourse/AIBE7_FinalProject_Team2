// 첫 방문날 출석 보정 (FE-71, 임시). 출석 API(P-6)는 익명 ID 쿠키가 있을 때만 기록한다(BE-97). 처음 온 사용자는 앱 시작 때 부른 출석이 지나간 뒤에
// 체험 시작(API 2) · 성향 테스트 제출(P-2) · 꾸미기 저장(P-7)으로 쿠키가 생기므로, 그 요청이 처음 성공한 뒤 출석을 한 번 더 부른다.
// 쿠키가 HttpOnly라 방금 발급됐는지 알 수 없어서 "이번 방문의 첫 성공" 뒤에 한 번 부른다(서버는 같은 날 여러 번 불러도 하루로 센다).
// shortcut: 서버가 익명 ID를 발급할 때 출석도 함께 기록하면(BE-98) 이 파일과 createAppApi의 연결을 지운다.
const ISSUING = ['startExperience', 'submitPersonality', 'putProfileDeco'];

/** 쿠키를 만들 수 있는 요청이 처음 성공하면 baseApi.postAttendance()를 한 번 더 부르는 래퍼들. 출석 실패는 조용히 넘긴다 */
export function attendanceAfterCookie(baseApi) {
  let attended = false;
  const wrapped = {};
  for (const name of ISSUING) {
    if (typeof baseApi[name] !== 'function') continue;
    wrapped[name] = async (...args) => {
      const result = await baseApi[name](...args);
      if (!attended && typeof baseApi.postAttendance === 'function') {
        attended = true;
        Promise.resolve(baseApi.postAttendance()).catch(() => {});
      }
      return result;
    };
  }
  return wrapped;
}
