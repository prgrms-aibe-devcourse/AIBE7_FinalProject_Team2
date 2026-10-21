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

// 공용 재연 컷(FE-32): 사건 사진이 없을 때 쓰는 샘플 연출 사진. 내로 · 남불이 기록을 읽는 장면이라 특정 사건 내용을 드러내지 않는다.
// 원본은 이미지 레포 character/cuts/reenact/common(PNG 약 2MB) → 1000px webp로 줄여 public에 둔다. 사건 번호로 돌려 쓴다
export const commonPhotos = ['/images/cases/common-1.webp', '/images/cases/common-2.webp', '/images/cases/common-3.webp'];

// 발단 · 결론 자리(기사 지면 작은 사진 · 증거조사 사진)용 공용 컷. 전용 컷이 나오면 이 두 경로의 파일만 바꾼다(지금은 임시로 common-2 · 3)
export const commonStagePhotos = { origin: '/images/cases/common-2.webp', conclusion: '/images/cases/common-3.webp' };

/** 공용 재연 컷 한 장(seed로 고정 — 같은 자리는 늘 같은 컷). role('origin' | 'conclusion')을 주면 그 자리 전용 컷 */
export function commonPhotoAt(seed = 0, role) {
  if (commonStagePhotos[role]) return commonStagePhotos[role];
  return commonPhotos[Math.abs(Number(seed) || 0) % commonPhotos.length];
}

/** 기사 속 작은 사진 두 자리(발단 · 결론). 비어 있는 자리는 공용 컷으로 채운다 */
export function extraPhotosOf(extraPhotos) {
  const [origin, conclusion] = extraPhotos ?? [];
  return [origin ?? { src: commonStagePhotos.origin }, conclusion ?? { src: commonStagePhotos.conclusion }];
}

/** 사진이 없거나 불러오지 못하면 공용 컷으로 바꾼다. 공용 컷도 못 불러오면 깨진 이미지 대신 사진을 뺀다 */
export function useCommonPhotoOnError(image, seed = 0, role) {
  image.addEventListener('error', () => {
    if (image.dataset.fallback) { image.remove(); return; }
    image.dataset.fallback = 'true';
    image.src = commonPhotoAt(seed, role);
  });
}

/** 사건 사진: 썸네일이 있으면 그것, 없으면 공용 재연 컷(사건 번호로 고정 — 새로고침해도 같은 사진) */
export function casePhoto(item) {
  if (item?.thumbnailUrl) return item.thumbnailUrl;
  const id = Number(item?.caseId) || 0;
  return commonPhotos[id % commonPhotos.length];
}
