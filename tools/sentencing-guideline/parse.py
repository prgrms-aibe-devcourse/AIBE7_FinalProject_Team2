#!/usr/bin/env python3
"""fetch.py가 받은 양형기준 페이지(raw/<범죄>/)를 버전 단위 구조 JSON · 마크다운으로 바꾼다 (BE-52).

출력(out/<범죄>/):
- guideline.json : 버전 → 페이지 → 탭 → 블록(표 · 목록 · 문단). 사람이 보기 · 바로 찾기용
- chunks.jsonl   : RAG(임베딩) 조각. 한 조각에 한 유형 × 한 섹션, 머리말(메타데이터) 포함
- md/<버전>.md   : 읽기용 마크다운

표준 라이브러리만 쓴다.
사용: python3 parse.py 살인 [--raw raw] [--out out]
"""
import argparse
import json
import re
from html.parser import HTMLParser
from pathlib import Path

VOID = {"br", "img", "col", "input", "hr", "meta", "link"}


class Node:
	def __init__(self, tag, attrs=None):
		self.tag = tag
		self.attrs = dict(attrs or {})
		self.children = []  # Node 또는 str

	def cls(self):
		return self.attrs.get("class", "").split()

	def find_all(self, tag=None, id_=None, cls=None):
		out = []
		for c in self.children:
			if isinstance(c, Node):
				if (tag is None or c.tag == tag) and (id_ is None or c.attrs.get("id") == id_) and (cls is None or cls in c.cls()):
					out.append(c)
				out.extend(c.find_all(tag, id_, cls))
		return out

	def find(self, *a, **k):
		r = self.find_all(*a, **k)
		return r[0] if r else None

	def text(self):
		parts = []
		for c in self.children:
			if isinstance(c, str):
				parts.append(c)
			elif c.tag == "br":
				parts.append(" ")
			else:
				parts.append(c.text())
		return clean("".join(parts))


def clean(s: str) -> str:
	return re.sub(r"\s+", " ", s.replace("\xa0", " ")).strip()


class Builder(HTMLParser):
	def __init__(self):
		super().__init__(convert_charrefs=True)
		self.root = Node("root")
		self.stack = [self.root]

	def handle_starttag(self, tag, attrs):
		n = Node(tag, attrs)
		# 닫히지 않은 li/td/th/tr/p 를 같은 종류가 열릴 때 닫는다 (경계 태그를 넘지 않는 범위에서)
		bound = {"li": ("ul", "ol"), "td": ("tr",), "th": ("tr",), "tr": ("table", "tbody", "thead"), "p": ("div", "td", "li")}.get(tag)
		if bound:
			for i in range(len(self.stack) - 1, 0, -1):
				t = self.stack[i].tag
				if t == tag or (tag in ("td", "th") and t in ("td", "th")):
					del self.stack[i:]
					break
				if t in bound:
					break
		self.stack[-1].children.append(n)
		if tag not in VOID:
			self.stack.append(n)

	def handle_endtag(self, tag):
		if tag in VOID:
			return
		for i in range(len(self.stack) - 1, 0, -1):
			if self.stack[i].tag == tag:
				del self.stack[i:]
				return

	def handle_data(self, data):
		self.stack[-1].children.append(data)


def parse_html(html: str) -> Node:
	b = Builder()
	b.feed(html)
	return b.root


# ---------- 표 ----------

def expand_table(table: Node):
	"""rowspan · colspan을 풀어 2차원 격자로. (rows, header_row_count)"""
	trs = table.find_all("tr")
	grid = {}
	head_rows = 0
	for r, tr in enumerate(trs):
		c = 0
		for cell in [x for x in tr.children if isinstance(x, Node) and x.tag in ("td", "th")]:
			while (r, c) in grid:
				c += 1
			rs = int(cell.attrs.get("rowspan", 1) or 1)
			cs = int(cell.attrs.get("colspan", 1) or 1)
			items = [clean(li.text()) for li in cell.find_all("li") if not li.find_all("li")]
			val = {"text": cell.text(), "items": items, "th": cell.tag == "th"}
			for dr in range(rs):
				for dc in range(cs):
					grid[(r + dr, c + dc)] = val
			c += cs
		if all(x.tag == "th" for x in tr.children if isinstance(x, Node) and x.tag in ("td", "th")) and tr.children:
			head_rows = max(head_rows, r + 1)
	nrows = max((r for r, _ in grid), default=-1) + 1
	ncols = max((c for _, c in grid), default=-1) + 1
	rows = [[grid.get((r, c), {"text": "", "items": [], "th": False}) for c in range(ncols)] for r in range(nrows)]
	return rows, head_rows


def cell_str(cell) -> str:
	return "; ".join(cell["items"]) if cell["items"] else cell["text"]


def table_block(table: Node) -> dict:
	rows, head_rows = expand_table(table)
	caption = table.find("caption")
	header = [clean(cell_str(c)) for c in rows[0]] if head_rows else None
	body = rows[head_rows:]
	return {
		"kind": "table",
		"caption": caption.text() if caption else "",
		"header": header,
		"rows": [[{"text": c["text"], "items": c["items"]} for c in r] for r in body],
	}


# ---------- 목록 · 문단 ----------

def list_items(node: Node, depth=0):
	out = []
	for li in [c for c in node.children if isinstance(c, Node) and c.tag == "li"]:
		own = clean("".join(c if isinstance(c, str) else ("" if c.tag in ("ul", "ol") else c.text()) for c in li.children))
		out.append({"depth": depth, "text": own})
		for sub in [c for c in li.children if isinstance(c, Node) and c.tag in ("ul", "ol")]:
			out.extend(list_items(sub, depth + 1))
	return out


def blocks_of(node: Node):
	"""탭 안의 블록(표 · 목록 · 문단)을 문서 순서대로."""
	out = []
	for c in node.children:
		if isinstance(c, str):
			if clean(c):
				out.append({"kind": "p", "text": clean(c)})
			continue
		if c.tag == "table":
			out.append(table_block(c))
		elif c.tag in ("ul", "ol"):
			out.append({"kind": "list", "items": list_items(c)})
		elif c.tag in ("p", "h4", "h5", "strong", "span") and clean(c.text()):
			out.append({"kind": "p", "text": c.text()})
		elif c.tag in ("div", "tbody", "form"):
			out.extend(blocks_of(c))
	return out


def parse_page(html: str):
	root = parse_html(html)
	# 본문 제목은 h3 안의 이미지 alt에 있다 (예: "살인범죄 양형기준 2009년")
	alts = [img.attrs.get("alt", "") for h3 in root.find_all("h3") for img in h3.find_all("img")]
	title = next((t for t in alts if "양형기준" in t), "")
	dates = [clean(s.text()) for s in root.find_all("span") if "시행" in s.text() and re.search(r"\d{4}\.", s.text()) and len(s.text()) < 200 and not s.find_all("span")]
	tabs_div = root.find("div", id_="tabs")
	tabs = []
	if tabs_div:
		labels = {}
		ul = tabs_div.find("ul")
		for a in ul.find_all("a") if ul else []:
			labels[a.attrs.get("href", "").lstrip("#")] = a.text()
		for d in tabs_div.find_all("div"):
			tid = d.attrs.get("id", "")
			if tid.startswith("tabs-"):
				tabs.append({"id": tid, "label": labels.get(tid, tid), "blocks": blocks_of(d)})
	return {"title": title, "dates": dates, "tabs": tabs}


def main(argv=None):
	ap = argparse.ArgumentParser()
	ap.add_argument("crime")
	ap.add_argument("--raw", default=str(Path(__file__).parent / "raw"))
	ap.add_argument("--out", default=str(Path(__file__).parent / "out"))
	a = ap.parse_args(argv)
	raw = Path(a.raw) / a.crime
	manifest = json.loads((raw / "manifest.json").read_text(encoding="utf-8"))
	versions = {}
	for m in manifest:
		page = parse_page((raw / m["file"]).read_text(encoding="utf-8"))
		page.update({"pageName": m["page"], "url": m["url"]})
		versions.setdefault(m["version"], []).append(page)
	out = Path(a.out) / a.crime
	out.mkdir(parents=True, exist_ok=True)
	(out / "guideline.json").write_text(json.dumps(versions, ensure_ascii=False, indent=1), encoding="utf-8")
	print(f"버전 {len(versions)}개 → {out/'guideline.json'}")
	for v, pages in versions.items():
		print(f"- {v}: " + ", ".join(f"{p['pageName']}[탭 {len(p['tabs'])}]" for p in pages))


if __name__ == "__main__":
	main()
