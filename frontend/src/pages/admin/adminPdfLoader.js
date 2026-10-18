// 브라우저용 pdf.js 불러오기. 사건 등록 화면에서 PDF를 고를 때 처음 불러온다(관리자 청크에도 미리 넣지 않는다).
// 옛 브라우저(사파리 · 오래된 크롬)에서도 돌도록 pdf.js의 legacy 빌드를 쓴다. 워커 파일은 vite가 따로 내보내는 주소(?url)로 가리킨다. node 테스트는 이 파일을 쓰지 않는다.
import { extractPdfText } from './adminPdf.js';

// 사파리는 ReadableStream을 `for await`로 읽는 문법을 지원하지 않는다. pdf.js 안쪽 코드가 쓸 수 있으므로 없으면 채워 둔다
if (typeof ReadableStream !== 'undefined' && !ReadableStream.prototype[Symbol.asyncIterator]) {
  ReadableStream.prototype[Symbol.asyncIterator] = async function* streamValues() {
    const reader = this.getReader();
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) return;
        yield value;
      }
    } finally {
      reader.releaseLock();
    }
  };
}

let loaded;
async function loadPdfjs() {
  loaded ??= Promise.all([
    import('pdfjs-dist/legacy/build/pdf.min.mjs'),
    import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'),
  ]).then(([pdfjs, worker]) => {
    pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
    return pdfjs;
  });
  return loaded;
}

// 한글 글꼴을 PDF 안에 넣지 않은 문서(국내 판결문 PDF에 흔함)는 글자표(cMap)가 있어야 글자를 읽는다.
// 한글용 글자표만 public/pdfjs/cmaps에 복사해 두고 그 주소를 알려 준다(전체는 1.6MB, 한글용은 240KB)
const CMAP_URL = `${import.meta.env.BASE_URL}pdfjs/cmaps/`;

/** PDF 파일(File) → { text, pages }. 실패하면 PdfReadError */
export async function readPdfFile(file) {
  const [pdfjs, buffer] = await Promise.all([loadPdfjs(), file.arrayBuffer()]);
  return extractPdfText(buffer, pdfjs, { cMapUrl: CMAP_URL });
}
