"""작업(job) 관리 (COMMON-50). pipeline.py를 서브프로세스로 돌리고 상태를 읽어 준다.

왜 서브프로세스인가 — 파이프라인(BE-31)은 단계마다 `out/pipeline/<name>/state.json`에 진행 상황을 남기고
중단한 자리에서 이어서 돌릴 수 있게 만들어져 있다. 같은 프로세스 안에서 함수로 부르면 그 구조를 다시 만들어야
하고, 작업 하나가 터지면 서비스 전체가 함께 넘어간다. 서브프로세스로 두면 ① 기존 재시작 구조를 그대로 쓰고
② 작업이 죽어도 서비스는 살아 있고 ③ 필요하면 죽일 수 있다. 상태도 따로 만들지 않고 파이프라인이 쓰는
state.json을 그대로 읽는다 — 진행 상황의 출처가 하나뿐이라 서로 어긋날 일이 없다.

이 모듈은 FastAPI에 기대지 않는다(테스트에서 HTTP 없이 그대로 부른다). HTTP 쪽은 app.py에 있다.
"""

from __future__ import annotations

import datetime
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import uuid
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
PIPELINE_SCRIPT = TOOL_DIR / "pipeline.py"
DEFAULT_JOBS_DIR = TOOL_DIR / "out" / "jobs"

# 파이프라인 단계 순서 (pipeline.py STAGES와 같아야 한다)
STAGES = ("extract", "court", "contamination", "rag", "generate", "select", "axis", "load")

# 작업 상태 (API 명세 A-15와 같은 값)
QUEUED, RUNNING, SUCCEEDED, FAILED = "QUEUED", "RUNNING", "SUCCEEDED", "FAILED"
# 단계 상태 (API 명세 A-15)
PENDING, STAGE_RUNNING, DONE, SKIPPED, STAGE_FAILED = "PENDING", "RUNNING", "DONE", "SKIPPED", "FAILED"

# 파이프라인 state.json의 단계 상태 → API 명세 값
_STAGE_STATUS = {"running": STAGE_RUNNING, "done": DONE, "failed": STAGE_FAILED}

# 서비스에서 기본으로 끄는 단계
# - load: 적재는 관리자가 검수한 뒤 백엔드(Java)가 한다(BE-93). 파이프라인의 load는 비공개 저장소 체크아웃과
#   로컬 DB를 요구하므로 컨테이너에서는 쓰지 않는다
# - rag: psql로 로컬 DB에 붙어야 하는데 컨테이너에서는 DB가 로컬이 아니다(pipeline.py _is_local_host).
#   못 쓰면 사건 입력의 references로 진행한다는 기존 정책(요구사항 v5.5 "RAG 우선, 안 되면 퓨샷")을 따른다
# - contamination: 사전 학습 점검은 호출 수가 많아(기본 10회) 기본은 꺼 둔다. 요청에서 켤 수 있다
DISABLED_STAGES = ("load", "rag", "contamination")

# 그중 요청으로도 되살릴 수 없는 단계. load는 운영 DB에 직접 쓰기 때문에 이 서비스에서는 어떤 요청으로도 켜지지 않는다
# (rag · contamination은 stages로 켤 수 있다)
NEVER_ENABLED_STAGES = ("load",)

JOB_ID_PATTERN = re.compile(r"^[0-9a-f]{32}$")


class JobError(Exception):
    """작업 요청이 잘못됐을 때 (호출한 쪽 잘못). HTTP 400으로 바꾼다"""


class JobConflict(Exception):
    """이미 실행 중인 작업이 있을 때. HTTP 409로 바꾼다"""


class JobNotFound(Exception):
    """작업을 찾을 수 없을 때. HTTP 404로 바꾼다"""


def _now():
    return datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(timespec="seconds")


def _read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _is_zombie(pid):
    """거둬 가지 않은 자식(Z 상태)인지. /proc가 없는 환경(macOS 등)에서는 판단하지 않는다.

    `/proc/<pid>/stat`은 `pid (명령) 상태 ...` 형식이고 명령 이름에 공백 · 괄호가 들어갈 수 있어
    마지막 `) ` 뒤부터 자른다
    """
    try:
        fields = Path(f"/proc/{pid}/stat").read_text().rsplit(") ", 1)[-1].split()
        return fields[0] == "Z"
    except (OSError, IndexError):
        return False


def _alive(pid, process=None):
    """프로세스가 아직 살아 있는지.

    우리가 띄운 자식이면 `poll()`로 본다. **이 호출이 끝난 자식을 거둬 간다(reap).**
    거두지 않으면 좀비로 남는데, 좀비는 `os.kill(pid, 0)`이 성공해서 "살아 있음"으로 보인다
    — 그러면 작업 하나가 끝난 뒤로 새 작업은 영원히 409가 되고 지우지도 못한다.

    서비스를 다시 띄운 뒤라 더 이상 자식이 아니면 PID로 본다. 그때는 좀비가 생겨도 init이 거둬 간다.
    """
    if process is not None:
        return process.poll() is None
    if not pid:
        return False
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:  # 다른 사용자 소유 = 살아 있다
        return True
    return not _is_zombie(pid)


class JobStore:
    """작업 폴더를 읽고 쓴다. 작업 하나 = 폴더 하나라 서비스를 다시 띄워도 이어서 볼 수 있다.

    <jobs_dir>/<jobId>/
      ├── job.json     작업 메타 (요청 요약 · 프로세스 PID · 생성 시각)
      ├── config.json  파이프라인 설정
      ├── raw/         판결문 원문 파일
      └── log.txt      파이프라인 출력
    """

    def __init__(self, jobs_dir=None, tool_dir=None, popen=subprocess.Popen):
        self.jobs_dir = Path(jobs_dir or os.environ.get("PIPELINE_JOBS_DIR") or DEFAULT_JOBS_DIR)
        self.tool_dir = Path(tool_dir or TOOL_DIR)
        self._popen = popen  # 테스트에서 가짜로 바꾼다
        # 이 프로세스가 띄운 작업의 핸들 (jobId → Popen). 끝난 자식을 거둬 가려면 핸들이 있어야 한다(_alive)
        self._processes = {}

    # ---------------------------------------------------------------- 경로

    def job_dir(self, job_id):
        if not JOB_ID_PATTERN.match(job_id or ""):
            raise JobNotFound(f"작업 ID 형식이 아닙니다: {job_id}")
        path = self.jobs_dir / job_id
        if not path.is_dir():
            raise JobNotFound(f"작업을 찾을 수 없습니다: {job_id}")
        return path

    def state_path(self, job_id):
        """파이프라인이 쓰는 상태 파일. 작업 ID를 파이프라인 이름으로 써서 작업끼리 섞이지 않게 한다"""
        return self.tool_dir / "out" / "pipeline" / self.pipeline_name(job_id) / "state.json"

    @staticmethod
    def pipeline_name(job_id):
        return f"job-{job_id}"

    # ---------------------------------------------------------------- 생성 · 실행

    def create(self, request):
        """작업 폴더를 만들고 설정 · 판결문 파일을 쓴다. 아직 실행하지는 않는다"""
        sources = request.get("sources") or []
        if not sources:
            raise JobError("sources가 비어 있습니다 (판결문 1건 이상)")
        if len(sources) > 3:
            raise JobError("sources는 3건까지입니다 (심급별 1건)")

        job_id = uuid.uuid4().hex
        path = self.jobs_dir / job_id
        raw_dir = path / "raw"
        raw_dir.mkdir(parents=True, exist_ok=True)

        config_sources = []
        for index, source in enumerate(sources, start=1):
            text = source.get("originalText")
            if not isinstance(text, str) or not text.strip():
                raise JobError(f"sources[{index - 1}].originalText가 비어 있습니다")
            # 원문은 작업 폴더에만 둔다(공개 저장소에 남기지 않는다). 볼륨 설정은 docker-compose.pipeline.yml 참고
            source_file = raw_dir / f"source-{index}.txt"
            source_file.write_text(text, encoding="utf-8")
            entry = {"path": str(source_file)}
            if source.get("note"):
                entry["note"] = source["note"]
            config_sources.append(entry)

        config = self._build_config(job_id, config_sources, request)
        _write_json(path / "config.json", config)
        _write_json(path / "job.json", {
            "jobId": job_id,
            "status": QUEUED,
            "createdAt": _now(),
            "finishedAt": None,
            "pid": None,
            "until": request.get("until") or "axis",
            # 원문은 메타에 남기지 않는다(판결문 원문은 작업 폴더의 raw/에만 둔다)
            "sourceCount": len(config_sources),
        })
        return job_id

    def _build_config(self, job_id, config_sources, request):
        """파이프라인 설정. 요청에서 받은 stages를 덮어쓰되, 서비스에서 끄기로 한 단계는 끈 채로 둔다"""
        stages = {stage: {"enabled": False} for stage in DISABLED_STAGES}
        for stage, override in (request.get("stages") or {}).items():
            if stage not in STAGES:
                raise JobError(f"모르는 단계입니다: {stage}")
            stages.setdefault(stage, {}).update(override)
        # rag · contamination은 요청으로 다시 켤 수 있지만 load는 아니다.
        # load는 운영 DB에 직접 쓰는 단계다 — 적재는 관리자가 검수한 뒤 백엔드가 한다(COMMON-50).
        # 요청 값으로 되살아나지 않게 덮어쓰기 **뒤에** 다시 끈다
        for stage in NEVER_ENABLED_STAGES:
            stages[stage] = dict(stages.get(stage) or {}, enabled=False)
        models = request.get("models")
        if models:
            stages.setdefault("generate", {})["models"] = models
        if not (stages.get("generate") or {}).get("models"):
            raise JobError("generate 모델이 없습니다 (models 또는 stages.generate.models)")

        config = {
            "name": self.pipeline_name(job_id),
            "sources": config_sources,
            "stages": stages,
        }
        for key in ("incidentDate", "indictmentDate", "finalSourceIndex", "allowFreeTierForJudgment"):
            if request.get(key) is not None:
                config[key] = request[key]
        return config

    def start(self, job_id):
        """파이프라인을 서브프로세스로 띄운다. 이미 도는 작업이 있으면 거절한다

        한 번에 하나만 돌린다 — 외부 AI 호출 한도를 지키기 위해서다(API 명세 A-14 IMPORT_IN_PROGRESS).
        """
        running = self.running_job_id()
        if running and running != job_id:
            raise JobConflict(f"이미 실행 중인 작업이 있습니다: {running}")

        path = self.job_dir(job_id)
        meta = _read_json(path / "job.json") or {}
        log_file = path / "log.txt"
        command = [sys.executable, str(PIPELINE_SCRIPT), "run", str(path / "config.json")]
        until = meta.get("until") or "axis"
        if until:
            command += ["--until", until]

        handle = log_file.open("a", encoding="utf-8")
        try:
            process = self._popen(command, cwd=str(self.tool_dir), stdout=handle, stderr=subprocess.STDOUT)
        finally:
            handle.close()
        self._processes[job_id] = process
        meta.update(status=RUNNING, pid=process.pid, startedAt=_now())
        _write_json(path / "job.json", meta)
        return meta

    def _job_alive(self, meta):
        """그 작업의 프로세스가 살아 있는지. 이 프로세스가 띄운 작업이면 핸들로 본다 (_alive 참고)"""
        return _alive(meta.get("pid"), self._processes.get(meta.get("jobId")))

    def cancel(self, job_id):
        """도는 작업을 멈춘다. state.json은 그대로 남아 어디까지 갔는지 볼 수 있다"""
        meta = _read_json(self.job_dir(job_id) / "job.json") or {}
        pid = meta.get("pid")
        if self._job_alive(meta):
            os.kill(pid, signal.SIGTERM)
        meta.update(status=FAILED, finishedAt=_now(), error="관리자가 중단했습니다")
        _write_json(self.job_dir(job_id) / "job.json", meta)
        return meta

    # ---------------------------------------------------------------- 조회

    def running_job_id(self):
        """지금 돌고 있는 작업 ID (없으면 None)"""
        if not self.jobs_dir.is_dir():
            return None
        for path in sorted(self.jobs_dir.iterdir()):
            meta = _read_json(path / "job.json") or {}
            if meta.get("status") == RUNNING and self._job_alive(meta):
                return meta.get("jobId")
        return None

    def status(self, job_id):
        """작업 상태 + 단계별 진행 상황 (API 명세 A-15와 같은 모양)"""
        path = self.job_dir(job_id)
        meta = _read_json(path / "job.json") or {}
        config = _read_json(path / "config.json") or {}
        state = _read_json(self.state_path(job_id)) or {"stages": {}}
        stages = self._stage_list(config, state)
        status, error = self._job_status(meta, stages)
        meta = self._settle(path, meta, status, error)
        return {
            "jobId": job_id,
            "status": status,
            "stages": stages,
            "error": error,
            "createdAt": meta.get("createdAt"),
            "startedAt": meta.get("startedAt"),
            "finishedAt": meta.get("finishedAt"),
        }

    def _settle(self, path, meta, status, error):
        """끝난 작업의 메타를 한 번만 마무리한다.

        진행 상황의 출처는 어디까지나 파이프라인의 state.json이다. 다만 메타에 끝난 사실을 적어 두면
        ① finishedAt을 남길 수 있고 ② 프로세스 핸들이 없는(서비스를 다시 띄운 뒤) 상황에서도
        running_job_id가 끝난 작업을 "도는 중"으로 보지 않는다
        """
        if meta.get("status") != RUNNING or status not in (SUCCEEDED, FAILED):
            return meta
        meta.update(status=status, finishedAt=_now())
        if error:
            meta["error"] = error
        _write_json(path / "job.json", meta)
        return meta

    def _stage_list(self, config, state):
        configured = config.get("stages") or {}
        entries = state.get("stages") or {}
        stages = []
        for stage in STAGES:
            # 설정에 없으면 파이프라인 기본값(켜짐)이다. 서비스가 끄는 단계는 설정에 enabled=False로 들어가 있다
            enabled = (configured.get(stage) or {}).get("enabled", True)
            entry = entries.get(stage) or {}
            if not enabled and not entry:
                status = SKIPPED
            else:
                status = _STAGE_STATUS.get(entry.get("status"), PENDING)
            item = {"name": stage, "status": status}
            if entry.get("error"):
                item["error"] = entry["error"].splitlines()[0]
            stages.append(item)
        return stages

    def _job_status(self, meta, stages):
        """프로세스 생사 + 단계 상태로 작업 상태를 정한다. 실패 이유는 단계 쪽 error를 그대로 쓴다"""
        failed = next((s for s in stages if s["status"] == STAGE_FAILED), None)
        if failed:
            return FAILED, failed.get("error") or f"{failed['name']} 단계가 실패했습니다"
        if meta.get("status") == QUEUED:
            return QUEUED, None
        if self._job_alive(meta):
            return RUNNING, None
        if meta.get("status") == FAILED:  # cancel 등으로 이미 실패로 적힌 작업
            return FAILED, meta.get("error")
        # 프로세스가 끝났다. 돌리기로 한 단계가 모두 done이면 성공, 아니면 중간에 끊긴 것이다
        planned = [s for s in stages if s["status"] != SKIPPED]
        if planned and all(s["status"] == DONE for s in planned):
            return SUCCEEDED, None
        return FAILED, "파이프라인이 끝나기 전에 멈췄습니다 (로그를 확인하세요)"

    def result(self, job_id):
        """생성 결과 — 관리자 검수 화면이 쓸 사건 · 재판부 판결 · AI 판결 JSON (BE-93이 그대로 내려준다).

        판결문 원문(extract outputs의 source)은 넣지 않는다. 원문은 열람 기록을 남기는 A-7로만 본다
        (요구사항 FR-5-3 "원본 판결문 정보는 내부 전용").
        """
        self.job_dir(job_id)  # 없는 작업이면 여기서 JobNotFound
        state = _read_json(self.state_path(job_id)) or {"stages": {}}
        outputs = {stage: (state.get("stages", {}).get(stage) or {}).get("outputs") or {} for stage in STAGES}
        extract, selection = outputs.get("extract") or {}, outputs.get("select") or {}
        result = {
            "jobId": job_id,
            "case": _read_json(Path(extract["case"])) if extract.get("case") else None,
            "report": _read_json(Path(extract["report"])) if extract.get("report") else None,
            "courtJudgment": _read_json(Path(extract["court"])) if extract.get("court") else None,
            "judgment": _read_json(Path(selection["outputFile"])) if selection.get("outputFile") else None,
            "selection": {k: selection.get(k) for k in ("modelSpec", "runIndex", "strategy", "reason", "warnings")}
            if selection else None,
            "axis": outputs.get("axis") or None,
        }
        if result["case"] is None and result["judgment"] is None:
            raise JobNotFound(f"아직 결과가 없습니다: {job_id}")
        return result

    def log(self, job_id, tail=200):
        path = self.job_dir(job_id) / "log.txt"
        if not path.exists():
            return ""
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        return "\n".join(lines[-tail:])

    def delete(self, job_id):
        """작업 폴더를 지운다 (판결문 원문이 들어 있어 검수가 끝나면 지운다)"""
        path = self.job_dir(job_id)
        meta = _read_json(path / "job.json") or {}
        if self._job_alive(meta):
            raise JobConflict("실행 중인 작업은 지울 수 없습니다 (먼저 중단하세요)")
        self._processes.pop(job_id, None)
        shutil.rmtree(path)
        state_dir = self.state_path(job_id).parent
        if state_dir.is_dir():
            shutil.rmtree(state_dir)
