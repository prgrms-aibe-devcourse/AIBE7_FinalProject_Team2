// 내Law남불 앱 시작점: 라우터를 만들고 api(목 또는 실제)를 연결한다. 사용법은 src/app/README.md, 연동 확인은 sample.html 참고
import './app/styles.js';
import { createAppApi } from './app/appApi.js';
import { routes } from './app/routes.js';
import { createRouter } from './app/router.js';
import { loadApi } from './api/index.js';
import { applyMotion } from './utils/motion.js';

applyMotion(); // 연출 스위치 설정을 첫 화면 전에 <html data-motion>에 반영한다

const app = document.querySelector('#app');
const headerHost = document.createElement('div');
headerHost.id = 'route-header';
const view = document.createElement('div');
view.id = 'route-view';
const footerHost = document.createElement('div');
footerHost.id = 'route-footer';
app.replaceChildren(headerHost, view, footerHost);
// 공통 헤더 높이를 --site-header-h로 내보낸다(FE-56). 화면 전체를 덮는 고정 무대(6막 등)가 헤더 아래에서 시작하도록 쓴다.
// 좁은 화면에서 헤더가 두 줄이 되거나 헤더가 없는 화면(관리자)이면 값이 따라 바뀐다.
// 헤더 요소 자체를 잰다(성향검사처럼 헤더를 fixed로 얹으면 #route-header 높이는 0이다)
const headerSize = new ResizeObserver(() => {
  document.documentElement.style.setProperty('--site-header-h', `${headerHost.firstElementChild?.offsetHeight ?? 0}px`);
});
new MutationObserver(() => {
  headerSize.disconnect();
  headerSize.observe(headerHost.firstElementChild ?? headerHost); // 화면마다 헤더를 새로 그리므로 새 요소를 잰다
}).observe(headerHost, { childList: true });

try {
  const router = createRouter({ view, headerHost, footerHost, routes });
  const api = createAppApi(await loadApi(), {
    navigate: router.navigate,
    currentCaseId: router.currentCaseId,
    navigationToken: router.navigationToken,
    isBackNav: router.isBackNav,
  });
  await router.start(api);
} catch (error) {
  // 시작 단계의 예외(스크립트 불러오기 실패 등)로 빈 화면이 되지 않도록 안내한다
  console.error('앱을 시작하지 못했어요:', error);
  const message = document.createElement('p');
  message.setAttribute('role', 'alert');
  message.textContent = '앱을 시작하지 못했어요. 새로고침해 주세요.';
  view.replaceChildren(message);
}
