import { element } from './siteLayout.js';

// 관용 지수 눈금 (FR-8-4). 만점처럼 보이지 않게 "엄격함 ↔ 관대함" 양 끝 눈금 막대에 위치만 표시한다.
// level: 0(가장 엄격) ~ 4(가장 관대). 숫자 "n / 4"는 화면에 쓰지 않는다. 스타일은 src/style.css
export function renderToleranceScale(level) {
  const scale = element('div', 'tolerance-scale');
  const track = element('div', 'tolerance-track');
  track.setAttribute('role', 'img');
  track.setAttribute('aria-label', `엄격함과 관대함 사이 눈금에서 5칸 중 ${level + 1}번째 (왼쪽 끝이 가장 엄격, 오른쪽 끝이 가장 관대)`);
  const marker = element('span', 'tolerance-marker');
  marker.style.left = `${(level / 4) * 100}%`;
  track.append(marker);
  const ends = element('div', 'tolerance-ends');
  ends.append(element('span', '', '엄격함'), element('span', '', '관대함'));
  scale.append(track, ends);
  return scale;
}
