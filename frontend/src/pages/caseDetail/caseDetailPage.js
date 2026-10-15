import { button, element } from '../../components/siteLayout.js';
import { fallbackImages } from '../../utils/newspaper.js';
import { screenByStatus } from '../../utils/screenByStatus.js';
import { caseStage, mountStagePage, scriptFor } from '../overview/overviewPage.js';
import { caseInfoEar, earButton, paperFromCase } from '../../components/newspaperFront.js';

// S-20 사건 디테일 (확장, IA v1.12 · FE-32). 신문 지면(S-02)에서 고른 기사를 오려 책상 위에 펼쳐 놓은 모습이다
// (흐름 목업 ③ "사건 정보를 읽는다"). 형량 · 판결 정보는 없다.
// 대본이 있는 사건은 이 화면이 곧 판결 체험 1막이다 — S-03과 같은 신문 지면 무대를 캐릭터 · 대사 없이 기다리게 두고(parked),
// 우하단 "시작하기"(하던 체험이 있으면 오른쪽 귀 "이어서 하기"도)를 누르면 화면을 다시 그리지 않고 주소만 S-03(/start)으로 바꾼 채
// 내로 · 남불이 들어오며 1막이 시작된다(drawStage). 왼쪽 귀는 "다른 사건 보기".
// 대본이 없는 사건은 오려 낸 기사(재연 사진 · 사건 개요 · 사건 정보)를 보여 준다(draw). 모든 사건 대본 제작은 FE-33.
// 데이터는 API 16 하나(체험 없이 열린다). 버튼은 내 진행 상태(myStatus)로 나누고, 누르면 API 2 응답 상태로 이어서 볼 화면을 정한다(1-6).
// S-02에서 기사를 골라 오면(from: 'paper') S-02의 신문 한 장이 넘어가며(utils/pageTurn.js) 이 면이 그 밑에서 드러난다.

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
  if (from) clip.style.visibility = 'hidden'; // 불러오는 동안 빈 종이가 먼저 보이지 않게 한다(다음 면은 내려앉으며 나타난다)
  container.replaceChildren(root);
  const active = () => container.contains(root);
  let starting = false;

  // 새로고침 · 뒤로 가기로 다시 들어왔을 때 펼침 연출을 되풀이하지 않게, 주소 기록에 남은 출발 위치를 지운다
  if (from) history.replaceState({ ...history.state, extras: {} }, '');

  // Esc: 신문으로 돌아간다(목업과 같은 한 단계 뒤로). 무대에서 체험을 시작한 뒤에는 받지 않는다(이제 S-03이다)
  let started = false;
  function onKey(event) {
    if (!active() || started) { document.removeEventListener('keydown', onKey); return; }
    if (event.key === 'Escape' && !event.defaultPrevented) navigate('list');
  }
  document.addEventListener('keydown', onKey);

  function message(text, action) {
    const panel = element('section', 'message-panel');
    panel.setAttribute('role', 'status');
    panel.append(element('p', '', text));
    if (action) panel.append(action);
    if (action) clip.style.visibility = ''; // 오류 안내는 바로 보인다(불러오는 중은 숨겨 둔 채 다음 면이 내려앉기를 기다린다)
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

  // 신문 한 장이 넘어가는 동안 이 면은 그 밑에 놓여 있다(넘기는 연출은 S-02가 시작). 자료가 늦게 와도 부드럽게 나타난다
  function unfold(node = clip) {
    clip.style.visibility = '';
    if (from && !reducedMotion()) node.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220, easing: 'ease-out' });
  }

  // 대본이 있는 사건: 1막 무대를 첫 장면 앞에서 멈춰 둔다. 선택지로 체험을 시작하면 그 자리에서 1막이 이어진다
  function drawStage(data, script) {
    const { label, failText } = ACTIONS[data.myStatus] ?? NOT_STARTED;
    const info = { ...data };
    // 오른쪽 귀: 하던 체험이 있으면 "이어서 하기" · "결과 다시 보기", 처음 보는 사건이면 사건 번호 · 분류 그대로
    const resume = ACTIONS[data.myStatus]
      ? earButton(`${label} ▶`, `사건 ${String(data.caseId).padStart(2, '0')} · ${data.crimeCategoryLabel}`, () => begin(resume), 'ear-right ear-go') : null;
    // 진행 중인 체험을 "시작하기"로 열면 내로가 먼저 묻는다: 이어서 할지, 1막부터 새로 볼지(완료한 사건은 묻지 않고 다시 보기)
    const prompt = data.myStatus === 'IN_PROGRESS' ? {
      text: '진행 중인 체험이 있어요. 이어서 할래요? 1막부터 다시 봐도 보낸 판단은 그대로예요.',
      choices: [
        { label: '이어서 하기', primary: true, action: () => chosen?.('resume') },
        { label: '1막부터 다시 보기', action: () => chosen?.('again') },
      ],
    } : undefined;
    let chosen = null;
    const { stage, setOverview, setAlreadySent } = caseStage({
      caseId, api, navigate, script, info, paper: paperFromCase(data, script.paper), parked: true, prompt, ...(resume ? { earRight: resume } : {}),
    });
    // 우하단 "시작하기": 누르면 내로 · 남불이 들어오며 1막이 처음부터 시작된다(진행 중이면 내로가 먼저 묻는다). 바로 이어 보기는 오른쪽 귀
    const dock = element('div', 'paper-start');
    const notice = element('p', 'paper-start-notice');
    notice.setAttribute('role', 'alert');
    const go = button('시작하기 ▶', () => begin(go, { fromStart: true }), 'primary-button');
    go.setAttribute('aria-label', `시작하기, ${data.title}`); // 화면 글자("시작하기")로 시작해야 음성 입력으로 누를 수 있다(WCAG 2.5.3)
    dock.append(notice, go);
    mountStagePage(root, { stage });
    root.append(dock);
    unfold(stage);

    // 이 지면에서 1막을 잇는다(사전 판단 전, STARTED). 묻는 장면이 맨 앞에 있으면 건너뛴다
    async function playFresh() {
      navigate('overview', { caseId }, { keepView: true });
      resume?.replaceWith(caseInfoEar(info)); // 이제 S-03과 같은 지면
      if (prompt) stage.advance({ dropCurrent: true }); // 묻는 장면은 빼서 ◀ 이전으로 다시 나오지 않게
      setOverview(await loadOverview());
    }
    // 이미 사전 판단을 낸 사건을 1막부터 다시 본다. 판단은 바꿀 수 없으니 엽서 차례에 보낸 판단을 보여 주고 저장된 단계로 잇는다(주소는 S-20 그대로)
    async function playAgain(experience, saved) {
      stage.replay();
      if (prompt) stage.advance({ dropCurrent: true }); // 묻는 장면은 빼서 ◀ 이전으로 다시 나오지 않게
      // 완료한 사건은 세 판결 비교(API 14)에서 보낸 판단을 읽는다. 진행 중이면 이 브라우저에 적어 둔 것을 쓴다
      let sent;
      if (experience.status === 'COMPLETED') sent = await api.getComparison(caseId).then((r) => r?.preToFinal?.preJudgment?.label).catch(() => undefined);
      if (active()) setAlreadySent(saved, sent ?? undefined);
    }

    async function begin(trigger, { fromStart = false } = {}) {
      if (starting) return;
      starting = true;
      trigger.setAttribute('aria-busy', 'true');
      notice.textContent = '';
      try {
        const experience = await api.startExperience(caseId);
        if (!active()) return;
        // 이동은 API 2 응답 상태로 정한다(API 명세 1-6)
        const saved = () => navigate(screenByStatus[experience.status] ?? 'overview', { caseId });
        const fresh = experience.status === 'STARTED';
        if (!fromStart && !fresh) { saved(); return; } // 오른쪽 귀 "이어서 하기": 저장된 단계로 바로
        started = true;
        dock.remove();
        if (fromStart && prompt) {
          // 진행 중: 내로가 먼저 묻는다 → "이어서 하기"는 저장된 단계로, "새로 하기"는 1막부터(사전 판단 전이면 둘 다 1막부터)
          chosen = (pick) => {
            chosen = null;
            if (pick === 'resume' && !fresh) saved();
            else if (fresh) playFresh();
            else playAgain(experience, saved);
          };
          stage.release();
          return;
        }
        if (!fresh) stage.replay(); // 완료한 사건을 "시작하기": 묻지 않고 1막부터 다시 보기
        stage.release();
        if (fresh) await playFresh(); else await playAgain(experience, saved);
      } catch (error) {
        if (!active() || started) return;
        notice.textContent = error?.code === 'CASE_NOT_FOUND' ? '사건을 찾을 수 없어요. 신문으로 돌아가 다시 골라 주세요.' : failText;
      } finally {
        starting = false;
        trigger.removeAttribute('aria-busy');
      }
    }
    // 2막 사전 판단 폼에 쓸 형량 구간(API 4). 1막을 보는 동안 받아 오고, 끝내 못 받으면 S-03을 새로 열어 그 화면의 오류 안내에 맡긴다
    async function loadOverview(tries = 2) {
      try {
        return await api.getOverview(caseId);
      } catch (error) {
        if (tries > 1 && active()) { await new Promise((resolve) => setTimeout(resolve, 800)); return loadOverview(tries - 1); }
        if (active()) window.location.reload();
        throw error;
      }
    }
  }

  async function load() {
    if (!active()) return;
    message('사건 정보를 불러오는 중…');
    try {
      const data = await api.getCaseDetail(caseId);
      if (!active()) return;
      const script = scriptFor(caseId, data.title);
      if (script) drawStage(data, script);
      else draw(data);
    } catch (error) {
      if (!active()) return;
      // CASE_NOT_FOUND는 withErrorRedirect가 S-14로 먼저 보낸다(app/errorRedirect.js). 여기 오는 것은 그 밖의 오류다
      message('사건 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', button('다시 시도', load));
    }
  }
  await load();
}
