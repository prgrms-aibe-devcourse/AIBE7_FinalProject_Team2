export function formatMonths(months) {
  const years = Math.floor(months / 12);
  const remainder = months % 12;
  return [years ? `${years}년` : '', remainder || !years ? `${remainder}개월` : ''].filter(Boolean).join(' ');
}

export function renderRecommendedRangeBar(container, { law }) {
  const box = document.createElement('section');
  box.className = 'recommended-range';
  const heading = document.createElement('h3');
  heading.textContent = '양형기준 권고 범위';
  const range = document.createElement('p');
  range.className = 'range-value';
  const { minMonths, maxMonths, basis } = law.recommended;
  range.textContent = `징역 ${formatMonths(minMonths)} ~ ${formatMonths(maxMonths)}`;
  const max = law.allowedRanges.find(({ penaltyType }) => penaltyType === 'PRISON')?.allowedMax;
  box.append(heading, range);
  if (Number.isFinite(max) && max > 0) {
    const track = document.createElement('div');
    track.className = 'range-track';
    track.setAttribute('role', 'img');
    track.setAttribute('aria-label', `0부터 ${formatMonths(max)} 중 권고 범위 ${range.textContent}`);
    const fill = document.createElement('span');
    fill.className = 'range-fill';
    const start = Math.max(0, Math.min(100, minMonths / max * 100));
    const end = Math.max(start, Math.min(100, maxMonths / max * 100));
    fill.style.left = `${start}%`;
    fill.style.width = `${end - start}%`;
    track.append(fill);
    const labels = document.createElement('div');
    labels.className = 'range-labels';
    const zero = document.createElement('span');
    zero.textContent = '0';
    const upper = document.createElement('span');
    upper.textContent = `선고할 수 있는 상한 ${formatMonths(max)}`;
    labels.append(zero, upper);
    box.append(track, labels);
  }
  const source = document.createElement('p');
  source.className = 'muted';
  source.textContent = basis;
  const note = document.createElement('p');
  note.className = 'range-note';
  note.textContent = '권고 범위는 정답이 아니라 참고 기준입니다.';
  box.append(source, note);
  container.append(box);
  return box;
}
