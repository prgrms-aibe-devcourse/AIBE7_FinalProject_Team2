// 서류철 부품(FE-96): 판결 체험 파일철과 성향 결과지가 함께 쓰는 종이 귀 · 진행 책갈피. 모양은 folderParts.css
import { element } from './siteLayout.js';
import { getBookmarkColor, selectedBookmarkFace } from '../utils/bookmarkCharacter.js';

// 종이 귀 그림(장식). 눌러서 장을 넘기는 버튼은 쓰는 곳이 만들고 이 그림을 바로 안에 넣는다(호버 · 눌림이 버튼을 따른다).
// flipped = 왼쪽 아래 모서리(앞 장으로 돌아가는 귀)
export function paperFold({ flipped = false } = {}) {
  const fold = element('span', `paper-fold${flipped ? ' is-flipped' : ''}`);
  fold.setAttribute('aria-hidden', 'true');
  return fold;
}

// 진행 책갈피: 펼쳐 본 가장 뒤 장의 탭 뒤에 끼우는 리본 + 마이페이지에서 고른 얼굴(장식). 잠금이 아니라 진행 표시다.
// 리본 색 · 얼굴은 마이페이지 책갈피 설정(FE-67 · FE-68). 얼굴을 못 불러오면 리본만 남긴다
export function folderBookmark() {
  const bookmark = element('span', `folder-bookmark bookmark-color-${getBookmarkColor()}`);
  bookmark.setAttribute('aria-hidden', 'true');
  const face = element('img');
  face.alt = '';
  face.src = selectedBookmarkFace();
  face.addEventListener('error', () => face.remove(), { once: true });
  bookmark.append(face);
  return bookmark;
}
