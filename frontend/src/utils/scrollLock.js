// 팝업이 떠 있는 동안 뒤 화면 스크롤을 잠근다. 잠그면 스크롤바가 사라져 화면 폭이 넓어지며 뒤 지면이 다시 계산돼 움찔하므로,
// 스크롤바가 있던 화면이면 그 자리를 비워 둔다(html.{gutterClass}에 scrollbar-gutter: stable을 CSS로 둔다). 스크롤바가 없던 화면은 건드리지 않는다
export function lockScroll(openClass, gutterClass, win = window, doc = document) {
  const root = doc.documentElement;
  root.classList.toggle(gutterClass, win.innerWidth > root.clientWidth);
  doc.body.classList.add(openClass);
}

export function unlockScroll(openClass, gutterClass, doc = document) {
  doc.body.classList.remove(openClass);
  doc.documentElement.classList.remove(gutterClass);
}
