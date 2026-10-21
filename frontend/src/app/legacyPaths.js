// 6막 재구성 전 옛 주소 → 새 주소 (BE-82 주소표, FE-58). 공유 링크 · 북마크로 옛 주소에 들어와도 같은 화면을 연다.
// 정리 화면(/summary)은 없앴다. 그 주소는 판결 직전(REVIEWED)에만 거쳐 가던 곳이라 3막 내 판결로 보낸다(IA v1.16, BE-82 표의 /letters 대신)
const OLD_TO_NEW = {
  start: 'impression',
  review: 'trial',
  summary: 'verdict',
  'result/ai': 'ai-verdict',
  'result/court': 'court-verdict',
  'result/compare': 'compare',
  opinion: 'letters',
};
const OLD_PATH = new RegExp(`^(/cases/[^/]+)/(${Object.keys(OLD_TO_NEW).join('|')})/?$`);

/** 옛 주소면 새 주소, 아니면 null */
export function legacyPath(pathname) {
  const found = OLD_PATH.exec(pathname);
  return found ? `${found[1]}/${OLD_TO_NEW[found[2]]}` : null;
}
