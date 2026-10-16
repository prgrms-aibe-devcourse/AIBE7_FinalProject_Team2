import { element } from './siteLayout.js';

// 4축 그래프의 한 줄: 축마다 양쪽 성향의 비중(관대 쪽 비중 = 축 점수). 성향 결과(S-16)와 마이페이지(S-17)가 함께 쓴다.
// 점수가 null이면 체험 자료가 없는 축이라 막대 대신 안내를 둔다(명세 P-5).
const pct = (ratio) => Math.round(ratio * 100);

export function renderAxisRow(axis, lenientRatio) {
  const row = element('li', 'axis-row');
  row.append(element('p', 'axis-label', axis.label));
  if (typeof lenientRatio !== 'number') {
    row.append(element('p', 'axis-empty', '체험 자료가 아직 없어요'));
    return row;
  }
  const strict = pct(1 - lenientRatio);
  const lenient = 100 - strict;
  const labels = element('div', 'axis-ends');
  labels.append(
    element('span', strict > lenient ? 'is-major' : '', `${axis.strict.name} ${strict}%`),
    element('span', lenient > strict ? 'is-major' : '', `${lenient}% ${axis.lenient.name}`),
  );
  const bar = element('div', 'axis-bar');
  bar.setAttribute('role', 'img');
  bar.setAttribute('aria-label', `${axis.strict.name} ${strict}%, ${axis.lenient.name} ${lenient}%`);
  const fill = element('span');
  fill.style.width = `${strict}%`; // 왼쪽 = 엄격 쪽 비중
  bar.append(fill);
  row.append(labels, bar);
  return row;
}
