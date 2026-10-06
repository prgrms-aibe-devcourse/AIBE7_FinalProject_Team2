import { renderLawInfoBox } from '../../components/lawInfoBox.js';
import { renderRecommendedRangeBar } from '../../components/recommendedRangeBar.js';
import { renderProgressSidebar } from './progressSidebar.js';
import { renderContent } from '../../utils/contentLines.js';

// 양측 주장 카드 위 안내 (정보 구조 S-04 v1.10, FE-14). 서버 응답에 없는 화면 고정 문구다.
// "양형 이유" 같은 출처 표현은 쓰지 않는다 — 재판부가 인정한 사정인지가 판결 공개 전에 드러난다.
// 매핑에 없는 sectionType은 안내 없이 그린다.
const ARGUMENT_NOTICES = {
  PROSECUTOR: '판결문에 나온 사정을 검사 측 입장에서 정리했어요.',
  DEFENSE: '판결문에 나온 사정을 피고인 · 변호인 측 입장에서 정리했어요.',
};

export async function renderReviewPage(container, { caseId, caseHeader, api, navigate }) {
  const root = document.createElement('div');
  root.className = 'review-page';
  container.replaceChildren(root);
  // 컨테이너가 다른 화면으로 바뀌면 이전 요청은 그 화면을 덮어쓰지 않는다.
  const active = () => container.contains(root);
  let loading = false;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function button(text, action, className = 'secondary-button') {
    const node = element('button', className, text);
    node.type = 'button';
    node.addEventListener('click', action);
    return node;
  }
  function showError(error) {
    if (!active()) return;
    const box = element('section', 'message-panel');
    box.setAttribute('role', 'alert');
    if (error?.code === 'INVALID_STATE') {
      box.append(element('p', '', `지금 단계에서는 이 화면을 볼 수 없어요. (현재 상태: ${error.currentStatus})`));
      console.info('현재 상태:', error.currentStatus);
    } else {
      box.append(element('p', '', '정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'), button('다시 시도', load));
    }
    root.replaceChildren(box);
  }
  function renderItems(body, section) {
    const wrapper = element('div', section.step === 3 ? 'argument-grid' : 'section-items');
    for (const item of section.items) {
      const article = element('section', section.step === 3 ? 'argument-card' : 'fact-block');
      article.append(element('h3', '', item.title));
      const notice = section.step === 3 ? ARGUMENT_NOTICES[item.sectionType] : undefined;
      if (notice) article.append(element('p', 'argument-notice', notice));
      if (item.content) article.append(renderContent(item.content));
      if (item.data) {
        const grid = element('dl', 'damage-grid');
        item.data.forEach(({ label, value }) => {
          const card = element('div', 'damage-card');
          card.append(element('dt', '', label), element('dd', '', value));
          grid.append(card);
        });
        article.append(grid);
      }
      wrapper.append(article);
    }
    body.append(wrapper);
  }
  async function confirm(step, trigger) {
    if (loading) return;
    loading = true;
    trigger.disabled = true;
    trigger.setAttribute('aria-busy', 'true');
    const originalText = trigger.textContent;
    trigger.textContent = '확인 중…';
    try {
      await api.postReviewStep(caseId, step);
      if (!active()) return;
      if (step === 4) {
        navigate('summary');
      } else {
        const data = await api.getReview(caseId);
        if (active()) draw(data, true);
      }
    } catch (error) {
      if (error?.code === 'STEP_OUT_OF_ORDER') {
        loading = false;
        await load();
      } else {
        showError(error);
      }
    } finally {
      loading = false;
      trigger.disabled = false;
      trigger.removeAttribute('aria-busy');
      trigger.textContent = originalText;
    }
  }
  function draw(data, moveFocus = false) {
    const current = data.openStep ?? 4;
    const header = element('header', 'case-topbar');
    header.append(button('← 목록', () => navigate('list'), 'back-button'),
      element('strong', 'case-title', caseHeader.title), element('span', 'case-badge', caseHeader.chargeName));
    const progress = element('div', 'top-progress');
    const meter = element('progress');
    meter.max = 6;
    meter.value = current;
    meter.setAttribute('aria-label', '사건 정보 확인 진행');
    progress.append(meter, element('span', '', `${current} / 6`));
    header.append(progress);
    const layout = element('main', 'review-layout');
    const main = element('div', 'review-main');
    main.append(element('h1', 'page-heading', '사건 정보 확인'));
    const accordion = element('div', 'review-accordion');
    const titles = ['사건 개요', '상세 사실관계', '양측 주장', '법률 · 양형기준'];
    let focusTarget;
    titles.forEach((title, index) => {
      const step = index + 1;
      const section = data.sections.find((entry) => entry.step === step);
      const locked = !section;
      const opened = data.openStep === step;
      const card = element('section', `review-section${opened ? ' is-open' : ''}${locked ? ' is-locked' : ''}`);
      const heading = element('h2');
      const toggle = button('', () => {
        const expanded = toggle.getAttribute('aria-expanded') !== 'true';
        toggle.setAttribute('aria-expanded', String(expanded));
        body.hidden = !expanded;
        stateLabel.textContent = section.confirmed
          ? `${expanded ? '접기 ▴' : '펼치기 ▾'} · 확인 완료`
          : expanded ? '펼쳐짐 ▾' : '펼치기 ▾';
      }, 'section-toggle');
      toggle.disabled = locked;
      toggle.setAttribute('aria-expanded', String(opened));
      const name = element('span', 'section-name', `${section?.confirmed ? '✓' : ['①', '②', '③', '④'][index]} ${title}`);
      const stateLabel = element('span', 'section-state', locked ? '잠김 · 이전 단계 확인 후 열립니다' : section.confirmed ? '펼치기 ▾ · 확인 완료' : '펼쳐짐 ▾');
      toggle.append(name, stateLabel);
      heading.append(toggle);
      card.append(heading);
      const body = element('div', 'section-body');
      if (!locked) {
        body.id = `review-${caseId}-step-${step}`;
        toggle.setAttribute('aria-controls', body.id);
        body.hidden = !opened;
        renderItems(body, section);
        if (step === 4 && data.law) {
          renderLawInfoBox(body, { law: data.law });
          renderRecommendedRangeBar(body, { law: data.law });
          const terms = element('dl', 'law-terms');
          data.law.terms.forEach(({ term, desc }) => terms.append(element('dt', '', term), element('dd', '', desc)));
          body.append(terms);
        }
        if (opened) {
          const actions = element('div', 'section-actions');
          const next = button(step === 4 ? '판결 전 최종 정리로' : '읽었습니다 · 다음 단계 열기', () => confirm(step, next), 'primary-button');
          actions.append(next);
          body.append(actions);
          focusTarget = toggle;
        }
        card.append(body);
      }
      accordion.append(card);
    });
    main.append(accordion);
    if (data.status === 'REVIEWED') {
      const actions = element('div', 'section-actions');
      actions.append(button('판결 전 최종 정리로', () => navigate('summary'), 'primary-button'));
      main.append(actions);
    }
    layout.append(main);
    renderProgressSidebar(layout, data);
    root.replaceChildren(header, layout);
    if (moveFocus) focusTarget?.focus();
  }
  async function load() {
    if (loading || !active()) return;
    loading = true;
    const message = element('p', 'message-panel', '사건 정보를 불러오는 중…');
    message.setAttribute('role', 'status');
    root.replaceChildren(message);
    try {
      const data = await api.getReview(caseId);
      if (active()) draw(data);
    } catch (error) { showError(error); }
    finally { loading = false; }
  }
  await load();
}
