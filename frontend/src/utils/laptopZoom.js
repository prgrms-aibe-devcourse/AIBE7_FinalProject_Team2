// 메인 책상 노트북 → 커뮤니티 (FE-47). 노트북 화면이 기울어진 그 자리에서 똑바로 서며 화면 가득 커지고(베젤째),
// 다 커지면 커뮤니티로 넘어간 뒤 걷힌다. 커뮤니티 화면도 노트북 베젤 테두리 안에 있어 커진 화면이 그대로 이어진다(community.css).
// 연출 층은 body에 얹어 두므로 화면이 바뀌어도 끝까지 재생되고, 끝나면 스스로 걷힌다. 움직임 줄이기는 부르는 쪽(landingPage.js)이 거른다
const ZOOM_MS = 620; // 화면이 커지는 시간(이 끝에 커뮤니티로 이동한다)
const FADE_MS = 260; // 커뮤니티가 그려진 뒤 연출 층이 걷히는 시간

function div(className) {
  const node = document.createElement('div');
  node.className = className;
  return node;
}

// 요소의 회전 각도(도). transform이 없으면 0
function rotation(node) {
  const value = getComputedStyle(node).transform;
  if (!value || value === 'none') return 0;
  const [a, b] = value.match(/matrix\(([^)]+)\)/)?.[1].split(',').map(Number) ?? [1, 0];
  return (Math.atan2(b, a) * 180) / Math.PI;
}

/**
 * @param {HTMLElement} screen 책상 위 노트북 화면(.laptop-screen) @param {number} scale 책상 그림 배율(--desk-scale)
 * @param {() => void} onNavigate 커뮤니티로 이동하는 함수(화면이 다 커졌을 때 부른다)
 */
export function zoomLaptop(screen, scale, onNavigate) {
  const width = window.innerWidth;
  const height = window.innerHeight;
  const box = screen.getBoundingClientRect(); // 기울어진 사각형의 바깥 상자 — 가운데 점은 사각형의 가운데와 같다
  const angle = rotation(screen.parentElement); // 노트북 그림(.laptop-art)의 기울기
  const from = {
    x: box.left + box.width / 2 - width / 2,
    y: box.top + box.height / 2 - height / 2,
    sx: (screen.offsetWidth * scale) / width,
    sy: (screen.offsetHeight * scale) / height,
  };

  const layer = div('laptop-zoom-layer');
  layer.setAttribute('aria-hidden', 'true');
  layer.inert = true;
  const bezel = div('laptop-zoom-bezel');
  bezel.append(div('laptop-zoom-display'));
  layer.append(bezel);
  document.body.append(layer);

  const zoom = bezel.animate([
    { transform: `translate(${from.x}px, ${from.y}px) rotate(${angle}deg) scale(${from.sx}, ${from.sy})`, borderRadius: '40px' },
    { transform: 'translate(0, 0) rotate(0deg) scale(1, 1)', borderRadius: '0px' },
  ], { duration: ZOOM_MS, easing: 'cubic-bezier(.65, 0, .35, 1)', fill: 'forwards' });
  // 화면 속 말풍선 빛(노트북 화면색)이 커지며 커뮤니티 종이색으로 바뀐다
  bezel.firstChild.animate([{ opacity: 0 }, { opacity: 0, offset: 0.35 }, { opacity: 1 }], { duration: ZOOM_MS, easing: 'ease-in', fill: 'forwards' });

  zoom.finished.catch(() => {}).then(() => {
    onNavigate();
    layer.animate([{ opacity: 1 }, { opacity: 0 }], { duration: FADE_MS, delay: 40, easing: 'ease-out', fill: 'forwards' })
      .finished.catch(() => {}).then(() => layer.remove());
  });
}
