import { button, element } from '../../components/siteLayout.js';
import { renderToleranceScale } from '../../components/toleranceScale.js';
import { canTurnFolder, descriptionSections, noticeBlock, renderFolder, resultPages, setFolderTurnFrom, sheetMessage, takeFolderTurn, TOLERANCE_NOTE, turnPage, typeCard } from './personalityParts.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { AXES } from '../../utils/personalityAxes.js';

// S-16 성향 결과 — 내 결과 (WBS 7.6.5, FE-42 디자인). 공유 버튼은 해시 주소(/test/types/{shareId})를 공유한다(FR-8-6).
// 문구는 docs/personality-test-content.md 4 · 5장. 캐릭터 이미지는 유형마다 하나(personality-image-numbers.md, 01 ~ 32).
// 결과지 두 장(탭): 결과 = 유형 카드 · 가치관 4축 · 관용 지수 / 풀이 = 일상 · 판결 설명 · 안내 · 다음 행동.
// 4축 그래프: components/axisRow.js(마이페이지와 같이 쓴다). 엄격 쪽 남색 · 관대 쪽 갈색, 둘 다 진하게 막대를 가득 채운다.

// 공유 주소는 유형 설명만 열린다(개인 응답 · 점수 없음, FR-8-6). 이미지 공유(링크 미리보기)는 OG(COMMON-32)가 맡는다
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

// heading: 제목 단계(결과 화면은 "가치관 4축" 아래 h3, 공유 화면은 h2)
export function toleranceBlock(level, heading = 'h3') {
  const block = element('div', 'tolerance');
  block.append(element(heading, 'tolerance-title', '관용 지수'), renderToleranceScale(level), element('p', 'tolerance-note', TOLERANCE_NOTE));
  return block;
}

export async function renderPersonalityResultPage(container, { api, navigate }) {
  const { root, tabs, body } = renderFolder(container, { navigate, sheetLabel: '결과지' });
  root.classList.add('is-result');
  // 검사지를 막 제출했으면 가로로 눕힌 철을 돌려 세운다. 다 돌면 결과지 글자 · 탭이 나타난다(personality.css folder-turn)
  const turnFrom = takeFolderTurn();
  if (turnFrom && canTurnFolder()) {
    setFolderTurnFrom(root.querySelector('.test-folder'), turnFrom);
    root.classList.add('is-turning');
    root.addEventListener('animationend', (event) => {
      if (event.animationName === 'folder-content-in') root.classList.remove('is-turning'); // 마지막 연출(글자 나타남)이 끝나면
    });
  }
  // 결과 공개(FE-95): 검사지를 막 제출했을 때만 유형 이름을 도장처럼 찍고 4축을 그린다(personality.css name-stamp · axis-draw).
  // 결과 주소로 바로 오거나 탭을 오갈 때는 하지 않는다. 다 끝나면 클래스를 떼어, 탭을 넘겼다 돌아와도(display: none → 보임) 다시 돌지 않게 한다
  const revealing = Boolean(turnFrom);
  const startedAt = performance.now();
  if (revealing) {
    root.classList.add('is-revealing');
    const REVEAL = /^(name-stamp|reveal-up|axis-draw)$/;
    root.addEventListener('animationend', (event) => {
      if (!REVEAL.test(event.animationName)) return;
      const pending = root.getAnimations({ subtree: true }).some((item) => REVEAL.test(item.animationName ?? '') && item.playState !== 'finished');
      if (!pending) root.classList.remove('is-revealing');
    });
  }
  const active = () => container.contains(root);

  async function load() {
    sheetMessage(body, '결과를 불러오는 중이에요.');
    let result;
    try {
      result = await api.getMyPersonality();
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'PERSONALITY_RESULT_NOT_FOUND') {
        navigate('test', {}, { replace: true }); // 아직 테스트를 하지 않았으면(404) 테스트로 보낸다
        return;
      }
      sheetMessage(body, '결과를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.', { retry: load, alert: true }); // 같은 화면이라 이동 대신 다시 불러온다
      return;
    }
    if (active()) draw(result);
  }

  function draw({ type, axisScores }) {
    // 결과를 불러오는 동안 철은 이미 돌고 있다. 철이 다 선 뒤(1.05s)에 시작하도록 지난 시간만큼 뺀다. 철을 돌리지 않으면(모바일 · 바로 옴) 곧바로
    if (revealing) {
      const standAt = root.classList.contains('is-turning') ? 1.05 : 0.15;
      root.style.setProperty('--reveal-at', `${Math.max(0.15, standAt - (performance.now() - startedAt) / 1000).toFixed(2)}s`);
    }
    const card = typeCard(type);

    const graph = element('section', 'result-section');
    graph.append(element('h2', '', '가치관 4축'));
    const list = element('ul', 'axis-list');
    AXES.forEach((axis) => list.append(renderAxisRow(axis, axisScores[axis.key])));
    graph.append(list);
    const tolerance = element('section', 'result-section'); // 4축과 관용 지수는 점선으로 나눈 따로 섹션
    tolerance.append(toleranceBlock(type.tolerance, 'h2'));

    const actions = element('div', 'result-actions');
    const shareNote = element('p', 'share-note');
    shareNote.setAttribute('role', 'status');
    const more = element('div', 'result-more');
    more.append(
      button('공유하기', () => share(type.shareId, type, shareNote), 'secondary-button'),
      button('마이페이지에서 보기', () => navigate('me'), 'secondary-button'),
      button('다시 하기', () => navigate('test'), 'secondary-button'),
    );
    actions.append(button('실제 사건으로 판결하러 가기', () => navigate('list'), 'primary-button'), more);

    // 귀로 넘기면 새 장을 처음부터 읽도록 맨 위로 올린다
    const flipTo = (index) => {
      pages.show(index, { focusPanel: true });
      window.scrollTo({ top: 0 });
    };
    const pages = resultPages(tabs, body, [
      { label: '결과', nodes: [card, graph, tolerance, turnPage('풀이 보기', () => flipTo(1))] },
      { label: '풀이', nodes: [typeCard(type, { other: true }), ...descriptionSections(type), noticeBlock(), actions, shareNote, turnPage('결과 보기', () => flipTo(0), { prev: true })] },
    ], {
      // 공개 연출 중에 장을 넘기면 연출을 끝낸다: 숨겨진 장의 연출은 멈추고, 풀이 장이 같은 연출(.is-revealing)을 새로 받지 않게(리뷰 반영)
      onSwitch: () => root.classList.remove('is-revealing'),
    });
  }

  await load();
}
