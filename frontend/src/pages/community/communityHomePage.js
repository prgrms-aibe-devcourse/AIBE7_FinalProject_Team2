import { button, element, mockTag } from '../../components/siteLayout.js';
import { profileFace } from '../../components/personCards.js';
import { normalizeDeco } from '../../utils/profileDeco.js';
import { scriptFor } from '../overview/overviewPage.js';
import { NOT_READY, authorButton, clip, communityShell, composer, filterControls, popularList, postLink, svgIcon } from './communityParts.js';

// S-18 커뮤니티 메인 (FE-89). 메인 책상의 노트북 · 헤더 "커뮤니티"로 들어온다. 글 목록은 /community/posts(communityPage.js)
// 가운데: 지금 이야기가 많은 사건(한 장씩 넘기는 슬라이드) → 레벨 랭킹(시상대 + 4 ~ 10위 줄) → 추천 사건(신문 세 단).
// 왼쪽 거르기 · 오른쪽 글쓰기 칸은 글 목록과 같은 틀(communityShell)이다. 거르기를 고르면 그 조건의 글 목록으로 간다
const REST_GAP = 12; // .cm-rest 카드 간격(community.css와 같다)
const REST_INSET = 46; // 4 ~ 10위 첫 카드를 왼쪽 흐림 칸(.more-left 40px)보다 조금 안쪽에 둔다. 화살표는 세 장씩 넘겨 이 간격이 유지된다
const REST_STEP = 3; // 화살표 한 번에 넘기는 카드 수
const SETTLE_MS = 140; // 슬라이드 · 4 ~ 10위 줄이 멈췄다고 보는 시간(넘기는 중에 되돌리면 튄다)

export function renderCommunityHomePage(container, { api, navigate }) {
  const { root, filters: filterHost, main, side, openButton, closeButton, closeCompose } = communityShell({ navigate });
  container.replaceChildren(root);
  const active = () => container.contains(root);

  if (!api.getCommunityHome) {
    main.append(element('p', 'cm-note', NOT_READY));
    return undefined;
  }

  const head = element('div', 'cm-home-head');
  head.append(routeLink('모든 글 보기', '/community/posts', () => navigate('communityPosts')), openButton);
  const body = element('div', 'cm-home-body');
  main.append(head, body);

  async function load() {
    const loading = element('p', 'cm-note', '커뮤니티를 불러오고 있어요…');
    loading.setAttribute('role', 'status');
    body.replaceChildren(loading);
    let data;
    try {
      data = await api.getCommunityHome();
    } catch {
      if (!active()) return;
      const box = element('div', 'cm-empty');
      box.append(element('p', '', '커뮤니티를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'), button('다시 불러오기', () => load(), 'secondary-button'));
      body.replaceChildren(box);
      return;
    }
    if (!active()) return;
    if (!filterHost.querySelector('.cm-filter-groups')) {
      filterHost.append(filterControls({ value: { category: null, caseId: null }, cases: data.cases, onChange: ({ category, caseId }) => navigate('communityPosts', { category: category ?? null, filterCaseId: caseId }) }));
      side.append(composer({ api, navigate, cases: data.cases, closeButton, closeCompose, onPosted: (post) => { closeCompose(); navigate('communityPosts', { justPosted: post.postId }); } }),
        popularList(data.popular, navigate));
    }
    body.replaceChildren(
      ...(data.hotCases.length ? [hotSection(data.hotCases)] : []),
      rankSection(data.ranking),
      ...(data.recommendedCases.length ? [recoSection(data.recommendedCases)] : []),
    );
  }

  // 앱 안 이동 링크: 주소가 있는 진짜 링크(새 탭 열기 · 주소 복사가 된다), 그냥 누르면 앱 안에서 이동한다(communityParts.js postLink와 같은 방식)
  function routeLink(text, href, go, className = '') {
    const link = element('a', className, text);
    link.href = href;
    link.addEventListener('click', (event) => {
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      go();
    });
    return link;
  }
  // 사건 글 목록으로: 사건 거르기 값은 주소가 아니라 기록 항목에 담긴다(communityPage.js) — 새 탭으로 열면 거르지 않은 목록이 열린다
  const caseListLink = (caseId, count, className = 'cm-home-more') => routeLink(`이 사건 글 ${count}개 모두 보기`, '/community/posts', () => navigate('communityPosts', { filterCaseId: caseId }), className);

  function sectionHead(titleText, id, { tag = false, note = null } = {}) {
    const box = element('div', 'cm-home-section-head');
    const title = element('h2', 'cm-home-title', titleText);
    title.id = id;
    if (tag) title.append(mockTag());
    box.append(title);
    if (note) box.append(note);
    return box;
  }

  // 사건 글 몇 개: 글쓴이 명함 버튼 · 제목 · 좋아요 · 댓글 수(댓글 내용은 보이지 않는다)
  function briefList(posts) {
    if (!posts.length) return element('p', 'cm-home-empty', '아직 이 사건 글이 없어요.');
    const list = element('ul', 'cm-brief-list');
    list.append(...posts.map((post) => {
      const item = element('li', 'cm-brief');
      const title = element('h3', 'cm-brief-title');
      title.append(postLink(post.title, post.postId, navigate));
      const counts = element('p', 'cm-brief-counts');
      counts.append(element('span', '', `좋아요 ${post.likeCount}`), element('span', '', `댓글 ${post.commentCount}`));
      item.append(authorButton(post.author), title, counts);
      return item;
    }));
    return list;
  }

  // ── 지금 이야기가 많은 사건: 한 장씩 넘기는 슬라이드. 장마다 오려 낸 기사 조각 + 그 사건 글. 자동으로 넘기지 않는다
  function hotSection(hotCases) {
    const section = element('section', 'cm-hot');
    section.setAttribute('aria-roledescription', '슬라이드 묶음');
    section.setAttribute('aria-labelledby', 'cm-hot-title');
    const note = element('p', 'cm-home-note', '최근 하루 글 · 댓글 기준');
    note.append(mockTag());
    const sectionTop = sectionHead('지금 이야기가 많은 사건', 'cm-hot-title', { note });
    const nav = element('div', 'cm-hot-nav');
    const prev = arrowButton('prev', '이전 사건');
    const next = arrowButton('next', '다음 사건');
    const count = element('span', 'cm-hot-count');
    count.setAttribute('aria-live', 'polite');
    nav.append(prev, count, next);
    if (hotCases.length > 1) sectionTop.append(nav);
    const track = element('div', 'cm-hot-track');
    track.id = 'cm-hot-track';
    prev.setAttribute('aria-controls', track.id);
    next.setAttribute('aria-controls', track.id);
    // 끝없이 돌아간다(셋째 뒤에 다시 첫째): 진짜 장 앞뒤에 복제본을 한 장씩 두고(가운데가 진짜), 멈췄을 때 복제본에 와 있으면 소리 없이 진짜 장으로 되돌린다.
    // 복제본은 읽지도 누르지도 않는다(4 ~ 10위 줄과 같은 방식). 장이 하나면 복제본도 화살표도 없다
    const total = hotCases.length;
    const looping = total > 1;
    const slides = hotCases.map((hot, index) => hotSlide(hot, index, total));
    const cloneOf = (index) => {
      const copy = hotSlide(hotCases[index], index, total);
      copy.dataset.clone = 'true';
      copy.setAttribute('aria-hidden', 'true');
      return copy;
    };
    track.append(...(looping ? [cloneOf(total - 1)] : []), ...slides, ...(looping ? [cloneOf(0)] : []));
    section.append(sectionTop, track);

    // 넘기기: 화살표 · 손가락 밀기 · 가로 스크롤 모두 같은 자리(scroll-snap)를 쓴다. 보이지 않는 장은 inert로 두어 Tab이 넘어가지 않는다
    const offset = looping ? 1 : 0; // 앞 복제본 한 장
    let shown = -1; // 지금 보는 장(트랙 자리)
    const position = () => Math.min(track.children.length - 1, Math.max(0, Math.round(track.scrollLeft / (track.clientWidth || 1))));
    const go = (index, behavior) => (behavior === 'instant' ? jumpTo(track, index * track.clientWidth) : track.scrollTo({ left: index * track.clientWidth, behavior }));
    const paint = () => {
      const index = position();
      if (index === shown) return;
      shown = index;
      count.textContent = `${(index - offset + total) % total + 1} / ${total}`;
      [...track.children].forEach((slide, i) => { slide.inert = i !== index || slide.dataset.clone === 'true'; });
    };
    const recenter = () => {
      if (!looping) return;
      if (shown === 0) go(total, 'instant');
      else if (shown === total + 1) go(1, 'instant');
    };
    prev.addEventListener('click', () => { recenter(); go(position() - 1); });
    next.addEventListener('click', () => { recenter(); go(position() + 1); });
    let settle;
    track.addEventListener('scroll', () => { paint(); clearTimeout(settle); settle = setTimeout(recenter, SETTLE_MS); }, { passive: true }); // 멈춘 뒤에 되돌린다(넘기는 중에 건드리면 튄다)
    // 폭이 바뀌면 보던 장에 다시 맞춘다(처음에는 진짜 첫 장부터)
    let sized = false;
    watchSize(track, () => { go(sized ? Math.max(shown, 0) : offset, 'instant'); sized = true; paint(); });
    return section;
  }

  function hotSlide({ case: item, talkCount, postCount, posts }, index, total) {
    const slide = element('div', `cm-hot-slide${index % 2 ? ' is-alt' : ''}`); // 기울기는 장 번호로 정한다(복제본이 원본과 같게)
    slide.setAttribute('role', 'group');
    slide.setAttribute('aria-roledescription', '슬라이드');
    slide.setAttribute('aria-label', `${index + 1} / ${total}: ${item.title}`);
    const wrap = element('div', 'cm-hot-clip-wrap');
    const clipping = element('article', 'cm-hot-clipping');
    const kicker = element('p', 'cm-hot-kicker');
    kicker.append(element('span', '', item.crimeCategoryLabel), element('span', '', `최근 하루 글 · 댓글 ${talkCount}개`));
    clipping.append(kicker, element('h3', 'cm-hot-headline', item.headline), element('p', 'cm-hot-deck', item.deck));
    if (item.thumbnailUrl) {
      const photo = element('img', 'cm-hot-photo');
      photo.src = item.thumbnailUrl;
      photo.alt = '';
      photo.decoding = 'async';
      if (index > 0) photo.loading = 'lazy';
      clipping.append(photo);
    } else {
      const article = element('div', 'cm-hot-article');
      article.append(...item.article.map((text) => element('p', '', text)));
      clipping.append(article);
    }
    const facts = element('p', 'cm-hot-facts');
    const people = element('span');
    people.append(element('b', '', `${item.participantCount.toLocaleString()}명`), ' 판결');
    facts.append(people, element('span', '', `약 ${item.estimatedMinutes}분`));
    // 판결 체험 대본이 있는 사건만 "판결하기"(없는 사건은 사건 정보에서 "준비 중"이 나온다, caseListPage와 같은 기준)
    const playable = Boolean(scriptFor(item.caseId, item.title));
    clipping.append(facts, routeLink(playable ? '이 사건 판결하기' : '사건 보기', `/cases/${item.caseId}`, () => navigate('caseDetail', { caseId: item.caseId }), 'cm-hot-try'));
    wrap.append(clip(), clipping);
    const postsBox = element('div', 'cm-hot-posts');
    postsBox.append(element('p', 'cm-hot-posts-label', '이 사건 글'), briefList(posts));
    if (postCount) postsBox.append(caseListLink(item.caseId, postCount));
    slide.append(wrap, postsBox);
    return slide;
  }

  // ── 레벨 랭킹: 위 셋은 시상대(가운데 1위가 가장 높다), 아래 일곱은 작은 카드 한 줄. 카드는 프로필카드 그대로, 아래 칸은 공개한 성향 이름
  function rankSection(ranking) {
    const section = element('section', 'cm-rank');
    section.setAttribute('aria-labelledby', 'cm-rank-title');
    section.append(sectionHead('레벨 랭킹', 'cm-rank-title', { tag: true, note: element('p', 'cm-home-note', '레벨은 사건 체험과 커뮤니티 활동으로 올라요.') }));
    if (!ranking.length) {
      section.append(element('p', 'cm-home-empty', '아직 랭킹이 없어요.'));
      return section;
    }
    const podium = element('ol', 'cm-podium');
    podium.append(...ranking.slice(0, 3).map((person) => {
      const item = element('li', `is-${person.rank}`);
      const step = element('div', 'cm-step', `${person.rank}위`);
      step.append(element('small', '', `Lv.${person.level}`));
      item.append(rankCard(person), step);
      return item;
    }));
    section.append(podium);
    const others = ranking.slice(3);
    if (others.length) section.append(restRow(others));
    return section;
  }

  function rankCard(person) {
    const box = element('div', 'cm-rank-card cm-cards'); // 커피 색은 카드 둘레에서만(community.css .cm-cards)
    box.append(profileFace(normalizeDeco(person.profileCard), person.nickname, person.shownType?.type.name ?? null));
    return box;
  }

  // 4 ~ 10위: 카드가 너무 작아질 자리면 줄이지 않고 좌우로 넘긴다(최소 폭은 community.css --rest-min).
  // 넘길 때는 끝없이 돌아간다(10위 뒤에 다시 4위): 진짜 카드 앞뒤에 복제본을 한 벌씩 두고(가운데가 진짜),
  // 멈췄을 때 복제본 쪽으로 반 벌 이상 가 있으면 한 벌 폭만큼 소리 없이 되돌린다(같은 그림이라 이어져 보인다). 복제본은 읽지도 누르지도 않는다
  function restRow(others) {
    const wrap = element('div', 'cm-rest-wrap');
    const rest = element('ol', 'cm-rest');
    rest.id = 'cm-rest';
    rest.start = others[0].rank;
    const prev = arrowButton('prev', '앞 순위 보기');
    const next = arrowButton('next', '뒤 순위 보기');
    prev.setAttribute('aria-controls', rest.id);
    next.setAttribute('aria-controls', rest.id);
    const restItem = (person, clone) => {
      const item = element('li', clone ? 'is-clone' : '');
      if (clone) { item.setAttribute('aria-hidden', 'true'); item.inert = true; }
      const no = element('span', 'cm-rank-no', `${person.rank}위`);
      no.append(element('small', '', `Lv.${person.level}`));
      item.append(no, rankCard(person));
      return item;
    };
    rest.append(...others.map((person) => restItem(person, true)), ...others.map((person) => restItem(person, false)), ...others.map((person) => restItem(person, true)));
    wrap.append(prev, rest, next);

    const setWidth = () => rest.children[others.length].offsetLeft - rest.children[0].offsetLeft; // 한 벌(카드 + 간격) 폭
    const stepWidth = () => (rest.children[1].offsetLeft - rest.children[0].offsetLeft) * REST_STEP;
    const jump = (left) => jumpTo(rest, left);
    const recenter = () => {
      const set = setWidth();
      if (rest.scrollLeft < set * 0.5) jump(rest.scrollLeft + set);
      else if (rest.scrollLeft > set * 1.5) jump(rest.scrollLeft - set);
    };
    prev.addEventListener('click', () => { recenter(); rest.scrollBy({ left: -stepWidth() }); });
    next.addEventListener('click', () => { recenter(); rest.scrollBy({ left: stepWidth() }); });
    let settle;
    rest.addEventListener('scroll', () => { clearTimeout(settle); settle = setTimeout(recenter, SETTLE_MS); }, { passive: true });
    let fit = null;
    watchSize(wrap, () => {
      wrap.classList.remove('is-fit'); // 화살표 칸 · 복제본이 있는 폭으로 먼저 재야 진짜 카드가 넘치는지 안다
      const nowFit = setWidth() - REST_GAP <= rest.clientWidth;
      wrap.classList.toggle('is-fit', nowFit);
      rest.classList.toggle('more-left', !nowFit);
      rest.classList.toggle('more-right', !nowFit);
      if (!nowFit && fit !== false) jump(setWidth() - REST_INSET); // 처음(또는 다시 넘치게 됐을 때)은 진짜 첫 카드부터, 왼쪽 흐림 칸을 비켜서
      fit = nowFit;
    });
    return wrap;
  }

  // ── 추천 사건: 슬라이드에 없는 사건, 신문 지면처럼 세 단
  function recoSection(recommended) {
    const section = element('section', 'cm-reco-block');
    section.setAttribute('aria-labelledby', 'cm-reco-title');
    const columns = element('div', 'cm-reco');
    columns.append(...recommended.map(({ case: item, postCount, posts }) => {
      const column = element('section', 'cm-reco-col');
      column.setAttribute('aria-label', item.title);
      column.append(element('p', 'cm-reco-label', `${item.crimeCategoryLabel} · ${item.participantCount.toLocaleString()}명 판결`),
        element('h3', 'cm-reco-title', item.title), element('p', 'cm-reco-intro', item.shortIntro), briefList(posts));
      if (postCount) column.append(caseListLink(item.caseId, postCount));
      return column;
    }));
    section.append(sectionHead('추천 사건', 'cm-reco-title'), columns);
    return section;
  }

  // 크기가 바뀔 때마다 부른다. 화면을 떠나면 스스로 멈춘다
  function watchSize(node, onResize) {
    const observer = new ResizeObserver(() => {
      if (!active()) { observer.disconnect(); return; }
      onResize();
    });
    observer.observe(node);
  }

  return load();
}

// 소리 없이 자리만 옮긴다: CSS의 부드러운 스크롤(scroll-behavior: smooth)을 잠깐 끄고 값을 바꾼다. scrollTo의 behavior: 'instant'는 브라우저마다 달라(사파리) 쓰지 않는다
function jumpTo(node, left) {
  node.style.scrollBehavior = 'auto';
  node.scrollLeft = left;
  node.style.scrollBehavior = '';
}

function arrowButton(direction, label) {
  const node = element('button', 'cm-arrow');
  node.type = 'button';
  node.setAttribute('aria-label', label);
  node.append(svgIcon('0 0 16 16', null, [[direction === 'prev' ? 'M10 3 5 8l5 5' : 'm6 3 5 5-5 5']]));
  return node;
}
