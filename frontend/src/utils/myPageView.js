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
  ['characters/deform', '데포르메'],
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
  [...new Set(paths)].sort().forEach((path) => {
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

// 컬렉션 — 체험을 마친 사건마다 만난 모습(역할 의상)과 대본에 나온 일러스트를 모은다. 같은 그림은 한 장으로 센다.
// 대본이 없는 사건은 모을 것이 없으므로 건너뛴다.
// 사진 주소는 화면에 박힌 대본(저장소 파일) 기준이다 — 가능한 한 DB 기준으로 저장소를 최신화한다. 사건 사진은 DB 저장값(관리자 업로드)이 우선이지만(BE-141)
// 이 컬렉션은 DB 값을 보지 않으므로, 관리자가 DB에 사진을 올릴 때 같은 파일을 저장소에도 올리면 어긋나지 않는다. 조금 다른 것은 감수한다(필수가 아닌 후속 이슈 BE-143).
export function collectionOf(experiences, scripts) {
  return (experiences ?? [])
    .filter((item) => item.status === 'COMPLETED' && scripts?.[item.caseId])
    .map((item) => {
      const script = scripts[item.caseId];
      const { paper, evidencePhotos } = script;
      const seen = new Set();
      const items = [];
      const add = (src, label) => {
        if (!src || seen.has(src)) return;
        seen.add(src);
        items.push({ src, label });
      };
      add(paper?.photo, '신문 1면');
      (paper?.extraPhotos ?? []).forEach((photo, index) => add(photo.src, `연출 컷 ${index + 1}`));
      Object.entries(evidencePhotos ?? {}).forEach(([side, src]) => add(src, `${side} 재연`));
      return { caseId: item.caseId, title: item.title, cast: castOf(script), items };
    })
    .filter((group) => group.items.length > 0 || group.cast.length > 0);
}
