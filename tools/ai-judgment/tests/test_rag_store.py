"""AI 판결 RAG 판례 저장소 적재 도구 테스트 (BE-51). 네트워크 · DB를 쓰지 않는다.

판결문 원문 · 사건번호 · 비공개 메타데이터가 임베딩 요청에 들어가지 않는지, 남은 개인정보가 있으면 저장하지 않는지를 확인한다.
실행: tools/ai-judgment에서 `python3 -m unittest discover tests`
"""

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

TOOL_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_DIR))

import llm  # noqa: E402
import rag_store  # noqa: E402
from rag_store import RagStoreError  # noqa: E402

CASE_NUMBER = "2019고합123"
RAW_TEXT = (f"서울중앙지방법원 {CASE_NUMBER} 살인\n범 죄 사 실\n피고인 A는 2019. 3. 28. 02:50경 서울 강남구 테헤란로 123 "
            "피해자의 집에서 피해자를 흉기로 찔러 살해하였다.\n양형의 이유\n계획적 범행이다.")


def candidate(source="law.go.kr", ref="1", group="G001", **changes):
    row = {"source": source, "sourceId": ref, "caseNumber": CASE_NUMBER if source == "law.go.kr" else None,
           "court": "서울중앙지방법원", "decidedAt": "20190910" if source == "law.go.kr" else None, "level": "1심",
           "type": "살인", "incidentDate": "2019-03-28", "guideline": "2013 수정 (2013. 4. 22. 의결)",
           "beforePenaltyRevision": False, "partlyAcquitted": False, "concurrent": False, "checks": [],
           "caseGroup": group, "passed": True, "duplicateOf": None}
    row.update(changes)
    return row


def good_output(**changes):
    output = {
        "factsSummary": "피고인은 오래 함께 지낸 피해자와 다툰 뒤 범행 당일 새벽 자택에서 흉기로 피해자를 여러 차례 찔러 숨지게 했다.",
        "sentencingReason": "범행 방법이 잔혹하고 유족을 위로할 조치를 하지 않았다. 피고인이 잘못을 뉘우치는 점은 참작한다.",
        "statutes": ["형법 제250조 제1항"],
        "sentence": {"penaltyType": "PRISON", "prisonMonths": 180, "suspensionMonths": None, "fineAmount": None},
        "sentencingFactors": {"aggravating": ["잔혹한 범행 방법"], "mitigating": ["반성"]},
        "notes": [],
    }
    output.update(changes)
    return output


def fake_caller(*outputs):
    """차례로 응답하는 가짜 모델. 받은 (system, user)를 calls에 남긴다."""
    calls = []

    def caller(spec, system, user, **options):
        calls.append({"spec": spec, "system": system, "user": user})
        output = outputs[min(len(calls), len(outputs)) - 1]
        text = output if isinstance(output, str) else json.dumps(output, ensure_ascii=False)
        return llm.LLMResult(text, "openai", spec.partition(":")[2], usage={"totalTokens": 10})

    caller.calls = calls
    return caller


def fake_embed_http(dim=rag_store.EMBEDDING_DIM):
    """batchEmbedContents 가짜. 받은 요청 본문을 bodies에 남긴다."""
    bodies = []

    def http(url, headers, body, timeout):
        bodies.append({"url": url, "body": body})
        return {"embeddings": [{"values": [0.001 * (i + 1)] * dim} for i, _ in enumerate(body["requests"])]}

    http.bodies = bodies
    return http


class TargetsTest(unittest.TestCase):
    def test_targets_keepsPassedNonDuplicates_withStableGroupAndHash(self):
        rows = [candidate(ref="20", group="G007"), candidate(source="LBox", ref="5", group="G007"),
                candidate(ref="30", group="G008", passed=False), candidate(source="LBox", ref="6", duplicateOf="law.go.kr:20")]
        items = rag_store.targets(rows)
        self.assertEqual([t["key"] for t in items], ["LAW_GO_KR-20", "LBOX-5"])
        # 그룹 ID는 실행마다 바뀌는 G007이 아니라 그룹에서 가장 앞선 판례 키다
        self.assertEqual({t["caseGroup"] for t in items}, {"LAW_GO_KR-20"})
        self.assertEqual(items[0]["caseNumberHashes"], [rag_store.case_number_hash(CASE_NUMBER)])
        self.assertRegex(items[0]["caseNumberHashes"][0], r"^[0-9a-f]{64}$")
        self.assertEqual(items[1]["caseNumberHashes"], [])
        self.assertEqual(items[0]["decidedAt"], "2019-09-10")
        self.assertEqual(items[0]["courtLevel"], "FIRST")
        self.assertFalse(items[0]["flags"]["verified"])
        # 원래 사건번호 · 법원명은 적재 대상에 남지 않는다
        self.assertNotIn(CASE_NUMBER, json.dumps(items, ensure_ascii=False))
        self.assertNotIn("서울중앙지방법원", json.dumps(items, ensure_ascii=False))

    def test_case_number_hashes_splitsMergedCaseNumbers(self):
        merged = rag_store.case_number_hashes("2014고13, 2014고14(병합)")
        self.assertEqual(merged, sorted([rag_store.case_number_hash("2014고13"), rag_store.case_number_hash("2014고14")]))
        # 대상 사건 case_source에 번호가 하나만 있어도 같은 해시가 들어 있어야 자기 사건으로 빠진다
        self.assertIn(rag_store.case_number_hash("2014고13"), merged)
        self.assertEqual(rag_store.case_number_hashes(" 2016 고합 79 "), [rag_store.case_number_hash("2016고합79")])
        self.assertEqual(rag_store.case_number_hashes("2008재고합1"), [rag_store.case_number_hash("2008재고합1")])
        self.assertEqual(rag_store.case_number_hashes("특수번호-1"), [rag_store.case_number_hash("특수번호-1")])  # 형식 밖
        self.assertEqual(rag_store.case_number_hashes(None), [])

    def test_targets_keepsCheckFlags(self):
        items = rag_store.targets([candidate(checks=["발생일과 선고일이 3년 넘게 차이 남"])])
        self.assertEqual(items[0]["flags"]["checks"], ["발생일과 선고일이 3년 넘게 차이 남"])

    def test_targets_unknownLevel_isError(self):
        with self.assertRaisesRegex(RagStoreError, "심급"):
            rag_store.targets([candidate(level="기타")])


class BuildTest(unittest.TestCase):
    target = rag_store.targets([candidate()])[0]

    def test_build_prompt_masksCaseNumberCourtAddress_andWrapsJudgment(self):
        system, user = rag_store.build_prompt(RAW_TEXT)
        self.assertIn("<judgment>", user)
        self.assertIn("데이터", system)
        for value in (CASE_NUMBER, "서울중앙지방법원", "테헤란로 123"):
            self.assertNotIn(value, user)

    def test_build_one_valid(self):
        caller = fake_caller(good_output())
        result = rag_store.build_one(self.target, RAW_TEXT, "openai:m", caller=caller)
        self.assertEqual(result["result"]["sentence"]["prisonMonths"], 180)
        self.assertEqual(result["attempts"], 1)
        self.assertNotIn(CASE_NUMBER, caller.calls[0]["user"])

    def test_build_one_residualPersonalInfo_retriedThenRejected(self):
        leaked = good_output(factsSummary="피고인은 2019. 3. 28. 서울 강남구 피해자의 집에서 피해자를 살해했다.")
        caller = fake_caller(leaked)
        with self.assertRaisesRegex(RagStoreError, "정확한 날짜 의심"):
            rag_store.build_one(self.target, RAW_TEXT, "openai:m", caller=caller, max_retries=1)
        self.assertEqual(len(caller.calls), 2)
        self.assertIn("앞 응답의 문제", caller.calls[1]["user"])

    def test_build_one_residualFixedOnRetry_isAccepted(self):
        leaked = good_output(sentencingReason="피고인(45세)은 반성한다.")
        caller = fake_caller(leaked, good_output())
        result = rag_store.build_one(self.target, RAW_TEXT, "openai:m", caller=caller)
        self.assertEqual(result["attempts"], 2)

    def test_validate_result_errors(self):
        cases = [
            ("factsSummary가 비어", good_output(factsSummary=" ")),
            ("sentencingReason가 4000자", good_output(sentencingReason="가" * 4001)),
            ("penaltyType", good_output(sentence={"penaltyType": "JAIL"})),
            ("prisonMonths가 없습니다", good_output(sentence={"penaltyType": "PRISON", "prisonMonths": None})),
            ("prisonMonths는 null 또는 양의 정수", good_output(sentence={"penaltyType": "PRISON", "prisonMonths": 1.5})),
            ("sentencingFactors", good_output(sentencingFactors={"aggravating": "잔혹"})),
            ("statutes", good_output(statutes="형법 제250조")),
            ("chargeName", good_output(chargeName=["살인"])),
        ]
        for message, output in cases:
            with self.subTest(message):
                self.assertTrue(any(message in e for e in rag_store.validate_result(output)),
                                rag_store.validate_result(output))
        self.assertEqual(rag_store.validate_result(good_output(sentence=None)), [])

    def test_run_build_rejectsFreeAutoModel(self):
        args = SimpleNamespace(model="free:gemini", allow_free_tier=True)
        with self.assertRaisesRegex(RagStoreError, "free:"):
            rag_store.run_build(args)

    def test_run_build_freeTierModel_needsExplicitAllow(self):
        with mock.patch.object(llm, "has_free_tier", return_value=True):
            with self.assertRaisesRegex(RagStoreError, "--allow-free-tier"):
                rag_store.run_build(SimpleNamespace(model="gemini:m", allow_free_tier=False))

    def test_run_build_resumesAndWritesPerPrecedent(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "raw" / "law").mkdir(parents=True)
            (tmp / "raw" / "lbox").mkdir(parents=True)
            (tmp / "raw" / "law" / "1.json").write_text(
                json.dumps({"PrecService": {"판례내용": RAW_TEXT.replace("\n", "<br/>")}}, ensure_ascii=False), encoding="utf-8")
            (tmp / "raw" / "lbox" / "2.txt").write_text(RAW_TEXT, encoding="utf-8")
            (tmp / "c.json").write_text(json.dumps([candidate(), candidate(source="LBox", ref="2", group="G2")]),
                                        encoding="utf-8")
            args = SimpleNamespace(model="openai:m", candidates=tmp / "c.json", raw=tmp / "raw", out=tmp / "out",
                                   limit=None, max_retries=0, max_tokens=100, delay=0, allow_free_tier=False)
            caller = fake_caller(good_output())
            self.assertEqual(rag_store.run_build(args, caller=caller), [])
            self.assertEqual(sorted(p.name for p in (tmp / "out" / "precedents").iterdir()),
                             ["LAW_GO_KR-1.json", "LBOX-2.json"])
            rag_store.run_build(args, caller=caller)  # 이미 끝난 판례는 다시 부르지 않는다
            self.assertEqual(len(caller.calls), 2)
            self.assertNotIn("usedFreeTier", rag_store.load_json(tmp / "out" / "precedents" / "LBOX-2.json"))

    def test_run_build_allowedFreeTier_isRecorded(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(llm, "has_free_tier", return_value=True):
            tmp = Path(tmp)
            (tmp / "raw" / "lbox").mkdir(parents=True)
            (tmp / "raw" / "lbox" / "2.txt").write_text(RAW_TEXT, encoding="utf-8")
            (tmp / "c.json").write_text(json.dumps([candidate(source="LBox", ref="2")]), encoding="utf-8")
            args = SimpleNamespace(model="gemini:m", candidates=tmp / "c.json", raw=tmp / "raw", out=tmp / "out",
                                   limit=None, max_retries=0, max_tokens=100, delay=0, allow_free_tier=True)
            rag_store.run_build(args, caller=fake_caller(good_output()))
            self.assertTrue(rag_store.load_json(tmp / "out" / "precedents" / "LBOX-2.json")["usedFreeTier"])


class ImportTest(unittest.TestCase):
    def test_run_import_discardsStaleEmbedding(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            target = rag_store.targets([candidate()])[0]
            rag_store.write_json(out / "embeddings" / f"{target['key']}.json", {"model": "m", "dim": 3, "values": [1, 2, 3]})
            (out / "manual" / "outputs").mkdir(parents=True)
            (out / "manual" / "outputs" / f"{target['key']}.json").write_text(json.dumps(good_output(), ensure_ascii=False),
                                                                             encoding="utf-8")
            (out / "c.json").write_text(json.dumps([candidate()]), encoding="utf-8")
            failed = rag_store.run_import(SimpleNamespace(out=out, candidates=out / "c.json", keys=None,
                                                          model=rag_store.MANUAL_MODEL))
            self.assertEqual(failed, [])
            self.assertTrue((out / "precedents" / f"{target['key']}.json").exists())
            self.assertFalse((out / "embeddings" / f"{target['key']}.json").exists())


class EmbedTest(unittest.TestCase):
    def make_precedents(self, out, count=2):
        target = rag_store.targets([candidate()])[0]
        for i in range(count):
            precedent = {**target, "key": f"LAW_GO_KR-{i}", "result": good_output(factsSummary=f"사실관계 요약 {i}")}
            rag_store.write_json(Path(out) / "precedents" / f"LAW_GO_KR-{i}.json", precedent)

    @mock.patch.dict(os.environ, {"GEMINI_API_KEY": "k"})
    def test_embed_sendsFactsSummaryOnly_withDocumentTaskType(self):
        with tempfile.TemporaryDirectory() as out:
            self.make_precedents(out)
            http = fake_embed_http()
            rag_store.run_embed(SimpleNamespace(out=out, model="gemini-embedding-001", delay=0), http=http)
            requests = http.bodies[0]["body"]["requests"]
            self.assertEqual([r["content"]["parts"][0]["text"] for r in requests], ["사실관계 요약 0", "사실관계 요약 1"])
            self.assertEqual({r["taskType"] for r in requests}, {"RETRIEVAL_DOCUMENT"})
            self.assertEqual({r["outputDimensionality"] for r in requests}, {3072})
            # 원문 · 사건번호 · 해시 · 양형 이유 · 메타데이터는 임베딩 요청에 들어가지 않는다
            sent = json.dumps(http.bodies, ensure_ascii=False)
            for value in (CASE_NUMBER, rag_store.case_number_hash(CASE_NUMBER), "서울중앙지방법원", "테헤란로",
                          good_output()["sentencingReason"], "2019-03-28", "LAW_GO_KR"):
                self.assertNotIn(value, sent)
            saved = rag_store.load_json(Path(out) / "embeddings" / "LAW_GO_KR-0.json")
            self.assertEqual((saved["model"], saved["dim"], len(saved["values"])), ("gemini-embedding-001", 3072, 3072))
            rag_store.run_embed(SimpleNamespace(out=out, model="gemini-embedding-001", delay=0), http=http)
            self.assertEqual(len(http.bodies), 1)  # 이미 만든 임베딩은 다시 부르지 않는다

    @mock.patch.dict(os.environ, {"GEMINI_API_KEY": "k"})
    def test_embed_batchesBy100(self):
        http = fake_embed_http(dim=3)
        vectors = llm.embed([f"t{i}" for i in range(150)], dimensions=3, http=http)
        self.assertEqual(len(vectors), 150)
        self.assertEqual([len(b["body"]["requests"]) for b in http.bodies], [100, 50])

    @mock.patch.dict(os.environ, {"GEMINI_API_KEY": "k"})
    def test_embed_wrongDimensionOrCount_isError(self):
        with self.assertRaisesRegex(llm.LLMError, "차원"):
            llm.embed(["a"], dimensions=3072, http=fake_embed_http(dim=768))
        with self.assertRaisesRegex(llm.LLMError, "개수"):
            llm.embed(["a", "b"], dimensions=3, http=lambda url, headers, body, timeout: {"embeddings": [{"values": [1, 2, 3]}]})


class SqlTest(unittest.TestCase):
    def write(self, out, guideline="2013 수정 (2013. 4. 22. 의결)", with_embedding=True):
        target = {**rag_store.targets([candidate(guideline=guideline)])[0]}
        rag_store.write_json(Path(out) / "precedents" / f"{target['key']}.json",
                             {**target, "result": good_output(notes=["피고인이 여럿"]), "privacyWarnings": ["x: 실명일 수 있음"]})
        if with_embedding:
            rag_store.write_json(Path(out) / "embeddings" / f"{target['key']}.json",
                                 {"model": "gemini-embedding-001", "dim": 3072, "values": [0.5] * 3072})

    def test_build_sql_replacesTable_withGuidelineLookupAndFlags(self):
        with tempfile.TemporaryDirectory() as out:
            self.write(out)
            sql = rag_store.build_sql(out)
        self.assertIn("DELETE FROM rag_precedent;", sql)
        self.assertEqual(sql.count("INSERT INTO rag_precedent"), 1)
        self.assertIn("version_name = '2013 수정 (2013. 4. 22. 의결)'", sql)
        self.assertIn("'[0.5,0.5", sql)
        self.assertIn('"verified": false', sql)
        self.assertIn('"privacyWarnings": 1', sql)  # 경고 개수만 남기고 내용은 넣지 않는다
        self.assertNotIn(CASE_NUMBER, sql)
        self.assertNotIn("실명일 수 있음", sql)

    def test_build_sql_guidelineBeforeOrMissing_isNull(self):
        for name in ("없음 (양형기준 시행 전)", "2009 최초 (DB 미등록)", None):
            with self.subTest(name), tempfile.TemporaryDirectory() as out:
                self.write(out, guideline=name)
                self.assertNotIn("sentencing_guideline", rag_store.build_sql(out))

    def test_build_sql_missingEmbedding_isError(self):
        with tempfile.TemporaryDirectory() as out:
            self.write(out, with_embedding=False)
            with self.assertRaisesRegex(RagStoreError, "임베딩이 없는"):
                rag_store.build_sql(out)

    def test_run_sql_rejectsPublicRepositoryPath(self):
        with tempfile.TemporaryDirectory() as out:
            self.write(out)
            args = SimpleNamespace(out=out, output=str(TOOL_DIR / "out" / "R__40_rag_precedent.sql"))
            with self.assertRaises(RagStoreError):
                rag_store.run_sql(args)

    def test_merge_groups_similarSummaries_joinSameCaseGroup(self):
        def pair(key, group, values):
            return ({"key": key, "caseGroup": group, "flags": {"verified": False}}, {"values": values})
        pairs = [pair("LAW_GO_KR-2", "LAW_GO_KR-2", [1.0, 0.0, 0.0]),   # 1심
                 pair("LBOX-9", "LBOX-9", [0.99, 0.05, 0.0]),            # 같은 사건 다른 출처 (사건번호 없음)
                 pair("LAW_GO_KR-1", "LAW_GO_KR-1", [0.0, 1.0, 0.0])]    # 다른 사건
        merged = rag_store.merge_groups(pairs, threshold=0.95)
        self.assertEqual([(a, b) for a, b, _ in merged], [("LAW_GO_KR-2", "LBOX-9")])
        self.assertEqual([p["caseGroup"] for p, _ in pairs], ["LAW_GO_KR-2", "LAW_GO_KR-2", "LAW_GO_KR-1"])
        self.assertTrue(pairs[1][0]["flags"]["groupMergedByEmbedding"])
        self.assertNotIn("groupMergedByEmbedding", pairs[0][0]["flags"])
        self.assertNotIn("groupMergedByEmbedding", pairs[2][0]["flags"])

    def test_build_sql_usesModelChargeName(self):
        with tempfile.TemporaryDirectory() as out:
            self.write(out)
            path = next((Path(out) / "precedents").glob("*.json"))
            precedent = rag_store.load_json(path)
            precedent["result"]["chargeName"] = "살인미수"
            rag_store.write_json(path, precedent)
            self.assertIn("'MURDER', '살인미수'", rag_store.build_sql(out))

    def test_build_sql_nonMurderCharge_isFlaggedNotDropped(self):
        with tempfile.TemporaryDirectory() as out:
            self.write(out)
            path = next((Path(out) / "precedents").glob("*.json"))
            precedent = rag_store.load_json(path)
            precedent["result"]["chargeName"] = "유기치사"
            rag_store.write_json(path, precedent)
            sql = rag_store.build_sql(out)
        self.assertEqual(sql.count("INSERT INTO rag_precedent"), 1)
        self.assertIn("유죄 죄명이 살인 계열이 아님 (유기치사)", sql)

    def test_build_sql_lboxIsOnHold_lawGoKrIsNot(self):
        with tempfile.TemporaryDirectory() as out:
            self.write(out)
            target = rag_store.targets([candidate(source="LBox", ref="7", group="G9")])[0]
            rag_store.write_json(Path(out) / "precedents" / f"{target['key']}.json", {**target, "result": good_output()})
            rag_store.write_json(Path(out) / "embeddings" / f"{target['key']}.json",
                                 {"model": "gemini-embedding-001", "dim": 3072, "values": [0.1] * 3072})
            sql = rag_store.build_sql(out)
        law, lbox = [r for r in sql.split("INSERT INTO")[1:]]
        self.assertIn("false, NULL, ", law)
        self.assertIn("true, 'LBox Open 사용 불가", lbox)
        self.assertIn("사용 보류 1건: 출처 LBOX", sql)

    def test_hold_reason_precedentAndSourceHoldsCombine(self):
        with mock.patch.dict(rag_store.PRECEDENT_HOLDS, {"LAW_GO_KR-1": "무죄", "LBOX-2": "무죄"}, clear=True):
            self.assertEqual(rag_store.hold_reason({"source": "LAW_GO_KR", "key": "LAW_GO_KR-1"}), "무죄")
            self.assertIsNone(rag_store.hold_reason({"source": "LAW_GO_KR", "key": "LAW_GO_KR-3"}))
            self.assertTrue(rag_store.hold_reason({"source": "LBOX", "key": "LBOX-2"}).endswith(" · 무죄"))

    def test_build_sql_withCandidates_refreshesMetadataAndDropsMissing(self):
        with tempfile.TemporaryDirectory() as out:
            self.write(out)  # LAW_GO_KR-1, 저장 당시 caseGroup = LAW_GO_KR-1, guideline = 2013 수정
            stale = rag_store.targets([candidate(source="LBox", ref="8", group="G8")])[0]
            rag_store.write_json(Path(out) / "precedents" / f"{stale['key']}.json", {**stale, "result": good_output()})
            rag_store.write_json(Path(out) / "embeddings" / f"{stale['key']}.json",
                                 {"model": "gemini-embedding-001", "dim": 3072, "values": [0.1] * 3072})
            latest = [candidate(group="G1", guideline=None, guidelineByIncidentDate="2013 수정 (2013. 4. 22. 의결)"),
                      candidate(ref="2", group="G1"),  # 같은 그룹의 다른 판례 (정리 전) → 그룹 ID가 바뀌지 않음
                      candidate(source="LBox", ref="8", duplicateOf="law.go.kr:1")]  # 사본으로 빠짐
            sql = rag_store.build_sql(out, candidates=latest)
        self.assertEqual(sql.count("INSERT INTO rag_precedent"), 1)
        self.assertNotIn("sentencing_guideline", sql)  # 양형기준 미상 → guideline_id NULL
        # 버전은 검색할 때 발생일로 정한다(BE-54). 옛 참고 값은 빼고 기준 종류만 남긴다
        self.assertNotIn("guidelineByIncidentDate", sql)
        self.assertIn('"guidelineBasis": "INCIDENT_DATE"', sql)

    def test_load_holds_validatesFormat(self):
        with tempfile.TemporaryDirectory() as tmp:
            good, bad = Path(tmp) / "good.json", Path(tmp) / "bad.json"
            good.write_text(json.dumps({"LAW_GO_KR-1": "살인 관련 공소사실 무죄"}, ensure_ascii=False), encoding="utf-8")
            bad.write_text(json.dumps({"LAW_GO_KR-1": ""}), encoding="utf-8")
            self.assertEqual(rag_store.load_holds(good), {"LAW_GO_KR-1": "살인 관련 공소사실 무죄"})
            with self.assertRaisesRegex(RagStoreError, "--holds"):
                rag_store.load_holds(bad)
        self.assertEqual(rag_store.PRECEDENT_HOLDS, {})  # 공개 코드에는 판례 목록이 없다

    def test_sql_text_escapesQuotes(self):
        self.assertEqual(rag_store.sql_text("피고인의 '사과'"), "'피고인의 ''사과'''")


if __name__ == "__main__":
    unittest.main()
