"""graphify 그래프에 문서 <-> 코드 연결 엣지를 추가한다 (COMMON-34).

문서는 문서끼리, 코드는 구조(AST)만 추출하므로 기본 그래프에는 둘을 잇는 엣지가 없다.
이 스크립트는 이름 · 경로 · 번호가 일치하는 것만 규칙 기반으로 이어 `implements` / `references`
엣지를 만든다 (LLM 추정 없음, 전부 근거가 코드에 적혀 있는 연결).

연결 규칙
  1. API 명세 (API N, A-N)  <- 컨트롤러 메서드 (HTTP 메서드 + 경로 일치)
  2. API 명세 (API N, A-N)  <- 코드·테스트 주석에 "API N" / "A-N" 이 적힌 곳
  3. 요구사항 (REQ-NNN)      <- 코드 주석에 "REQ-NNN" 이 적힌 곳
  4. ERD 테이블              <- SQL 마이그레이션 테이블, JPA @Table 엔티티
  5. 에러 코드 표            <- ErrorCode enum 상수 (이름 일치)

사용 (레포 루트에서)
  python3 tools/graphify/link_docs_code.py            # 기존 graphify-out/graph.json 을 기반으로 재구성
  python3 tools/graphify/link_docs_code.py --stats    # 연결 통계만 출력 (그래프는 쓰지 않음)

선행 조건: graphify 설치 + `/graphify .` 로 문서(semantic) 추출이 한 번 끝나 graphify-out/graph.json 이 있을 것.
확인한 버전: graphifyy 0.9.90. 이 스크립트는 graphify 내부 함수(`graphify.watch._read_build_excludes` 등 `_` 시작 함수)를 직접 불러 쓰므로,
graphify를 올린 뒤 import 오류가 나면 이 호출부를 먼저 확인한다.
"""
import argparse
import collections
import json
import re
import sys
from pathlib import Path

from graphify.analyze import god_nodes, suggest_questions, surprising_connections
from graphify.build import build_from_json
from graphify.cluster import cluster, score_all
from graphify.detect import detect
from graphify.export import to_json
from graphify.extract import collect_files, extract
from graphify.paths import out_path
from graphify.report import generate
from graphify.watch import _read_build_excludes, _read_build_gitignore

ROOT = Path.cwd()
OUT = ROOT / "graphify-out"
API_DOC = "docs/api-specification.md"
CODE_EXT = {".java", ".js", ".sql"}

RE_API = re.compile(r"\bAPI (\d{1,2})\b")
RE_ADMIN = re.compile(r"\bA-(\d{1,2})\b")
RE_REQ = re.compile(r"\bREQ-(\d{3})\b")
RE_CLASS_MAP = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"')
RE_METHOD_MAP = re.compile(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?')
RE_JAVA_DECL = re.compile(r"\b(\w+)\s*\(")
RE_ROUTE = re.compile(r"^\s*###\s+(API\s+\d+|A-\d+)\.[^`]*`(GET|POST|PUT|PATCH|DELETE)\s+(\S+)`")
RE_ENUM_TOKEN = re.compile(r"\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b")


def load_semantic_base():
    """기존 graph.json 에서 문서(semantic) 추출분만 꺼낸다. 첫 실행 때만 읽고 이후엔 캐시 파일을 쓴다."""
    cache = OUT / "semantic-base.json"
    if cache.exists():
        return json.loads(cache.read_text(encoding="utf-8"))
    g = json.loads((OUT / "graph.json").read_text(encoding="utf-8"))
    keep = {n["id"] for n in g["nodes"] if n.get("_origin") == "semantic"}
    drop = {"community", "community_name", "norm_label"}
    base = {
        "nodes": [{k: v for k, v in n.items() if k not in drop} for n in g["nodes"] if n["id"] in keep],
        "edges": [e for e in g["links"] if e.get("_origin") == "semantic"],
        "hyperedges": g.get("hyperedges", []),
        "labels": {n["id"]: n.get("community_name") for n in g["nodes"] if n.get("community_name")},
    }
    cache.write_text(json.dumps(base, ensure_ascii=False), encoding="utf-8")
    return base


def run_ast():
    det = detect(ROOT, extra_excludes=_read_build_excludes(out_path()), gitignore=_read_build_gitignore(out_path()))
    code = []
    for f in det["files"]["code"]:
        p = Path(f)
        code.extend(collect_files(p) if p.is_dir() else [p])
    return det, extract(code, cache_root=ROOT, root=ROOT)


class Index:
    """AST 노드를 파일 · 줄 번호로 찾기 위한 색인."""

    def __init__(self, ast_nodes):
        self.by_file = collections.defaultdict(list)
        self.file_node = {}
        for n in ast_nodes:
            sf = n.get("source_file")
            if not sf:
                continue
            m = re.match(r"L(\d+)", str(n.get("source_location") or ""))
            line = int(m.group(1)) if m else 0
            self.by_file[sf].append((line, n))
            if n["label"] == Path(sf).name:
                self.file_node[sf] = n["id"]
        for v in self.by_file.values():
            v.sort(key=lambda t: t[0])

    def target(self, sf, line, window=6):
        """주석이 달린 줄 바로 뒤에 선언된 노드 (없으면 파일 노드)."""
        for ln, n in self.by_file.get(sf, []):
            if line < ln <= line + window and n["label"] != Path(sf).name:
                return n["id"]
        return self.file_node.get(sf)

    def method(self, sf, name, first_line, decl_line):
        """AST 는 어노테이션 줄(first_line)이나 선언 줄(decl_line)을 메서드 위치로 잡는다."""
        for ln, n in self.by_file.get(sf, []):
            if n["label"] == f".{name}()" and first_line - 1 <= ln <= decl_line + 1:
                return n["id"]
        return None

    def named(self, sf, label):
        for _, n in self.by_file.get(sf, []):
            if n["label"] == label:
                return n["id"]
        return None


def edge(src, dst, rel, how, score=1.0, sf=None, loc=None):
    return {"source": src, "target": dst, "relation": rel, "confidence": "EXTRACTED", "confidence_score": score,
            "source_file": sf, "source_location": loc, "weight": 1.0, "_origin": "semantic", "link": how}


def code_files():
    for p in sorted(ROOT.glob("**/*")):
        rel = p.relative_to(ROOT).as_posix()
        if p.suffix in CODE_EXT and p.is_file() and not rel.startswith(("tools/", "backend/private-seed/", "graphify-out/")) \
                and "node_modules" not in rel and "/build/" not in rel and "/dist/" not in rel:
            yield rel, p.read_text(encoding="utf-8", errors="ignore").splitlines()


def build_links(nodes, idx):
    doc_api, doc_req, doc_node = collections.defaultdict(list), collections.defaultdict(list), {}
    for n in nodes:
        sf, lab = n.get("source_file", ""), n["label"]
        if not sf.startswith(("docs/", "README", "CLAUDE", "AGENTS", "PRODUCT")):
            continue
        doc_node[n["id"]] = n
        if sf == API_DOC:
            m = re.match(r"API (\d{1,2})\b", lab) or re.match(r"A-(\d{1,2})\b", lab)
            if m:
                doc_api[("A" if lab.startswith("A-") else "API", int(m.group(1)))].append(n["id"])
        m = re.match(r"REQ-(\d{3})\b", lab)
        if m:
            doc_req[m.group(1)].append(n["id"])

    links, stats = [], collections.Counter()
    seen = set()

    def add(src, dst, rel, how, **kw):
        if src and dst and (src, dst, rel) not in seen:
            seen.add((src, dst, rel))
            links.append(edge(src, dst, rel, how, **kw))
            stats[how] += 1

    # 1. API 명세 <- 컨트롤러 메서드 (경로 일치)
    routes = {}
    for line in (ROOT / API_DOC).read_text(encoding="utf-8").splitlines():
        m = RE_ROUTE.match(line)
        if m:
            key = ("A" if m.group(1).startswith("A-") else "API", int(re.sub(r"\D", "", m.group(1))))
            routes[(m.group(2), re.sub(r"\{[^}]+\}", "{}", m.group(3)))] = key
    for sf, lines in code_files():
        if not sf.endswith("Controller.java"):
            continue
        base = ""
        for ln in lines:
            m = RE_CLASS_MAP.search(ln)
            if m:
                base = m.group(1)
                break
        for i, ln in enumerate(lines):
            m = RE_METHOD_MAP.search(ln)
            if not m:
                continue
            path = (base + (m.group(2) or "")).replace("/api/v1", "", 1) or "/"
            key = routes.get((m.group(1).upper(), re.sub(r"\{[^}]+\}", "{}", path)))
            decl = next((j for j in range(i + 1, min(i + 8, len(lines))) if not lines[j].strip().startswith("@")), None)
            if key is None or decl is None:
                continue
            name = RE_JAVA_DECL.search(lines[decl])
            mid = idx.method(sf, name.group(1), i + 1, decl + 1) if name else None
            for d in doc_api.get(key, []):
                add(mid, d, "implements", "route-match", sf=sf, loc=f"L{decl + 1}")

    # 2~3. 주석에 적힌 API 번호 / REQ 번호
    for sf, lines in code_files():
        for i, ln in enumerate(lines):
            if not re.search(r"//|/\*|\*|#|--", ln):
                continue
            tgt = None
            for kind, rx in (("API", RE_API), ("A", RE_ADMIN)):
                for m in rx.finditer(ln):
                    for d in doc_api.get((kind, int(m.group(1))), []):
                        tgt = tgt or idx.target(sf, i + 1)
                        add(tgt, d, "references", "api-comment", sf=sf, loc=f"L{i + 1}")
            for m in RE_REQ.finditer(ln):
                for d in doc_req.get(m.group(1), []):
                    tgt = tgt or idx.target(sf, i + 1)
                    add(tgt, d, "references", "req-comment", sf=sf, loc=f"L{i + 1}")

    # 4. ERD 테이블 <- SQL 테이블, JPA 엔티티
    tables = {}
    for n in doc_node.values():
        if n["source_file"] == "docs/erd.md":
            m = re.match(r"([a-z][a-z0-9_]+)\b", n["label"])
            if m:
                tables[m.group(1)] = n["id"]
    for sf, nl in idx.by_file.items():
        if sf.endswith(".sql"):
            for _, n in nl:
                if n["label"] in tables:
                    add(n["id"], tables[n["label"]], "implements", "table-sql", sf=sf, loc=n.get("source_location"))
    for sf, lines in code_files():
        if not sf.endswith(".java"):
            continue
        for ln in lines:
            m = re.search(r'@Table\(\s*name\s*=\s*"([a-z0-9_]+)"', ln)
            if m and m.group(1) in tables:
                add(idx.named(sf, Path(sf).stem), tables[m.group(1)], "implements", "table-entity", sf=sf)

    # 5. 에러 코드 표 <- ErrorCode 상수
    err_sf = next((sf for sf in idx.by_file if sf.endswith("ErrorCode.java")), None)
    consts = {n["label"]: n["id"] for _, n in idx.by_file.get(err_sf, []) if re.fullmatch(r"[A-Z][A-Z0-9_]+", n["label"])}
    for n in doc_node.values():
        for tok in set(RE_ENUM_TOKEN.findall(n["label"])) | ({n["label"]} if n["label"] in consts else set()):
            if tok in consts:
                add(consts[tok], n["id"], "implements", "error-code", sf=err_sf)

    cover = {
        "API": f"{len({k for k in doc_api if k[0] == 'API'})}개 문서 API 중 연결된 것: "
               f"{len({d for l in links for k, ids in doc_api.items() if k[0] == 'API' for d in ids if l['target'] == d})}",
        "ADMIN": f"{len({k for k in doc_api if k[0] == 'A'})}개 문서 관리자 API 중 연결된 것: "
                 f"{len({d for l in links for k, ids in doc_api.items() if k[0] == 'A' for d in ids if l['target'] == d})}",
        "REQ": f"문서 REQ {len(doc_req)}개 중 연결된 것: {len({l['target'] for l in links if l['link'] == 'req-comment'})}",
        "TABLE": f"문서 테이블 {len(tables)}개 중 연결된 것: {len({l['target'] for l in links if l['link'].startswith('table')})}",
    }
    return links, stats, cover


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--stats", action="store_true", help="연결 통계만 출력하고 그래프는 쓰지 않는다")
    args = ap.parse_args()
    if not (OUT / "graph.json").exists() and not (OUT / "semantic-base.json").exists():
        sys.exit("graphify-out/graph.json 이 없다. 먼저 `/graphify .` 로 문서 추출을 한 번 돌린다.")

    base = load_semantic_base()
    det, ast = run_ast()
    nodes = list(ast["nodes"])
    seen = {n["id"] for n in nodes}
    for n in base["nodes"]:
        if n["id"] not in seen:
            seen.add(n["id"])
            nodes.append(n)
    links, stats, cover = build_links(nodes, Index(ast["nodes"]))
    print("연결 엣지:", dict(stats), "합계", len(links))
    for v in cover.values():
        print(" -", v)
    if args.stats:
        return

    ext = {"nodes": nodes, "edges": ast["edges"] + base["edges"] + links, "hyperedges": base["hyperedges"],
           "input_tokens": 0, "output_tokens": 0}
    G = build_from_json(ext, root=".", directed=False)
    com = cluster(G)
    coh = score_all(G, com)
    labels = {}
    old = base.get("labels", {})
    for c, ns in com.items():
        votes = collections.Counter(old[n] for n in ns if n in old)
        labels[c] = votes.most_common(1)[0][0] if votes else f"Community {c}"
    questions = suggest_questions(G, com, labels)
    if not to_json(G, com, str(OUT / "graph.json"), community_labels=labels):
        sys.exit("graph.json 을 줄이는 쓰기는 거부됐다 (기존 그래프가 더 큼).")
    report = generate(G, com, coh, labels, god_nodes(G), surprising_connections(G, com), det,
                      {"input": 0, "output": 0}, ".", suggested_questions=questions)
    (OUT / "GRAPH_REPORT.md").write_text(report, encoding="utf-8")
    (OUT / "links.json").write_text(json.dumps(links, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"그래프: 노드 {G.number_of_nodes()} · 엣지 {G.number_of_edges()} · 커뮤니티 {len(com)}")


if __name__ == "__main__":
    main()
