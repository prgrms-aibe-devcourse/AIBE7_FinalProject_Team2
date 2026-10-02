// 사건 상단 바(S-04 ~ S-09)에 쓰는 사건 제목 · 유형 (IA 6장 "사건 제목 · 유형 배지")
//
// 임시 공급 방식 (정합성 점검 D-01 결정 전):
//   S-04 이후 화면의 API에는 사건 제목이 없다. 그래서 사건 목록(API 1, 상태와 관계없이 열려 있음)에서
//   caseId로 찾는다. 새로고침 · 주소 직접 입력에도 동작하고, 사건 수가 적은 MVP에서는 비용이 작다.
//   D-01에서 API 응답에 제목이 추가되면 loadCaseHeader 한 곳만 바꾸면 된다.
//
// 목록 응답에는 죄명(chargeName)이 없어 범죄 유형으로 대신한다. 사건 개요(API 4)를 보면 정확한 죄명으로 갱신된다.

const crimeTypeLabel = { MURDER: '살인', FRAUD: '사기', INJURY: '상해' };

const cache = new Map();

/** 사건 목록 응답(API 1)에서 사건 제목을 기억해 둔다 */
export function rememberFromList(data) {
  for (const item of data?.cases ?? []) {
    if (cache.get(item.caseId)?.exact) continue; // 정확한 죄명이 있으면 덮어쓰지 않는다
    cache.set(item.caseId, {
      caseId: item.caseId,
      title: item.title,
      // 서버가 준 분류명(crimeCategoryLabel, 예: "재산범죄")을 우선한다. crimeType으로 만든 라벨("사기" 등)은
      // 실제 죄명과 다를 수 있다 (예: 횡령 성격 사건도 crimeType=FRAUD일 수 있음) (리뷰 반영)
      chargeName: item.crimeCategoryLabel ?? crimeTypeLabel[item.crimeType] ?? '',
      exact: false,
    });
  }
}

/** 사건 개요 응답(API 4)에서 사건 제목과 정확한 죄명을 기억해 둔다 */
export function rememberFromOverview(data) {
  const item = data?.case;
  if (!item) return;
  cache.set(item.caseId, { caseId: item.caseId, title: item.title, chargeName: item.chargeName, exact: true });
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
