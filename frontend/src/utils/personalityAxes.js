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

// 관대 쪽 비중(%). 4축 막대 · 비중 글자 · 변경폭이 모두 이 값을 쓴다(FE-70).
// 엄격 쪽을 먼저 반올림하고 나머지를 관대 쪽으로 둔다. 반올림하면 50%가 되지만 실제로는 반반이 아니면(0.497 · 0.503) 기운 쪽으로 1%p 옮긴다.
// 서버는 유형 글자를 정확한 분수의 부호로 정하므로(성향 산식 3-3), 막대는 "반반"인데 유형은 한쪽 글자인 어긋남을 막는다. 정확히 반반이면 50(동점은 관대 글자)
const TIE = 1e-9; // 분수 평균의 부동소수 오차는 반반으로 본다
export function lenientPercent(ratio) {
  const lenient = 100 - Math.round((1 - ratio) * 100);
  if (lenient !== 50 || Math.abs(ratio - 0.5) < TIE) return lenient;
  return ratio > 0.5 ? 51 : 49;
}
