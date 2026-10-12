// sample.html 연동 확인 페이지 스크립트. 앱 코드(라우터 표 · api)를 그대로 불러와 동작을 확인한다.
import { API_MODE } from '../api/config.js';
import { loadApi } from '../api/index.js';
import { routes } from '../app/routes.js';
import { screenByStatus } from '../utils/screenByStatus.js';

const $ = (selector) => document.querySelector(selector);

function row(...cells) {
  const tr = document.createElement('tr');
  for (const cell of cells) {
    const td = document.createElement('td');
    if (cell instanceof Node) td.append(cell);
    else td.textContent = cell;
    tr.append(td);
  }
  return tr;
}

function code(text) {
  const node = document.createElement('code');
  node.textContent = text;
  return node;
}

// 기준 사건에 따라 href가 바뀌는 링크. pattern은 routes.js의 주소 형식 (예: /cases/:caseId/review)
const links = [];
function caseLink(pattern, label) {
  const anchor = document.createElement('a');
  links.push({ anchor, pattern, label });
  return anchor;
}

function refreshLinks(caseId) {
  for (const { anchor, pattern, label } of links) {
    anchor.href = pattern.replace(':caseId', String(caseId));
    anchor.textContent = label ?? anchor.href.replace(window.location.origin, '');
  }
}

$('#mode').textContent = API_MODE === 'real' ? 'real (실제 백엔드)' : 'mock (목 데이터)';
$('#mockCaseNote').hidden = API_MODE !== 'mock';

// 화면 표 — routes.js에서 그대로 만든다
for (const route of routes) {
  $('#routes tbody').append(row(
    code(route.name),
    route.path ? caseLink(route.path) : '주소 없음 (맞는 화면이 없을 때)',
    route.title,
    route.ownsFooter ? '화면이 직접' : '라우터가 붙임',
    route.needsHeader ? '필요' : '—',
  ));
}

// 체험 상태 → 화면 표
const routeByName = new Map(routes.map((route) => [route.name, route]));
for (const [status, screen] of Object.entries(screenByStatus)) {
  const pattern = routeByName.get(screen)?.path ?? '';
  $('#screens tbody').append(row(code(status), code(screen), pattern ? code(pattern) : '화면 준비 중'));
}

// 상태를 바꾼 뒤 열어 볼 주소 (기준 사건으로 만든다)
const quick = [
  ['/cases/:caseId/verdict', null],
  ['/cases/:caseId/result/ai', null],
  ['/cases/:caseId/start', null],
  ['/cases/999999/review', '/cases/999999/review (없는 사건)'],
];
quick.forEach(([pattern, label], index) => {
  if (index > 0) $('#quickLinks').append(' · ');
  $('#quickLinks').append(caseLink(pattern, label));
});

// 기준 사건: 사건 목록(API 1)에서 불러와 선택한다. API 호출 도구의 caseId와 링크가 함께 바뀐다.
function setBaseCase(caseId) {
  $('#caseId').value = String(caseId);
  refreshLinks(caseId);
}

refreshLinks(Number($('#caseId').value));
$('#baseCase').addEventListener('change', () => setBaseCase(Number($('#baseCase').value)));
$('#caseId').addEventListener('input', () => {
  const caseId = Number($('#caseId').value);
  if (Number.isSafeInteger(caseId) && caseId > 0) refreshLinks(caseId);
});

// 화면이 받는 것과 같은 api 객체. 에러 이동 없이 원본을 직접 불러 응답 · 에러 본문을 그대로 본다.
const api = await loadApi();

async function loadCases() {
  $('#casesMsg').textContent = '불러오는 중…';
  try {
    const data = await api.getCases();
    $('#baseCase').replaceChildren(...data.cases.map((item) => {
      const option = document.createElement('option');
      option.value = String(item.caseId);
      option.textContent = item.caseId + ' · ' + item.title;
      return option;
    }));
    $('#casesMsg').textContent = '사건 ' + data.cases.length + '건';
    if (data.cases.length > 0) setBaseCase(data.cases[0].caseId);
  } catch (error) {
    const reason = error?.code === 'NETWORK_ERROR' ? '서버에 연결할 수 없어요. 백엔드가 실행 중인지 확인하세요.' : (error?.code ?? '알 수 없는 오류');
    $('#casesMsg').textContent = '사건 목록을 불러오지 못했어요: ' + reason;
  }
}
$('#reloadCases').addEventListener('click', loadCases);
loadCases();

const hints = {
  getCases: '추가 인자: 범죄 유형 (예: "MURDER"). 비워도 돼요. caseId는 쓰지 않아요.',
  startExperience: '체험을 시작해요. 이미 있으면 기존 체험의 현재 상태(status)를 돌려줘요. 실제 모드에서는 익명 ID 쿠키가 내려와요.',
  getOverview: '사건 개요 · 사전 판단 선택지. 체험 상태가 STARTED일 때만 열려요.',
  postPreJudgment: '추가 인자: {"rangeOptionId": 형량 구간 ID}. 개요 응답의 rangeOptions에서 고르세요.',
  getReview: '사건 정보. 사전 판단 이후에만 열려요.',
  postReviewStep: '추가 인자: 확인한 섹션 번호 (2 ~ 4). 예: 2',
  getVerdictForm: '판결 입력 정보. 사건 정보를 모두 확인한 뒤에만 열려요.',
  postVerdict: '추가 인자: 판결 본문 JSON. 예: {"penaltyType":"PRISON","prisonMonths":120,"factors":[]}',
  getAiJudgment: 'AI 판결. 판결 확정 이후.',
  postCourtReveal: '실제 판결 공개.',
  getCourtJudgment: '실제 판결. AI 판결 확인 이후.',
  postComparisonReveal: '비교 공개.',
  getComparison: '세 판결 비교. 비교 공개 이후.',
};
const mockOnly = new Set(['resetMock', 'setMockStatus']);
const names = Object.keys(api).filter((name) => typeof api[name] === 'function' && !mockOnly.has(name));
for (const name of names) {
  const option = document.createElement('option');
  option.value = name;
  option.textContent = name;
  $('#fn').append(option);
}
const showHint = () => { $('#hint').textContent = hints[$('#fn').value] ?? ''; };
$('#fn').addEventListener('change', showHint);
showHint();

$('#run').addEventListener('click', async () => {
  const name = $('#fn').value;
  const extraText = $('#extra').value.trim();
  const caseId = Number($('#caseId').value);
  let extra;
  try {
    extra = extraText === '' ? undefined : JSON.parse(extraText);
  } catch {
    $('#result').textContent = '추가 인자가 올바른 JSON이 아니에요.';
    return;
  }
  const args = name === 'getCases' ? [extra] : (extra === undefined ? [caseId] : [caseId, extra]);
  $('#result').textContent = '요청 중…';
  try {
    const data = await api[name](...args);
    $('#result').textContent = JSON.stringify({ ok: true, data }, null, 2);
  } catch (error) {
    $('#result').textContent = JSON.stringify({ ok: false, error }, null, 2);
  }
});

// 목 전용 도구 — 실제 모드에는 없다
if (typeof api.setMockStatus === 'function') {
  for (const status of Object.keys(screenByStatus)) {
    const option = document.createElement('option');
    option.value = status;
    option.textContent = status;
    $('#status').append(option);
  }
  const message = (text) => { $('#mockMsg').textContent = text; };
  $('#applyStatus').addEventListener('click', async () => {
    await api.setMockStatus($('#status').value);
    message('상태를 ' + $('#status').value + '(으)로 바꿨어요.');
  });
  $('#resetMock').addEventListener('click', async () => {
    await api.resetMock();
    message('처음 상태(PRE_JUDGED)로 되돌렸어요.');
  });
} else {
  $('#mockTools').hidden = true;
  $('#realNote').hidden = false;
}
