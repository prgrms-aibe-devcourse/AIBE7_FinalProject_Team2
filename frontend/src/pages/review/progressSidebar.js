// 대본이 없는 사건의 대체 화면(2막 재판)에서만 쓴다(FE-98 정리). 대본이 있는 사건은 무대의 막 칩 · 절차 칩이 진행을 보여 준다.
// 아래 단계 이름은 6막 이전 체계라 대본 없는 사건이 생기면 6막 체계로 바꾼다.
export function renderProgressSidebar(container, { lastReviewedStep, openStep }) {
  const aside = document.createElement('aside');
  aside.className = 'progress-sidebar';
  aside.setAttribute('aria-label', '진행 상황');
  const heading = document.createElement('h2');
  heading.textContent = '진행 상황';
  const list = document.createElement('ol');
  const labels = ['첫인상', '상세 사실관계', '양측 주장', '법률 · 양형기준', '사건 요약', '판결'];
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
