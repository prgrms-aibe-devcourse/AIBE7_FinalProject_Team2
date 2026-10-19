// 관리자 화면(회원 · 게시글 · 신고 · 관리자 계정 · 공지 · 사건 등록 · 사건 검수)이 함께 쓰는 작은 부품.
// 값은 모두 textContent로 넣는다(회원이 쓴 글에 HTML이 섞여도 실행되지 않게).
import { button, element } from '../../components/siteLayout.js';
import { errorText } from './adminLabels.js';

export const PAGE_SIZE = 20;

export function errorLine() {
  const line = element('p', 'admin-error');
  line.setAttribute('role', 'alert');
  return line;
}

export function pageTop(title, ...actions) {
  const top = element('div', 'admin-list-top');
  top.append(element('h1', '', title), ...actions);
  return top;
}

export function badge(text, kind = '') {
  return element('span', `admin-badge${kind ? ` is-${kind}` : ''}`, text);
}

export { formatDate } from './adminLabels.js'; // 날짜 표시는 adminLabels.js 한 곳(formatDateTime과 함께)

/** 탭 줄. tabs = [{ key, label }] */
export function tabBar(tabs, current, onSelect) {
  const bar = element('div', 'admin-tabs');
  bar.setAttribute('role', 'tablist');
  tabs.forEach((tab) => {
    const node = button(tab.label, () => onSelect(tab.key), `admin-tab${tab.key === current ? ' is-active' : ''}`);
    node.setAttribute('role', 'tab');
    node.setAttribute('aria-selected', String(tab.key === current));
    bar.append(node);
  });
  return bar;
}

/**
 * 검색줄. filters = [{ name, label, options: [[value, text]] }]. 제출하면 onSubmit({ keyword, ...필터값 })
 * 값은 빈 문자열이면 빼고 넘긴다.
 */
export function searchBar({ placeholder, filters = [], onSubmit }) {
  const form = element('form', 'admin-search');
  const keyword = Object.assign(element('input'), { type: 'search', placeholder, maxLength: 100 });
  keyword.setAttribute('aria-label', placeholder);
  form.append(keyword);
  const selects = filters.map((filter) => {
    const select = element('select');
    select.name = filter.name;
    select.setAttribute('aria-label', filter.label);
    [['', filter.label], ...filter.options].forEach(([value, text]) => {
      const option = element('option', '', text);
      option.value = value;
      select.append(option);
    });
    form.append(select);
    return select;
  });
  const submit = element('button', 'primary-button', '검색');
  submit.type = 'submit';
  form.append(submit);
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const values = { keyword: keyword.value.trim() };
    selects.forEach((select) => { values[select.name] = select.value; });
    onSubmit(Object.fromEntries(Object.entries(values).filter(([, value]) => value !== '')));
  });
  return form;
}

/** 표. columns = [{ label, cell(row) → 문자열 또는 노드 }] */
export function dataTable(columns, rows, emptyText = '해당하는 항목이 없어요.') {
  if (!rows.length) return element('p', 'admin-message', emptyText);
  const table = element('table', 'admin-table');
  const head = element('tr');
  columns.forEach((column) => head.append(element('th', '', column.label)));
  const thead = element('thead');
  thead.append(head);
  const tbody = element('tbody');
  rows.forEach((row) => {
    const tr = element('tr');
    columns.forEach((column) => {
      const td = element('td');
      const value = column.cell(row);
      if (value instanceof Node) td.append(value);
      else td.textContent = value === null || value === undefined || value === '' ? '—' : String(value);
      tr.append(td);
    });
    tbody.append(tr);
  });
  table.append(thead, tbody);
  const wrap = element('div', 'admin-table-wrap');
  wrap.append(table);
  return wrap;
}

export function pager(data, onPage) {
  const pages = Math.max(1, Math.ceil(data.totalElements / data.size));
  const box = element('div', 'admin-pager');
  const prev = button('이전', () => onPage(data.page - 1));
  prev.disabled = data.page === 0;
  const next = button('다음', () => onPage(data.page + 1));
  next.disabled = data.page + 1 >= pages;
  box.append(prev, element('span', '', `${data.page + 1} / ${pages} (총 ${data.totalElements}건)`), next);
  return box;
}

/** 한 줄 사유를 받는 작은 폼. 접어 두었다가 버튼으로 연다 */
export function reasonForm({ label, submitText, maxLength = 200, extra = [], onSubmit }) {
  const form = element('form', 'admin-reject');
  form.hidden = true;
  const reason = Object.assign(element('textarea'), { rows: 2, maxLength, required: true });
  const field = labeledField(label, reason);
  const error = errorLine();
  const send = element('button', 'secondary-button', submitText);
  send.type = 'submit';
  form.append(field, ...extra.map((item) => item.node), error, send);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!reason.value.trim()) { error.textContent = '사유를 입력해 주세요.'; return; }
    send.disabled = true;
    error.textContent = '';
    try {
      await onSubmit(reason.value.trim(), Object.fromEntries(extra.map((item) => [item.name, item.read()])));
    } catch (failure) {
      error.textContent = failure.message ?? errorText(failure);
      send.disabled = false;
    }
  });
  return {
    node: form,
    toggle() { form.hidden = !form.hidden; if (!form.hidden) reason.focus(); },
  };
}

/**
 * 목록 화면의 공통 흐름: 불러오는 중 → 표 → 오류(다시 시도). 탭 · 쪽을 빠르게 바꿀 때 늦게 온 응답이 지금 화면을 덮지 않게 한다.
 * @param {{ body: HTMLElement, active: () => boolean, unauthorized: () => void }} ctx
 * @returns {(fetcher: () => Promise<any>, draw: (data) => void) => Promise<void>}
 */
export function createLoader({ body, active, unauthorized }) {
  let seq = 0;
  return async function load(fetcher, draw) {
    const mine = ++seq;
    const loading = element('p', 'admin-message', '불러오는 중이에요.');
    loading.setAttribute('role', 'status');
    body.replaceChildren(loading);
    try {
      const data = await fetcher();
      if (active() && mine === seq) draw(data);
    } catch (failure) {
      if (!active() || mine !== seq) return;
      if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
      const panel = element('div', 'admin-message');
      panel.setAttribute('role', 'alert');
      panel.append(element('p', '', errorText(failure, failure?.status === 404 ? '서버에 아직 준비되지 않은 기능이에요.' : '불러오지 못했어요.')), button('다시 시도', () => load(fetcher, draw)));
      body.replaceChildren(panel);
    }
  };
}

/** 상태를 바꾸는 버튼의 공통 처리: 잠그고 → 요청 → 성공하면 다시 그리기, 실패하면 문구 */
export async function runAction({ trigger, errorTarget, action, onDone, active, unauthorized }) {
  trigger.disabled = true;
  errorTarget.textContent = '';
  try {
    await action();
    if (active()) await onDone();
  } catch (failure) {
    if (!active()) return;
    if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
    errorTarget.textContent = errorText(failure, '요청을 처리하지 못했어요.');
    trigger.disabled = false;
  }
}

/**
 * 목록 아래에 펼치는 상세 보기 틀: 불러오는 중 → 그리기 → 오류 문구. 열 때마다 화면 안으로 스크롤한다.
 * @returns {{ node: HTMLElement, show: (id) => Promise<void> }}
 */
export function detailPanel({ fetcher, draw, failText, active, unauthorized }) {
  const node = element('section', 'admin-section admin-detail');
  node.hidden = true;
  let seq = 0;
  async function show(id) {
    const mine = ++seq; // 나중에 연 상세가 이긴다: 먼저 연 상세의 늦은 응답은 버린다 (createLoader와 같은 방식)
    node.hidden = false;
    node.replaceChildren(element('p', 'admin-message', '불러오는 중이에요.'));
    try {
      const data = await fetcher(id);
      if (!active() || mine !== seq) return;
      draw(data, node);
      node.scrollIntoView?.({ block: 'nearest' });
    } catch (failure) {
      if (!active() || mine !== seq) return;
      if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
      node.replaceChildren(element('p', 'admin-error', errorText(failure, failText)));
    }
  }
  return { node, show };
}

let hintSeq = 0;

/** 라벨 + 입력칸. hint가 있으면 입력칸의 aria-describedby로 이어 준다 */
export function labeledField(text, control, hint) {
  const label = element('label', 'admin-field');
  label.append(element('span', '', text), control);
  if (hint) {
    const note = element('small', '', hint);
    note.id = `admin-hint-${++hintSeq}`;
    control.setAttribute('aria-describedby', [control.getAttribute('aria-describedby'), note.id].filter(Boolean).join(' ')); // 이미 이어 둔 설명은 지우지 않는다
    label.append(note);
  }
  return label;
}

/** 체크박스 + 라벨 */
export function checkField(text, control) {
  const label = element('label', 'admin-check');
  label.append(control, element('span', '', text));
  return label;
}
