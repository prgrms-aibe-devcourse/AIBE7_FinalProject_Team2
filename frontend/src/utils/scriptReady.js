// 체험할 수 있는 사건인가 (FE-125). 서버가 준 scriptReady(API 1 · 16, BE-116)가 있으면 그 값이 기준이고,
// 없을 때(목 · 구버전 응답 · 사건 요약 일부)만 프론트의 로컬 대본(scriptFor)으로 판정한다. FE-33이 로컬 대본을 걷으면 이 대체도 지운다
export function isScriptReady(item, localScript) {
  return typeof item?.scriptReady === 'boolean' ? item.scriptReady : Boolean(localScript);
}
