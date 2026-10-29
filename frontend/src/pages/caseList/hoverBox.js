// S-02 지면의 호버 상자 (FE-126). 눌러서 어딘가로 가는 칸(기사 · 유형 · 투고 · 광고)은 모두 같은 상자를 띄운다: 칸의 글 · 사진에서 바깥으로 GAP만큼.
// 칸마다 단 사이 여백(padding)이 달라(한쪽만 22px 등) outline으로는 상자가 한쪽으로 쏠리고 크기도 제각각이었다.
// 그래서 마우스를 올릴 때 그 칸의 padding을 재서 상자(.is-pickable::after)의 자리를 정한다(caseList.css)
const GAP = 10;

/** 상자 자리를 지금 칸의 padding에 맞춘다(창 폭에 따라 padding이 바뀌므로 올릴 때마다 잰다) */
export function fitHoverBox(node) {
  const style = getComputedStyle(node);
  const inset = ['Top', 'Right', 'Bottom', 'Left'].map((side) => `${parseFloat(style[`padding${side}`]) - GAP}px`).join(' ');
  node.style.setProperty('--pick-inset', inset);
}

/**
 * 칸 전체를 누를 수 있게 한다(마우스, 호버 상자). 키보드 · 화면 낭독기는 칸 안의 버튼으로 간다
 * @param {HTMLElement} node
 * @param {() => void} go
 */
export function boxLink(node, go) {
  node.classList.add('is-pickable');
  node.addEventListener('mouseenter', () => fitHoverBox(node));
  node.addEventListener('click', (event) => { if (!event.target.closest('button')) go(); });
  return node;
}
