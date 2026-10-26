import { button, element } from '../../components/siteLayout.js';
import { POST_STATUS } from './adminLabels.js';
import { PAGE_SIZE, badge, confirmChange, createLoader, dataTable, detailPanel, errorLine, formatDate, pageTop, pager, reasonForm, runAction, searchBar } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// 게시글 · 댓글 관리 (A-21 ~ A-27). 목록 · 검색 → 제목을 누르면 아래에 본문 · 댓글 · 숨김 / 복구 / 삭제.
// 삭제는 되돌릴 수 없는 처리라 사유를 받고 확인을 한 번 더 묻는다(서버에서는 소프트 삭제).
// FE-105: 숨김(글 · 댓글)도 사용자 화면에서 거두는 처리라 확인을 묻는다(확인 기준은 adminKit.js confirmChange). 처리 뒤에는 알림줄로 알린다
const STATUS_KIND = { VISIBLE: 'approved', HIDDEN: 'pending', DELETED: 'rejected' };

export function renderAdminPostsPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'posts',
    title: '게시글 · 댓글',
    draw: async ({ api, main, active, unauthorized, notify }) => {
      let query = {};
      let page = 0;
      const body = element('div', 'admin-list-body');
      const { node: detail, show: showDetail } = detailPanel({
        fetcher: (postId) => api.getPost(postId), draw: (post) => drawDetail(post), failText: '게시글을 불러오지 못했어요.', active, unauthorized,
      });
      const search = searchBar({
        placeholder: '제목 · 작성자 검색',
        filters: [
          { name: 'status', label: '상태 전체', options: Object.entries(POST_STATUS) },
          { name: 'reported', label: '신고 여부 전체', options: [['true', '신고된 글만']] },
        ],
        onSubmit: (values) => { query = { ...values, reported: values.reported === 'true' ? true : undefined }; page = 0; load(); },
      });
      main.replaceChildren(pageTop('게시글 · 댓글'), search, body, detail);
      const loader = createLoader({ body, active, unauthorized });
      const ctx = { active, unauthorized, notify };

      function load() {
        return loader(() => api.getPosts({ ...query, page, size: PAGE_SIZE }), (data) => {
          const table = dataTable([
            { label: '게시글', cell: (row) => { const wrap = element('span'); wrap.append(button(row.title, () => showDetail(row.postId), 'admin-link'), element('span', 'admin-sub', `#${row.postId} · ${row.authorNickname}`)); return wrap; } },
            { label: '상태', cell: (row) => badge(POST_STATUS[row.status] ?? row.status, STATUS_KIND[row.status]) },
            { label: '작성일', cell: (row) => formatDate(row.createdAt) },
            { label: '댓글', cell: (row) => row.commentCount },
            { label: '신고', cell: (row) => (row.reportCount ? `${row.reportCount}건` : '—') },
          ], data.items, '해당하는 게시글이 없어요.', '게시글 목록');
          body.replaceChildren(table, ...(data.items.length ? [pager(data, (next) => { page = next; load(); })] : []));
        });
      }

      function drawDetail(post) {
        const refresh = async () => { await load(); await showDetail(post.postId); };
        const error = errorLine();
        const head = element('div', 'admin-case-top');
        head.append(element('h2', '', post.title), badge(POST_STATUS[post.status] ?? post.status, STATUS_KIND[post.status]));
        const article = element('pre', 'admin-original', post.body); // 회원이 쓴 글이라 textContent로만 넣는다
        const meta = element('p', 'admin-sub', `${post.authorNickname} · ${formatDate(post.createdAt)}${post.hiddenReason ? ` · 처리 사유: ${post.hiddenReason}` : ''}`);
        const actions = element('div', 'admin-actions');
        const forms = [];

        if (post.status === 'VISIBLE') {
          const hide = reasonForm({
            label: '숨김 사유 (200자 이하)', submitText: '숨기기',
            onSubmit: async (reason) => {
              if (!confirmChange(`"${post.title}" 글을 숨길까요?`, '사용자 화면에서 바로 사라져요. 복구로 되돌릴 수 있어요.')) return false;
              await api.hidePost(post.postId, reason);
              await refresh();
              notify('게시글을 숨겼어요.');
              return true;
            },
          });
          actions.append(button('숨기기', () => hide.toggle()));
          forms.push(hide.node);
        }
        if (post.status === 'HIDDEN') {
          const restore = button('복구', () => runAction({ trigger: restore, errorTarget: error, action: () => api.restorePost(post.postId), onDone: refresh, doneText: '게시글을 복구했어요.', ...ctx }), 'primary-button');
          actions.append(restore);
        }
        if (post.status !== 'DELETED') {
          const remove = reasonForm({
            label: '삭제 사유 (200자 이하, 되돌릴 수 없어요)', submitText: '삭제하기',
            onSubmit: async (reason) => {
              if (!confirmChange(`"${post.title}" 글을 삭제할까요?`, '복구할 수 없어요.')) return false;
              await api.deletePost(post.postId, reason);
              await refresh();
              notify('게시글을 삭제했어요.');
              return true;
            },
          });
          actions.append(button('삭제', () => remove.toggle()));
          forms.push(remove.node);
        }

        const comments = element('ul', 'admin-comments');
        post.comments.forEach((comment) => {
          const item = element('li', 'admin-comment');
          const commentError = errorLine();
          const toggle = comment.status === 'VISIBLE'
            ? button('숨기기', () => {
              if (!confirmChange(`${comment.authorNickname} 님의 댓글을 숨길까요?`, '사용자 화면에서 바로 사라져요. 복구로 되돌릴 수 있어요.')) return;
              runAction({ trigger: toggle, errorTarget: commentError, action: () => api.hideComment(comment.commentId), onDone: () => showDetail(post.postId), doneText: '댓글을 숨겼어요.', ...ctx });
            })
            : button('복구', () => runAction({ trigger: toggle, errorTarget: commentError, action: () => api.restoreComment(comment.commentId), onDone: () => showDetail(post.postId), doneText: '댓글을 복구했어요.', ...ctx }));
          toggle.setAttribute('aria-label', `${comment.authorNickname} 님 댓글 ${toggle.textContent}`);
          const line = element('div', 'admin-candidate-head');
          line.append(element('strong', '', comment.authorNickname), badge(comment.status === 'VISIBLE' ? '공개' : '숨김', comment.status === 'VISIBLE' ? 'approved' : 'pending'), element('span', 'admin-sub', `${formatDate(comment.createdAt)}${comment.reportCount ? ` · 신고 ${comment.reportCount}건` : ''}`), toggle);
          item.append(line, element('p', '', comment.content), commentError);
          comments.append(item);
        });

        detail.replaceChildren(head, meta, article, actions, ...forms, error, element('h3', '', `댓글 (${post.comments.length})`), post.comments.length ? comments : element('p', 'admin-sub', '댓글이 없어요.'));
      }

      await load();
    },
  });
}
