"""요약어 후보 (BE-53) 테스트. Gemini는 호출하지 않는다 (임베딩은 가짜 함수).

실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import copy
import json
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import pipeline  # noqa: E402
import summary_tags  # noqa: E402
from common import load_json, write_json  # noqa: E402
from summary_tags import (  # noqa: E402
    TASK_TYPE, SummaryTagError, annotate_report, find_candidates, load_vocabulary, option_errors, skipped_report,
)

SAMPLE_SEED = TOOL_DIR.parent.parent / "backend" / "src" / "main" / "resources" / "db" / "seed" / "R__seed_sample_case.sql"


class FakeEmbed:
    """글자 → 벡터 표. 받은 요청을 기록해 무엇을 보냈는지 확인한다."""

    def __init__(self, table):
        self.table, self.calls = table, []

    def __call__(self, texts, task_type):
        self.calls.append((list(texts), task_type))
        return [self.table[t] for t in texts]


VOCAB = ["흉기 사용", "피해 회복 공탁", "범행 인정 · 반성"]
TABLE = {
    "흉기 사용": [1.0, 0.0, 0.0],
    "피해 회복 공탁": [0.0, 1.0, 0.0],
    "범행 인정 · 반성": [0.0, 0.0, 1.0],
    "흉기를 씀": [0.98, 0.2, 0.0],         # 흉기 사용과 매우 비슷 (0.85 이상)
    "300만 원 공탁": [0.5, 0.7, 0.4],       # 피해 회복 공탁이 가장 비슷하지만 0.74로 0.85 미만
    "공탁하고 반성": [0.0, 0.7, 0.7],       # 피해 회복 공탁 · 범행 인정 · 반성과 똑같이 비슷 (1 · 2순위 차 0)
    "거의 공탁": [0.0, 0.7, 0.695],         # 1 · 2순위 차 약 0.005 (0.01 미만)
    "공탁 쪽": [0.0, 0.7, 0.6],             # 1 · 2순위 차 약 0.10 (애매하지 않음)
}


def report_with(tags):
    return {"status": "NEEDS_REVIEW", "factorExtras": [
        {"factorId": i, "preLabel": None, "summaryTag": t, "valueAxis": None} for i, t in enumerate(tags, 1)]}


class VocabularyFileTest(unittest.TestCase):
    def test_bundled_vocabulary_is_valid(self):
        tags = load_vocabulary()
        self.assertGreater(len(tags), 0)
        self.assertTrue(all(len(t) <= 20 for t in tags))

    def test_bundled_vocabulary_has_all_public_sample_case_tags(self):
        # 처음 값은 공개 가상 사건 시드의 요약어를 모두 담는다 (시드가 바뀌면 목록도 맞춘다)
        sql = SAMPLE_SEED.read_text(encoding="utf-8")
        block = re.search(r"ARRAY\[(.*?)\]", sql[sql.index("summary_tag"):], re.S).group(1)
        seed_tags = re.findall(r"'([^']+)'", block)
        self.assertEqual(len(seed_tags), 11)
        self.assertLessEqual(set(seed_tags), set(load_vocabulary()))

    def test_invalid_vocabulary_is_rejected(self):
        cases = {
            "빈 목록": {"tags": []},
            "중복": {"tags": ["반성", "반성"]},
            "20자 초과": {"tags": ["가" * 21]},
            "문자열 아님": {"tags": [1]},
            "형식 아님": ["반성"],
        }
        for name, data in cases.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "tags.json"
                path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
                with self.assertRaises(SummaryTagError):
                    load_vocabulary(path)


class FindCandidatesTest(unittest.TestCase):
    def test_scores_order_and_suggestion(self):
        fake = FakeEmbed(TABLE)
        results = find_candidates(["흉기를 씀", "300만 원 공탁", "흉기 사용"], VOCAB, fake, top_k=2, min_score=0.85)

        near, below, exact = results
        self.assertEqual([c["tag"] for c in near["candidates"]], ["흉기 사용", "피해 회복 공탁"])
        self.assertGreaterEqual(near["candidates"][0]["score"], 0.85)
        self.assertEqual(near["suggested"], "흉기 사용")          # 목록에 없고 0.85 이상 → 제안
        self.assertEqual(below["candidates"][0]["tag"], "피해 회복 공탁")
        self.assertIsNone(below["suggested"])                     # 가장 비슷해도 0.85 미만 → 제안 없음
        self.assertTrue(exact["inVocabulary"])
        self.assertIsNone(exact["suggested"])                     # 이미 목록에 있는 요약어는 제안하지 않는다
        self.assertEqual(exact["candidates"][0], {"tag": "흉기 사용", "score": 1.0})
        self.assertTrue(all(len(r["candidates"]) == 2 for r in results))

    def test_one_embedding_call_with_semantic_similarity_and_tags_only(self):
        fake = FakeEmbed(TABLE)
        find_candidates(["흉기를 씀", "흉기 사용"], VOCAB, fake)

        self.assertEqual(len(fake.calls), 1)
        texts, task_type = fake.calls[0]
        self.assertEqual(task_type, TASK_TYPE)
        self.assertEqual(TASK_TYPE, "SEMANTIC_SIMILARITY")
        # 보내는 것은 기준 목록 + 요약어뿐이고, 같은 글자는 한 번만 보낸다
        self.assertEqual(texts, VOCAB + ["흉기를 씀"])

    def test_default_embed_passes_recorded_model_and_task_type(self):
        # 보고서에 적는 모델로 실제로 부른다 (llm.embed 기본값이 바뀌어도 기록과 어긋나지 않게)
        with mock.patch.object(summary_tags, "embed", side_effect=lambda texts, **kw: [TABLE[t] for t in texts]) as fake:
            find_candidates(["흉기를 씀"], VOCAB)
        kwargs = fake.call_args.kwargs
        self.assertEqual((kwargs["model"], kwargs["task_type"]), (summary_tags.EMBEDDING_MODEL, "SEMANTIC_SIMILARITY"))

    def test_no_tags_no_embedding_call(self):
        fake = FakeEmbed(TABLE)
        self.assertEqual(find_candidates([], VOCAB, fake), [])
        self.assertEqual(fake.calls, [])
        annotated = annotate_report({"factorExtras": []}, VOCAB, fake)
        self.assertEqual((annotated["summaryTagCandidates"]["status"], annotated["summaryTagCandidates"]["suggested"]), ("OK", 0))
        self.assertEqual(fake.calls, [])


class ReviewDecisionTest(unittest.TestCase):
    """BE-53 리뷰 결정: minScore 0.90, 1 · 2순위 차 0.01 미만은 애매함(제안 안 함), 빈 요약어는 계산에서 뺀다."""

    def test_default_min_score_is_090_and_example_matches(self):
        self.assertEqual(summary_tags.DEFAULT_MIN_SCORE, 0.90)
        self.assertEqual(summary_tags.AMBIGUOUS_MARGIN, 0.01)
        example = load_json(TOOL_DIR / "pipeline.example.json")
        self.assertEqual(example["stages"]["extract"]["summaryTags"]["minScore"], summary_tags.DEFAULT_MIN_SCORE)
        self.assertEqual(pipeline.DEFAULT_CONFIG["stages"]["extract"]["summaryTags"]["minScore"], 0.90)

    def test_tie_is_ambiguous_and_not_suggested(self):
        # 점수 기준은 넘지만(0.7071 ≥ 0.5) 1 · 2순위가 같으면 관리자가 직접 고르게 제안하지 않는다
        tie, = find_candidates(["공탁하고 반성"], VOCAB, FakeEmbed(TABLE), top_k=3, min_score=0.5)
        self.assertTrue(tie["ambiguous"])
        self.assertIsNone(tie["suggested"])
        self.assertEqual(len(tie["candidates"]), 3)  # 후보 · 점수는 그대로 남긴다

    def test_margin_boundary(self):
        near, clear = find_candidates(["거의 공탁", "공탁 쪽"], VOCAB, FakeEmbed(TABLE), top_k=3, min_score=0.5)
        self.assertTrue(near["ambiguous"])            # 차 약 0.005 < 0.01
        self.assertIsNone(near["suggested"])
        self.assertFalse(clear["ambiguous"])          # 차 약 0.10
        self.assertEqual(clear["suggested"], "피해 회복 공탁")

    def test_ambiguity_ignores_top_k(self):
        # 후보를 1개만 남겨도 1 · 2순위 비교는 전체 목록 기준이다
        tie, = find_candidates(["공탁하고 반성"], VOCAB, FakeEmbed(TABLE), top_k=1, min_score=0.5)
        self.assertEqual(len(tie["candidates"]), 1)
        self.assertTrue(tie["ambiguous"])
        self.assertIsNone(tie["suggested"])

    def test_clear_match_is_not_ambiguous(self):
        near, exact = find_candidates(["흉기를 씀", "흉기 사용"], VOCAB, FakeEmbed(TABLE))
        self.assertFalse(near["ambiguous"])
        self.assertEqual(near["suggested"], "흉기 사용")  # 0.98 ≥ 0.90 (기본값)
        self.assertFalse(exact["ambiguous"])

    def test_min_score_uses_unrounded_score(self):
        # CodeRabbit #153: 0.89996은 표시가 0.9지만 기준 0.90 미만이라 제안하지 않는다. 0.90004는 표시 0.9 · 제안한다
        import math
        table = {"흉기 사용": [1.0, 0.0, 0.0], "피해 회복 공탁": [0.0, 1.0, 0.0], "범행 인정 · 반성": [0.0, 0.0, 1.0]}
        for raw, expected in ((0.89996, None), (0.90004, "흉기 사용")):
            table[f"tag-{raw}"] = [raw, math.sqrt(1 - raw * raw), 0.0]
        below, above = find_candidates(["tag-0.89996", "tag-0.90004"], VOCAB, FakeEmbed(table), min_score=0.90)
        self.assertEqual(below["candidates"][0]["score"], 0.9)    # 표시는 반올림 그대로
        self.assertIsNone(below["suggested"])
        self.assertEqual(above["candidates"][0]["score"], 0.9)
        self.assertEqual(above["suggested"], "흉기 사용")

    def test_missing_or_empty_summary_tag_does_not_stop(self):
        report = {"factorExtras": [{"factorId": 1, "summaryTag": "흉기를 씀"}, {"factorId": 2, "summaryTag": ""},
                                   {"factorId": 3}, {"factorId": 4, "summaryTag": None}]}
        fake = FakeEmbed(TABLE)
        annotated = annotate_report(report, VOCAB, fake)
        extras = annotated["factorExtras"]
        self.assertEqual(extras[0]["summaryTagCandidates"]["suggested"], "흉기 사용")
        for extra in extras[1:]:
            self.assertEqual(extra["summaryTagCandidates"],
                             {"candidates": [], "inVocabulary": False, "ambiguous": False, "suggested": None})
        self.assertEqual(fake.calls[0][0], VOCAB + ["흉기를 씀"])  # 빈 값은 보내지 않는다
        self.assertNotIn("summaryTag", report["factorExtras"][2])  # 원본은 그대로

    def test_all_empty_tags_no_embedding_call(self):
        fake = FakeEmbed(TABLE)
        results = find_candidates(["", None], VOCAB, fake)
        self.assertEqual([r["candidates"] for r in results], [[], []])
        self.assertEqual(fake.calls, [])

    def test_summary_counts_ambiguous(self):
        annotated = annotate_report(report_with(["흉기를 씀", "공탁하고 반성"]), VOCAB, FakeEmbed(TABLE))
        summary = annotated["summaryTagCandidates"]
        self.assertEqual((summary["suggested"], summary["ambiguous"], summary["ambiguousMargin"]), (1, 1, 0.01))


class AnnotateReportTest(unittest.TestCase):
    def test_annotate_adds_candidates_without_changing_tags(self):
        report = report_with(["흉기를 씀", "300만 원 공탁"])
        original = copy.deepcopy(report)

        annotated = annotate_report(report, VOCAB, FakeEmbed(TABLE))

        self.assertEqual(report, original)  # 원본은 그대로
        self.assertEqual([e["summaryTag"] for e in annotated["factorExtras"]], ["흉기를 씀", "300만 원 공탁"])  # 바꾸지 않음
        self.assertEqual(annotated["factorExtras"][0]["summaryTagCandidates"]["suggested"], "흉기 사용")
        summary = annotated["summaryTagCandidates"]
        self.assertEqual((summary["status"], summary["vocabularySize"], summary["suggested"], summary["taskType"]),
                         ("OK", 3, 1, "SEMANTIC_SIMILARITY"))

    def test_skipped_report_removes_stale_candidates(self):
        annotated = annotate_report(report_with(["흉기를 씀"]), VOCAB, FakeEmbed(TABLE))
        skipped = skipped_report(annotated, "키 없음")
        self.assertNotIn("summaryTagCandidates", skipped["factorExtras"][0])
        self.assertEqual(skipped["summaryTagCandidates"], {"status": "SKIPPED", "reason": "키 없음"})


class OptionErrorsTest(unittest.TestCase):
    def test_option_ranges(self):
        self.assertEqual(option_errors({}), [])
        self.assertEqual(option_errors({"topK": 3, "minScore": 0.85}), [])
        for options in ({"topK": 0}, {"topK": 11}, {"topK": True}, {"minScore": 1.5}, {"minScore": "0.8"}):
            with self.subTest(options=options):
                self.assertTrue(option_errors(options))

    def test_pipeline_validation_uses_options(self):
        # 실제로 쓰는 경로처럼 예시 설정을 기본값과 합쳐 검사한다 (load_config와 같은 _merge)
        config = pipeline._merge(pipeline.DEFAULT_CONFIG, load_json(TOOL_DIR / "pipeline.example.json"))
        pipeline.validate_config(config)  # 예시 설정(켜짐 · topK 3 · minScore 0.9)은 통과
        config["stages"]["extract"]["summaryTags"] = {"enabled": "yes", "topK": 0}
        with self.assertRaises(pipeline.PipelineError) as raised:
            pipeline.validate_config(config)
        self.assertIn("summaryTags.enabled", str(raised.exception))
        self.assertIn("summaryTags.topK", str(raised.exception))

    def test_default_is_off_and_example_is_on(self):
        self.assertFalse(pipeline.DEFAULT_CONFIG["stages"]["extract"]["summaryTags"]["enabled"])
        example = load_json(TOOL_DIR / "pipeline.example.json")
        self.assertTrue(example["stages"]["extract"]["summaryTags"]["enabled"])
        self.assertEqual(option_errors({k: v for k, v in example["stages"]["extract"]["summaryTags"].items()
                                        if k not in ("enabled", "_comment")}), [])


class AttachSummaryTagsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.report_path = Path(self.tmp.name) / "case.report.json"
        self.report = report_with(["흉기를 씀"])
        write_json(self.report_path, self.report)
        self.logs = []

    def tearDown(self):
        self.tmp.cleanup()

    def test_writes_candidates_into_report(self):
        with mock.patch.object(pipeline, "load_vocabulary", return_value=VOCAB):
            updated = pipeline.attach_summary_tags(self.report_path, self.report, {"topK": 2}, self.logs.append,
                                                   embed_fn=FakeEmbed(TABLE))
        saved = load_json(self.report_path)
        self.assertEqual(saved, updated)
        self.assertEqual(saved["summaryTagCandidates"]["topK"], 2)
        self.assertEqual(saved["factorExtras"][0]["summaryTagCandidates"]["suggested"], "흉기 사용")
        self.assertTrue(any("요약어 후보" in line for line in self.logs))

    def test_without_gemini_key_skips_and_keeps_extract(self):
        env = {k: v for k, v in os.environ.items() if k not in ("GEMINI_API_KEY", "GOOGLE_API_KEY")}
        with mock.patch.dict(os.environ, env, clear=True), \
                mock.patch.object(summary_tags, "embed", side_effect=AssertionError("호출하면 안 됨")):
            updated = pipeline.attach_summary_tags(self.report_path, self.report, {}, self.logs.append)
        self.assertEqual(updated["summaryTagCandidates"]["status"], "SKIPPED")
        self.assertEqual(load_json(self.report_path)["summaryTagCandidates"]["status"], "SKIPPED")
        self.assertTrue(any("extract는 계속" in line for line in self.logs))

    def test_embedding_failure_skips(self):
        def failing(texts, task_type):
            raise pipeline.LLMError("임베딩 차원이 3072이 아닙니다 (10)")
        with mock.patch.object(pipeline, "load_vocabulary", return_value=VOCAB):
            updated = pipeline.attach_summary_tags(self.report_path, self.report, {}, self.logs.append, embed_fn=failing)
        self.assertEqual(updated["summaryTagCandidates"]["status"], "SKIPPED")

    def test_broken_vocabulary_stops_pipeline(self):
        with mock.patch.object(pipeline, "load_vocabulary", side_effect=SummaryTagError("중복")):
            with self.assertRaises(pipeline.PipelineError):
                pipeline.attach_summary_tags(self.report_path, self.report, {}, self.logs.append,
                                             embed_fn=FakeEmbed(TABLE))


if __name__ == "__main__":
    unittest.main()
