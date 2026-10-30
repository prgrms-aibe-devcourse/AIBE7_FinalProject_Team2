// 관리자 화면이 쓸 api(목 또는 실제)를 고른다. 사용자 화면의 api(src/api/index.js)와 따로 둔다 —
// 사용자 api의 에러 이동(app/errorRedirect.js)이 관리자 에러(예: CASE_NOT_FOUND)로 사용자 화면을 열지 않게 하기 위해서다.
import { API_MODE } from '../../api/config.js';

let loaded;
export function loadAdminApi() {
  // 배포 빌드(VITE_API_MODE=real, DEV 아님)는 앞 조건이 빌드 시점에 true로 접혀 adminMockApi가 번들에서 빠진다(FE-135, 운영 점검에서 34KB 노출 확인).
  // import.meta.env를 이 자리에서 직접 읽어야 접힌다(api/config.js 설명)
  loaded ??= (import.meta.env?.VITE_API_MODE === 'real' && !import.meta.env?.DEV) || API_MODE === 'real'
    ? import('./adminRealApi.js')
    : import('./adminMockApi.js');
  return loaded;
}
