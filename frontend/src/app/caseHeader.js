// 사건 상단 바(S-04 ~ S-09)에 쓰는 사건 제목 · 유형 (IA 6장 "사건 제목 · 유형 배지")
//
// 공급원 (정합성 점검 D-01, BE-79): S-04 이후 화면의 API에는 사건 제목이 없다. 그래서 사건 목록(API 1,
// 상태와 관계없이 열려 있음)에서 caseId로 찾는다. 목록 응답이 제목과 죄명(chargeName)을 모두 주므로
// 새로고침 · 주소 직접 입력에도 동작하고, 사건 수가 적은 MVP에서는 비용이 작다.
// 사건이 많아져 목록 전체를 받기 부담스러워지면 공통 사건 헤더 API로 옮긴다
// (docs/decisions/BE-79.md "추후 재검토"). 그때는 loadCaseHeader 한 곳만 바꾸면 된다.

const cache = new Map();

/** 사건 목록 응답(API 1)에서 사건 제목과 죄명을 기억해 둔다 */
export function rememberFromList(data) {
  for (const item of data?.cases ?? []) {
    // 목록에 죄명이 없으면(구버전 서버 등) 이미 알고 있는 값을 지킨다
    const chargeName = item.chargeName ?? cache.get(item.caseId)?.chargeName ?? '';
    cache.set(item.caseId, { caseId: item.caseId, title: item.title, chargeName });
  }
}

/** 사건 개요 응답(API 4)에서 사건 제목과 정확한 죄명을 기억해 둔다 */
export function rememberFromOverview(data) {
  const item = data?.case;
  if (!item) return;
  cache.set(item.caseId, { caseId: item.caseId, title: item.title, chargeName: item.chargeName });
}

/**
 * 사건 상단 바용 { caseId, title, chargeName }
 * 기억해 둔 값이 없으면 사건 목록(API 1)에서 찾는다. 목록에도 없으면 null (없는 사건)
 * 목록 조회 자체가 실패하면(서버 연결 문제 등) 제목 없이 화면은 보여 줄 수 있도록 대체값을 돌려준다.
 */
export async function loadCaseHeader(api, caseId) {
  if (!cache.has(caseId)) {
    try {
      rememberFromList(await api.getCases());
    } catch {
      return { caseId, title: `사건 ${caseId}`, chargeName: '' };
    }
  }
  const header = cache.get(caseId);
  return header ? { caseId: header.caseId, title: header.title, chargeName: header.chargeName } : null;
}
