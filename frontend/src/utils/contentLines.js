// FE-14: 섹션 본문(API 6 content)을 화면 요소로 바꾼다.
// 본문에 줄바꿈(\n)이 있으면 줄마다 점 목록(ul > li)으로, 없으면 지금처럼 문단(p) 하나로 그린다.
// 줄 앞에 이미 붙은 목록 기호(- · • *)는 점이 두 번 보이지 않도록 뗀다. 빈 줄은 건너뛴다.
// textContent로 넣으므로 HTML이 해석되지 않는다(XSS 걱정 없음).
const LEADING_MARK = /^\s*[-•·*]\s+/;

export function renderContent(text) {
  const lines = String(text).split('\n').map((line) => line.replace(LEADING_MARK, '').trim()).filter(Boolean);
  if (lines.length <= 1) {
    const paragraph = document.createElement('p');
    paragraph.textContent = lines[0] ?? '';
    return paragraph;
  }
  const list = document.createElement('ul');
  list.className = 'content-lines';
  for (const line of lines) {
    const item = document.createElement('li');
    item.textContent = line;
    list.append(item);
  }
  return list;
}
