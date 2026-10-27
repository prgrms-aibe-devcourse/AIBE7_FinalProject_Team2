import { element } from '../../components/siteLayout.js';

// 로그인(S-12) · 회원가입이 함께 쓰는 카카오 버튼과 요청 중 표시 (FE-104)
const KAKAO_SVG = '<svg viewBox="0 0 18 18" aria-hidden="true" focusable="false"><path d="M9 1.6C4.6 1.6 1 4.4 1 7.9c0 2.2 1.5 4.2 3.7 5.3l-.9 3.4c-.1.3.3.6.5.4l4.1-2.7.6.1c4.4 0 8-2.8 8-6.3S13.4 1.6 9 1.6Z"/></svg>';

// 카카오 디자인 가이드 문구는 "카카오 로그인" · "카카오로 시작하기" 중에서 고른다
export function kakaoButton(label, onClick) {
  const kakao = element('button', 'kakao-button');
  kakao.type = 'button';
  kakao.innerHTML = KAKAO_SVG;
  kakao.append(element('span', '', label));
  kakao.addEventListener('click', onClick);
  return kakao;
}

// 요청 중에는 disabled 대신 aria-disabled를 쓴다: disabled가 되면 눌렀던 버튼의 포커스가 사라져 키보드 사용자가 처음부터 다시 Tab을 눌러야 한다.
// 누름은 화면의 pending 검사가 막고, 모양은 login.css의 [aria-disabled="true"]가 맡는다
// 요청 중에는 누른 버튼(pressed)의 글자를 busyLabel로 바꿔 기다리는 중임을 보인다(카카오는 글자 span만)
export function setBusy(nodes, busy, pressed = null, busyLabel = '로그인하는 중…') {
  nodes.forEach((node) => {
    const label = node.querySelector('span') ?? node;
    if (busy) {
      node.setAttribute('aria-disabled', 'true');
      if (node !== pressed) return;
      label.dataset.label ??= label.textContent;
      label.textContent = busyLabel;
    } else {
      node.removeAttribute('aria-disabled');
      if (label.dataset.label) label.textContent = label.dataset.label;
    }
  });
}
