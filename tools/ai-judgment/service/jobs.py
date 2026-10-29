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
import logging
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import uuid
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent.parent
PIPELINE_SCRIPT = TOOL_DIR / "pipeline.py"
DEFAULT_JOBS_DIR = TOOL_DIR / "out" / "jobs"

# 파이프라인이 작업 이름(job-<작업ID>)으로 파일 · 폴더를 만드는 곳. 작업을 지울 때 여기까지 지워야
# 판결문 원문이 남지 않는다 (PR #219 리뷰 반영, FR-5-3)
#   cases/job-<id>.source_internal.json  ← 원문. extract가 쓴다 (pipeline.py CASES_DIR)
#   cases/job-<id>.case.json · .court_judgment_internal.json · .report.json
#   out/runs/job-<id>-<해시>/            ← generate 회차 결과 (pipeline.py RUNS_DIR)
# out/pipeline/job-<id>/ (state · references · contamination · axis)는 state_path로 따로 지운다
def _classify_result(classify):
    """사건 분류 결과(BE-123)를 **내용으로** 돌려준다. 축 투표와 같은 이유로 `classificationFile` 경로는 빼고 파일의
    표 · 플래그 근거(votes · flags · recovery)를 넣는다. 적재(legal_case 민감 플래그 컬럼)는 BE-125다."""
    if not classify:
        return None
    data = _read_json(Path(classify["classificationFile"])) if classify.get("classificationFile") else None
    result = {key: value for key, value in classify.items() if key != "classificationFile"}
    for key in ("votes", "flags", "recovery"):
        if data and key in data:
            result[key] = data[key]
    return result


ARTIFACT_DIRS = ("cases", "out/runs")

# 파이프라인 단계 순서 (pipeline.py STAGES와 같아야 한다)
STAGES = ("extract", "court", "contamination", "rag", "generate", "select", "axis", "classify", "script", "load")

# pipeline.py에서 **기본값이 꺼짐**인 단계 (pipeline.py DEFAULT_CONFIG의 stages.script · classify.enabled = False).
# 나머지 단계는 설정에 없으면 돈다는 뜻이라, 이 목록을 따로 두지 않으면 요청하지 않은 대본 단계가
# 영영 PENDING으로 남아 끝난 작업이 FAILED로 보인다 (BE-108에서 script를 STAGES에 넣으며 드러났다)
DEFAULT_OFF_STAGES = ("classify", "script")

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

# 로그 꼬리 요청의 상한 (log 참고)
MAX_LOG_TAIL = 2000

# 끝난 작업을 자동으로 지우기까지의 보관 기간 (COMMON-51). PIPELINE_JOB_RETENTION_DAYS로 바꾼다.
# 백엔드가 검수 뒤 DELETE /jobs/{id}를 부르는 것이 정상 경로이고, 이 정리는 그 호출이 빠졌을 때를 위한
# **마지막 안전장치**다 (FR-5-3 "원본 판결문 정보는 내부 전용"). 기본 7일은 운영에서 실제 검수 소요
# 시간을 보기 전의 보수적인 값이다 — 관찰한 뒤 환경변수로 조정한다 (docs/decisions/COMMON-51.md)
DEFAULT_RETENTION_DAYS = 7

# 정리를 다시 도는 간격. 보관 기간이 날 단위라 시간 단위면 충분하다 — 간격을 짧게 해도 지워지는 시점이
# 의미 있게 빨라지지 않는다
CLEANUP_INTERVAL_SECONDS = 6 * 60 * 60

logger = logging.getLogger(__name__)


class JobError(Exception):
    """작업 요청이 잘못됐을 때 (호출한 쪽 잘못). HTTP 400으로 바꾼다"""


class JobConflict(Exception):
    """이미 실행 중인 작업이 있을 때. HTTP 409로 바꾼다"""


class JobNotFound(Exception):
    """작업을 찾을 수 없을 때. HTTP 404로 바꾼다"""


def _now():
    return datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(timespec="seconds")


def _parse_time(value):
    """작업 메타의 시각 문자열을 datetime으로. 읽을 수 없으면 None.

    시간대가 없는 값(손으로 고친 메타 등)은 **현지 시간**으로 본다 — `_now()`가 현지 시간대로 쓰기 때문이다
    """
    if not isinstance(value, str):
        return None
    try:
        parsed = datetime.datetime.fromisoformat(value)
    except ValueError:
        return None
    return parsed.astimezone() if parsed.tzinfo is None else parsed


def _read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _read_meta(path):
    """작업 폴더의 job.json. 없거나 깨졌거나 **객체가 아니면** 빈 메타로 본다 (PR #326 리뷰 반영).

    `_read_json(...) or {}`만으로는 `"문자열"` · `[1, 2]`처럼 JSON으로는 올바른 값이 그대로 돌아와,
    뒤의 `meta.get(...)`이 AttributeError를 내고 정리 순회 · 도는 작업 확인이 통째로 멈춘다
    """
    meta = _read_json(path / "job.json")
    return meta if isinstance(meta, dict) else {}


def _mtime(path):
    """파일 수정 시각 (UTC). 없으면 None"""
    try:
        return datetime.datetime.fromtimestamp(path.stat().st_mtime, tz=datetime.timezone.utc)
    except OSError:
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


def _terminate(pid):
    """작업 프로세스를 멈춘다.

    파이프라인은 단계에 따라 자식 프로세스를 띄운다(psql 등). 부모에게만 SIGTERM을 보내면 그 자식이 남아
    계속 돌 수 있다. 그래서 **프로세스 그룹째** 보낸다 — start_new_session=True로 띄워 그룹 ID가 pid와 같다.
    그룹을 찾지 못하는 환경이면 프로세스 하나에만 보낸다 (PR #219 리뷰 반영).
    """
    if not pid:
        return
    try:
        os.killpg(os.getpgid(pid), signal.SIGTERM)
        return
    except (ProcessLookupError, PermissionError, OSError):
        pass
    try:
        os.kill(pid, signal.SIGTERM)
    except (ProcessLookupError, PermissionError):
        pass


def _axis_result(axis):
    """가치관 축 투표 결과를 **내용으로** 돌려준다.

    단계 산출물에는 `votesFile`(컨테이너 안 경로)만 있어서 그대로 내보내면 부르는 쪽이 읽을 수 없다.
    파일을 읽어 factors를 넣고 경로는 뺀다 — 백엔드가 쓸 수 없는 값이고 내부 경로를 밖으로 내보낼 이유도 없다.
    """
    if not axis:
        return None
    votes = _read_json(Path(axis["votesFile"])) if axis.get("votesFile") else None
    result = {key: value for key, value in axis.items() if key != "votesFile"}
    result["factors"] = (votes or {}).get("factors") or []
    return result


def _script_result(script):
    """사건 대본(증거조사 대사)을 **내용으로** 돌려준다 (BE-107 생성 → BE-108 적재).

    단계 산출물에는 `evidenceFile`(컨테이너 안 경로)만 있어서 그대로 내보내면 부르는 쪽이 읽을 수 없다.
    axis의 `votesFile`과 똑같은 이유로 파일을 읽어 evidence를 넣고 경로는 뺀다 — 백엔드가 쓸 수 없는
    값이고 내부 경로를 밖으로 내보낼 이유도 없다.

    `publishable`(생성 실패 · 호출 한도 중단으로 증거 카드와 대사가 어긋남)은 그대로 넘긴다.
    적재할지 · 공개할지는 백엔드와 관리자가 판단한다(BE-108).

    파일에서 가져오는 값은 `evidence`와 `createdAt`뿐이다. `createdAt`(대본을 만든 시각)은 단계 산출물에
    없고 증거 파일에만 있어서, 여기서 올려 주지 않으면 적재가 읽을 값이 사라진다(PR #300 리뷰).
    나머지(`stopped` · `model` · `cast` · `balanceWarning` · `publishable`)는 **단계 산출물 쪽이 맞다** —
    증거 파일에는 `stopped`가 아예 없어서 파일에서 가져오면 항상 false가 된다.

    대본 전체(BE-121, `stages.script.full`)를 만들었으면 `scriptFile`도 같은 이유로 경로를 빼고 내용을
    `fullScript`로 넣는다. 적재(`case_script` 확장)는 BE-126이다 — 그전까지 백엔드는 이 값을 읽지 않는다.
    """
    if not script:
        return None
    data = _read_json(Path(script["evidenceFile"])) if script.get("evidenceFile") else None
    result = {key: value for key, value in script.items() if key not in ("evidenceFile", "scriptFile")}
    result["evidence"] = (data or {}).get("evidence") or []
    created_at = (data or {}).get("createdAt")
    if created_at:
        result["createdAt"] = created_at
    if script.get("scriptFile"):
        result["fullScript"] = _read_json(Path(script["scriptFile"]))
    return result


def run_cleanup_loop(store, stop, interval=CLEANUP_INTERVAL_SECONDS):
    """서비스가 내려갈 때까지 주기적으로 정리한다 (COMMON-51). 시작하자마자 한 번 돈다.

    `stop`은 `threading.Event`다 — 내려가라는 신호를 받으면 기다리던 중에도 바로 돌아온다.
    **한 번 실패해도 끝내지 않는다**: 다음 주기에 다시 지우면 되고, 정리가 조용히 멈추면 판결문 원문이
    남는 것을 아무도 모르게 된다 (FR-5-3). 이 함수는 HTTP 층을 모른다 — app.py가 스레드로 띄운다
    """
    while True:
        try:
            store.cleanup()
        except Exception:  # noqa: BLE001 - 정리 실패로 스레드가 끝나면 안 된다
            logger.exception("오래된 작업 정리에 실패했습니다")
        if stop.wait(interval):
            return


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
        # "도는 작업이 있나" 확인과 실제 띄우기를 묶는 잠금 (start 참고)
        self._start_lock = threading.Lock()

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
        """작업 폴더를 만들고 설정 · 판결문 파일을 쓴다. 아직 실행하지는 않는다.

        **요청을 전부 검증한 뒤에야 파일을 쓴다** (PR #219 리뷰 반영). 원문을 먼저 쓰고 나중에 검증하면,
        400으로 거절한 요청의 판결문 원문이 디스크에 남는다
        """
        sources = request.get("sources")
        if not isinstance(sources, list) or not sources:
            raise JobError("sources가 비어 있습니다 (판결문 1건 이상)")
        if len(sources) > 3:
            raise JobError("sources는 3건까지입니다 (심급별 1건)")

        # 1) 먼저 검증만 한다. 아직 아무것도 쓰지 않는다
        texts = []
        for index, source in enumerate(sources):
            if not isinstance(source, dict):
                raise JobError(f"sources[{index}]는 객체여야 합니다")
            text = source.get("originalText")
            if not isinstance(text, str) or not text.strip():
                raise JobError(f"sources[{index}].originalText가 비어 있습니다")
            note = source.get("note")
            if note is not None and not isinstance(note, str):
                raise JobError(f"sources[{index}].note는 문자열이어야 합니다")
            texts.append((text, note))

        job_id = uuid.uuid4().hex
        path = self.jobs_dir / job_id
        raw_dir = path / "raw"
        # 원문은 작업 폴더에만 둔다(공개 저장소에 남기지 않는다). 볼륨 설정은 docker-compose.pipeline.yml 참고
        config_sources = []
        for index, (text, note) in enumerate(texts, start=1):
            entry = {"path": str(raw_dir / f"source-{index}.txt")}
            if note:
                entry["note"] = note
            config_sources.append(entry)
        config = self._build_config(job_id, config_sources, request)  # 여기서 실패해도 쓴 파일이 없다

        # 2) 검증이 모두 끝난 뒤에만 쓴다
        raw_dir.mkdir(parents=True, exist_ok=True)
        for index, (text, _) in enumerate(texts, start=1):
            (raw_dir / f"source-{index}.txt").write_text(text, encoding="utf-8")
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
        overrides = request.get("stages")
        if overrides is not None and not isinstance(overrides, dict):
            raise JobError("stages는 객체여야 합니다")
        for stage, override in (overrides or {}).items():
            if stage not in STAGES:
                raise JobError(f"모르는 단계입니다: {stage}")
            # dict가 아니면 update()에서 TypeError가 나 500이 된다. 요청이 잘못된 것이므로 400으로 돌려준다
            if not isinstance(override, dict):
                raise JobError(f"stages.{stage}는 객체여야 합니다")
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

        **확인과 띄우기를 잠금으로 묶는다** (PR #219 리뷰 반영). FastAPI는 `def` 엔드포인트를 스레드 풀에서
        돌리기 때문에, 묶지 않으면 두 요청이 나란히 "도는 작업 없음"을 통과해 파이프라인이 둘 다 뜬다.
        워커는 1개라서(Dockerfile `--workers 1`) 프로세스 안 잠금으로 충분하다.
        """
        with self._start_lock:
            running = self.running_job_id()
            if running and running != job_id:
                raise JobConflict(f"이미 실행 중인 작업이 있습니다: {running}")

            path = self.job_dir(job_id)
            meta = _read_meta(path)
            log_file = path / "log.txt"
            command = [sys.executable, str(PIPELINE_SCRIPT), "run", str(path / "config.json")]
            until = meta.get("until") or "axis"
            if until:
                command += ["--until", until]

            handle = log_file.open("a", encoding="utf-8")
            try:
                # start_new_session: 파이프라인을 새 프로세스 그룹의 우두머리로 띄운다.
                # 중단할 때 파이프라인이 띄운 자식까지 그룹째 멈추기 위해서다 (cancel 참고)
                process = self._popen(command, cwd=str(self.tool_dir), stdout=handle,
                                      stderr=subprocess.STDOUT, start_new_session=True)
            finally:
                handle.close()
            self._processes[job_id] = process
            # 메타를 쓰고 나서야 잠금을 놓는다 — 다음 요청의 running_job_id가 이 작업을 보게 하려면 필요하다.
            # 다시 돌리는 작업이면 지난번 finishedAt을 지운다 — 남겨 두면 끝난 지 오래된 작업으로 보여
            # 자동 정리가 방금 만든 결과를 지울 수 있다 (PR #326 리뷰 반영, BE-107 `--from script` 재실행)
            meta.update(status=RUNNING, pid=process.pid, startedAt=_now(), finishedAt=None)
            _write_json(path / "job.json", meta)
            return meta

    def _job_alive(self, meta, job_id=None):
        """그 작업의 프로세스가 살아 있는지. 이 프로세스가 띄운 작업이면 핸들로 본다 (_alive 참고)

        핸들은 **폴더 이름(job_id)으로도** 찾는다 (PR #326 리뷰 반영). job.json이 깨져 메타에 jobId가 없어도
        도는 작업을 "끝난 작업"으로 잘못 보고 지우지 않기 위해서다
        """
        return _alive(meta.get("pid"), self._processes.get(job_id or meta.get("jobId")))

    def cancel(self, job_id):
        """도는 작업을 멈춘다. state.json은 그대로 남아 어디까지 갔는지 볼 수 있다.

        **이미 끝난 작업은 건드리지 않는다** (PR #219 리뷰 반영). 전에는 성공으로 끝난 작업에 중단을 불러도
        FAILED로 덮어써서, 끝난 결과를 되돌릴 수 없게 망가뜨렸다.
        """
        path = self.job_dir(job_id)
        meta = _read_meta(path)
        if not self._job_alive(meta, job_id):
            return meta
        _terminate(meta.get("pid"))
        meta.update(status=FAILED, finishedAt=_now(), error="관리자가 중단했습니다")
        _write_json(path / "job.json", meta)
        return meta

    # ---------------------------------------------------------------- 조회

    def running_job_id(self):
        """지금 돌고 있는 작업 ID (없으면 None)"""
        if not self.jobs_dir.is_dir():
            return None
        for path in sorted(self.jobs_dir.iterdir()):
            meta = _read_meta(path)
            if meta.get("status") == RUNNING and self._job_alive(meta, path.name):
                return meta.get("jobId")
        return None

    def status(self, job_id):
        """작업 상태 + 단계별 진행 상황 (API 명세 A-15와 같은 모양)"""
        path = self.job_dir(job_id)
        meta = _read_meta(path)
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
            # 설정에 없으면 파이프라인 기본값이다 — 대부분 켜짐이지만 DEFAULT_OFF_STAGES는 꺼짐이다.
            # 서비스가 끄는 단계(DISABLED_STAGES)는 설정에 enabled=False로 들어가 있다
            enabled = (configured.get(stage) or {}).get("enabled", stage not in DEFAULT_OFF_STAGES)
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

        재판부 판결은 **court 단계의 courtDraft**를 쓴다. extract의 court 산출물
        (`*.court_judgment_internal.json`)은 형벌 값만 담은 사전 학습 점검 전용 파일이라
        (case-extractor/schema.py build_court_judgment) 적재에 필요한 judgmentFactors · 요약 ·
        판결 이유가 없다. 처음에는 그 파일을 내보냈는데 적재(BE-93 2차)를 붙이면서 드러났다.
        """
        self.job_dir(job_id)  # 없는 작업이면 여기서 JobNotFound
        state = _read_json(self.state_path(job_id)) or {"stages": {}}
        outputs = {stage: (state.get("stages", {}).get(stage) or {}).get("outputs") or {} for stage in STAGES}
        extract, court = outputs.get("extract") or {}, outputs.get("court") or {}
        selection = outputs.get("select") or {}
        result = {
            "jobId": job_id,
            "case": _read_json(Path(extract["case"])) if extract.get("case") else None,
            "report": _read_json(Path(extract["report"])) if extract.get("report") else None,
            "courtDraft": _read_json(Path(court["courtDraft"])) if court.get("courtDraft") else None,
            "judgment": _read_json(Path(selection["outputFile"])) if selection.get("outputFile") else None,
            "selection": {k: selection.get(k) for k in ("modelSpec", "runIndex", "strategy", "reason", "warnings")}
            if selection else None,
            "axis": _axis_result(outputs.get("axis") or {}),
            "classify": _classify_result(outputs.get("classify") or {}),
            # 대본은 안 돌렸으면 None이다. 사건 · 판결만 있으면 적재는 그대로 되고, 대본은 나중에 들어와도 된다(BE-108)
            "script": _script_result(outputs.get("script") or {}),
        }
        if result["case"] is None and result["judgment"] is None:
            raise JobNotFound(f"아직 결과가 없습니다: {job_id}")
        return result

    def log(self, job_id, tail=200):
        """파이프라인 출력의 마지막 tail줄.

        tail은 1 ~ MAX_LOG_TAIL로 맞춘다. 0을 그대로 쓰면 `lines[-0:]`이 **로그 전체**가 되어,
        꼬리만 달라던 요청에 수만 줄을 돌려주게 된다 (PR #219 리뷰 반영)
        """
        path = self.job_dir(job_id) / "log.txt"
        if not path.exists():
            return ""
        try:
            tail = int(tail)
        except (TypeError, ValueError):
            raise JobError("tail은 정수여야 합니다")
        tail = max(1, min(tail, MAX_LOG_TAIL))
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        return "\n".join(lines[-tail:])

    def delete(self, job_id):
        """작업이 남긴 것을 **모두** 지운다. 검수가 끝나면 백엔드가 부른다.

        **판결문 원문을 지우는 것이 이 함수의 핵심이다** (요구사항 FR-5-3). 파이프라인은 한 작업의 산출물을
        네 군데에 나눠 쓰기 때문에, 작업 폴더만 지우면 원문(`cases/job-<id>.source_internal.json`)이 볼륨에
        그대로 남는다 (PR #219 리뷰에서 발견).

          1. <jobs_dir>/<id>/          입력 원문 raw/ · 설정 · 로그
          2. out/pipeline/job-<id>/    state.json · references · contamination · axis
          3. cases/job-<id>.*          extract · court 산출물 (**원문 포함**)
          4. out/runs/job-<id>-*/      generate 회차 결과

        3 · 4는 state.json의 outputs 경로가 아니라 **작업 이름으로 훑어서** 지운다. 단계가 파일을 쓰다가
        중간에 실패하면 outputs에 경로가 기록되지 않아, 경로만 보고 지우면 원문이 남기 때문이다.

        **확인과 삭제를 `start`와 같은 잠금으로 묶는다** (PR #326 리뷰 반영). 묶지 않으면 "도는 중 아님"을
        확인한 뒤 지우는 사이에 같은 작업이 다시 시작돼, 파이프라인이 막 지워지는 `cases/`를 읽게 된다.
        """
        with self._start_lock:
            self._delete(job_id)

    def _delete(self, job_id):
        """delete의 본체. **`_start_lock`을 잡은 채로만 부른다** (cleanup도 같은 잠금 안에서 부른다)"""
        path = self.job_dir(job_id)
        meta = _read_meta(path)
        if self._job_alive(meta, job_id):
            raise JobConflict("실행 중인 작업은 지울 수 없습니다 (먼저 중단하세요)")
        self._processes.pop(job_id, None)
        self._remove_artifacts(job_id)
        state_dir = self.state_path(job_id).parent
        if state_dir.is_dir():
            shutil.rmtree(state_dir)
        shutil.rmtree(path)  # 작업 폴더는 마지막에 — 앞에서 실패하면 흔적이 남아 다시 지울 수 있다

    def cleanup(self, retention_days=None, now=None):
        """보관 기간이 지난 작업을 지운다 (COMMON-51). 지운 작업 ID 목록을 돌려준다.

        백엔드가 검수를 마치고 `DELETE /jobs/{id}`를 부르는 것이 정상 경로다. 그런데 백엔드가 그 호출 전에
        재시작 · 교체되거나, 검수가 미완으로 남거나, 호출이 실패하고 재시도되지 않으면 **판결문 원문이
        볼륨에 무기한 남는다.** 이 정리가 그 경우의 마지막 안전장치다 (FR-5-3).

        규칙:
          - **도는 작업은 건드리지 않는다.** 며칠째 도는 작업도 마찬가지다(끝나야 지울 수 있다).
          - 나이는 **마지막 기록**으로 본다 — `finishedAt` · `startedAt` · `createdAt` · `log.txt` · `state.json`
            중 가장 늦은 값이다(`_last_touched`). 셋 다 없으면 작업 폴더 수정 시각을 쓴다. 메타가 없거나 깨진
            폴더도 원문을 담고 있을 수 있어, 읽을 값이 없다고 건너뛰면 그 폴더만 영영 남는다.
          - 지우는 범위는 `delete`와 같다(작업 폴더 · state · cases · runs). 같은 함수를 그대로 쓴다.
          - 한 작업의 "살아 있는지 확인 → 나이 확인 → 삭제"를 `start`와 같은 잠금으로 묶는다. 그 사이에
            같은 작업이 다시 시작되지 못한다.
          - 한 건이 실패해도 나머지는 계속 지운다. 다음 주기에 다시 시도한다.
        """
        days = self._retention_days() if retention_days is None else retention_days
        if days <= 0:  # 0 이하 = 자동 정리를 끈다 (운영에서 잠시 꺼 둘 수 있게)
            return []
        if not self.jobs_dir.is_dir():
            return []
        cutoff = (now or datetime.datetime.now(datetime.timezone.utc)) - datetime.timedelta(days=days)

        deleted = []
        for path in sorted(self.jobs_dir.iterdir()):
            if not path.is_dir():
                continue
            with self._start_lock:
                meta = _read_meta(path)
                if self._job_alive(meta, path.name):
                    continue
                when = self._last_touched(path, meta)
                if when is None or when > cutoff:
                    continue
                try:
                    self._delete(path.name)
                except (JobNotFound, JobConflict, OSError) as e:
                    # 작업 ID 형식이 아닌 폴더(JobNotFound)는 우리 것이 아니므로 그대로 둔다
                    logger.warning("오래된 작업을 지우지 못했습니다: %s (%s)", path.name, e)
                    continue
            deleted.append(path.name)
            logger.info("오래된 작업을 지웠습니다: %s (마지막 기록 %s, 보관 %d일)", path.name,
                        when.isoformat(timespec="seconds"), days)
        return deleted

    @staticmethod
    def _retention_days():
        """PIPELINE_JOB_RETENTION_DAYS (없거나 숫자가 아니면 기본값)"""
        raw = os.environ.get("PIPELINE_JOB_RETENTION_DAYS")
        if raw is None or raw.strip() == "":
            return DEFAULT_RETENTION_DAYS
        try:
            return int(raw)
        except ValueError:
            # 오타 하나로 정리가 조용히 멈추지 않게, 기본값으로 돌아가고 로그를 남긴다
            logger.warning("PIPELINE_JOB_RETENTION_DAYS 값이 정수가 아닙니다 (%r). 기본 %d일을 씁니다",
                           raw, DEFAULT_RETENTION_DAYS)
            return DEFAULT_RETENTION_DAYS

    def _last_touched(self, path, meta):
        """그 작업에 마지막으로 뭔가 기록된 시각. 아무것도 없으면 폴더 수정 시각으로 본다.

        메타의 시각 하나만 믿지 않고 **가장 늦은 값**을 쓴다 (PR #326 리뷰 반영). 다시 돌린 작업이 끝났는데
        아직 아무도 `status()`를 부르지 않으면 메타에는 옛 시각만 남아 있다. 로그 · state.json은 파이프라인이
        도는 동안 계속 갱신되므로, 메타가 늦거나 깨져도 "최근에 돈 작업"을 알아본다
        """
        candidates = [_parse_time(meta.get(key)) for key in ("finishedAt", "startedAt", "createdAt")]
        candidates += [_mtime(path / "log.txt"), _mtime(self.state_path(path.name))]
        candidates = [when for when in candidates if when is not None]
        return max(candidates) if candidates else _mtime(path)

    def _remove_artifacts(self, job_id):
        """ARTIFACT_DIRS에서 이 작업 이름으로 시작하는 파일 · 폴더를 지운다.

        작업 ID는 job_dir에서 이미 32자리 16진수로 확인했으므로 glob 패턴에 다른 문자가 섞이지 않는다
        """
        prefix = self.pipeline_name(job_id)
        for relative in ARTIFACT_DIRS:
            base = self.tool_dir / relative
            if not base.is_dir():
                continue
            for target in base.glob(f"{prefix}*"):
                if target.is_dir():
                    # 실패를 숨기지 않는다 (PR #326 리뷰 반영). 숨기면 delete가 성공으로 끝나 작업 폴더까지
                    # 지워지고, 정리는 작업 폴더만 훑으므로 남은 회차 결과를 다시 찾지 못한다
                    try:
                        shutil.rmtree(target)
                    except FileNotFoundError:
                        pass  # 그새 이미 지워졌다
                else:
                    target.unlink(missing_ok=True)
