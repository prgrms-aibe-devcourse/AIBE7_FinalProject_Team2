import { button, element } from '../../components/siteLayout.js';
import { errorText } from './adminLabels.js';
import { badge, checkField, createLoader, dataTable, errorLine, formatDate, labeledField, pageTop, runAction, tabBar } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// 공지 · 설정 (A-36 ~ A-41). 공지 작성 · 공개 / 비공개 · 상단 고정, 금칙어 목록.
const TABS = [{ key: 'notices', label: '공지' }, { key: 'words', label: '금칙어' }];

export function renderAdminNoticesPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'notices',
    title: '공지 · 설정',
    draw: async ({ api, main, active, unauthorized }) => {
      let tab = 'notices';
      const tabs = element('div');
      const body = element('div', 'admin-list-body');
      main.replaceChildren(pageTop('공지 · 설정'), tabs, body);
      const loader = createLoader({ body, active, unauthorized });
      const ctx = { active, unauthorized };

      function drawTabs() {
        tabs.replaceChildren(tabBar(TABS, tab, (key) => { tab = key; drawTabs(); loadTab(); }));
      }
      const loadTab = () => (tab === 'notices' ? loadNotices() : loadWords());

      function noticeForm() {
        const form = element('form', 'admin-form');
        form.noValidate = true;
        const title = Object.assign(element('input'), { type: 'text', maxLength: 100 });
        const text = Object.assign(element('textarea'), { rows: 5, maxLength: 2000 });
        const pinned = Object.assign(element('input'), { type: 'checkbox' });
        const published = Object.assign(element('input'), { type: 'checkbox' });
        const error = errorLine();
        const submit = element('button', 'primary-button', '공지 등록');
        submit.type = 'submit';
        form.append(labeledField('제목 (100자 이하)', title), labeledField('내용 (2,000자 이하)', text), checkField('상단에 고정', pinned), checkField('바로 공개', published), error, submit);
        form.addEventListener('submit', async (event) => {
          event.preventDefault();
          if (!title.value.trim() || !text.value.trim()) { error.textContent = '제목과 내용을 입력해 주세요.'; return; }
          submit.disabled = true;
          error.textContent = '';
          try {
            await api.createNotice({ title: title.value.trim(), body: text.value.trim(), pinned: pinned.checked, published: published.checked });
            if (active()) await loadNotices();
          } catch (failure) {
            if (!active()) return;
            if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
            error.textContent = errorText(failure, '공지를 등록하지 못했어요.');
            submit.disabled = false;
          }
        });
        const wrap = element('section', 'admin-section');
        wrap.append(element('h2', '', '새 공지'), form);
        return wrap;
      }

      function loadNotices() {
        return loader(() => api.getNotices(), (data) => {
          const table = dataTable([
            { label: '공지', cell: (row) => { const wrap = element('span'); wrap.append(element('strong', '', row.title), element('span', 'admin-sub', formatDate(row.createdAt))); return wrap; } },
            { label: '상태', cell: (row) => { const wrap = element('span', 'admin-badges'); wrap.append(badge(row.published ? '공개' : '비공개', row.published ? 'approved' : 'pending')); if (row.pinned) wrap.append(badge('고정')); return wrap; } },
            {
              label: '관리',
              cell: (row) => {
                const wrap = element('div', 'admin-actions');
                const error = errorLine();
                const change = (changes, trigger) => runAction({ trigger, errorTarget: error, action: () => api.updateNotice(row.noticeId, changes), onDone: loadNotices, ...ctx });
                const publish = button(row.published ? '비공개로' : '공개하기', () => change({ published: !row.published }, publish));
                const pin = button(row.pinned ? '고정 해제' : '상단 고정', () => change({ pinned: !row.pinned }, pin));
                wrap.append(publish, pin, error);
                return wrap;
              },
            },
          ], data.items, '등록한 공지가 없어요.');
          body.replaceChildren(table, noticeForm());
        });
      }

      function loadWords() {
        return loader(() => api.getBannedWords(), (data) => {
          const list = element('ul', 'admin-words');
          const error = errorLine();
          data.items.forEach((word) => {
            const item = element('li');
            const remove = button('삭제', () => {
              if (!window.confirm(`금칙어 "${word}"를 지울까요?`)) return;
              runAction({ trigger: remove, errorTarget: error, action: () => api.removeBannedWord(word), onDone: loadWords, ...ctx });
            });
            remove.setAttribute('aria-label', `${word} 삭제`);
            item.append(element('span', '', word), remove);
            list.append(item);
          });
          const form = element('form', 'admin-search');
          const input = Object.assign(element('input'), { type: 'text', maxLength: 30, placeholder: '새 금칙어 (30자 이하)' });
          input.setAttribute('aria-label', '새 금칙어');
          const add = element('button', 'primary-button', '추가');
          add.type = 'submit';
          form.append(input, add);
          form.addEventListener('submit', (event) => {
            event.preventDefault();
            if (!input.value.trim()) { error.textContent = '금칙어를 입력해 주세요.'; return; }
            runAction({ trigger: add, errorTarget: error, action: () => api.addBannedWord(input.value.trim()), onDone: loadWords, ...ctx });
          });
          body.replaceChildren(
            element('p', 'admin-sub', '게시글 · 댓글에 이 낱말이 들어 있으면 서버가 등록을 막거나 숨김 후보로 올려요(동작 방식은 서버 구현 때 정해요).'),
            data.items.length ? list : element('p', 'admin-message', '등록한 금칙어가 없어요.'), form, error,
          );
        });
      }

      drawTabs();
      await loadTab();
    },
  });
}
