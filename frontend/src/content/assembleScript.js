// 공개 대본(API 18)의 블록 + 메타 + 공통 틀 → 무대가 쓰는 대본(FE-139). 고정 문구 · 연출은 프론트 몫이고 서버 블록에는 대사만 있다.
// 민감 안내는 무대(attachNotice), 기사는 API 17, 4 ~ 6막의 판결 값은 각 화면이 채운다. 틀의 원형: tools/ai-judgment/script_assembler.py
import frame from './scriptFrame.json' with { type: 'json' };
import { castOf } from '../components/stage.js';

const LAST_QUESTION = {
  admitted: '공소사실을 인정합니까?',
  denied: '공소사실을 인정합니까?',
  mental_state: '공소사실을 인정합니까?',
  intent_disputed: '행위 사실을 인정합니까?',
  defense_claim: '행위 사실을 인정합니까?',
};
const SLOT = { A: 10, B: 50, stand: 30 };

const courtSlots = ({ prosecutor, defense }) => ({
  [prosecutor]: { slot: SLOT.A, row: 'front' },
  [defense]: { slot: SLOT.B, row: 'front' },
});

// 1막은 법정 밖이라 안내 배역(내로 · 남불) 순서가 사건마다 같다
function introAct(lines) {
  const steps = [
    { say: 'nambul', look: { nambul: 'guide-neutral' }, text: lines[0], focus: 'head' },
    { say: 'naero', text: '그럼, 좀 더 자세히 살펴볼까요?', focus: 'photo' },
    { say: 'nambul', text: lines[1], focus: 'body' },
    { say: 'naero', text: lines[2], focus: 'body' },
    { say: 'nambul', text: lines[3], focus: 'body' },
    { say: 'naero', look: { naero: 'react-sorry', nambul: 'idle-neutral' }, text: '여기까지가 기사에 나온 내용이네요.', focus: 'body' },
    { say: 'nambul', look: { nambul: 'react-sorry' }, text: lines[4], focus: 'body' },
  ];
  return {
    title: '1막 첫인상 (기사)', scene: 'sheet',
    look: { naero: 'guide-neutral', nambul: 'idle-neutral' },
    slots: { naero: { slot: 5, row: 'front' }, nambul: { slot: 55, row: 'front' } },
    steps,
  };
}

function trialAct(cast, { U2, U3 }, meta) {
  const { prosecutor: A, defense: B } = cast;
  const evidence = U3.map((line, i) => ({
    say: line.role === 'prosecutor' ? A : B,
    role: line.role === 'prosecutor' ? '검사' : '변호인',
    card: 'evidence', ev: i, text: line.text,
    ...(i === 0 ? { proc: 3 } : {}),
    ...(i === U3.length - 1 ? { confirm: 2 } : {}),
  }));
  const steps = [
    { say: 'narrator', cameo: A, hideCast: true, delay: 500, proc: 0, section: 2, text: '지금부터 재판을 시작할게요. 이번 재판의 재판장은 여러분이에요.' },
    { say: A, role: '검사', text: '검사 측, 준비됐습니다.' },
    { say: B, role: '변호인', text: '변호인 측도 준비됐습니다.' },
    { say: A, role: '검사', proc: 1, card: 'indict', text: U2.indict[0] },
    { say: A, role: '검사', card: 'indict', text: U2.indict[1] },
    { say: B, role: '변호인', card: 'plea', text: U2.plea[0] },
    { say: B, role: '변호인', card: 'plea', text: U2.plea[1] },
    { say: 'narrator', cameo: A, proc: 2, card: 'issues', text: U2.issues_narr[0] },
    { say: 'narrator', cameo: A, card: 'issues', text: U2.issues_narr[1] },
    ...evidence,
    {
      say: 'narrator', cameo: B, proc: 4, section: 3,
      look: { [A]: 'role:prosecutor', [B]: 'role:actor' }, tags: { [B]: 'A씨 역' }, spots: { [B]: 'stand' }, slots: { [B]: { slot: SLOT.stand, row: 'front' } },
      text: '이제 피고인신문 차례예요. 먼저 검사 측의 질문을 들어볼게요.',
    },
    // 답은 서버가 정규화해 준다(API 18)
    ...U2.questions.slice(0, 2).flatMap(({ q, a }) => [{ say: A, role: '검사', text: q }, { say: B, role: 'A씨 역', text: a }]),
    { say: A, role: '검사', text: LAST_QUESTION[meta.issue] },
    { say: B, role: 'A씨 역', confirm: 3, text: meta.admits ? '…네, 인정합니다.' : '…아닙니다.' },
    { say: A, role: '검사', section: 4, text: '검사 측 질문은 여기까지입니다.' },
    { say: B, role: '변호인', look: { [B]: 'role:defense' }, tags: { [B]: '' }, spots: { [B]: '' }, slots: { [B]: { slot: SLOT.B, row: 'front' } }, text: '변호인 측은 질문 없습니다.' },
    { say: 'narrator', cameo: A, hideCast: true, confirm: 4, next: '의견 듣기', text: '피고인신문이 끝났어요. 이제 검사 측의 의견과 변호인의 최종변론을 들어볼게요.' },
  ];
  return {
    title: '2막 재판', scene: 'court', procedures: ['인정신문', '모두진술', '쟁점 정리', '증거조사', '피고인신문'],
    look: { [A]: 'role:prosecutor', [B]: 'role:defense' }, slots: courtSlots(cast), steps,
  };
}

function verdictAct(cast, U4) {
  const { prosecutor: A, defense: B } = cast;
  return {
    title: '3막 내 판결', scene: 'court', procedures: ['검사 의견', '최종변론', '판결'],
    look: { [A]: 'role:prosecutor', [B]: 'role:defense' }, slots: courtSlots(cast),
    steps: [
      { say: A, role: '검사', proc: 0, text: U4.prosecutor },
      { say: B, role: '변호인', proc: 1, text: U4.defense },
      { say: A, role: '검사', proc: 2, text: '이제 모든 진술이 끝났습니다. 재판장님, 판결을 내려 주십시오.' },
      { proc: 2, card: 'verdict', look: { [A]: 'read-thinking' }, board: 'full' },
    ],
  };
}

// 서버가 준 카드 값은 모양을 믿지 않는다 — 문자열 배열이 아닌 ul · 문자열이 아닌 문구는 비운다(무대가 forEach로 돌 때 멈추지 않게)
const texts = (list) => (Array.isArray(list) ? list.filter((item) => typeof item === 'string' && item) : []);
const text = (value) => (typeof value === 'string' && value ? value : '');

function cardsOf({ indict, plea, issues }) {
  return {
    indict: { k: '공소사실 · 검사', ul: texts(indict?.ul), p: text(indict?.law) ? [{ b: '적용 법조', t: indict.law }] : [] },
    plea: { k: '피고인 측 모두진술 · 변호인', p: text(plea?.b) ? [{ b: plea.b }] : [], ul: texts(plea?.ul) },
    issues: { k: '쟁점 정리', p: text(issues?.p) ? [issues.p] : [], ul: texts(issues?.ul) },
  };
}

// 틀의 법정 막(4 · 5막)은 기본 배역(내로 A · 남불 B)으로 적혀 있다. 배역이 반대인 사건은 두 이름을 맞바꾼다
const forCast = (act, { prosecutor }) => (prosecutor === 'naero' ? act
  : JSON.parse(JSON.stringify(act).replace(/naero|nambul/g, (who) => (who === 'naero' ? 'nambul' : 'naero'))));

const hasLines = (list, count) => Array.isArray(list) && list.length >= count && list.slice(0, count).every(Boolean);
const playable = ({ blocks, meta }) => {
  const { U1, U2, U3, U4 } = blocks ?? {};
  return hasLines(U1?.lines, 5) && hasLines(U2?.indict, 2) && hasLines(U2?.plea, 2) && hasLines(U2?.issues_narr, 2)
    && hasLines(U2?.questions, 2) && U2.questions.slice(0, 2).every(({ q, a }) => q && a)
    && Array.isArray(U3) && U3.length > 0 && U3.every((line) => line?.text && ['prosecutor', 'defense'].includes(line.role))
    && U4?.prosecutor && U4?.defense && LAST_QUESTION[meta?.issue] && typeof meta?.admits === 'boolean';
};

/** API 18 응답 → 대본. 조립할 수 없으면(빠진 단위 · 마지막 질문이나 답을 고를 수 없음) null — 화면은 "준비 중"으로 다룬다 */
export function assembleScript(response) {
  if (!playable(response ?? {})) return null;
  const { blocks, meta, evidence = [] } = response;
  const cast = castOf(meta.cast ?? response.cast);
  const labels = new Map(evidence.map(({ factorId, card }) => [factorId, card]));
  return {
    cast,
    cards: cardsOf(blocks.U2.cards ?? {}),
    evidence: blocks.U3.map(({ factorId, role, text }) => ({ side: role === 'prosecutor' ? '검사' : '변호인', text: labels.get(factorId) ?? text })),
    acts: {
      '1a': introAct(blocks.U1.lines),
      '1b': frame['1b'],
      2: trialAct(cast, blocks, meta),
      3: verdictAct(cast, blocks.U4),
      4: forCast(frame['4'], cast),
      5: forCast(frame['5'], cast),
      6: frame['6'],
    },
  };
}
