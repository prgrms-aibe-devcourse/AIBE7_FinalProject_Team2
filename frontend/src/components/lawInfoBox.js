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
  const guide = document.createElement('p');
  guide.className = 'muted';
  guide.textContent = '법정형은 법에 정해진 형의 범위, 선고할 수 있는 범위는 감경·가중까지 반영해 실제로 정할 수 있는 최대 범위예요.';
  const note = document.createElement('p');
  note.className = 'muted';
  note.textContent = law.allowedRangeNote;
  box.append(list, guide, note);
  container.append(box);
  return box;
}
