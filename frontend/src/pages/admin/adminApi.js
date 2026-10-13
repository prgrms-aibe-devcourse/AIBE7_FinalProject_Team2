// 관리자 화면이 쓸 api(목 또는 실제)를 고른다. 사용자 화면의 api(src/api/index.js)와 따로 둔다 —
// 사용자 api의 에러 이동(app/errorRedirect.js)이 관리자 에러(예: CASE_NOT_FOUND)로 사용자 화면을 열지 않게 하기 위해서다.
import { API_MODE } from '../../api/config.js';

let loaded;
export function loadAdminApi() {
  loaded ??= API_MODE === 'real' ? import('./adminRealApi.js') : import('./adminMockApi.js');
  return loaded;
}
