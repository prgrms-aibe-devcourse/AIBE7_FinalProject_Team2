import { element } from '../../components/siteLayout.js';

// 책상 위 펠트 패드(FE-69). 메인 책상(S-01)과 같은 나뭇결 위에 메인처럼 가로로 긴 데스크 패드를 놓고, 옆에 펜을 하나 걸쳐 둔다(장식).
// 로그인 · 로그인됨 · 카카오 콜백 화면이 함께 쓴다. 상태가 바뀌면 card 안만 바꾼다
export function loginDesk(container, tag = 'section') {
  const root = element('div', 'login-page');
  const main = element('main', 'login-main');
  const desk = element('div', 'login-desk');
  const card = element(tag, 'login-card');
  const pen = element('span', 'login-pen');
  pen.setAttribute('aria-hidden', 'true');
  desk.append(card, pen);
  main.append(desk);
  root.append(main); // 공통 헤더는 라우터가 붙인다(FE-56)
  container.replaceChildren(root);

  // 내로 · 남불이 패드 뒤에서 위 가장자리 너머로 고개를 내민다(장식). 네이비 내로가 펠트에 묻히지 않게 몸 아래쪽은 패드가 가린다.
  // look은 characters/ 아래 '폴더/표정'(예: 'cheek/idle-sorry'), 없으면 치운다. 고개 내밀 자리는 .login-card-message가 만든다
  function peek(look) {
    desk.querySelectorAll('.login-peek').forEach((node) => node.remove());
    card.classList.toggle('login-card-message', Boolean(look));
    if (!look) return;
    const [folder, face] = look.split('/');
    ['naero', 'nambul'].forEach((who) => {
      const image = element('img', `login-peek peek-${who}`);
      image.src = `/images/characters/${folder}/${who}_${face}.webp`;
      image.alt = '';
      image.width = 800; // 원본 비율(800 × 1000). 높이는 CSS가 정하고, 이미지가 늦게 떠도 자리가 밀리지 않게 비율을 알려 준다
      image.height = 1000;
      image.decoding = 'async';
      desk.prepend(image);
    });
  }
  return { root, main, desk, card, peek };
}
