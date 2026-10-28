// 메인 책상에서 물건을 눌러 온 새 화면의 스크롤 처리(caseListPage · personalityParts가 함께 쓴다).
// 라우터가 이동 직전에 스크롤을 올리지 않고 두고 갔다(deskY > 0): 새 화면은 맨 위로 올리고, 뒤에 남긴 책상(ghost)은 누른 때의 스크롤 자리에 둔다
export function holdDeskScroll(ghost, deskY) {
  if (!(deskY > 0)) return;
  window.scrollTo(0, 0);
  if (ghost) ghost.scrollTop = deskY;
}
