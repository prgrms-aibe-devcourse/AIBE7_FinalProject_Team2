// 한글 단어 뒤에 붙는 목적격 조사(을 · 를) 판정. 백엔드 KoreanParticle(judgment 도메인)과 같은 규칙이다(BE-10 · BE-26).
// S-09 내 판결 한 줄 요약 · 공통점/차이점 규칙 문장이 함께 쓴다(comparisonMock.js).

const HANGUL_FIRST = 0xac00;
const HANGUL_LAST = 0xd7a3;
const JONGSEONG_COUNT = 28;

// 숫자를 한글로 읽었을 때 받침이 있는지 (0 영 · 1 일 · 3 삼 · 6 육 · 7 칠 · 8 팔)
const DIGIT_HAS_FINAL_CONSONANT = [true, true, false, true, false, false, true, true, true, false];

/**
 * 받침이 있으면 "을", 없으면 "를".
 * 단어는 팀이 자유롭게 입력하는 값이라 괄호 · 문장부호로 끝날 수 있다("반성(자백)").
 * 그래서 끝에서부터 거슬러 올라가 처음 만나는 한글 또는 숫자로 판정한다 (BE-10 리뷰).
 */
export function objectParticle(word) {
  for (let i = word.length - 1; i >= 0; i -= 1) {
    const code = word.charCodeAt(i);
    if (code >= HANGUL_FIRST && code <= HANGUL_LAST) {
      return (code - HANGUL_FIRST) % JONGSEONG_COUNT === 0 ? '를' : '을';
    }
    const digit = word[i];
    if (digit >= '0' && digit <= '9') {
      return DIGIT_HAS_FINAL_CONSONANT[Number(digit)] ? '을' : '를';
    }
  }
  return '를';
}
