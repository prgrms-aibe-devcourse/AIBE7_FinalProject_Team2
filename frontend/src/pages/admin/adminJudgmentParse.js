// 판결문 원문 텍스트에서 사건 등록 폼의 항목(사건번호 · 법원명 · 선고일 · 심급 · 범죄 유형)을 읽어 낸다.
// PDF에서 뽑은 글이든 붙여넣은 전문이든 같은 규칙을 쓴다. DOM을 쓰지 않는 순수 함수라 node 테스트로 확인한다.
// 못 찾은 항목은 키를 아예 돌려주지 않는다 — 폼은 읽어 낸 항목만 채우고, 나머지는 사람이 입력한다.

// 사건부호. "2024년 3" 같은 날짜 글을 사건번호로 잘못 읽지 않도록 목록에 있는 부호만 인정한다.
// 형사 부호(고합 · 고단 · 노 · 도 등)가 중심이다. 민사 · 특허(다 · 두)와 행정(구합 · 누)은 이후 사건 유형을 넓힐 때를 염두에 두고 일부러 넣었다(PR #180 리뷰, 팀 결정).
// 주의: 지금은 형사 사건만 다루므로, 판결문 앞에 관련 민사 사건번호가 먼저 나오면 사건번호 · 심급이 그쪽으로 잘못 채워질 수 있다 — 자동 채움은 사람이 확인하는 전제다.
const CASE_CODE = '감고합|재고합|고합|고단|고정|고약|감노|재노|노|감도|재도|도|초기|구합|누|다|두';
const CASE_NUMBER = new RegExp(`(\\d{4})\\s*(${CASE_CODE})\\s*(\\d{1,6})`);
const COURT_NAME = /([가-힣]{2,10}(?:지방법원|고등법원|가정법원)(?:\s*[가-힣]{2,6}지원)?|대법원)/;
const DECIDED_AT = /선\s*고(?:\s*일)?\s*[:：]?\s*(\d{4})\s*[.년\-/]\s*(\d{1,2})\s*[.월\-/]\s*(\d{1,2})/;
// "2025. 1. 10. 선고" 처럼 날짜가 앞에 오는 서식
const DECIDED_AT_FIRST = /(\d{4})\s*[.년\-/]\s*(\d{1,2})\s*[.월\-/]\s*(\d{1,2})\s*일?\s*\.?\s*선\s*고/;
// 판결문 첫머리는 "서 울 중 앙 지 방 법 원"처럼 글자 사이를 띄운 경우가 많다. 한 글자씩 띄운 3자 이상 낱말은 붙여서 읽는다
const SPACED_WORD = /(?:[가-힣] ){2,}[가-힣]/g;
const joinSpaced = (text) => text.replace(SPACED_WORD, (word) => word.replace(/ /g, ''));
// 알려진 한계: 범죄 유형은 사건번호 뒤 40자 안에 살인 · 강도 · 절도 · 사기 · 상해 · 폭행치사가 들어 있는지로만 정한다.
// 그래서 "상해치사" · "폭행치사"는 INJURY로 읽히고, 여러 낱말이 함께 있으면 이 목록의 앞쪽이 이긴다. 틀리면 사람이 폼에서 고친다
const CRIME_KEYWORD = [['살인', 'MURDER'], ['강도', 'ROBBERY'], ['절도', 'THEFT'], ['사기', 'FRAUD'], ['상해', 'INJURY'], ['폭행치사', 'INJURY']];

const SUPREME_CODES = new Set(['도', '감도', '재도', '다', '두']);
const APPEAL_CODES = new Set(['노', '감노', '재노', '누']);

function courtLevelOf(code, courtName) {
  if (SUPREME_CODES.has(code) || courtName === '대법원') return 'SUPREME';
  if (APPEAL_CODES.has(code) || courtName?.includes('고등법원')) return 'APPEAL';
  return 'FIRST';
}

const pad = (value) => String(value).padStart(2, '0');

/**
 * @param {string} text 판결문 원문
 * @returns {{ caseNumber?: string, courtName?: string, decidedAt?: string, courtLevel?: 'FIRST'|'APPEAL'|'SUPREME', crimeType?: 'MURDER'|'FRAUD'|'INJURY'|'ROBBERY'|'THEFT' }}
 */
export function parseJudgmentText(text) {
  const found = {};
  if (typeof text !== 'string' || !text.trim()) return found;
  // 첫머리에 사건 표시가 몰려 있다. 본문 깊은 곳의 다른 사건 인용(예: 원심 판결)을 잘못 읽지 않게 앞부분만 본다
  const head = joinSpaced(text.slice(0, 3000));

  const number = CASE_NUMBER.exec(head);
  if (number) {
    found.caseNumber = `${number[1]}${number[2]}${number[3]}`;
    const afterNumber = head.slice(number.index + number[0].length, number.index + number[0].length + 40);
    const crime = CRIME_KEYWORD.find(([word]) => afterNumber.includes(word));
    if (crime) found.crimeType = crime[1];
  }

  const court = COURT_NAME.exec(head);
  if (court) found.courtName = court[1].replace(/\s+/g, ' ').trim();

  const decided = DECIDED_AT.exec(head) ?? DECIDED_AT_FIRST.exec(head);
  if (decided) {
    const [year, month, day] = [Number(decided[1]), Number(decided[2]), Number(decided[3])];
    // 2월 30일 같은 없는 날짜는 Date가 다음 달로 넘겨 버리므로, 다시 조합한 년 · 월 · 일이 그대로인지 확인한다
    const date = new Date(Date.UTC(year, month - 1, day));
    const exists = date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
    if (exists) found.decidedAt = `${year}-${pad(month)}-${pad(day)}`;
  }

  if (found.caseNumber || found.courtName) found.courtLevel = courtLevelOf(number?.[2], found.courtName);
  return found;
}
