// 내Law남불 앱 시작점: 라우터를 만들고 api(목 또는 실제)를 연결한다. 사용법은 src/app/README.md, 연동 확인은 sample.html 참고
import './app/styles.js';
import { createAppApi } from './app/appApi.js';
import { routes } from './app/routes.js';
import { createRouter } from './app/router.js';
import { loadApi } from './api/index.js';

const app = document.querySelector('#app');
const view = document.createElement('div');
view.id = 'route-view';
const footerHost = document.createElement('div');
footerHost.id = 'route-footer';
app.replaceChildren(view, footerHost);

try {
  const router = createRouter({ view, footerHost, routes });
  const api = createAppApi(await loadApi(), {
    navigate: router.navigate,
    currentCaseId: router.currentCaseId,
    navigationToken: router.navigationToken,
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
