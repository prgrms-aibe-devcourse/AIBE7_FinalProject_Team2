import { button, element } from '../../components/siteLayout.js';
import { screenByStatus } from '../../utils/screenByStatus.js';
import { caseStage, mountStagePage, scriptFor } from '../overview/overviewPage.js';
import { caseInfoEar, caseNewspaper, earButton, paperFromCase } from '../../components/newspaperFront.js';
import { reducedMotion } from '../../utils/motion.js';
import { openCaseReviewPopup } from '../caseReview/caseReviewPopup.js';

// S-20 사건 디테일 (확장, IA v1.12 · FE-32). 신문 지면(S-02)에서 고른 기사를 한 장 넘기면 나오는 신문 안쪽 면이다
// (흐름 목업 ③ "사건 정보를 읽는다"). 형량 · 판결 정보는 없다. 모든 사건이 같은 지면(components/newspaperFront.js caseNewspaper)을 쓴다.
// 대본이 있는 사건은 이 화면이 곧 판결 체험 1막이다 — S-03과 같은 신문 지면 무대를 캐릭터 · 대사 없이 기다리게 두고(parked),
// 우하단 "시작하기"(하던 체험이 있으면 오른쪽 귀 "이어서 하기"도)를 누르면 화면을 다시 그리지 않고 주소만 S-03(/start)으로 바꾼 채
// 내로 · 남불이 들어오며 1막이 시작된다(drawStage). 왼쪽 귀는 "다른 사건 보기".
// 대본이 없는 사건은 같은 지면을 캐릭터 · 대사 없이 보여 준다(drawPaper). 기사 본문이 없으면 짧은 소개 한 단락만 싣고,
// 새 체험은 "준비 중"으로 막는다(FE-56, 하던 · 마친 체험은 이어서 볼 수 있다). 모든 사건 대본 제작은 FE-33.
// 데이터는 API 16 하나(체험 없이 열린다). 버튼은 내 진행 상태(myStatus)로 나누고, 누르면 API 2 응답 상태로 이어서 볼 화면을 정한다(1-6).
// S-02에서 기사를 골라 오면(from: 'paper') S-02의 신문 한 장이 넘어가며(utils/pageTurn.js) 이 면이 그 밑에서 드러난다.

// 진행 상태별 버튼 문구 · 실패 안내 (S-02와 같은 말)
const ACTIONS = {
  IN_PROGRESS: { label: '이어서 하기', failText: '사건을 열지 못했어요. 잠시 후 다시 시도해 주세요.' },
  COMPLETED: { label: '결과 다시 보기', failText: '결과를 열지 못했어요. 잠시 후 다시 시도해 주세요.' },
};
const NOT_STARTED = { label: '시작하기', failText: '체험을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.' };

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

  // 대본이 없는 사건: 대본 사건과 같은 신문 지면(안쪽 면)을 캐릭터 · 대사 없이 보여 준다. 기사 본문이 없으면 짧은 소개 한 단락만 싣는다(paperFromCase).
  // FE-56: 새 체험은 시연 전까지 열지 않는다(목 · 실제 모두 이후 화면이 대본 사건 기준이라 막다른 길이 된다).
  // 지면은 그대로 읽게 두고, "시작하기" 자리에 누를 수 없는 "준비 중"과 안내를 둔다. 대본이 생기면(FE-33) drawStage로 간다.
  // 이미 하던 · 마친 체험(myStatus)이 있으면 막지 않는다: 오른쪽 귀 "이어서 하기" · "결과 다시 보기"와 우하단 버튼이 API 2 응답 상태로 이어서 볼 화면으로 보낸다
  function drawPaper(data) {
    const action = ACTIONS[data.myStatus];
    const notice = element('p', 'paper-start-notice', action ? '' : '이 사건의 판결 체험은 준비 중이에요. 신문으로 돌아가 다른 사건을 골라 주세요.');
    notice.setAttribute('role', action ? 'alert' : 'status');
    const resume = action
      ? earButton(`${action.label} ▶`, `사건 ${String(data.caseId).padStart(2, '0')} · ${data.crimeCategoryLabel}`, () => start(resume, notice, action.failText), 'ear-right ear-go') : null;
    const sheet = caseNewspaper({ paper: paperFromCase(data), info: data, navigate, ...(resume ? { earRight: resume } : {}) });
    let go;
    if (action) {
      go = button(`${action.label} ▶`, () => start(go, notice, action.failText), 'primary-button');
      go.setAttribute('aria-label', `${action.label}, ${data.title}`); // 화면 글자로 시작해야 음성 입력으로 누를 수 있다(WCAG 2.5.3)
    } else {
      go = element('button', 'primary-button', '준비 중');
      go.type = 'button';
      go.setAttribute('aria-disabled', 'true');
      go.setAttribute('aria-label', `준비 중, ${data.title} 체험`);
    }
    const dock = element('div', 'paper-start');
    dock.append(notice);
    // 마친 사건만: 대본 사건의 선택지와 같이 독자 엽서를 열 수 있다 (FE-62 #213 리뷰)
    if (data.myStatus === 'COMPLETED') {
      dock.append(button('독자 엽서 쓰기', (event) => openCaseReviewPopup({ caseId, api, navigate, from: event.currentTarget }), 'secondary-button'));
    }
    dock.append(go);
    const deskBack = element('div', 'desk');
    deskBack.setAttribute('aria-hidden', 'true');
    root.className = 'case-list-page case-paper-page overview-page';
    root.replaceChildren(deskBack, sheet, dock);
    unfold(sheet);
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
    // 하던 · 마친 체험을 "시작하기"로 열면 내로가 먼저 묻는다: 진행 중이면 이어서 할지 1막부터 새로 볼지, 완료면 결과만 볼지 1막부터 다시 볼지
    // (선택지 'resume'은 둘 다 저장된 단계로 간다 — 완료는 6막 세 판결 비교)
    const PROMPTS = {
      IN_PROGRESS: { text: '진행 중인 체험이 있어요. 이어서 할래요? 1막부터 다시 봐도 보낸 판단은 그대로예요.', resume: '이어서 하기' },
      COMPLETED: { text: '이미 마친 체험이에요. 결과만 볼래요? 1막부터 다시 봐도 보낸 판단은 그대로예요.', resume: '결과만 보기' },
    };
    const asking = PROMPTS[data.myStatus];
    const prompt = asking ? {
      text: asking.text,
      choices: [
        { label: asking.resume, primary: true, action: () => chosen?.('resume') },
        { label: '1막부터 다시 보기', action: () => chosen?.('again') },
        // 마친 사건만: 독자 엽서(S-09)으로 (FE-62)
        ...(data.myStatus === 'COMPLETED' ? [{ label: '독자 엽서 쓰기', action: () => chosen?.('opinion') }] : []),
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
      // 완료한 사건만 세 판결 비교(API 14)에서 보낸 판단을 읽어 보여 준다. 진행 중이면 형량은 보여 주지 않고 제출 완료 안내만 한다(BE-77)
      let sent;
      if (experience.status === 'COMPLETED') sent = await api.getComparison(caseId).then((r) => r?.preToFinal?.preJudgment?.label).catch(() => undefined);
      if (active()) setAlreadySent(saved, sent ?? undefined);
    }

    async function begin(trigger, { fromStart = false } = {}) {
      if (starting) return;
      starting = true;
      trigger.setAttribute('aria-busy', 'true');
      trigger.disabled = true; // 요청 중에는 눌러도 무시되므로 비활성으로 보이게
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
          // 하던 · 마친 체험: 내로가 먼저 묻는다 → "이어서 하기" · "결과만 보기"는 저장된 단계로, "1막부터 다시 보기"는 1막부터(사전 판단 전이면 둘 다 1막부터)
          chosen = (pick) => {
            // 독자 엽서: 6막처럼 남불이 엽서를 건넨다. 창을 닫으면 이 선택지로 돌아오므로 chosen은 비우지 않는다
            if (pick === 'opinion') { openCaseReviewPopup({ caseId, api, navigate, from: stage.querySelector('.spr.b'), hand: true }); return; }
            chosen = null;
            if (pick === 'resume' && !fresh) { saved(); return; }
            // 비동기 처리 중 오류(형량 데이터를 끝내 못 받음 등)는 loadOverview가 S-03을 새로 열어 안내한다. 여기서는 콘솔로 새지 않게만 막는다
            (fresh ? playFresh() : playAgain(experience, saved)).catch((error) => console.warn('1막 이어 보기 중 오류:', error));
          };
          stage.release();
          return;
        }
        if (!fresh) stage.replay(); // 묻지 못한 채 이미 하던 · 마친 체험이 된 경우(다른 탭에서 진행 등): 1막부터 다시 보기
        stage.release();
        if (fresh) await playFresh(); else await playAgain(experience, saved);
      } catch (error) {
        if (!active() || started) return;
        notice.textContent = error?.code === 'CASE_NOT_FOUND' ? '사건을 찾을 수 없어요. 신문으로 돌아가 다시 골라 주세요.' : failText;
      } finally {
        starting = false;
        trigger.removeAttribute('aria-busy');
        trigger.disabled = false;
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
      else drawPaper(data);
    } catch (error) {
      if (!active()) return;
      // CASE_NOT_FOUND는 withErrorRedirect가 S-14로 먼저 보낸다(app/errorRedirect.js). 여기 오는 것은 그 밖의 오류다
      message('사건 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', button('다시 시도', load));
    }
  }
  await load();
}
