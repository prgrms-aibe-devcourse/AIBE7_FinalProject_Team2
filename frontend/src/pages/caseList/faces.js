// S-02 신문 면 정의 (FE-123). 탭 하나가 신문 한 면이고, 면마다 달라야 하는 정책을 이 목록 한 곳에 둔다.
// 화면(caseListPage.js) 없이 시험할 수 있게 DOM을 쓰지 않는다(faces.test.js). 면 본문을 그리는 쪽은 caseListPage.js의 FACE_VIEWS가 view 이름으로 고른다.
// 새 면을 더하는 방법은 README.md "면 추가하기"를 본다.
import { FRONT_LIMIT } from '../../utils/newspaperFaces.js';

// 1면 포스트잇 문구: 지금 눌러서 체험을 시작할 수 있는 사건 수에 맞춘다(FE-102 — 모두 열린 것처럼 "눌러 보세요"만 권하지 않는다).
// 포스트잇은 1면에만 붙어 있다(FE-126, caseListPage.js draw)
export function tipMessage(total, playable) {
  const base = `오늘 지면에 사건이 ${total}건 실렸어요.`;
  if (playable >= total) return `${base} 마음이 가는 기사를 눌러 보세요!`;
  if (playable > 0) return `${base} 지금 체험할 수 있는 기사는 ${playable}건이에요.`;
  return `${base} 체험은 곧 열려요. 기사는 미리 읽어 볼 수 있어요.`;
}

/**
 * 면 하나. 빠진 속성은 아래 기본값을 쓴다.
 * - key: 면 열쇠(주소 ?type= · 탭 data-type). 1면은 undefined
 * - label: 탭 이름, name: 양옆 화살표 · 귀에 쓰는 이름("살인면")
 * - view: 본문 모양(caseListPage.js FACE_VIEWS의 이름 — cases · index · test · opinion)
 * - apiType: API 1에 보낼 범죄 유형. 없으면 전체 목록을 받는다
 * - front: 1면(9건만 싣고, 머리기사 표시가 붙고, 안쪽 면 모양이 아니다)
 * - count(summary): 탭 옆 건수. null이면 달지 않는다
 * - hideWhenEmpty: 건수가 0이면 탭 · 넘김에서 숨긴다(utils/newspaperFaces.js visibleFaces)
 * - fill: 면 아래 세로 여백을 읽을거리로 채운다(FE-119 결정: 지금은 모든 면. 전용 내용을 넣은 면은 끌 수 있다)
 */
const face = (props) => ({
  name: props.label, apiType: undefined, front: false, count: () => null, hideWhenEmpty: false, fill: true, ...props,
});
const crimeFace = (key, label) => face({
  key, label, name: `${label}면`, view: 'cases', apiType: key, hideWhenEmpty: true, count: (summary) => summary.byCrimeType[key] ?? 0,
});

// 1면(헤드, 9건만) → 범죄 유형 면(0건이면 숨김) → 색인(전체 제목 목록 · 검색 · 종류 버튼) → 성향 테스트 · 오피니언. 순서가 곧 넘김 순서다
export const FACES = [
  face({ key: undefined, label: '1면', view: 'cases', front: true, count: (summary) => Math.min(summary.total, FRONT_LIMIT) }),
  crimeFace('MURDER', '살인'),
  crimeFace('FRAUD', '사기'),
  crimeFace('INJURY', '상해'),
  crimeFace('ROBBERY', '강도'),
  crimeFace('THEFT', '절도'),
  face({ key: 'INDEX', label: '색인', view: 'index' }),
  face({ key: 'TEST', label: '성향 테스트', view: 'test' }),
  face({ key: 'OPINION', label: '오피니언', view: 'opinion' }),
];

/** 열쇠로 면을 찾는다(undefined는 1면). 없는 열쇠면 undefined */
export const faceOf = (key) => FACES.find((item) => item.key === key);
/** 주소 · 다른 화면에서 받은 값이 면 열쇠면 그대로, 아니면 undefined(1면) */
export const faceKeyOf = (value) => (value && faceOf(value) ? value : undefined);
