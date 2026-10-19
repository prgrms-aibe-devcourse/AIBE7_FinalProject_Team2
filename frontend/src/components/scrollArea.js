// 겹치는 스크롤바: 기본 스크롤바를 숨기고, 글 위에 겹치는 반투명 막대를 스크롤하는 동안만 보여 준다.
// 스크롤 자체(휠 · 터치 · 키보드)는 브라우저 기본 동작 그대로라 막대는 위치 표시만 한다. 스타일은 style.css의 .scroll-area
// 기본 막대가 숨어 있으니 키보드로도 스크롤할 수 있게 본문이 포커스를 받는다(Safari는 스크롤 영역에 포커스를 주지 않는다). label은 읽기 도구가 읽는 이름
export function scrollArea(content, label = '내용') {
  const area = document.createElement('div');
  area.className = 'scroll-area';
  const body = document.createElement('div');
  body.className = 'scroll-body';
  body.tabIndex = 0;
  body.setAttribute('role', 'region');
  body.setAttribute('aria-label', label);
  const thumb = document.createElement('div');
  thumb.className = 'scroll-thumb';
  thumb.setAttribute('aria-hidden', 'true');
  body.append(content);
  area.append(body, thumb);

  let hideTimer = null;
  body.addEventListener('scroll', () => {
    const { scrollTop, scrollHeight, clientHeight } = body;
    if (scrollHeight <= clientHeight) return;
    const size = Math.max(24, (clientHeight / scrollHeight) * clientHeight);
    thumb.style.height = `${size}px`;
    thumb.style.transform = `translateY(${(scrollTop / (scrollHeight - clientHeight)) * (clientHeight - size)}px)`;
    area.classList.add('scrolling');
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => area.classList.remove('scrolling'), 700);
  }, { passive: true });
  return area;
}
