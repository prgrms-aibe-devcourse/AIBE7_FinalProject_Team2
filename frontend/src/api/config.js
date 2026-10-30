// API 설정. 환경변수는 빌드 시점에 주입된다 (frontend/.env.example 참고). node 테스트에는 import.meta.env가 없어 ?.로 읽는다

// API 기본 경로. 개발 서버는 /api 요청을 백엔드(8080)로 프록시하므로 기본값 그대로 쓴다.
export const API_BASE_URL = import.meta.env?.VITE_API_BASE_URL ?? '/api/v1';

// 화면이 쓰는 API 종류. mock(기본)은 HTTP 요청 없이 목 데이터로, real은 실제 백엔드로 요청한다.
// 개발 서버(DEV)에서는 내비 스위치가 남긴 값(localStorage)이 환경변수보다 앞선다. 배포 빌드는 DEV가 거짓이라 환경변수만 읽는다
const MODE_KEY = 'nlnb-api-mode';
function storedMode() {
  if (!import.meta.env?.DEV) return null;
  try { return localStorage.getItem(MODE_KEY); } catch { return null; }
}
export const API_MODE = (storedMode() ?? import.meta.env?.VITE_API_MODE) === 'real' ? 'real' : 'mock';
// 배포 빌드에서 목 빼기(FE-135): API_MODE는 실행 시점 값(localStorage)이 섞여 번들러가 접지 못한다. 그래서 목을 동적 import 하는 곳
// (api/index.js · pages/admin/adminApi.js · mocks/mockStageButtons.js)은 그 자리에서 import.meta.env?.VITE_API_MODE · DEV를 직접 읽어 빌드 시점에 분기를 접는다.
// 다른 모듈의 상수로 빼면 접히지 않아 목이 다시 번들에 들어간다(빌드 결과로 확인)

export function setApiMode(mode) {
  try { localStorage.setItem(MODE_KEY, mode); } catch { return; }
  window.location.reload();
}
