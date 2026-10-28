// 닉네임처럼 줄여 쓰면 안 되는 글자를 한 줄에 맞춘다(FE-120): 말줄임 없이, 넘칠 때만 글자 크기를 줄인다.
// 글자 폭은 글자 종류에 따라 달라 CSS만으로는 못 맞춘다 → 칸 폭이 바뀌거나(카드 크기 · 창 크기) 글자가 바뀔 때 다시 잰다.
// 칸은 줄바꿈 없이(white-space: nowrap) 가로로만 차는 블록이어야 한다. 원래 크기는 CSS가 정하고, 여기서는 줄일 때만 인라인으로 덮는다
const FLOOR = 0.98; // 칸에 꼭 맞으면 서브픽셀 오차로 한 줄이 잘릴 수 있어 살짝 작게

function pixels(value) {
  return Number.parseFloat(value) || 0;
}

export function fitLine(element) {
  const style = getComputedStyle(element);
  const inset = pixels(style.paddingLeft) + pixels(style.paddingRight);
  element.style.fontSize = '';
  const room = element.clientWidth - inset;
  const need = element.scrollWidth - inset;
  if (room <= 0 || need <= room) return;
  element.style.fontSize = `${pixels(getComputedStyle(element).fontSize) * (room / need) * FLOOR}px`;
}

export function fitOneLine(element) {
  let lastWidth = -1;
  const refit = () => {
    lastWidth = element.clientWidth;
    fitLine(element);
  };
  // 글자 크기를 바꾸면 높이도 바뀌어 관찰이 다시 울리므로, 폭이 달라졌을 때만 다시 잰다
  new ResizeObserver(() => { if (element.clientWidth !== lastWidth) refit(); }).observe(element);
  new MutationObserver(refit).observe(element, { childList: true, characterData: true, subtree: true });
  document.fonts?.ready.then(refit);
  return element;
}
