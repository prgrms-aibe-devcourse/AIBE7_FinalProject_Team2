import { element } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/index.js';

// 로그인 화면 폴리싱용 개발 도구(FE-69): 화면 상태 사이를 바로 넘어가는 버튼 줄. 목 모드에서만 나오고 실제 모드에는 달리지 않는다.
// steps: [{ label, run }] — run은 그 상태를 그린다. 같은 상태를 다시 눌러도 처음부터 다시 그린다
export function devJumpBar(steps) {
  if (API_MODE !== 'mock') return null;
  const bar = element('div', 'login-dev-jump');
  bar.setAttribute('role', 'group');
  bar.setAttribute('aria-label', '개발용 화면 넘어가기');
  bar.append(element('span', 'login-dev-jump-title', '개발용 넘어가기'));
  steps.forEach(({ label, run }) => {
    const step = element('button', 'login-dev-jump-button', label);
    step.type = 'button';
    step.addEventListener('click', run);
    bar.append(step);
  });
  return bar;
}
