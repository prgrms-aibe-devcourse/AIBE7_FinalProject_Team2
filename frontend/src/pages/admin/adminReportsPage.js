import { button, element } from '../../components/siteLayout.js';
import { REPORT_ACTION, REPORT_REASON, REPORT_STATUS, TARGET_TYPE } from './adminLabels.js';
import { PAGE_SIZE, badge, createLoader, dataTable, errorLine, formatDate, labeledField, pageTop, pager, reasonForm, tabBar } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// 신고 관리 (A-28 ~ A-30). 처리 대기 → 처리(숨김 · 정지 · 경고) 또는 기각. 처리한 신고는 이력으로 남는다.
const TABS = [{ key: 'PENDING', label: '처리 대기' }, { key: 'RESOLVED', label: '처리 완료' }, { key: 'REJECTED', label: '기각' }, { key: '', label: '전체' }];
const STATUS_KIND = { PENDING: 'pending', RESOLVED: 'approved', REJECTED: 'rejected' };

export function renderAdminReportsPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'reports',
    title: '신고 관리',
    draw: async ({ api, main, active, unauthorized }) => {
      let tab = 'PENDING';
      let targetType = '';
      let page = 0;
      const tabs = element('div');
      const filter = element('select');
      filter.setAttribute('aria-label', '대상 종류');
      [['', '대상 전체'], ...Object.entries(TARGET_TYPE)].forEach(([value, text]) => { const option = element('option', '', text); option.value = value; filter.append(option); });
      filter.addEventListener('change', () => { targetType = filter.value; page = 0; load(); });
      const body = element('div', 'admin-list-body');
      const filterRow = element('div', 'admin-search');
      filterRow.append(filter);
      main.replaceChildren(pageTop('신고 관리'), tabs, filterRow, body);
      const loader = createLoader({ body, active, unauthorized });

      function drawTabs() {
        tabs.replaceChildren(tabBar(TABS, tab, (key) => { tab = key; page = 0; drawTabs(); load(); }));
      }

      function actionCell(row) {
        const cell = element('div', 'admin-report-actions');
        if (row.status !== 'PENDING') {
          cell.append(element('span', 'admin-sub', `${row.action ? REPORT_ACTION[row.action] : '기각'} · ${row.handledBy ?? ''} · ${formatDate(row.handledAt)}`), element('span', 'admin-sub', row.note ?? ''));
          return cell;
        }
        const error = errorLine();
        const choose = element('select');
        choose.setAttribute('aria-label', '처리 방법');
        // 대상 종류에 맞지 않는 처리는 고르지 못하게 한다: 회원 정지는 회원 신고에만, 숨김은 게시글 · 댓글 신고에만
        Object.entries(REPORT_ACTION)
          .filter(([key]) => (key === 'SUSPEND' ? row.targetType === 'MEMBER' : key === 'HIDE' ? row.targetType !== 'MEMBER' : true))
          .forEach(([value, text]) => { const option = element('option', '', text); option.value = value; choose.append(option); });
        const chooseField = labeledField('처리 방법', choose);
        const resolve = reasonForm({ label: '처리 메모 (300자 이하)', submitText: '처리하기', maxLength: 300, extra: [{ name: 'action', node: chooseField, read: () => choose.value }], onSubmit: async (note, { action }) => { await api.resolveReport(row.reportId, { action, note }); await load(); } });
        const reject = reasonForm({ label: '기각 사유 (300자 이하)', submitText: '기각하기', maxLength: 300, onSubmit: async (note) => { await api.rejectReport(row.reportId, note); await load(); } });
        const buttons = element('div', 'admin-actions');
        buttons.append(button('처리', () => { reject.node.hidden = true; resolve.toggle(); }), button('기각', () => { resolve.node.hidden = true; reject.toggle(); }));
        cell.append(buttons, resolve.node, reject.node, error);
        return cell;
      }

      function load() {
        return loader(() => api.getReports({ status: tab || undefined, targetType: targetType || undefined, page, size: PAGE_SIZE }), (data) => {
          const table = dataTable([
            { label: '대상', cell: (row) => { const wrap = element('span'); wrap.append(element('strong', '', `${TARGET_TYPE[row.targetType] ?? row.targetType} #${row.targetId}`), element('span', 'admin-sub', row.targetSummary)); return wrap; } },
            { label: '사유', cell: (row) => { const wrap = element('span'); wrap.append(element('span', '', REPORT_REASON[row.reason] ?? row.reason), element('span', 'admin-sub', row.detail ?? '')); return wrap; } },
            { label: '신고자 · 접수', cell: (row) => `${row.reporter} · ${formatDate(row.createdAt)}` },
            { label: '상태', cell: (row) => badge(REPORT_STATUS[row.status] ?? row.status, STATUS_KIND[row.status]) },
            { label: '처리', cell: actionCell },
          ], data.items, '해당하는 신고가 없어요.');
          body.replaceChildren(table, ...(data.items.length ? [pager(data, (next) => { page = next; load(); })] : []));
        });
      }

      drawTabs();
      await load();
    },
  });
}
