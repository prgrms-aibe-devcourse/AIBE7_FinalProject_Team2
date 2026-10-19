import { button, element } from '../../components/siteLayout.js';
import { ADMIN_ROLE, AUDIT_ACTION, errorText } from './adminLabels.js';
import { PAGE_SIZE, badge, createLoader, dataTable, errorLine, formatDate, labeledField, pageTop, pager, runAction, tabBar } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// 관리자 계정 · 감사 로그 (A-31 ~ A-35). 계정 추가 · 활성 / 비활성, 관리자 행동 기록 열람.
// 비밀번호는 화면에서 다루지 않는다 — 계정을 만들면 서버가 초대 절차로 처리한다(방식은 서버 구현 때 정한다).
const TABS = [{ key: 'accounts', label: '관리자 계정' }, { key: 'audit', label: '감사 로그' }];

export function renderAdminAccountsPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'accounts',
    title: '관리자 계정',
    draw: async ({ api, main, active, unauthorized, admin }) => {
      let tab = 'accounts';
      let auditAction = '';
      let page = 0;
      const tabs = element('div');
      const body = element('div', 'admin-list-body');
      main.replaceChildren(pageTop('관리자 계정'), tabs, body);
      const loader = createLoader({ body, active, unauthorized });
      const ctx = { active, unauthorized };

      function drawTabs() {
        tabs.replaceChildren(tabBar(TABS, tab, (key) => { tab = key; page = 0; drawTabs(); loadTab(); }));
      }
      const loadTab = () => (tab === 'accounts' ? loadAccounts() : loadAudit());

      function addForm() {
        const form = element('form', 'admin-form');
        form.noValidate = true;
        const email = Object.assign(element('input'), { type: 'email', maxLength: 100, autocomplete: 'off' });
        const name = Object.assign(element('input'), { type: 'text', maxLength: 30 });
        const role = element('select');
        Object.entries(ADMIN_ROLE).forEach(([value, text]) => { const option = element('option', '', text); option.value = value; role.append(option); });
        const error = errorLine();
        const submit = element('button', 'primary-button', '계정 추가');
        submit.type = 'submit';
        form.append(labeledField('이메일', email), labeledField('표시 이름', name), labeledField('권한', role), error, submit);
        form.addEventListener('submit', async (event) => {
          event.preventDefault();
          if (!email.value.trim() || !name.value.trim()) { error.textContent = '이메일과 표시 이름을 입력해 주세요.'; return; }
          submit.disabled = true;
          error.textContent = '';
          try {
            await api.createAccount({ email: email.value.trim(), displayName: name.value.trim(), role: role.value });
            if (active()) await loadAccounts();
          } catch (failure) {
            if (!active()) return;
            if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
            error.textContent = errorText(failure, '계정을 추가하지 못했어요.');
            submit.disabled = false;
          }
        });
        const wrap = element('section', 'admin-section');
        wrap.append(element('h2', '', '관리자 추가'), form);
        return wrap;
      }

      function loadAccounts() {
        return loader(() => api.getAccounts(), (data) => {
          const table = dataTable([
            { label: '관리자', cell: (row) => { const wrap = element('span'); wrap.append(element('strong', '', row.displayName), element('span', 'admin-sub', row.email)); return wrap; } },
            { label: '권한', cell: (row) => ADMIN_ROLE[row.role] ?? row.role },
            { label: '상태', cell: (row) => badge(row.active ? '사용 중' : '비활성', row.active ? 'approved' : 'rejected') },
            { label: '최근 접속', cell: (row) => formatDate(row.lastLoginAt) },
            { label: '등록일', cell: (row) => formatDate(row.createdAt) },
            {
              label: '관리',
              cell: (row) => {
                const wrap = element('div');
                const error = errorLine();
                const self = admin && row.email === admin.email;
                const toggle = button(row.active ? '비활성화' : '다시 사용', () => {
                  if (row.active && !window.confirm(`${row.displayName} 계정을 비활성화할까요? 로그인 중이면 바로 로그아웃돼요.`)) return;
                  runAction({ trigger: toggle, errorTarget: error, action: () => api.setAccountActive(row.adminId, !row.active), onDone: loadAccounts, ...ctx });
                });
                toggle.disabled = Boolean(self && row.active); // 자기 자신은 잠글 수 없다
                if (self) toggle.title = '자기 자신의 계정은 비활성화할 수 없어요.';
                wrap.append(toggle, error);
                return wrap;
              },
            },
          ], data.items);
          body.replaceChildren(table, addForm());
        });
      }

      function loadAudit() {
        return loader(() => api.getAuditLogs({ action: auditAction || undefined, page, size: PAGE_SIZE }), (data) => {
          const filter = element('select');
          filter.setAttribute('aria-label', '행동 종류');
          [['', '행동 전체'], ...Object.entries(AUDIT_ACTION)].forEach(([value, text]) => { const option = element('option', '', text); option.value = value; option.selected = value === auditAction; filter.append(option); });
          filter.addEventListener('change', () => { auditAction = filter.value; page = 0; loadAudit(); });
          const bar = element('div', 'admin-search');
          bar.append(filter);
          const table = dataTable([
            { label: '시각', cell: (row) => formatDate(row.createdAt) },
            { label: '관리자', cell: (row) => row.adminEmail },
            { label: '행동', cell: (row) => AUDIT_ACTION[row.action] ?? row.action },
            { label: '대상', cell: (row) => (row.targetType ? `${row.targetType} #${row.targetId}` : '—') },
          ], data.items, '기록이 없어요.');
          body.replaceChildren(bar, table, ...(data.items.length ? [pager(data, (next) => { page = next; loadAudit(); })] : []));
        });
      }

      drawTabs();
      await loadTab();
    },
  });
}
