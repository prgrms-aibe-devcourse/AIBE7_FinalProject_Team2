import { button, element, renderSiteHeader } from '../../components/siteLayout.js';
import { renderToleranceScale } from '../../components/toleranceScale.js';
import { descriptionSections, noticeBlock, typeCard } from './personalityParts.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { AXES } from '../../utils/personalityAxes.js';

// S-16 성향 결과 — 내 결과 (WBS 7.6.5). 공유 버튼은 해시 주소(/test/types/{shareId})를 공유한다(FR-8-6). 이미지 공유는 캐릭터 이미지(7.6.8) 뒤에 붙인다.
// 캐릭터 이미지는 아직 없어서(7.6.8) 이름표 자리만 둔다. 문구는 docs/personality-test-content.md 4 · 5장.
// 4축 그래프: 축마다 양쪽 성향의 비중(관대 쪽 비중 = axisScores). 관용 지수 게이지: 0 ~ 4칸.
// 공유 주소는 유형 설명만 열린다(개인 응답 · 점수 없음, FR-8-6). 이미지 공유는 캐릭터 이미지(7.6.8) 뒤에 붙인다
// 공유 주소는 서버가 준 해시 값(shareId)이다. 유형 코드는 주소에도 드러나지 않는다
async function share(shareId, type, note) {
  const url = `${window.location.origin}/test/types/${shareId}`;
  note.textContent = '';
  if (navigator.share) {
    try {
      await navigator.share({ title: `나의 판결 성향은 ${type.name}`, text: type.oneLiner, url });
      return;
    } catch (error) {
      if (error?.name === 'AbortError') return; // 공유 창을 닫은 경우
      // 공유 창을 못 열면(권한 · 미지원 환경) 링크 복사로 넘어간다
    }
  }
  try {
    await navigator.clipboard.writeText(url);
    note.textContent = '링크를 복사했어요. 친구에게 붙여 넣어 보내 보세요.';
  } catch {
    note.textContent = `링크를 복사하지 못했어요. 주소를 직접 복사해 주세요: ${url}`;
  }
}

function toleranceGauge(level) {
  const gauge = element('div', 'tolerance');
  gauge.append(element('p', 'tolerance-title', '관용 지수'), renderToleranceScale(level));
  gauge.append(element('p', 'tolerance-note', '관용 지수는 높을수록 좋은 것이 아니에요. 잘잘못을 따질 때 어떤 가치를 더 중요하게 보는지를 나타낼 뿐이에요.'));
  return gauge;
}

export async function renderPersonalityResultPage(container, { api, navigate }) {
  const root = element('div', 'personality-page');
  container.replaceChildren(root);
  const active = () => container.contains(root);
  root.append(renderSiteHeader({ navigate, onIntro: () => navigate('landing', { section: 'intro' }) }));
  const main = element('main', 'personality-main');
  root.append(main);

  async function load() {
    const loading = element('p', 'message-panel', '결과를 불러오는 중이에요.');
    loading.setAttribute('role', 'status');
    main.replaceChildren(loading);

    let result;
    try {
      result = await api.getMyPersonality();
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'PERSONALITY_RESULT_NOT_FOUND') {
        navigate('test', {}, { replace: true }); // 아직 테스트를 하지 않았으면(404) 테스트로 보낸다
        return;
      }
      const panel = element('div', 'message-panel');
      panel.setAttribute('role', 'alert');
      panel.append(element('p', '', '결과를 불러오지 못했어요.'), button('다시 시도', load, 'primary-button')); // 같은 화면이라 이동 대신 다시 불러온다
      main.replaceChildren(panel);
      return;
    }
    if (!active()) return;
    draw(result);
  }

  function draw(result) {
    const { type, axisScores } = result;
    const card = typeCard(type, '나의 판결 성향');

    const graph = element('section', 'result-section');
    graph.append(element('h2', '', '가치관 4축'));
    const list = element('ul', 'axis-list');
    AXES.forEach((axis) => list.append(renderAxisRow(axis, axisScores[axis.key])));
    graph.append(list, toleranceGauge(type.tolerance));

    const actions = element('div', 'personality-actions result-actions');
    actions.append(
      button('실제 사건으로 판결하러 가기', () => navigate('list'), 'primary-button'),
      button('마이페이지에서 보기', () => navigate('me'), 'secondary-button'),
      button('다시 하기', () => navigate('test'), 'secondary-button'),
    );
    const shareNote = element('p', 'share-note');
    shareNote.setAttribute('role', 'status');
    actions.append(button('공유하기', () => share(type.shareId, type, shareNote), 'secondary-button'));

    main.replaceChildren(card, graph, ...descriptionSections(type), noticeBlock(), actions, shareNote);
  }

  await load();
}
