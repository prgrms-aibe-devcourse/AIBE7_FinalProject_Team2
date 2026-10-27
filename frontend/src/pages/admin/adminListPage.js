import { button, element } from '../../components/siteLayout.js';
import { CASE_STATUS, CRIME_TYPE, formatDateTime } from './adminLabels.js';
import { createLoader, dataTable, pager, routeLink, tabBar } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// S-19 검수 사건 목록 (A-5). 최근 활동 순, 20건씩.
// FE-105: 거르기 줄 · 표 · 쪽 넘기기 · 불러오기는 다른 관리자 목록과 같은 공통 부품(adminKit.js)을 쓴다. 사건 제목은 진짜 링크(새 탭으로 열기)
const FILTERS = [
  { key: 'review', label: '검수할 사건', status: 'DRAFT,REVIEW', pending: undefined, hint: '아직 공개하지 않은 사건(적재됨 · 검수 중)이에요.' },
  { key: 'published-pending', label: '공개 사건 · 새 후보', status: 'PUBLISHED', pending: true, hint: '공개한 사건 가운데 새 판결 후보가 들어왔거나 가치관 축이 확정되지 않은 사건이에요.' },
  { key: 'published', label: '공개한 사건', status: 'PUBLISHED', pending: undefined, hint: '사용자에게 보이는 사건 전체예요.' },
];
const SIZE = 20;

export function renderAdminListPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'cases',
    title: '검수 사건 목록',
    draw: async ({ api, main, active, unauthorized }) => {
      let filter = FILTERS[0];
      let page = 0;

      const heading = element('h1', '', '검수 사건 목록');
      const register = button('+ 사건 등록', () => navigate('adminRegister'), 'primary-button admin-register-button');
      const top = element('div', 'admin-list-top');
      top.append(heading, register);
      const tabs = element('div');
      const hint = element('p', 'admin-sub admin-filter-hint');
      const body = element('div', 'admin-list-body');
      main.replaceChildren(top, tabs, hint, body);
      const load = createLoader({ body, active, unauthorized, notFoundText: '사건 목록을 찾지 못했어요. 주소나 서버 연결을 확인해 주세요.' }); // 서버에 있는 API라 "준비 안 됨"으로 말하지 않는다

      function drawTabs() {
        tabs.replaceChildren(tabBar(FILTERS, filter.key, (key) => {
          filter = FILTERS.find((item) => item.key === key);
          page = 0;
          drawTabs();
          show();
        }, '사건 거르기'));
        hint.textContent = filter.hint;
      }

      function pendingText(pending) {
        const parts = [];
        if (pending.aiCandidates) parts.push(`AI 후보 ${pending.aiCandidates}`);
        if (pending.courtCandidates) parts.push(`재판부 후보 ${pending.courtCandidates}`);
        if (pending.axisNeedsReview) parts.push(`축 미확정 ${pending.axisNeedsReview}`);
        return parts.join(' · ') || '없음';
      }

      function show() {
        return load(() => api.getCases({ status: filter.status, pending: filter.pending, page, size: SIZE }), (data) => {
          const table = dataTable([
            {
              label: '사건',
              cell: (item) => {
                const wrap = element('span');
                wrap.append(
                  routeLink(item.title, `/admin/cases/${item.caseId}`, () => navigate('adminCase', { caseId: item.caseId })),
                  element('span', 'admin-sub', `#${item.caseId} · ${CRIME_TYPE[item.crimeType] ?? item.crimeType}`),
                );
                return wrap;
              },
            },
            { label: '상태', cell: (item) => CASE_STATUS[item.status] ?? item.status },
            { label: '검수 대기', cell: (item) => pendingText(item.pending) },
            { label: '공개 판결', cell: (item) => [item.hasPublishedAi ? 'AI ✓' : 'AI —', item.hasPublishedCourt ? '재판부 ✓' : '재판부 —'].join(' · ') },
            { label: '최근 활동', cell: (item) => formatDateTime(item.lastActivityAt) },
          ], data.items, '해당하는 사건이 없어요.', `${filter.label} 목록`);
          body.replaceChildren(table, ...(data.items.length ? [pager({ ...data, page, size: SIZE }, (next) => { page = next; show(); })] : []));
        });
      }

      drawTabs();
      await show();
    },
  });
}
