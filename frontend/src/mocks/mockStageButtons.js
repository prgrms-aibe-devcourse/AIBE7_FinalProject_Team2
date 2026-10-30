import { ACT_JUMP_KEY } from '../components/stage.js';
import { API_MODE } from '../api/config.js';

// 배포 빌드(VITE_API_MODE=real, DEV 아님)는 아래 두 함수의 첫 조건이 빌드 시점에 true로 접혀 목 저장소 import가 번들에서 빠진다(FE-135, api/config.js 설명)

// 무대의 "처음부터 다시"(목 데이터 전용): 목 저장소를 사건 시작 전으로 되돌리고 S-03을 새로 연다. 실제 API에서는 버튼을 달지 않는다
export function restartMock(caseId) {
  if ((import.meta.env?.VITE_API_MODE === 'real' && !import.meta.env?.DEV) || API_MODE !== 'mock') return undefined;
  return async () => {
    const { restart } = await import('./mockExperienceStore.js');
    restart();
    window.location.assign(`/cases/${caseId}/impression`);
  };
}

// 막 버튼으로 바로 가기(목 데이터 전용, 테스트 기간): 목 상태를 그 막에 맞추고 막의 화면을 연다.
// 무대는 ACT_JUMP_KEY를 한 번 읽어 그 막의 첫 장면에서 시작한다. 실제 API에서는 막 버튼이 눌리지 않는다
// 대본 키(ACT_GROUPS) → 화면 주소
const ACT_SCREENS = { '1a': 'impression', '1b': 'impression', 2: 'trial', 3: 'verdict', 4: 'ai-verdict', 5: 'court-verdict', 6: 'compare' };
export function jumpActMock(caseId) {
  if ((import.meta.env?.VITE_API_MODE === 'real' && !import.meta.env?.DEV) || API_MODE !== 'mock') return undefined;
  return async (act) => {
    const { jumpToAct } = await import('./mockExperienceStore.js');
    jumpToAct(act);
    try { sessionStorage.setItem(ACT_JUMP_KEY, String(act)); } catch { /* 저장소를 못 쓰면 막의 처음 장면에서 시작한다 */ }
    window.location.assign(`/cases/${caseId}/${ACT_SCREENS[act]}`);
  };
}
