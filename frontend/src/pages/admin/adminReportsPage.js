import { button, element } from '../../components/siteLayout.js';
import { REPORT_ACTION, REPORT_REASON, REPORT_STATUS, TARGET_TYPE } from './adminLabels.js';
import { PAGE_SIZE, badge, confirmChange, createLoader, dataTable, errorLine, focusHeading, formatDate, labeledField, pageTop, pager, reasonForm, tabBar } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// 신고 관리 (A-28 ~ A-30). 처리 대기 → 처리(숨김 · 정지 · 경고) 또는 기각. 처리한 신고는 이력으로 남는다.
// FE-105: 같은 대상에 들어온 신고가 이 쪽에 여럿이면 "같은 대상 신고 N건"으로 알린다(한 번에 처리하는 API는 아직 없다).
// 숨김 · 정지는 처리 전에 대상을 적어 확인하고, 처리한 뒤에는 알림줄로 알리고 다음 신고(없으면 제목)로 포커스를 옮긴다
const ACTION_EFFECT = { HIDE: '숨김 처리할까요? 사용자 화면에서 바로 사라져요.', SUSPEND: '정지할까요? 이 회원은 바로 글을 쓸 수 없게 돼요.' };
const TABS = [{ key: 'PENDING', label: '처리 대기' }, { key: 'RESOLVED', label: '처리 완료' }, { key: 'REJECTED', label: '기각' }, { key: '', label: '전체' }];
const STATUS_KIND = { PENDING: 'pending', RESOLVED: 'approved', REJECTED: 'rejected' };

export function renderAdminReportsPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'reports',
    title: '신고 관리',
    draw: async ({ api, main, active, unauthorized, notify }) => {
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
        tabs.replaceChildren(tabBar(TABS, tab, (key) => { tab = key; page = 0; drawTabs(); load(); }, '신고 상태'));
      }

      // 처리 · 기각 뒤: 목록을 다시 불러와 같은 순서의 다음 신고 "처리" 버튼으로(없으면 제목으로) 포커스를 옮긴다
      async function afterHandled(index, text) {
        await load();
        if (!active()) return;
        notify(text);
        const next = [...body.querySelectorAll('.admin-report-open')][index];
        if (next) next.focus(); else focusHeading(main);
      }

      function actionCell(row, index) {
        const cell = element('div', 'admin-report-actions');
        const target = `${TARGET_TYPE[row.targetType] ?? row.targetType} #${row.targetId}`;
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
        const resolve = reasonForm({
          label: '처리 메모 (300자 이하)', submitText: '처리하기', maxLength: 300, extra: [{ name: 'action', node: chooseField, read: () => choose.value }],
          onSubmit: async (note, { action }) => {
            if (ACTION_EFFECT[action] && !confirmChange(`${target}을(를) ${ACTION_EFFECT[action]}`)) return false;
            await api.resolveReport(row.reportId, { action, note });
            await afterHandled(index, `${target} 신고를 처리했어요(${REPORT_ACTION[action]}).`);
            return true;
          },
        });
        // 기각은 대상에 손대지 않고 신고 기록만 닫는다(확인 없이)
        const reject = reasonForm({ label: '기각 사유 (300자 이하)', submitText: '기각하기', maxLength: 300, onSubmit: async (note) => { await api.rejectReport(row.reportId, note); await afterHandled(index, `${target} 신고를 기각했어요.`); } });
        const open = button('처리', () => { reject.node.hidden = true; resolve.toggle(); }, 'secondary-button admin-report-open');
        open.setAttribute('aria-label', `${target} 신고 처리`);
        const dismiss = button('기각', () => { resolve.node.hidden = true; reject.toggle(); });
        dismiss.setAttribute('aria-label', `${target} 신고 기각`);
        const buttons = element('div', 'admin-actions');
        buttons.append(open, dismiss);
        cell.append(buttons, resolve.node, reject.node, error);
        return cell;
      }

      function load() {
        return loader(() => api.getReports({ status: tab || undefined, targetType: targetType || undefined, page, size: PAGE_SIZE }), (data) => {
          const sameTarget = (row) => data.items.filter((other) => other.status === row.status && other.targetType === row.targetType && other.targetId === row.targetId).length;
          const pending = data.items.filter((row) => row.status === 'PENDING');
          const table = dataTable([
            {
              label: '대상',
              cell: (row) => {
                const wrap = element('span');
                wrap.append(element('strong', '', `${TARGET_TYPE[row.targetType] ?? row.targetType} #${row.targetId}`), element('span', 'admin-sub', row.targetSummary));
                const count = sameTarget(row);
                if (row.status === 'PENDING' && count > 1) wrap.append(badge(`같은 대상 신고 ${count}건`, 'pending'));
                return wrap;
              },
            },
            { label: '사유', cell: (row) => { const wrap = element('span'); wrap.append(element('span', '', REPORT_REASON[row.reason] ?? row.reason), element('span', 'admin-sub', row.detail ?? '')); return wrap; } },
            { label: '신고자 · 접수', cell: (row) => `${row.reporter} · ${formatDate(row.createdAt)}` },
            { label: '상태', cell: (row) => badge(REPORT_STATUS[row.status] ?? row.status, STATUS_KIND[row.status]) },
            { label: '처리', cell: (row) => actionCell(row, pending.indexOf(row)) },
          ], data.items, '해당하는 신고가 없어요.', '신고 목록');
          body.replaceChildren(table, ...(data.items.length ? [pager(data, (next) => { page = next; load(); })] : []));
        });
      }

      drawTabs();
      await load();
    },
  });
}
