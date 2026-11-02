import { assembleScript } from '../content/assembleScript.js';

// 받은 대본은 새로고침 전까지 사건별로 기억한다: 막마다 화면이 바뀌어도 다시 받지 않고, 체험 중에 서버 대본이 바뀌어도 받아 둔 대본으로 끝까지 간다.
// "준비 중"은 곧 풀릴 수 있어 기억하지 않는다
const loaded = new Map();

/** 사건의 판결 체험 대본(FE-139). 서버 대본이 없으면(404 · 조립 불가) null. 그 밖의 오류는 던져 화면의 오류 처리에 맡긴다 */
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
