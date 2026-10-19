#!/usr/bin/env python3
"""parse.py가 만든 guideline.json을 RAG · 바로 찾기용 조각(chunks.jsonl)과 읽기용 마크다운으로 바꾼다 (BE-52).

조각 규칙: 한 조각에 한 버전 × 한 유형(또는 한 인자 행 · 한 정의) × 한 섹션. 본문 앞에 머리말(범죄 · 적용 기간 · 섹션)을
붙여 임베딩 입력(`embedText`)을 만들고, 같은 정보를 필드로도 둔다(`meta`)해서 바로 찾기(필터)에 쓴다.

버전의 시행일은 시행 중 페이지 상단의 개정 이력("YYYY. M. D. 의결|수정, YYYY. M. D. 시행")을 시간 순으로 버전과 맞춰 정하고,
각 버전 페이지 자체의 마지막 시행일과 비교한다(늦거나 없으면 실패, 이르면 경고 — 사이트가 옛 페이지 표기를 갱신하지 않은 경우).
DB 버전 이름(sentencing_guideline.version_name)은 기존 시드(R__10)와 같은 규칙 "<의결 연도> 수정 (<의결일> 의결)"을 쓴다.

사용: python3 chunk.py 살인 [--out out]
"""
import argparse
import json
import re
from datetime import date, timedelta
from pathlib import Path

DATE_RE = re.compile(r"(\d{4})\.\s*(\d{1,2})\.\s*(\d{1,2})\.\s*시행")
HISTORY_RE = re.compile(r"(\d{4})\.\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(의결|수정)\s*,?\s*(\d{4})\.\s*(\d{1,2})\.\s*(\d{1,2})\.\s*시행")


def history(dates):
	"""개정 이력 → [(의결일, '의결'|'수정', 시행일)] (시간 순)."""
	out = []
	for y, m, d, kind, y2, m2, d2 in HISTORY_RE.findall(" ".join(dates)):
		out.append((date(int(y), int(m), int(d)), kind, date(int(y2), int(m2), int(d2))))
	return out


def db_version_name(decided, kind):
	"""sentencing_guideline.version_name 규칙 (R__10과 같다). 예: '2013 수정 (2013. 4. 22. 의결)'."""
	return f"{decided.year} {'최초' if kind == '의결' else '수정'} ({fmt(decided)} 의결)"


def effective_dates(dates):
	out = []
	for s in dates:
		out += [date(int(y), int(m), int(d)) for y, m, d in DATE_RE.findall(s)]
	return out


def version_order(versions):
	"""내비게이션 순서는 [시행 중, 과거 오래된 순...]. 시간 순으로 맞춘다 → 시행 중이 맨 뒤."""
	names = list(versions)
	return names[1:] + names[:1]


def version_label(name):
	m = re.search(r"(\d)차 수정 .*?(\d{4})년", name)
	if m:
		return f"{m.group(1)}차 수정 {m.group(2)}"
	m = re.search(r"(\d{4})년", name)
	return f"최초 {m.group(1)}" if m else "현행"


def fmt(d):
	return f"{d.year}. {d.month}. {d.day}."


def cell_text(c):
	return "; ".join(c["items"]) if c["items"] else c["text"]


def find_tab(page, label_part):
	return next((t for t in page["tabs"] if label_part in t["label"]), None)


def norm_range(t):
	return re.sub(r"\s+-\s+", " ~ ", t)


def type_names(pages):
	"""'유형의 정의' 탭의 '1. 제1유형 : 이름' 항목에서 {번호: 이름}."""
	out = {}
	for p in pages:
		tab = find_tab(p, "유형의 정의")
		for b in (tab["blocks"] if tab else []):
			for it in b.get("items", []):
				m = re.match(r"\d+\.\s*제(\d+)유형\s*[:：]\s*(.+)", it["text"])
				if m:
					out[m.group(1)] = m.group(2).strip()
	return out


def page_of(pages, key):
	return next((p for p in pages if key in p["pageName"]), None)


def main(argv=None):
	ap = argparse.ArgumentParser()
	ap.add_argument("crime")
	ap.add_argument("--out", default=str(Path(__file__).parent / "out"))
	a = ap.parse_args(argv)
	out = Path(a.out) / a.crime
	versions = json.loads((out / "guideline.json").read_text(encoding="utf-8"))

	order = version_order(versions)
	hist = history(versions[order[-1]][0]["dates"])
	current_dates = effective_dates(versions[order[-1]][0]["dates"])
	if not len(hist) == len(current_dates) == len(order):
		raise SystemExit(f"개정 이력 {len(hist)}개 · 시행일 {len(current_dates)}개 ≠ 버전 {len(order)}개 (사이트 구조가 바뀌었는지 확인)")
	start = dict(zip(order, current_dates))
	db_name = {n: db_version_name(decided, kind) for n, (decided, kind, _) in zip(order, hist)}
	decided_on = {n: decided for n, (decided, _, _) in zip(order, hist)}
	# 시행 중 버전은 사이트 이름에 차수가 없다. "현행"은 개정되면 틀린 말이 되므로 차수로 적는다 (예: 5차 수정 2026)
	vlabel = {n: version_label(n) if version_label(n) != "현행" else f"{i}차 수정 {start[n].year}" for i, n in enumerate(order)}
	for name in order:  # 교차 검증: 버전 페이지의 마지막 시행일 = 맞춘 시행일. 정본은 시행 중 페이지의 개정 이력
		own = effective_dates(versions[name][0]["dates"])
		if not own or own[-1] > start[name]:
			raise SystemExit(f"{name}: 페이지 시행일 {own[-1:]}이 개정 이력의 시행일 {start[name]}과 맞지 않는다")
		if own[-1] != start[name]:  # 사이트가 옛 페이지 상단 표기를 갱신하지 않은 경우(2011 페이지: 2009. 7. 1.까지만 적힘)
			print(f"경고: {name} 페이지는 시행일을 {own[-1]}까지만 적고 있다 → 개정 이력의 {start[name]}을 사용")
	end = {n: (start[order[i + 1]] - timedelta(days=1) if i + 1 < len(order) else None) for i, n in enumerate(order)}

	chunks, md_parts = [], {}
	seq = {}

	def add(name, section, subject, body, extra=None):
		n = seq[name] = seq.get(name, 0) + 1
		s, e = start[name], end[name]
		period = f"{fmt(s)} ~ {fmt(e) if e else '현재'}"
		head = f"[{a.crime}범죄 양형기준 · {vlabel[name]}(시행 {period}) · {section}" + (f" · {subject}" if subject else "") + "]"
		chunks.append({
			"id": f"{a.crime}-{s.isoformat()}-{n:03d}",
			"meta": {
				"crime": f"{a.crime}범죄", "version": vlabel[name], "guidelineVersion": db_name[name], "siteVersionName": name,
				"effectiveFrom": s.isoformat(), "effectiveTo": e.isoformat() if e else None,
				"section": section, "subject": subject, **(extra or {}),
				"sourceUrl": versions[name][0]["url"],
			},
			"text": body,
			"embedText": head + "\n" + body,
		})
		md_parts.setdefault(name, []).append(f"### {section}" + (f" — {subject}" if subject else "") + "\n\n" + body)

	for name in order:
		pages = versions[name]
		main_page, prob_page = pages[0], page_of(pages, "집행유예")
		for p in pages:
			for tab in p["tabs"]:
				is_probation = p is prob_page
				blocks = tab["blocks"]
				for bi, b in enumerate(blocks):
					if b["kind"] == "table":
						hdr = b["header"] or []
						if "기본" in hdr:  # 형량범위 표
							for r in b["rows"]:
								cells = [norm_range(cell_text(c)) for c in r]
								if len(cells) == 5:  # 유형 번호 · 구분(이름) · 감경 · 기본 · 가중
									no, label, lo, base, hi = cells
								else:  # 2009 · 2011 표는 "제1유형" 한 칸(이름 없음) → 유형의 정의 탭에서 이름을 찾는다
									label, lo, base, hi = cells
									no = (re.search(r"\d+", label) or [""])[0]
									label = type_names(pages).get(no, "")
								subject = f"제{no}유형" + (f" {label}" if label else "")
								add(name, "형량범위", subject,
									f"{subject} 권고 형량범위 — 감경영역 {lo}, 기본영역 {base}, 가중영역 {hi}",
									{"typeNo": int(no) if no.isdigit() else None, "typeName": label,
									 "range": {"감경": lo, "기본": base, "가중": hi}})
						else:  # 양형인자 / 집행유예 참작사유 표
							neg = [i for i, h in enumerate(hdr) if h in ("감경요소", "부정적")]
							pos = [i for i, h in enumerate(hdr) if h in ("가중요소", "긍정적")]
							label_cols = len(hdr) - 2
							sec = "집행유예 참작사유" if is_probation else "양형인자"
							for r in b["rows"]:
								path = []
								for c in r[:label_cols]:
									t = cell_text(c)
									if t and (not path or path[-1] != t):
										path.append(t)
								lcol, rcol = r[-2], r[-1]
								ln, rn = ("부정적", "긍정적") if is_probation else ("감경요소", "가중요소")
								body = f"{' > '.join(path)}\n- {ln}: {'; '.join(lcol['items']) or lcol['text']}\n- {rn}: {'; '.join(rcol['items']) or rcol['text']}"
								add(name, sec, " > ".join(path), body,
									{"factorPath": path, ln: lcol["items"] or [lcol["text"]], rn: rcol["items"] or [rcol["text"]]})
					elif b["kind"] == "p":
						add(name, "형량 적용 참고" if tab["label"].startswith("형종") else tab["label"], "", b["text"])
					elif b["kind"] == "list":
						items = b["items"]
						# 정의 · 원칙 목록: 깊이 0 항목마다 한 조각(그 아래 항목 포함). 짧은 목록은 통째로 한 조각
						whole = len(items) <= 12 and tab["label"] not in ("유형의 정의", "양형인자의 정의")
						groups, cur = [], None
						for it in items:
							if it["depth"] == 0 or cur is None or whole:
								if whole and cur is not None:
									cur.append(it)
									continue
								cur = [it]
								groups.append(cur)
							else:
								cur.append(it)
						for g in groups:
							text = "\n".join(("  " * it["depth"]) + ("- " if it["depth"] else "") + it["text"] for it in g)
							subject = g[0]["text"][:40] if not whole else ""
							add(name, "형량 적용 참고" if tab["label"].startswith("형종") else tab["label"], subject, text)

	(out / "versions.json").write_text(json.dumps([{
		"crimeCategory": f"{a.crime}범죄", "versionName": db_name[n], "label": vlabel[n], "siteVersionName": n,
		"decidedOn": decided_on[n].isoformat(), "effectiveFrom": start[n].isoformat(),
		"effectiveTo": end[n].isoformat() if end[n] else None, "sourceUrl": versions[n][0]["url"], "chunks": seq[n],
	} for n in order], ensure_ascii=False, indent=1), encoding="utf-8")
	(out / "chunks.jsonl").write_text("\n".join(json.dumps(c, ensure_ascii=False) for c in chunks) + "\n", encoding="utf-8")
	(out / "md").mkdir(exist_ok=True)
	for name in order:
		s, e = start[name], end[name]
		(out / "md" / f"{start[name].isoformat()}_{vlabel[name].replace(' ', '')}.md").write_text(
			f"# {a.crime}범죄 양형기준 — {vlabel[name]}\n\n- 시행: {fmt(s)} ~ {fmt(e) if e else '현재'}\n- 출처: {versions[name][0]['url']}\n\n" + "\n\n".join(md_parts[name]) + "\n",
			encoding="utf-8")
	print(f"조각 {len(chunks)}개 · 버전 {len(order)}개")
	for n in order:
		print(f"- {vlabel[n]}: 시행 {start[n]} ~ {end[n] or '현재'}, 조각 {seq[n]}개")
	lens = sorted(len(c["embedText"]) for c in chunks)
	print(f"조각 길이(자): 최소 {lens[0]} · 중앙 {lens[len(lens)//2]} · 최대 {lens[-1]}, 전체 {sum(lens)}자")


if __name__ == "__main__":
	main()
