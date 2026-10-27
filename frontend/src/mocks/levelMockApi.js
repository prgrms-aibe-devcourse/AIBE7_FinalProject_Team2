// 레벨 · 직업 목 API (FE-114, BE-106, 명세 11장 L-1 ~ L-2)와 마이페이지(P-5)에 붙는 level 응답.
// 서버와 같은 레벨 곡선(k = 0.08, 총 10000 XP)과 직업 11등급(Lv1 · 10 · 20 … 100)을 쓴다. 경험치 · 고른 직업은 목 전용으로 localStorage에 둔다.
// 개발 전용(실제 API에는 없다): setMockLevel · unlockMockNextJob · resetMockLevel — 프로필카드 화면의 "개발용" 버튼이 쓴다

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const KEY = 'nlnb-mock-level';
const MAX_LEVEL = 100;
const TOTAL_XP = 10000;
const K = 0.08;
const START_LEVEL = 7; // 예전 목 값(Lv.7)에서 시작한다

const JOBS = [
  ['NONE', '무직'], ['LAW_STUDENT', '로스쿨생'], ['TRAINEE', '연수원생'], ['PROBATION_JUDGE', '수습 판사'], ['JUDGE', '판사'], ['PRESIDING_JUDGE', '부장판사'],
  ['HIGH_COURT_JUDGE', '고등법원 판사'], ['DISTRICT_COURT_CHIEF', '지방법원장'], ['HIGH_COURT_CHIEF', '고등법원장'], ['JUSTICE', '대법관'], ['CHIEF_JUSTICE', '대법원장'],
].map(([code, name], index) => ({ code, name, unlockLevel: index === 0 ? 1 : index * 10 }));

// 레벨별 누적 XP: 10000 × [1 − ln(1 + k(100 − L)) / ln(1 + 99k)]
const xpAt = (level) => Math.round(TOTAL_XP * (1 - Math.log(1 + K * (MAX_LEVEL - level)) / Math.log(1 + (MAX_LEVEL - 1) * K)));
const levelOf = (xp) => {
  let level = 1;
  while (level < MAX_LEVEL && xpAt(level + 1) <= xp) level += 1;
  return level;
};
const highestJobAt = (level) => JOBS.filter((job) => job.unlockLevel <= level).at(-1);

/** 다른 사람(커뮤니티 목 글쓴이)의 직업 { code, name }: 그 레벨에서 열린 가장 높은 직업 */
export const mockJobAt = (level) => { const { code, name } = highestJobAt(level); return { code, name }; };

const read = () => {
  try {
    return JSON.parse(localStorage.getItem(KEY)) ?? {};
  } catch {
    return {};
  }
};
const write = (value) => {
  try {
    localStorage.setItem(KEY, JSON.stringify(value));
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만 바뀐다
  }
};
let memory = null; // 저장소를 못 쓰는 환경(테스트 등)에서도 이번 방문 동안 값을 지킨다
const state = () => ({ xp: xpAt(START_LEVEL), job: null, daily: null, given: [], ...(memory ?? read()) });
const save = (next) => { memory = next; write(next); };

// 커뮤니티 경험치(명세 11장 수급처 표): 글 30(하루 3건) · 판결 공유 글 보너스 +20(사건당 1회) · 댓글 10(하루 5건) · 좋아요 누르기 2(글당 1회, 하루 10개).
// 커뮤니티 API는 서버에 아직 없어 목 커뮤니티가 글 · 댓글 · 좋아요 때 직접 부른다(서버는 BE가 LevelService.award로 지급한다)
const COMMUNITY_XP = { post: { xp: 30, perDay: 3 }, comment: { xp: 10, perDay: 5 }, like: { xp: 2, perDay: 10 }, verdictBonus: { xp: 20, perDay: Infinity } };
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });

/** 개발 전용: 커뮤니티 활동으로 경험치를 준다. key는 같은 대상 중복 방지(글 · 좋아요는 대상, 보너스는 사건). 받은 XP를 돌려준다(상한이면 0) */
export function awardMockXp(source, key = null) {
  const rule = COMMUNITY_XP[source];
  const current = state();
  const day = today();
  const daily = current.daily?.day === day ? current.daily : { day };
  const givenKey = key == null ? null : `${source}:${key}`;
  if (!rule || (givenKey && current.given.includes(givenKey)) || (daily[source] ?? 0) >= rule.perDay) return 0;
  save({ ...current, xp: Math.min(TOTAL_XP, current.xp + rule.xp), daily: { ...daily, [source]: (daily[source] ?? 0) + 1 }, given: givenKey ? [...current.given, givenKey] : current.given });
  return rule.xp;
}

/** L-1 응답. 고른 적이 없으면 열린 가장 높은 직업이 카드에 보인다 */
export function mockLevelView() {
  const { xp, job } = state();
  const level = levelOf(xp);
  const chosen = JOBS.find((item) => item.code === job && item.unlockLevel <= level) ?? highestJobAt(level);
  return {
    level,
    xp,
    currentLevelXp: xpAt(level),
    nextLevelXp: level >= MAX_LEVEL ? null : xpAt(level + 1),
    job: { code: chosen.code, name: chosen.name },
    jobs: JOBS.map((item) => ({ ...item, unlockXp: xpAt(item.unlockLevel), unlocked: item.unlockLevel <= level, selected: item.code === chosen.code })),
  };
}

export async function getLevel() {
  await delay();
  return mockLevelView();
}

function levelError(code, status, field) {
  throw { code, message: code === 'JOB_LOCKED' ? '아직 열리지 않은 직업입니다.' : '요청 값이 올바르지 않습니다.', currentStatus: null, details: field ? [{ field }] : null, status };
}

/** L-2: 모르는 코드 → 400, 아직 안 열린 직업 → 409. 응답은 저장 뒤의 L-1 값 */
export async function putLevelJob(job) {
  await delay();
  const target = JOBS.find((item) => item.code === job);
  if (!target) levelError('VALIDATION_ERROR', 400, 'job');
  if (target.unlockLevel > mockLevelView().level) levelError('JOB_LOCKED', 409);
  save({ ...state(), job: target.code });
  return mockLevelView();
}

/** 개발 전용: 레벨을 그 값으로(1 ~ 100). 직업은 한 번 열리면 다시 잠기지 않으므로 낮출 때는 쓰지 않는다 */
export async function setMockLevel(level) {
  await delay();
  save({ ...state(), xp: xpAt(Math.min(MAX_LEVEL, Math.max(1, level))) });
  return mockLevelView();
}

/** 개발 전용: 다음 직업이 열리는 레벨까지 올린다. 이미 마지막 직업이면 그대로 */
export async function unlockMockNextJob() {
  const { level } = mockLevelView();
  const next = JOBS.find((job) => job.unlockLevel > level);
  return setMockLevel(next ? next.unlockLevel : level);
}

/** 개발 전용: 처음 상태(Lv.7 · 고른 직업 없음)로 */
export async function resetMockLevel() {
  await delay();
  save({ xp: xpAt(START_LEVEL), job: null, daily: null, given: [] });
  return mockLevelView();
}
