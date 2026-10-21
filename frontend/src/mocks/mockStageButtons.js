import { ACT_JUMP_KEY } from '../components/stage.js';
import { API_MODE } from '../api/config.js';

// 무대의 "처음부터 다시"(목 데이터 전용): 목 저장소를 사건 시작 전으로 되돌리고 S-03을 새로 연다. 실제 API에서는 버튼을 달지 않는다
export function restartMock(caseId) {
  if (API_MODE !== 'mock') return undefined;
  return async () => {
    const { restart } = await import('./mockExperienceStore.js');
    restart();
    window.location.assign(`/cases/${caseId}/start`);
  };
}

// 막 버튼으로 바로 가기(목 데이터 전용, 테스트 기간): 목 상태를 그 막에 맞추고 막의 화면을 연다.
// 무대는 ACT_JUMP_KEY를 한 번 읽어 그 막의 첫 장면에서 시작한다. 실제 API에서는 막 버튼이 눌리지 않는다
const ACT_SCREENS = { 1: 'start', 2: 'start', 3: 'review', 4: 'summary', 5: 'verdict', 6: 'result/ai', 7: 'result/court', 8: 'result/compare' };
export function jumpActMock(caseId) {
  if (API_MODE !== 'mock') return undefined;
  return async (act) => {
    const { jumpToAct } = await import('./mockExperienceStore.js');
    jumpToAct(act);
    try { sessionStorage.setItem(ACT_JUMP_KEY, String(act)); } catch { /* 저장소를 못 쓰면 막의 처음 장면에서 시작한다 */ }
    window.location.assign(`/cases/${caseId}/${ACT_SCREENS[act]}`);
  };
}
