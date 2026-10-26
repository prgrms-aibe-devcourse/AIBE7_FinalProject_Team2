import { button, element } from '../../components/siteLayout.js';
import { canGoBackInApp } from '../../app/router.js';
import { currentUser } from '../../utils/session.js';
import {
  NOT_READY, authorButton, communityShell, composer, filterControls, foldedVerdict, formatDate, likeButton, loginHint, popularList, nameCard, postImages, postMeta, textField, verdictWithType,
} from './communityParts.js';

// S-18 커뮤니티 글 상세 (FE-47, /community/posts/:postId). 목록과 같은 노트북 화면 틀에 글 전체 · 좋아요 · 댓글을 둔다.
// 왼쪽 거르기를 누르면 그 조건의 목록으로 간다. 오른쪽 글쓰기 칸은 목록과 같다
const COMMENT_MAX = 500;

export function renderCommunityPostPage(container, { api, navigate, postId }) {
  const shell = communityShell({ navigate });
  const { root, filters: filterHost, main, side, openButton, closeButton, closeCompose } = shell;
  container.replaceChildren(root);
  const active = () => container.contains(root);

  if (!api.getCommunityPost) {
    main.append(element('p', 'cm-note', NOT_READY));
    return undefined;
  }

  const back = button('← 목록으로', () => (canGoBackInApp() ? history.back() : navigate('communityPosts')), 'cm-back');
  const toolbar = element('div', 'cm-toolbar');
  toolbar.append(back, openButton);
  const body = element('div', 'cm-detail');
  main.append(toolbar, body);
  const loading = element('p', 'cm-note', '글을 불러오고 있어요…');
  loading.setAttribute('role', 'status');
  body.append(loading);

  // 거르기 · 글쓰기 칸 · 많이 본 글은 목록 응답으로 채운다(글 상세와 따로 불러 글이 먼저 보이게 한다)
  api.getCommunityPosts?.().then((data) => {
    if (!active()) return;
    filterHost.append(filterControls({ value: { category: undefined, caseId: null }, cases: data.cases, onChange: ({ category, caseId }) => navigate('communityPosts', { category: category ?? null, filterCaseId: caseId }) }));
    side.append(composer({ api, navigate, cases: data.cases, closeButton, closeCompose, onPosted: (post) => { closeCompose(); navigate('communityPosts', { justPosted: post.postId }); } }),
      popularList(data.popular, navigate));
  }).catch(() => {});

  return (async () => {
    let post;
    try {
      post = await api.getCommunityPost(postId);
    } catch (error) {
      if (!active()) return;
      const box = element('div', 'cm-empty');
      box.append(element('p', '', error?.code === 'POST_NOT_FOUND' ? '글을 찾을 수 없어요. 지워졌거나 주소가 잘못됐어요.' : '글을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'),
        button('목록으로', () => navigate('communityPosts'), 'secondary-button'));
      body.replaceChildren(box);
      return;
    }
    if (!active()) return;
    document.title = `${post.title} · 커뮤니티 · 내Law남불`;
    body.replaceChildren(article(post), commentSection(post));
    body.querySelector('h1')?.focus({ preventScroll: true });
  })();

  function article(post) {
    const node = element('article', 'cm-article');
    node.setAttribute('aria-labelledby', 'cm-article-title');
    const title = element('h1', 'cm-article-title', post.title);
    title.id = 'cm-article-title';
    title.tabIndex = -1;
    node.append(postMeta(post, { full: true }), title, element('div', 'cm-article-body', post.body));
    if (post.images.length) node.append(postImages(post.images, { full: true }));
    if (post.verdict) node.append(foldedVerdict(post, () => verdictWithType(post.verdict, post.caseTitle, post.author)));
    const actions = element('div', 'cm-actions');
    actions.append(likeButton(post, { api, onLoginNeeded: () => loginHint(node, navigate, '로그인하면 좋아요를 누를 수 있어요.') }));
    node.append(actions, nameCard(post.author));
    return node;
  }

  // 댓글: 오래된 순. 아래에 댓글 쓰기(로그아웃이면 로그인 안내)
  function commentSection(post) {
    const section = element('section', 'cm-comments');
    section.id = 'comments';
    section.setAttribute('aria-labelledby', 'cm-comments-title');
    const heading = element('h2', 'cm-comments-title');
    heading.id = 'cm-comments-title';
    const list = element('ol', 'cm-comment-list');
    let total = post.comments.length;
    const paintCount = () => { heading.textContent = `댓글 ${total}개`; };
    paintCount();
    post.comments.forEach((comment) => list.append(commentItem(comment)));
    section.append(heading, list);
    if (!total) list.append(element('li', 'cm-comment-empty', '아직 댓글이 없어요. 첫 댓글을 남겨 보세요.'));

    if (!currentUser()) {
      loginHint(section, navigate, '로그인하면 댓글을 남길 수 있어요.');
      return section;
    }
    const form = element('form', 'cm-comment-form');
    form.noValidate = true;
    const { field, input } = textField({ id: 'cm-comment-input', label: '댓글 쓰기', max: COMMENT_MAX, placeholder: '생각을 남겨 주세요', multiline: true, rows: 3 });
    const error = element('p', 'cm-error');
    error.setAttribute('role', 'alert');
    const submit = element('button', 'primary-button', '댓글 달기');
    submit.type = 'submit';
    const actions = element('div', 'cm-form-actions');
    actions.append(error, submit);
    form.append(field, actions);
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      error.textContent = '';
      const text = input.value.trim();
      if (!text) { error.textContent = '댓글을 적어 주세요.'; input.focus(); return; }
      submit.disabled = true;
      try {
        const comment = await api.createCommunityComment(post.postId, { body: text });
        if (!active()) return;
        list.querySelector('.cm-comment-empty')?.remove();
        const item = commentItem(comment);
        item.classList.add('is-new');
        list.append(item);
        total += 1;
        paintCount();
        input.value = '';
        input.dispatchEvent(new Event('input'));
      } catch (failure) {
        if (!active()) return;
        error.textContent = failure?.code === 'LOGIN_REQUIRED' ? '로그인이 풀렸어요. 다시 로그인해 주세요.' : '댓글을 달지 못했어요. 잠시 뒤 다시 시도해 주세요.';
      } finally {
        submit.disabled = false;
      }
    });
    section.append(form);
    return section;
  }

  function commentItem(comment) {
    const item = element('li', 'cm-comment');
    const who = element('div', 'cm-meta');
    const time = element('time', '', formatDate(comment.createdAt, { full: true }));
    time.dateTime = comment.createdAt;
    who.append(authorButton(comment.author), time);
    item.append(who, element('p', 'cm-comment-body', comment.body));
    return item;
  }
}
