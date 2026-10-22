// 유형 코드(4글자) → 공유 주소 shareId. 목 API가 서버 시드와 같은 shareId를 쓰게 한다(목과 실제의 P-4 · 공유 주소가 같아야 한다).
// 내 결과 지면은 BE-100부터 P-5 history[].type을 그대로 쓰므로 화면이 코드를 계산하지 않는다(FE-75 → BE-100).
// shareId는 서버 시드가 원본이고(R__personality_content.sql), 같은 값이 OG 빌드 입력 og/personality-types.json에 있다(백엔드 PersonalityContentSeedTest가 확인).
// 유형 코드는 주소 · 화면에 쓰지 않는다 — 이 파일은 코드를 shareId로 바꾸는 데만 쓴다
import og from '../../og/personality-types.json' with { type: 'json' };

// 유형 번호(01 ~ 16). 순서가 바뀌어도 번호는 바꾸지 않는다 (docs/character/personality-image-numbers.md)
export const TYPE_NUMBERS = {
  ESTJ: 1, ISTJ: 2, ESTP: 3, ENTJ: 4, ESFJ: 5, ISFJ: 6, INTJ: 7, ISTP: 8,
  ENTP: 9, ESFP: 10, ENFJ: 11, INFJ: 12, INTP: 13, ISFP: 14, ENFP: 15, INFP: 16,
};

const SHARE_IDS = new Map(og.types.map((type) => [type.typeNumber, type.shareId]));

/** 유형 코드의 shareId. 모르는 코드면 null */
export function shareIdOfCode(code) {
  return SHARE_IDS.get(TYPE_NUMBERS[code]) ?? null;
}
