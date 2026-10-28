import { button, element } from '../../components/siteLayout.js';
import { NOT_READY, communityShell, composer, filterControls, popularList } from './communityParts.js';

// 커뮤니티 글쓰기 (/community/write). 목록 · 상세와 같은 틀(communityShell)에 글쓰기 칸 하나를 둔다.
// 올리면 글 목록으로 가서 새 글을 보여 주고, 사건 목록 · 많이 본 글은 목록 응답으로 채운다
export function renderCommunityWritePage(container, { api, navigate }) {
  const { root, filters: filterHost, main } = communityShell({ navigate, withCompose: false });
  container.replaceChildren(root);
  const active = () => container.contains(root);

  if (!api.getCommunityPosts) {
    main.append(element('p', 'cm-note', NOT_READY));
    return undefined;
  }

  const toolbar = element('div', 'cm-toolbar');
  toolbar.append(button('← 목록으로', () => navigate('communityPosts'), 'cm-back'));
  const body = element('div', 'cm-write');
  main.append(element('h2', 'visually-hidden', '글쓰기'), toolbar, body);
  const loading = element('p', 'cm-note', '글쓰기 칸을 불러오고 있어요…');
  loading.setAttribute('role', 'status');
  body.append(loading);

  return api.getCommunityPosts().then((data) => {
    if (!active()) return;
    filterHost.append(
      filterControls({ value: { category: undefined, caseId: null }, cases: data.cases, onChange: ({ category, caseId }) => navigate('communityPosts', { category: category ?? null, filterCaseId: caseId }) }),
      popularList(data.popular, navigate),
    );
    body.replaceChildren(composer({ api, navigate, cases: data.cases, onPosted: (post) => navigate('communityPosts', { justPosted: post.postId }) }));
  }).catch(() => {
    if (!active()) return;
    const box = element('div', 'cm-empty');
    box.append(element('p', '', '글쓰기 칸을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'), button('목록으로', () => navigate('communityPosts'), 'secondary-button'));
    body.replaceChildren(box);
  });
}
