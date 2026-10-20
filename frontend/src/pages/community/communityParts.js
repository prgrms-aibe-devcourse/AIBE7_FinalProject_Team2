import { button, element, mockTag } from '../../components/siteLayout.js';
import { formatDispositions, formatPenalty } from '../../utils/judgmentFormat.js';
import { bookmarkFace } from '../../utils/bookmarkCharacter.js';
import { currentUser } from '../../utils/session.js';

// S-18 커뮤니티 (FE-47) 목록 · 상세가 함께 쓰는 틀과 조각: 노트북 화면 틀(왼쪽 거르기 · 가운데 · 오른쪽 글쓰기), 글 한 편, 내 판결 카드, 글쓰기 칸.
// 판결 공유는 내 판결만 담는다(AI · 실제 판결은 넣지 않는다, 앵커링 방지 — PRODUCT.md).
// 커뮤니티 API가 아직 명세에 없어 목 모드에서만 동작한다(mocks/communityMockApi.js). 실제 모드에는 api.getCommunityPosts가 없어 "준비 중"으로 안내한다
export const CATEGORY_LABELS = { VERDICT: '판결 공유', QUESTION: '질문', FREE: '자유' };
export const SORT_LABELS = { LATEST: '최신순', LIKES: '좋아요순', COMMENTS: '댓글순' };
export const NOT_READY = '커뮤니티는 아직 준비 중이에요. 곧 열려요.';
const TITLE_MAX = 60;
const BODY_MAX = 2000;
const IMAGE_MAX = 3;
const IMAGE_EDGE = 1280; // 첨부 사진은 긴 변을 이 크기로 줄여 보낸다(목은 브라우저 저장소에 둔다)
const NARROW = '(max-width: 1023px)'; // community.css와 같은 경계. 이보다 좁으면 글쓰기 칸이 화면을 덮는 창으로 열린다

// 아이콘(한 가지 선 굵기 · 24 격자). 글자 · 이모지로 대신하지 않는다
const ICONS = {
  heart: '<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z"/>',
  comment: '<path d="M5 5h14v10H9l-4 4V5Z"/>',
  image: '<rect x="4" y="5" width="16" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="m5 18 5-5 3 3 2-2 4 4"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  pen: '<path d="M5 19h4L19 9l-4-4L5 15v4Z"/><path d="m13 7 4 4"/>',
};
export function icon(name) {
  const node = element('span', `cm-icon icon-${name}`);
  node.setAttribute('aria-hidden', 'true');
  node.innerHTML = `<svg viewBox="0 0 24 24" focusable="false">${ICONS[name]}</svg>`;
  return node;
}

// "10월 19일" · 상세는 "2026년 10월 19일 21:14"
export function formatDate(iso, { full = false } = {}) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const day = `${date.getMonth() + 1}월 ${date.getDate()}일`;
  if (!full) return day;
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  return `${date.getFullYear()}년 ${day} ${time}`;
}

function timeNode(iso, full) {
  const node = element('time', '', formatDate(iso, { full }));
  node.dateTime = iso;
  return node;
}

// 글쓴이 얼굴: 책갈피 캐릭터(내로 · 남불) 얼굴. 못 불러오면 닉네임 첫 글자
export function avatar({ nickname, character }) {
  const node = element('span', 'cm-avatar');
  node.setAttribute('aria-hidden', 'true');
  const image = element('img');
  image.alt = '';
  image.width = 36;
  image.height = 36;
  image.loading = 'lazy';
  image.src = bookmarkFace(character === 'nambul' ? 'nambul' : 'naero');
  image.addEventListener('error', () => { image.remove(); node.textContent = [...nickname][0] ?? ''; }, { once: true });
  node.append(image);
  return node;
}

// 내 판결 카드: 사건 · 내 형량 · 무겁게 / 가볍게 본 요소 수. 다른 판결(AI · 실제)은 담지 않는다
export function verdictCard(verdict, caseTitle) {
  const card = element('div', 'cm-verdict');
  const penalty = element('p', 'cm-verdict-penalty');
  penalty.append(element('span', 'cm-verdict-label', '내 판결'), element('strong', '', formatPenalty(verdict)));
  const extra = formatDispositions(verdict);
  if (extra) penalty.append(element('span', 'cm-verdict-extra', extra));
  card.append(penalty, element('p', 'cm-verdict-factors', `무겁게 본 요소 ${verdict.upCount}개 · 가볍게 본 요소 ${verdict.downCount}개`));
  if (caseTitle) card.append(element('p', 'cm-verdict-case', caseTitle));
  return card;
}

function tags(post) {
  const row = element('span', 'cm-tags');
  row.append(element('span', 'cm-tag', CATEGORY_LABELS[post.category] ?? post.category));
  if (post.caseTitle) row.append(element('span', 'cm-tag is-case', post.caseTitle));
  return row;
}

export function postMeta(post, { full = false } = {}) {
  const meta = element('div', 'cm-meta');
  const who = element('span', 'cm-who');
  who.append(element('strong', '', post.author.nickname), timeNode(post.createdAt, full));
  meta.append(avatar(post.author), who, tags(post));
  return meta;
}

export function postImages(images, { full = false } = {}) {
  const box = element('div', `cm-images count-${images.length}${full ? ' is-full' : ''}`);
  images.forEach((src, index) => {
    const image = element('img');
    image.src = src;
    image.alt = `첨부 사진 ${index + 1}`;
    image.width = 4; // 비율 힌트(늦게 떠도 글이 밀리지 않게). 크기는 CSS가 정한다
    image.height = 3;
    image.loading = 'lazy';
    image.decoding = 'async';
    box.append(image);
  });
  return box;
}

// 좋아요 버튼: 누르면 바로 바뀌고(낙관적), 실패하면 되돌린다. 로그아웃 상태면 로그인 안내를 띄운다
export function likeButton(post, { api, onLoginNeeded }) {
  const node = element('button', 'cm-action cm-like');
  node.type = 'button';
  const count = element('span', 'cm-count');
  node.append(icon('heart'), element('span', '', '좋아요'), count);
  let state = { liked: post.liked, likeCount: post.likeCount };
  const paint = () => {
    node.setAttribute('aria-pressed', String(state.liked));
    count.textContent = String(state.likeCount);
  };
  paint();
  let busy = false;
  node.addEventListener('click', async () => {
    if (!currentUser()) { onLoginNeeded(node); return; }
    if (busy) return;
    busy = true;
    const before = state;
    state = { liked: !before.liked, likeCount: before.likeCount + (before.liked ? -1 : 1) };
    paint();
    try {
      state = await api.toggleCommunityLike(post.postId);
    } catch (error) {
      state = before;
      if (error?.code === 'LOGIN_REQUIRED') onLoginNeeded(node);
    } finally {
      busy = false;
      if (node.isConnected) paint();
    }
  });
  return node;
}

// 로그인 안내 한 줄(좋아요 · 댓글을 로그아웃 상태에서 눌렀을 때). 같은 자리에 한 번만 붙는다
export function loginHint(host, navigate, text) {
  host.querySelector('.cm-login-hint')?.remove();
  const hint = element('p', 'cm-login-hint');
  hint.setAttribute('role', 'status');
  hint.append(element('span', '', text), button('로그인', () => navigate('login'), 'cm-text-button'));
  host.append(hint);
}

// ---- 노트북 화면 틀 ----
// 왼쪽: 제목 · 말머리 · 사건 거르기 / 가운데: 화면마다 다름 / 오른쪽: 늘 열려 있는 글쓰기 칸 + 많이 본 글(목데이터).
// 좁은 화면에서는 한 열이 되고, 글쓰기 칸은 "글쓰기" 버튼으로 여는 창이 된다
export function communityShell({ navigate }) {
  const root = element('div', 'community-page');
  const screen = element('div', 'community-screen');
  const filters = element('aside', 'cm-filters');
  filters.setAttribute('aria-label', '글 거르기');
  const main = element('main', 'cm-main');
  const side = element('aside', 'cm-side');
  side.setAttribute('aria-label', '글쓰기');
  side.id = 'cm-compose';
  screen.append(filters, main, side);
  root.append(screen);

  const head = element('div', 'cm-head');
  const title = element('h1', 'cm-title', '커뮤니티');
  title.append(mockTag());
  head.append(title, element('p', 'cm-sub', '같은 기록, 다른 생각'));
  filters.append(head);

  // 좁은 화면: 글쓰기 칸을 창으로 연다(열려 있는 동안 뒤 화면은 조작되지 않는다)
  const openButton = button('', () => openCompose(), 'cm-compose-open primary-button');
  openButton.append(icon('pen'), element('span', '', '글쓰기'));
  openButton.setAttribute('aria-controls', side.id);
  openButton.setAttribute('aria-expanded', 'false');
  const closeButton = button('', () => closeCompose(), 'cm-compose-close');
  closeButton.append(icon('close'));
  closeButton.setAttribute('aria-label', '글쓰기 닫기');
  const narrow = window.matchMedia(NARROW);
  const onKey = (event) => { if (event.key === 'Escape') closeCompose(); };
  // 창 밖은 모두 조작되지 않게 한다: 창에서 body까지 올라가며 형제 요소(공통 헤더 · 푸터 포함)에 inert를 건다(PR #200 리뷰 반영)
  let inertNodes = [];
  function openCompose() {
    side.classList.add('is-open');
    side.setAttribute('role', 'dialog');
    side.setAttribute('aria-modal', 'true');
    inertNodes = [];
    for (let node = side; node && node !== document.body; node = node.parentElement) {
      for (const sibling of node.parentElement?.children ?? []) {
        if (sibling !== node && !sibling.inert) { sibling.inert = true; inertNodes.push(sibling); }
      }
    }
    openButton.setAttribute('aria-expanded', 'true');
    document.addEventListener('keydown', onKey);
    side.querySelector('input, textarea, button:not(.cm-compose-close)')?.focus();
  }
  function closeCompose() {
    if (!side.classList.contains('is-open')) return;
    side.classList.remove('is-open');
    side.removeAttribute('role');
    side.removeAttribute('aria-modal');
    inertNodes.forEach((node) => { node.inert = false; });
    inertNodes = [];
    openButton.setAttribute('aria-expanded', 'false');
    document.removeEventListener('keydown', onKey);
    if (root.isConnected) openButton.focus();
  }
  // 넓은 화면으로 바뀌면 창을 닫는다(넓은 화면에서는 늘 열려 있는 칸이다). 화면을 떠난 뒤 처음 불리면 스스로 떨어진다
  const onWidth = () => {
    if (!root.isConnected) { narrow.removeEventListener('change', onWidth); document.removeEventListener('keydown', onKey); return; }
    if (!narrow.matches) closeCompose();
  };
  narrow.addEventListener('change', onWidth);

  return { root, filters, main, side, openButton, closeButton, closeCompose };
}

// 말머리 · 사건 거르기. value는 { category, caseId }. onChange는 바뀐 값으로 부른다
export function filterControls({ value, cases, onChange }) {
  const box = element('div', 'cm-filter-groups');
  const group = element('div', 'cm-filter-group');
  const groupTitle = element('h2', 'cm-filter-title', '말머리');
  groupTitle.id = 'cm-filter-category';
  const list = element('div', 'cm-filter-list');
  list.setAttribute('role', 'group');
  list.setAttribute('aria-labelledby', groupTitle.id);
  [[null, '전체'], ...Object.entries(CATEGORY_LABELS)].forEach(([key, label]) => {
    const item = button(label, () => onChange({ ...value, category: key }), 'cm-filter');
    item.setAttribute('aria-pressed', String(value.category === key));
    list.append(item);
  });
  group.append(groupTitle, list);

  const caseGroup = element('div', 'cm-filter-group');
  const select = element('select', 'cm-select');
  select.id = 'cm-filter-case';
  const label = element('label', 'cm-filter-title', '사건');
  label.htmlFor = select.id;
  select.append(new Option('모든 사건', ''));
  cases.forEach(({ caseId, title }) => select.append(new Option(title, String(caseId))));
  select.value = value.caseId ? String(value.caseId) : '';
  select.addEventListener('change', () => onChange({ ...value, caseId: select.value ? Number(select.value) : null }));
  caseGroup.append(label, select);
  box.append(group, caseGroup);
  return box;
}

export function popularList(popular, navigate) {
  const box = element('section', 'cm-popular');
  box.setAttribute('aria-labelledby', 'cm-popular-title');
  const title = element('h2', 'cm-side-title', '많이 본 글');
  title.id = 'cm-popular-title';
  title.append(mockTag());
  const list = element('ol', 'cm-popular-list');
  popular.forEach(({ postId, title: text }) => {
    const item = element('li');
    item.append(postLink(text, postId, navigate));
    list.append(item);
  });
  box.append(title, list);
  return box;
}

// 글로 가는 링크: 주소가 있는 진짜 링크(새 탭 열기 · 주소 복사가 된다), 그냥 누르면 앱 안에서 이동한다
export function postLink(text, postId, navigate, className = '') {
  const link = element('a', className, text);
  link.href = `/community/posts/${postId}`;
  link.addEventListener('click', (event) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    navigate('communityPost', { postId });
  });
  return link;
}

// ---- 글쓰기 칸 ----
// 말머리 · 사건 · 제목 · 본문 · 사진(최대 3장) · 내 판결 붙이기. 로그아웃 상태면 로그인 안내만 둔다.
// 올리면 onPosted(새 글)를 부른다
export function composer({ api, navigate, cases, onPosted, closeButton, closeCompose }) {
  const box = element('section', 'cm-compose');
  box.setAttribute('aria-labelledby', 'cm-compose-title');
  const head = element('div', 'cm-compose-head');
  const title = element('h2', 'cm-side-title', '생각 나누기');
  title.id = 'cm-compose-title';
  head.append(title, closeButton);
  box.append(head);

  if (!currentUser()) {
    box.append(element('p', 'cm-compose-note', '로그인하면 글을 쓰고 댓글 · 좋아요를 남길 수 있어요.'),
      button('로그인하기', () => { closeCompose(); navigate('login'); }, 'primary-button cm-compose-login'));
    return box;
  }

  const form = element('form', 'cm-form');
  form.noValidate = true;

  const categories = element('fieldset', 'cm-field cm-categories');
  categories.append(element('legend', 'cm-label', '말머리'));
  const choice = element('div', 'cm-segments');
  ['FREE', 'QUESTION', 'VERDICT'].forEach((key, index) => {
    const option = element('label', 'cm-segment');
    const radio = element('input');
    radio.type = 'radio';
    radio.name = 'category';
    radio.value = key;
    radio.checked = index === 0;
    option.append(radio, element('span', '', CATEGORY_LABELS[key]));
    choice.append(option);
  });
  categories.append(choice);

  const caseField = element('div', 'cm-field');
  const caseSelect = element('select', 'cm-select');
  caseSelect.id = 'cm-compose-case';
  caseSelect.name = 'caseId';
  const caseLabel = element('label', 'cm-label', '사건');
  caseLabel.htmlFor = caseSelect.id;
  caseSelect.append(new Option('사건 없이 쓰기', ''));
  cases.forEach(({ caseId, title: text }) => caseSelect.append(new Option(text, String(caseId))));
  caseField.append(caseLabel, caseSelect);

  const titleField = textField({ id: 'cm-compose-title-input', label: '제목', max: TITLE_MAX, placeholder: '제목을 적어 주세요' });
  const bodyField = textField({ id: 'cm-compose-body', label: '내용', max: BODY_MAX, placeholder: '이 사건, 어떻게 보셨나요?', multiline: true });

  // 사진: 고르면 줄여서 미리 보여 주고, 하나씩 뺄 수 있다
  const photos = [];
  const photoField = element('div', 'cm-field');
  const fileInput = element('input', 'visually-hidden');
  fileInput.type = 'file';
  fileInput.accept = 'image/*';
  fileInput.multiple = true;
  fileInput.tabIndex = -1;
  fileInput.setAttribute('aria-hidden', 'true');
  const addPhoto = button('', () => fileInput.click(), 'cm-attach');
  const addLabel = element('span', '');
  addPhoto.append(icon('image'), addLabel);
  const previews = element('ul', 'cm-previews');
  previews.setAttribute('aria-label', '붙인 사진');
  const paintPhotos = () => {
    addLabel.textContent = `사진 붙이기 ${photos.length} / ${IMAGE_MAX}`;
    addPhoto.disabled = photos.length >= IMAGE_MAX;
    previews.replaceChildren(...photos.map((src, index) => {
      const item = element('li', 'cm-preview');
      const image = element('img');
      image.src = src;
      image.alt = `붙인 사진 ${index + 1}`;
      image.width = 1;
      image.height = 1;
      const remove = button('', () => { photos.splice(index, 1); paintPhotos(); addPhoto.focus(); }, 'cm-preview-remove');
      remove.append(icon('close'));
      remove.setAttribute('aria-label', `사진 ${index + 1} 빼기`);
      item.append(image, remove);
      return item;
    }));
  };
  fileInput.addEventListener('change', async () => {
    const files = [...fileInput.files].filter((file) => file.type.startsWith('image/')).slice(0, IMAGE_MAX - photos.length);
    fileInput.value = '';
    addPhoto.disabled = true; // 줄이는 동안 다시 고르지 못하게 한다(PR #200 리뷰 반영)
    for (const file of files) {
      try {
        const src = await shrinkImage(file);
        if (photos.length < IMAGE_MAX) photos.push(src);
      } catch { showError('사진을 읽지 못했어요. 다른 사진을 골라 주세요.'); }
    }
    if (box.isConnected) paintPhotos();
  });
  photoField.append(fileInput, addPhoto, previews);
  paintPhotos();

  // 내 판결 붙이기: 판결을 확정했을 때만 고를 수 있다. 고르면 말머리는 판결 공유, 사건은 그 사건으로 맞춘다
  const verdictField = element('div', 'cm-field cm-share');
  const share = element('input');
  share.type = 'checkbox';
  share.id = 'cm-compose-verdict';
  share.disabled = true;
  const shareLabel = element('label', 'cm-check');
  shareLabel.htmlFor = share.id;
  shareLabel.append(share, element('span', '', '내 판결 붙이기'));
  const shareNote = element('p', 'cm-hint', '내 판결을 불러오는 중이에요.');
  shareNote.id = 'cm-compose-verdict-note';
  share.setAttribute('aria-describedby', shareNote.id);
  const sharePreview = element('div', 'cm-share-preview');
  verdictField.append(shareLabel, shareNote, sharePreview);
  let shareable = null;
  api.getMyShareableVerdict?.().then((value) => {
    if (!box.isConnected) return;
    shareable = value;
    share.disabled = !value;
    shareNote.textContent = value
      ? 'AI 판결 · 실제 판결은 붙지 않아요. 사건을 아직 안 본 사람도 볼 수 있어요.'
      : '판결을 확정하면 내 판결을 붙일 수 있어요.';
  }).catch(() => { if (box.isConnected) shareNote.textContent = '내 판결을 불러오지 못했어요.'; });
  share.addEventListener('change', () => {
    sharePreview.replaceChildren();
    if (share.checked && shareable) {
      form.elements.category.value = 'VERDICT';
      caseSelect.value = String(shareable.caseId);
      sharePreview.append(verdictCard(shareable.verdict, shareable.caseTitle));
    }
    caseSelect.disabled = share.checked;
  });

  const error = element('p', 'cm-error');
  error.setAttribute('role', 'alert');
  const submit = element('button', 'primary-button cm-submit', '올리기');
  submit.type = 'submit';
  const actions = element('div', 'cm-form-actions');
  actions.append(error, submit);
  function showError(text) { error.textContent = text; }

  form.append(categories, caseField, titleField.field, bodyField.field, photoField, verdictField, actions);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    showError('');
    const titleText = titleField.input.value.trim();
    const bodyText = bodyField.input.value.trim();
    if (!titleText) { showError('제목을 적어 주세요.'); titleField.input.focus(); return; }
    if (!bodyText) { showError('내용을 적어 주세요.'); bodyField.input.focus(); return; }
    submit.disabled = true;
    submit.textContent = '올리는 중…';
    try {
      const post = await api.createCommunityPost({
        category: form.elements.category.value,
        caseId: caseSelect.value ? Number(caseSelect.value) : null,
        title: titleText,
        body: bodyText,
        images: [...photos],
        shareVerdict: share.checked,
      });
      if (box.isConnected) onPosted(post);
    } catch (failure) {
      if (!box.isConnected) return;
      showError(failure?.code === 'LOGIN_REQUIRED' ? '로그인이 풀렸어요. 다시 로그인해 주세요.'
        : failure?.code === 'VALIDATION_ERROR' ? '입력한 내용을 확인해 주세요.'
          : '글을 올리지 못했어요. 잠시 뒤 다시 시도해 주세요.');
      submit.disabled = false;
      submit.textContent = '올리기';
    }
  });
  box.append(form);
  return box;
}

// 입력칸 + 글자 수. 글자 수는 낭독기가 칸 설명으로 읽는다
export function textField({ id, label, max, placeholder, multiline = false, rows = 5 }) {
  const field = element('div', 'cm-field');
  const name = element('label', 'cm-label', label);
  name.htmlFor = id;
  const input = element(multiline ? 'textarea' : 'input', 'cm-input');
  input.id = id;
  input.name = id;
  input.autocomplete = 'off';
  if (!multiline) input.type = 'text';
  else input.rows = rows;
  input.maxLength = max;
  input.placeholder = placeholder;
  const counter = element('span', 'cm-counter');
  counter.id = `${id}-count`;
  input.setAttribute('aria-describedby', counter.id);
  const paint = () => { counter.textContent = `${input.value.length} / ${max}`; };
  input.addEventListener('input', paint);
  paint();
  const top = element('div', 'cm-label-row');
  top.append(name, counter);
  field.append(top, input);
  return { field, input };
}

// 사진을 긴 변 IMAGE_EDGE로 줄여 JPEG data URL로 바꾼다
async function shrinkImage(file) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, IMAGE_EDGE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close?.();
  return canvas.toDataURL('image/jpeg', 0.82);
}
