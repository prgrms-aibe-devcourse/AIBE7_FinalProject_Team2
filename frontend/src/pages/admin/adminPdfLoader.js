// 브라우저용 pdf.js 불러오기. 사건 등록 화면에서 PDF를 고를 때 처음 불러온다(관리자 청크에도 미리 넣지 않는다).
// 옛 브라우저(사파리 · 오래된 크롬)에서도 돌도록 pdf.js의 legacy 빌드를 쓴다. 워커 파일은 vite가 따로 내보내는 주소(?url)로 가리킨다. node 테스트는 이 파일을 쓰지 않는다.
import { extractPdfText } from './adminPdf.js';

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

/** PDF 파일(File) → { text, pages }. 실패하면 PdfReadError */
export async function readPdfFile(file) {
  const [pdfjs, buffer] = await Promise.all([loadPdfjs(), file.arrayBuffer()]);
  return extractPdfText(buffer, pdfjs);
}
