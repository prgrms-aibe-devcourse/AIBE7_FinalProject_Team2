// 내 결과(FE-75): 마이페이지 "내가 체험한 사건"의 "내 결과 보기"로 들어오는 지면. 주소 /me/cases/{caseId}
// 6막 세 판결 비교(S-08)의 다음 날 신문 모양을 빌리되, 판결 비교 대신 내 판단과 내 성향을 싣는다.
//   제호 · 1면 · 광고                       → 6막 지면과 같다(comparisonStage.js에서 가져온다). 1면 헤드라인 · 리드는 사건 제목 · 판결을 내린 판사(나)로 바꾼다
//   독자 의견 · 세 판결, 나란히 · 만난 모습   → 싣지 않는다(첫인상 · 최종 판결은 1면 리드에 굵게 싣는다)
//   판단 요소, 어디서 갈렸나                  → 이 사건으로 달라진 내 성향(성향카드의 성향 변화 흐름, 이 사건 칸 강조). 오른쪽 단: 지문 · 그래프 네 개가 각각 섹션
//   같게 본 것, 다르게 본 것                  → 이 사건에서 드러난 판결 성향(이 사건 한 건의 4축) · 그 유형의 성향카드 · 설명. 왼쪽 단: 1면 · 막대 · 카드가 각각 섹션
// 두 단은 긴 쪽이 높이를 정하고, 짧은 쪽 단이 섹션 사이에 남는 높이를 똑같이 나눠 끝선을 맞춘다(myResult.css)
// 대사 · 카메라 없이 처음부터 읽는 지면이다(6막 "여기 남아있기"와 같은 읽기 화면 클래스를 쓴다).
// 지면 글은 기사 문체("~다")로 쓰고, 바뀌는 값은 모두 API 값 그대로 끼운다(지어내는 문장 없음).
// 데이터: API 14(처음 판단 · 최종 판결, 누를 때 한 번) + P-5(성향 변화 흐름 · 이 사건의 축 점수 · 유형 history[].type). 결정 기록 docs/decisions/FE-75.md · BE-100.md
import { button, element, mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { renderTrend, trendsOf } from '../../components/trendGraph.js';
import { AXES } from '../../utils/personalityAxes.js';
import { TREND_RECENT, recordOf } from '../../utils/myPageView.js';
import { story, masthead, frontStory, advertisements, openForReading } from '../comparison/comparisonStage.js';
import { myNickname, typeFace } from '../../components/personCards.js';
import { currentUser } from '../../utils/session.js';

const IS_MOCK = API_MODE === 'mock'; // 목 모드는 비교 · 성향 모두 목이라 기사 제목 옆에 표기한다

function headed(node) {
  if (IS_MOCK) node.querySelector('.sheet-head')?.append(mockTag());
  return node;
}

const bold = (text) => element('strong', '', text);

// 이 사건으로 달라진 내 성향: 성향카드 화면의 성향 변화 흐름 그래프 그대로, 이 사건 칸만 강조한다. 안내는 기사 문체
const trendHint = ({ hasTest, focused }) => `${hasTest ? '초심(성향 테스트)과 ' : ''}최근 체험 ${TREND_RECENT}건까지의 판결 성향을 축마다 실었다. `
  + `가운데 줄이 반반이고, 아래 숫자는 사건을 마친 순서, '최근'은 최근 체험 3건의 평균이다.${focused ? ' 띠를 깐 칸이 이 사건이다.' : ''} 점을 누르면 그때의 사건과 비중이 나온다.`;
// 오른쪽 단의 섹션들: [제목 + 지문] · 그래프 네 개. 단 높이를 맞출 때 섹션 사이에 여백을 나누려고 그래프를 하나씩 단의 섹션으로 꺼낸다
function trendSections(myPage, record) {
  const intro = story('factors', '이 사건으로 달라진 내 성향');
  if (!myPage) {
    intro.append(element('p', 'sheet-text', '성향 기록을 불러오지 못했어요. 마이페이지에서 다시 열어 주세요.'));
    return [intro];
  }
  if (!record.order) intro.append(element('p', 'sheet-text', '이 사건은 성향 기록이 없어 강조한 칸이 없다.'));
  const trend = renderTrend(trendsOf(myPage), myPage.history ?? [], myPage.judgmentPersonality, { mock: IS_MOCK, focus: record.order, hint: trendHint, table: false });
  const charts = [...trend.querySelectorAll('.trend-chart')];
  intro.append(...[...trend.childNodes].filter((node) => !node.classList?.contains('trend-grid'))); // 지문(체험이 없으면 안내 한 줄)
  return [intro, ...charts];
}

// 성향카드 옆 설명(기사 문체): 유형 이름(16개 모두 "~형"이라 「」 뒤 "에 가깝다"로 잇는다) + 판결 연계 설명(verdictDescription, "~다" 문체)
// type: 이 사건 한 건의 유형(P-5 history[].type, 유형 공통 형식 9-1). 네 축이 다 모이지 않은 사건은 서버가 null로 준다(BE-100).
// undefined는 응답에 type 칸이 없는 경우(BE-100 이전 서버)라 "네 축 부족"이라고 단정하지 않는다(PR #246 리뷰)
const NO_TYPE_FIELD = '이 사건의 유형은 지금 보여 줄 수 없다. 잠시 뒤 마이페이지에서 다시 열면 보인다.';
const NOT_ENOUGH_AXES = '이 사건에서는 네 축이 모두 드러나지 않아 유형을 정하지 않았다.';
function cardStory(type) {
  const text = element('div', 'my-result-card-text');
  if (type) {
    const lead = element('p', 'sheet-text');
    lead.append('이 사건 한 건의 판단만 놓고 보면 ', bold(`「${type.name}」`), '에 가깝다.');
    text.append(lead);
    if (type.verdictDescription) text.append(element('p', 'sheet-text', type.verdictDescription));
  } else {
    text.append(element('p', 'sheet-text', type === undefined ? NO_TYPE_FIELD : NOT_ENOUGH_AXES));
  }
  text.append(element('p', 'sheet-text my-result-note', '이 성향은 이 사건 한 건의 판단만으로 집계했으며, 최근 체험 3건을 평균한 성향은 마이페이지에서 확인할 수 있다.'));
  return text;
}

// 왼쪽 단의 판결 성향 섹션들: [제목 + 이 사건 한 건의 4축(마이페이지 성향카드의 막대와 같은 배치)] · [그 유형의 성향카드(왼쪽) + 설명].
// 성향카드는 구분선 없는 별도 섹션이라, 단 높이를 맞출 때 막대와 카드 사이에도 여백이 나뉜다
function leanSections(record) {
  const lean = headed(story('rules', '이 사건에서 드러난 판결 성향'));
  lean.classList.add('my-result-lean');
  if (!record?.scores) {
    lean.append(element('p', 'sheet-text', record === null ? '성향 기록을 불러오지 못했어요. 마이페이지에서 다시 열어 주세요.' : '이 사건은 성향 기록이 없다.'));
    return [lean];
  }
  const list = element('ul', 'axis-list');
  AXES.forEach((axis) => list.append(renderAxisRow(axis, record.scores[axis.key])));
  lean.append(list);
  const card = element('section', 'my-result-card');
  card.setAttribute('aria-label', '이 사건의 성향카드');
  const emptyNote = record.type === undefined ? '"지금은 성향카드를 보여 줄 수 없어요"' : '"이 사건에서 네 축이 모두 드러나지 않았어요"';
  card.append(typeFace(record.type, emptyNote), cardStory(record.type));
  return [lean, card];
}

// 1면: 사건 제목과 판결을 내린 판사(나)를 중심으로.
//   사건 제목(API 16 · 목록) · 첫인상 구간(API 14 preToFinal.preJudgment.label, 예: "실형 5년 이상 ~ 10년 미만")
//   최종 판결(preToFinal.finalJudgmentText, 서버 FinalJudgmentText: "사형" · "무기징역" · "징역 2년 6개월" · "벌금 …" · "징역 3년에 집행유예 5년")
//   첫인상과 견준 방향(preToFinal.direction: HEAVIER · SAME · LIGHTER)
// 형량(첫인상 구간 · 최종 판결)은 굵게 둔다. 값 바로 뒤에는 받침에 따라 갈리는 조사(이/가 · 을/를 · 으로/로)를 붙이지 않는다 — "~의", "~에", 쉼표 · "쪽"으로 잇는다.
// 판사 이름은 마이페이지 프로필카드와 같은 값(닉네임, 없으면 "익명의 판사")이다.
// 이 지면의 주인공은 내 판결이라 헤드라인에 최종 판결을 싣는다(FE-100 — 예전 헤드라인 "…의 판결, 다시 펼치다"는 판결이 사진 아래 작은 글씨로 밀렸다)
const DIRECTION_LINES = {
  HEAVIER: '사건 기록과 법정 공방을 모두 지켜본 뒤에는 첫인상보다 무거운 쪽으로 판결을 정했다.',
  SAME: '사건 기록과 법정 공방을 모두 지켜본 뒤에도 첫인상과 같은 구간 안에서 판결을 정했다.',
  LIGHTER: '사건 기록과 법정 공방을 모두 지켜본 뒤에는 첫인상보다 가벼운 쪽으로 판결을 정했다.',
};
function leadOf(caseTitle, preToFinal, judge) {
  return {
    headline: `${judge}의 판결은 ${preToFinal.finalJudgmentText}`,
    deck: [`${caseTitle}, 기사만 읽고 고른 첫인상은 `, bold(preToFinal.preJudgment.label), ' 쪽이었다'],
    text: [
      DIRECTION_LINES[preToFinal.direction] ?? '사건 기록과 법정 공방을 모두 지켜본 뒤 판결을 정했다.',
      ' 그 판결에 드러난 판결 성향과, 이 사건으로 달라진 성향을 함께 싣는다.',
    ],
  };
}

// 지면 아래 광고 · 알림 단(FE-100): 6막 지면의 광고를 빌리되, 이 지면을 읽는 사람(체험을 마친 나)에게 맞게 바꾼다.
//   광고 → 성향 테스트를 했으면 "내 성향카드"(마이페이지 성향카드 화면), 안 했으면 "판결 성향 테스트" 그대로
//   알림 "오늘의 지면" → 누르면 사건 목록(다른 사건 보기). 모양만 칸이고 누를 수 없던 자리를 살린다
//   "첫인상 엽서 모집" → 이 사건을 이미 마친 사람에게는 맞지 않아, 같은 자리(세 번째 칸)를 "마이페이지로" 돌아가기 칸으로 바꾼다(FE-100, 버튼 대신 지면 모양 그대로)
function clickable(box, label, action) {
  box.classList.add('is-clickable');
  box.setAttribute('role', 'button');
  box.tabIndex = 0;
  box.setAttribute('aria-label', label);
  box.addEventListener('click', action);
  box.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    action();
  });
}
function resultAds(hasTest) {
  const ads = advertisements();
  const back = ads.querySelector('.sheet-ad.is-notice');
  back.querySelector('.sheet-ad-tag').textContent = '마이페이지';
  back.querySelector('.sheet-ad-title').textContent = '마이페이지로';
  back.querySelector('.sheet-ad-text').textContent = '내가 체험한 사건 목록으로 돌아가요. 다른 사건의 결과도 거기서 펼쳐 볼 수 있어요.';
  if (hasTest) {
    const lead = ads.querySelector('.sheet-ad.is-lead');
    lead.querySelector('.sheet-ad-tag').textContent = '알림';
    lead.querySelector('.sheet-ad-title').textContent = '내 성향카드';
    lead.querySelector('.sheet-ad-text').textContent = '이 판결까지 더해진 내 성향은? 초심부터 최근까지의 흐름을 마이페이지에서 펼쳐 보세요.';
  }
  return ads;
}

export async function renderMyResultPage(container, { caseId, caseHeader, api, navigate }) {
  const root = element('main', 'comparison-page comparison-full my-result-page'); // 6막 읽기 지면과 같은 화면 틀(courtSketch.css). 본문 landmark(FE-100)
  container.replaceChildren(root);
  const active = () => container.contains(root);
  let loading = false;

  function message(text, action) {
    const panel = element('section', 'message-panel');
    panel.setAttribute('role', 'status');
    panel.append(element('p', '', text));
    if (action) panel.append(action);
    root.replaceChildren(panel);
  }

  function draw(data, myPage) {
    const record = recordOf(myPage, caseId);
    const sheet = element('article', 'sheet sheet-result');
    sheet.setAttribute('aria-label', '내 결과 지면');
    const mast = masthead();
    // 왼쪽 귀: 6막은 "다른 사건 보기"지만, 이 지면은 마이페이지에서 펼쳐 온 지면이라 들어온 곳(내가 체험한 사건)으로 돌아가는 자리다(FE-100).
    // 다른 사건은 아래 "오늘의 지면" 알림이 맡는다. 오른쪽 귀의 "고민 중" 메모는 판결을 마친 지면이라 "판결 확정"으로 바꾼다
    const earLeft = mast.querySelector('.sheet-ear-left');
    earLeft.querySelector('b').textContent = '← 마이페이지';
    earLeft.querySelector('small').textContent = '내가 체험한 사건으로';
    earLeft.addEventListener('click', () => navigate('me', { view: 'cases' }));
    mast.querySelector('.sheet-ear-memo').textContent = '판결 확정';
    // 왼쪽 · 오른쪽 단을 따로 쌓는다. 한 격자 줄에 두면 긴 흐름 그래프 높이만큼 왼쪽 기사 사이가 벌어진다(myResult.css)
    const left = element('div', 'sheet-col is-left');
    left.append(frontStory(caseHeader.title, leadOf(caseHeader.title, data.preToFinal, myNickname(myPage, currentUser()))), ...leanSections(record));
    const right = element('div', 'sheet-col is-right');
    right.append(...trendSections(myPage, record ?? {}));
    const hasTest = Boolean(myPage) && trendsOf(myPage).some(({ points }) => points[0]?.isTest);
    const ads = resultAds(hasTest);
    sheet.append(mast, left, right, ads);
    openForReading(sheet, () => (hasTest ? navigate('me', { view: 'type' }) : navigate('testPaper')));
    if (hasTest) sheet.querySelector('.sheet-ad.is-lead')?.setAttribute('aria-label', '내 성향카드 — 마이페이지에서 보기');
    const today = ads.querySelector('.sheet-ad:not(.is-lead)');
    if (today) clickable(today, '오늘의 지면 — 다른 사건 보기', () => navigate('list'));
    const toMyPage = ads.querySelector('.sheet-ad.is-notice');
    if (toMyPage) clickable(toMyPage, '마이페이지로 — 내가 체험한 사건 목록', () => navigate('me', { view: 'cases' }));

    // 6막 읽기 화면과 같은 구조(.stage-wrap.reading-paper > .stage > .scene > .sheet)라 지면 스타일을 그대로 받는다
    const wrap = element('div', 'stage-wrap reading-paper');
    const stage = element('div', 'stage');
    const scene = element('div', 'scene');
    scene.append(sheet);
    stage.append(scene);
    wrap.append(stage);
    const desk = element('div', 'desk');
    desk.setAttribute('aria-hidden', 'true');
    root.replaceChildren(desk, element('h1', 'visually-hidden', `내 결과 · ${caseHeader.title}`), wrap);
  }

  async function load() {
    if (loading || !active()) return;
    loading = true;
    message('내 결과를 불러오는 중…');
    try {
      // { record: true }는 목 전용(지난 체험 기록 보기, comparisonMockApi.js). 실제 API는 두 번째 인수를 쓰지 않는다.
      // 마이페이지를 못 불러와도 처음 판단 · 최종 판결은 보여 준다
      const [data, myPage] = await Promise.all([api.getComparison(caseId, { record: true }), api.getMyPage().catch(() => null)]);
      if (active()) draw(data, myPage);
    } catch (error) {
      if (!active()) return;
      // 마치지 않은 체험(INVALID_STATE)은 withErrorRedirect가 지금 단계 화면으로 먼저 보낸다(app/errorRedirect.js)
      message('내 결과를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', button('다시 시도', load));
    } finally { loading = false; }
  }
  await load();
}
