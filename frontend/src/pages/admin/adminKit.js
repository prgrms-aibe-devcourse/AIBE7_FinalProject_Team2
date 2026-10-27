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

/**
 * 보기 바꾸기 줄(목록 거르기 · 공지/금칙어 같은 보기 전환). tabs = [{ key, label }], label = 묶음 이름(낭독기용)
 * 누르면 아래 목록을 다시 불러오는 버튼 묶음이라 role=tab 대신 aria-pressed를 쓴다(FE-105: tab은 화살표 키 · 탭 패널을 약속한다)
 */
export function tabBar(tabs, current, onSelect, label = '보기') {
  const bar = element('div', 'admin-tabs');
  bar.setAttribute('role', 'group');
  bar.setAttribute('aria-label', label);
  tabs.forEach((tab) => {
    const node = button(tab.label, () => onSelect(tab.key), `admin-tab${tab.key === current ? ' is-active' : ''}`);
    node.setAttribute('aria-pressed', String(tab.key === current));
    bar.append(node);
  });
  return bar;
}

/**
 * 사용자에게 보이는 상태를 바꾸는 작업의 확인 (FE-105). 확인 기준은 한 가지다:
 * 되돌릴 수 없거나, 사용자가 보던 것을 거두거나(숨김 · 삭제 · 정지 · 비활성화 · 비공개), 사용자에게 새로 공개하는 작업만 묻는다.
 * 되돌리는 작업(복구 · 정지 해제 · 다시 사용)과 운영 기록만 바뀌는 작업(기각 · 고정 · 축 확정)은 묻지 않는다.
 * what = 무엇을 어떻게 하는지 한 줄(대상 이름 포함), effect = 그 결과(선택)
 */
export function confirmChange(what, effect = '') {
  return window.confirm(effect ? `${what}\n${effect}` : what);
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

/** 표. columns = [{ label, cell(row) → 문자열 또는 노드 }], caption = 표 이름(낭독기용, 화면에는 숨김) */
export function dataTable(columns, rows, emptyText = '해당하는 항목이 없어요.', caption = '') {
  if (!rows.length) {
    const empty = element('p', 'admin-message', emptyText);
    empty.setAttribute('role', 'status'); // 검색 · 거르기 결과가 비면 낭독기에도 알린다
    return empty;
  }
  const table = element('table', 'admin-table');
  if (caption) table.append(element('caption', 'admin-visually-hidden', caption));
  const head = element('tr');
  columns.forEach((column) => {
    const th = element('th', '', column.label);
    th.setAttribute('scope', 'col');
    head.append(th);
  });
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
  const box = element('nav', 'admin-pager');
  box.setAttribute('aria-label', '쪽 넘기기');
  const prev = button('이전', () => onPage(data.page - 1));
  prev.disabled = data.page === 0;
  const next = button('다음', () => onPage(data.page + 1));
  next.disabled = data.page + 1 >= pages;
  box.append(prev, element('span', '', `${data.page + 1} / ${pages} (총 ${data.totalElements}건)`), next);
  return box;
}

/** 한 줄 사유를 받는 작은 폼. 접어 두었다가 버튼으로 연다. onSubmit이 false를 돌려주면(확인 취소) 버튼만 되살린다 */
export function reasonForm({ label, submitText, maxLength = 200, extra = [], onSubmit }) {
  const form = element('form', 'admin-reject');
  form.hidden = true;
  form.noValidate = true; // 빈 사유는 아래 문구로 알린다(브라우저 기본 말풍선 대신)
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
      const done = await onSubmit(reason.value.trim(), Object.fromEntries(extra.map((item) => [item.name, item.read()])));
      if (done === false) send.disabled = false;
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
 * @param {{ body: HTMLElement, active: () => boolean, unauthorized: () => void, notFoundText?: string }} ctx 404 문구는 서버에 아직 없는 메뉴 기준이라, 이미 있는 API의 목록은 notFoundText로 바꾼다
 * @returns {(fetcher: () => Promise<any>, draw: (data) => void) => Promise<void>}
 */
export function createLoader({ body, active, unauthorized, notFoundText = '서버에 아직 준비되지 않은 기능이에요.' }) {
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
      panel.append(element('p', '', errorText(failure, failure?.status === 404 ? notFoundText : '불러오지 못했어요.')), button('다시 시도', () => load(fetcher, draw)));
      body.replaceChildren(panel);
    }
  };
}

/** 상태를 바꾸는 버튼의 공통 처리: 잠그고 → 요청 → 성공하면 다시 그리기 + 완료 알림(notify), 실패하면 문구 */
export async function runAction({ trigger, errorTarget, action, onDone, active, unauthorized, notify, doneText }) {
  trigger.disabled = true;
  errorTarget.textContent = '';
  try {
    await action();
    if (!active()) return;
    await onDone();
    if (doneText && active()) notify?.(doneText);
  } catch (failure) {
    if (!active()) return;
    if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
    errorTarget.textContent = errorText(failure, '요청을 처리하지 못했어요.');
    trigger.disabled = false;
  }
}

/**
 * 목록 아래에 펼치는 상세 보기 틀: 불러오는 중 → 그리기 → 오류 문구. 열 때마다 화면 안으로 스크롤하고,
 * 상세 제목(h2)으로 포커스를 옮긴다(목록에서 눌렀을 때 · 처리 뒤 다시 그렸을 때 키보드 위치를 잃지 않게, FE-105).
 * @returns {{ node: HTMLElement, show: (id) => Promise<void> }}
 */
export function detailPanel({ fetcher, draw, failText, active, unauthorized }) {
  const node = element('section', 'admin-section admin-detail');
  node.hidden = true;
  let seq = 0;
  async function show(id) {
    const mine = ++seq; // 나중에 연 상세가 이긴다: 먼저 연 상세의 늦은 응답은 버린다 (createLoader와 같은 방식)
    node.hidden = false;
    const loading = element('p', 'admin-message', '불러오는 중이에요.');
    loading.setAttribute('role', 'status');
    node.replaceChildren(loading);
    try {
      const data = await fetcher(id);
      if (!active() || mine !== seq) return;
      draw(data, node);
      node.scrollIntoView?.({ block: 'nearest' });
      focusHeading(node, 'h2');
    } catch (failure) {
      if (!active() || mine !== seq) return;
      if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
      const error = element('p', 'admin-error', errorText(failure, failText));
      error.setAttribute('role', 'alert');
      node.replaceChildren(error);
    }
  }
  return { node, show };
}

/** 제목으로 포커스를 옮긴다(제목은 Tab 순서에 넣지 않는다). 스크롤은 부른 쪽이 정한다 */
export function focusHeading(scope, selector = 'h1') {
  const heading = scope.querySelector?.(selector);
  if (!heading) return;
  heading.tabIndex = -1;
  heading.focus({ preventScroll: true });
}

/**
 * 표 안 제목 링크: 진짜 주소(href)가 있어 새 탭으로 열기 · 주소 복사가 되고, 그냥 누르면 앱 안에서 이동한다.
 * 보조 키(새 탭 · 새 창)를 누른 채 누르면 브라우저에 맡긴다
 */
export function routeLink(text, href, onOpen) {
  const link = element('a', 'admin-link', text);
  link.href = href;
  link.addEventListener('click', (event) => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    onOpen();
  });
  return link;
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
