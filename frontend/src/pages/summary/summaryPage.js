// S-05 판결 전 최종 정리(4막). FE-48: 내용(사건 개요 · 핵심 사실)은 5막 파일철 첫 장 "사건 요약"으로 옮겼다.
// 서버 진행 상태(REVIEWED → S-05)와 주소는 그대로 두고, 이 화면에 오면 기록을 바꿔 판결 입력(S-06)으로 바로 넘긴다.
// 막 칩 줄의 "4막"도 남겨 둔다(주소 이름을 바꾸고 막을 다시 묶는 방식은 추후 검토).
export async function renderSummaryPage(container, { caseId, api, navigate }) {
  const root = document.createElement('div');
  root.className = 'summary-page';
  container.replaceChildren(root);
  const active = () => container.contains(root);
  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function message(text, action) {
    const panel = element('section', 'message-panel');
    panel.setAttribute('role', 'status');
    panel.append(element('p', '', text));
    if (action) panel.append(action);
    root.replaceChildren(panel);
  }
  let loading = false;
  async function load() {
    if (loading || !active()) return;
    loading = true;
    message('판결 입력으로 넘어가는 중…');
    try {
      const data = await api.getReview(caseId);
      if (!active()) return;
      if (data.status === 'REVIEWED') { navigate('verdict', {}, { replace: true }); return; }
      const back = element('button', 'secondary-button', '사건 정보 확인으로');
      back.type = 'button';
      back.addEventListener('click', () => navigate('review'));
      message('사건 정보를 모두 확인한 뒤 볼 수 있어요.', back);
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'INVALID_STATE') {
        message(`지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`);
        console.info('현재 상태:', error.currentStatus);
      } else {
        const retry = element('button', 'secondary-button', '다시 시도');
        retry.type = 'button';
        retry.addEventListener('click', load);
        message('정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', retry);
      }
    } finally {
      loading = false;
    }
  }
  await load();
}
