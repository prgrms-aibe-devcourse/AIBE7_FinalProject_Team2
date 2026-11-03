import { button, element, renderSiteFooter } from '../../components/siteLayout.js';
import { earButton, innerPageHead, pageIndex } from '../../components/newspaperFront.js';
import { todayLabel } from '../../utils/newspaper.js';
import { reducedMotion } from '../../utils/motion.js';
import { renderAxisRow } from '../../components/axisRow.js';
import { AXES, CHARACTER_NAMES } from '../../utils/personalityAxes.js';
import { leaveByTurn } from '../../utils/pageTurn.js';
import { descriptionSections, noticeBlock, sheetMessage, withOtherCharacter } from './personalityParts.js';
import { share, toleranceBlock } from './personalityResultPage.js';

// 신문 성향 테스트 결과 (FE-126, /test/paper/result). 신문 성향 테스트(personalityPaperPage.js)를 마치면 오는 지면이다.
// 내용은 기존 결과(personalityResultPage.js, 서류철 결과지)와 같다: 유형 이름 · 한 줄 · 캐릭터 그림 · 밈 · 가치관 4축 · 관용 지수 ·
// 일상 · 판결 설명 · 다른 캐릭터 그림 · 결과 안내 · 다음 행동(판결하러 가기 · 공유 · 마이페이지 · 다시 하기). 부품도 같은 것을 쓴다.
// 모양만 신문이다: 큰 제목 = 유형 이름, 머리기사 = 캐릭터 사진 | 4축 · 관용 지수, 아래 두 단 해설 기사
function portrait(type, className) {
  const figure = element('figure', `tp-photo ${className}`);
  const frame = element('div', 'tp-photo-frame tpr-photo-frame');
  const name = CHARACTER_NAMES[type.character] ?? type.character;
  if (type.imageUrl) {
    const image = element('img');
    image.src = type.imageUrl;
    image.alt = `${name} 캐릭터`;
    image.width = 800;
    image.height = 1000;
    image.addEventListener('error', () => image.replaceWith(element('strong', 'tpr-nameplate', name)), { once: true });
    frame.append(image);
  } else {
    frame.append(element('strong', 'tpr-nameplate', name));
  }
  figure.append(frame, element('figcaption', '', `${type.meme} · ${name}`));
  return figure;
}

export async function renderPersonalityPaperResultPage(container, { api, navigate, from }) {
  const root = element('div', 'case-list-page tp-page');
  const desk = element('div', 'desk');
  desk.setAttribute('aria-hidden', 'true');
  const sheet = element('article', 'paper inner-page tp-sheet tpr-sheet');
  sheet.setAttribute('aria-label', '신문 10면 오피니언 · 판결 성향 테스트 결과');
  const turnAway = leaveByTurn(sheet, 'case-list-page tp-page');
  if (from === 'paper' && !reducedMotion()) sheet.classList.add('is-turning-in'); // 응모권을 내면 한 장 넘겨 결과 지면이 온다
  if (from) history.replaceState({ ...history.state, extras: {} }, '');

  const runner = innerPageHead({
    earLeft: earButton('다시 하기', '신문 성향 테스트를 처음부터 다시 해요', () => navigate('testPaper'), 'ear-left'),
    earRight: earButton('메인으로', '책상으로 돌아가요', () => navigate('landing'), 'ear-right'),
    meta: `오피니언면 · ${todayLabel()} · 10면`,
  });
  const tabsZone = element('div', 'tabs-zone');
  tabsZone.append(pageIndex(navigate, 'test', { turn: turnAway })); // 다른 면으로 갈 때 지면을 넘긴다
  const body = element('div', 'tp-body');
  sheet.append(runner, tabsZone, body, renderSiteFooter());
  root.append(element('h1', 'visually-hidden', '판결 성향 테스트 결과'), desk, sheet);
  container.replaceChildren(root);
  window.scrollTo(0, 0);
  const active = () => container.contains(root);

  async function load() {
    sheetMessage(body, '응모권을 모아 정리하는 중이에요.');
    let result;
    try {
      result = await api.getMyPersonality();
    } catch (error) {
      if (!active()) return;
      if (error?.code === 'PERSONALITY_RESULT_NOT_FOUND') {
        navigate('testPaper', {}, { replace: true }); // 아직 테스트를 하지 않았으면 신문 테스트로
        return;
      }
      sheetMessage(body, '결과를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.', { retry: load, alert: true });
      return;
    }
    if (active()) draw(result);
  }

  function draw({ type, axisScores }) {
    // 큰 제목: 유형 이름 + 한 줄 성향
    const banner = element('header', 'tpr-banner');
    banner.append(element('p', 'tpr-kicker', '독자 설문 결과 · 나의 판결 성향'), element('h2', 'tp-headline tpr-name', type.name), element('p', 'tp-deck tpr-oneliner', type.oneLiner));

    // 머리기사: 캐릭터 사진 | 가치관 4축 · 관용 지수
    const graph = element('section', 'tpr-graph');
    graph.append(element('h3', 'tpr-label', '가치관 4축'));
    const list = element('ul', 'axis-list');
    AXES.forEach((axis) => list.append(renderAxisRow(axis, axisScores[axis.key])));
    graph.append(list, toleranceBlock(type.tolerance, 'h3'));
    const top = element('section', 'tpr-top');
    top.setAttribute('aria-label', '결과');
    top.append(portrait(type, 'tpr-portrait'), graph);

    // 해설 기사: 일상에서는 · 판결을 볼 때는(두 단) | 같은 유형의 다른 캐릭터 사진 + 결과 안내
    const story = element('section', 'tpr-story');
    story.setAttribute('aria-label', '풀이');
    const columns = element('div', 'tpr-columns');
    descriptionSections(type).forEach((section) => {
      section.className = 'tpr-column';
      section.querySelector('h2')?.replaceWith(element('h3', 'tpr-label', section.querySelector('h2').textContent));
      section.querySelector('p')?.classList.add('tp-text');
      columns.append(section);
    });
    const side = element('aside', 'tpr-side');
    const notice = noticeBlock({ own: true });
    notice.className = 'tpr-notice';
    side.append(portrait(withOtherCharacter(type), 'tpr-other'), notice);
    story.append(columns, side);

    // 다음 행동: 판결하러 가기(주) · 공유 · 마이페이지 · 다시 하기
    const actions = element('div', 'tpr-actions');
    const shareNote = element('p', 'share-note tpr-share-note');
    shareNote.setAttribute('role', 'status');
    const more = element('div', 'tpr-more');
    more.append(
      button('공유하기', () => share(type.shareId, type, shareNote), 'secondary-button'),
      button('마이페이지에서 보기', () => navigate('me'), 'secondary-button'),
      button('다시 하기', () => navigate('testPaper'), 'secondary-button'),
    );
    actions.append(button('실제 사건으로 판결하러 가기', () => navigate('list'), 'primary-button'), more);

    body.replaceChildren(banner, top, story, actions, shareNote);
    const name = banner.querySelector('.tpr-name');
    name.tabIndex = -1;
    name.focus({ preventScroll: true });
  }

  await load();
}
