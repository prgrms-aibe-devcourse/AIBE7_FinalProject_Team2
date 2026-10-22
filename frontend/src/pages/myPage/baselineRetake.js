// 성향카드 재심(다시 정하기, FE-77, 명세 P-2 · P-5 v0.50). 화면 이름: 초심(첫 테스트) · 재심 · 최근(최근 3건 평균). 성향카드 화면의 가운데 칸과 내 정보 수정이 같이 쓴다.
// 사용자당 1회. 처음 성향을 덮어쓰지 않고 "다시 정한 결과"를 하나 더 남긴다. 확인 창에서 "테스트 시작"을 누르면 성향 테스트로 간다(navigate('test', { baseline: true }))
import { button, element } from '../../components/siteLayout.js';

// 완료한 사건 수(도전과제 completed와 같은 셈). 다시 정하면 "체험 n건 뒤"로 남는다
const completedCount = (data) => new Set((data.experiences ?? []).filter((item) => item.status === 'COMPLETED').map((item) => item.caseId)).size;

// 지금 상태. needTest: 첫 테스트 전(화면은 첫 테스트로 잇는다), soon: 서버가 아직 baselineRetake를 주지 않음(실제 모드, 후속 BE 작업)
export function retakeState(data) {
  const retake = data.baselineRetake;
  if (!retake) return 'soon';
  if (retake.result) return 'done';
  return retake.available ? 'ready' : 'needTest';
}

export const RETAKE_NOTES = {
  ready: '한 번만 청구할 수 있어요.',
  done: '이미 재심을 받았어요.',
  soon: '서버 연동 후 열려요.',
};

// "체험 n건 뒤" 문구. v0.50 전 결과처럼 건수를 모르면 null
export function retakeAfter(result) {
  const count = result?.completedExperienceCount;
  return typeof count === 'number' ? `체험 ${count}건 뒤` : null;
}

// 확인 창. 바깥 · 취소 · Esc로 닫고, 닫히면 연 버튼으로 포커스를 돌려준다
export function openRetakeDialog({ data, navigate, from }) {
  const dialog = element('dialog', 'my-retake-dialog');
  const title = element('h2', 'my-retake-dialog-title', '재심 청구하기');
  title.id = 'my-retake-dialog-title';
  dialog.setAttribute('aria-labelledby', title.id);
  const start = button('테스트 시작하기', () => {
    dialog.close();
    navigate('test', { baseline: true });
  }, 'primary-button');
  const actions = element('div', 'my-retake-dialog-actions');
  actions.append(start, button('취소', () => dialog.close(), 'secondary-button'));
  dialog.append(
    title,
    element('p', '', '성향 테스트를 한 번 더 해서 성향카드를 다시 정해요. 초심 카드는 지우지 않고 그대로 남아요.'),
    element('p', 'my-retake-dialog-note', `재심은 한 번만 청구할 수 있어요. 지금까지 체험한 사건 ${completedCount(data)}건 뒤에 재심했다는 기록이 남아요.`),
    actions,
  );
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  dialog.addEventListener('close', () => {
    dialog.remove();
    if (from?.isConnected) from.focus();
  });
  (document.querySelector('.my-page') ?? document.body).append(dialog);
  dialog.showModal();
  start.focus();
}
