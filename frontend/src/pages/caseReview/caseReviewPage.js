import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { canGoBackInApp } from '../../app/router.js';

// S-21 사건 리뷰 (FE-55). 8막 마지막 대사창의 "리뷰하기"로 들어오는 독자 엽서 화면.
// 위: 이 사건의 평균 별점 → 내 엽서(별점 + 내 의견, 사건마다 한 장) → 아래: 다른 독자의 엽서.
// 리뷰 API가 아직 명세에 없어 목 모드에서만 동작한다(mocks/caseReviewMockApi.js). 실제 모드에는 api.getCaseReviews가 없어 "준비 중"으로 안내한다.
const COMMENT_MAX = 200;
const STAR_LABELS = ['', '아쉬웠어요', '그저 그랬어요', '괜찮았어요', '좋았어요', '정말 좋았어요'];
const NOT_READY = '사건 리뷰는 아직 준비 중이에요. 곧 열려요.';

const stars = (rating) => '★'.repeat(rating) + '☆'.repeat(5 - rating);

// 화면(/cases/:caseId/opinion)과 8막 팝업(caseReviewPopup.js)이 같은 내용을 쓴다
export function renderCaseReviewPage(container, { caseId, api, navigate }) {
  const root = element('div', 'case-review-page');
  const main = element('main', 'case-review-main');
  root.append(renderSiteHeader({ navigate }), main);
  container.replaceChildren(root);
  const heading = element('h1', 'case-review-heading', '독자 엽서');
  // 들어온 곳(8막 · 사건 상세 · 커뮤니티 등)과 상관없이 맞는 문구: 앱 안의 이전 화면으로, 바로 들어왔으면 사건 목록으로 (PR #190 리뷰 반영)
  const back = button('← 이전으로', () => (canGoBackInApp() ? history.back() : navigate('list')), 'case-review-back');
  const body = element('div', 'case-review');
  main.append(back, heading, body);
  return mountCaseReview(body, { caseId, api, navigate });
}

// host 안에 평균 별점 · 내 엽서 · 다른 독자 엽서를 그린다. 불러오기가 끝나면 풀린다
export async function mountCaseReview(main, { caseId, api, navigate }) {
  if (!api.getCaseReviews) {
    main.append(element('p', 'case-review-note', NOT_READY));
    return;
  }
  const loading = element('p', 'case-review-note', '엽서를 불러오고 있어요…');
  loading.setAttribute('role', 'status');
  main.append(loading);

  let data;
  const active = () => main.isConnected; // 받는 사이 화면을 떠났거나 엽서를 닫았으면 그리지 않는다(app/README.md)
  try {
    data = await api.getCaseReviews(caseId);
  } catch (error) {
    if (!active()) return;
    loading.remove();
    main.append(failure(error?.code === 'CASE_NOT_FOUND' ? '사건을 찾을 수 없어요.' : '엽서를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'));
    return;
  }
  if (!active()) return;
  loading.remove();

  const summary = element('p', 'case-review-summary');
  const list = element('ul', 'case-review-list');
  const slot = element('div', 'case-review-slot');
  const listTitle = element('h2', 'case-review-subtitle', '다른 독자들의 엽서');
  main.append(summary, slot, listTitle, list);

  paintSummary(data);
  if (data.mine) slot.append(sentCard(data.mine));
  else slot.append(formCard());
  data.reviews.forEach((review) => list.append(readerCard(review)));
  if (!data.reviews.length) list.append(element('li', 'case-review-empty', '아직 도착한 엽서가 없어요. 첫 엽서를 남겨 보세요.'));

  function paintSummary({ count, averageRating }) {
    summary.replaceChildren();
    if (!count) { summary.textContent = '아직 별점이 없어요.'; return; }
    summary.append(element('b', 'case-review-avg', averageRating.toFixed(1)), ` / 5 · 독자 엽서 ${count}장`);
  }

  function failure(text) {
    const box = element('div', 'case-review-note');
    box.append(element('p', '', text), button('사건 목록으로', () => navigate('list'), 'secondary-button'));
    return box;
  }

  // 내 엽서 쓰기: 별점(라디오) + 내 의견 + 보내기. 한 번 보내면 바꿀 수 없어 보내기 전에 한 번 더 묻지는 않지만 안내를 단다
  function formCard() {
    const card = element('form', 'case-review-card is-form');
    card.noValidate = true;
    card.append(postcardHead('이 사건, 어땠나요?', '받는 곳: 내Law남불 신문 편집부'));

    const group = element('fieldset', 'case-review-stars');
    group.append(element('legend', '', '별점'));
    let rating = 0;
    const hint = element('span', 'case-review-star-hint', '별을 눌러 주세요');
    hint.setAttribute('aria-live', 'polite');
    const inputs = [1, 2, 3, 4, 5].map((value) => {
      const label = element('label', 'case-review-star');
      const input = element('input');
      input.type = 'radio';
      input.name = `rating-${caseId}`;
      input.value = String(value);
      input.className = 'visually-hidden';
      const face = element('span', '', '★');
      face.setAttribute('aria-hidden', 'true');
      label.append(input, face, element('span', 'visually-hidden', `${value}점 ${STAR_LABELS[value]}`));
      input.addEventListener('change', () => { rating = value; paint(); });
      group.append(label);
      return label;
    });
    function paint() {
      inputs.forEach((label, index) => label.classList.toggle('is-on', index < rating));
      hint.textContent = rating ? `${rating}점 · ${STAR_LABELS[rating]}` : '별을 눌러 주세요';
      group.removeAttribute('aria-invalid');
    }
    group.append(hint);

    const field = element('div', 'case-review-field');
    const label = element('label', '', '내 의견');
    label.htmlFor = `case-review-comment-${caseId}`;
    const textarea = element('textarea');
    textarea.id = label.htmlFor;
    textarea.rows = 4;
    textarea.maxLength = COMMENT_MAX;
    textarea.placeholder = '이 사건을 체험하고 든 생각을 적어 주세요';
    textarea.setAttribute('aria-describedby', `case-review-count-${caseId}`);
    const count = element('span', 'case-review-count', `0 / ${COMMENT_MAX}`);
    count.id = textarea.getAttribute('aria-describedby');
    textarea.addEventListener('input', () => { count.textContent = `${textarea.value.length} / ${COMMENT_MAX}`; textarea.removeAttribute('aria-invalid'); });
    field.append(label, textarea, count);

    const error = element('p', 'case-review-error');
    error.setAttribute('role', 'alert');
    error.hidden = true;
    const submit = element('button', 'primary-button', '엽서 보내기');
    submit.type = 'submit';
    const actions = element('div', 'case-review-actions');
    actions.append(element('p', 'case-review-guide', '보낸 엽서는 바꿀 수 없어요.'), submit);
    card.append(group, field, error, actions);

    const showError = (text, target) => { error.textContent = text; error.hidden = false; target?.focus(); };
    card.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (submit.disabled) return;
      const comment = textarea.value.trim();
      error.hidden = true;
      if (!rating) { group.setAttribute('aria-invalid', 'true'); showError('별점을 골라 주세요.', inputs[0].querySelector('input')); return; }
      if (!comment) { textarea.setAttribute('aria-invalid', 'true'); showError('내 의견을 한 줄 이상 적어 주세요.', textarea); return; }
      submit.disabled = true;
      submit.textContent = '보내는 중…';
      try {
        const mine = await api.postCaseReview(caseId, { rating, comment });
        data = await api.getCaseReviews(caseId).catch(() => ({ ...data, mine }));
        paintSummary(data);
        const sent = sentCard(mine, true);
        slot.replaceChildren(sent);
        sent.focus({ preventScroll: true });
      } catch (failed) {
        submit.disabled = false;
        submit.textContent = '엽서 보내기';
        if (failed?.code === 'ALREADY_REVIEWED') showError('이미 엽서를 보낸 사건이에요. 화면을 새로고침해 주세요.');
        else if (failed?.code === 'VALIDATION_ERROR') showError('입력값을 확인해 주세요.');
        else showError('엽서를 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.');
      }
    });
    return card;
  }

  // 이미 보낸 내 엽서(접수 도장). justSent면 방금 찍힌 도장 연출 + 안내
  function sentCard(mine, justSent = false) {
    const card = element('section', 'case-review-card is-sent');
    card.tabIndex = -1;
    card.setAttribute('aria-label', '내가 보낸 엽서');
    const mark = element('span', 'case-review-postmark');
    mark.setAttribute('aria-hidden', 'true');
    mark.append(element('b', '', '접수'), element('small', '', '완료'));
    if (!justSent) mark.classList.add('is-still');
    card.append(postcardHead('내가 보낸 엽서', `보낸 날 ${mine.createdAt}`), body(mine), mark,
      element('p', 'case-review-guide', justSent ? '엽서를 보냈어요. 고마워요! 보낸 엽서는 바꿀 수 없어요.' : '이미 엽서를 보낸 사건이에요. 보낸 엽서는 바꿀 수 없어요.'));
    const actions = element('div', 'case-review-actions');
    actions.append(button('다른 사건 보러 가기', () => navigate('list'), 'primary-button'));
    card.append(actions);
    return card;
  }

  function readerCard(review) {
    const item = element('li', 'case-review-card is-reader');
    item.append(body(review), element('p', 'case-review-by', `— ${review.nickname} · ${review.createdAt}`));
    return item;
  }

  function body({ rating, comment }) {
    const wrap = element('div', 'case-review-body');
    const line = element('p', 'case-review-rating', stars(rating));
    line.setAttribute('role', 'img');
    line.setAttribute('aria-label', `별점 5점 만점에 ${rating}점`);
    wrap.append(line, element('p', 'case-review-comment', comment));
    return wrap;
  }
}

// 엽서 머리: 제목 · 받는 곳 + 우표
function postcardHead(title, sub) {
  const head = element('div', 'case-review-head');
  const text = element('div');
  text.append(element('h2', '', title), element('p', '', sub));
  const stamp = element('span', 'case-review-stamp', '우표');
  stamp.setAttribute('aria-hidden', 'true');
  head.append(text, stamp);
  return head;
}
