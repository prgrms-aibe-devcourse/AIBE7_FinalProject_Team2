// PDF에서 글자를 뽑는다(스캔한 이미지 PDF의 글자 인식은 하지 않는다).
// pdf.js는 글자 조각(item)을 좌표와 함께 준다. 같은 줄(세로 위치가 거의 같음)은 이어 붙이고 줄이 바뀌면 줄바꿈을 넣는다.
// pdfjs 모듈은 밖에서 받는다 — 브라우저는 adminPdfLoader.js의 번들용 모듈을, node 테스트는 legacy 빌드를 넘긴다.

export const MAX_PDF_BYTES = 20 * 1024 * 1024;
export const MAX_PDF_PAGES = 200;

export class PdfReadError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code; // NOT_PDF | TOO_BIG | TOO_MANY_PAGES | NO_TEXT | LOCKED | BROKEN
  }
}

const LINE_TOLERANCE = 2; // 같은 줄로 볼 세로 위치 차이(PDF 단위)

/** 한 쪽의 글자 조각들 → 줄바꿈이 들어간 글 */
export function joinItems(items) {
  let out = '';
  let lastY = null;
  for (const item of items) {
    if (typeof item.str !== 'string') continue;
    const y = item.transform?.[5];
    if (out && lastY !== null && y !== undefined && Math.abs(y - lastY) > LINE_TOLERANCE) out += '\n';
    out += item.str;
    if (item.hasEOL) out += '\n';
    lastY = y ?? lastY;
  }
  return out.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
}

/**
 * @param {ArrayBuffer | Uint8Array} data PDF 파일 내용
 * @param {{ getDocument: Function }} pdfjs pdf.js 모듈
 * @returns {Promise<{ text: string, pages: number }>}
 */
export async function extractPdfText(data, pdfjs) {
  const bytes = new Uint8Array(data); // 복사본: pdf.js가 넘긴 버퍼를 가져가 버리고(전송), node Buffer는 받지 않는다
  if (bytes.byteLength > MAX_PDF_BYTES) throw new PdfReadError('TOO_BIG', 'PDF가 너무 커요. 20MB 이하만 읽을 수 있어요.');
  if (String.fromCharCode(...bytes.subarray(0, 5)) !== '%PDF-') throw new PdfReadError('NOT_PDF', 'PDF 파일이 아니에요.');

  const task = pdfjs.getDocument({ data: bytes, useSystemFonts: false, isEvalSupported: false });
  let doc;
  try {
    doc = await task.promise;
  } catch (failure) {
    task.destroy();
    if (failure?.name === 'PasswordException') throw new PdfReadError('LOCKED', '비밀번호가 걸린 PDF는 읽을 수 없어요.');
    throw new PdfReadError('BROKEN', 'PDF를 열지 못했어요. 파일이 깨졌을 수 있어요.');
  }
  try {
    if (doc.numPages > MAX_PDF_PAGES) throw new PdfReadError('TOO_MANY_PAGES', `쪽수가 너무 많아요. ${MAX_PDF_PAGES}쪽 이하만 읽을 수 있어요.`);
    const pages = [];
    for (let number = 1; number <= doc.numPages; number += 1) {
      const page = await doc.getPage(number);
      const content = await page.getTextContent();
      pages.push(joinItems(content.items));
      page.cleanup();
    }
    const text = pages.filter(Boolean).join('\n\n');
    if (!text.trim()) throw new PdfReadError('NO_TEXT', '글자를 읽지 못했어요. 스캔한 이미지 PDF는 지원하지 않아요. 텍스트 전문이나 직접 입력을 써 주세요.');
    return { text, pages: doc.numPages };
  } finally {
    task.destroy();
  }
}
