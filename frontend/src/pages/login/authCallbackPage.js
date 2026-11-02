import { button, element } from '../../components/siteLayout.js';
import { loginDesk } from './loginDesk.js';
import { saveUser } from '../../utils/session.js';
import { devJumpBar } from './loginDevJump.js';
import { offerAnonymousImport } from './anonymousImport.js';
import { askKakaoSignupConsent, offerTermsConsent } from './termsConsent.js';

// 카카오 로그인 콜백 화면 /auth/callback (FE-74, API 명세 10장 M-4). 서버가 카카오 로그인을 마치고 브라우저를 이리로 보낸다.
// - 성공: 리프레시 토큰 쿠키만 심겨 있으므로 M-1 재발급을 한 번 불러 첫 액세스 토큰을 받고, 로그인 표시를 남긴 뒤 메인으로 간다
// - 실패 · 취소: ?error=login_failed로 온다. 재발급이 실패해도 같은 안내를 보여 준다
// - 처음 온 카카오 계정(BE-145): ?signup=terms로 온다. 아직 회원이 아니라 토큰도 없다 → 재발급 대신 약관 동의 창을 띄워,
//   동의하면 M-12로 회원이 만들어지고 로그인된 뒤 가져오기 창으로 가고, 닫으면 M-13으로 가입 대기를 지우고 메인으로 간다
// - 이미 가입한 회원인데 약관 동의 기록이 없으면(M-8 termsRequired) 로그인 뒤 동의 창을 거친다. 순서는 동의 → 가져오기다
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
    { label: '카카오 첫 가입 동의', run: () => window.location.assign('/auth/callback?signup=terms') },
    { label: '로그인 화면', run: () => navigate('login') },
  ]);
  if (jump) root.append(jump);
  const params = new URLSearchParams(window.location.search);
  const devLoading = jump && params.get('dev') === 'loading';
  const signupTerms = !devLoading && params.get('signup') === 'terms';

  if (!devLoading && (params.has('error') || !(signupTerms ? api.signupKakao : api.reissueToken))) {
    showFailure();
    return;
  }

  // 알림 역할은 안내 한 줄에만 둔다: 카드 전체(버튼 포함)를 알림으로 두면 화면 낭독기가 버튼까지 읽는다 (FE-104)
  const waiting = element('p', 'login-lead', signupTerms ? '약관에 동의하면 가입이 완료돼요.' : '잠시만 기다려 주세요.');
  waiting.setAttribute('role', 'status');
  document.title = `${signupTerms ? '카카오 가입' : '로그인하는 중'} · ${siteTitle()}`; // 탭 제목도 상태를 알린다(라우터는 화면 이름 "로그인"으로 둔다)
  peek('cheek/idle-thinking');
  panel.append(element('h1', '', signupTerms ? '카카오 가입을 마무리해요' : '로그인하는 중…'), waiting);
  if (devLoading) return undefined;
  if (signupTerms) return finishKakaoSignup();
  return api.reissueToken().then(async () => {
    // 헤더에 보일 이름: 서버의 내 회원 정보(M-8)가 주는 표시 이름(닉네임, 없으면 '방청객 {회원 ID}'). 못 받으면 예전 임시 표시를 쓴다.
    const me = await Promise.resolve(api.getMe?.()).catch(() => null);
    if (!container.contains(root)) return; // 기다리는 사이 다른 화면으로 갔으면 이동하지 않는다
    saveUser({ nickname: me?.displayName || KAKAO_MEMBER_NICKNAME });
    // 동의 기록이 없는 회원은 동의가 먼저다(BE-145). 닫으면 로그아웃이라 가져오기 창은 띄우지 않고 메인으로 간다
    if (await offerTermsConsent(api, me) !== 'declined') {
      await offerAnonymousImport(api); // 이 브라우저의 체험 기록 · 첫인상 엽서가 있으면 가져올지 먼저 고르게 한다(FE-138)
    }
    if (!container.contains(root)) return;
    navigate('landing', {}, { replace: true });
  }, () => {
    if (container.contains(root)) showFailure();
  });

  // 처음 온 카카오 계정: 동의 창 → (동의) 가입 · 로그인 → 가져오기 창 → 메인
  async function finishKakaoSignup() {
    const result = await askKakaoSignupConsent(api);
    if (!container.contains(root)) return;
    if (result.status === 'expired') { // 가입 대기가 사라졌다(10분이 지났거나 이미 썼다). 카카오 로그인부터 다시 해야 한다
      showFailure('가입을 마치지 못했어요. 카카오 로그인을 처음부터 다시 해 주세요.');
      return;
    }
    if (result.status === 'agreed') {
      saveUser({ nickname: result.nickname || KAKAO_MEMBER_NICKNAME }); // 카카오 회원은 닉네임이 없어 서버가 '방청객 {회원 ID}'를 준다
      await offerAnonymousImport(api);
      if (!container.contains(root)) return;
    }
    navigate('landing', {}, { replace: true }); // 가입하지 않기로 했으면(declined) 그냥 메인으로 간다
  }

  function showFailure(message = '취소했거나 잠시 문제가 생겼어요. 로그인하지 않아도 모든 판결 체험을 할 수 있어요.') {
    const actions = element('div', 'login-actions');
    actions.append(
      button('다시 로그인', () => navigate('login', {}, { replace: true }), 'primary-button'),
      button('처음으로', () => navigate('landing', {}, { replace: true }), 'secondary-button'),
    );
    peek('cheek/idle-sorry');
    const reason = element('p', 'login-lead', message);
    reason.setAttribute('role', 'alert');
    document.title = `로그인 실패 · ${siteTitle()}`;
    panel.replaceChildren(element('h1', '', '카카오 로그인을 마치지 못했어요'), reason, actions);
  }
}

// 라우터가 붙인 "화면 이름 · 사이트 이름"에서 사이트 이름만 쓴다
const siteTitle = () => document.title.split(' · ').pop();
