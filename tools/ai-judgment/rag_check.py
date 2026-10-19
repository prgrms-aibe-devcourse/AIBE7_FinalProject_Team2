"""생성 참고 자료 RAG 확인 도구 (BE-54, WBS 7.5.5). 실제 로컬 DB와 Gemini 임베딩으로 검색 결과 · 점수를 본다.

점수 하한 · 상한(rag-handover 5-4) · 양형기준 top_k · min_score를 정하려고 만든 도구다. 결과를 저장하지 않고 화면에만 쓴다.
사건번호 · 원문은 출력하지 않는다(사건번호는 해시로만 검색에 쓴다).

사용법 (tools/ai-judgment에서, GEMINI_API_KEY 필요, 로컬 DB는 bootRun으로 R__40 · R__41이 들어간 상태):
    python3 rag_check.py case examples/case_input.json [--source x.source_internal.json] [--incident-date 2014-03-01]
    python3 rag_check.py db-case 1            # DB에 있는 사건으로 검색 (자기 사건은 case_source 사건번호 해시로 뺀다)
    python3 rag_check.py sample --count 10    # 판례 요약을 검색어로 다른 판례를 찾아 점수 분포를 본다 (자기 사건 그룹 제외)
    python3 rag_check.py self                 # 자기 사건 점수: 상한(maxScore)이 자기 사건 판결을 막는지 본다 (#143 리뷰)
공통 옵션: --explore(하한 0 · 후보 20건으로 넓게 보기), --prompt(프롬프트에 들어갈 참고 자료 출력),
          --db-mode docker|psql, --container, --db-user, --database, --db-url (기본은 파이프라인 stages.load.db 기본값)
"""

import argparse
import statistics
import sys

import pipeline
import rag_context
from build_prompt import render_similar_cases
from common import load_json
from llm import LLMError, embed


def add_db_arguments(parser):
    """로컬 DB 접속 옵션 (기본은 파이프라인 stages.load.db 기본값). db_case_input.py도 같이 쓴다."""
    parser.add_argument("--db-mode", choices=("docker", "psql"))
    parser.add_argument("--container")
    parser.add_argument("--db-user")
    parser.add_argument("--database")
    parser.add_argument("--db-url")


def db_config(args):
    db = dict(pipeline.DEFAULT_CONFIG["stages"]["load"]["db"])
    for key, value in (("mode", args.db_mode), ("container", args.container), ("user", args.db_user),
                       ("database", args.database), ("url", args.db_url)):
        if value:
            db[key] = value
    return db


def json_row(sql, db):
    return rag_context.parse_json_output(pipeline.query_db(sql, db))


def query_embedder(texts, task_type):
    return embed(texts, model=rag_context.EMBEDDING_MODEL, task_type=task_type, dimensions=rag_context.EMBEDDING_DIM)


def options(args):
    overrides = {"precedent": {}, "guideline": {}}
    if args.explore:
        overrides["precedent"].update(minScore=0.0, candidates=20, topK=20)
        overrides["guideline"].update(minScore=0.0, topK=12)
    return rag_context.options_with(overrides)


def print_candidates(candidates, guideline_id, opts):
    print(f"\n유사 판례 후보 {len(candidates)}건 (하한 {opts['precedent']['minScore']} 이상, 상한 {opts['precedent']['maxScore']}):")
    print("  점수    판례 id  그룹                 같은 버전  죄명 · 심급 · 형량")
    for item in candidates:
        same = "예" if guideline_id is not None and item.get("guidelineId") == guideline_id else ""
        flag = "  ⚠ 상한 이상(자기 사건 의심)" if item["score"] >= opts["precedent"]["maxScore"] else ""
        print(f"  {item['score']:.4f}  {item['id']:>7}  {str(item['caseGroup'])[:20]:<20} {same:<8}  "
              f"{item.get('chargeName') or ''} · {item.get('courtLevel') or ''} · {rag_context.penalty_text(item)}{flag}")


def run_case(case, sources, args, indictment_date=None, incident_date=None):
    db = db_config(args)
    opts = options(args)
    text = rag_context.query_text(case)
    category = rag_context.GUIDELINE_CATEGORIES.get(case.get("crimeType"))
    at, basis = rag_context.base_date(indictment_date, incident_date)
    hashes = rag_context.own_case_hashes(sources)
    print(f"검색어 {len(text)}자 · 범죄 {case.get('crimeType')} · 기준일 종류 {basis or '없음'} · 자기 사건 해시 {len(hashes)}개")
    vector = query_embedder([text], task_type="RETRIEVAL_QUERY")[0]
    result = rag_context.parse_query_result(pipeline.query_db(
        rag_context.build_query_sql(vector, case.get("crimeType"), category, at, hashes, opts), db))
    guideline = result.get("guideline")
    guideline_id = guideline["id"] if guideline else None
    print(f"\n양형기준 버전: {guideline['versionName'] if guideline else '없음'} · 형량범위 {len(result['ranges'])}개")
    print(f"양형인자 · 정의 검색 {len(result['factors'])}건 (하한 {opts['guideline']['minScore']}):")
    for factor in result["factors"]:
        print(f"  {factor['score']:.4f}  [{factor['section']}] {factor.get('subject') or ''}")
    print_candidates(result["precedents"], guideline_id, opts)
    references, report = rag_context.build_references(
        case, sources, indictment_date=indictment_date, incident_date=incident_date, options=opts,
        embedder=lambda texts, task_type: [vector for _ in texts], runner=lambda sql: pipeline.query_db(sql, db),
        log=lambda *a: None)
    print(f"\n결과: {report['status']} · 쓰는 판례 {len(report['precedents'])}건 · 대체 사유 {report['fallbackReasons'] or '없음'}")
    if args.prompt:
        print("\n=== 프롬프트 '양형기준' ===\n" + (references.get("sentencingGuideline") or "(제공되지 않음)"))
        print("\n=== 프롬프트 '유사 판례' ===\n" + render_similar_cases(references.get("similarCases")))


def cmd_case(args):
    case = load_json(args.case_input)
    source = load_json(args.source) if args.source else {"sources": []}
    run_case(case, source.get("sources") or [], args, indictment_date=args.indictment_date,
             incident_date=args.incident_date or source.get("incidentDate"))


def db_case_sql(case_id):
    return f"""SELECT json_build_object(
  'title', c.title, 'crimeType', c.crime_type, 'overview', c.overview, 'incidentDate', c.incident_date,
  'sections', (SELECT coalesce(json_agg(json_build_object('title', s.title, 'content', s.content, 'data', s.data)
                                        ORDER BY s.display_order), '[]'::json)
               FROM case_section s WHERE s.case_id = c.id AND s.section_type NOT IN ('LAW_TERM', 'SUMMARY')),
  'sources', (SELECT coalesce(json_agg(json_build_object('caseNumber', cs.case_number)), '[]'::json)
              FROM case_source cs WHERE cs.case_id = c.id))
FROM legal_case c WHERE c.id = {int(case_id)};"""


def cmd_db_case(args):
    db = db_config(args)
    row = json_row(db_case_sql(args.case_id), db)
    print(f"사건 {args.case_id}: {row['title']}")
    run_case(row, row["sources"], args, indictment_date=args.indictment_date,
             incident_date=args.incident_date or row.get("incidentDate"))


def cmd_sample(args):
    """판례 요약을 검색어로 삼아 다른 판례를 찾는다(자기 사건 그룹 제외). 실제 사건 개요 ↔ 판례 점수 분포를 대신 본다."""
    db = db_config(args)
    rows = json_row(f"""SELECT coalesce(json_agg(t ORDER BY t.id), '[]'::json) FROM (
  SELECT id, case_group, facts_summary FROM rag_precedent WHERE NOT on_hold AND crime_type = 'MURDER'
  ORDER BY md5(id::text) LIMIT {int(args.count)}) t;""", db)
    if not rows:
        print("판례가 없습니다 (bootRun으로 R__40이 들어갔는지 확인)")
        return 1
    vectors = query_embedder([r["facts_summary"] for r in rows], task_type="RETRIEVAL_QUERY")
    top1, top5 = [], []
    print(f"판례 {len(rows)}건으로 검색 (각자 자기 사건 그룹은 뺌):")
    for row, vector in zip(rows, vectors):
        found = json_row(f"""SELECT coalesce(json_agg(s.score ORDER BY s.score DESC), '[]'::json)
FROM rag_precedent_search({rag_context.sql_vector(vector)}, 'MURDER', NULL,
                          ARRAY[{rag_context.sql_text(row['case_group'])}]::text[], 5, 0.0) s;""", db)
        scores = [round(s, 4) for s in found]
        if scores:
            top1.append(scores[0])
            top5.append(scores[-1])
        print(f"  판례 {row['id']:>5}: {scores}")

    def summary(name, values):
        if values:
            print(f"  {name}: 최소 {min(values):.4f} · 중앙 {statistics.median(values):.4f} · 최대 {max(values):.4f}")

    print("\n분포:")
    summary("1위 점수", top1)
    summary("5위 점수", top5)
    print("\n참고: 하한은 '5위 점수' 아래쪽, 상한(자기 사건 의심)은 '1위 점수' 최대보다 위로 잡는다. "
          "판례끼리라 실제 사건 개요 ↔ 판례보다 점수가 높게 나올 수 있으니 db-case 결과와 함께 본다.")
    return 0


def self_scores(vector, where_sql, db):
    """검색어 벡터와 조건에 맞는 판례(자기 사건)의 점수. 검색 함수의 자기 사건 제외를 거치지 않고 직접 잰다."""
    return json_row(f"""SELECT coalesce(json_agg(json_build_object('id', t.id, 'score', t.score) ORDER BY t.score DESC), '[]'::json)
FROM (SELECT p.id, 1 - (p.embedding <=> {rag_context.sql_vector(vector)}) AS score FROM rag_precedent p WHERE {where_sql}) t;""", db)


def cmd_self(args):
    """자기 사건 점수를 잰다 (#143 리뷰). 사건번호 해시가 없으면 점수 상한(maxScore)만 자기 사건 판결을 막으므로, 상한이 그 점수보다
    낮은지 본다. 두 가지를 잰다.
      1) 같은 사건의 다른 판결: 사건 그룹이 2건 이상인 판례끼리(1심 ↔ 항소심 · 사본). 한 판결의 요약을 검색어로 같은 그룹 판례 점수
      2) DB 사건 → 자기 판례: case_source 사건번호 해시가 판례와 겹치는 사건이 있으면 사건 개요(검색어)로 자기 판례 점수
    사건번호 · 본문은 출력하지 않는다."""
    db = db_config(args)
    max_score = options(args)["precedent"]["maxScore"]
    scores = []
    rows = json_row("""SELECT coalesce(json_agg(t ORDER BY t.case_group, t.id), '[]'::json) FROM (
  SELECT id, case_group, facts_summary FROM rag_precedent WHERE crime_type = 'MURDER' AND case_group IN (
    SELECT case_group FROM rag_precedent WHERE crime_type = 'MURDER' GROUP BY case_group HAVING count(*) > 1)) t;""", db)
    print(f"1) 같은 사건의 다른 판결 (그룹 안 판례 {len(rows)}건):")
    vectors = query_embedder([r["facts_summary"] for r in rows], task_type="RETRIEVAL_QUERY") if rows else []
    for row, vector in zip(rows, vectors):
        found = self_scores(vector, f"p.case_group = {rag_context.sql_text(row['case_group'])} AND p.id <> {int(row['id'])}", db)
        scores += [f["score"] for f in found]
        print(f"  판례 {row['id']:>5} → 같은 그룹 {[(f['id'], round(f['score'], 4)) for f in found]}")

    cases = json_row("""SELECT coalesce(json_agg(c.id ORDER BY c.id), '[]'::json) FROM legal_case c
WHERE EXISTS (SELECT 1 FROM case_source cs WHERE cs.case_id = c.id);""", db)
    print(f"\n2) DB 사건 → 자기 판례 (원본 판결문이 있는 사건 {len(cases)}건):")
    matched = 0
    for case_id in cases:
        case = json_row(db_case_sql(case_id), db)
        hashes = rag_context.own_case_hashes(case["sources"])
        if not hashes:
            continue
        hash_sql = rag_context.sql_text_array(hashes)
        where = f"p.case_group IN (SELECT case_group FROM rag_precedent WHERE case_number_hashes && {hash_sql})"
        if not json_row(f"SELECT json_build_array(count(*)) FROM rag_precedent p WHERE {where};", db)[0]:
            continue
        matched += 1
        vector = query_embedder([rag_context.query_text(case)], task_type="RETRIEVAL_QUERY")[0]
        found = self_scores(vector, where, db)
        scores += [f["score"] for f in found]
        print(f"  사건 {case_id:>5} → 자기 판례 {[(f['id'], round(f['score'], 4)) for f in found]}")
    if not matched:
        print("  자기 판례가 저장소에 있는 사건이 없습니다")

    print()
    if not scores:
        print("잴 수 있는 자기 사건 점수가 없습니다")
        return 0
    lowest = min(scores)
    print(f"자기 사건 점수: 최소 {lowest:.4f} · 중앙 {statistics.median(scores):.4f} · 최대 {max(scores):.4f} ({len(scores)}개)")
    blocked = sum(1 for s in scores if s >= max_score)
    print(f"상한 {max_score} 이상(상한이 막음) {blocked}개 / 미만(상한으로는 못 막음) {len(scores) - blocked}개")
    print("참고: 상한 아래 자기 사건이 있으면 상한은 두 번째 방어선이 되지 못한다. 그래서 사건번호가 없으면 유사 판례를 붙이지 않는다")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description="생성 참고 자료 RAG 확인 (BE-54)")
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--explore", action="store_true", help="하한 0 · 후보 20건으로 넓게 본다")
    common.add_argument("--prompt", action="store_true", help="프롬프트에 들어갈 참고 자료를 출력한다")
    common.add_argument("--indictment-date")
    common.add_argument("--incident-date")
    add_db_arguments(common)
    sub = parser.add_subparsers(dest="command", required=True)
    case = sub.add_parser("case", parents=[common], help="사건 입력 파일로 검색")
    case.add_argument("case_input")
    case.add_argument("--source", help="source_internal.json (자기 사건 제외용 사건번호 · 발생일)")
    db_case = sub.add_parser("db-case", parents=[common], help="DB의 사건으로 검색")
    db_case.add_argument("case_id", type=int)
    sample = sub.add_parser("sample", parents=[common], help="판례끼리 검색해 점수 분포 보기")
    sample.add_argument("--count", type=int, default=10)
    sub.add_parser("self", parents=[common], help="자기 사건 점수 (상한 근거)")
    args = parser.parse_args(argv)
    try:
        return {"case": cmd_case, "db-case": cmd_db_case, "sample": cmd_sample, "self": cmd_self}[args.command](args) or 0
    except (LLMError, rag_context.RagUnavailable, rag_context.RagContextError, pipeline.PipelineError) as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
