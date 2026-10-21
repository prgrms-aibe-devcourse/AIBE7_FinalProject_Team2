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

// 점의 가로 위치(%). 왼쪽 = 엄격 쪽, 오른쪽 = 관대 쪽이다(ratio는 관대 쪽 비중)
export function trailLeft(ratio) {
  return Math.round(ratio * 100);
}

// 성향 변화 흐름(FE-70)의 한 축: 성향 테스트 점수가 있으면 맨 앞(order 0)에 두고, 뒤로 체험 순서대로 잇는다.
// label은 그래프 아래 · 표에 쓰는 짧은 이름, title은 툴팁에 쓰는 긴 이름이다
export function axisTrend(history, axisKey, testScores) {
  const test = testScores?.[axisKey];
  const start = typeof test === 'number' ? [{ order: 0, ratio: test, label: '테스트', title: '성향 테스트', isTest: true }] : [];
  const points = axisSeries(history, axisKey).map((point) => ({ ...point, label: String(point.order), title: `${point.order}번째 체험 · ${history[point.order - 1]?.title ?? ''}`.replace(/ · $/, ''), isTest: false }));
  return [...start, ...points];
}

// 한 축의 변경폭: 처음(테스트 또는 첫 체험) → 지금(가장 최근 체험). 체험이 하나도 없거나 점이 둘보다 적으면 null.
// moved는 관대 쪽으로 움직인 %p(음수면 엄격 쪽), toward는 움직인 쪽 이름, steady는 STEADY_POINTS 안쪽이면 true
export function axisChange(points, axis) {
  if (points.length < 2 || points.every((point) => point.isTest)) return null;
  const from = points[0];
  const to = points[points.length - 1];
  const moved = Math.round((to.ratio - from.ratio) * 100);
  const steady = Math.abs(moved) < STEADY_POINTS;
  return { from, to, moved, steady, toward: steady ? null : (moved > 0 ? axis.lenient.name : axis.strict.name) };
}

// 한 위치를 말로: 더 기운 쪽 이름과 그 비중. 정확히 반반이면 "반반"
export function leanText(ratio, axis) {
  const lenient = Math.round(ratio * 100);
  if (lenient === 50) return '반반';
  return lenient > 50 ? `${axis.lenient.name} ${lenient}%` : `${axis.strict.name} ${100 - lenient}%`;
}

// 이번 사건에서 만난 모습 — 대본에 나온 역할 의상(look "role:…")을 등장 순서대로 모은다. 8막 신문 사진란과 마이페이지가 함께 쓴다
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
