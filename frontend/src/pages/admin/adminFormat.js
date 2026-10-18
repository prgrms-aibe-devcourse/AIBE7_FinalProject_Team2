// S-19 검수 화면이 파이프라인 산출물(A-6 summaryTagCandidates, A-8 generation)을 읽기 쉽게 풀어 쓰는 순수 함수.
// DOM을 쓰지 않아 node 테스트로 확인할 수 있다. 값 모양은 tools/ai-judgment(summary_tags.py, pipeline.py build_generation_report) 기준이다.
// 사람이 검수해 넣은 행은 generation 자체가 null이고, 점검을 켜지 않은 단계는 키가 없거나 SKIPPED라 모든 값을 없을 수 있다고 본다.
import { CONTAMINATION_VERDICT, SELECT_STRATEGY } from './adminLabels.js';

const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const filled = (value) => value !== null && value !== undefined && value !== '';

/** 객체 · 배열은 JSON 한 줄로, 나머지는 문자열로 */
export function plain(value) {
  if (!filled(value)) return null;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

const score = (value) => (typeof value === 'number' ? value.toFixed(4) : plain(value));

/**
 * A-6 factors[].summaryTagCandidates → 화면용 요약. 후보 계산을 켜지 않은 요소는 null.
 * 제안일 뿐 요약어를 바꾸지 않는다: suggested가 있으면 관리자가 요약어를 그 값으로 맞출지 판단한다.
 * @returns {null | { state: 'IN_VOCABULARY' | 'AMBIGUOUS' | 'SUGGESTED' | 'NONE', suggested: string | null, candidates: { tag: string, score: string }[] }}
 */
export function describeSummaryTagCandidates(raw) {
  if (!isObject(raw)) return null;
  const candidates = (Array.isArray(raw.candidates) ? raw.candidates : [])
    .filter((item) => isObject(item) && filled(item.tag))
    .map((item) => ({ tag: String(item.tag), score: score(item.score) }));
  let state = 'NONE';
  if (raw.inVocabulary) state = 'IN_VOCABULARY';
  else if (raw.ambiguous) state = 'AMBIGUOUS';
  else if (filled(raw.suggested)) state = 'SUGGESTED';
  return { state, suggested: filled(raw.suggested) ? String(raw.suggested) : null, candidates };
}

export const SUMMARY_TAG_STATE_TEXT = {
  IN_VOCABULARY: '공개 기준 목록에 있는 요약어예요.',
  AMBIGUOUS: '비슷한 기준 요약어가 둘 이상 붙어 있어 제안하지 않았어요.',
  SUGGESTED: '기준 목록의 비슷한 요약어를 제안해요. 바꿀지는 직접 판단해 주세요.',
  NONE: '비슷한 기준 요약어가 없어요.',
};

function countsText(counts) {
  if (!isObject(counts)) return null;
  const parts = Object.entries(counts).map(([key, count]) => `${key} ${count}`);
  return parts.length ? parts.join(' · ') : null;
}

function criteriaText(criteria) {
  if (!isObject(criteria)) return null;
  const parts = Object.entries(criteria).map(([key, value]) => `${key} ${plain(value)}`);
  return parts.length ? parts.join(' · ') : null;
}

function ragRows(rag) {
  if (!isObject(rag) || !filled(rag.status)) return { status: 'SKIPPED', rows: [] };
  const precedents = Array.isArray(rag.precedents) ? rag.precedents : [];
  const rows = [
    ['검색 상태', rag.status],
    ['대체 사유', (Array.isArray(rag.fallbackReasons) ? rag.fallbackReasons : []).map(plain).join(', ')],
    ['임베딩 모델', plain(rag.embeddingModel)],
    ['양형기준', plain(rag.guideline)],
    ['기준 종류', plain(rag.guidelineBasis)],
    ['참고 판례', precedents.length ? precedents.map((item) => (isObject(item) ? `#${item.id} (${score(item.score)})` : plain(item))).join(', ') : null],
    ['제외한 의심 판례', filled(rag.excludedSuspects) ? `${rag.excludedSuspects}건` : null],
    ['검색 경고', (Array.isArray(rag.warnings) ? rag.warnings : []).map(plain).join(' / ')],
  ];
  return { status: String(rag.status), rows };
}

/**
 * A-8 generation → 화면용 요약. AI 후보가 아니면 generation이 null이라 null을 돌려 준다.
 * 검증 경고는 pipeline의 validationWarnings(없으면 옛 이름 warnings)를 읽는다.
 */
export function describeGeneration(generation) {
  if (!isObject(generation)) return null;
  const report = isObject(generation.generationReport) ? generation.generationReport : {};
  const snapshot = isObject(generation.inputSnapshot) ? generation.inputSnapshot : {};

  const otherSnapshot = Object.fromEntries(Object.entries(snapshot).filter(([key]) => !['promptVersion', 'system', 'user'].includes(key)));
  const selectionRaw =isObject(report.selection) ? report.selection : null;
  const contaminationRaw = isObject(report.contamination) ? report.contamination : null;
  const verdict = contaminationRaw?.verdict ?? null;
  const warnings = (Array.isArray(report.validationWarnings) ? report.validationWarnings : Array.isArray(report.warnings) ? report.warnings : []).map(plain).filter(Boolean);

  return {
    model: [
      ['모델 이름', plain(generation.modelName)],
      ['요청 모델', plain(report.modelSpec)],
      ['실제 응답 모델', plain(report.servedModel)],
      ['프롬프트 버전', plain(generation.promptVersion)],
      ['생성 회차', filled(report.runIndex) ? `${report.runIndex}회차` : null],
      ['생성 시각', plain(report.generatedAt)],
    ],
    selection: selectionRaw
      ? [
        ['선택 방식', SELECT_STRATEGY[selectionRaw.strategy] ?? plain(selectionRaw.strategy)],
        ['선택 이유', plain(selectionRaw.reason)],
        ['점수', filled(selectionRaw.score) ? score(selectionRaw.score) : null],
      ]
      : null,
    contamination: {
      verdict,
      label: verdict ? CONTAMINATION_VERDICT[verdict] ?? String(verdict) : '점검 기록 없음',
      // CONTAMINATED · SUSPECT는 눈에 띄게 표시한다. SKIPPED · 기록 없음은 문제가 아니라 "점검하지 않음"이다
      level: verdict === 'CONTAMINATED' ? 'danger' : verdict === 'SUSPECT' || verdict === 'INSUFFICIENT' ? 'warn' : verdict === 'CLEAN' ? 'ok' : 'none',
      rows: contaminationRaw
        ? [['판정 근거', plain(contaminationRaw.reason)], ['분류별 개수', countsText(contaminationRaw.counts)], ['판정 기준', criteriaText(contaminationRaw.criteria)]]
        : [],
    },
    warnings,
    rag: ragRows(report.rag),
    // inputSnapshot은 AI에 넣은 프롬프트 전체다. 길어서 접어서 보여 주고, 실제 판결 값이 섞이지 않았는지 눈으로 점검하는 용도다
    snapshot: {
      promptVersion: plain(snapshot.promptVersion),
      system: typeof snapshot.system === 'string' ? snapshot.system : null,
      user: typeof snapshot.user === 'string' ? snapshot.user : null,
      other: Object.keys(otherSnapshot).length ? otherSnapshot : null, // 위 세 키 말고 다른 입력(예: 참고 자료)이 있을 때만
    },
    usage: plain(report.usage),
    hasReport: Object.keys(report).length > 0,
  };
}
