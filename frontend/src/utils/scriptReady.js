// 체험할 수 있는 사건인가 (FE-125 · FE-145). 서버 scriptStatus(API 1 · 16 · 18, BE-126 2차: READY · PREPARING · MAINTENANCE)가 있으면
// READY일 때만 체험 가능이다. 없으면 scriptReady(BE-116) boolean, 그것도 없을 때(목 · 구버전 응답 · 사건 요약 일부)만 프론트의 로컬 대본(scriptFor)으로 판정한다.
// FE-33이 로컬 대본을 걷으면 이 대체도 지운다. 이름은 BE-126 2차 PR에서 확정되면 여기 한 곳만 고친다
export const SCRIPT_STATUS = { READY: 'READY', PREPARING: 'PREPARING', MAINTENANCE: 'MAINTENANCE' };

export function scriptStatusOf(item, localScript) {
  if (Object.values(SCRIPT_STATUS).includes(item?.scriptStatus)) return item.scriptStatus;
  // 모르는 scriptStatus 값(서버가 나중에 추가한 막는 신호일 수 있다)은 열지 않고 PREPARING으로 막는다(PR #377 리뷰)
  if (item?.scriptStatus != null) return SCRIPT_STATUS.PREPARING;
  const ready = typeof item?.scriptReady === 'boolean' ? item.scriptReady : Boolean(localScript);
  return ready ? SCRIPT_STATUS.READY : SCRIPT_STATUS.PREPARING;
}

export const isScriptReady = (item, localScript) => scriptStatusOf(item, localScript) === SCRIPT_STATUS.READY;
