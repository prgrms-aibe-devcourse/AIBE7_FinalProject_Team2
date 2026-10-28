// History API 라우터 (프레임워크 없음)
//
// - 화면은 navigate(name, { caseId, ...나머지 }) 로 이동한다. caseId를 생략하면 지금 사건 번호를 쓴다.
//   (나머지 값은 화면 진입 함수에 그대로 넘어간다. 예: navigate('landing', { section: 'intro' }))
// - 같은 화면(주소 · 값이 같음)으로의 이동은 무시한다. 에러 이동과 화면 자체 이동이 겹쳐도 한 번만 그려진다.
// - 새로고침 · 주소 직접 입력 · 뒤로 가기 모두 같은 화면을 다시 그린다.
// - 오래된 이동의 결과는 버린다 (빠르게 연속 이동했을 때 마지막 이동만 그려진다).
import { element, renderSiteFooter, renderSiteHeader } from '../components/siteLayout.js';
import { loadCaseHeader } from './caseHeader.js';
import { legacyPath } from './legacyPaths.js';

const SITE_TITLE = '내Law남불';

// 주소 자리표시자: :caseId(사건 번호) · :shareId(성향 유형 공유 주소의 해시 값, S-16 공유 — 유형 코드를 주소에 드러내지 않는다) · :postId(커뮤니티 글 번호, FE-47)
function toMatcher(path) {
  const pattern = path.replace(':caseId', '(?<caseId>[0-9]+)').replace(':shareId', '(?<shareId>[A-Za-z0-9]{6,16})').replace(':postId', '(?<postId>[0-9]+)');
  return new RegExp(`^${pattern}/?$`);
}

function buildPath(path, caseId, shareId, postId) {
  return path.replace(':caseId', String(caseId)).replace(':shareId', String(shareId)).replace(':postId', String(postId)); // 해시는 대소문자를 구분한다(그대로 둔다)
}

const validPostId = (value) => Number.isSafeInteger(value) && value > 0;

// deskY(책상에서 누른 때의 스크롤 자리)는 연출용이라 같은 이동인지 가릴 때 뺀다 — 안 그러면 스크롤만 달리해 다시 눌렀을 때 같은 화면이 한 번 더 열린다
function sameExtras(a, b) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  keys.delete('deskY');
  return [...keys].every((key) => a[key] === b[key]);
}

// 불러오는 중 · 오류 안내. 공통 스타일은 src/style.css의 .message-panel (화면 CSS를 빌리지 않는다, 리뷰 반영)
function messagePanel(text) {
  const panel = element('p', 'message-panel', text);
  panel.setAttribute('role', 'status');
  return panel;
}

/**
 * @param {{ view: HTMLElement, headerHost?: HTMLElement, footerHost: HTMLElement, routes: Array }} options
 *   view        화면이 그려지는 영역 (화면 진입 함수의 container)
 *   headerHost  공통 헤더(상단 내비)를 붙이는 영역 (FE-56: 모든 화면이 같은 내비를 쓴다. ownsHeader 화면은 비운다)
 *   footerHost  공통 푸터를 붙이는 영역 (화면이 자기 푸터를 그리지 않을 때만 사용)
 */
export function createRouter({ view, headerHost, footerHost, routes }) {
  const byName = new Map(routes.map((route) => [route.name, route]));
  const matchers = routes.filter((route) => route.path).map((route) => ({ route, regex: toMatcher(route.path) }));
  const notFound = byName.get('notFound');

  let api = null;
  let current = { name: null, path: null, caseId: undefined, extras: {} };
  let token = 0;
  // 기록 항목마다 순번을 매겨 둔다. popstate가 오면 지금 알고 있던 순번과 비교해서 뒤로 · 앞으로 가기를
  // 구분한다 (브라우저는 popstate에 방향을 담아 주지 않는다, 리뷰 반영 — 앞으로 가기에서는 건너뛰지 않음).
  let historySeq = 0;
  let lastKnownSeq = 0;

  const needsCaseId = (route) => Boolean(route.path?.includes(':caseId'));

  function match(pathname) {
    for (const { route, regex } of matchers) {
      const found = regex.exec(pathname);
      if (!found) continue;
      const rawCaseId = found.groups?.caseId;
      const caseId = rawCaseId === undefined ? undefined : Number(rawCaseId);
      if (caseId !== undefined && !(Number.isSafeInteger(caseId) && caseId > 0)) return null;
      const rawPostId = found.groups?.postId;
      const postId = rawPostId === undefined ? undefined : Number(rawPostId);
      if (postId !== undefined && !validPostId(postId)) return null;
      return { route, caseId, shareId: found.groups?.shareId, postId };
    }
    return null;
  }

  async function show(route, caseId, extras, path, navKind) {
    const mine = ++token;
    current = { name: route.name, path, caseId, extras, navKind };
    document.title = route.name === 'landing' ? SITE_TITLE : `${route.title} · ${SITE_TITLE}`;
    // 화면마다 새로 그린다(지금 메뉴 표시 · 로그인 상태가 바뀌므로)
    // 색 테마는 화면이 그려지기를 기다리지 않고 이 순간 헤더 · body에 정한다(화면이 늦게 그려져도 헤더 · 바탕 · 푸터가 앞 화면 색으로 남지 않게, FE-66)
    const header = route.ownsHeader ? null : renderSiteHeader({ navigate });
    for (const node of [header, document.body]) {
      if (!node) continue;
      if (route.theme) node.dataset.theme = route.theme; else delete node.dataset.theme;
    }
    headerHost?.replaceChildren(...(header ? [header] : []));
    if (headerHost) headerHost.inert = false; // 이전 화면의 대화창(엽서 · 커뮤니티 글쓰기 창)이 막아 둔 헤더 · 푸터를 푼다(stage.js, communityParts.js)
    footerHost.inert = false;

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
    await show(notFound, undefined, {}, null, 'navigate');
  }

  /** 화면 이동. 반환값은 화면을 그리는 작업의 완료 시점이다 (기다리지 않아도 된다)
   *  keepView: 화면은 다시 그리지 않고 주소 · 탭 제목만 바꾼다(판결 체험 4 → 5 → 6막처럼 한 무대에서 이어 갈 때).
   *  keepScroll: 새 화면을 그리되 스크롤을 맨 위로 올리지 않는다(무대 자리가 같은 화면끼리, 예: 판결 서명 → AI 판결).
   *  show()를 거치지 않으므로 이전 화면의 정리(cleanup)가 돌지 않는다 — 화면별 정리 로직이 생기면 여기서 빠지지 않게 챙길 것 */
  async function navigate(name, params = {}, { replace = false, keepView = false, keepScroll = false } = {}) {
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
    if (route.path.includes(':shareId') && !/^[A-Za-z0-9]{6,16}$/.test(String(extras.shareId))) {
      return showNotFound();
    }
    if (route.path.includes(':postId') && !validPostId(extras.postId)) {
      return showNotFound();
    }
    const path = buildPath(route.path, caseId, extras.shareId, extras.postId);
    if (path === current.path && sameExtras(extras, current.extras)) {
      return undefined;
    }
    // replace는 지금 기록 항목을 바꾸는 것이라 순번을 유지하고, push는 새 항목이라 순번을 새로 매긴다
    const seq = replace ? (history.state?.seq ?? ++historySeq) : ++historySeq;
    history[replace ? 'replaceState' : 'pushState']({ extras, seq }, '', path);
    lastKnownSeq = seq; // 코드로 만든 이동이니 바로 갱신해 둔다 (다음 popstate 비교 기준)
    if (keepView) {
      // 지금 화면이 그대로 이어지므로 token은 올리지 않는다(진행 중인 요청을 오래된 것으로 버리지 않게)
      current = { ...current, name: route.name, path, caseId, extras };
      document.title = `${route.title} · ${SITE_TITLE}`;
      return undefined;
    }
    // deskY: 메인 책상에서 물건을 눌러 왔다. 새 화면이 뒤에 책상을 남기고 직접 맨 위로 올리므로, 그 전에 책상이 위로 튀지 않게 여기서는 올리지 않는다(못 올렸으면 다 그린 뒤 올린다)
    const holdScroll = extras.deskY > 0;
    if (!keepScroll && !holdScroll) window.scrollTo(0, 0);
    const y = window.scrollY;
    const drawing = show(route, caseId, extras, path, 'navigate'); // 코드로 호출한 이동은 뒤로 · 앞으로 가기가 아니다
    // keepScroll: 새 화면은 처음에 "불러오는 중"만 있어 문서가 짧아지며 스크롤이 0으로 깎인다 — 다 그린 뒤 원래 자리로 돌린다
    const mine = token; // show()가 token을 올린 직후 값 — 그려지는 사이 다른 화면으로 갔다면 복원하지 않는다
    if (keepScroll) drawing?.then?.(() => { if (token === mine) window.scrollTo(0, y); });
    else if (holdScroll) drawing?.then?.(() => { if (token === mine) window.scrollTo(0, 0); });
    return drawing;
  }

  // 지금 주소에 맞는 화면을 그린다 (처음 접속 · 새로고침 · 뒤로 · 앞으로 가기)
  // navKind: 'back' | 'forward' | 'navigate' (코드로 부른 이동, 최초 접속 · 새로고침 포함)
  function renderLocation(navKind) {
    const moved = legacyPath(window.location.pathname);
    // 옛 주소(공유 링크 · 북마크)는 새 주소로 바꿔 그린다. 쿼리 · 해시는 그대로 둔다
    if (moved) history.replaceState(history.state, '', moved + window.location.search + window.location.hash);
    const found = match(window.location.pathname);
    if (!found) {
      return showNotFound();
    }
    const raw = window.location.pathname.replace(/(.)\/$/, '$1');
    // /cases/01/trial처럼 정규화되지 않은 사건 번호로 들어오면, navigate()가 만드는 경로(/cases/1/trial)와
    // 달라져서 "같은 화면으로의 이동 무시"가 깨진다. 주소창도 정규화된 경로로 맞춰 둔다 (리뷰 반영)
    const path = found.caseId === undefined && found.shareId === undefined && found.postId === undefined ? raw : buildPath(found.route.path, found.caseId, found.shareId, found.postId);
    if (path !== raw) {
      history.replaceState(history.state, '', path); // state(순번 포함)는 그대로 유지한다
    }
    // 주소의 :shareId · :postId는 화면에 extras.shareId · extras.postId로 넘긴다 (직접 입력 · 새로고침해도 같은 값)
    const extras = { ...(history.state?.extras ?? {}), ...(found.shareId === undefined ? {} : { shareId: found.shareId }), ...(found.postId === undefined ? {} : { postId: found.postId }) };
    return show(found.route, found.caseId, extras, path, navKind);
  }

  return {
    navigate,
    /** 지금 보고 있는 사건 번호 (없으면 undefined) */
    currentCaseId: () => current.caseId,
    /** 화면이 바뀔 때마다 1씩 커지는 번호. 요청을 시작한 화면이 아직 보이는지 확인하는 데 쓴다 */
    navigationToken: () => token,
    /** 지금 보여 주는 중인 화면이 "뒤로 가기"로 시작됐는지. 앞으로 가기 · 코드로 부른 이동은 false (리뷰 반영) */
    isBackNav: () => current.navKind === 'back',
    /** 지금 화면을 같은 주소로 다시 그린다 (이동 무시 규칙을 건너뛴다) */
    reload: () => renderLocation('navigate'),
    /** 라우터 시작. api는 화면에 넘길 api 객체 */
    start(appApi) {
      api = appApi;
      // 최초 진입 기록에도 순번을 매겨 둬야 이후 popstate에서 방향을 비교할 수 있다
      const seq = history.state?.seq ?? ++historySeq;
      if (history.state?.seq !== seq) {
        // 쿼리 · 해시는 남긴다 (/auth/callback?error=login_failed를 화면이 읽어야 한다, FE-74)
        history.replaceState({ ...(history.state ?? {}), seq }, '', window.location.pathname + window.location.search + window.location.hash);
      }
      lastKnownSeq = seq;
      firstSeq = seq;
      historySeq = Math.max(historySeq, seq); // 새로고침해도 다음 push 순번이 지금 기록보다 커지게
      window.addEventListener('popstate', () => {
        const seq2 = history.state?.seq;
        const direction = seq2 === undefined ? 'navigate' : seq2 < lastKnownSeq ? 'back' : seq2 > lastKnownSeq ? 'forward' : 'navigate';
        lastKnownSeq = seq2 ?? lastKnownSeq;
        renderLocation(direction);
      });
      return renderLocation('navigate'); // 최초 접속 · 새로고침은 뒤로 가기가 아니다
    },
  };
}

// 이 탭에서 라우터가 처음 그린 기록의 순번. 그보다 큰 순번이면 앱 안에서 뒤로 갈 기록이 있다
let firstSeq = null;

/** 앱 안에서 뒤로 갈 기록이 있는지(첫 진입 화면이면 false). history.length는 앱에 오기 전 다른 사이트 기록까지 세서 쓰지 않는다 (FE-55 리뷰 반영) */
export function canGoBackInApp() {
  return firstSeq !== null && (history.state?.seq ?? firstSeq) > firstSeq;
}
