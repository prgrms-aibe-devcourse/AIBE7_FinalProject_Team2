import { button, element } from '../../components/siteLayout.js';
import { descriptionSections, markFolderTurn, noticeBlock, renderFolder, sheetMessage, typeCard } from './personalityParts.js';
import { toleranceBlock } from './personalityResultPage.js';

// 공유된 성향 결과 /test/types/{shareId} (S-16, FR-8-6). 주소는 유형 코드가 아니라 서버가 정한 해시 값이다. 친구가 링크를 열면 **유형 설명만** 보인다 —
// 공유한 사람의 응답 · 축 점수 · 체험 기록은 이 화면의 데이터에 없다. "나도 해 보기"로 S-15에 보낸다.
// 4축 그래프가 없어 내용이 짧으므로 내 결과와 달리 탭 없이 한 장에 이어서 보여 준다: 유형 카드 · 관용 지수 · 설명 · 안내 · 나도 해 보기
export async function renderPersonalitySharedPage(container, { shareId, api, navigate }) {
  const { root, body } = renderFolder(container, { navigate, sheetLabel: '공유된 결과지' });
  root.classList.add('is-result', 'is-shared');
  const active = () => container.contains(root);
  // 세운 철을 눕혀 검사지로 넘어간다(결과지로 넘어올 때의 철 돌리기와 짝, personality.css folder-turn)
  const tryIt = { label: '나도 해 보기', run: () => { markFolderTurn(root.querySelector('.test-folder')); navigate('test'); } };

  async function load() {
    sheetMessage(body, '유형을 불러오는 중이에요.');
    let type;
    try {
      type = await api.getPersonalityType(shareId);
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'PERSONALITY_TYPE_NOT_FOUND') { // 없는 shareId(404)
        sheetMessage(body, '찾을 수 없는 유형이에요. 링크를 다시 확인해 주세요.', { action: tryIt, alert: true });
        return;
      }
      sheetMessage(body, '유형을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.', { retry: load, alert: true });
      return;
    }
    if (!active()) return;

    const gauge = element('section', 'result-section');
    gauge.append(toleranceBlock(type.tolerance, 'h2'));
    const actions = element('div', 'result-actions');
    actions.append(button(tryIt.label, tryIt.run, 'primary-button'));
    body.replaceChildren(typeCard(type, { pair: true }), gauge, ...descriptionSections(type), noticeBlock(), actions);
  }

  await load();
}
