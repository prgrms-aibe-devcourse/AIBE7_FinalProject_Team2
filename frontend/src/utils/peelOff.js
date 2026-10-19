// 포스트잇 떼기 (FE-50): 눌린 포스트잇이 날아가며 사라진다. 메인 책상의 내로 포스트잇과 S-02 남불 포스트잇이 함께 쓴다.
// 포커스가 있던 버튼이 사라지면 포커스를 잃으므로, 비활성화하기 *전에* 포커스 여부를 보고 대신 받을 곳으로 옮긴다
import { reducedMotion } from './motion.js';

export const PEEL_MS = 520;

// from · to = transform 값(날아가기 전 · 후). focusTarget() = 포커스를 대신 받을 요소(없으면 null을 돌려 주면 안 옮긴다).
export async function peelOff(node, { from, to, focusTarget }) {
  const hadFocus = document.activeElement === node;
  node.disabled = true;
  if (hadFocus) focusTarget?.()?.focus({ preventScroll: true });
  if (!reducedMotion()) {
    await node.animate(
      [{ transform: from }, { transform: to, opacity: 0 }],
      { duration: PEEL_MS, easing: 'cubic-bezier(.4, 0, .7, .2)', fill: 'forwards' },
    ).finished.catch(() => {});
  }
  node.remove();
}
