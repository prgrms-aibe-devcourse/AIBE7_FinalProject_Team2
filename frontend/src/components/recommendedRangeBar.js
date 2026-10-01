export function formatMonths(months) {
  const years = Math.floor(months / 12);
  const remainder = months % 12;
  return [years ? `${years}년` : '', remainder || !years ? `${remainder}개월` : ''].filter(Boolean).join(' ');
}

function arrangeTicks(labels, track) {
  const width = labels.clientWidth;
  if (!width) return;
  const accepted = [];
  const ticks = [...labels.children, ...track.querySelectorAll('.range-input-label')]
    .sort((a, b) => Number(b.dataset.priority) - Number(a.dataset.priority));
  for (const tick of ticks) {
    tick.hidden = false;
    const position = Number(tick.dataset.position);
    const size = tick.getBoundingClientRect().width;
    const left = Math.max(0, Math.min(width - size, width * position / 100 - size / 2));
    tick.style.left = `${left}px`;
    const collides = accepted.some((other) => Math.abs(position - other.position) <= 6
      || (left < other.right + 4 && left + size + 4 > other.left));
    tick.hidden = collides;
    if (!collides) accepted.push({ position, left, right: left + size });
  }
}

function observeTicks(labels, track) {
  let mounted = labels.isConnected;
  const resizeObserver = new ResizeObserver(() => {
    if (labels.isConnected) arrangeTicks(labels, track);
  });
  const removalObserver = new MutationObserver((records) => {
    if (labels.isConnected) {
      mounted = true;
    } else if (mounted || records.some((record) => [...record.removedNodes].some((node) => node.contains(labels)))) {
      resizeObserver.disconnect();
      removalObserver.disconnect();
    }
  });
  removalObserver.observe(document.documentElement, { childList: true, subtree: true });
  resizeObserver.observe(labels);
}

export function renderRecommendedRangeBar(container, { law, markerMonths = null, showTrack = true, trackMaxMonths = null, allowedMinMonths = null, penaltyType = 'PRISON' }) {
  const box = document.createElement('section');
  box.className = 'recommended-range';
  const heading = document.createElement('h3');
  heading.textContent = '양형기준 권고 범위';
  const range = document.createElement('p');
  range.className = 'range-value';
  const { minMonths, maxMonths, basis } = law.recommended;
  const recommendedText = `징역 ${formatMonths(minMonths)} ~ ${formatMonths(maxMonths)}`;
  range.textContent = recommendedText;
  const max = Number.isFinite(trackMaxMonths) ? trackMaxMonths
    : law.allowedRanges.find(({ penaltyType }) => penaltyType === 'PRISON')?.allowedMax;
  box.append(heading, range);
  const hasMinimum = Number.isFinite(allowedMinMonths);
  const overlapMin = hasMinimum ? Math.max(minMonths, allowedMinMonths) : minMonths;
  const overlapMax = Math.min(maxMonths, max);
  const overlaps = overlapMin <= overlapMax;
  if (hasMinimum) {
    heading.textContent = '선고할 수 있는 범위';
    range.textContent = `징역 ${formatMonths(allowedMinMonths)} ~ ${formatMonths(max)}`;
    const recommendation = document.createElement('p');
    recommendation.className = `range-recommendation${overlaps ? '' : ' muted'}`;
    recommendation.textContent = `양형기준 권고 범위 ${recommendedText}${!overlaps
      ? ' — 이 형벌에는 해당하지 않아요'
      : overlapMin !== minMonths || overlapMax !== maxMonths
        ? ` (이 형벌에서는 ${formatMonths(overlapMin)} ~ ${formatMonths(overlapMax)})` : ''}`;
    box.append(recommendation);
  }
  if (showTrack && Number.isFinite(max) && max > 0) {
    const track = document.createElement('div');
    track.className = 'range-track';
    track.setAttribute('role', 'img');
    const markerLabel = Number.isFinite(markerMonths) ? `입력한 형량 ${formatMonths(markerMonths)}` : '';
    const allowedLabel = hasMinimum
      ? `, 선고 가능 범위 징역 ${formatMonths(allowedMinMonths)} ~ ${formatMonths(max)}, ${overlaps
        ? `권고 범위와 겹치는 구간 징역 ${formatMonths(overlapMin)} ~ ${formatMonths(overlapMax)}`
        : '권고 범위와 겹치는 구간 없음'}` : '';
    track.setAttribute('aria-label', `0부터 ${formatMonths(max)} 중 권고 범위 ${recommendedText}${allowedLabel}${markerLabel ? `, ${markerLabel}` : ''}`);
    if (hasMinimum) {
      const unavailable = document.createElement('span');
      unavailable.className = 'range-unavailable';
      unavailable.style.width = `${Math.max(0, Math.min(100, allowedMinMonths / max * 100))}%`;
      unavailable.setAttribute('aria-hidden', 'true');
      unavailable.title = '선고할 수 없는 구간';
      track.append(unavailable);
    }
    const fill = document.createElement('span');
    fill.className = 'range-fill';
    const start = Math.max(0, Math.min(100, overlapMin / max * 100));
    const end = Math.max(start, Math.min(100, maxMonths / max * 100));
    fill.style.left = `${start}%`;
    fill.style.width = `${end - start}%`;
    if (hasMinimum && !overlaps) fill.hidden = true;
    track.append(fill);
    if (markerLabel) {
      const marker = document.createElement('span');
      marker.className = `range-marker${markerMonths > max ? ' is-warning' : ''}`;
      marker.style.left = `${Math.max(0, Math.min(100, markerMonths / max * 100))}%`;
      // role="img"의 이름에 형량을 포함하므로 장식용 마커는 중복해서 읽지 않는다.
      marker.setAttribute('aria-hidden', 'true');
      marker.title = markerLabel;
      track.append(marker);
    }
    const labels = document.createElement('div');
    labels.className = 'range-labels';
    const zero = document.createElement('span');
    zero.textContent = '0';
    const upper = document.createElement('span');
    upper.textContent = `선고할 수 있는 상한 ${formatMonths(max)}`;
    labels.append(zero, upper);
    box.append(track, labels);
    if (hasMinimum) {
      track.classList.add('range-track-with-ticks');
      labels.className = 'range-ticks';
      labels.setAttribute('aria-hidden', 'true');
      labels.replaceChildren();
      const tick = (months, priority, className = '') => {
        const label = document.createElement('span');
        label.className = `range-tick ${className}`.trim();
        label.textContent = months === 0 ? '0' : formatMonths(months);
        label.dataset.position = Math.max(0, Math.min(100, months / max * 100));
        label.dataset.priority = priority;
        if (className === 'range-input-label') {
          label.setAttribute('aria-hidden', 'true');
          track.append(label);
        } else labels.append(label);
      };
      if (markerLabel) tick(markerMonths, 3, 'range-input-label');
      // 끝점은 유지하되 입력값과 충돌하면 입력값을 우선한다.
      tick(0, 2.5);
      tick(max, 2.5);
      tick(allowedMinMonths, 2, 'range-minimum-label');
      if (overlaps) {
        tick(overlapMin, 1);
        tick(overlapMax, 1);
      }
      observeTicks(labels, track);
      const lower = document.createElement('p');
      lower.className = 'range-lower-label';
      lower.textContent = '빗금: 선고할 수 없는 구간';
      box.append(lower);
      if (!overlaps) {
        const explanation = document.createElement('p');
        explanation.className = 'range-no-overlap';
        explanation.textContent = `${penaltyType === 'PRISON' ? '선고할 수 있는 형량은' : '감경한 징역은'} ${formatMonths(allowedMinMonths)} ~ ${formatMonths(max)}이라 권고 범위(${recommendedText})와 겹치지 않아요. 권고 범위는 유기징역 기준이에요.`;
        box.append(explanation);
      }
    }
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
