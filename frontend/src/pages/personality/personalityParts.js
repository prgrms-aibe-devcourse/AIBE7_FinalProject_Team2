import { element } from '../../components/siteLayout.js';

// S-16(내 결과)과 공유된 결과(/test/types/{code})가 함께 쓰는 조각. 공유 화면에는 개인 응답 · 축 점수를 넣지 않는다(FR-8-6).
// 캐릭터 이미지는 아직 없어서(7.6.8) 이름표 자리만 둔다.

export function typeCard(type, typeCode, kicker) {
  const card = element('section', 'result-card');
  const portrait = element('div', 'result-portrait');
  portrait.append(element('strong', '', type.character), element('span', '', type.meme));
  portrait.setAttribute('aria-label', `${type.character} 캐릭터 (이미지 준비 중)`);
  const head = element('div', 'result-head');
  head.append(
    element('p', 'personality-kicker', kicker),
    element('p', 'result-code', typeCode),
    element('h1', '', type.name),
    element('p', 'result-oneliner', type.oneLiner),
  );
  card.append(portrait, head);
  return card;
}

export function descriptionSections(type) {
  const daily = element('section', 'result-section');
  daily.append(element('h2', '', '일상에서는'), element('p', '', type.daily));
  const verdict = element('section', 'result-section');
  verdict.append(element('h2', '', '판결을 볼 때는'), element('p', '', type.verdict));
  return [daily, verdict];
}

export function noticeBlock() {
  const notice = element('section', 'result-notice');
  [
    '이 결과는 일상 가치관을 기준으로 한 비교이며, 실제 법적 판단과는 다를 수 있어요.',
    '4글자 판결 성향 유형은 성격 유형 검사와 별개예요. 알고 있는 성격 유형과 다르게 나올 수 있어요.',
  ].forEach((text) => notice.append(element('p', '', text)));
  return notice;
}
