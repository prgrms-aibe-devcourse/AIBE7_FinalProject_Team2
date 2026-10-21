// localStorage 속성 접근 자체가 예외를 던지는 환경(쿠키 · 사이트 데이터 차단)이 있다.
// 기본 인수는 함수 본문의 try보다 먼저 평가되므로, 기본 인수로 쓰는 저장소 접근을 이 한 곳에서 감싼다
export const localStore = () => {
  try { return globalThis.localStorage; } catch { return undefined; }
};
