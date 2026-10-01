// FE-2 공통 진행 표시가 나오면 교체 예정.
export function renderProgressSidebar(container, { lastReviewedStep, openStep }) {
  const aside = document.createElement('aside');
  aside.className = 'progress-sidebar';
  aside.setAttribute('aria-label', '진행 상황');
  const heading = document.createElement('h2');
  heading.textContent = '진행 상황';
  const list = document.createElement('ol');
  const labels = ['사건 개요 · 사전 판단', '상세 사실관계', '양측 주장', '법률 · 양형기준', '판결 전 최종 정리', '판결'];
  labels.forEach((label, index) => {
    const step = index + 1;
    const complete = step <= lastReviewedStep;
    const current = !complete && step === openStep;
    const item = document.createElement('li');
    // 보는 위치는 진행 기호와 별개다. REVIEWED에서도 ④의 완료 기호를 유지한다.
    if (step === (openStep ?? 4)) {
      item.className = 'is-viewing';
      item.setAttribute('aria-current', 'location');
    }
    item.textContent = `${complete ? '✓' : current ? '●' : '○'} ${label}`;
    item.setAttribute('aria-label', `${step}단계 ${label}, ${complete ? '확인 완료' : current ? '현재 단계' : '남은 단계'}`);
    list.append(item);
  });
  const note = document.createElement('p');
  note.textContent = '이전 단계는 접힌 상태로 남아 있어 언제든 다시 펼쳐 볼 수 있습니다.';
  aside.append(heading, list, note);
  container.append(aside);
}
