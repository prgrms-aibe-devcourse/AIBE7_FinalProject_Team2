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

// 첫 값과 마지막 값을 견줘 어느 쪽으로 얼마나 움직였는지 말해 준다. 값이 둘보다 적으면 null
export function trailSummary(points, axis) {
  if (points.length < 2) return null;
  const moved = Math.round((points[points.length - 1].ratio - points[0].ratio) * 100);
  if (Math.abs(moved) < STEADY_POINTS) return '처음과 거의 같아요';
  const side = moved > 0 ? axis.lenient.name : axis.strict.name;
  return `처음보다 ${side} 쪽으로 ${Math.abs(moved)}%p 움직였어요`;
}

// 점의 가로 위치(%). 왼쪽 = 엄격 쪽, 오른쪽 = 관대 쪽이다(ratio는 관대 쪽 비중)
export function trailLeft(ratio) {
  return Math.round(ratio * 100);
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
