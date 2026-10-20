// 메인 책상 노트북 → 커뮤니티 (FE-47). 컴퓨터 부팅을 눈 깜빡임으로 표현한다: 화면이 위아래에서 눈을 감듯 어두워지고,
// 감은 동안 커뮤니티로 넘어가 글 목록까지 불러온 뒤(최소 HOLD_MS, 최대 MAX_HOLD_MS) 눈을 뜨듯 위아래로 열리며 커뮤니티가 드러난다.
// 연출 층은 body에 얹어 두므로 화면이 바뀌어도 끝까지 재생되고, 끝나면 스스로 걷힌다. 움직임 줄이기는 부르는 쪽(landingPage.js)이 거른다
const CLOSE_MS = 420; // 눈을 감는 시간
const HOLD_MS = 260; // 감은 채 머무는 최소 시간(짧게 로딩돼도 이만큼은 감고 있는다)
const MAX_HOLD_MS = 1500; // 감은 채 기다리는 최대 시간(서버가 응답하지 않아도 이 뒤에는 눈을 뜬다)
const OPEN_MS = 720; // 눈을 뜨는 시간(감을 때보다 천천히)

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

/** @param {() => (Promise<unknown> | void)} onNavigate 커뮤니티로 이동하는 함수(눈을 감은 동안 부른다). 화면을 다 그리면 풀리는 Promise를 주면 그때까지 감고 기다린다 */
export function bootLaptop(onNavigate) {
  const layer = div('laptop-boot-layer');
  layer.setAttribute('aria-hidden', 'true');
  layer.inert = true;
  const top = div('laptop-boot-lid is-top');
  const bottom = div('laptop-boot-lid is-bottom');
  layer.append(top, bottom);
  document.body.append(layer);

  const lids = [[top, '-101%'], [bottom, '101%']];
  const closing = lids.map(([lid, from]) => lid.animate([{ transform: `translateY(${from})` }, { transform: 'translateY(0)' }],
    { duration: CLOSE_MS, easing: 'cubic-bezier(.5, 0, .75, 0)', fill: 'forwards' }));

  Promise.all(closing.map((animation) => animation.finished)).catch(() => {}).then(() => {
    const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    // 화면이 다 그려질 때까지(실패해도) 기다리되, 최소 HOLD_MS는 감고 있고 MAX_HOLD_MS를 넘기지 않는다
    const drawn = Promise.race([Promise.resolve(onNavigate()).catch(() => {}), wait(MAX_HOLD_MS)]);
    Promise.all([drawn, wait(HOLD_MS)]).then(() => {
      const opening = lids.map(([lid, to]) => lid.animate([{ transform: 'translateY(0)' }, { transform: `translateY(${to})` }],
        { duration: OPEN_MS, easing: 'cubic-bezier(.2, .7, .2, 1)', fill: 'forwards' }));
      Promise.all(opening.map((animation) => animation.finished)).catch(() => {}).then(() => layer.remove());
    });
  });
}
