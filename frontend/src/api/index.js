// 화면이 쓸 api 객체를 고른다 (VITE_API_MODE=mock | real, 기본 mock)
// 화면 코드는 어느 쪽인지 몰라도 되도록, 두 구현의 함수 이름 · 인자 · 응답 · 에러 형식을 같게 유지한다.
import { API_MODE } from './config.js';

export { API_MODE };

export async function loadApi() {
  // 배포 빌드(VITE_API_MODE=real, DEV 아님)는 앞 조건이 빌드 시점에 true로 접혀 아래 목 import가 번들에서 빠진다(FE-135).
  // import.meta.env를 이 자리에서 직접 읽어야 접힌다(config.js 설명)
  if ((import.meta.env?.VITE_API_MODE === 'real' && !import.meta.env?.DEV) || API_MODE === 'real') {
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
    import('../mocks/personalityMockApi.js'),
    import('../mocks/profileDecoMockApi.js'), // 꾸미기 저장 · 출석 기록(P-6 · P-7)
    import('../mocks/levelMockApi.js'), // 레벨 · 직업(FE-114, BE-106)
    import('../mocks/caseReviewMockApi.js'), // 사건 리뷰(FE-55). 실제 모드에는 아직 없다
    import('../mocks/communityMockApi.js'), // 커뮤니티(FE-47). 실제 모드에는 아직 없다
    import('../mocks/authMockApi.js'), // 개발용 목업 로그인(FE-52). 실제 모드의 login · logout은 카카오 로그인(FE-74)
  ]);
  return Object.assign({}, ...modules);
}
