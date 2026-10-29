// 첫 로그인 가져오기 안내 (FE-138, BE-140 결정). 로그인(가입 · 카카오 포함)이 끝난 직후, 이 브라우저(익명 ID)에 쌓인 체험 기록과 첫인상 엽서를
// 계정으로 가져올지 묻는다. 서버가 후보를 알려 주면(M-9) 확인 창을 띄우고, [가져오기](M-10) · [초기화하고 로그인](M-11) · 닫기(= 로그인하지 않기, 로그아웃) 중 하나를 고르게 한다.
// 자동으로 가져오지 않는 이유: 공용 PC에서 남이 한 체험을 내 계정으로 가져가는 일을 막으려고, 가져온다는 사실을 보여 주고 본인이 정하게 한다.
// 서버에 아직 없거나(404 · 204) 후보가 없거나 조회가 실패하면 창 없이 지나간다 — 로그인을 막지 않는다
import { button, element } from '../../components/siteLayout.js';
import { clearUser } from '../../utils/session.js';

const NOTE = '본인이 하지 않았다면 초기화하고 로그인하세요.';
const FAILURE = '처리하지 못했어요. 잠시 뒤 다시 눌러 주세요.';

/** 가져올 기록 문구: "체험 기록 3건 · 첫인상 엽서 2건". 0건인 것은 뺀다 */
export function importSummary(candidate) {
  const { experienceCount = 0, preJudgmentCount = 0 } = candidate ?? {};
  return [experienceCount > 0 && `체험 기록 ${experienceCount}건`, preJudgmentCount > 0 && `첫인상 엽서 ${preJudgmentCount}건`].filter(Boolean).join(' · ');
}

/**
 * 로그인 직후 부른다. 후보가 있으면 고를 때까지 기다린다.
 * @param {{ getAnonymousImport?: Function, importAnonymous?: Function, discardAnonymous?: Function, logout?: Function }} api
 * @param {(candidate: object, actions: { importIt: Function, discard: Function, decline: Function }) => Promise<string>} show 창을 띄우는 함수(시험에서 바꾼다)
 * @returns {Promise<'none' | 'imported' | 'discarded' | 'declined'>} declined면 로그인하지 않기로 해 로그아웃한 것이다 — 부른 화면은 이어서 이동하지 않는다
 */
export async function offerAnonymousImport(api, show = showImportDialog) {
  const candidate = await Promise.resolve(api.getAnonymousImport?.()).catch(() => null);
  if (!candidate || !importSummary(candidate)) return 'none';
  return show(candidate, {
    importIt: () => api.importAnonymous(),
    discard: () => api.discardAnonymous(),
    decline: async () => { // 서버 쪽 로그아웃(리프레시 쿠키 삭제)이 실패하면 로그인이 살아 있으므로 던져서 창이 안내하게 한다. 익명 기록은 그대로 둔다
      await api.logout?.();
      clearUser();
    },
  });
}

// 확인 창: 닫기(오른쪽 위 ×, Esc)는 로그인하지 않는 것이다. 처리하는 동안 버튼이 잠기고, 실패하면 안내하고 다시 고르게 한다
function showImportDialog(candidate, { importIt, discard, decline }) {
  return new Promise((resolve) => {
    const dialog = element('dialog', 'import-dialog');
    const title = element('h2', 'import-dialog-title', '이 브라우저의 기록을 가져올까요?');
    title.id = 'import-dialog-title';
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
        await action();
        settled = true;
        dialog.close();
        resolve(result);
      } catch {
        error.textContent = FAILURE;
        error.hidden = false;
        buttons.forEach((item) => item.removeAttribute('aria-disabled'));
        pending = false;
      }
    };
    const declining = choose(decline, 'declined');
    const importButton = button('가져오기', choose(importIt, 'imported'), 'primary-button');
    const discardButton = button('초기화하고 로그인', choose(discard, 'discarded'), 'secondary-button');
    const closeButton = button('×', declining, 'import-dialog-close');
    closeButton.setAttribute('aria-label', '닫기: 로그인하지 않고 돌아가기');
    closeButton.title = '로그인하지 않기';
    const buttons = [importButton, discardButton, closeButton];
    const actions = element('div', 'import-dialog-actions');
    actions.append(importButton, discardButton);
    dialog.append(
      closeButton,
      title,
      element('p', '', '이 브라우저의 체험 기록과 첫인상 엽서 기록을 이 계정으로 가져오게 됩니다.'),
      element('p', 'import-dialog-summary', importSummary(candidate)),
      element('p', 'import-dialog-note', NOTE),
      error,
      actions,
    );
    // Esc는 닫기와 같다. 브라우저가 직접 닫는 경우(취소를 막은 뒤 Esc를 한 번 더)에는 고르기 전이면 다시 연다
    dialog.addEventListener('cancel', (event) => { event.preventDefault(); declining(); });
    dialog.addEventListener('close', () => {
      if (settled) dialog.remove();
      else dialog.showModal();
    });
    document.body.append(dialog);
    dialog.showModal();
    importButton.focus();
  });
}
