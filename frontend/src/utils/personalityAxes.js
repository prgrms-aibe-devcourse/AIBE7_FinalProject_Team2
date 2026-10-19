// 유형의 character 코드값(API 명세 9-1)에 대응하는 화면 이름
export const CHARACTER_NAMES = { NAERO: '내로', NAMBUL: '남불' };

// 가치관 4축 (FR-8-1). key는 factor.value_axis와 같은 값, strict = 엄격 글자, lenient = 관대 글자(I · N · F · P).
// 성향 테스트 · 결과 · 마이페이지 화면과 목 채점이 함께 쓴다.
export const AXES = [
  { key: 'APOLOGY_SINCERITY', label: '사과와 진정성', strict: { letter: 'E', name: '행동증명형' }, lenient: { letter: 'I', name: '마음신뢰형' } },
  { key: 'FAULT_STANDARD', label: '잘잘못의 기준', strict: { letter: 'S', name: '결과중시형' }, lenient: { letter: 'N', name: '의도중시형' } },
  { key: 'PRINCIPLE_RELATION', label: '원칙과 관계', strict: { letter: 'T', name: '원칙형' }, lenient: { letter: 'F', name: '관계형' } },
  { key: 'ORDER_OPPORTUNITY', label: '질서와 기회', strict: { letter: 'J', name: '질서형' }, lenient: { letter: 'P', name: '기회형' } },
];
