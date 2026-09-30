export function renderLawInfoBox(container, { law, penaltyType = 'PRISON' }) {
  const box = document.createElement('section');
  box.className = 'law-info-box';
  const heading = document.createElement('h3');
  heading.textContent = '적용 법률 · 법정형';
  box.append(heading);
  const list = document.createElement('dl');
  const selected = law.allowedRanges.find((range) => range.penaltyType === penaltyType);
  for (const [label, value] of [
    ...(law.appliedLaw ? [['적용 법률', law.appliedLaw]] : []),
    ['법정형', law.statutoryPenaltyText],
    ['선고할 수 있는 범위', selected?.text ?? (penaltyType === 'PRISON' ? '징역 범위 정보 없음' : '범위 정보 없음')],
  ]) {
    const term = document.createElement('dt');
    const desc = document.createElement('dd');
    term.textContent = label;
    desc.textContent = value;
    list.append(term, desc);
  }
  const note = document.createElement('p');
  note.className = 'muted';
  note.textContent = law.allowedRangeNote;
  box.append(list, note);
  container.append(box);
  return box;
}
