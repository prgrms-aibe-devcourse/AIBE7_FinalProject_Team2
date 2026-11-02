// 약관 동의 창 (BE-145, API 명세 M-12 ~ M-14, docs/decisions/BE-145.md). 두 경우에 같은 창을 쓴다.
// 1) 카카오 첫 가입: 카카오 콜백(M-4)이 처음 온 계정을 /auth/callback?signup=terms로 보낸다. 이 계정은 아직 회원이 아니다 —
//    [동의하고 시작]이면 M-12로 회원이 만들어지고 로그인되며, 닫으면 M-13으로 가입 대기를 지운다(DB에는 아무것도 남지 않는다).
// 2) 기존 회원: 로그인 뒤 내 회원 정보(M-8)의 termsRequired가 true면(동의 기록이 없는 카카오 회원 · BE-133 이전 아이디 회원) M-14로 동의를 받는다.
//    닫으면 로그아웃이다 — 첫 로그인 가져오기 창(anonymousImport.js)의 닫기와 같다.
// 순서는 동의 → 가져오기다: 가져오기는 "이 계정으로 기록을 옮기는" 일이라 동의가 먼저여야 앞뒤가 맞다.
// getMe 조회가 실패하면 동의 창 없이 지나간다 — 로그인을 막지 않는다(가져오기 창과 같다). 서버도 동의를 다른 API의 조건으로 걸지 않는다
import { button, element } from '../../components/siteLayout.js';
import { clearUser } from '../../utils/session.js';
import { TOKEN_NOT_ISSUED } from '../../api/authFlow.js';

const FAILURE = '처리하지 못했어요. 잠시 뒤 다시 눌러 주세요.';
const NOT_CHECKED = '이용약관과 개인정보처리방침에 동의해 주세요.';
/** 서버 오류 코드(M-12): 가입 대기가 없거나 10분이 지났거나 이미 썼다 */
export const SIGNUP_EXPIRED = 'KAKAO_SIGNUP_EXPIRED';

/**
 * 카카오 첫 가입 동의. 동의하면 회원이 만들어지고 로그인된다.
 * @param {{ signupKakao: Function, cancelKakaoSignup?: Function }} api
 * @param {Function} show 창을 띄우는 함수(시험에서 바꾼다)
 * @returns {Promise<{ status: 'agreed', nickname?: string } | { status: 'declined' } | { status: 'expired' } | { status: 'failed' } | { status: 'loggedIn' }>}
 *   declined면 가입하지 않기로 한 것이다. expired면 가입 대기가 사라져 카카오 로그인부터 다시 해야 한다.
 *   failed면 회원은 만들어졌지만(가입 대기는 이미 소비됨) 재발급만 실패해 로그인을 마치지 못한 것이다 — 다시 동의해도 KAKAO_SIGNUP_EXPIRED만 돌아온다.
 *   loggedIn은 expired였는데 이미 가입이 끝나 로그인 쿠키가 살아 있는 경우다(뒤로 가기 · 새로고침으로 동의 창을 다시 열었을 때) —
 *   "처음부터 다시"를 안내하기 전에 재발급을 한 번 시도해, 되면 로그인으로 이어 가게 한다(PR #385 리뷰)
 */
export async function askKakaoSignupConsent(api, show = showTermsDialog) {
  let nickname;
  const status = await show({ kind: 'signup' }, {
    agree: async () => {
      try {
        nickname = (await api.signupKakao({ agreed: true }))?.nickname;
      } catch (failure) {
        if (failure?.code === SIGNUP_EXPIRED) return 'expired'; // 창을 닫고 호출한 화면이 안내한다
        // 가입은 됐는데 재발급만 실패했다(authFlow.js TOKEN_NOT_ISSUED). 가입 대기는 이미 한 번 써서 다시 동의해도 소용없다
        if (failure?.code === TOKEN_NOT_ISSUED) return 'failed';
        throw failure; // 그 밖의 실패는 창이 안내하고 다시 고르게 한다
      }
      return undefined;
    },
    // 가입 대기는 10분 뒤에도 사라지므로 취소 요청이 실패해도 막지 않는다 — 로그인된 것이 없다
    decline: async () => { await Promise.resolve(api.cancelKakaoSignup?.()).catch(() => {}); },
  });
  if (status === 'expired' && await canReissue(api)) return { status: 'loggedIn' };
  return { status, nickname };
}

// 로그인 쿠키가 살아 있으면 재발급이 된다. 재발급 함수가 없으면(목 모드) 로그인된 것으로 보지 않는다
async function canReissue(api) {
  if (typeof api.reissueToken !== 'function') return false;
  return Promise.resolve(api.reissueToken()).then(() => true, () => false);
}

/**
 * 로그인 직후 부른다. 내 회원 정보(M-8)의 termsRequired가 true일 때만 창을 띄우고, 고를 때까지 기다린다.
 * @param {{ agreeTerms?: Function, logout?: Function }} api
 * @param {{ termsRequired?: boolean } | null | undefined} me M-8 응답. 못 받았으면 null
 * @param {Function} show 창을 띄우는 함수(시험에서 바꾼다)
 * @returns {Promise<'none' | 'agreed' | 'declined'>} declined면 로그인하지 않기로 해 로그아웃한 것이다 — 부른 화면은 이어서 가져오기 창을 띄우지 않는다
 */
export async function offerTermsConsent(api, me, show = showTermsDialog) {
  if (!me?.termsRequired || !api.agreeTerms) return 'none';
  return show({ kind: 'member' }, {
    agree: async () => { await api.agreeTerms({ agreed: true }); },
    // 서버 쪽 로그아웃(리프레시 쿠키 삭제)이 실패하면 로그인이 살아 있으므로 던져서 창이 안내하게 한다
    decline: async () => {
      await api.logout?.();
      clearUser();
    },
  });
}

const COPY = {
  signup: {
    title: '카카오로 시작하려면 약관에 동의해 주세요',
    lead: '처음 오셨네요. 이용약관과 개인정보처리방침에 동의하면 가입이 완료돼요. 동의하지 않으면 가입하지 않아요.',
    agree: '동의하고 시작',
    closeLabel: '닫기: 가입하지 않고 돌아가기',
    closeTitle: '가입하지 않기',
  },
  member: {
    title: '서비스를 계속 쓰려면 약관에 동의해 주세요',
    lead: '이 계정은 약관 동의 기록이 없어요. 이용약관과 개인정보처리방침에 동의해 주세요. 동의하지 않으면 로그아웃돼요.',
    agree: '동의하고 계속',
    closeLabel: '닫기: 동의하지 않고 로그아웃하기',
    closeTitle: '로그아웃하기',
  },
};

// 확인 창: 닫기(오른쪽 위 ×, Esc)는 동의하지 않는 것이다. 처리하는 동안 버튼이 잠기고, 실패하면 안내하고 다시 고르게 한다.
// 동의 동작이 'expired' 같은 문자열을 돌려주면 창을 닫고 그 값으로 끝낸다(가입 대기가 사라진 경우)
function showTermsDialog({ kind }, { agree, decline }) {
  const copy = COPY[kind];
  return new Promise((resolve) => {
    const dialog = element('dialog', 'import-dialog terms-dialog');
    const title = element('h2', 'import-dialog-title', copy.title);
    title.id = 'terms-dialog-title';
    dialog.setAttribute('aria-labelledby', title.id);
    const error = element('p', 'import-dialog-error');
    error.setAttribute('role', 'alert');
    error.hidden = true;
    let pending = false;
    let settled = false;
    const choose = (action, result) => async () => {
      if (pending) return;
      pending = true;
      error.hidden = true;
      buttons.forEach((item) => item.setAttribute('aria-disabled', 'true'));
      try {
        const outcome = await action();
        settled = true;
        dialog.close();
        resolve(outcome ?? result);
      } catch {
        error.textContent = FAILURE;
        error.hidden = false;
        buttons.forEach((item) => item.removeAttribute('aria-disabled'));
        pending = false;
      }
    };
    const declining = choose(decline, 'declined');
    const agreeing = choose(agree, 'agreed');

    // 필수 동의 한 줄: 체크박스 + 문구, 아래에 두 문서 링크(새 탭으로 열어 이 창이 닫히지 않게 한다) — 아이디 가입 화면(signupPage.js)과 같다
    const check = element('input');
    check.type = 'checkbox';
    check.id = 'terms-dialog-agree';
    const caption = element('label', '', '(필수) 이용약관과 개인정보처리방침에 동의해요');
    caption.htmlFor = check.id;
    const checkRow = element('div', 'terms-dialog-check');
    checkRow.append(check, caption);
    check.addEventListener('change', () => { error.hidden = true; });
    const links = element('p', 'terms-dialog-links');
    [['/terms', '이용약관 보기'], ['/privacy', '개인정보처리방침 보기']].forEach(([href, text]) => {
      const link = element('a', 'terms-dialog-link', text);
      link.href = href;
      link.target = '_blank';
      link.rel = 'noopener';
      links.append(link);
    });

    const agreeButton = button(copy.agree, () => {
      if (pending) return;
      if (!check.checked) { // 체크 없이는 보내지 않는다(서버도 agreed:true만 받는다)
        error.textContent = NOT_CHECKED;
        error.hidden = false;
        check.focus();
        return;
      }
      agreeing();
    }, 'primary-button');
    const closeButton = button('×', declining, 'import-dialog-close');
    closeButton.setAttribute('aria-label', copy.closeLabel);
    closeButton.title = copy.closeTitle;
    const buttons = [agreeButton, closeButton];
    const actions = element('div', 'import-dialog-actions');
    actions.append(agreeButton);
    dialog.append(closeButton, title, element('p', '', copy.lead), checkRow, links, error, actions);
    // Esc는 닫기와 같다. 브라우저가 직접 닫는 경우(취소를 막은 뒤 Esc를 한 번 더)에는 고르기 전이면 다시 연다
    dialog.addEventListener('cancel', (event) => { event.preventDefault(); declining(); });
    dialog.addEventListener('close', () => {
      if (settled) dialog.remove();
      else dialog.showModal();
    });
    document.body.append(dialog);
    dialog.showModal();
    // 첫 포커스는 버튼이 아니라 제목이다: 습관적인 Enter 한 번으로 동의가 실행되지 않게 하고, 낭독기가 내용부터 읽게 한다(PR #347 리뷰)
    title.tabIndex = -1;
    title.focus();
  });
}
