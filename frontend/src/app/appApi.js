// 화면에 주입하는 api 객체를 만든다: 목 또는 실제 api에 "사건 제목 기억"과 "에러 시 화면 이동"을 입힌다.
import { rememberFromList, rememberFromOverview } from './caseHeader.js';
import { attendanceAfterCookie } from './attendanceAfterCookie.js';
import { withErrorRedirect } from './errorRedirect.js';

export function createAppApi(baseApi, { navigate, currentCaseId, navigationToken, isBackNav }) {
  const remembering = {
    ...baseApi,
    // 쿠키가 처음 만들어질 수 있는 요청 뒤에 출석을 한 번 더 부른다(첫 방문날 보정, BE-98 전까지 임시)
    ...attendanceAfterCookie(baseApi),
    // 목록 · 개요 응답이 지나갈 때 사건 제목을 기억해 둔다 (S-04 이후 상단 바에 쓴다)
    async getCases(...args) {
      const data = await baseApi.getCases(...args);
      rememberFromList(data);
      return data;
    },
    async getOverview(...args) {
      const data = await baseApi.getOverview(...args);
      rememberFromOverview(data);
      return data;
    },
  };
  return withErrorRedirect(remembering, { navigate, currentCaseId, navigationToken, isBackNav });
}
