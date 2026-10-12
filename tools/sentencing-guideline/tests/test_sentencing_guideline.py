"""양형기준 RAG 저장소 도구 테스트 (BE-52). 네트워크 · DB를 쓰지 않는다.

사이트 HTML을 흉내 낸 작은 페이지로 버전 찾기 · 표 해석(rowspan/colspan) · 시행일 맞추기 · 조각 · SQL 생성을 확인한다.
실행: tools/sentencing-guideline에서 `python3 -m unittest discover tests`
"""

import contextlib
import hashlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import chunk  # noqa: E402
import fetch  # noqa: E402
import parse  # noqa: E402
import store  # noqa: E402

NAV = """
<ul>
  <li><a href="/sc/krsc/criterion/criterion_01/murder_01.jsp">살인범죄 양형기준</a></li>
  <li><a href="/sc/krsc/criterion/criterion_04/robber_01.jsp">강도범죄 양형기준</a></li>
  <li><a href="/sc/krsc/criterion/past/murder/murder_01.jsp">살인범죄 양형기준 2009년</a></li>
  <li><a href="/sc/krsc/criterion/past/criterion_01_2013/murder_01.jsp"><span>살인범죄 2차 수정 양형기준 2013년</span></a></li>
  <li><a href="/sc/krsc/criterion/past/murder/murder_01.jsp">살인범죄 양형기준 2009년</a></li>
</ul>
"""


def page(title, dates, range_table, extra_tabs=""):
	"""버전 첫 페이지 흉내: 제목 · 개정 이력 · 하위 페이지 링크 · 탭(형량 표 + 양형인자 표 + 유형의 정의)."""
	return f"""
<h3><img src="x.gif" alt="{title}" /></h3>
<span class="first">{dates}</span>
<ul><li class="first on"><a href="murder_01.jsp" title="형종 및 형량의 기준">형종</a></li>
    <li><a href="murder_02.jsp" title="집행유예 기준">집행유예</a></li></ul>
<div id="tabs">
  <ul><li><a href="#tabs-0"><span>형종 및 <br/>형량의 기준</span></a></li><li><a href="#tabs-1"><span>유형의 <br/>정의</span></a></li></ul>
  <div id="tabs-0">
    <div class="tableType01">{range_table}</div>
    <ul class="cirList01"><li>살인미수범죄의 권고 형량범위는 1/2로 감경</li></ul>
    <table>
      <thead><tr><th colspan="3">구분</th><th>감경요소</th><th>가중요소</th></tr></thead>
      <tbody>
        <tr><td rowspan="2">특별양형인자</td><td rowspan="1">행위</td><td>공통</td>
            <td><ul><li> 과잉방위</li><li>미필적 고의</li></ul></td><td><ul><li>계획적 범행</li></ul></td></tr>
        <tr><td colspan="2">행위자/기타</td><td><ul><li>자수</li></ul></td><td><ul><li>누범</li></ul></td></tr>
      </tbody>
    </table>
  </div>
  <div id="tabs-1">
    <ol><li>1. 제1유형 : 참작할 사유가 있는 살인<ul><li>극도의 생계 곤란</li></ul></li>
        <li>2. 제2유형 : 보통 동기에 의한 살인</li></ol>
  </div>
  {extra_tabs}
</div>
"""


OLD_TABLE = """<table><thead><tr><th>구분</th><th>감경</th><th>기본</th><th>가중</th></tr></thead>
<tbody><tr><td>제1유형</td><td>3년 - 5년</td><td>4년 - 6년</td><td>5년 - 7년</td></tr>
<tr><td>제2유형</td><td>6년 - 9년</td><td>8년 - 11년</td><td>10년 - 13년</td></tr></tbody></table>"""

NEW_TABLE = """<table><thead><tr><th>유형</th><th>구분</th><th>감경</th><th>기본</th><th>가중</th></tr></thead>
<tbody><tr><td>1</td><td>참작 동기 살인</td><td>3년 ~ 5년</td><td>4년 ~ 6년</td><td>5년 ~ 8년</td></tr>
<tr><td>2</td><td>보통 동기 살인</td><td>7년 ~ 12년</td><td>10년 ~ 16년</td><td>15년 이상, 무기 이상</td></tr></tbody></table>"""

HISTORY = "2009. 4. 24. 의결, 2009. 7. 1. 시행, 2013. 4. 22. 수정, 2013. 5. 15. 시행, 2026. 3. 30. 수정, 2026. 7. 1. 시행"


def build_guideline(tmp, current_dates=HISTORY, old_2013_dates="2009. 4. 24. 의결, 2009. 7. 1. 시행 2013. 4. 22. 수정, 2013. 5. 15. 시행"):
	"""내비게이션 순서(시행 중 → 과거 오래된 순)로 guideline.json을 만든다."""
	pages = {
		"살인범죄 양형기준": page("살인범죄 양형기준", current_dates, NEW_TABLE),
		"살인범죄 양형기준 2009년": page("살인범죄 양형기준 2009년", "2009.4.24. 의결, 2009.7.1. 시행", OLD_TABLE),
		"살인범죄 2차 수정 양형기준 2013년": page("살인범죄 2차 수정 양형기준 2013년", old_2013_dates, NEW_TABLE),
	}
	versions = {}
	for name, html in pages.items():
		p = parse.parse_page(html)
		p.update({"pageName": "(첫 페이지)", "url": f"https://example/{len(versions)}.jsp"})
		versions[name] = [p]
	out = Path(tmp) / "살인"
	out.mkdir(parents=True)
	(out / "guideline.json").write_text(json.dumps(versions, ensure_ascii=False), encoding="utf-8")
	return out


def run_chunk(tmp):
	buf = io.StringIO()
	with contextlib.redirect_stdout(buf):
		chunk.main(["살인", "--out", str(tmp)])
	out = Path(tmp) / "살인"
	chunks = [json.loads(line) for line in (out / "chunks.jsonl").read_text(encoding="utf-8").splitlines()]
	return chunks, json.loads((out / "versions.json").read_text(encoding="utf-8")), buf.getvalue()


class FetchTest(unittest.TestCase):
	def test_find_versions_matches_crime_prefix_and_dedupes(self):
		found = fetch.find_versions(NAV, "살인")
		self.assertEqual([name for name, _ in found], ["살인범죄 양형기준", "살인범죄 양형기준 2009년", "살인범죄 2차 수정 양형기준 2013년"])
		self.assertTrue(all(url.startswith("https://sc.scourt.go.kr/sc/krsc/criterion/") for _, url in found))

	def test_sub_pages_keeps_same_directory_titled_links(self):
		html = page("t", "", "")
		url = "https://sc.scourt.go.kr/sc/krsc/criterion/past/murder/murder_01.jsp"
		self.assertEqual([u.rsplit("/", 1)[1] for _, u in fetch.sub_pages(html, url)], ["murder_01.jsp", "murder_02.jsp"])


class ParseTest(unittest.TestCase):
	def test_tabs_and_labels(self):
		p = parse.parse_page(page("살인범죄 양형기준 2009년", "2009.4.24. 의결, 2009.7.1. 시행", OLD_TABLE))
		self.assertEqual(p["title"], "살인범죄 양형기준 2009년")
		self.assertEqual([t["label"] for t in p["tabs"]], ["형종 및 형량의 기준", "유형의 정의"])

	def test_rowspan_colspan_expand_to_grid(self):
		p = parse.parse_page(page("t", "", NEW_TABLE))
		factor = [b for b in p["tabs"][0]["blocks"] if b["kind"] == "table"][1]
		self.assertEqual(factor["header"], ["구분", "구분", "구분", "감경요소", "가중요소"])
		first, second = factor["rows"]
		self.assertEqual([c["text"] for c in first[:3]], ["특별양형인자", "행위", "공통"])
		self.assertEqual(first[3]["items"], ["과잉방위", "미필적 고의"])
		# rowspan으로 이어진 칸과 colspan으로 넓힌 칸이 같은 값으로 채워진다
		self.assertEqual([c["text"] for c in second[:3]], ["특별양형인자", "행위자/기타", "행위자/기타"])

	def test_nested_list_depth(self):
		p = parse.parse_page(page("t", "", ""))
		items = p["tabs"][1]["blocks"][0]["items"]
		self.assertEqual([(i["depth"], i["text"]) for i in items],
						 [(0, "1. 제1유형 : 참작할 사유가 있는 살인"), (1, "극도의 생계 곤란"), (0, "2. 제2유형 : 보통 동기에 의한 살인")])


class ChunkTest(unittest.TestCase):
	def test_history_and_db_version_name(self):
		hist = chunk.history([HISTORY])
		self.assertEqual(len(hist), 3)
		self.assertEqual(chunk.db_version_name(*hist[0][:2]), "2009 최초 (2009. 4. 24. 의결)")
		self.assertEqual(chunk.db_version_name(*hist[1][:2]), "2013 수정 (2013. 4. 22. 의결)")  # 기존 시드(R__10)와 같은 이름

	def test_versions_are_ordered_and_periods_close(self):
		with tempfile.TemporaryDirectory() as tmp:
			build_guideline(tmp)
			_, versions, _ = run_chunk(tmp)
		self.assertEqual([(v["label"], v["effectiveFrom"], v["effectiveTo"]) for v in versions], [
			("최초 2009", "2009-07-01", "2013-05-14"),
			("2차 수정 2013", "2013-05-15", "2026-06-30"),
			("2차 수정 2026", "2026-07-01", None),  # 시행 중 버전은 "현행" 대신 차수로 적는다
		])

	def test_range_chunks_old_table_gets_type_names_and_normalized_ranges(self):
		with tempfile.TemporaryDirectory() as tmp:
			build_guideline(tmp)
			chunks, _, _ = run_chunk(tmp)
		first = [c for c in chunks if c["meta"]["section"] == "형량범위" and c["meta"]["effectiveFrom"] == "2009-07-01"][0]
		self.assertEqual(first["meta"]["typeNo"], 1)
		self.assertEqual(first["meta"]["typeName"], "참작할 사유가 있는 살인")  # 유형의 정의 탭에서 가져온 이름
		self.assertEqual(first["meta"]["range"], {"감경": "3년 ~ 5년", "기본": "4년 ~ 6년", "가중": "5년 ~ 7년"})
		self.assertTrue(first["embedText"].startswith("[살인범죄 양형기준 · 최초 2009(시행 2009. 7. 1. ~ 2013. 5. 14.) · 형량범위 · 제1유형"))

	def test_factor_chunk_path_and_lists(self):
		with tempfile.TemporaryDirectory() as tmp:
			build_guideline(tmp)
			chunks, _, _ = run_chunk(tmp)
		factor = [c for c in chunks if c["meta"]["section"] == "양형인자"][1]
		self.assertEqual(factor["meta"]["factorPath"], ["특별양형인자", "행위자/기타"])  # 겹친 칸은 한 번만
		self.assertEqual(factor["meta"]["감경요소"], ["자수"])

	def test_stale_page_date_warns_but_uses_history(self):
		with tempfile.TemporaryDirectory() as tmp:
			build_guideline(tmp, old_2013_dates="2009.4.24. 의결, 2009.7.1. 시행")
			_, versions, log = run_chunk(tmp)
		self.assertIn("경고", log)
		self.assertEqual(versions[1]["effectiveFrom"], "2013-05-15")

	def test_history_count_mismatch_stops(self):
		with tempfile.TemporaryDirectory() as tmp:
			build_guideline(tmp, current_dates="2009. 4. 24. 의결, 2009. 7. 1. 시행")
			with self.assertRaises(SystemExit):
				run_chunk(tmp)


class StoreTest(unittest.TestCase):
	def prepared(self, tmp, embed=True):
		build_guideline(tmp)
		chunks, _, _ = run_chunk(tmp)
		if embed:
			with open(Path(tmp) / "살인" / "embeddings.jsonl", "w", encoding="utf-8") as f:
				for c in chunks:
					f.write(json.dumps({"id": c["id"], "sha": hashlib.sha256(c["embedText"].encode()).hexdigest(),
										"model": "test", "dim": 3, "values": [0.1, 0.2, 0.3]}) + "\n")
		return chunks

	def test_sql_refuses_without_embeddings(self):
		with tempfile.TemporaryDirectory() as tmp:
			self.prepared(tmp, embed=False)
			with self.assertRaises(SystemExit):
				store.build_sql(store.crimes(Path(tmp), []))

	def test_sql_refuses_stale_embedding(self):
		with tempfile.TemporaryDirectory() as tmp:
			chunks = self.prepared(tmp)
			c = chunks[0]
			c["embedText"] += " (바뀜)"
			(Path(tmp) / "살인" / "chunks.jsonl").write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in chunks) + "\n", encoding="utf-8")
			with self.assertRaises(SystemExit):
				store.build_sql(store.crimes(Path(tmp), []))

	def test_sql_upserts_versions_and_replaces_chunks(self):
		with tempfile.TemporaryDirectory() as tmp:
			chunks = self.prepared(tmp)
			sql = store.build_sql(store.crimes(Path(tmp), []))
		self.assertEqual(sql.count("INSERT INTO sentencing_guideline"), 3)
		self.assertIn("ON CONFLICT (crime_category, version_name)", sql)
		self.assertIn("'2013 수정 (2013. 4. 22. 의결)'", sql)
		self.assertLess(sql.index("DELETE FROM rag_guideline;"), sql.index("INSERT INTO rag_guideline"))
		self.assertEqual(sql.count("INSERT INTO rag_guideline"), len(chunks))
		self.assertIn("'[0.1,0.2,0.3]'::vector", sql)

	def test_sql_quotes_single_quotes(self):
		self.assertEqual(store.q("‘무기’와 'x'"), "'‘무기’와 ''x'''")


if __name__ == "__main__":
	unittest.main()
