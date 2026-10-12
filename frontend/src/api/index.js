// 화면이 쓸 api 객체를 고른다 (VITE_API_MODE=mock | real, 기본 mock)
// 화면 코드는 어느 쪽인지 몰라도 되도록, 두 구현의 함수 이름 · 인자 · 응답 · 에러 형식을 같게 유지한다.
import { API_MODE } from './config.js';

export { API_MODE };

export async function loadApi() {
  if (API_MODE === 'real') {
    return import('./realApi.js');
  }
  // 목은 개발 중 화면 확인용이다 (src/mocks/README.md). 실제 모드에서는 목 파일을 불러오지 않는다.
  const modules = await Promise.all([
    import('../mocks/caseListMockApi.js'),
    import('../mocks/overviewMockApi.js'),
    import('../mocks/reviewMockApi.js'),
    import('../mocks/verdictMockApi.js'),
    import('../mocks/aiMockApi.js'),
    import('../mocks/courtMockApi.js'),
    import('../mocks/comparisonMockApi.js'),
  ]);
  return Object.assign({}, ...modules);
}
