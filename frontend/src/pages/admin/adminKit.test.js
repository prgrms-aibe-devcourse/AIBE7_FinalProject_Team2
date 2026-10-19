import test from 'node:test';
import assert from 'node:assert/strict';

// element()가 document.createElement만 쓰므로 작은 대역으로 충분하다
const fake = (tag) => ({
  tag, hidden: false, children: [], attrs: {},
  setAttribute(key, value) { this.attrs[key] = value; },
  getAttribute(key) { return this.attrs[key] ?? null; },
  replaceChildren(...children) { this.children = children; },
  append(...children) { this.children.push(...children); },
});
globalThis.document = { createElement: fake };
const { detailPanel, labeledField } = await import('./adminKit.js');

test('detailPanel: 나중에 연 상세가 이긴다', async () => {
  let release;
  const slow = new Promise((resolve) => { release = resolve; });
  const drawn = [];
  const panel = detailPanel({ fetcher: (id) => (id === 1 ? slow : Promise.resolve({ id })), draw: (data) => drawn.push(data.id), failText: '', active: () => true, unauthorized() {} });
  const first = panel.show(1);
  await panel.show(2);
  release({ id: 1 });
  await first;
  assert.deepEqual(drawn, [2]);
});

test('labeledField: hint를 입력칸 설명으로 잇고, 이미 있던 설명은 지우지 않는다', () => {
  const input = document.createElement('input');
  input.setAttribute('aria-describedby', 'existing-error');
  labeledField('키워드', input, '쉼표로 나눠요');
  assert.match(input.getAttribute('aria-describedby'), /^existing-error admin-hint-\d+$/);
});
