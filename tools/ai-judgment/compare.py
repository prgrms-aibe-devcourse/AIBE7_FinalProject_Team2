"""generate.py가 쌓은 실행 기록으로 모델별 비교표를 만든다 (BE-30).

사용법:
    python3 compare.py out/runs/case                          # Markdown 표를 화면에
    python3 compare.py out/runs/case --out out/runs/case/comparison.md --csv out/runs/case/comparison.csv
    python3 compare.py out/runs/case --court cases/court_judgment_internal.json   # 실제 판결과 차이 (내부 전용)
    python3 compare.py out/runs/case --price openai:gpt-5=1.25,10                  # 100만 토큰당 입력,출력 단가(USD)

비교 항목
- 실행 · 호출 실패 · 파싱 실패 · 검증 통과 수, 평균 오류 · 경고 수
- 최종 선고 형벌 분포, 징역 개월 (최소 · 중앙값 · 최대), 집행유예 붙인 횟수, 권고 범위 안에 든 횟수
- 판단 요소 방향 일관성: 같은 모델의 회차끼리 요소마다 다수 의견과 같은 비율의 평균 (1에 가까울수록 매번 비슷하게 판단)
- 전체 다수 의견 일치율: 모든 모델 · 회차의 요소별 다수 의견과 같은 비율의 평균 (다른 모델들과 얼마나 비슷한지)
- 평균 입력 · 출력 · 합계 토큰, 평균 소요 시간, (단가를 주면) 회당 예상 비용
- --court를 주면 실제 판결과 형벌 종류 일치율 · 징역 개월 평균 차이 (내부 전용 — 이 결과는 공유 · 커밋하지 않는다)

요소 방향은 검증을 통과한 실행만으로 센다. 고르지 않은 요소는 '선택 안 함'이라는 값으로 센다.
"""

import argparse
import csv
import io
import statistics
import sys
from collections import Counter
from pathlib import Path

from common import NO_TERM_PENALTIES, final_penalty, load_json
from generate import RUN_SCHEMA

NOT_SELECTED = "-"


class CompareError(Exception):
    pass


def load_runs(batch_dir):
    """batch_dir 아래 run-NNN.json 기록을 모델별로 모은다 (모델 이름 순, 회차 순)."""
    batch_dir = Path(batch_dir)
    if not (batch_dir / "batch.json").exists():
        raise CompareError(f"{batch_dir}에 batch.json이 없습니다. generate.py로 만든 결과 폴더를 지정하세요")
    groups = {}
    for path in sorted(batch_dir.glob("*/run-*.json")):
        if path.name.endswith(".output.json"):
            continue
        record = load_json(path)
        if record.get("schema") != RUN_SCHEMA:
            continue
        groups.setdefault(record["modelSpec"], []).append(record)
    if not groups:
        raise CompareError(f"{batch_dir}에 실행 기록이 없습니다")
    for records in groups.values():
        records.sort(key=lambda r: r["runIndex"])
    return load_json(batch_dir / "batch.json"), dict(sorted(groups.items()))


def factor_directions(output):
    return {int(f["factorId"]): f.get("direction") for f in output.get("factors") or [] if "factorId" in f}


def _majority(values):
    """가장 많은 값. 동수면 정렬 순서로 첫 값을 고른다 (결과가 매번 같도록)."""
    counts = Counter(values)
    best = max(counts.values())
    return sorted(v for v, c in counts.items() if c == best)[0]


def direction_table(valid_records, factor_ids):
    """요소 ID → 실행별 방향 목록 (고르지 않았으면 NOT_SELECTED)."""
    table = {fid: [] for fid in factor_ids}
    for record in valid_records:
        directions = factor_directions(record["output"])
        for fid in factor_ids:
            table[fid].append(directions.get(fid, NOT_SELECTED))
    return table


def consistency(table):
    """요소별 (다수 의견과 같은 실행 수 / 실행 수)의 평균. 실행이 2개 미만이면 의미가 없어 None."""
    rates = []
    for values in table.values():
        if len(values) < 2:
            return None
        rates.append(Counter(values).most_common(1)[0][1] / len(values))
    return sum(rates) / len(rates) if rates else None


def agreement(table, consensus):
    matches = total = 0
    for fid, values in table.items():
        for value in values:
            total += 1
            matches += value == consensus[fid]
    return matches / total if total else None


def parse_prices(items):
    prices = {}
    for item in items or []:
        spec, sep, rest = item.rpartition("=")
        try:
            input_price, output_price = (float(x) for x in rest.split(","))
        except ValueError:
            raise CompareError(f"--price 형식은 '공급자:모델=입력단가,출력단가'입니다: {item!r}") from None
        if not sep or not spec:
            raise CompareError(f"--price 형식은 '공급자:모델=입력단가,출력단가'입니다: {item!r}")
        prices[spec] = (input_price, output_price)
    return prices


def _mean(values):
    values = [v for v in values if v is not None]
    return sum(values) / len(values) if values else None


def summarize(case, groups, court=None, prices=None):
    """모델별 요약 행 목록과 실행별 상세 행 목록을 돌려준다."""
    factor_ids = sorted(int(f["factorId"]) for f in case.get("factors", []))
    recommended = case.get("recommended")
    prices = prices or {}

    tables = {}
    all_valid = []
    for spec, records in groups.items():
        valid = [r for r in records if r["validation"]["ok"]]
        all_valid.extend(valid)
        tables[spec] = direction_table(valid, factor_ids)
    overall = direction_table(all_valid, factor_ids)
    consensus = {fid: _majority(values) for fid, values in overall.items() if values}

    rows, details = [], []
    for spec, records in groups.items():
        valid = [r for r in records if r["validation"]["ok"]]
        parsed = [r for r in records if r["output"] is not None]
        finals = [r["finalPenalty"] for r in valid]
        prison = [r["output"]["prisonMonths"] for r in valid
                  if r["finalPenalty"] == "PRISON" and isinstance(r["output"].get("prisonMonths"), int)]
        usages = [(r.get("meta") or {}).get("usage") or {} for r in records if not r["callError"]]
        input_tokens = _mean([u.get("inputTokens") for u in usages])
        output_tokens = _mean([u.get("outputTokens") for u in usages])
        row = {
            "model": spec,
            "runs": len(records),
            "callFailed": sum(1 for r in records if r["callError"]),
            "parseFailed": sum(1 for r in records if r["parseError"]),
            "valid": len(valid),
            "avgErrors": _mean([len(r["validation"]["errors"]) for r in parsed]),
            "avgWarnings": _mean([len(r["validation"]["warnings"]) for r in parsed]),
            "penalties": ", ".join(f"{p}×{n}" for p, n in sorted(Counter(finals).items())) or None,
            "prisonMin": min(prison) if prison else None,
            "prisonMedian": statistics.median(prison) if prison else None,
            "prisonMax": max(prison) if prison else None,
            "suspended": sum(1 for r in valid if r["output"].get("suspensionMonths") is not None),
            "inRecommended": (sum(1 for m in prison if recommended["minMonths"] <= m <= recommended["maxMonths"])
                              if recommended else None),
            "factorConsistency": consistency(tables[spec]) if valid else None,
            "consensusAgreement": agreement(tables[spec], consensus) if valid and consensus else None,
            "avgInputTokens": input_tokens,
            "avgOutputTokens": output_tokens,
            "avgTotalTokens": _mean([u.get("totalTokens") for u in usages]),
            "avgLatencySeconds": _mean([(r.get("meta") or {}).get("latencySeconds") for r in records]),
            "avgCostUsd": None,
        }
        if spec in prices and input_tokens is not None and output_tokens is not None:
            input_price, output_price = prices[spec]
            row["avgCostUsd"] = (input_tokens * input_price + output_tokens * output_price) / 1_000_000
        if court is not None:
            row.update(court_diff(court, valid))
        rows.append(row)

        for r in records:
            usage = (r.get("meta") or {}).get("usage") or {}
            output = r["output"] or {}
            details.append({
                "model": spec,
                "run": r["runIndex"],
                "status": ("호출 실패" if r["callError"] else "파싱 실패" if r["parseError"]
                           else "통과" if r["validation"]["ok"] else "검증 실패"),
                "errors": len(r["validation"]["errors"]),
                "warnings": len(r["validation"]["warnings"]),
                "penalty": r["finalPenalty"],
                "prisonMonths": output.get("prisonMonths"),
                "fineAmount": output.get("fineAmount"),
                "suspensionMonths": output.get("suspensionMonths"),
                "factorCount": len(output.get("factors") or []),
                "totalTokens": usage.get("totalTokens"),
                "latencySeconds": (r.get("meta") or {}).get("latencySeconds"),
            })
    return rows, details, consensus


def court_diff(court, valid):
    """실제 판결과의 차이 (내부 전용). 형벌은 최종 선고 형벌로 비교한다."""
    actual = final_penalty(court)
    same = [r for r in valid if r["finalPenalty"] == actual]
    diffs = []
    if actual == "PRISON" and court.get("prisonMonths") is not None:
        diffs = [abs(r["output"]["prisonMonths"] - court["prisonMonths"]) for r in same
                 if isinstance(r["output"].get("prisonMonths"), int)]
    return {
        "courtPenaltyMatch": len(same) / len(valid) if valid else None,
        "courtPrisonDiffAvg": _mean(diffs) if actual not in NO_TERM_PENALTIES else None,
    }


RESULT_COLUMNS = [
    ("model", "모델"),
    ("runs", "실행"),
    ("callFailed", "호출 실패"),
    ("parseFailed", "파싱 실패"),
    ("valid", "검증 통과"),
    ("avgErrors", "평균 오류"),
    ("avgWarnings", "평균 경고"),
    ("penalties", "최종 형벌 분포"),
    ("prisonMin", "징역 최소(개월)"),
    ("prisonMedian", "징역 중앙값"),
    ("prisonMax", "징역 최대"),
    ("suspended", "집행유예"),
    ("inRecommended", "권고 범위 안"),
    ("factorConsistency", "요소 방향 일관성"),
    ("consensusAgreement", "전체 다수 의견 일치율"),
]
COST_COLUMNS = [
    ("model", "모델"),
    ("avgInputTokens", "평균 입력 토큰"),
    ("avgOutputTokens", "평균 출력 토큰"),
    ("avgTotalTokens", "평균 합계 토큰"),
    ("avgLatencySeconds", "평균 소요(초)"),
    ("avgCostUsd", "회당 비용(USD)"),
]
COLUMNS = RESULT_COLUMNS + COST_COLUMNS[1:]
COURT_COLUMNS = [
    ("courtPenaltyMatch", "실제 판결 형벌 일치율"),
    ("courtPrisonDiffAvg", "실제 판결 징역 차이 평균(개월)"),
]
DETAIL_COLUMNS = [
    ("model", "모델"),
    ("run", "회차"),
    ("status", "상태"),
    ("errors", "오류"),
    ("warnings", "경고"),
    ("penalty", "최종 형벌"),
    ("prisonMonths", "징역(개월)"),
    ("fineAmount", "벌금(원)"),
    ("suspensionMonths", "집행유예(개월)"),
    ("factorCount", "고른 요소 수"),
    ("totalTokens", "합계 토큰"),
    ("latencySeconds", "소요(초)"),
]
RATE_KEYS = {"factorConsistency", "consensusAgreement", "courtPenaltyMatch"}


def _cell(key, value):
    if value is None:
        return "—"
    if key in RATE_KEYS:
        return f"{value:.0%}"
    if key == "avgCostUsd":
        return f"{value:.4f}"
    if isinstance(value, float):
        return f"{value:.1f}" if not value.is_integer() else str(int(value))
    return str(value).replace("|", "\\|")


def markdown_table(columns, rows):
    lines = ["| " + " | ".join(title for _, title in columns) + " |",
             "| " + " | ".join("---" for _ in columns) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(_cell(key, row.get(key)) for key, _ in columns) + " |")
    return "\n".join(lines)


def render_markdown(batch, rows, details, consensus, case, with_court):
    result_columns = RESULT_COLUMNS + (COURT_COLUMNS if with_court else [])
    labels = {int(f["factorId"]): f["label"] for f in case.get("factors", [])}
    consensus_lines = [f"- {fid}. {labels.get(fid, '')}: {'선택 안 함' if d == NOT_SELECTED else d}"
                       for fid, d in sorted(consensus.items())]
    parts = [
        "# 모델별 AI 판결 비교",
        "",
        f"- 사건: {batch.get('caseTitle')}",
        f"- 프롬프트 버전: {batch.get('promptVersion')}",
        "- 요소 방향 · 형벌 분포 · 징역은 검증을 통과한 실행만 센다. 토큰 합계는 공급자가 준 값을 그대로 쓴다(README 토큰 사용 기록).",
    ]
    if with_court:
        parts.append("- ⚠️ 실제 판결과 비교한 칸이 있다. **내부 전용**이므로 공유 · 커밋하지 않는다.")
    parts += ["", "## 판결 결과", "", markdown_table(result_columns, rows), "",
              "## 토큰 · 속도 · 비용", "", markdown_table(COST_COLUMNS, rows), "",
              "## 판단 요소 전체 다수 의견", "", *(consensus_lines or ["(검증 통과한 실행 없음)"]), "",
              "## 실행별 상세", "", markdown_table(DETAIL_COLUMNS, details), ""]
    return "\n".join(parts)


def render_csv(rows, with_court):
    columns = COLUMNS + (COURT_COLUMNS if with_court else [])
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow([title for _, title in columns])
    for row in rows:
        writer.writerow(["" if row.get(key) is None else row.get(key) for key, _ in columns])
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description="모델별 AI 판결 비교표를 만든다")
    parser.add_argument("batch_dir", help="generate.py 결과 폴더 (out/runs/<batch>)")
    parser.add_argument("--case", help="사건 입력 JSON (기본: 첫 실행 기록의 프롬프트와 같은 사건이어야 한다)")
    parser.add_argument("--court", help="실제 판결 파일 (내부 전용). 주면 실제 판결과의 차이 칸을 넣는다")
    parser.add_argument("--price", action="append", help="'공급자:모델=입력단가,출력단가' (100만 토큰당 USD). 여러 번 지정 가능")
    parser.add_argument("--out", help="Markdown 저장 경로 (없으면 화면에 출력)")
    parser.add_argument("--csv", help="모델별 요약 CSV 저장 경로")
    args = parser.parse_args()
    try:
        batch, groups = load_runs(args.batch_dir)
        case = load_json(args.case) if args.case else {}
        if args.case and case.get("title") != batch.get("caseTitle"):
            raise CompareError(f"--case의 사건({case.get('title')})이 묶음의 사건({batch.get('caseTitle')})과 다릅니다")
        if not args.case:
            case = _case_from_records(groups)
        court = load_json(args.court) if args.court else None
        rows, details, consensus = summarize(case, groups, court, parse_prices(args.price))
    except CompareError as e:
        print(f"[오류] {e}", file=sys.stderr)
        return 1
    markdown = render_markdown(batch, rows, details, consensus, case, court is not None)
    if args.out:
        Path(args.out).write_text(markdown, encoding="utf-8")
        print(f"Markdown: {args.out}")
    else:
        print(markdown)
    if args.csv:
        # 엑셀에서 한글이 깨지지 않게 BOM을 붙인다
        Path(args.csv).write_text(render_csv(rows, court is not None), encoding="utf-8-sig")
        print(f"CSV: {args.csv}", file=sys.stderr if not args.out else sys.stdout)
    return 0


def _case_from_records(groups):
    """--case가 없으면 판단 요소 목록을 실행 기록의 응답에서 모은다 (라벨은 비어 있다).

    권고 범위 칸은 사건 파일이 있어야 계산하므로 비어 있게 된다.
    """
    factor_ids = set()
    for records in groups.values():
        for record in records:
            if record["validation"]["ok"]:
                factor_ids.update(factor_directions(record["output"]))
    return {"factors": [{"factorId": fid, "label": ""} for fid in sorted(factor_ids)]}


if __name__ == "__main__":
    sys.exit(main())
