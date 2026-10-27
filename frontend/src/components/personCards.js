import { element } from './siteLayout.js';
import { CHARACTER_NAMES } from '../utils/personalityAxes.js';
import { SASH_COLORS, SASH_ICONS, SASH_STYLES, portraitLabel, sashColorOf } from '../utils/profileDeco.js';
import { DEFAULT_TYPE_DECO } from '../utils/typeCardDeco.js';
import { signatureSvg } from './signatureInk.js';
import { fitPortraitIn } from '../utils/portraitFit.js';

// 프로필카드 · 성향카드를 그리는 조각(FE-88). 마이페이지(myPage.js) · 내 결과(myResultPage.js) · 커뮤니티 글쓴이 명함(communityParts.js)이 함께 쓴다(FE-79).
// 모양은 personCards.css의 .my-card · .deco-frame-* · .deco-bg-* · .deco-sash-* 가 정한다. 커뮤니티에서는 카드를 .cm-cards 안에 놓아 같은 커피 색을 입힌다

export const DEFAULT_NICKNAME = '익명의 판사'; // 로그인 · 닉네임 API 전까지 임시 문구
// 마이페이지 · 내 결과에 쓰는 내 이름(FE-100): 서버 값(P-5 nickname)이 있으면 그 값, 없으면 로그인한 세션의 닉네임(헤더와 같은 이름), 둘 다 없으면 임시 문구.
// 목 모드는 서버 닉네임이 없어 헤더는 "해송", 마이페이지는 "익명의 판사"로 갈렸고 내 정보 수정에서 바꾼 이름도 카드에 안 보였다
export const myNickname = (data, session) => data?.nickname || session?.nickname || DEFAULT_NICKNAME;

export const svg = (path, className) => {
  const node = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  node.setAttribute('viewBox', '0 0 16 16');
  node.setAttribute('aria-hidden', 'true');
  node.setAttribute('focusable', 'false');
  node.classList.add(className);
  const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  line.setAttribute('d', path);
  node.append(line);
  return node;
};

export function picture(src, alt, className = '') {
  const image = element('img', className);
  image.src = src;
  image.alt = alt;
  image.loading = 'lazy';
  image.addEventListener('error', () => image.classList.add('is-missing'), { once: true });
  return image;
}

// 그림 위 · 아래 여백(둥근 위쪽이 머리를 깎지 않게)은 200px 카드 기준이라 카드 폭에 비례시킨다(카드 칸도 비례하므로 어떤 크기에서도 같은 모양)
const fitOptions = (extra = {}) => (win) => {
  const width = win.closest('.my-card')?.clientWidth || 200;
  const scale = width / 200;
  return { top: Math.round(22 * scale), bottom: Math.max(2, Math.round(6 * scale)), ...extra };
};

export const nameOf = (list, id) => list.find((item) => item.id === id)?.name ?? id;

// 성향 이미지: 이미지(imageUrl)가 아직 없거나 불러오지 못하면 캐릭터 이름을 대신 둔다(personalityParts.js와 같은 규칙)
export function typePortrait(type) {
  const portrait = element('div', 'my-card-window');
  const characterName = CHARACTER_NAMES[type.character] ?? type.character;
  const showName = () => {
    portrait.replaceChildren(element('strong', '', characterName));
    portrait.setAttribute('aria-label', `${characterName} 캐릭터 (이미지 준비 중)`);
  };
  if (type.imageUrl) {
    const image = element('img');
    image.alt = `${characterName} 캐릭터`;
    image.addEventListener('error', showName, { once: true });
    image.src = type.imageUrl;
    portrait.append(image);
    fitPortraitIn(portrait, image, fitOptions({ centerWide: true })); // 이미지마다 다른 투명 여백을 걷어 대표 이미지와 비슷한 크기로 맞춘다(FE-67). 가로로 긴 그림은 프로필카드처럼 창 세로 가운데에
  } else showName();
  return portrait;
}

// 성향을 모를 때 타로카드의 빈 그림 창("?")
export function emptyWindow() {
  const window_ = element('div', 'my-card-window');
  window_.append(element('strong', '', '?'));
  window_.setAttribute('aria-hidden', 'true');
  return window_;
}

// 카드 책갈피(FE-68): 카드 좌우에 걸리는 띠 두 줄. 색은 .deco-sash-*, 모양은 .sash-style-*(짧고 굵은 책갈피 · 얇고 긴 띠)가 정한다.
// 짧은 책갈피에는 좌 · 우 아이콘을 따로 단다. 장식이라 스크린리더에는 숨긴다(카드의 읽는 이름에 적는다)
const SASH_ICON_ART = {
  star: { path: 'M8 1.5l1.8 4.7 4.7 1.8-4.7 1.8L8 14.5l-1.8-4.7L1.5 8l4.7-1.8z' },
  scale: { path: 'M8 2v12M4 14h8M3 5h10M3 5l-2 4h4zM13 5l-2 4h4z' },
  gavel: { path: 'M9.5 2.5l4 4M7.5 4.5l4 4M8.5 3.5l-4 4 2 2 4-4M5.5 8.5L2 12M9 14h5' },
  cup: { path: 'M3 6h8v4a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3zM11 7h1.5a1.5 1.5 0 0 1 0 3H11M5 2.5c0 1 1 1 1 2M8 2.5c0 1 1 1 1 2' },
  heart: { path: 'M8 13.5S2.5 10 2.5 6.3A2.8 2.8 0 0 1 8 5a2.8 2.8 0 0 1 5.5 1.3C13.5 10 8 13.5 8 13.5z' },
  naero: { src: '/images/characters/icon/generated/naero_face-happy.webp' },
  nambul: { src: '/images/characters/icon/generated/nambul_face-cheer.webp' },
  law: { src: '/images/characters/icon/cropped/law-scale_icon.webp' },
};
export function sashIcon(id) {
  const art = SASH_ICON_ART[id];
  if (!art) return null;
  return art.path ? svg(art.path, 'my-sash-icon') : picture(art.src, '', 'my-sash-icon');
}
export function sashes(deco) {
  if (deco.sashStyle === 'none') return [];
  return [['left', deco.sashIconLeft], ['right', deco.sashIconRight]].map(([side, icon]) => {
    const band = element('span', `my-card-sash is-${side}`);
    band.setAttribute('aria-hidden', 'true');
    const art = deco.sashStyle === 'short' ? sashIcon(icon) : null;
    if (art) band.append(art);
    return band;
  });
}
export const sashClasses = (deco) => `deco-sash-${sashColorOf(deco) ?? 'none'} sash-style-${deco.sashStyle}`;
export const sashNote = (deco) => {
  if (deco.sashStyle === 'none') return '';
  const icons = deco.sashStyle === 'short' ? [deco.sashIconLeft, deco.sashIconRight].filter((id) => id !== 'none').map((id) => nameOf(SASH_ICONS, id)) : [];
  return `, ${nameOf(SASH_COLORS[deco.sashStyle], sashColorOf(deco))} ${nameOf(SASH_STYLES, deco.sashStyle)}${icons.length ? `(${icons.join(' · ')})` : ''}`;
};

// 성향카드 꾸미기(FE-82): 기본 배경은 원래 타로카드 모양이라 클래스를 붙이지 않는다
export const typeDecoClasses = (deco) => `deco-frame-${deco.frame}${deco.background === DEFAULT_TYPE_DECO.background ? '' : ` deco-bg-${deco.background}`} ${sashClasses(deco)}`;

// 프로필카드의 속: 위 닉네임 · 그림 창(대표 이미지) · 아래 성향 이름. 테두리 · 배경 · 책갈피는 클래스(.deco-frame-* · .deco-bg-* · .deco-sash-*)로 입힌다
// 카드 아래 서명 그림. 크기는 CSS가 칸에 맞춰 정하니 서명 원래 비율만 남긴다. 내 정보 수정이 닉네임을 바꿀 때 다시 그리는 데도 쓴다
export function cardSignature(seed) {
  const ink = signatureSvg(seed);
  const [, , w, h] = ink.getAttribute('viewBox').split(' ');
  ink.setAttribute('width', w);
  ink.setAttribute('height', h);
  return ink;
}

// extra: { level: 'Lv.7' 같은 접두, signer: 서명 씨앗(주면 글자 대신 서명을 그린다), label: 서명일 때 낭독용 글 }
export function profileCardParts(deco, nickname, typeName, { level = null, signer = null } = {}) {
  const src = deco.portrait;
  const caption = portraitLabel(src);
  const face = element('div', 'my-card-window');
  const faceImage = picture(src, `${caption} 대표 이미지`, 'my-profile-face');
  face.append(faceImage);
  fitPortraitIn(face, faceImage, fitOptions({ centerWide: true })); // 가로로 긴 그림(사건 사진 등)은 창 세로 가운데에, 서 있는 캐릭터는 발을 아래에
  const result = element('p', `my-card-note${typeName || signer ? '' : ' is-empty'}${signer ? ' is-signature' : ''}${level ? ' has-level' : ''}`);
  if (level) result.append(element('span', 'my-card-level', level));
  if (signer) result.append(cardSignature(signer));
  else result.append(typeName ?? '성향 테스트 전');
  const line = signer ? `${nickname}의 서명` : `성향 ${typeName ?? '테스트 전'}`;
  return {
    caption,
    note: `${nickname}, 대표 이미지 ${caption}, ${level ? `${level} ` : ''}${line}${sashNote(deco)}`,
    classes: `is-profile deco-frame-${deco.frame} deco-bg-${deco.background} ${sashClasses(deco)}`,
    children: [element('p', 'my-card-name', nickname), face, result, ...sashes(deco)],
  };
}

// 누를 수 없는 프로필카드(커뮤니티 명함 · 작은 창). 크기는 부모가 정한다(카드 안 글자 · 책갈피는 카드 폭에 비례)
export function profileFace(deco, nickname, typeName, extra) {
  const parts = profileCardParts(deco, nickname, typeName, extra);
  const card = element('div', `my-card ${parts.classes} is-preview`);
  card.setAttribute('role', 'img');
  card.setAttribute('aria-label', `프로필카드: ${parts.note}`);
  card.append(...parts.children);
  return card;
}

// 성향카드(타로카드). deco가 있으면 성향카드 꾸미기(테두리 · 배경 · 책갈피)를 입힌다
export function typeFace(type, emptyNote = '"성향 테스트로 카드를 뽑아 봐요"', deco = null) {
  const card = element('div', `my-card is-type is-detail${type ? '' : ' is-empty'}${deco ? ` ${typeDecoClasses(deco)}` : ''}`);
  card.setAttribute('role', 'group');
  card.setAttribute('aria-label', `${type ? `성향카드: ${type.name}` : '성향카드: 아직 몰라요'}${deco ? sashNote(deco) : ''}`);
  card.append(element('p', 'my-card-name', type ? type.name : '아직 몰라요'), type ? typePortrait(type) : emptyWindow(), element('p', 'my-card-note my-type-quote', type ? type.meme : emptyNote), ...(deco ? sashes(deco) : []));
  return card;
}
