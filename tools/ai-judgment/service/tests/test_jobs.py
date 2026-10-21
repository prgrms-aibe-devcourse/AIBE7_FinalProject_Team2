"""작업 관리 테스트 (COMMON-50). 파이프라인을 실제로 돌리지 않는다 — 서브프로세스는 가짜로 바꾸고,
상태는 state.json을 직접 써서 만든다. 네트워크 · LLM 호출 없음.

실행: tools/ai-judgment/service 에서 python3 -m unittest discover tests
"""

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


if __name__ == "__main__":
    unittest.main()
