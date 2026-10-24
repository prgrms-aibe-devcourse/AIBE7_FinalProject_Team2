import { button, element } from '../../components/siteLayout.js';
import {
  NOT_READY, SORT_LABELS, communityShell, composer, filterControls, icon, likeButton, loginHint, popularList, postImages, postLink, postMeta, verdictCard,
} from './communityParts.js';

// S-18 커뮤니티 목록 (FE-47). 메인 책상의 노트북에서 들어온다(utils/laptopBoot.js).
// 가운데 한 줄 타임라인(정렬 · 글 · 더 보기), 왼쪽 말머리 · 사건 거르기, 오른쪽 늘 열려 있는 글쓰기 칸.
// 거르기 값은 기록 항목에 넣어 두어(history.state) 상세에서 뒤로 오면 같은 목록이 보인다. 바꿀 때는 화면을 다시 그리지 않아 쓰던 글이 남는다
const DEFAULT_FILTERS = { category: null, caseId: null, sort: 'LATEST' };

// caseId는 라우터가 사건 경로용으로 덮어쓰므로 사건 거르기 값은 filterCaseId로 받는다(PR #200 리뷰 반영)
export function renderCommunityPage(container, { api, navigate, category = null, filterCaseId: caseId = null, sort = 'LATEST', justPosted = null }) {
  const shell = communityShell({ navigate });
  const { root, filters: filterHost, main, side, openButton, closeButton, closeCompose } = shell;
  container.replaceChildren(root);
  const active = () => container.contains(root);

  if (!api.getCommunityPosts) {
    main.append(element('p', 'cm-note', NOT_READY));
    return undefined;
  }

  let filters = { category, caseId, sort: SORT_LABELS[sort] ? sort : 'LATEST' };
  let page = 1;
  let token = 0;
  const saveFilters = () => {
    const { caseId: filterCaseId, ...rest } = filters;
    history.replaceState({ ...history.state, extras: { ...rest, filterCaseId } }, '');
  };
  if (justPosted) saveFilters(); // 새로고침 · 뒤로 가기로 새 글 표시를 되풀이하지 않는다

  const toolbar = element('div', 'cm-toolbar');
  const count = element('p', 'cm-count-line');
  count.setAttribute('aria-live', 'polite');
  const sorts = element('div', 'cm-sorts');
  sorts.setAttribute('role', 'group');
  sorts.setAttribute('aria-label', '정렬');
  toolbar.append(count, sorts, openButton);
  const timeline = element('div', 'cm-timeline');
  const more = element('div', 'cm-more');
  main.append(element('h2', 'visually-hidden', '글 목록'), toolbar, timeline, more);
  const loading = element('p', 'cm-note', '글을 불러오고 있어요…');
  loading.setAttribute('role', 'status');
  timeline.append(loading);

  // 정렬 칩은 한 번만 만들고 눌린 표시만 바꾼다(다시 만들면 키보드 포커스가 사라진다, PR #200 리뷰 반영)
  function paintSorts() {
    if (!sorts.children.length) {
      sorts.append(...Object.entries(SORT_LABELS).map(([key, label]) => {
        const chip = button(label, () => change({ ...filters, sort: key }), 'cm-chip');
        chip.dataset.sort = key;
        return chip;
      }));
    }
    for (const chip of sorts.children) chip.setAttribute('aria-pressed', String(filters.sort === chip.dataset.sort));
  }

  function change(next) {
    filters = next;
    page = 1;
    saveFilters();
    load();
  }

  let caseList = null; // 고를 수 있는 사건(첫 응답에서 받는다)
  async function load({ append = false } = {}) {
    const mine = ++token;
    paintSorts();
    more.replaceChildren();
    let data;
    try {
      data = await api.getCommunityPosts({ ...filters, page });
    } catch {
      if (!active() || mine !== token) return;
      timeline.replaceChildren(failure());
      return;
    }
    if (!active() || mine !== token) return;
    if (!caseList) {
      caseList = data.cases;
      filterHost.append(filterControls({ value: filters, cases: caseList, onChange: onFilter }));
      side.append(composer({ api, navigate, cases: data.cases, closeButton, closeCompose, onPosted: (post) => { closeCompose(); navigate('community', { justPosted: post.postId }); } }),
        popularList(data.popular, navigate));
    }
    count.textContent = `글 ${data.total}개`;
    const firstNew = append ? timeline.children.length : 0;
    timeline.replaceChildren(...data.posts.map((post) => postItem(post)));
    if (!data.posts.length) timeline.append(emptyState());
    if (data.hasMore) {
      more.append(button('글 더 보기', () => { page += 1; load({ append: true }); }, 'secondary-button cm-more-button'));
    }
    // 더 보기: 새로 붙은 첫 글로 포커스를 옮겨 이어 읽게 한다
    if (append) timeline.children[firstNew]?.querySelector('h3 a')?.focus();
    if (justPosted) {
      const fresh = timeline.querySelector(`[data-post-id="${justPosted}"]`);
      justPosted = null;
      if (fresh) {
        fresh.classList.add('is-new');
        fresh.querySelector('h3 a')?.focus({ preventScroll: true });
        fresh.scrollIntoView({ block: 'center' });
      }
    }
  }

  // 거르기를 바꾸면 눌린 표시를 다시 그리고, 포커스는 방금 조작한 칸에 둔다
  function onFilter(value, { refocus = true } = {}) {
    const usedSelect = value.caseId !== filters.caseId;
    change({ ...filters, ...value });
    filterHost.querySelector('.cm-filter-groups')?.replaceWith(filterControls({ value: filters, cases: caseList ?? [], onChange: onFilter }));
    if (refocus) filterHost.querySelector(usedSelect ? '.cm-case-clear, .cm-case-search' : '.cm-filter[aria-pressed="true"]')?.focus({ preventScroll: true });
  }

  function postItem(post) {
    const item = element('article', 'cm-post');
    item.dataset.postId = String(post.postId);
    item.setAttribute('aria-labelledby', `cm-post-${post.postId}`);
    const title = element('h3', 'cm-post-title');
    const link = postLink(post.title, post.postId, navigate);
    link.id = `cm-post-${post.postId}`;
    title.append(link);
    item.append(postMeta(post), title, element('p', 'cm-excerpt', post.body.replace(/\s*\n+\s*/g, ' '))); // 목록에서는 문단을 한 줄로 이어 세 줄까지 보인다
    if (post.images.length) item.append(postImages(post.images));
    if (post.verdict) item.append(verdictCard(post.verdict, post.caseTitle));
    const actions = element('div', 'cm-actions');
    const comments = postLink('', post.postId, navigate, 'cm-action');
    comments.append(icon('comment'), element('span', '', '댓글'), element('span', 'cm-count', String(post.commentCount)));
    comments.setAttribute('aria-label', `댓글 ${post.commentCount}개 — ${post.title}`);
    actions.append(likeButton(post, { api, onLoginNeeded: () => loginHint(item, navigate, '로그인하면 좋아요를 누를 수 있어요.') }), comments);
    item.append(actions);
    return item;
  }

  function emptyState() {
    const box = element('div', 'cm-empty');
    const filtered = filters.category || filters.caseId;
    box.append(element('p', '', filtered ? '이 조건에 맞는 글이 아직 없어요.' : '아직 글이 없어요. 첫 생각을 남겨 보세요.'));
    if (filtered) box.append(button('모든 글 보기', () => onFilter({ category: DEFAULT_FILTERS.category, caseId: DEFAULT_FILTERS.caseId }), 'secondary-button'));
    return box;
  }

  function failure() {
    const box = element('div', 'cm-empty');
    box.append(element('p', '', '글을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'), button('다시 불러오기', () => load(), 'secondary-button'));
    return box;
  }

  return load();
}
