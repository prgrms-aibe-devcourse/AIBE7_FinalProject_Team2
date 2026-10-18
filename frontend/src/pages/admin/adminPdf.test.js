import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import * as pdfjs from 'pdfjs-dist/legacy/build/pdf.mjs';
import { PdfReadError, extractPdfText, joinItems } from './adminPdf.js';
import { parseJudgmentText } from './adminJudgmentParse.js';

const sample = () => readFileSync(new URL('./samples/sample-judgment.pdf', import.meta.url));
const errorCode = (promise) => promise.then(() => null, (failure) => (failure instanceof PdfReadError ? failure.code : failure));

test('예시 PDF에서 글자를 뽑는다(여러 쪽)', async () => {
  const { text, pages } = await extractPdfText(sample(), pdfjs);
  assert.equal(pages, 2);
  assert.match(text, /가상지방법원/);
  assert.match(text, /징역 12년에 처한다/);
  assert.match(text, /양형의 이유/); // 2쪽 내용
  assert.ok(text.split('\n').length > 15, '줄바꿈이 살아 있어야 한다');
});

test('예시 PDF에서 사건번호 · 법원명 · 선고일 · 범죄 유형을 읽는다', async () => {
  const { text } = await extractPdfText(sample(), pdfjs);
  assert.deepEqual(parseJudgmentText(text), {
    caseNumber: '2099고합000', crimeType: 'MURDER', courtName: '가상지방법원', decidedAt: '2025-01-10', courtLevel: 'FIRST',
  });
});

test('PDF가 아니거나 너무 크면 거절한다', async () => {
  assert.equal(await errorCode(extractPdfText(new TextEncoder().encode('hello'), pdfjs)), 'NOT_PDF');
  const big = new Uint8Array(20 * 1024 * 1024 + 1);
  assert.equal(await errorCode(extractPdfText(big, pdfjs)), 'TOO_BIG');
});

test('헤더만 맞고 내용이 깨진 PDF는 BROKEN', async () => {
  assert.equal(await errorCode(extractPdfText(new TextEncoder().encode('%PDF-1.4\nnot really'), pdfjs)), 'BROKEN');
});

test('joinItems: 세로 위치가 바뀌면 줄바꿈, 같으면 이어 붙인다', () => {
  const items = [
    { str: '사 건', transform: [1, 0, 0, 1, 0, 700] }, { str: ' 2099고합000', transform: [1, 0, 0, 1, 40, 700] },
    { str: '피 고 인', transform: [1, 0, 0, 1, 0, 680] },
  ];
  assert.equal(joinItems(items), '사 건 2099고합000\n피 고 인');
});

test('parseJudgmentText: 심급은 사건부호와 법원명으로 정한다', () => {
  assert.equal(parseJudgmentText('서울고등법원 제3형사부 판결\n사건 2025노123 살인').courtLevel, 'APPEAL');
  assert.equal(parseJudgmentText('대법원 제1부 판결\n사건 2025도456 사기').courtLevel, 'SUPREME');
  assert.equal(parseJudgmentText('서울중앙지방법원 판결\n사건 2025고합789 상해').courtLevel, 'FIRST');
  assert.equal(parseJudgmentText('서울중앙지방법원 판결\n사건 2025고합789 상해').crimeType, 'INJURY');
});

test('parseJudgmentText: 날짜 글을 사건번호로 읽지 않고, 못 찾은 항목은 키를 만들지 않는다', () => {
  assert.deepEqual(parseJudgmentText('2024년 3월 2일에 있었던 일을 적는다'), {});
  assert.deepEqual(parseJudgmentText(''), {});
  assert.deepEqual(parseJudgmentText(null), {});
  assert.equal(parseJudgmentText('선 고 2025년 3월 14일').decidedAt, '2025-03-14');
  assert.equal(parseJudgmentText('선고 2025. 13. 40.').decidedAt, undefined);
});

test('parseJudgmentText: 글자를 띄운 첫머리와 날짜가 앞에 오는 서식도 읽는다', () => {
  const text = '서 울 중 앙 지 방 법 원\n제 2 7 형 사 부\n판 결\n사 건 2023고합123 살인\n피 고 인 김 모 씨\n2025. 3. 14. 선고';
  assert.deepEqual(parseJudgmentText(text), { caseNumber: '2023고합123', crimeType: 'MURDER', courtName: '서울중앙지방법원', decidedAt: '2025-03-14', courtLevel: 'FIRST' });
});
