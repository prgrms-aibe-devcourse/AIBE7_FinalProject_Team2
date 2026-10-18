import { button, element } from '../../components/siteLayout.js';
import { CASE_STATUS, CRIME_TYPE, errorText, formatDateTime } from './adminLabels.js';
import { renderAdminShell } from './adminShell.js';

// S-19 검수 사건 목록 (A-5). 최근 활동 순, 20건씩.
const FILTERS = [
  { key: 'review', label: '검수할 사건', status: 'DRAFT,REVIEW', pending: undefined },
  { key: 'published-pending', label: '공개 사건 · 새 후보', status: 'PUBLISHED', pending: true },
  { key: 'published', label: '공개한 사건', status: 'PUBLISHED', pending: undefined },
];
const SIZE = 20;

export function renderAdminListPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    title: '검수 사건 목록',
    draw: async ({ api, main, active, unauthorized }) => {
      let filter = FILTERS[0];
      let page = 0;

      const heading = element('h1', '', '검수 사건 목록');
      const register = button('+ 사건 등록', () => navigate('adminRegister'), 'primary-button admin-register-button');
      const top = element('div', 'admin-list-top');
      top.append(heading, register);
      const tabs = element('div', 'admin-tabs');
      tabs.setAttribute('role', 'tablist');
      const body = element('div', 'admin-list-body');
      main.replaceChildren(top, tabs, body);

      function drawTabs() {
        tabs.replaceChildren(...FILTERS.map((item) => {
          const tab = button(item.label, () => { filter = item; page = 0; drawTabs(); load(); }, `admin-tab${item === filter ? ' is-active' : ''}`);
          tab.setAttribute('role', 'tab');
          tab.setAttribute('aria-selected', String(item === filter));
          return tab;
        }));
      }

      function pendingText(pending) {
        const parts = [];
        if (pending.aiCandidates) parts.push(`AI 후보 ${pending.aiCandidates}`);
        if (pending.courtCandidates) parts.push(`재판부 후보 ${pending.courtCandidates}`);
        if (pending.axisNeedsReview) parts.push(`축 미확정 ${pending.axisNeedsReview}`);
        return parts.join(' · ') || '없음';
      }

      function drawTable(data) {
        if (!data.items.length) {
          body.replaceChildren(element('p', 'admin-message', '해당하는 사건이 없어요.'));
          return;
        }
        const table = element('table', 'admin-table');
        const head = element('tr');
        ['사건', '상태', '검수 대기', '공개 판결', '최근 활동'].forEach((text) => head.append(element('th', '', text)));
        const thead = element('thead');
        thead.append(head);
        const tbody = element('tbody');
        data.items.forEach((item) => {
          const row = element('tr');
          const titleCell = element('td');
          const open = button(item.title, () => navigate('adminCase', { caseId: item.caseId }), 'admin-link');
          titleCell.append(open, element('span', 'admin-sub', `#${item.caseId} · ${CRIME_TYPE[item.crimeType] ?? item.crimeType}`));
          const published = [item.hasPublishedAi ? 'AI ✓' : 'AI —', item.hasPublishedCourt ? '재판부 ✓' : '재판부 —'].join(' · ');
          row.append(
            titleCell,
            element('td', '', CASE_STATUS[item.status] ?? item.status),
            element('td', '', pendingText(item.pending)),
            element('td', '', published),
            element('td', '', formatDateTime(item.lastActivityAt)),
          );
          tbody.append(row);
        });
        table.append(thead, tbody);

        const pages = Math.max(1, Math.ceil(data.totalElements / SIZE));
        const pager = element('div', 'admin-pager');
        const prev = button('이전', () => { page -= 1; load(); });
        prev.disabled = page === 0;
        const next = button('다음', () => { page += 1; load(); });
        next.disabled = page + 1 >= pages;
        pager.append(prev, element('span', '', `${page + 1} / ${pages} (총 ${data.totalElements}건)`), next);

        const wrap = element('div', 'admin-table-wrap');
        wrap.append(table);
        body.replaceChildren(wrap, pager);
      }

      let seq = 0; // 탭 · 쪽을 빠르게 바꿀 때 늦게 온 이전 응답이 지금 표를 덮지 않게 한다
      async function load() {
        const mine = ++seq;
        const loading = element('p', 'admin-message', '불러오는 중이에요.');
        loading.setAttribute('role', 'status');
        body.replaceChildren(loading);
        try {
          const data = await api.getCases({ status: filter.status, pending: filter.pending, page, size: SIZE });
          if (active() && mine === seq) drawTable(data);
        } catch (failure) {
          if (!active() || mine !== seq) return;
          if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
          const panel = element('div', 'admin-message');
          panel.setAttribute('role', 'alert');
          panel.append(element('p', '', errorText(failure, '목록을 불러오지 못했어요.')), button('다시 시도', load));
          body.replaceChildren(panel);
        }
      }

      drawTabs();
      await load();
    },
  });
}
