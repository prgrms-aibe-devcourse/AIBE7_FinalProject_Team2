// 경험치 페이지(FE-120): 마이페이지 첫 화면의 경험치 막대를 누르면 열린다. 지금 레벨 · 경험치, 직업이 열리는 레벨 표, 경험치 얻는 방법 표를 보여 준다.
// 쌓인 경험치의 내역(획득 로그)은 보여 주지 않는다 — 서버에 내역 조회가 없고, 이 화면은 "어떻게 올라가는지"를 알리는 표가 맡는다.
// 레벨 · 직업 값은 서버 L-1(data.level)이고, 얻는 방법 표는 API 명세 11장 수급처 표를 그대로 옮긴 고정 문구다(값이 바뀌면 명세와 함께 고친다)
import { element, mockTag } from '../../components/siteLayout.js';
import { API_MODE } from '../../api/config.js';
import { progressOf } from '../../utils/levelProgress.js';

const IS_MOCK = API_MODE === 'mock';
const TOP_LEVEL = 100;
const won = (number) => number.toLocaleString('ko-KR');

// [구분, 얻는 방법, 경험치, 받는 조건]
const SOURCES = [
  ['판결 체험', '사건 첫 완료', '100', '사건마다 한 번'],
  ['판결 체험', '판결 성향 테스트', '100', '한 번'],
  ['판결 체험', '출석', '5', '하루 한 번'],
  ['판결 체험', '도전과제 달성', '50 · 100 · 200', '과제마다 한 번, 어려운 과제일수록 많이 줘요'],
  ['판결 체험', '일러스트 수집', '10', '한 장마다. 아직 지급하지 않아요(준비 중)'],
  ['판결 체험', 'SNS 공유', '100', '완료한 사건만, 사건마다 한 번 · 하루 한 번'],
  ['커뮤니티', '글 쓰기', '30', '하루 3건까지'],
  ['커뮤니티', '판결 공유 글 보너스', '+20', '사건마다 한 번'],
  ['커뮤니티', '댓글 쓰기', '10', '하루 5건까지'],
  ['커뮤니티', '좋아요 누르기', '2', '글마다 한 번, 하루 10개까지'],
  ['커뮤니티', '좋아요 받기', '3', '글마다 10개까지, 하루 30개까지'],
];

function heroBlock(level) {
  const { top, span, done, percent } = progressOf(level);
  const box = element('section', 'lv-hero');
  const now = element('p', 'lv-now');
  now.append(element('b', '', `Lv.${level.level}`), element('span', '', level.job?.name ?? '무직'));
  const bar = element('div', 'my-achievement-bar lv-bar');
  bar.setAttribute('role', 'progressbar');
  bar.setAttribute('aria-label', '경험치 진행률');
  bar.setAttribute('aria-valuemin', '0');
  bar.setAttribute('aria-valuemax', String(span));
  bar.setAttribute('aria-valuenow', String(done));
  bar.setAttribute('aria-valuetext', top ? '최고 레벨' : `${done} / ${span} XP`);
  const fill = element('span');
  fill.style.width = `${percent}%`;
  bar.append(fill);
  const ends = element('p', 'lv-ends');
  ends.append(element('span', '', `Lv.${level.level} · ${won(level.currentLevelXp)} XP`), element('span', '', top ? '최고 레벨' : `Lv.${level.level + 1} · ${won(level.nextLevelXp)} XP`));
  const total = element('p', 'lv-total', `지금까지 모은 경험치 ${won(level.xp)} XP${top ? '' : ` · Lv.${level.level + 1}까지 ${won(level.nextLevelXp - level.xp)} XP`}`);
  if (IS_MOCK) total.append(mockTag());
  box.append(now, bar, ends, total);
  const next = (level.jobs ?? []).find((job) => !job.unlocked);
  if (next) box.append(element('p', 'lv-next', `다음 직업은 ${next.name} — Lv.${next.unlockLevel}에 열려요(${won(next.unlockXp - level.xp)} XP 남음)`));
  return box;
}

// 직업이 열리는 레벨 표. 지금 카드에 보이는 직업(selected, 위쪽 직업명과 같다)에 "지금"을 달고, 잠긴 직업은 남은 경험치를 보여 준다
function jobsBlock(level) {
  const section = element('section', 'lv-section');
  section.append(element('h2', 'lv-title', '직업은 이렇게 열려요'), element('p', 'my-hint', `레벨이 오르면 직업이 하나씩 열리고, 한 번 열린 직업은 다시 잠기지 않아요. 최고 레벨은 Lv.${TOP_LEVEL}이에요.`));
  const table = element('table', 'lv-table');
  const head = element('tr');
  ['레벨', '직업', '필요 경험치(누적)', '상태'].forEach((label) => { const cell = element('th', '', label); cell.setAttribute('scope', 'col'); head.append(cell); });
  const thead = element('thead');
  thead.append(head);
  table.append(thead);
  const body = element('tbody');
  const jobs = level.jobs ?? [];
  const nowCode = jobs.find((job) => job.selected)?.code;
  jobs.forEach((job) => {
    const isNow = job.code === nowCode;
    const row = element('tr', `${job.unlocked ? 'is-open' : 'is-locked'}${isNow ? ' is-now' : ''}`);
    const state = job.unlocked ? (isNow ? '지금' : '열림') : `${won(job.unlockXp - level.xp)} XP 남음`;
    const levelCell = element('th', '', `Lv.${job.unlockLevel}`);
    levelCell.setAttribute('scope', 'row');
    row.append(levelCell, element('td', '', job.name), element('td', 'lv-num', `${won(job.unlockXp)} XP`), element('td', 'lv-state', state));
    body.append(row);
  });
  table.append(body);
  const wrap = element('div', 'lv-table-wrap');
  wrap.append(table);
  section.append(wrap);
  return section;
}

function sourcesBlock() {
  const section = element('section', 'lv-section');
  section.append(element('h2', 'lv-title', '경험치 얻는 방법'), element('p', 'my-hint', '판결 체험과 커뮤니티 활동으로 쌓여요. 같은 활동을 되풀이해도 정해진 횟수까지만 받아요.'));
  const table = element('table', 'lv-table');
  const head = element('tr');
  ['구분', '방법', '경험치', '받는 조건'].forEach((label) => { const cell = element('th', '', label); cell.setAttribute('scope', 'col'); head.append(cell); });
  const thead = element('thead');
  thead.append(head);
  table.append(thead);
  const body = element('tbody');
  SOURCES.forEach(([group, way, xp, rule], index) => {
    const starts = index === 0 || SOURCES[index - 1][0] !== group;
    const row = element('tr', starts && index > 0 ? 'is-group-start' : '');
    if (starts) {
      const cell = element('th', 'lv-group', group);
      cell.setAttribute('scope', 'rowgroup');
      cell.rowSpan = SOURCES.filter(([name]) => name === group).length;
      row.append(cell);
    }
    row.append(element('td', '', way), element('td', 'lv-num', xp), element('td', 'lv-rule', rule));
    body.append(row);
  });
  table.append(body);
  const wrap = element('div', 'lv-table-wrap');
  wrap.append(table);
  section.append(wrap);
  return section;
}

export function renderLevelPage(data) {
  const box = element('div', 'my-detail lv-page');
  if (!data.level) {
    box.append(element('p', 'my-hint', '레벨 정보를 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.'));
    return box;
  }
  box.append(heroBlock(data.level), jobsBlock(data.level), sourcesBlock());
  return box;
}
