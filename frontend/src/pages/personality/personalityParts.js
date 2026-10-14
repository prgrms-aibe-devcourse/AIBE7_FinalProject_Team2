import { element } from '../../components/siteLayout.js';
import { CHARACTER_NAMES } from '../../utils/personalityAxes.js';

// S-16(내 결과)과 공유된 결과(/test/types/{shareId})가 함께 쓰는 조각. 공유 화면에는 개인 응답 · 축 점수를 넣지 않는다(FR-8-6).
// 4글자 코드(INTP 등)와 축 글자(E · I 등)는 화면에 보이지 않는다(상표 우려, 10/13). 내부 데이터에만 쓰고, 공유 주소는 해시 값({shareId})이다.
// 캐릭터 이미지(imageUrl)가 아직 없으면(null, 7.6.8) 이름표 자리를 대체 이미지로 둔다.

export function typeCard(type, kicker) {
  const card = element('section', 'result-card');
  const characterName = CHARACTER_NAMES[type.character] ?? type.character;
  const portrait = element('div', 'result-portrait');
  const showNameplate = () => {
    portrait.replaceChildren(element('strong', '', characterName), element('span', '', type.meme));
    portrait.setAttribute('aria-label', `${characterName} 캐릭터 (이미지 준비 중)`);
  };
  if (type.imageUrl) {
    const image = element('img');
    image.alt = `${characterName} 캐릭터`;
    image.addEventListener('error', showNameplate, { once: true }); // 파일이 없거나 불러오지 못하면 깨진 이미지 대신 이름표
    image.src = type.imageUrl;
    portrait.append(image, element('span', '', type.meme));
  } else {
    showNameplate();
  }
  const head = element('div', 'result-head');
  head.append(
    element('p', 'personality-kicker', kicker),
    element('h1', '', type.name),
    element('p', 'result-oneliner', type.oneLiner),
  );
  card.append(portrait, head);
  return card;
}

export function descriptionSections(type) {
  const daily = element('section', 'result-section');
  daily.append(element('h2', '', '일상에서는'), element('p', '', type.dailyDescription));
  const verdict = element('section', 'result-section');
  verdict.append(element('h2', '', '판결을 볼 때는'), element('p', '', type.verdictDescription));
  return [daily, verdict];
}

export function noticeBlock() {
  const notice = element('section', 'result-notice');
  [
    '이 결과는 일상 가치관을 기준으로 한 비교이며, 실제 법적 판단과는 다를 수 있어요.',
    '이 판결 성향 유형은 성격 유형 검사와 별개예요. 알고 있는 성격 유형과 다르게 나올 수 있어요.',
  ].forEach((text) => notice.append(element('p', '', text)));
  return notice;
}
