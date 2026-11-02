import { lenientPercent } from './personalityAxes.js';

// S-17 마이페이지 화면용 계산. 화면 코드에서 떼어 npm test로 확인한다.
// 점수는 API 명세 9장의 축 점수와 같다: 0 ~ 1, 관대 쪽 비중이고 값이 없는 축은 null.

// 이 정도(%p) 안쪽의 움직임은 "거의 그대로"로 본다
export const STEADY_POINTS = 5;

// 한 축의 체험별 위치. order는 체험 순서(1부터)라서 값이 없는 체험은 건너뛰어도 번호는 그대로다
export function axisSeries(history, axisKey) {
  const points = [];
  (history ?? []).forEach((entry, index) => {
    const ratio = entry?.axisScores?.[axisKey];
    if (typeof ratio === 'number') points.push({ order: index + 1, ratio });
  });
  return points;
}

// 성향 변화 흐름(FE-70)의 한 축: 성향 테스트 점수가 있으면 맨 앞(order 0)에 두고, 뒤로 체험 순서대로 잇는다.
// label은 그래프 아래 · 표에 쓰는 짧은 이름, title은 툴팁에 쓰는 긴 이름이다
export function axisTrend(history, axisKey, testScores) {
  const test = testScores?.[axisKey];
  const start = typeof test === 'number' ? [{ order: 0, ratio: test, label: '초심', title: '초심 · 성향 테스트', isTest: true }] : [];
  const points = axisSeries(history, axisKey).map((point) => ({ ...point, label: String(point.order), title: `${point.order}번째 체험 · ${history[point.order - 1]?.title ?? ''}`.replace(/ · $/, ''), isTest: false }));
  return [...start, ...points];
}

// 성향 변화 흐름에 보일 칸(order): 테스트(0, 있을 때만) + 최근 체험 recent건. 체험이 적으면 있는 만큼만.
// 테스트가 없으면 체험만 recent건이다(창 밖의 첫 체험을 따로 붙이면 recent건보다 많아진다, FE-77)
export const TREND_RECENT = 10;
export function trendOrders(historyLength, hasTest, recent = TREND_RECENT) {
  const latest = Array.from({ length: Math.min(recent, historyLength) }, (_, index) => historyLength - Math.min(recent, historyLength) + index + 1);
  return hasTest ? [0, ...latest] : latest;
}

// 성향카드를 다시 정한 테스트(FE-77)가 흐름에 들어갈 자리: 그 시각까지 마친 체험 수(history는 완료 순).
// 사람마다 시점이 달라 건수(completedExperienceCount)가 아니라 시각으로 정한다(그래프의 체험은 성향 기여가 저장된 첫 회차만이라 건수와 어긋날 수 있다)
export function retakeAfterOrder(history, testedAt) {
  const list = history ?? [];
  const at = Date.parse(testedAt);
  if (Number.isNaN(at)) return list.length;
  return list.filter((entry) => Date.parse(entry?.completedAt) <= at).length;
}

// trendOrders 결과에 다시 정함 칸('retake')을 끼운다: 마친 체험 after건 바로 뒤. 그 체험이 최근 창 밖으로 밀려났으면 보이는 첫 체험 앞에 둔다(항상 보인다)
export function withRetakeSlot(orders, after) {
  const index = orders.findIndex((order) => order > after);
  return index === -1 ? [...orders, 'retake'] : [...orders.slice(0, index), 'retake', ...orders.slice(index)];
}

// 강조할 체험(focus, 내 결과 지면의 이 사건 FE-75)이 위 칸 밖이면 순서대로 끼워 넣는다
export function trendSlotOrders(historyLength, hasTest, focus) {
  const orders = trendOrders(historyLength, hasTest);
  return focus && !orders.includes(focus) ? [...orders, focus].sort((a, b) => a - b) : orders;
}

// 한 축의 변경폭: 처음(테스트 또는 첫 체험) → 지금. 지금은 current(현재 판결 성향 = 최근 체험 3건 평균, API 명세 P-5)이고,
// 그 축 값이 없으면 가장 최근 체험 점이다. 체험이 하나도 없거나 점이 둘보다 적으면 null.
// moved는 관대 쪽으로 움직인 %p(음수면 엄격 쪽), toward는 움직인 쪽 이름, steady는 STEADY_POINTS 안쪽이면 true,
// same은 실제 값이 같으면 true(부동소수 오차 SAME 안쪽 포함). 표시가 같아도 실제로 다르면 false라서 "변화 없음"은 완전 일치만이다
export function axisChange(points, axis, current) {
  if (points.length < 2 || points.every((point) => point.isTest)) return null;
  const from = points[0];
  const to = typeof current === 'number' ? { ratio: current, isCurrent: true } : points[points.length - 1];
  const moved = lenientPercent(to.ratio) - lenientPercent(from.ratio); // 화면에 보이는 두 비중의 차이
  const steady = Math.abs(moved) < STEADY_POINTS;
  const same = Math.abs(to.ratio - from.ratio) < SAME;
  return { from, to, moved, steady, same, toward: steady ? null : (moved > 0 ? axis.lenient.name : axis.strict.name) };
}

// "거의 그대로"(steady)여도 처음과 지금이 실제로 다르면 변경폭에 빗금 띠(머리 없는 화살표 몸통)를 그린다. 숫자 · 글자("거의 그대로")는 바꾸지 않고 그리는 폭에만 쓴다.
// - 폭은 두 경계 사이(표시 비중 차이)에 비례한다. 표시 비중이 같은데 실제 값이 다르면 최소 폭(BAND_MIN_PERCENT)으로 **표시 경계 가운데에** 두고,
//   화면은 그 띠의 양 끝에 경계선 둘을 긋는다(표시 차이 1%p일 때와 같은 모양).
//   방향으로 늘리지 않는 이유: 49 → 49에서 관대 쪽으로 1%p를 늘리면 끝이 50%에 닿는데, lenientPercent(반반 특별법)는 정확히 반반이 아니면 50을 보여 주지 않는다.
// - 실제로 같으면(부동소수 오차 SAME 안쪽 포함) 그리지 않는다. 표시값은 단조라서 표시 차이의 부호는 실제 변화의 부호와 같다.
// 돌려주는 값: { left, width(%, 막대 안), grew('strict' | 'lenient', 실제 변화 쪽) } 또는 null
export const BAND_MIN_PERCENT = 1;
const SAME = 1e-9; // personalityAxes의 TIE와 같은 허용 오차(분수 평균의 부동소수 잡음은 같은 값)
export function steadyBand(fromRatio, toRatio) {
  if (Math.abs(toRatio - fromRatio) < SAME) return null;
  const fromStrict = 100 - lenientPercent(fromRatio);
  const toStrict = 100 - lenientPercent(toRatio);
  const shown = Math.abs(toStrict - fromStrict);
  const width = Math.max(shown, BAND_MIN_PERCENT);
  const center = (fromStrict + toStrict) / 2;
  const left = Math.min(Math.max(center - width / 2, 0), 100 - width); // 막대 안쪽으로
  return { left, width, grew: toRatio > fromRatio ? 'lenient' : 'strict' };
}

// 관대 쪽 비중(%)은 4축 막대와 같은 함수(utils/personalityAxes.js)를 쓴다. 0.585가 막대 58% · 글자 59%로 갈리지 않게
export { lenientPercent };

// 한 위치를 말로: 더 기운 쪽 이름과 그 비중. 정확히 반반이면 "반반"
export function leanText(ratio, axis) {
  const lenient = lenientPercent(ratio);
  if (lenient === 50) return '반반';
  return lenient > 50 ? `${axis.lenient.name} ${lenient}%` : `${axis.strict.name} ${100 - lenient}%`;
}

// 내 결과 지면(FE-75)에 실을 이 사건의 기록: scores(이 사건의 축 점수), type(이 사건 한 건의 유형, 서버가 정한다 BE-100),
// order(성향 변화 흐름의 체험 순서, 1부터). myPage는 마이페이지 응답(P-5). 못 불러왔으면 null, 성향 기록이 없는 사건이면 빈 객체.
// type: 유형 객체 · null(서버가 "네 축이 다 모이지 않았다"로 판단) · undefined(응답에 type 칸이 없음 = BE-100 이전 서버, PR #246 리뷰)
export function recordOf(myPage, caseId) {
  if (!myPage) return null;
  const index = (myPage.history ?? []).findIndex((item) => String(item.caseId) === String(caseId));
  if (index < 0) return {};
  const item = myPage.history[index];
  return { scores: item.axisScores, type: Object.hasOwn(item, 'type') ? item.type : undefined, order: index + 1 };
}

// 이번 사건에서 만난 모습 — 대본에 나온 역할 의상(look "role:…")을 등장 순서대로 모은다. 6막 신문 사진란과 마이페이지가 함께 쓴다
const CAST_NAMES = { naero: '내로', nambul: '남불' };
const ROLE_NAMES = { actor: 'A씨 역', prosecutor: '검사', defense: '변호인', judge: '판사' };
export function castOf(script) {
  const seen = new Map();
  Object.values(script?.acts ?? {}).forEach((act) => {
    [act.look, ...(act.steps ?? []).map((step) => step.look)].forEach((look) => Object.entries(look ?? {}).forEach(([who, value]) => {
      if (CAST_NAMES[who] && typeof value === 'string' && value.startsWith('role:')) {
        const role = value.slice(5);
        seen.set(`${who}:${role}`, { who, role, label: `${CAST_NAMES[who]} · ${ROLE_NAMES[role] ?? role}` });
      }
    }));
  });
  return [...seen.values()];
}

// 이미지를 사건별이 아니라 종류별로 묶는다(FE-67). 종류 = public/images 아래 폴더. 위에서부터 이 순서로 나온다.
// 사건마다 가진 고유 이미지(cases/)는 "사건 이미지" 종류 안에서 사건별 하위 묶음으로 나눈다.
const KINDS = [
  ['characters/roles', '역할 의상'],
  ['characters/scene', '장면 컷'],
  ['characters/icon/generated', '얼굴 아이콘 · 생성한 것'],
  ['characters/icon/cropped', '얼굴 아이콘 · 잘라 만든 것'],
  ['characters/mascot', '로 마스코트'],
  ['characters/law', '로 · 성인 비율(보존)'],
  ['characters/adult', '성인 비율 로 컷'],
  ['characters/standing', '서 있는 컷'],
  ['characters/cheek', '볼따구 컷'],
  ['characters/cheek-slim', '슬림 볼따구 컷'],
  ['characters/personality', '성향 이미지'],
  ['cases', '사건 이미지'],
];
export const KIND_TITLES = Object.fromEntries(KINDS); // 폴더 → 종류 이름 (대표 이미지 페이지 FE-68도 쓴다)
const OTHER_KIND = '기타';
const CASE_FILE = /^case-(\d+)(?:[-.]|$)/; // cases/case-1-origin.webp → 사건 1

/**
 * 경로("/images/a/b.webp")를 종류별로 묶는다. 각 묶음은 { key, title, items } 이고, 사건 이미지는 items 대신 사건별 groups를 가진다.
 * @param {Iterable<string>} paths
 * @param {{ labels?: Record<string, string>, caseNames?: Record<string, string> }} options labels: 경로별 이름(없으면 파일 이름), caseNames: 사건 번호별 이름(없으면 "사건 N")
 */
export function imageKinds(paths, { labels = {}, caseNames = {} } = {}) {
  const buckets = new Map();
  [...new Set(paths)].sort((a, b) => a.localeCompare(b, 'en', { numeric: true })).forEach((path) => {
    const slash = path.lastIndexOf('/');
    const folder = path.slice(0, slash).replace(/^\/images\/?/, '');
    const file = path.slice(slash + 1);
    const known = KINDS.find(([key]) => key === folder);
    const key = known ? known[0] : folder || OTHER_KIND;
    if (!buckets.has(key)) buckets.set(key, { key, title: known ? known[1] : key, items: [] });
    buckets.get(key).items.push({ src: path, label: labels[path] ?? file.replace(/\.webp$/, '') });
  });
  const order = (key) => { const i = KINDS.findIndex(([known]) => known === key); return i < 0 ? KINDS.length : i; };
  return [...buckets.values()].sort((a, b) => order(a.key) - order(b.key) || a.key.localeCompare(b.key)).map((kind) => {
    if (kind.key !== 'cases') return kind;
    const groups = new Map();
    kind.items.forEach((item) => {
      const id = CASE_FILE.exec(item.src.slice(item.src.lastIndexOf('/') + 1))?.[1];
      const title = id ? (caseNames[id] ?? `사건 ${id}`) : '공용 컷';
      if (!groups.has(title)) groups.set(title, []);
      groups.get(title).push(item);
    });
    return { key: kind.key, title: kind.title, items: kind.items, groups: [...groups].map(([title, items]) => ({ title, items })) };
  });
}

// ── 컬렉션(FE-132) ──
// 획득 조건은 화면이 판정하고, 서버(P-9 POST /collection)는 항목 · 획득 시각 · 확인 여부만 저장한다. 마이페이지는 서버 목록(P-5 collection)을 그린다.
// 일러스트 key는 사건별이 아니라 그림별이다. 사건마다 일러스트 3장(재연 · 발단 · 결론 자리)이고 id는 사진 주소 규칙(BE-141)과 같은 이름이다:
// case-{사건}-{reenact | origin | conclusion} → /images/cases/{id}.webp. 신문 1면(재연) · 기사 속 작은 사진(발단 · 결론) · 증거조사 두 칸(결론 · 발단)이 이 셋을 가리킨다.
// 관리자가 사진을 DB에만 올려도(A-13) 자리 id는 같고, 컬렉션은 저장소 파일을 보여 준다(BE-143 후속).
export const ILLUSTRATION_SLOTS = ['reenact', 'origin', 'conclusion'];
const SLOT_NAMES = { reenact: '재연', origin: '발단', conclusion: '결론' };
const ILLUSTRATION_ID = /^case-(\d+)-(reenact|origin|conclusion)$/;

export const illustrationIdsOf = (caseId) => ILLUSTRATION_SLOTS.map((slot) => `case-${caseId}-${slot}`);

/** 사건 하나를 마치면 얻는 것: 일러스트 3장 + 만난 모습(역할 의상 {who}:{role}). P-9 items 모양({ kind, itemId }) */
export function collectionOf(caseId, script) {
  return [
    ...illustrationIdsOf(caseId).map((itemId) => ({ kind: 'illustration', itemId })),
    ...castOf(script).map(({ who, role }) => ({ kind: 'cast', itemId: `${who}:${role}` })),
  ];
}

// 역할 이름. 사건 전용 의상(role "case-1")은 "사건 1 배역"
const roleName = (role) => ROLE_NAMES[role] ?? (/^case-(\d+)$/.test(role) ? `사건 ${role.slice(5)} 배역` : role);

/**
 * 서버 목록(P-5 collection.items, 최근에 얻은 순)을 화면 항목으로 바꾼다. 모르는 종류 · 형식은 뺀다.
 * caseTitles: 사건 번호 → 제목(체험 기록에서). 없으면 "사건 N"
 * 돌려주는 항목: { kind, itemId, caseId(그림의 사건, 일러스트만), who · role(의상만), src, label, acquiredAt, isNew }
 */
export function collectedItems(items, caseTitles = {}) {
  return (items ?? []).flatMap((item) => {
    const base = { kind: item.kind, itemId: item.itemId, acquiredAt: item.acquiredAt ?? null, isNew: item.isNew === true };
    if (item.kind === 'illustration') {
      const match = ILLUSTRATION_ID.exec(item.itemId ?? '');
      if (!match) return [];
      const caseId = Number(match[1]);
      return [{ ...base, caseId, caseTitle: caseTitles[caseId] ?? `사건 ${caseId}`, slot: match[2], src: `/images/cases/${item.itemId}.webp`, label: SLOT_NAMES[match[2]] }];
    }
    if (item.kind === 'cast') {
      const [who, role] = String(item.itemId ?? '').split(':');
      if (!CAST_NAMES[who] || !role) return [];
      return [{ ...base, who, role, src: `/images/characters/roles/${who}_${role}.webp`, label: `${CAST_NAMES[who]} · ${roleName(role)}` }];
    }
    return [];
  });
}

/**
 * 컬렉션 화면의 묶음: 만난 모습(역할 의상) 한 묶음 + 사건별 일러스트 묶음(최근에 얻은 사건부터, 사건 안은 재연 · 발단 · 결론 순)
 * @returns {{ cast: object[], cases: { caseId: number, title: string, items: object[] }[] }}
 */
export function collectionGroups(collected) {
  const cases = new Map();
  collected.filter((item) => item.kind === 'illustration').forEach((item) => {
    if (!cases.has(item.caseId)) cases.set(item.caseId, { caseId: item.caseId, title: item.caseTitle, items: [] });
    cases.get(item.caseId).items.push(item);
  });
  const slotOrder = (item) => ILLUSTRATION_SLOTS.indexOf(item.slot);
  return {
    cast: collected.filter((item) => item.kind === 'cast'),
    cases: [...cases.values()].map((group) => ({ ...group, items: [...group.items].sort((a, b) => slotOrder(a) - slotOrder(b)) })),
  };
}

/** 기존 사용자 백필(P-9 backfill): 마친 사건인데 그 사건 일러스트가 하나라도 빠진 사건 번호(중복 없이). retry는 의상이 남아 다시 보낼 사건 */
export function backfillCaseIds(experiences, items, retry = []) {
  const have = new Set((items ?? []).map((item) => item.itemId));
  const done = (experiences ?? []).filter((item) => item.status === 'COMPLETED').map((item) => item.caseId);
  return [...new Set(done)].filter((caseId) => retry.includes(caseId) || illustrationIdsOf(caseId).some((id) => !have.has(id)));
}

/** 획득 · 달성 날짜(한국 시간): "2026. 10. 14." 값이 없거나 읽지 못하면 '' */
export function acquiredDate(value) {
  const time = Date.parse(value ?? '');
  if (Number.isNaN(time)) return '';
  return new Date(time).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric' });
}
