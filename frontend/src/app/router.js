// History API 라우터 (프레임워크 없음)
//
// - 화면은 navigate(name, { caseId, ...나머지 }) 로 이동한다. caseId를 생략하면 지금 사건 번호를 쓴다.
//   (나머지 값은 화면 진입 함수에 그대로 넘어간다. 예: navigate('landing', { section: 'intro' }))
// - 같은 화면(주소 · 값이 같음)으로의 이동은 무시한다. 에러 이동과 화면 자체 이동이 겹쳐도 한 번만 그려진다.
// - 새로고침 · 주소 직접 입력 · 뒤로 가기 모두 같은 화면을 다시 그린다.
// - 오래된 이동의 결과는 버린다 (빠르게 연속 이동했을 때 마지막 이동만 그려진다).
import { element, renderSiteFooter } from '../components/siteLayout.js';
import { loadCaseHeader } from './caseHeader.js';

const SITE_TITLE = '내Law남불';

function toMatcher(path) {
  const pattern = path.replace(':caseId', '([0-9]+)');
  return new RegExp(`^${pattern}/?$`);
}

function buildPath(path, caseId) {
  return path.replace(':caseId', String(caseId));
}

function sameExtras(a, b) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  return [...keys].every((key) => a[key] === b[key]);
}

// 불러오는 중 · 오류 안내. 공통 스타일은 src/style.css의 .message-panel (화면 CSS를 빌리지 않는다, 리뷰 반영)
function messagePanel(text) {
  const panel = element('p', 'message-panel', text);
  panel.setAttribute('role', 'status');
  return panel;
}

/**
 * @param {{ view: HTMLElement, footerHost: HTMLElement, routes: Array }} options
 *   view        화면이 그려지는 영역 (화면 진입 함수의 container)
 *   footerHost  공통 푸터를 붙이는 영역 (화면이 자기 푸터를 그리지 않을 때만 사용)
 */
export function createRouter({ view, footerHost, routes }) {
  const byName = new Map(routes.map((route) => [route.name, route]));
  const matchers = routes.filter((route) => route.path).map((route) => ({ route, regex: toMatcher(route.path) }));
  const notFound = byName.get('notFound');

  let api = null;
  let current = { name: null, path: null, caseId: undefined, extras: {} };
  let token = 0;

  const needsCaseId = (route) => Boolean(route.path?.includes(':caseId'));

  function match(pathname) {
    for (const { route, regex } of matchers) {
      const found = regex.exec(pathname);
      if (!found) continue;
      const caseId = found[1] === undefined ? undefined : Number(found[1]);
      if (caseId !== undefined && !(Number.isSafeInteger(caseId) && caseId > 0)) return null;
      return { route, caseId };
    }
    return null;
  }

  async function show(route, caseId, extras, path, fromHistoryNav) {
    const mine = ++token;
    current = { name: route.name, path, caseId, extras, fromHistoryNav };
    document.title = route.name === 'landing' ? SITE_TITLE : `${route.title} · ${SITE_TITLE}`;

    let caseHeader;
    if (route.needsHeader) {
      view.replaceChildren(messagePanel('사건 정보를 불러오는 중이에요.'));
      footerHost.replaceChildren();
      caseHeader = await loadCaseHeader(api, caseId);
      if (mine !== token) return;
      if (!caseHeader) {
        await showNotFound();
        return;
      }
    }

    footerHost.replaceChildren(...(route.ownsFooter ? [] : [renderSiteFooter()]));
    try {
      // extras를 먼저 펼쳐야 한다 — navigate(name, { api: ... })처럼 같은 이름의 값이 와도
      // 라우터가 주입하는 api · navigate · caseId · caseHeader를 덮어쓰지 않는다 (리뷰 반영)
      await route.render(view, { ...extras, caseId, caseHeader, api, navigate });
    } catch (error) {
      if (mine !== token) return;
      console.error('화면을 그리지 못했어요:', error);
      view.replaceChildren(messagePanel('화면을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'));
    }
  }

  // 주소는 바꾸지 않고 404 · 오류 화면만 보여 준다 (새로고침하면 같은 주소가 다시 판정된다)
  async function showNotFound() {
    await show(notFound, undefined, {}, null, false);
  }

  /** 화면 이동. 반환값은 화면을 그리는 작업의 완료 시점이다 (기다리지 않아도 된다) */
  async function navigate(name, params = {}, { replace = false } = {}) {
    const route = byName.get(name);
    if (!route) {
      console.warn(`알 수 없는 화면 이름: ${name}`);
      return showNotFound();
    }
    if (!route.path) {
      return showNotFound();
    }
    const { caseId: given, ...extras } = params;
    const caseId = needsCaseId(route) ? (given ?? current.caseId) : undefined;
    if (needsCaseId(route) && caseId === undefined) {
      return showNotFound();
    }
    const path = buildPath(route.path, caseId);
    if (path === current.path && sameExtras(extras, current.extras)) {
      return undefined;
    }
    history[replace ? 'replaceState' : 'pushState']({ extras }, '', path);
    window.scrollTo(0, 0);
    return show(route, caseId, extras, path, false); // 코드로 호출한 이동은 "뒤로 가기"가 아니다
  }

  // 지금 주소에 맞는 화면을 그린다 (처음 접속 · 새로고침 · 뒤로 가기)
  // fromHistoryNav: 뒤로 · 앞으로 가기(popstate)로 시작됐으면 true. 최초 접속 · 새로고침은 false
  function renderLocation(fromHistoryNav) {
    const found = match(window.location.pathname);
    if (!found) {
      return showNotFound();
    }
    const raw = window.location.pathname.replace(/(.)\/$/, '$1');
    // /cases/01/review처럼 정규화되지 않은 사건 번호로 들어오면, navigate()가 만드는 경로(/cases/1/review)와
    // 달라져서 "같은 화면으로의 이동 무시"가 깨진다. 주소창도 정규화된 경로로 맞춰 둔다 (리뷰 반영)
    const path = found.caseId === undefined ? raw : buildPath(found.route.path, found.caseId);
    if (path !== raw) {
      history.replaceState(history.state, '', path);
    }
    return show(found.route, found.caseId, history.state?.extras ?? {}, path, fromHistoryNav);
  }

  return {
    navigate,
    /** 지금 보고 있는 사건 번호 (없으면 undefined) */
    currentCaseId: () => current.caseId,
    /** 화면이 바뀔 때마다 1씩 커지는 번호. 요청을 시작한 화면이 아직 보이는지 확인하는 데 쓴다 */
    navigationToken: () => token,
    /** 지금 보여 주는 중인 화면이 뒤로 · 앞으로 가기(popstate)로 시작됐는지. 코드로 부른 navigate()면 false */
    isFromHistoryNav: () => current.fromHistoryNav,
    /** 지금 화면을 같은 주소로 다시 그린다 (이동 무시 규칙을 건너뛴다) */
    reload: () => renderLocation(false),
    /** 라우터 시작. api는 화면에 넘길 api 객체 */
    start(appApi) {
      api = appApi;
      window.addEventListener('popstate', () => renderLocation(true));
      return renderLocation(false); // 최초 접속 · 새로고침은 "뒤로 가기"가 아니다
    },
  };
}
