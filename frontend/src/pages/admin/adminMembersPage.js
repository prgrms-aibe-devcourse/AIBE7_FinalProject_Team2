import { button, element } from '../../components/siteLayout.js';
import { MEMBER_STATUS, POST_STATUS } from './adminLabels.js';
import { PAGE_SIZE, badge, confirmChange, createLoader, dataTable, detailPanel, errorLine, formatDate, labeledField, pageTop, pager, reasonForm, runAction, searchBar } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// 회원 관리 (A-17 ~ A-20). 목록 · 검색 → 닉네임을 누르면 아래에 상세 · 정지 · 정지 해제.
// FE-105: 정지는 기간까지 적어 확인을 묻고, 정지 해제(되돌리기)는 묻지 않는다(확인 기준은 adminKit.js confirmChange). 처리 뒤 알림줄로 알린다
const STATUS_KIND = { ACTIVE: 'approved', SUSPENDED: 'rejected', WITHDRAWN: '' };
const DURATIONS = [['7', '7일'], ['30', '30일'], ['90', '90일'], ['', '기간 없음 (직접 해제할 때까지)']];

export function renderAdminMembersPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'members',
    title: '회원 관리',
    draw: async ({ api, main, active, unauthorized, notify }) => {
      let query = {};
      let page = 0;
      const body = element('div', 'admin-list-body');
      const { node: detail, show: showDetail } = detailPanel({
        fetcher: (memberId) => api.getMember(memberId), draw: (member) => drawDetail(member), failText: '회원을 불러오지 못했어요.', active, unauthorized,
      });
      const search = searchBar({
        placeholder: '닉네임 · 이메일 검색',
        filters: [{ name: 'status', label: '상태 전체', options: Object.entries(MEMBER_STATUS) }],
        onSubmit: (values) => { query = values; page = 0; load(); },
      });
      main.replaceChildren(pageTop('회원 관리'), search, body, detail);
      const loader = createLoader({ body, active, unauthorized });

      function load() {
        return loader(() => api.getMembers({ ...query, page, size: PAGE_SIZE }), (data) => {
          const table = dataTable([
            { label: '회원', cell: (row) => { const open = button(row.nickname, () => showDetail(row.memberId), 'admin-link'); const wrap = element('span'); wrap.append(open, element('span', 'admin-sub', `#${row.memberId} · ${row.email}`)); return wrap; } },
            { label: '상태', cell: (row) => badge(MEMBER_STATUS[row.status] ?? row.status, STATUS_KIND[row.status]) },
            { label: '가입일', cell: (row) => formatDate(row.joinedAt) },
            { label: '최근 접속', cell: (row) => formatDate(row.lastLoginAt) },
            { label: '체험 · 글', cell: (row) => `${row.experienceCount} · ${row.postCount}` },
            { label: '신고', cell: (row) => (row.reportCount ? `${row.reportCount}건` : '—') },
          ], data.items, '해당하는 회원이 없어요.', '회원 목록');
          body.replaceChildren(table, ...(data.items.length ? [pager(data, (next) => { page = next; load(); })] : []));
        });
      }

      function drawDetail(member) {
        const list = element('dl', 'admin-dl');
        [
          ['닉네임', member.nickname], ['이메일', member.email], ['가입 경로', member.provider], ['상태', MEMBER_STATUS[member.status]],
          ['가입일', formatDate(member.joinedAt)], ['최근 접속', formatDate(member.lastLoginAt)], ['체험 수', member.experienceCount], ['신고 받은 횟수', member.reportCount],
          ...(member.status === 'SUSPENDED' ? [['정지 사유', member.suspendReason], ['정지 해제일', member.suspendedUntil ? formatDate(member.suspendedUntil) : '직접 해제할 때까지']] : []),
        ].forEach(([label, value]) => list.append(element('dt', '', label), element('dd', '', value === null || value === undefined || value === '' ? '—' : String(value))));

        const posts = element('ul', 'admin-factor-reasons');
        member.recentPosts.forEach((post) => posts.append(element('li', '', `${post.title} (${POST_STATUS[post.status] ?? post.status}, ${formatDate(post.createdAt)})`)));

        const actions = element('div', 'admin-actions');
        const error = errorLine();
        const refresh = async () => { await load(); await showDetail(member.memberId); };
        const ctx = { active, unauthorized, notify };
        const parts = [element('h2', '', `${member.nickname} 님`), list, element('h3', '', '최근 게시글'), member.recentPosts.length ? posts : element('p', 'admin-sub', '작성한 글이 없어요.')];

        if (member.status === 'ACTIVE') {
          const duration = element('select');
          DURATIONS.forEach(([value, text]) => { const option = element('option', '', text); option.value = value; duration.append(option); });
          const durationField = labeledField('정지 기간', duration);
          const form = reasonForm({
            label: '정지 사유 (회원에게 보이는 사유가 아니라 운영 기록용, 200자 이하)', submitText: '정지하기', extra: [{ name: 'days', node: durationField, read: () => (duration.value ? Number(duration.value) : null) }],
            onSubmit: async (reason, { days }) => {
              if (!confirmChange(`${member.nickname} 님을 ${days ? `${days}일` : '직접 해제할 때까지'} 정지할까요?`, '정지되면 바로 글 · 댓글을 쓸 수 없게 돼요.')) return false;
              await api.suspendMember(member.memberId, { reason, days });
              await refresh();
              notify(`${member.nickname} 님을 정지했어요.`);
              return true;
            },
          });
          actions.append(button('회원 정지', () => form.toggle()));
          parts.push(actions, form.node);
        } else if (member.status === 'SUSPENDED') {
          const restore = button('정지 해제', () => runAction({ trigger: restore, errorTarget: error, action: () => api.restoreMember(member.memberId), onDone: refresh, doneText: `${member.nickname} 님의 정지를 풀었어요.`, ...ctx }), 'primary-button');
          actions.append(restore);
          parts.push(actions);
        } else {
          parts.push(element('p', 'admin-sub', '탈퇴한 회원이라 처리할 수 있는 작업이 없어요.'));
        }
        parts.push(error);
        detail.replaceChildren(...parts);
      }

      await load();
    },
  });
}
