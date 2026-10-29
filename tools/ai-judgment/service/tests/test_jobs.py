"""작업 관리 테스트 (COMMON-50). 파이프라인을 실제로 돌리지 않는다 — 서브프로세스는 가짜로 바꾸고,
상태는 state.json을 직접 써서 만든다. 네트워크 · LLM 호출 없음.

실행: tools/ai-judgment/service 에서 python3 -m unittest discover tests
"""

import datetime
import json
import os
import signal
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import jobs as jobs_module  # noqa: E402
from jobs import (DONE, FAILED, MAX_LOG_TAIL, PENDING, QUEUED, RUNNING, SKIPPED,  # noqa: E402
                  STAGE_FAILED, SUCCEEDED, JobConflict, JobError, JobNotFound, JobStore)


class FakeProcess:
    """가짜 서브프로세스. `returncode=None`이면 아직 도는 중으로 본다 (JobStore는 poll()로 판단한다)"""

    def __init__(self, pid, returncode=0):
        self.pid = pid
        self.returncode = returncode

    def poll(self):
        return self.returncode


class JobStoreTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.tool_dir = root / "tool"
        self.jobs_dir = root / "jobs"
        self.commands = []
        # 가짜 서브프로세스: 명령만 기록하고 살아 있지 않은 PID를 돌려준다(끝난 프로세스처럼 보인다)
        self.store = JobStore(jobs_dir=self.jobs_dir, tool_dir=self.tool_dir, popen=self._popen)

    def _popen(self, command, **kwargs):
        self.commands.append((command, kwargs))
        return FakeProcess(pid=0)  # 0 = _alive()가 False로 보는 값

    def _request(self, **overrides):
        request = {"sources": [{"originalText": "판결문 본문"}], "models": ["gemini:gemini-3.7-flash"]}
        request.update(overrides)
        return request

    def _write_state(self, job_id, stages):
        path = self.store.state_path(job_id)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"stages": stages}, ensure_ascii=False), encoding="utf-8")

    # ---------------------------------------------------------------- 생성

    def test_create_writes_config_and_sources(self):
        job_id = self.store.create(self._request(sources=[
            {"originalText": "1심 판결문"}, {"originalText": "항소심 판결문", "note": "열람 출력본"}]))

        config = json.loads((self.jobs_dir / job_id / "config.json").read_text(encoding="utf-8"))
        self.assertEqual(config["name"], f"job-{job_id}")
        self.assertEqual(len(config["sources"]), 2)
        self.assertEqual(config["sources"][1]["note"], "열람 출력본")
        first = Path(config["sources"][0]["path"])
        self.assertEqual(first.read_text(encoding="utf-8"), "1심 판결문")
        # 원문은 작업 폴더 안에만 둔다
        self.assertTrue(first.is_relative_to(self.jobs_dir / job_id))

    def test_create_disables_load_rag_contamination_by_default(self):
        job_id = self.store.create(self._request())

        stages = json.loads((self.jobs_dir / job_id / "config.json").read_text(encoding="utf-8"))["stages"]
        for stage in ("load", "rag", "contamination"):
            self.assertFalse(stages[stage]["enabled"], stage)
        self.assertEqual(stages["generate"]["models"], ["gemini:gemini-3.7-flash"])

    def test_create_lets_request_turn_a_disabled_stage_back_on(self):
        job_id = self.store.create(self._request(stages={"contamination": {"enabled": True, "runs": 3}}))

        stages = json.loads((self.jobs_dir / job_id / "config.json").read_text(encoding="utf-8"))["stages"]
        self.assertTrue(stages["contamination"]["enabled"])
        self.assertEqual(stages["contamination"]["runs"], 3)
        self.assertFalse(stages["load"]["enabled"])  # 적재는 그대로 꺼진 채

    def test_create_never_lets_a_request_turn_load_back_on(self):
        # load는 운영 DB에 직접 쓰는 단계다. 요청이 켜 달라고 해도 끈 채로 둔다 (COMMON-50)
        job_id = self.store.create(self._request(stages={"load": {"enabled": True, "seedDir": "/opt/seed"}}))

        load = json.loads((self.jobs_dir / job_id / "config.json").read_text(encoding="utf-8"))["stages"]["load"]
        self.assertFalse(load["enabled"])

    def test_create_writes_nothing_when_the_request_is_rejected(self):
        """검증 실패(400)에 판결문 원문이 디스크에 남으면 안 된다 (FR-5-3)"""
        rejected = (
            self._request(models=None),                                   # 모델 없음
            self._request(stages={"nosuch": {"enabled": True}}),           # 모르는 단계
            self._request(stages={"court": True}),                         # stages 값이 객체가 아님
            self._request(sources=[{"originalText": "본문"}, {"originalText": "  "}]),  # 두 번째가 빈 원문
        )
        for request in rejected:
            with self.assertRaises(JobError):
                self.store.create(request)

        existing = list(self.jobs_dir.iterdir()) if self.jobs_dir.is_dir() else []
        self.assertEqual(existing, [], "거절한 요청의 작업 폴더가 남았다")

    def test_create_rejects_a_stage_override_that_is_not_an_object(self):
        # update()에서 TypeError가 나 500이 되던 경로 — 400이어야 한다
        with self.assertRaises(JobError):
            self.store.create(self._request(stages={"court": True}))
        with self.assertRaises(JobError):
            self.store.create(self._request(stages=["court"]))

    def test_create_rejects_empty_or_too_many_sources(self):
        with self.assertRaises(JobError):
            self.store.create(self._request(sources=[]))
        with self.assertRaises(JobError):
            self.store.create(self._request(sources=[{"originalText": "a"}] * 4))

    def test_create_rejects_blank_original_text(self):
        with self.assertRaises(JobError):
            self.store.create(self._request(sources=[{"originalText": "   "}]))

    def test_create_rejects_unknown_stage_and_missing_models(self):
        with self.assertRaises(JobError):
            self.store.create(self._request(stages={"nosuch": {"enabled": True}}))
        with self.assertRaises(JobError):
            self.store.create({"sources": [{"originalText": "판결문"}]})

    # ---------------------------------------------------------------- 실행

    def test_start_runs_pipeline_until_axis(self):
        job_id = self.store.create(self._request())

        self.store.start(job_id)

        command, kwargs = self.commands[0]
        self.assertIn("pipeline.py", command[1])
        self.assertEqual(command[2], "run")
        self.assertEqual(command[-2:], ["--until", "axis"])
        self.assertEqual(kwargs["cwd"], str(self.tool_dir))
        meta = json.loads((self.jobs_dir / job_id / "job.json").read_text(encoding="utf-8"))
        self.assertEqual(meta["status"], RUNNING)

    def test_start_accepts_a_new_job_once_the_previous_process_ended(self):
        # 끝난 자식은 거둬 가지 않으면 좀비로 남아 os.kill(pid, 0)이 성공한다. 그걸 "도는 중"으로 보면
        # 작업 하나가 끝난 뒤로 새 작업이 영원히 409가 된다 — 핸들의 poll()로 판단해 막는다
        first = self.store.create(self._request())
        self.store.start(first)   # 가짜 프로세스는 바로 끝난 상태(poll() → 0)

        second = self.store.create(self._request())
        self.store.start(second)  # JobConflict가 나면 안 된다

        self.assertEqual(len(self.commands), 2)
        self.assertIsNone(self.store.running_job_id())

    def test_delete_works_after_the_process_ended(self):
        job_id = self.store.create(self._request())
        self.store.start(job_id)

        self.store.delete(job_id)

        self.assertFalse((self.jobs_dir / job_id).exists())

    def test_start_rejects_second_job_while_one_is_running(self):
        running = self.store.create(self._request())
        meta_path = self.jobs_dir / running / "job.json"
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        meta.update(status=RUNNING, pid=os.getpid())  # 이 테스트 프로세스 = 살아 있는 PID
        meta_path.write_text(json.dumps(meta), encoding="utf-8")

        other = self.store.create(self._request())
        with self.assertRaises(JobConflict):
            self.store.start(other)

    def test_start_holds_the_lock_while_checking_and_spawning(self):
        """확인과 띄우기가 한 잠금 안에 있어야 두 요청이 나란히 통과하지 못한다"""
        held = []
        self.store = JobStore(jobs_dir=self.jobs_dir, tool_dir=self.tool_dir,
                              popen=lambda command, **kwargs: (held.append(self.store._start_lock.locked()),
                                                               FakeProcess(pid=0))[1])
        self.store.start(self.store.create(self._request()))

        self.assertEqual(held, [True])

    def test_only_one_of_two_concurrent_starts_wins(self):
        import threading

        def slow_popen(command, **kwargs):
            time.sleep(0.05)  # 잠금이 없으면 두 스레드가 이 안에서 겹친다
            return FakeProcess(pid=os.getpid(), returncode=None)  # 살아 있는 것처럼 보이게

        self.store = JobStore(jobs_dir=self.jobs_dir, tool_dir=self.tool_dir, popen=slow_popen)
        jobs = [self.store.create(self._request()), self.store.create(self._request())]
        results = {}

        def run(job_id):
            try:
                self.store.start(job_id)
                results[job_id] = "started"
            except JobConflict:
                results[job_id] = "conflict"

        threads = [threading.Thread(target=run, args=(job_id,)) for job_id in jobs]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()

        self.assertEqual(sorted(results.values()), ["conflict", "started"],
                         f"파이프라인이 둘 다 떴다: {results}")

    def test_start_rejects_a_second_job_and_keeps_the_running_one(self):
        running = self.store.create(self._request())
        meta_path = self.jobs_dir / running / "job.json"
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        meta.update(status=RUNNING, pid=os.getpid())
        meta_path.write_text(json.dumps(meta), encoding="utf-8")

        other = self.store.create(self._request())
        with self.assertRaises(JobConflict):
            self.store.start(other)

        self.assertEqual(self.store.running_job_id(), running)

    # ---------------------------------------------------------------- 중단

    def test_cancel_does_not_overwrite_a_finished_job(self):
        """끝난 작업에 중단을 불러도 FAILED로 덮어쓰지 않는다"""
        job_id = self.store.create(self._request())
        self.store.start(job_id)  # 가짜 프로세스는 이미 끝난 상태
        self._write_state(job_id, {stage: {"status": "done"}
                                   for stage in ("extract", "court", "generate", "select", "axis")})
        self.store.status(job_id)  # 메타에 SUCCEEDED가 적힌다

        self.store.cancel(job_id)

        self.assertEqual(self.store.status(job_id)["status"], SUCCEEDED)

    def test_cancel_stops_the_process_group(self):
        killed = []
        self.store = JobStore(jobs_dir=self.jobs_dir, tool_dir=self.tool_dir,
                              popen=lambda command, **kwargs: FakeProcess(pid=4242, returncode=None))
        job_id = self.store.create(self._request())
        self.store.start(job_id)

        with mock.patch.object(jobs_module.os, "getpgid", return_value=4242), \
             mock.patch.object(jobs_module.os, "killpg", side_effect=lambda pgid, sig: killed.append((pgid, sig))):
            self.store.cancel(job_id)

        # 프로세스 하나가 아니라 그룹에 보낸다 (파이프라인이 띄운 자식까지 멈추게)
        self.assertEqual(killed, [(4242, signal.SIGTERM)])
        meta = json.loads((self.jobs_dir / job_id / "job.json").read_text(encoding="utf-8"))
        self.assertEqual(meta["error"], "관리자가 중단했습니다")
        # SIGTERM 직후에는 아직 살아 있어 RUNNING으로 보이는 게 맞다. 실제로 끝나면 FAILED가 된다
        self.store._processes[job_id].returncode = -signal.SIGTERM
        self.assertEqual(self.store.status(job_id)["status"], FAILED)

    def test_start_passes_start_new_session_so_children_can_be_stopped(self):
        job_id = self.store.create(self._request())
        self.store.start(job_id)

        self.assertTrue(self.commands[0][1]["start_new_session"])

    # ---------------------------------------------------------------- 상태

    def test_status_maps_pipeline_state_to_api_values(self):
        job_id = self.store.create(self._request())
        self._write_state(job_id, {
            "extract": {"status": "done"},
            "court": {"status": "running"},
        })

        stages = {s["name"]: s["status"] for s in self.store.status(job_id)["stages"]}

        self.assertEqual(stages["extract"], DONE)
        self.assertEqual(stages["court"], RUNNING)
        self.assertEqual(stages["generate"], PENDING)   # 켜져 있지만 아직 시작 전
        self.assertEqual(stages["load"], SKIPPED)       # 서비스에서 끈 단계
        self.assertEqual(stages["rag"], SKIPPED)

    def test_status_is_queued_before_start(self):
        job_id = self.store.create(self._request())

        self.assertEqual(self.store.status(job_id)["status"], QUEUED)

    def test_status_is_failed_when_a_stage_failed(self):
        job_id = self.store.create(self._request())
        self.store.start(job_id)
        self._write_state(job_id, {
            "extract": {"status": "done"},
            "court": {"status": "failed", "error": "비식별화 실패: 모델 응답 없음\n두 번째 줄"},
        })

        status = self.store.status(job_id)

        self.assertEqual(status["status"], FAILED)
        self.assertEqual(status["error"], "비식별화 실패: 모델 응답 없음")  # 첫 줄만
        self.assertEqual([s for s in status["stages"] if s["name"] == "court"][0]["status"], STAGE_FAILED)

    def test_status_is_succeeded_when_every_planned_stage_is_done(self):
        job_id = self.store.create(self._request())
        self.store.start(job_id)  # 가짜 프로세스는 이미 끝난 상태다
        self._write_state(job_id, {stage: {"status": "done"}
                                   for stage in ("extract", "court", "generate", "select", "axis")})

        self.assertEqual(self.store.status(job_id)["status"], SUCCEEDED)

    def test_status_marks_a_still_running_process_as_running(self):
        self.store = JobStore(jobs_dir=self.jobs_dir, tool_dir=self.tool_dir,
                              popen=lambda command, **kwargs: FakeProcess(pid=0, returncode=None))
        job_id = self.store.create(self._request())
        self.store.start(job_id)
        self._write_state(job_id, {"extract": {"status": "done"}})

        self.assertEqual(self.store.status(job_id)["status"], RUNNING)
        self.assertEqual(self.store.running_job_id(), job_id)

    def test_status_records_finished_at_once_the_job_ends(self):
        job_id = self.store.create(self._request())
        self.store.start(job_id)
        self._write_state(job_id, {stage: {"status": "done"}
                                   for stage in ("extract", "court", "generate", "select", "axis")})

        self.store.status(job_id)

        meta = json.loads((self.jobs_dir / job_id / "job.json").read_text(encoding="utf-8"))
        self.assertEqual(meta["status"], SUCCEEDED)
        self.assertIsNotNone(meta["finishedAt"])
        self.assertIsNotNone(self.store.status(job_id)["finishedAt"])

    def test_status_is_failed_when_process_stopped_midway(self):
        job_id = self.store.create(self._request())
        self.store.start(job_id)
        self._write_state(job_id, {"extract": {"status": "done"}})  # 뒤 단계는 시작도 못 했다

        status = self.store.status(job_id)

        self.assertEqual(status["status"], FAILED)
        self.assertIn("멈췄습니다", status["error"])

    def test_status_rejects_unknown_job(self):
        with self.assertRaises(JobNotFound):
            self.store.status("0" * 32)
        with self.assertRaises(JobNotFound):
            self.store.status("../etc/passwd")  # 경로 조작 차단

    # ---------------------------------------------------------------- 결과

    def test_result_returns_case_and_judgment_without_original_text(self):
        job_id = self.store.create(self._request())
        case_file = self.tool_dir / "cases" / "case.json"
        output_file = self.tool_dir / "out" / "run.output.json"
        for path, value in ((case_file, {"title": "사건"}), (output_file, {"penaltyType": "PRISON"})):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
        self._write_state(job_id, {
            "extract": {"status": "done", "outputs": {"case": str(case_file), "source": "/원문/경로.json"}},
            "select": {"status": "done", "outputs": {"outputFile": str(output_file), "modelSpec": "gemini:x",
                                                     "runIndex": 1, "strategy": "consensus", "reason": "합의",
                                                     "warnings": []}},
        })

        result = self.store.result(job_id)

        self.assertEqual(result["case"], {"title": "사건"})
        self.assertEqual(result["judgment"], {"penaltyType": "PRISON"})
        self.assertEqual(result["selection"]["modelSpec"], "gemini:x")
        # 판결문 원문(source)은 결과에 넣지 않는다 (FR-5-3, 원문은 A-7로만 본다)
        self.assertNotIn("source", result)
        self.assertNotIn("/원문/경로.json", json.dumps(result, ensure_ascii=False))

    def test_result_uses_court_stage_draft_not_the_contamination_file(self):
        """재판부 판결은 court 단계의 courtDraft여야 한다.

        extract의 court 산출물은 형벌 값만 담은 사전 학습 점검 전용 파일이라, 적재에 필요한
        judgmentFactors · 요약 · 판결 이유가 없다. 처음에는 그 파일을 내보내고 있었다 (BE-93 2차에서 발견).
        """
        job_id = self.store.create(self._request())
        draft = self.tool_dir / "cases" / "draft.json"
        contamination = self.tool_dir / "cases" / "internal.json"
        draft.parent.mkdir(parents=True, exist_ok=True)
        draft.write_text(json.dumps({"judgment": {"penaltyType": "PRISON"}, "judgmentFactors": [{"factorId": 1}]},
                                    ensure_ascii=False), encoding="utf-8")
        contamination.write_text(json.dumps({"caseTitle": "사건", "penaltyType": "PRISON"}, ensure_ascii=False),
                                 encoding="utf-8")
        self._write_state(job_id, {
            "extract": {"status": "done", "outputs": {"case": str(draft), "court": str(contamination)}},
            "court": {"status": "done", "outputs": {"courtDraft": str(draft)}},
        })

        result = self.store.result(job_id)

        self.assertIn("judgmentFactors", result["courtDraft"])
        self.assertNotIn("courtJudgment", result)	# 점검 전용 파일은 내보내지 않는다

    def test_result_reads_axis_votes_instead_of_sending_a_path(self):
        """가치관 축은 투표 내용을 보내야 한다. 경로만 보내면 부르는 쪽이 읽을 수 없다 (BE-93 2차에서 발견)"""
        job_id = self.store.create(self._request())
        votes = self.tool_dir / "cases" / "votes.json"
        votes.parent.mkdir(parents=True, exist_ok=True)
        votes.write_text(json.dumps({"factors": [{"factorId": 7, "valueAxis": "FAULT_STANDARD",
                                                  "valueAxisVotes": {"runs": 5}}]}, ensure_ascii=False),
                         encoding="utf-8")
        self._write_state(job_id, {
            "extract": {"status": "done", "outputs": {"case": str(votes)}},
            "axis": {"status": "done", "outputs": {"votesFile": str(votes), "runs": 5}},
        })

        axis = self.store.result(job_id)["axis"]

        self.assertEqual(axis["factors"][0]["factorId"], 7)
        self.assertNotIn("votesFile", axis)	# 컨테이너 내부 경로는 내보내지 않는다

    def test_result_reads_script_evidence_instead_of_sending_a_path(self):
        """대본도 축과 같다 — 대사 내용을 보내야 적재(BE-108)가 쓸 수 있다. 경로만 보내면 읽을 수 없다"""
        job_id = self.store.create(self._request())
        evidence = self.tool_dir / "cases" / "evidence.json"
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_text(json.dumps({
            "model": "google:gemini-2.5-flash", "publishable": True, "createdAt": "2026-10-27T09:00:00+09:00",
            "evidence": [{"factorId": 3, "side": "검사", "line": "피고인은 흉기를 미리 준비했습니다"}],
        }, ensure_ascii=False), encoding="utf-8")
        self._write_state(job_id, {
            "extract": {"status": "done", "outputs": {"case": str(evidence)}},
            "script": {"status": "done", "outputs": {"evidenceFile": str(evidence), "count": 1,
                                                     "failed": 0, "publishable": True, "stopped": False}},
        })

        script = self.store.result(job_id)["script"]

        self.assertEqual(script["evidence"][0]["factorId"], 3)
        self.assertTrue(script["publishable"])		# 적재 · 공개 판단에 쓰는 계약 값은 그대로 넘긴다
        self.assertNotIn("evidenceFile", script)	# 컨테이너 내부 경로는 내보내지 않는다
        # 생성 시각은 증거 파일에만 있다 — 여기서 올려 주지 않으면 적재가 generated_at을 영영 비워 둔다 (PR #300 리뷰)
        self.assertEqual(script["createdAt"], "2026-10-27T09:00:00+09:00")
        # stopped는 단계 산출물 쪽 값을 쓴다. 증거 파일에는 이 키가 없어서 파일에서 가져오면 항상 false가 된다
        self.assertFalse(script["stopped"])

    def test_result_script_without_created_at_in_file(self):
        """증거 파일에 createdAt이 없어도 나머지는 그대로 간다 — 적재는 생성 시각만 비워 두고 계속한다 (BE-108)"""
        job_id = self.store.create(self._request())
        evidence = self.tool_dir / "cases" / "evidence.json"
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_text(json.dumps({"evidence": [{"factorId": 1}]}, ensure_ascii=False), encoding="utf-8")
        self._write_state(job_id, {
            "extract": {"status": "done", "outputs": {"case": str(evidence)}},
            "script": {"status": "done", "outputs": {"evidenceFile": str(evidence), "publishable": False,
                                                     "stopped": True}},
        })

        script = self.store.result(job_id)["script"]

        self.assertNotIn("createdAt", script)
        self.assertTrue(script["stopped"])
        self.assertEqual(script["evidence"][0]["factorId"], 1)

    def test_result_sends_full_script_content_not_path(self):
        """대본 전체(BE-121)도 경로가 아니라 내용(fullScript)으로 보낸다. 대본 전체를 안 만들었으면 키가 없다"""
        job_id = self.store.create(self._request())
        folder = self.tool_dir / "cases"
        folder.mkdir(parents=True, exist_ok=True)
        evidence, full = folder / "evidence.json", folder / "script.json"
        evidence.write_text(json.dumps({"evidence": []}), encoding="utf-8")
        full.write_text(json.dumps({"acts": {"2": {"steps": []}}, "pending": ["U1"]}), encoding="utf-8")
        self._write_state(job_id, {
            "extract": {"status": "done", "outputs": {"case": str(evidence)}},
            "script": {"status": "done", "outputs": {"evidenceFile": str(evidence), "scriptFile": str(full),
                                                     "pending": ["U1"], "publishable": True, "stopped": False}},
        })

        script = self.store.result(job_id)["script"]

        self.assertNotIn("scriptFile", script)	# 컨테이너 내부 경로는 내보내지 않는다
        self.assertEqual(script["fullScript"]["pending"], ["U1"])
        self.assertEqual(script["pending"], ["U1"])

    def test_result_without_script_stage(self):
        """대본을 안 돌린 작업은 script가 None이다 — 사건 · 판결만으로도 적재는 그대로 된다 (BE-108)"""
        job_id = self.store.create(self._request())
        case_file = self.tool_dir / "cases" / "case.json"
        case_file.parent.mkdir(parents=True, exist_ok=True)
        case_file.write_text(json.dumps({"title": "사건"}, ensure_ascii=False), encoding="utf-8")
        self._write_state(job_id, {"extract": {"status": "done", "outputs": {"case": str(case_file)}}})

        self.assertIsNone(self.store.result(job_id)["script"])

    def test_result_before_pipeline_produced_anything(self):
        job_id = self.store.create(self._request())

        with self.assertRaises(JobNotFound):
            self.store.result(job_id)

    # ---------------------------------------------------------------- 로그

    def test_log_tail_zero_returns_the_tail_not_the_whole_file(self):
        job_id = self.store.create(self._request())
        (self.jobs_dir / job_id / "log.txt").write_text(
            "\n".join(f"line {i}" for i in range(100)), encoding="utf-8")

        self.assertEqual(self.store.log(job_id, tail=0), "line 99")       # lines[-0:] = 전체였다
        self.assertEqual(self.store.log(job_id, tail=-5), "line 99")
        self.assertEqual(len(self.store.log(job_id, tail=3).splitlines()), 3)
        self.assertEqual(len(self.store.log(job_id, tail=MAX_LOG_TAIL * 10).splitlines()), 100)

    def test_log_rejects_a_tail_that_is_not_a_number(self):
        job_id = self.store.create(self._request())
        (self.jobs_dir / job_id / "log.txt").write_text("한 줄", encoding="utf-8")

        with self.assertRaises(JobError):
            self.store.log(job_id, tail="전부")

    # ---------------------------------------------------------------- 정리

    def test_delete_removes_sources_and_state(self):
        job_id = self.store.create(self._request())
        self._write_state(job_id, {"extract": {"status": "done"}})

        self.store.delete(job_id)

        self.assertFalse((self.jobs_dir / job_id).exists())
        self.assertFalse(self.store.state_path(job_id).parent.exists())

    def test_delete_removes_the_original_judgment_text_in_cases(self):
        """삭제는 판결문 원문까지 지워야 한다 (FR-5-3). cases/는 작업 폴더 밖이라 전에는 남았다"""
        job_id = self.store.create(self._request())
        name = self.store.pipeline_name(job_id)
        cases = self.tool_dir / "cases"
        cases.mkdir(parents=True, exist_ok=True)
        source = cases / f"{name}.source_internal.json"      # 원문
        for leftover in (source, cases / f"{name}.case.json", cases / f"{name}.report.json"):
            leftover.write_text("{}", encoding="utf-8")
        other = cases / "job-00000000000000000000000000000000.source_internal.json"
        other.write_text("{}", encoding="utf-8")             # 다른 작업 것은 남아야 한다
        runs = self.tool_dir / "out" / "runs" / f"{name}-abc123"
        runs.mkdir(parents=True, exist_ok=True)
        (runs / "run1.json").write_text("{}", encoding="utf-8")

        self.store.delete(job_id)

        self.assertFalse(source.exists(), "판결문 원문이 남았다")
        self.assertFalse((cases / f"{name}.case.json").exists())
        self.assertFalse((cases / f"{name}.report.json").exists())
        self.assertFalse(runs.exists(), "생성 회차 폴더가 남았다")
        self.assertTrue(other.exists(), "다른 작업의 파일을 지웠다")

    def test_delete_removes_artifacts_even_when_state_has_no_outputs(self):
        """단계가 파일을 쓰다 실패하면 state.json에 경로가 없다. 그래도 원문은 지워져야 한다"""
        job_id = self.store.create(self._request())
        self._write_state(job_id, {"extract": {"status": "failed", "error": "중간에 실패"}})
        cases = self.tool_dir / "cases"
        cases.mkdir(parents=True, exist_ok=True)
        source = cases / f"{self.store.pipeline_name(job_id)}.source_internal.json"
        source.write_text("{}", encoding="utf-8")

        self.store.delete(job_id)

        self.assertFalse(source.exists(), "state.json에 경로가 없다고 원문이 남으면 안 된다")

    def test_delete_refuses_while_running(self):
        job_id = self.store.create(self._request())
        meta_path = self.jobs_dir / job_id / "job.json"
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        meta.update(status=RUNNING, pid=os.getpid())
        meta_path.write_text(json.dumps(meta), encoding="utf-8")

        with self.assertRaises(JobConflict):
            self.store.delete(job_id)


class JobCleanupTest(unittest.TestCase):
    """오래된 작업 자동 정리 (COMMON-51). 시간은 cleanup(now=...)로 직접 준다 — 기다리지 않는다"""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.tool_dir = root / "tool"
        self.jobs_dir = root / "jobs"
        self.store = JobStore(jobs_dir=self.jobs_dir, tool_dir=self.tool_dir,
                              popen=lambda command, **kwargs: FakeProcess(pid=0))
        self.now = datetime.datetime(2026, 10, 28, 12, 0, tzinfo=datetime.timezone.utc)

    def _job(self, finished_days_ago=None, created_days_ago=None, status=SUCCEEDED, pid=None):
        """작업 하나를 만들고 메타의 시각 · 상태를 원하는 대로 고쳐 둔다.

        만든 시각을 따로 주지 않으면 끝난 시각과 같게 둔다 — 나이는 가장 늦은 기록으로 보기 때문에,
        만든 시각이 끝난 시각보다 늦으면(실제로는 없는 순서) 오래된 작업도 최근 작업으로 보인다
        """
        if created_days_ago is None:
            created_days_ago = finished_days_ago or 0
        job_id = self.store.create({"sources": [{"originalText": "판결문 본문"}],
                                    "models": ["gemini:gemini-3.7-flash"]})
        meta_path = self.jobs_dir / job_id / "job.json"
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        meta["status"] = status
        meta["createdAt"] = (self.now - datetime.timedelta(days=created_days_ago)).isoformat(timespec="seconds")
        meta["finishedAt"] = None if finished_days_ago is None else \
            (self.now - datetime.timedelta(days=finished_days_ago)).isoformat(timespec="seconds")
        meta["pid"] = pid
        meta_path.write_text(json.dumps(meta), encoding="utf-8")
        return job_id

    def test_deletes_jobs_finished_before_the_retention_window(self):
        old = self._job(finished_days_ago=8)
        recent = self._job(finished_days_ago=2)

        deleted = self.store.cleanup(retention_days=7, now=self.now)

        self.assertEqual(deleted, [old])
        self.assertFalse((self.jobs_dir / old).exists())
        self.assertTrue((self.jobs_dir / recent).exists(), "보관 기간 안의 작업을 지웠다")

    def test_deleted_job_leaves_no_original_text_behind(self):
        """정리도 삭제와 같은 범위를 지워야 한다 — 작업 폴더만 지우면 원문이 cases/에 남는다 (FR-5-3)"""
        job_id = self._job(finished_days_ago=30)
        cases = self.tool_dir / "cases"
        cases.mkdir(parents=True, exist_ok=True)
        source = cases / f"{self.store.pipeline_name(job_id)}.source_internal.json"
        source.write_text("{}", encoding="utf-8")

        self.store.cleanup(retention_days=7, now=self.now)

        self.assertFalse(source.exists(), "판결문 원문이 남았다")
        self.assertFalse(self.store.state_path(job_id).parent.exists())

    def test_keeps_running_jobs_however_old(self):
        """도는 작업은 건드리지 않는다. 오래 돌고 있다는 것이 지울 이유가 되지는 않는다"""
        running = self._job(created_days_ago=30, status=RUNNING, pid=os.getpid())  # 이 테스트 = 살아 있는 PID

        deleted = self.store.cleanup(retention_days=7, now=self.now)

        self.assertEqual(deleted, [])
        self.assertTrue((self.jobs_dir / running).exists())

    def test_uses_created_time_when_the_job_never_finished(self):
        """시작하지 못했거나 끝을 기록하지 못한 작업(finishedAt 없음)도 원문을 품고 있다"""
        stuck = self._job(finished_days_ago=None, created_days_ago=9, status=QUEUED)

        self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), [stuck])

    def test_uses_folder_time_when_the_meta_is_unreadable(self):
        """job.json이 깨져도 raw/의 원문은 그대로다 — 읽을 값이 없다고 건너뛰면 그 폴더만 영영 남는다"""
        broken = self._job(finished_days_ago=30)
        (self.jobs_dir / broken / "job.json").write_text("{부서진 JSON", encoding="utf-8")
        old = (self.now - datetime.timedelta(days=30)).timestamp()
        os.utime(self.jobs_dir / broken, (old, old))

        self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), [broken])

    def test_keeps_a_rerun_job_whose_meta_was_not_settled_yet(self):
        """끝난 지 오래된 작업을 다시 돌리면(BE-107 `--from script`) 방금 만든 결과를 지우면 안 된다 (PR #326 리뷰).

        다시 돌린 프로세스가 끝났는데 아직 아무도 status()를 부르지 않은 상태 — 메타는 RUNNING인데 프로세스는 없다
        """
        job_id = self._job(finished_days_ago=8, created_days_ago=10)
        self.store.start(job_id)  # 가짜 프로세스는 바로 끝난다(pid=0, returncode=0)

        meta = json.loads((self.jobs_dir / job_id / "job.json").read_text(encoding="utf-8"))
        self.assertIsNone(meta["finishedAt"], "다시 돌린 작업에 지난번 finishedAt이 남았다")
        self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), [])
        self.assertTrue((self.jobs_dir / job_id).exists())

    def test_recent_pipeline_output_keeps_a_job_with_old_meta(self):
        """메타의 시각이 오래돼도 state.json · 로그가 최근에 쓰였으면 최근에 돈 작업이다"""
        job_id = self._job(finished_days_ago=8)
        state = self.store.state_path(job_id)
        state.parent.mkdir(parents=True, exist_ok=True)
        state.write_text("{}", encoding="utf-8")
        recent = (self.now - datetime.timedelta(days=1)).timestamp()
        os.utime(state, (recent, recent))

        self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), [])

    def test_meta_that_is_not_an_object_does_not_stop_the_sweep(self):
        """job.json이 JSON 배열 · 문자열이어도 정리 순회가 멈추면 안 된다 (PR #326 리뷰)"""
        odd, normal = self._job(finished_days_ago=30), self._job(finished_days_ago=30)
        (self.jobs_dir / odd / "job.json").write_text('["목록"]', encoding="utf-8")
        old = (self.now - datetime.timedelta(days=30)).timestamp()
        os.utime(self.jobs_dir / odd, (old, old))

        self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), sorted((odd, normal)))

    def test_keeps_a_running_job_even_if_its_meta_is_broken(self):
        """메타가 깨져 jobId를 몰라도 폴더 이름으로 프로세스 핸들을 찾는다 — 도는 작업의 파일을 지우면 안 된다"""
        job_id = self._job(finished_days_ago=30)
        self.store._processes[job_id] = FakeProcess(pid=0, returncode=None)  # 아직 도는 중
        (self.jobs_dir / job_id / "job.json").write_text("{부서진 JSON", encoding="utf-8")
        old = (self.now - datetime.timedelta(days=30)).timestamp()
        os.utime(self.jobs_dir / job_id, (old, old))

        self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), [])
        with self.assertRaises(JobConflict):
            self.store.delete(job_id)
        self.assertTrue((self.jobs_dir / job_id).exists())

    def test_check_and_delete_run_under_the_start_lock(self):
        """확인과 삭제 사이에 같은 작업이 다시 시작되지 못하게 start와 같은 잠금 안에서 지운다 (PR #326 리뷰)"""
        self._job(finished_days_ago=8)
        held = []
        original = self.store._delete

        def delete(job_id):
            held.append(self.store._start_lock.locked())
            return original(job_id)

        with mock.patch.object(self.store, "_delete", side_effect=delete):
            self.store.cleanup(retention_days=7, now=self.now)

        self.assertEqual(held, [True])

    def test_failed_run_folder_removal_keeps_the_job_for_the_next_sweep(self):
        """회차 결과 폴더를 못 지웠는데 성공으로 끝나면 작업 폴더가 사라져 다음 주기에 다시 찾지 못한다"""
        job_id = self._job(finished_days_ago=8)
        runs = self.tool_dir / "out" / "runs" / f"{self.store.pipeline_name(job_id)}-1"
        runs.mkdir(parents=True)

        with mock.patch.object(jobs_module.shutil, "rmtree", side_effect=PermissionError("권한 없음")):
            self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), [])

        self.assertTrue((self.jobs_dir / job_id).exists(), "산출물을 못 지웠는데 작업 폴더가 지워졌다")
        self.assertEqual(self.store.cleanup(retention_days=7, now=self.now), [job_id])  # 다음 주기에 마저 지운다
        self.assertFalse(runs.exists())

    def test_zero_or_negative_retention_turns_cleanup_off(self):
        job_id = self._job(finished_days_ago=365)

        self.assertEqual(self.store.cleanup(retention_days=0, now=self.now), [])
        self.assertTrue((self.jobs_dir / job_id).exists())

    def test_one_failure_does_not_stop_the_rest(self):
        first, second = self._job(finished_days_ago=8), self._job(finished_days_ago=8)
        failing = {sorted((first, second))[0]}
        original = self.store._delete

        def delete(job_id):
            if job_id in failing:
                raise OSError("지울 수 없습니다")
            return original(job_id)

        with mock.patch.object(self.store, "_delete", side_effect=delete):
            deleted = self.store.cleanup(retention_days=7, now=self.now)

        self.assertEqual(deleted, [j for j in sorted((first, second)) if j not in failing])

    def test_retention_days_comes_from_the_environment(self):
        with mock.patch.dict(os.environ, {"PIPELINE_JOB_RETENTION_DAYS": "3"}):
            self.assertEqual(JobStore._retention_days(), 3)
        with mock.patch.dict(os.environ, {"PIPELINE_JOB_RETENTION_DAYS": "사흘"}):
            self.assertEqual(JobStore._retention_days(), jobs_module.DEFAULT_RETENTION_DAYS)
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertEqual(JobStore._retention_days(), jobs_module.DEFAULT_RETENTION_DAYS)


class CleanupLoopTest(unittest.TestCase):
    """정리 루프 (COMMON-51). 진짜로 기다리지 않는다 — 가짜 Event가 바로 "멈춰라"를 돌려준다"""

    class FakeStop:
        """wait()가 호출될 때마다 기다린 간격을 적고, 정해진 횟수가 지나면 True(멈춰라)를 돌려준다"""

        def __init__(self, stop_after):
            self.stop_after = stop_after
            self.waits = []

        def wait(self, timeout=None):
            self.waits.append(timeout)
            return len(self.waits) >= self.stop_after

    def test_cleans_once_before_waiting(self):
        """시작하자마자 한 번 돌아야 한다 — 서비스를 다시 띄운 직후가 가장 쌓여 있을 때다"""
        store = mock.Mock()
        stop = self.FakeStop(stop_after=1)

        jobs_module.run_cleanup_loop(store, stop, interval=123)

        store.cleanup.assert_called_once_with()
        self.assertEqual(stop.waits, [123])

    def test_keeps_going_after_a_failure(self):
        """한 번 실패했다고 스레드가 끝나면 원문이 남는 것을 아무도 모르게 된다"""
        store = mock.Mock()
        store.cleanup.side_effect = [OSError("디스크 오류"), None]
        stop = self.FakeStop(stop_after=2)

        jobs_module.run_cleanup_loop(store, stop, interval=1)

        self.assertEqual(store.cleanup.call_count, 2)


if __name__ == "__main__":
    unittest.main()
