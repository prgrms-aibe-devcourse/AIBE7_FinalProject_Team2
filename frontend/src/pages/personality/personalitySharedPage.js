import { button, element } from '../../components/siteLayout.js';
import { descriptionSections, noticeBlock, renderFolder, resultPages, sheetMessage, turnPage, typeCard } from './personalityParts.js';
import { toleranceBlock } from './personalityResultPage.js';

// 공유된 성향 결과 /test/types/{shareId} (S-16, FR-8-6). 주소는 유형 코드가 아니라 서버가 정한 해시 값이다. 친구가 링크를 열면 **유형 설명만** 보인다 —
// 공유한 사람의 응답 · 축 점수 · 체험 기록은 이 화면의 데이터에 없다. "나도 해 보기"로 S-15에 보낸다.
// 결과지 두 장(탭)은 내 결과와 같다: 결과 = 유형 카드 · 관용 지수(축 점수 없음) / 풀이 = 설명 · 안내 · 나도 해 보기
export async function renderPersonalitySharedPage(container, { shareId, api, navigate }) {
  const { root, tabs, body } = renderFolder(container, { navigate, sheetLabel: '공유된 결과지' });
  root.classList.add('is-result');
  const active = () => container.contains(root);
  const tryIt = { label: '나도 해 보기', run: () => navigate('test') };

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
    const pages = resultPages(tabs, body, [
      { label: '결과', nodes: [typeCard(type), gauge, turnPage('풀이 보기', () => pages.show(1, { focusPanel: true }))] },
      { label: '풀이', nodes: [...descriptionSections(type), noticeBlock(), actions] },
    ]);
  }

  await load();
}
