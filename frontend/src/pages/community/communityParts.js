import { button, element, mockTag } from '../../components/siteLayout.js';
import { formatDispositions, formatPenalty } from '../../utils/judgmentFormat.js';
import { API_MODE } from '../../api/config.js';
import { composeErrorText } from './composeError.js';
import { currentUser } from '../../utils/session.js';
import { eraseSignature, signatureSvg, signerSeed, writeSignature } from '../../components/signatureInk.js';
import { profileFace, typeFace } from '../../components/personCards.js';
import { normalizeDeco } from '../../utils/profileDeco.js';
import { normalizeTypeDeco } from '../../utils/typeCardDeco.js';

// S-18 커뮤니티 (FE-47) 목록 · 상세가 함께 쓰는 틀과 조각: 노트북 화면 틀(왼쪽 거르기 · 가운데 · 오른쪽 글쓰기), 글 한 편, 내 판결 카드, 글쓰기 칸.
// 판결 공유는 내 판결만 담는다(AI · 실제 판결은 넣지 않는다, 앵커링 방지 — PRODUCT.md).
// 목 모드(mocks/communityMockApi.js)와 실제 모드(api/realApi.js, BE-115)가 같은 함수 이름을 쓴다. api.getCommunityPosts가 없는 구현이면 "준비 중"으로 안내한다
// 사진 올리기는 서버에 아직 없어(후속) 실제 모드에서는 글쓰기 사진 칸을 두지 않는다(서버는 images가 있으면 400이다)
const PHOTO_UPLOAD = API_MODE === 'mock';
export const CATEGORY_LABELS = { VERDICT: '판결 공유', QUESTION: '질문', FREE: '자유' };
export const SORT_LABELS = { LATEST: '최신순', LIKES: '좋아요순', COMMENTS: '댓글순' };
export const NOT_READY = '커뮤니티는 아직 준비 중이에요. 곧 열려요.';
const TITLE_MAX = 60;
const BODY_MAX = 2000;
const IMAGE_MAX = 3;
const IMAGE_EDGE = 1280; // 첨부 사진은 긴 변을 이 크기로 줄여 보낸다(목은 브라우저 저장소에 둔다)
const SHEET = '(max-width: 720px)'; // community.css 모바일 경계. 이보다 좁으면 글쓴이 작은 창이 아래에서 올라오는 판이 된다

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

// ---- 글쓴이 명함(FE-88) ----
// 글쓴이는 닉네임이 아니라 프로필카드 + 공개 성향카드 두 장(명함)으로 보인다. 카드는 마이페이지에서 꾸민 모양 그대로(.cm-cards가 커피 색을 입힌다).
// 성향으로 거르기 · 정렬 · 순위는 만들지 않는다(편 가르기 · 앵커링 방지). 글쓴이 값(author)은 목 API가 채우고, 실제 API가 같은 모양으로 내려준다(mocks/communityMockData.js)
const CLIP_PATH = 'M7 30V8a4 4 0 0 1 8 0v30a6 6 0 0 1-12 0V14';
const typeImage = (author) => author.shownType?.type.imageUrl ?? null;
const SLOT_NAMES = { initial: '초심', retake: '재심', current: '최근' };
const typeNameOf = (author) => author.shownType?.type.name ?? null;

// 커뮤니티 이름 · 로고(임시). 이름은 확정 전이라 한 곳에서만 쓴다. 로고는 책상 위 스탠드 + 달(DESIGN.md 책상 물건 은유)이고, 최종 이미지 로고가 나오면 logoMark만 바꾼다
export const COMMUNITY_NAME = '판사들의 밤';
export const logoMark = () => svgIcon('0 0 40 40', 'cm-logo', [
  ['M31 4.5a6.5 6.5 0 1 0 4.5 11A8 8 0 0 1 31 4.5Z', 'cm-logo-moon'],
  ['M13 17 6 33h20l-4-12', 'cm-logo-light'],
  ['M9 17a9 9 0 0 1 18 0Z', 'cm-logo-lamp'],
  ['M18 17v12M12 33.5h12M8 36.5h20', 'cm-logo-line'],
]);

// 장식 SVG: paths는 [[path 값, 클래스], ...]. 읽지 않는다(aria-hidden). 값이 정해진 상수만 넣는다
export function svgIcon(viewBox, className, paths) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  node.setAttribute('viewBox', viewBox);
  node.setAttribute('aria-hidden', 'true');
  node.setAttribute('focusable', 'false');
  if (className) node.classList.add(className);
  node.append(...paths.map(([d, pathClass]) => {
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    line.setAttribute('d', d);
    if (pathClass) line.classList.add(pathClass);
    return line;
  }));
  return node;
}

export function clip() {
  const node = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  node.setAttribute('viewBox', '0 0 22 52');
  node.setAttribute('aria-hidden', 'true');
  node.classList.add('cm-clip');
  const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  line.setAttribute('d', CLIP_PATH);
  node.append(line);
  return node;
}

// 글쓴이의 프로필카드(FE-116): 카드 아래 줄은 글쓴이가 고른 것(성향 · 서명 · 직업)이고, 레벨은 켰을 때만 윗줄에 붙는다
export function authorFace(author) {
  const { line = 'type', showLevel = false } = author.cardLine ?? {};
  const extra = { level: showLevel && author.level != null ? `Lv.${author.level}` : null };
  const deco = normalizeDeco(author.profileCard);
  if (line === 'signature') return profileFace(deco, author.nickname, null, { ...extra, signer: author.nickname });
  if (line === 'job' && author.job?.name) return profileFace(deco, author.nickname, author.job.name, extra);
  return profileFace(deco, author.nickname, typeNameOf(author), extra);
}

// 프로필카드 · 성향카드 한 쌍(누를 수 없음). 크기는 부모(.cm-namecard · .cm-pop)가 정한다
function cardPair(author) {
  const box = element('div', 'cm-cards cm-card-pair');
  box.append(authorFace(author));
  box.append(author.shownType
    ? typeFace(author.shownType.type, undefined, normalizeTypeDeco(author.shownType.deco))
    : typeFace(null, '"아직 성향 테스트를 안 했어요"'));
  return box;
}

// 목록 · 댓글에 쓰는 작은 명함: 카드 두 장이 겹쳐 있고, 닉네임 아래 공개 성향 이름이 붙는다
function miniPair(author) {
  const pair = element('span', 'cm-cards cm-mini-pair');
  pair.setAttribute('aria-hidden', 'true');
  const deco = normalizeDeco(author.profileCard);
  const profile = element('span', `cm-mini is-profile deco-frame-${deco.frame} deco-bg-${deco.background}`);
  const face = element('img');
  face.src = deco.portrait;
  face.alt = '';
  face.loading = 'lazy';
  profile.append(face);
  const type = element('span', `cm-mini is-type deco-frame-${normalizeTypeDeco(author.shownType?.deco).frame}`);
  if (typeImage(author)) {
    const image = element('img');
    image.src = typeImage(author);
    image.alt = '';
    image.loading = 'lazy';
    type.append(image);
  }
  pair.append(type, profile);
  return pair;
}

// ③ 작은 창: 닉네임 · 작은 명함을 누르면 그 자리 아래에 명함이 펼쳐진다. 좁은 화면은 아래에서 올라오는 판(community.css)
let popNode = null;
let popOpener = null;
function popover() {
  if (popNode?.isConnected) return popNode;
  popNode = element('div', 'cm-pop');
  popNode.setAttribute('popover', 'auto');
  popNode.setAttribute('role', 'dialog');
  popNode.tabIndex = -1;
  popNode.addEventListener('toggle', (event) => {
    if (event.newState !== 'closed') return;
    const opener = popOpener;
    popOpener = null;
    opener?.setAttribute('aria-expanded', 'false');
    // 포커스가 창 안이나 body에 있을 때(Esc · 빈 곳을 눌러 닫힘)만 글쓴이 버튼으로 돌려준다. 바깥 입력칸을 눌러 닫히면 그 입력칸의 포커스를 지킨다(PR #264 리뷰 반영)
    const active = document.activeElement;
    const focusLost = !active || active === document.body || popNode.contains(active);
    if (opener?.isConnected && focusLost) opener.focus({ preventScroll: true });
  });
  addEventListener('scroll', () => { if (popNode?.matches(':popover-open')) popNode.hidePopover(); }, { passive: true });
  // 다른 화면으로 가면(뒤로 가기 포함) 명함 창을 닫는다. 창은 body 끝에 붙어 있어 라우터가 화면을 바꿔도 남기 때문이다(PR #264 리뷰 반영)
  addEventListener('popstate', () => { if (popNode?.matches(':popover-open')) popNode.hidePopover(); });
  document.body.append(popNode);
  return popNode;
}

function openPop(author, from) {
  const node = popover();
  if (!node.showPopover) return; // 작은 창을 못 쓰는 브라우저는 카드를 펼치지 않는다(닉네임 · 성향 이름은 그대로 보인다)
  // 이미 열려 있으면(키보드로 창에서 나와 다른 글쓴이 버튼을 누른 경우) 다시 열지 않고 내용만 바꾼다: 열린 창에 showPopover를 부르면 예외가 난다. 앞 버튼의 펼침 표시는 여기서 거둔다(PR #264 리뷰 반영)
  if (node.matches(':popover-open')) popOpener?.setAttribute('aria-expanded', 'false');
  popOpener = from;
  node.setAttribute('aria-label', `${author.nickname}의 명함`);
  node.replaceChildren(cardPair(author), authorInfo(author));
  if (!node.matches(':popover-open')) node.showPopover();
  from.setAttribute('aria-expanded', 'true');
  node.focus({ preventScroll: true });
  if (matchMedia(SHEET).matches) { node.style.left = ''; node.style.top = ''; return; } // 모바일: 아래에서 올라오는 판(자리는 CSS가 정한다)
  const rect = from.getBoundingClientRect();
  const width = node.offsetWidth;
  node.style.left = `${Math.min(Math.max(8, rect.left), innerWidth - width - 8)}px`;
  const below = rect.bottom + 8;
  node.style.top = `${below + node.offsetHeight > innerHeight ? Math.max(8, rect.top - node.offsetHeight - 8) : below}px`;
}

// 닉네임 · 한 줄 소개 · 수치. 레벨 · 직업 · 체험한 사건은 마이페이지처럼 아직 서버 값이 없어 목이다
function authorInfo(author) {
  const box = element('div', 'cm-author-info');
  const stats = element('dl', 'cm-author-stats');
  [['레벨', `Lv.${author.level}`], ['직업', author.job?.name ?? '무직'], ['체험한 사건', `${author.completedCount}건`]].forEach(([label, value]) => {
    const row = element('div');
    row.append(element('dt', '', label), element('dd', '', value));
    stats.append(row);
  });
  box.append(element('p', 'cm-author-name', author.nickname));
  if (author.intro) box.append(element('p', 'cm-author-intro', `“${author.intro}”`));
  box.append(stats);
  return box;
}

// 글쓴이 버튼: 작은 명함 + 닉네임 + 공개 성향 이름. 누르면 작은 창(③)이 열린다
export function authorButton(author) {
  const node = element('button', 'cm-who-button');
  node.type = 'button';
  node.setAttribute('aria-haspopup', 'dialog');
  node.setAttribute('aria-expanded', 'false');
  const slot = author.shownType ? SLOT_NAMES[author.shownType.slot] : null;
  node.setAttribute('aria-label', `${author.nickname}, 성향 ${typeNameOf(author) ?? '테스트 전'}${slot ? `(${slot})` : ''}. 명함 펼치기`);
  const text = element('span', 'cm-who');
  text.append(element('strong', '', author.nickname), element('span', 'cm-who-type', typeNameOf(author) ?? '성향 테스트 전'));
  node.append(miniPair(author), text);
  node.addEventListener('click', () => openPop(author, node));
  return node;
}

// ② 글 상세 맨 아래의 글쓴이 명함: 카드 두 장 + 소개. 본문과 박스로 나누고 바탕을 한 단계 진한 종이로 둔다
export function nameCard(author) {
  const section = element('section', 'cm-namecard');
  const title = element('h2', 'cm-namecard-title', '글쓴이 명함');
  title.id = 'cm-namecard-title';
  if (API_MODE === 'mock') title.append(mockTag()); // 목 모드에서만 — 실제 모드는 C-2 응답의 명함이다(FE-142)
  section.setAttribute('aria-labelledby', title.id);
  const body = element('div', 'cm-namecard-body');
  body.append(cardPair(author), authorInfo(author));
  section.append(title, body);
  return section;
}

// 판결 카드: 사건 · 형량 · 무겁게 / 가볍게 본 요소 수. 다른 판결(AI · 실제)은 담지 않는다
export function verdictCard(verdict, caseTitle, label = '내 판결') {
  const card = element('div', 'cm-verdict');
  const penalty = element('p', 'cm-verdict-penalty');
  penalty.append(element('span', 'cm-verdict-label', label), element('strong', '', formatPenalty(verdict)));
  const extra = formatDispositions(verdict);
  if (extra) penalty.append(element('span', 'cm-verdict-extra', extra));
  card.append(penalty, element('p', 'cm-verdict-factors', `무겁게 본 요소 ${verdict.upCount}개 · 가볍게 본 요소 ${verdict.downCount}개`));
  if (caseTitle) card.append(element('p', 'cm-verdict-case', caseTitle));
  return card;
}

// 판결 공유 글(상세): 글쓴이 판결 카드에 판결한 사람의 공개 성향카드를 클립으로 꽂는다. 성향이 없으면(성향 테스트 전) 내 판결 카드만 둔다
export function verdictWithType(verdict, caseTitle, author) {
  const card = verdictCard(verdict, caseTitle, '글쓴이 판결');
  if (!author.shownType) return card;
  const row = element('div', 'cm-verdict-row');
  card.append(element('p', 'cm-verdict-type', `판결한 사람의 성향 ${author.shownType.type.name}(${SLOT_NAMES[author.shownType.slot]})`));
  const holder = element('div', 'cm-cards cm-verdict-clip');
  holder.setAttribute('aria-hidden', 'true'); // 장식 미니 카드: 성향 이름은 위 글자로 읽힌다
  holder.append(clip(), typeFace(author.shownType.type, undefined, normalizeTypeDeco(author.shownType.deco)));
  row.append(card, holder);
  return row;
}

// 판결 전 접기(FE-93): 이 사건을 아직 판결하지 않은 사람에게는 남의 형량을 먼저 보여 주지 않는다(PRODUCT 원칙 1 — 판단은 사용자의 것). 원하면 펼쳐 볼 수 있다
export function foldedVerdict(post, makeCard) {
  if (post.viewerJudged !== false) return makeCard();
  const box = element('div', 'cm-verdict is-folded');
  box.append(element('p', 'cm-verdict-fold-note', '이 사건을 판결하면 글쓴이 판결이 보여요. 내 판단을 먼저 해 보세요.'));
  box.append(button('그래도 보기', () => {
    const card = makeCard();
    card.tabIndex = -1; // 펼친 자리로 포커스를 옮겨 화면 읽기 프로그램이 바로 읽는다
    box.replaceWith(card);
    card.focus();
  }, 'cm-text-button'));
  return box;
}

function tags(post) {
  const row = element('span', 'cm-tags');
  row.append(element('span', 'cm-tag', CATEGORY_LABELS[post.category] ?? post.category));
  if (post.caseTitle) row.append(element('span', 'cm-tag is-case', post.caseTitle));
  return row;
}

export function postMeta(post, { full = false } = {}) {
  const meta = element('div', 'cm-meta');
  meta.append(authorButton(post.author), timeNode(post.createdAt, full), tags(post));
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
      if (error?.code === 'UNAUTHORIZED') onLoginNeeded(node);
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
// 왼쪽: 제목 · 말머리 · 사건 거르기 · 많이 본 글(목데이터) · 글쓰기 버튼 / 가운데: 화면마다 다름. 글쓰기는 칸이 아니라 "글쓰기" 버튼으로 가는 별도 화면이다(/community/write, 글쓰기 페이지).
// 좁은 화면에서는 한 열이 된다
export function communityShell({ navigate, withCompose = true }) {
  const root = element('div', 'community-page');
  const screen = element('div', 'community-screen');
  const filters = element('aside', 'cm-filters');
  filters.setAttribute('aria-label', '글 거르기');
  const main = element('main', 'cm-main');
  screen.append(filters, main);
  root.append(screen);

  const head = element('div', 'cm-head');
  // 제목을 누르면 커뮤니티 메인으로(주소가 있는 진짜 링크: 새 탭 열기 · 주소 복사가 된다, postLink와 같은 방식)
  const title = element('h1', 'cm-title');
  const home = element('a', 'cm-title-link');
  home.append(logoMark(), element('span', '', COMMUNITY_NAME));
  home.href = '/community';
  home.addEventListener('click', (event) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    navigate('community');
  });
  title.append(home);
  if (API_MODE === 'mock') title.append(mockTag()); // 목 모드에서만(FE-142)
  head.append(title, element('p', 'cm-sub', '같은 기록, 다른 생각'));
  filters.append(head);

  // 글쓰기 버튼: 왼쪽 열 맨 아래(거르기 · 많이 본 글이 나중에 붙어도 CSS order로 맨 아래), 좁은 화면은 그 열 오른쪽 위. 글쓰기 페이지 자신에는 두지 않는다
  if (withCompose) {
    const openButton = button('', () => navigate('communityWrite'), 'cm-compose-open primary-button');
    openButton.append(icon('pen'), element('span', '', '글쓰기'));
    filters.append(openButton);
  }

  return { root, filters, main };
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
  const picked = value.caseId ? (cases.find((item) => item.caseId === value.caseId) ?? { caseId: value.caseId, title: '선택한 사건' }) : null; // 목록에 없는 사건으로 걸러 왔어도 거르는 중 표시와 해제 버튼은 보인다
  if (picked) {
    const label = element('span', 'cm-filter-title', '사건');
    const chosen = element('div', 'cm-case-chosen');
    const clear = button('해제', () => onChange({ ...value, caseId: null }), 'cm-case-clear');
    clear.setAttribute('aria-label', `사건 ${picked.title} 거르기 해제`);
    chosen.append(element('span', '', picked.title), clear);
    caseGroup.append(label, chosen);
  } else {
    // 입력은 아래 목록만 다시 그리고, 사건을 고를 때만 거르기를 바꾼다(거르기를 바꾸면 이 칸이 새로 그려져 입력이 사라진다)
    const search = element('input', 'cm-case-search');
    search.type = 'search';
    search.id = 'cm-filter-case';
    search.placeholder = '사건 이름으로 찾기';
    search.autocomplete = 'off';
    search.setAttribute('aria-controls', 'cm-case-results');
    const label = element('label', 'cm-filter-title', '사건');
    label.htmlFor = search.id;
    const results = element('div');
    results.id = 'cm-case-results';
    const status = element('p', 'visually-hidden'); // 낭독은 개수만(한 글자마다 사건 제목을 다 읽지 않게)
    status.setAttribute('role', 'status');
    search.addEventListener('input', () => {
      const word = search.value.trim().toLowerCase();
      if (!word) { results.replaceChildren(); status.textContent = ''; return; }
      const found = cases.filter(({ title }) => title.toLowerCase().includes(word));
      found.sort((a, b) => Number(b.title.toLowerCase().startsWith(word)) - Number(a.title.toLowerCase().startsWith(word))); // 제목이 단어로 시작하는 사건을 위로(나머지는 원래 순서 유지: 정렬은 안정 정렬)
      status.textContent = found.length ? `사건 ${found.length}건` : '맞는 사건이 없어요';
      if (!found.length) { results.replaceChildren(element('p', 'cm-case-empty', '맞는 사건이 없어요')); return; }
      const list = element('ul', 'cm-case-results');
      found.forEach(({ caseId, title }) => {
        const item = element('li');
        item.append(button(title, () => onChange({ ...value, caseId }), 'cm-case-pick'));
        list.append(item);
      });
      results.replaceChildren(list);
    });
    caseGroup.append(label, search, status, results);
  }
  box.append(group, caseGroup);
  return box;
}

export function popularList(popular, navigate) {
  const box = element('section', 'cm-popular');
  box.setAttribute('aria-labelledby', 'cm-popular-title');
  const title = element('h2', 'cm-side-title', '많이 본 글');
  title.id = 'cm-popular-title';
  if (API_MODE === 'mock') title.append(mockTag()); // 목 모드에서만 — 실제 모드는 C-1 · C-6 응답의 많이 본 글이다(FE-142)
  const list = element('ol', 'cm-popular-list');
  popular.forEach(({ postId, title: text }) => {
    const item = element('li');
    item.append(postLink(text, postId, navigate));
    list.append(item);
  });
  box.append(title, popular.length ? list : element('p', 'cm-hint', '아직 많이 본 글이 없어요.'));
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

// ---- 글쓰기(글쓰기 페이지) ----
// 말머리 · 사건 · 제목 · 본문 · 사진(최대 3장) · 내 판결 붙이기. 로그아웃 상태면 로그인 안내만 둔다.
// 올리면 onPosted(새 글)를 부른다
export function composer({ api, navigate, cases, onPosted }) {
  const box = element('section', 'cm-compose');
  box.setAttribute('aria-labelledby', 'cm-compose-title');
  const head = element('div', 'cm-compose-head');
  const title = element('h2', 'cm-side-title', '편지 쓰기');
  title.id = 'cm-compose-title';
  head.append(title);
  box.append(head);

  if (!currentUser()) {
    box.append(element('p', 'cm-compose-note', '로그인하면 글을 쓰고 댓글 · 좋아요를 남길 수 있어요.'),
      button('로그인하기', () => navigate('login'), 'primary-button cm-compose-login'));
    return box;
  }

  const form = element('form', 'cm-form');
  form.noValidate = true;
  // 받는 사람 줄(FE-94): 고른 사건을 문장으로 보여 준다. 사건 없이 쓰면 모두에게 보내는 글이다
  const recipient = element('p', 'cm-recipient');
  const paintRecipient = () => {
    const picked = caseSelect.selectedOptions[0];
    recipient.replaceChildren('받는 사람 ', element('strong', '', caseSelect.value ? picked.text : '커뮤니티 모두'), caseSelect.value ? '을 본 사람들' : '에게');
  };

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
  caseSelect.addEventListener('change', paintRecipient);
  paintRecipient();

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
      ? 'AI 판결 · 실제 판결은 붙지 않아요. 이 사건을 아직 판결하지 않은 사람에게는 접혀 보여요.'
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
    paintRecipient();
  });

  const error = element('p', 'cm-error');
  error.setAttribute('role', 'alert');
  // 서명란: 올릴 때 서명이 한 획으로 쓰이고 글이 올라간다. 올리지 못하면 서명을 지운다
  const signField = element('div', 'cm-field cm-sign');
  const signLine = element('div', 'cm-sign-line');
  const signHint = element('span', 'cm-sign-hint', '올릴 때 서명이 남아요');
  const nickname = currentUser()?.nickname ?? '';
  const signature = signatureSvg(signerSeed(), { label: `${nickname}의 서명`, blank: true });
  signLine.append(signHint, signature);
  signField.append(element('span', 'cm-label', '서명'), signLine);
  const submit = element('button', 'primary-button cm-submit', '서명하고 올리기');
  submit.type = 'submit';
  const actions = element('div', 'cm-form-actions');
  actions.append(error, submit);
  function showError(text) { error.textContent = text; }

  form.append(recipient, categories, caseField, titleField.field, bodyField.field, ...(PHOTO_UPLOAD ? [photoField] : []), verdictField, signField, actions);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    showError('');
    const titleText = titleField.input.value.trim();
    const bodyText = bodyField.input.value.trim();
    if (!titleText) { showError('제목을 적어 주세요.'); titleField.input.focus(); return; }
    if (!bodyText) { showError('내용을 적어 주세요.'); bodyField.input.focus(); return; }
    submit.disabled = true;
    submit.textContent = '서명하는 중…';
    signHint.hidden = true;
    await writeSignature(signature);
    if (!box.isConnected) return;
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
      showError(composeErrorText(failure));
      submit.disabled = false;
      submit.textContent = '서명하고 올리기';
      eraseSignature(signature);
      signHint.hidden = false;
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
