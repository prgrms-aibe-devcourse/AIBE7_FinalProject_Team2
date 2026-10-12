#!/usr/bin/env python3
"""양형위원회 사이트(sc.scourt.go.kr)에서 범죄 유형별 양형기준 페이지를 받아 raw/ 에 저장한다 (BE-52).

- 사이트가 EUC-KR(cp949)이라 UTF-8로 바꿔 저장한다.
- 버전 목록은 내비게이션(시행 중 + 과거 양형기준)에서 "<범죄명>범죄"로 시작하는 링크를 찾아 만든다.
- 버전마다 하위 페이지(형종 및 형량 / 집행유예 등)를 따라가 모두 받는다.
- 표준 라이브러리만 쓴다.

사용: python3 fetch.py 살인 [--out raw]
"""
import argparse
import re
import sys
import time
import subprocess
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin

BASE = "https://sc.scourt.go.kr"
START = BASE + "/sc/krsc/criterion/criterion_04/robber_01.jsp"  # 내비게이션 전체가 들어 있는 아무 페이지
UA = "Mozilla/5.0"


def get(url: str) -> str:
	# 파이썬 번들 인증서로는 이 사이트 체인 검증이 실패해(중간 인증서 누락) 시스템 curl을 쓴다. 검증은 끄지 않는다.
	for attempt in range(3):
		res = subprocess.run(["curl", "-sSfL", "--max-time", "30", "-A", UA, url], capture_output=True)
		if res.returncode == 0:
			return res.stdout.decode("cp949", errors="replace")
		if attempt == 2:
			raise RuntimeError(f"받기 실패: {url}: {res.stderr.decode(errors='replace')}")
		print(f"  재시도 {attempt + 1}: {url}", file=sys.stderr)
		time.sleep(2)
	raise RuntimeError("unreachable")


class Links(HTMLParser):
	"""<a href> 와 안의 글자, title 속성을 모은다."""

	def __init__(self):
		super().__init__()
		self.links = []  # (href, text, title)
		self._cur = None

	def handle_starttag(self, tag, attrs):
		if tag == "a":
			a = dict(attrs)
			self._cur = [a.get("href", ""), "", a.get("title", "")]

	def handle_data(self, data):
		if self._cur is not None:
			self._cur[1] += data

	def handle_endtag(self, tag):
		if tag == "a" and self._cur is not None:
			href, text, title = self._cur
			self.links.append((href, re.sub(r"\s+", " ", text).strip(), title))
			self._cur = None


def links_of(html: str):
	p = Links()
	p.feed(html)
	return p.links


def find_versions(nav_html: str, crime: str):
	"""내비게이션에서 '<crime>범죄 ...' 링크를 모아 (이름, URL) 목록으로. 시행 중 + 과거."""
	seen, out = set(), []
	for href, text, _ in links_of(nav_html):
		if not href.startswith("/sc/krsc/criterion/"):
			continue
		if not re.match(rf"^(구 )?{re.escape(crime)}범죄", text):
			continue
		if href in seen:
			continue
		seen.add(href)
		out.append((text, urljoin(BASE, href)))
	return out


def sub_pages(html: str, page_url: str):
	"""같은 디렉터리의 하위 페이지(title 속성이 있는 *_NN.jsp 링크)."""
	base_dir = page_url.rsplit("/", 1)[0]
	seen, out = set(), []
	for href, _, title in links_of(html):
		u = urljoin(page_url, href)
		if u.rsplit("/", 1)[0] != base_dir or not title or u in seen:
			continue
		if not re.search(r"_\d+\.jsp$", u):
			continue
		seen.add(u)
		out.append((title, u))
	return out


def main(argv=None):
	ap = argparse.ArgumentParser()
	ap.add_argument("crime", help="범죄명 (예: 살인 → '살인범죄')")
	ap.add_argument("--out", default=str(Path(__file__).parent / "raw"))
	a = ap.parse_args(argv)
	out = Path(a.out) / a.crime
	out.mkdir(parents=True, exist_ok=True)

	nav = get(START)
	versions = find_versions(nav, a.crime)
	if not versions:
		sys.exit(f"{a.crime}범죄 링크를 찾지 못했다")
	print(f"{a.crime}범죄 버전 {len(versions)}개")
	for name, url in versions:
		print(f"- {name}: {url}")

	manifest = []
	for name, url in versions:
		html = get(url)
		pages = [("(첫 페이지)", url)] + [(t, u) for t, u in sub_pages(html, url) if u != url]
		for title, u in pages:
			fname = u.replace(BASE + "/sc/krsc/criterion/", "").replace("/", "__")
			body = html if u == url else get(u)
			(out / fname).write_text(body, encoding="utf-8")
			manifest.append({"version": name, "page": title, "url": u, "file": fname})
			print(f"  저장 {fname} ({title})")
			time.sleep(0.5)

	import json
	(out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
	print(f"완료: {len(manifest)}개 페이지 → {out}")


if __name__ == "__main__":
	main()
