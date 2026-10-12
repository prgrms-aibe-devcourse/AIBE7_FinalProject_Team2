#!/usr/bin/env python3
"""양형기준 조각(out/<범죄>/chunks.jsonl) 임베딩 · 적재 SQL 생성 (BE-52).

    python3 store.py embed [범죄 ...]            # 바뀐 조각만 임베딩 (GEMINI_API_KEY 필요)
    python3 store.py sql --output <R__41.sql>    # out/ 아래 모든 범죄를 합쳐 반복 마이그레이션 생성

- embed: embedText를 gemini-embedding-001(3072차원, RETRIEVAL_DOCUMENT)로 임베딩해 out/<범죄>/embeddings.jsonl에 둔다.
  embedText의 SHA-256이 같으면 다시 부르지 않는다. 판례 저장소와 같은 llm.embed()를 쓴다(tools/ai-judgment/llm.py).
- sql: sentencing_guideline 버전을 넣고(있으면 시행일 · 출처만 맞춤) rag_guideline을 통째로 지우고 다시 넣는다.
  임베딩이 없는 조각이 하나라도 있으면 만들지 않는다.
표준 라이브러리만 쓴다.
"""
import argparse
import hashlib
import json
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "ai-judgment"))
import llm  # noqa: E402

MODEL = "gemini-embedding-001"
DIM = 3072


def crimes(out, names):
	dirs = [out / n for n in names] if names else sorted(p for p in out.iterdir() if (p / "chunks.jsonl").exists())
	for d in dirs:
		if not (d / "chunks.jsonl").exists():
			sys.exit(f"{d}/chunks.jsonl 없음 (fetch.py → parse.py → chunk.py를 먼저 돌린다)")
	return dirs


def read_jsonl(path):
	if not path.exists():
		return []
	return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def sha(text):
	return hashlib.sha256(text.encode("utf-8")).hexdigest()


def load_embeddings(d):
	return {e["id"]: e for e in read_jsonl(d / "embeddings.jsonl")}


def run_embed(args):
	for d in crimes(Path(args.out), args.crimes):
		chunks = read_jsonl(d / "chunks.jsonl")
		cache = load_embeddings(d)
		pending = [c for c in chunks if cache.get(c["id"], {}).get("sha") != sha(c["embedText"]) or cache[c["id"]]["model"] != MODEL]
		for start in range(0, len(pending), llm.EMBEDDING_BATCH):
			batch = pending[start:start + llm.EMBEDDING_BATCH]
			vectors = llm.embed([c["embedText"] for c in batch], model=MODEL, dimensions=DIM)
			for c, v in zip(batch, vectors):
				cache[c["id"]] = {"id": c["id"], "sha": sha(c["embedText"]), "model": MODEL, "dim": len(v), "values": v}
			if args.delay and start + llm.EMBEDDING_BATCH < len(pending):
				time.sleep(args.delay)
		keep = {c["id"] for c in chunks}  # 사라진 조각의 임베딩은 버린다
		(d / "embeddings.jsonl").write_text("".join(json.dumps(cache[k], ensure_ascii=False) + "\n" for k in sorted(keep) if k in cache), encoding="utf-8")
		print(f"{d.name}: 조각 {len(chunks)}개, 새로 임베딩 {len(pending)}개", file=sys.stderr)


# ---------- sql ----------

def q(value):
	return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"


def q_int(value):
	return "NULL" if value is None else str(int(value))


def q_jsonb(value):
	return q(json.dumps(value, ensure_ascii=False)) + "::jsonb"


def q_vector(values):
	return "'[" + ",".join(repr(float(v)) for v in values) + "]'::vector"


def guideline_ref(crime, version):
	return f"(SELECT id FROM sentencing_guideline WHERE crime_category = {q(crime)} AND version_name = {q(version)})"


# meta에서 칸으로 따로 둔 값은 뺀다 (중복 저장 방지)
META_SKIP = {"crime", "guidelineVersion", "section", "subject", "typeNo", "sourceUrl"}


def build_sql(dirs):
	version_rows, chunk_rows, missing = [], [], []
	for d in dirs:
		for v in json.loads((d / "versions.json").read_text(encoding="utf-8")):
			version_rows.append(
				f"INSERT INTO sentencing_guideline (crime_category, version_name, effective_date, source_url)\n"
				f"VALUES ({q(v['crimeCategory'])}, {q(v['versionName'])}, DATE {q(v['effectiveFrom'])}, {q(v['sourceUrl'])})\n"
				f"ON CONFLICT (crime_category, version_name) DO UPDATE SET effective_date = EXCLUDED.effective_date, source_url = EXCLUDED.source_url;")
		cache = load_embeddings(d)
		for c in read_jsonl(d / "chunks.jsonl"):
			e = cache.get(c["id"])
			if not e or e["sha"] != sha(c["embedText"]):
				missing.append(c["id"])
				continue
			m = c["meta"]
			meta = {k: v for k, v in m.items() if k not in META_SKIP}
			chunk_rows.append(
				"INSERT INTO rag_guideline (guideline_id, chunk_key, section, subject, type_no, content, embed_text, meta, "
				"embedding, embedding_model, embedding_dim, source_url)\n"
				f"VALUES ({guideline_ref(m['crime'], m['guidelineVersion'])}, {q(c['id'])}, {q(m['section'])}, {q(m['subject'] or None)}, "
				f"{q_int(m.get('typeNo'))}, {q(c['text'])}, {q(c['embedText'])}, {q_jsonb(meta)}, "
				f"{q_vector(e['values'])}, {q(e['model'])}, {e['dim']}, {q(m['sourceUrl'])});")
	if missing:
		sys.exit(f"임베딩이 없거나 낡은 조각 {len(missing)}개 (store.py embed를 먼저 실행한다): {', '.join(missing[:5])} ...")
	header = [
		"-- R__41_rag_guideline.sql — AI 판결 RAG 양형기준 저장소 (BE-52). 공개 저장소 tools/sentencing-guideline/store.py sql 출력",
		"-- 손으로 고치지 않는다. 양형기준을 바꾸려면 fetch · parse · chunk · embed · sql을 다시 실행한다",
		"-- sentencing_guideline 버전은 이름으로 맞춰 넣고(있으면 시행일 · 출처만 고침), rag_guideline은 통째로 바꾼다",
		f"-- 범죄 {len(dirs)}개 ({', '.join(d.name for d in dirs)}) · 버전 {len(version_rows)}개 · 조각 {len(chunk_rows)}개",
		"",
	]
	return "\n".join(header) + "\n".join(version_rows) + "\n\nDELETE FROM rag_guideline;\n\n" + "\n\n".join(chunk_rows) + "\n"


def run_sql(args):
	dirs = crimes(Path(args.out), args.crimes)
	sql = build_sql(dirs)
	Path(args.output).write_text(sql, encoding="utf-8")
	print(f"{args.output} (조각 {sql.count('INSERT INTO rag_guideline')}개)", file=sys.stderr)


def main(argv=None):
	ap = argparse.ArgumentParser(description="AI 판결 RAG 양형기준 저장소 적재 (BE-52)")
	ap.add_argument("--out", default=str(HERE / "out"), help="chunk.py 출력 폴더")
	sub = ap.add_subparsers(dest="cmd", required=True)
	e = sub.add_parser("embed", help="조각 임베딩 (바뀐 것만)")
	e.add_argument("crimes", nargs="*", help="범죄명 (없으면 out/ 아래 전부)")
	e.add_argument("--delay", type=float, default=1.0, help="배치 사이 대기(초)")
	s = sub.add_parser("sql", help="R__41 시드 SQL 만들기")
	s.add_argument("crimes", nargs="*")
	s.add_argument("--output", required=True)
	args = ap.parse_args(argv)
	{"embed": run_embed, "sql": run_sql}[args.cmd](args)


if __name__ == "__main__":
	main()
