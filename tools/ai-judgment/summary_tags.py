"""요약어 후보 (BE-53, WBS 7.5.4): extract가 만든 요약어를 기준 목록과 의미로 비교해 비슷한 기존 요약어를 제안한다.

사건마다 같은 뜻의 요약어가 제각각("흉기 사용" / "흉기를 씀")으로 나오지 않게, 관리자가 고를 수 있는 후보와 점수를 남긴다.
**요약어를 자동으로 바꾸지 않는다** — 모델이 만든 값은 그대로 두고 제안만 한다(최종 판단은 관리자 후검수).

- 임베딩: llm.embed (gemini-embedding-001, 3072차원), task type SEMANTIC_SIMILARITY, 유사도는 cosine (ai-judgment-pipeline.md 8-1)
- 보내는 값: 요약어만(판단 요소 문장 · 판결문은 보내지 않는다). 기준 목록은 공개 값만 쓴다(summary_tags.json 설명)
- 결과: 보고서 factorExtras[].summaryTagCandidates · 맨 위 summaryTagCandidates(상태). 적재 · 관리자 화면 표시는 후속 이슈
- 기준 점수 minScore 0.85는 문서의 예시값이다(8-1 "점수 하한"). 실제 사건이 쌓이면 분포를 보고 정한다
"""

import copy
import json
import math
from pathlib import Path

from llm import embed

VOCABULARY_PATH = Path(__file__).resolve().parent / "summary_tags.json"
TASK_TYPE = "SEMANTIC_SIMILARITY"
EMBEDDING_MODEL = "gemini-embedding-001"
DEFAULT_TOP_K = 3
DEFAULT_MIN_SCORE = 0.85  # 예시값 (ai-judgment-pipeline.md 8-1). 확정 전
TAG_MAX_LENGTH = 20  # ERD factor.summary_tag varchar(20)
TOP_K_RANGE = (1, 10)


class SummaryTagError(Exception):
    """기준 목록 · 설정 오류."""


def default_embed(texts, task_type):
    """보고서에 적는 모델(EMBEDDING_MODEL)로 실제로 부른다. llm.embed 기본값이 바뀌어도 기록과 어긋나지 않게 (rag_context와 같은 방식)."""
    return embed(texts, model=EMBEDDING_MODEL, task_type=task_type)


def load_vocabulary(path=VOCABULARY_PATH):
    """기준 목록 → 요약어 목록. 형식이 틀리면 SummaryTagError (조용히 빈 목록으로 진행하지 않는다)."""
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as e:
        raise SummaryTagError(f"요약어 목록을 읽지 못했습니다: {e}") from e
    tags = data.get("tags") if isinstance(data, dict) else None
    if not isinstance(tags, list) or not tags:
        raise SummaryTagError("요약어 목록의 tags는 비어 있지 않은 배열이어야 합니다")
    for tag in tags:
        if not isinstance(tag, str) or not tag.strip() or len(tag) > TAG_MAX_LENGTH:
            raise SummaryTagError(f"요약어 목록에 {TAG_MAX_LENGTH}자 이내 문자열이 아닌 값이 있습니다")
    if len(set(tags)) != len(tags):
        raise SummaryTagError("요약어 목록에 같은 요약어가 두 번 있습니다")
    return list(tags)


def option_errors(options):
    """stages.extract.summaryTags 설정 검사 (enabled 제외). 오류 목록."""
    errors = []
    top_k = options.get("topK", DEFAULT_TOP_K)
    if not isinstance(top_k, int) or isinstance(top_k, bool) or not TOP_K_RANGE[0] <= top_k <= TOP_K_RANGE[1]:
        errors.append(f"stages.extract.summaryTags.topK는 {TOP_K_RANGE[0]} ~ {TOP_K_RANGE[1]} 정수다")
    min_score = options.get("minScore", DEFAULT_MIN_SCORE)
    if not isinstance(min_score, (int, float)) or isinstance(min_score, bool) or not 0 <= min_score <= 1:
        errors.append("stages.extract.summaryTags.minScore는 0 ~ 1 숫자다")
    return errors


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0


def find_candidates(tags, vocabulary, embed_fn=default_embed, top_k=DEFAULT_TOP_K, min_score=DEFAULT_MIN_SCORE):
    """요약어마다 비슷한 기준 요약어 상위 top_k개와 점수.

    tags: 판단 요소 순서의 요약어 목록. 돌려주는 값도 같은 순서다.
      candidates: [{tag, score}] 점수 높은 순 (소수 넷째 자리)
      inVocabulary: 요약어가 목록에 글자 그대로 있음
      suggested: 목록에 없는데 min_score 이상으로 비슷한 기준 요약어(관리자가 바꿀지 판단). 없으면 None
    임베딩은 한 번만 부른다(목록 + 요약어, 같은 글자는 한 번만 보냄). 요약어가 없으면 부르지 않는다.
    """
    if not tags:
        return []
    texts = list(dict.fromkeys(list(vocabulary) + list(tags)))
    vectors = dict(zip(texts, embed_fn(texts, task_type=TASK_TYPE)))
    results = []
    for tag in tags:
        scored = sorted(((cosine(vectors[tag], vectors[v]), v) for v in vocabulary), key=lambda x: (-x[0], x[1]))
        candidates = [{"tag": v, "score": round(score, 4)} for score, v in scored[:top_k]]
        in_vocabulary = tag in vocabulary
        best = candidates[0] if candidates else None
        suggested = best["tag"] if best and not in_vocabulary and best["score"] >= min_score else None
        results.append({"candidates": candidates, "inVocabulary": in_vocabulary, "suggested": suggested})
    return results


def annotate_report(report, vocabulary, embed_fn=default_embed, top_k=DEFAULT_TOP_K, min_score=DEFAULT_MIN_SCORE):
    """extract 보고서 사본에 요약어 후보를 더한다. 원본 report는 바꾸지 않는다.

    factorExtras[]마다 summaryTagCandidates를, 맨 위 summaryTagCandidates에 상태 · 기준을 남긴다
    (가치관 축 투표가 factorExtras에 valueAxisVotes를 더하는 것과 같은 자리, BE-48).
    """
    annotated = copy.deepcopy(report)
    extras = annotated.get("factorExtras") or []
    results = find_candidates([e["summaryTag"] for e in extras], vocabulary, embed_fn, top_k, min_score)
    for extra, result in zip(extras, results):
        extra["summaryTagCandidates"] = result
    annotated["summaryTagCandidates"] = {
        "status": "OK", "model": EMBEDDING_MODEL, "taskType": TASK_TYPE, "topK": top_k, "minScore": min_score,
        "vocabularySize": len(vocabulary), "suggested": sum(1 for r in results if r["suggested"]),
    }
    return annotated


def skipped_report(report, reason):
    """후보를 만들지 못했을 때의 보고서 사본 (상태만). 이전 실행의 후보가 남아 있으면 지운다."""
    annotated = copy.deepcopy(report)
    for extra in annotated.get("factorExtras") or []:
        extra.pop("summaryTagCandidates", None)
    annotated["summaryTagCandidates"] = {"status": "SKIPPED", "reason": reason}
    return annotated
