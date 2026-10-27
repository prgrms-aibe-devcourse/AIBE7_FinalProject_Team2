import { button, element } from '../../components/siteLayout.js';
import { confirmChange, labeledField } from './adminKit.js';
import { PHOTO_ROLE, errorText, formatDateTime } from './adminLabels.js';

// 사건 사진 · 신문 지면 (A-13-1 ~ A-13-9, FE-106). 사건 검수 상세(adminCasePage.js)에 붙는 부분이다.
//
// 사진은 **사람이 만들어 올린다**. AI는 참고할 한글 프롬프트만 추천한다(BE-91 결정) — 그래서 이 화면의
// 흐름은 "프롬프트 받기 → 복사해서 밖에서 그림 만들기 → 파일 올리기"다.
//
// 발단 · 결론은 사건마다 역할별로 한 장이다(uk_case_incident_photo_case_role). 같은 역할로 다시 올리면
// 서버가 교체하므로 화면도 "추가"가 아니라 "올리기 · 교체"로 말한다.
//
// 공개한 사건은 사진 · 지면을 바꿀 수 없다(A-11과 같은 규칙). 버튼을 숨기지 않고 잠근 뒤 이유를 적는다 —
// 왜 못 바꾸는지 모른 채 버튼을 찾아 헤매지 않게(FE-105 규칙).
const ROLES = ['ORIGIN', 'CONCLUSION'];
const ACCEPT = 'image/jpeg,image/png,image/webp';
const MAX_MB = 10;

/** 파일 고르기 + 올리기 한 벌. onPick은 File을 받아 Promise를 돌려준다 */
function uploadRow(label, { onUpload, onFailure, disabled, submitText = '올리기' }) {
  const input = element('input');
  Object.assign(input, { type: 'file', accept: ACCEPT, disabled });
  const field = labeledField(label, input, `JPG · PNG · WebP, ${MAX_MB}MB 이하`);
  const error = element('p', 'admin-error');
  error.setAttribute('role', 'alert');
  const send = button(submitText, async () => {
    const file = input.files?.[0];
    error.textContent = '';
    if (!file) { error.textContent = '올릴 파일을 골라 주세요.'; input.focus(); return; }
    send.disabled = true;
    try {
      await onUpload(file);
    } catch (failure) {
      onFailure(failure, '사진을 올리지 못했어요.', error);
      send.disabled = false;
    }
  }, 'secondary-button');
  send.disabled = disabled;
  const wrap = element('div', 'admin-upload');
  wrap.append(field, send, error);
  return wrap;
}

/** 추천 프롬프트 상자. 받아서 보여 주고, 복사 버튼을 단다(관리자가 그림 도구에 붙여 넣는다). promptRef가 있으면 받은 문구를 읽을 길을 터 준다(업로드 기록용) */
function promptBox({ label, onSuggest, hintField, promptRef }) {
  const box = element('div', 'admin-prompt');
  const output = element('textarea', 'admin-prompt-text');
  Object.assign(output, { rows: 3, readOnly: true, hidden: true });
  output.setAttribute('aria-label', `${label} 추천 문구`);
  if (promptRef) promptRef.read = () => output.value;
  const error = element('p', 'admin-error');
  error.setAttribute('role', 'alert');
  const copy = button('복사', async () => {
    try {
      await navigator.clipboard.writeText(output.value);
      copy.textContent = '복사함';
      setTimeout(() => { copy.textContent = '복사'; }, 2000);
    } catch {
      // 클립보드를 막아 둔 브라우저 · http 환경이 있다. 그때는 직접 고르도록 돕는다
      output.readOnly = false;
      output.select();
      error.textContent = '복사하지 못했어요. 글상자에서 직접 복사해 주세요.';
    }
  }, 'secondary-button');
  copy.hidden = true;
  const ask = button('추천 문구 받기', async () => {
    ask.disabled = true;
    error.textContent = '';
    try {
      const result = await onSuggest();
      output.value = result?.prompt ?? '';
      output.hidden = false;
      copy.hidden = false;
      output.focus();
    } catch (failure) {
      error.textContent = errorText(failure, '추천 문구를 받지 못했어요.');
    } finally {
      ask.disabled = false;
    }
  }, 'secondary-button');
  const actions = element('div', 'admin-actions');
  actions.append(ask, copy);
  box.append(...(hintField ? [hintField] : []), actions, output, error);
  return box;
}

function photoPreview(url, alt) {
  if (!url) return element('p', 'admin-sub', '아직 올린 사진이 없어요.');
  const figure = element('figure', 'admin-photo');
  const image = element('img');
  Object.assign(image, { src: url, alt, loading: 'lazy' });
  figure.append(image);
  return figure;
}

/**
 * 사진 · 신문 지면 부분을 그린다.
 *
 * @param ctx.media  { photos, paper } — A-13-5 · A-13-7을 미리 불러 둔 값(없으면 null)
 * @param ctx.after  바꾼 뒤 부르는 함수(사건을 다시 불러 그리고 알림줄을 띄운다)
 */
export function mediaSection(ctx, data) {
  const { api, caseId, section, media, after, handleFailure } = ctx;
  const editable = data.status !== 'PUBLISHED';
  const parts = [];

  if (!editable) {
    parts.push(element('p', 'admin-warn', '공개한 사건이에요. 사진 · 신문 지면은 바꿀 수 없어요. (A-11과 같은 규칙)'));
  }

  // ── 대표사진 (A-13-1 · A-13-3). 신문 지면 · 목록 카드에 쓰는 사진이다
  const representative = element('div', 'admin-media-block');
  representative.append(
    element('h3', '', '대표사진'),
    element('p', 'admin-sub', '목록 카드와 신문 지면에 쓰여요. 올리면 사건의 썸네일 주소가 이 사진으로 바뀌어요.'),
    photoPreview(data.content.thumbnailUrl, '사건 대표사진'),
    promptBox({ label: '대표사진', onSuggest: () => api.suggestRepresentativePrompt(caseId) }),
    uploadRow('대표사진 파일', {
      disabled: !editable,
      onFailure: handleFailure,
      submitText: data.content.thumbnailUrl ? '교체하기' : '올리기',
      onUpload: async (file) => {
        await api.uploadRepresentativePhoto(caseId, file);
        await after('대표사진을 올렸어요.', 'media');
      },
    }),
  );
  parts.push(representative);

  // ── 발단 · 결론 사진 (A-13-2 · A-13-4 · A-13-5 · A-13-9). 역할마다 한 장 고정 자리다
  const hint = element('input');
  Object.assign(hint, { type: 'text', maxLength: 500, placeholder: '예: 다투는 장면을 더 강조해줘' });
  const incidentPrompt = { read: () => '' }; // 추천 문구 상자가 실제로 받은 문구(또는 고친 값)를 여기로 읽는다
  const incident = element('div', 'admin-media-block');
  incident.append(
    element('h3', '', '발단 · 결론 사진'),
    element('p', 'admin-sub', '자리가 둘(발단 · 결론)뿐이에요. 같은 자리에 다시 올리면 교체돼요.'),
    promptBox({
      label: '발단 사진',
      hintField: labeledField('추천에 덧붙일 말 (선택, 500자 이하)', hint),
      onSuggest: () => api.suggestIncidentPrompt(caseId, hint.value.trim() || undefined),
      promptRef: incidentPrompt,
    }),
  );

  const photos = media.photos ?? [];
  ROLES.forEach((role) => {
    const saved = photos.find((photo) => photo.role === role) ?? null;
    const slot = element('div', 'admin-photo-slot');
    const caption = element('input');
    Object.assign(caption, { type: 'text', maxLength: 200, value: saved?.caption ?? '' });
    const head = element('div', 'admin-candidate-head');
    head.append(element('strong', '', PHOTO_ROLE[role]));
    if (saved) head.append(element('span', 'admin-sub', `올린 때 ${formatDateTime(saved.createdAt)}`));
    const error = element('p', 'admin-error');
    error.setAttribute('role', 'alert');

    const remove = button('지우기', () => {
      if (!confirmChange(`${PHOTO_ROLE[role]}을 지울까요?`, '사용자 화면에서 바로 사라져요. 다시 올릴 수 있어요.')) return;
      remove.disabled = true;
      error.textContent = '';
      api.deleteIncidentPhoto(caseId, role)
        .then(() => after(`${PHOTO_ROLE[role]}을 지웠어요.`, 'media'))
        .catch((failure) => {
          handleFailure(failure, '사진을 지우지 못했어요.', error);
          remove.disabled = false;
        });
    });
    remove.setAttribute('aria-label', `${PHOTO_ROLE[role]} 지우기`);
    remove.disabled = !editable || !saved;

    slot.append(
      head,
      photoPreview(saved?.imageUrl, `${PHOTO_ROLE[role]} 미리보기`),
      labeledField('사진 설명 (선택, 200자 이하)', caption),
      uploadRow(`${PHOTO_ROLE[role]} 파일`, {
        disabled: !editable,
        onFailure: handleFailure,
        submitText: saved ? '교체하기' : '올리기',
        onUpload: async (file) => {
          // 추천 문구는 발단(ORIGIN)만 지원한다(BE-91) — 결론(CONCLUSION) 업로드에는 함께 보내지 않는다
          const prompt = role === 'ORIGIN' ? incidentPrompt.read().trim() || undefined : undefined;
          await api.uploadIncidentPhoto(caseId, { file, role, caption: caption.value.trim() || undefined, prompt });
          await after(`${PHOTO_ROLE[role]}을 올렸어요.`, 'media');
        },
      }),
      remove,
      error,
    );
    incident.append(slot);
  });
  parts.push(incident);

  // ── 신문 지면 (A-13-6 ~ A-13-8)
  parts.push(paperBlock(ctx, data, editable));
  return section('사진 · 신문 지면', 'media', ...parts);
}

/** 신문 지면: 없으면 생성 버튼, 있으면 수정 폼. 저장은 보낸 칸만 보낸다(A-11과 같은 방식) */
function paperBlock(ctx, data, editable) {
  const { api, caseId, media, after, handleFailure } = ctx;
  const block = element('div', 'admin-media-block');
  block.append(element('h3', '', '신문 지면'));
  const error = element('p', 'admin-error');
  error.setAttribute('role', 'alert');
  const paper = media.paper;

  if (!paper) {
    const generate = button('신문 지면 만들기', () => {
      generate.disabled = true;
      error.textContent = '';
      api.generateCasePaper(caseId)
        .then(() => after('신문 지면을 만들었어요.', 'media'))
        .catch((failure) => {
          handleFailure(failure, '신문 지면을 만들지 못했어요.', error);
          generate.disabled = false;
        });
    }, 'primary-button');
    generate.disabled = !editable;
    block.append(
      element('p', 'admin-sub', '아직 만들지 않았어요. 사건 내용으로 AI가 지면을 지어요. 지은 뒤에 고칠 수 있어요.'),
      generate, error,
    );
    return block;
  }

  const headline = element('input');
  Object.assign(headline, { type: 'text', maxLength: 200, value: paper.headline ?? '' });
  const deck = element('input');
  Object.assign(deck, { type: 'text', maxLength: 300, value: paper.deck ?? '' });
  const caption = element('input');
  Object.assign(caption, { type: 'text', maxLength: 200, value: paper.caption ?? '' });
  const article = element('textarea');
  Object.assign(article, { rows: 8, value: (paper.article ?? []).join('\n\n') });

  const form = element('form', 'admin-paper-form');
  form.noValidate = true;
  const save = element('button', 'primary-button', '지면 저장');
  save.type = 'submit';
  save.dataset.anchor = 'media:paper';
  save.disabled = !editable;
  [headline, deck, caption, article].forEach((field) => { field.disabled = !editable; });

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    error.textContent = '';
    const paragraphs = article.value.split('\n\n').map((part) => part.trim()).filter(Boolean);
    if (!headline.value.trim()) { error.textContent = '헤드라인을 입력해 주세요.'; headline.focus(); return; }
    if (!paragraphs.length) { error.textContent = '본문을 한 문단 이상 써 주세요.'; article.focus(); return; }
    // 보낸 칸만 보낸다. 선택 칸을 비우면 null(지움)이다 — A-11 · A-13-8과 같은 방식
    const changes = {
      headline: headline.value.trim(),
      deck: deck.value.trim() || null,
      caption: caption.value.trim() || null,
      article: paragraphs,
    };
    save.disabled = true;
    api.updateCasePaper(caseId, changes)
      .then(() => after('신문 지면을 저장했어요.', 'media:paper'))
      .catch((failure) => {
        handleFailure(failure, '지면을 저장하지 못했어요.', error);
        save.disabled = false;
      });
  });

  form.append(
    labeledField('헤드라인 (1 ~ 200자)', headline),
    labeledField('부제 (선택, 300자 이하)', deck),
    labeledField('대표사진 설명 (선택, 200자 이하)', caption),
    labeledField('본문 (빈 줄로 문단을 나눠요)', article),
    error, save,
  );
  block.append(element('p', 'admin-sub', `마지막 수정 ${formatDateTime(paper.updatedAt)}`), form);
  return block;
}
