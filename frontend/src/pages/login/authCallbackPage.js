import { button, element } from '../../components/siteLayout.js';
import { loginDesk } from './loginDesk.js';
import { saveUser } from '../../utils/session.js';
import { devJumpBar } from './loginDevJump.js';

// 카카오 로그인 콜백 화면 /auth/callback (FE-74, API 명세 10장 M-4). 서버가 카카오 로그인을 마치고 브라우저를 이리로 보낸다.
// - 성공: 리프레시 토큰 쿠키만 심겨 있으므로 M-1 재발급을 한 번 불러 첫 액세스 토큰을 받고, 로그인 표시를 남긴 뒤 메인으로 간다
// - 실패 · 취소: ?error=login_failed로 온다. 재발급이 실패해도 같은 안내를 보여 준다
// 회원 정보에 개인정보를 두지 않아 카카오 회원은 닉네임이 없다 → 서버의 표시 이름(M-8 displayName: 닉네임을 정했으면 그 값, 아니면 '방청객 {회원 ID}')을 쓰고,
// 받지 못하면 고정 문구로 표시한다(PR #229 합의, BE-139 · FE-137)
// 모양은 로그인 화면과 같은 책상 위 펠트 패드(loginDesk.js, FE-69)다
export const KAKAO_MEMBER_NICKNAME = '카카오 회원';

export function renderAuthCallbackPage(container, { api, navigate }) {
  const { root, card: panel, peek } = loginDesk(container, 'div');

  // 개발용: 콜백 화면 상태로 바로 넘어간다(목 모드만). ?dev=loading은 재발급을 부르지 않고 로딩 화면에 머문다
  const jump = devJumpBar([
    { label: '콜백 로딩', run: () => window.location.assign('/auth/callback?dev=loading') },
    { label: '콜백 실패', run: () => window.location.assign('/auth/callback?error=login_failed') },
    { label: '로그인 화면', run: () => navigate('login') },
  ]);
  if (jump) root.append(jump);
  const devLoading = jump && new URLSearchParams(window.location.search).get('dev') === 'loading';

  if (!devLoading && (new URLSearchParams(window.location.search).has('error') || !api.reissueToken)) {
    showFailure();
    return;
  }

  // 알림 역할은 안내 한 줄에만 둔다: 카드 전체(버튼 포함)를 알림으로 두면 화면 낭독기가 버튼까지 읽는다 (FE-104)
  const waiting = element('p', 'login-lead', '잠시만 기다려 주세요.');
  waiting.setAttribute('role', 'status');
  document.title = `로그인하는 중 · ${siteTitle()}`; // 탭 제목도 상태를 알린다(라우터는 화면 이름 "로그인"으로 둔다)
  peek('cheek/idle-thinking');
  panel.append(element('h1', '', '로그인하는 중…'), waiting);
  if (devLoading) return undefined;
  return api.reissueToken().then(async () => {
    // 헤더에 보일 이름: 서버의 내 회원 정보(M-8)가 주는 표시 이름(닉네임, 없으면 '방청객 {회원 ID}'). 못 받으면 예전 임시 표시를 쓴다.
    const me = await Promise.resolve(api.getMe?.()).catch(() => null);
    if (!container.contains(root)) return; // 기다리는 사이 다른 화면으로 갔으면 이동하지 않는다
    saveUser({ nickname: me?.displayName || KAKAO_MEMBER_NICKNAME });
    navigate('landing', {}, { replace: true });
  }, () => {
    if (container.contains(root)) showFailure();
  });

  function showFailure() {
    const actions = element('div', 'login-actions');
    actions.append(
      button('다시 로그인', () => navigate('login', {}, { replace: true }), 'primary-button'),
      button('처음으로', () => navigate('landing', {}, { replace: true }), 'secondary-button'),
    );
    peek('cheek/idle-sorry');
    const reason = element('p', 'login-lead', '취소했거나 잠시 문제가 생겼어요. 로그인하지 않아도 모든 판결 체험을 할 수 있어요.');
    reason.setAttribute('role', 'alert');
    document.title = `로그인 실패 · ${siteTitle()}`;
    panel.replaceChildren(element('h1', '', '카카오 로그인을 마치지 못했어요'), reason, actions);
  }
}

// 라우터가 붙인 "화면 이름 · 사이트 이름"에서 사이트 이름만 쓴다
const siteTitle = () => document.title.split(' · ').pop();
