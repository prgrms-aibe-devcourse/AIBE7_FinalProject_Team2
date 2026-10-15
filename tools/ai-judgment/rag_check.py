"""생성 참고 자료 RAG 확인 도구 (BE-54, WBS 7.5.5). 실제 로컬 DB와 Gemini 임베딩으로 검색 결과 · 점수를 본다.

점수 하한 · 상한(rag-handover 5-4) · 양형기준 top_k · min_score를 정하려고 만든 도구다. 결과를 저장하지 않고 화면에만 쓴다.
사건번호 · 원문은 출력하지 않는다(사건번호는 해시로만 검색에 쓴다).

사용법 (tools/ai-judgment에서, GEMINI_API_KEY 필요, 로컬 DB는 bootRun으로 R__40 · R__41이 들어간 상태):
    python3 rag_check.py case examples/case_input.json [--source x.source_internal.json] [--incident-date 2014-03-01]
    python3 rag_check.py db-case 1            # DB에 있는 사건으로 검색 (자기 사건은 case_source 사건번호 해시로 뺀다)
    python3 rag_check.py sample --count 10    # 판례 요약을 검색어로 다른 판례를 찾아 점수 분포를 본다 (자기 사건 그룹 제외)
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


def cmd_db_case(args):
    db = db_config(args)
    row = json_row(f"""SELECT json_build_object(
  'title', c.title, 'crimeType', c.crime_type, 'overview', c.overview, 'incidentDate', c.incident_date,
  'sections', (SELECT coalesce(json_agg(json_build_object('title', s.title, 'content', s.content, 'data', s.data)
                                        ORDER BY s.display_order), '[]'::json)
               FROM case_section s WHERE s.case_id = c.id AND s.section_type NOT IN ('LAW_TERM', 'SUMMARY')),
  'sources', (SELECT coalesce(json_agg(json_build_object('caseNumber', cs.case_number)), '[]'::json)
              FROM case_source cs WHERE cs.case_id = c.id))
FROM legal_case c WHERE c.id = {int(args.case_id)};""", db)
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


def main(argv=None):
    parser = argparse.ArgumentParser(description="생성 참고 자료 RAG 확인 (BE-54)")
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--explore", action="store_true", help="하한 0 · 후보 20건으로 넓게 본다")
    common.add_argument("--prompt", action="store_true", help="프롬프트에 들어갈 참고 자료를 출력한다")
    common.add_argument("--indictment-date")
    common.add_argument("--incident-date")
    common.add_argument("--db-mode", choices=("docker", "psql"))
    common.add_argument("--container")
    common.add_argument("--db-user")
    common.add_argument("--database")
    common.add_argument("--db-url")
    sub = parser.add_subparsers(dest="command", required=True)
    case = sub.add_parser("case", parents=[common], help="사건 입력 파일로 검색")
    case.add_argument("case_input")
    case.add_argument("--source", help="source_internal.json (자기 사건 제외용 사건번호 · 발생일)")
    db_case = sub.add_parser("db-case", parents=[common], help="DB의 사건으로 검색")
    db_case.add_argument("case_id", type=int)
    sample = sub.add_parser("sample", parents=[common], help="판례끼리 검색해 점수 분포 보기")
    sample.add_argument("--count", type=int, default=10)
    args = parser.parse_args(argv)
    try:
        return {"case": cmd_case, "db-case": cmd_db_case, "sample": cmd_sample}[args.command](args) or 0
    except (LLMError, rag_context.RagUnavailable, rag_context.RagContextError, pipeline.PipelineError) as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
