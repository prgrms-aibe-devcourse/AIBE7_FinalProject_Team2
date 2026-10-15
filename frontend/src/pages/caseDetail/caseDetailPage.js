import { button, element } from '../../components/siteLayout.js';
import { fallbackImages } from '../../utils/newspaper.js';
import { screenByStatus } from '../../utils/screenByStatus.js';

// S-20 사건 디테일 (확장, IA v1.12 · FE-32). 신문 지면(S-02)에서 고른 기사를 오려 책상 위에 펼쳐 놓은 모습이다
// (흐름 목업 ③ "사건 정보를 읽는다"). 재연 사진 · 사건 개요 · 사건 정보 · 지면 기사를 보여 주고, 형량 · 판결 정보는 없다.
// 데이터는 API 16 하나(체험 없이 열린다). 버튼은 내 진행 상태(myStatus)로 나누고, 누르면 API 2 응답 상태로 이어서 볼 화면을 정한다(1-6).
// S-02에서 기사를 골라 오면(from: 고른 기사의 화면 위치) 오려 낸 기사가 그 자리에서 날아와 펼쳐진다.

const DIFFICULTY = { LOW: '쉬움', MID: '보통', HIGH: '어려움' };
// 진행 상태별 버튼 문구 · 실패 안내 (S-02와 같은 말)
const ACTIONS = {
  IN_PROGRESS: { label: '이어서 하기', failText: '사건을 열지 못했어요. 잠시 후 다시 시도해 주세요.' },
  COMPLETED: { label: '결과 다시 보기', failText: '결과를 열지 못했어요. 잠시 후 다시 시도해 주세요.' },
};
const NOT_STARTED = { label: '체험 시작', failText: '체험을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.' };
// shortcut: 캐릭터 얼굴은 원본 이미지 레포 PNG를 직접 읽는다(무대와 같음), 웹 webp 변환(WBS 7.6.8) 때 public 경로로 옮긴다
const FACE = (who) => `https://raw.githubusercontent.com/peanutboi710/NaeLawNamBul_Images/main/character/cuts/${who}/icon/${who}_icon_face-neutral.png`;
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export async function renderCaseDetailPage(container, { caseId, api, navigate, from }) {
  const root = element('div', 'case-detail-page');
  const desk = element('div', 'detail-desk');
  desk.setAttribute('aria-hidden', 'true');
  const clip = element('article', 'detail-clip');
  root.append(desk, clip);
  container.replaceChildren(root);
  const active = () => container.contains(root);
  let starting = false;

  // 새로고침 · 뒤로 가기로 다시 들어왔을 때 펼침 연출을 되풀이하지 않게, 주소 기록에 남은 출발 위치를 지운다
  if (from) history.replaceState({ ...history.state, extras: {} }, '');

  // Esc: 신문으로 돌아간다(목업과 같은 한 단계 뒤로)
  function onKey(event) {
    if (!active()) { document.removeEventListener('keydown', onKey); return; }
    if (event.key === 'Escape' && !event.defaultPrevented) navigate('list');
  }
  document.addEventListener('keydown', onKey);

  function message(text, action) {
    const panel = element('section', 'message-panel');
    panel.setAttribute('role', 'status');
    panel.append(element('p', '', text));
    if (action) panel.append(action);
    clip.replaceChildren(panel);
  }

  async function start(trigger, notice, failText) {
    if (starting) return;
    starting = true;
    trigger.disabled = true;
    trigger.setAttribute('aria-busy', 'true');
    notice.textContent = '';
    try {
      const experience = await api.startExperience(caseId);
      if (active()) navigate(screenByStatus[experience.status] ?? 'overview', { caseId });
    } catch (error) {
      if (!active()) return;
      trigger.disabled = false;
      trigger.removeAttribute('aria-busy');
      notice.textContent = error?.code === 'CASE_NOT_FOUND' ? '사건을 찾을 수 없어요. 신문으로 돌아가 다시 골라 주세요.' : failText;
    } finally {
      starting = false;
    }
  }

  function facts(data) {
    const list = element('dl', 'detail-facts');
    [
      ['사건명', data.title],
      ['죄명', data.chargeName],
      ['범죄 분류', data.crimeCategoryLabel],
      ['예상 소요', `약 ${data.estimatedMinutes}분${DIFFICULTY[data.difficulty] ? ` · ${DIFFICULTY[data.difficulty]}` : ''}`],
      ['참여', `${data.participantCount.toLocaleString('ko-KR')}명`],
    ].forEach(([term, value]) => {
      if (value) list.append(element('dt', '', term), element('dd', '', value));
    });
    return list;
  }

  function draw(data) {
    const top = element('div', 'detail-top');
    const square = element('span', `type-square type-${data.crimeType}`);
    square.setAttribute('aria-hidden', 'true');
    const kind = element('span', 'detail-kind');
    kind.append(square, data.crimeCategoryLabel);
    top.append(kind, element('span', '', `사건 ${String(data.caseId).padStart(2, '0')}`),
      button('← 신문으로 돌아가기', () => navigate('list'), 'detail-back'));

    const head = element('h1', 'detail-headline', data.headline || data.title);
    head.id = 'detail-heading';
    clip.setAttribute('aria-labelledby', head.id);

    const figure = element('figure', 'detail-photo');
    const photo = element('img');
    photo.src = data.thumbnailUrl || fallbackImages[data.crimeType] || fallbackImages.MURDER;
    photo.alt = ''; // 연출 이미지라 읽지 않는다 (사건 정보는 개요 · 사건 정보에 있다)
    figure.append(photo, element('figcaption', '', '사건 이해를 돕기 위한 연출 이미지'));

    const summary = element('section', 'detail-box');
    summary.append(element('h2', '', '사건 개요'), element('p', '', data.overview || data.shortIntro));
    const info = element('section', 'detail-box');
    info.append(element('h2', '', '사건 정보'), facts(data));
    if (data.keywords?.length) {
      const chips = element('p', 'detail-chips');
      data.keywords.forEach((keyword) => chips.append(element('span', '', `#${keyword}`)));
      info.append(chips);
    }
    const side = element('div', 'detail-side');
    side.append(summary, info);
    const grid = element('div', 'detail-grid');
    grid.append(figure, side);

    const parts = [top, head, element('p', 'detail-deck', data.deck || data.shortIntro), grid];
    // 지면 기사 본문(API 1의 제안 필드 article, S-03 개요 범위). 없으면 이 칸을 그리지 않는다
    if (data.article?.length) {
      const article = element('section', 'detail-article');
      article.append(element('h2', '', '지면 기사'), ...data.article.map((text) => element('p', '', text)));
      parts.push(article);
    }

    const { label, failText } = ACTIONS[data.myStatus] ?? NOT_STARTED;
    const cta = element('div', 'detail-cta');
    const duo = element('span', 'detail-duo');
    duo.setAttribute('aria-hidden', 'true');
    ['naero', 'nambul'].forEach((who) => {
      const face = element('img');
      face.src = FACE(who);
      face.alt = '';
      face.addEventListener('error', () => face.remove(), { once: true });
      duo.append(face);
    });
    const say = element('p', 'detail-say');
    say.append(element('b', '', '내로 · 남불'), data.myStatus ? '이어서 같이 볼까요?' : '이 사건, 같이 볼까요?',
      element('small', '', '형량은 체험을 마친 뒤에 공개돼요. 먼저 기사만 보고 내 생각을 남겨요.'));
    const notice = element('p', 'detail-notice');
    notice.setAttribute('role', 'alert');
    const go = button(`${label} ▶`, () => start(go, notice, failText), 'primary-button');
    go.setAttribute('aria-label', `${data.title} ${label}`);
    const actions = element('div', 'detail-actions');
    actions.append(button('다른 사건 보기', () => navigate('list'), 'secondary-button'), go);
    cta.append(duo, say, actions, notice);
    parts.push(cta);
    clip.replaceChildren(...parts);
    unfold();
  }

  // 오려 낸 기사가 S-02에서 고른 자리에서 날아와 책상 위에 펼쳐진다(목업 ③). 움직임 줄이기면 바로 놓인다
  function unfold() {
    if (!from || reducedMotion()) return;
    const box = clip.getBoundingClientRect();
    const scale = Math.max(0.22, Math.min(0.8, from.width / box.width));
    const dx = from.left + from.width / 2 - (box.left + box.width / 2);
    const dy = from.top + from.height / 2 - (box.top + box.height / 2);
    clip.animate([
      { transform: `translate(${dx}px, ${dy}px) scale(${scale}) rotate(-3deg)`, opacity: 0 },
      { opacity: 1, offset: 0.55 },
      { transform: 'rotate(-.4deg)', opacity: 1 },
    ], { duration: 600, easing: 'cubic-bezier(.2, .8, .2, 1)' });
  }

  async function load() {
    if (!active()) return;
    message('사건 정보를 불러오는 중…');
    try {
      const data = await api.getCaseDetail(caseId);
      if (active()) draw(data);
    } catch (error) {
      if (!active()) return;
      // CASE_NOT_FOUND는 withErrorRedirect가 S-14로 먼저 보낸다(app/errorRedirect.js). 여기 오는 것은 그 밖의 오류다
      message('사건 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', button('다시 시도', load));
    }
  }
  await load();
}
