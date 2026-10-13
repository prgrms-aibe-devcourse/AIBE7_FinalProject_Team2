// 신문 콘셉트 화면(S-01 메인 · S-02 사건 목록)이 함께 쓰는 값 (WBS 7.7.1 공통 정리)
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// 지면 날짜 줄: "2026년 10월 13일 화요일"
export function todayLabel() {
  const now = new Date();
  return `${now.getFullYear()}년 ${now.getMonth() + 1}월 ${now.getDate()}일 ${WEEKDAYS[now.getDay()]}요일`;
}

// thumbnailUrl이 없을 때 쓰는 유형별 연출 이미지 (frontend/public/images)
export const fallbackImages = {
  MURDER: '/images/case-murder.svg',
  FRAUD: '/images/case-fraud.svg',
  INJURY: '/images/case-injury.svg',
};
