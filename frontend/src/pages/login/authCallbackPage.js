import { button, element } from '../../components/siteLayout.js';
import { saveUser } from '../../utils/session.js';

// 카카오 로그인 콜백 화면 /auth/callback (FE-74, API 명세 10장 M-4). 서버가 카카오 로그인을 마치고 브라우저를 이리로 보낸다.
// - 성공: 리프레시 토큰 쿠키만 심겨 있으므로 M-1 재발급을 한 번 불러 첫 액세스 토큰을 받고, 로그인 표시를 남긴 뒤 메인으로 간다
// - 실패 · 취소: ?error=login_failed로 온다. 재발급이 실패해도 같은 안내를 보여 준다
// 회원 정보에 개인정보를 두지 않아 서버 닉네임이 없다 → 고정 문구로 표시한다(PR #229 합의)
// 모양은 404 화면과 같은 안내 패널(style.css .not-found-*)을 빌린다
export const KAKAO_MEMBER_NICKNAME = '카카오 회원';

export function renderAuthCallbackPage(container, { api, navigate }) {
  const root = element('div', 'not-found-page');
  const panel = element('div', 'message-panel not-found-panel');
  root.append(panel);
  container.replaceChildren(root);

  if (new URLSearchParams(window.location.search).has('error') || !api.reissueToken) {
    showFailure();
    return;
  }

  panel.setAttribute('role', 'status');
  panel.append(element('h1', '', '로그인하는 중…'), element('p', '', '잠시만 기다려 주세요.'));
  return api.reissueToken().then(() => {
    if (!container.contains(root)) return; // 기다리는 사이 다른 화면으로 갔으면 이동하지 않는다
    saveUser({ nickname: KAKAO_MEMBER_NICKNAME });
    navigate('landing', {}, { replace: true });
  }, () => {
    if (container.contains(root)) showFailure();
  });

  function showFailure() {
    const actions = element('div', 'not-found-actions');
    actions.append(
      button('다시 로그인', () => navigate('login', {}, { replace: true }), 'primary-button'),
      button('처음으로', () => navigate('landing', {}, { replace: true }), 'secondary-button'),
    );
    panel.setAttribute('role', 'alert');
    panel.replaceChildren(
      element('h1', '', '카카오 로그인을 마치지 못했어요'),
      element('p', '', '취소했거나 잠시 문제가 생겼어요. 로그인하지 않아도 모든 판결 체험을 할 수 있어요.'),
      actions,
    );
  }
}
