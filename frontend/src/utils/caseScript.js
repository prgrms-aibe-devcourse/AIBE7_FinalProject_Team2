import { assembleScript } from '../content/assembleScript.js';

// 받은 대본은 새로고침 전까지 사건별로 기억한다: 막마다 화면이 바뀌어도 다시 받지 않고, 체험 중에 서버 대본이 바뀌어도 받아 둔 대본으로 끝까지 간다.
// "준비 중"은 곧 풀릴 수 있어 기억하지 않는다
const loaded = new Map();

// 점검 중(503 SCRIPT_UNDER_MAINTENANCE, FE-145)은 "준비 중"(null)과 다르게 안내해야 해서 null로 뭉개지 않고 던진다
export const isScriptMaintenance = (error) => error?.code === 'SCRIPT_UNDER_MAINTENANCE';

// 체험 화면의 불러오기 실패 안내. 점검 중이면 다시 시도해도 같은 오류라는 걸 알려 주고, 그 밖에는 일반 안내다(FE-145)
export const loadFailText = (error) => (isScriptMaintenance(error)
  ? '대본을 점검하고 있어요. 점검이 끝나면 이어서 할 수 있어요. 잠시 후 다시 시도해 주세요.'
  : '정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');

/** 사건의 판결 체험 대본(FE-139). 서버 대본이 없으면(404 · 조립 불가) null. 점검 중 등 그 밖의 오류는 던져 화면이 처리하게 한다 */
export async function loadCaseScript(api, caseId) {
  if (loaded.has(caseId)) return loaded.get(caseId);
  let response;
  try {
    response = await api.getCaseScript(caseId);
  } catch (error) {
    if (error?.code === 'SCRIPT_NOT_FOUND') return null;
    throw error;
  }
  const script = assembleScript(response);
  if (script) loaded.set(caseId, script);
  else console.warn('[FE-139] 공개 대본을 조립하지 못해 준비 중으로 둡니다', { caseId });
  return script;
}
