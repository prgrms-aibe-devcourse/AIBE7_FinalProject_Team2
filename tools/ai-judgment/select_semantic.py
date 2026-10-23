"""select 판결 이유 임베딩 유사도 (BE-55): 회차 선택(consensus)에 판결 이유 본문의 의미 유사도를 더한다.

판단 요소 방향(consensus의 기존 기준, pipeline.py의 direction_table)은 "유리 · 불리 · 선택 안 함" 3값짜리
라벨이다. 같은 사건을 3회 생성하면 방향은 전부 같은데(동점) 판결 이유(reasoning) 본문의 설득력 · 구체성은
회차마다 다른 경우가 흔하다 — 실제로 BE-76 비교 시험에서 "요소 방향은 13/13 그대로인데 형량은 회차마다
움직였다"는 결과가 나왔다(docs/rag-handover.md 5-7). 이때 지금 코드는 "경고 적은 · 앞선 회차"로 떨어져
사실상 1번 회차를 그대로 쓰는 셈이 된다.

이 모듈은 reasoning 본문을 임베딩해 "다른 회차들과 가장 비슷한(= 가장 가운데에 있는) 회차"에 가산점을
준다 — 혼자 튀는 답(환각 위험)보다 여러 회차가 수렴한 내용을 우선한다는 뜻이다(medoid와 같은 생각).

- 임베딩: llm.embed(gemini-embedding-001, 3072차원), task type SEMANTIC_SIMILARITY, 유사도는 cosine
  (summary_tags.py와 같은 패턴 — BE-53)
- 점수: 후보마다 "다른 후보 전원과의 평균 코사인 유사도"(0~1에 가깝다). 후보가 1개뿐이거나 reasoning이
  비어 있으면 비교 의미가 없어 None을 돌려준다(호출부가 가중치 없이 기존 방식으로 선택한다)
- 가중치(stages.select.semanticWeight, 기본 0)만큼 기존 요소-방향 일치율과 섞는다. 0이면 이 모듈을 아예
  부르지 않아 기존 동작과 100% 같다(회귀 없음). BE-55 이슈 설명의 예시값은 0.3이지만, 어떤 값이 맞는지는
  샘플로 비교해 팀이 정한다(docs/decisions/BE-55.md) — 그래서 기본값은 0으로 두고 켜는 쪽을 선택하게 했다
- 임베딩 호출이 실패하면(키 없음 · 한도 초과 등) RAG · 요약어 후보(BE-53)와 같은 정책: 조용히 건너뛰고
  기존 방식(요소 방향 일치율)으로 선택한다 — 사건 등록 전체를 실패시키지 않는다
"""

import math

from llm import embed

EMBEDDING_MODEL = "gemini-embedding-001"
TASK_TYPE = "SEMANTIC_SIMILARITY"
DEFAULT_WEIGHT = 0.0  # 기존 동작과 같음(끔). 샘플 검증 뒤 팀이 올릴 값을 stages.select.semanticWeight로 켠다
WEIGHT_RANGE = (0.0, 1.0)


def option_errors(select_options):
    """stages.select.semanticWeight 설정 검사(strategy · run · model 제외). 오류 목록."""
    errors = []
    weight = select_options.get("semanticWeight", DEFAULT_WEIGHT)
    if not isinstance(weight, (int, float)) or isinstance(weight, bool) or not WEIGHT_RANGE[0] <= weight <= WEIGHT_RANGE[1]:
        errors.append(f"stages.select.semanticWeight는 {WEIGHT_RANGE[0]} ~ {WEIGHT_RANGE[1]} 숫자다")
    return errors


def default_embed(texts):
    """보고서에 적는 모델(EMBEDDING_MODEL)로 실제로 부른다. llm.embed 기본값이 바뀌어도 기록과 어긋나지 않게
    (summary_tags.default_embed · rag_context.py와 같은 방식)."""
    return embed(texts, model=EMBEDDING_MODEL, task_type=TASK_TYPE)


def reasoning_similarity_scores(candidates, embed_fn=default_embed):
    """후보 회차(candidates, select_run의 유효 회차 목록)의 reasoning 본문으로 유사도 점수를 낸다.

    돌려주는 값: id(candidate) → 0~1 점수(다른 후보들과의 평균 코사인 유사도). 후보가 2개 미만이거나
    reasoning이 비어 있는 후보가 하나라도 있으면 None(호출부가 가중치 없이 진행하라는 신호).
    embed_fn은 테스트에서 네트워크 대신 넣는 함수다(텍스트 목록 → 벡터 목록).
    """
    if len(candidates) < 2:
        return None
    texts = []
    for record in candidates:
        reasoning = (record.get("output") or {}).get("reasoning")
        if not isinstance(reasoning, str) or not reasoning.strip():
            return None
        texts.append(reasoning)
    vectors = embed_fn(texts)
    if len(vectors) != len(candidates):
        raise ValueError(f"임베딩 개수가 후보 수와 다릅니다 (후보 {len(candidates)}개, 임베딩 {len(vectors)}개)")
    norms = [math.sqrt(sum(v * v for v in vec)) for vec in vectors]
    scores = {}
    for i, record in enumerate(candidates):
        others = [j for j in range(len(candidates)) if j != i]
        similarities = [_cosine(vectors[i], vectors[j], norms[i], norms[j]) for j in others]
        scores[id(record)] = sum(similarities) / len(similarities)
    return scores


def _cosine(a, b, norm_a, norm_b):
    dot = sum(x * y for x, y in zip(a, b))
    return dot / (norm_a * norm_b) if norm_a and norm_b else 0.0
