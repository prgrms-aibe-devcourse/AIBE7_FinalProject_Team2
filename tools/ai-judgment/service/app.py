"""AI 판결 파이프라인 HTTP 서비스 (COMMON-50).

`tools/ai-judgment`의 오프라인 파이프라인(BE-31)을 백엔드(Spring Boot)가 부를 수 있게 감싼 얇은 층이다.
파이프라인 로직은 건드리지 않는다 — 요청을 설정 파일로 옮기고, 서브프로세스로 돌리고, state.json을 읽어
상태를 돌려줄 뿐이다(jobs.py).

**내부 전용이다.** docker-compose 내부 네트워크에서 백엔드만 부른다(포트를 호스트로 열지 않는다,
deploy/docker-compose.pipeline.yml). 그래서 이 서비스 자체에는 인증을 두지 않았다 — 관리자 인증 · CSRF는
백엔드의 관리자 체인(API 명세 8-1)이 이미 하고, 여기에 또 두면 비밀값만 하나 더 늘어난다.
밖에서 닿을 수 있게 바뀌면 그때는 인증이 필요하다(docs/decisions/COMMON-50.md "추후 재검토").
"""

from __future__ import annotations

import contextlib
import logging
import threading

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import PlainTextResponse

from jobs import MAX_LOG_TAIL, JobConflict, JobError, JobNotFound, JobStore, run_cleanup_loop

# uvicorn은 자기 로거(uvicorn*)만 설정하고 루트에는 핸들러를 두지 않는다. 그대로 두면 jobs.py가 남기는
# INFO 기록 — 어떤 작업을 왜 지웠는지(COMMON-51) — 가 아무 데도 찍히지 않는다. 서비스 진입점에서 한 번
# 맞춘다. uvicorn이 로깅을 먼저 설정한 뒤 이 모듈을 불러오므로 서로 덮어쓰지 않는다
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

logger = logging.getLogger(__name__)
store = JobStore()


@contextlib.asynccontextmanager
async def lifespan(app):
    """서비스가 떠 있는 동안 오래된 작업 정리 스레드를 함께 둔다 (COMMON-51).

    작업 하나를 지우는 데 파일 삭제가 몇 번 드는 정도라 별도 스케줄러(APScheduler 등)를 들이지 않는다.
    워커가 1개라(Dockerfile `--workers 1`) 스레드도 하나뿐이다. 정리 자체는 jobs.py에 있다
    """
    stop = threading.Event()
    thread = threading.Thread(target=run_cleanup_loop, args=(store, stop), name="job-cleanup", daemon=True)
    thread.start()
    try:
        yield
    finally:
        stop.set()
        thread.join(timeout=5)


app = FastAPI(title="AI 판결 파이프라인 서비스", version="1.0.0", docs_url=None, redoc_url=None,
              lifespan=lifespan)


def _handle(call):
    """작업 예외를 HTTP 상태로 바꾼다. 메시지에 판결문 원문 · API 키가 들어가지 않게 jobs.py에서 관리한다"""
    try:
        return call()
    except JobNotFound as e:
        raise HTTPException(status_code=404, detail=str(e)) from e
    except JobConflict as e:
        raise HTTPException(status_code=409, detail=str(e)) from e
    except JobError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e


@app.get("/health")
def health():
    """컨테이너 헬스체크. 파이프라인을 돌려 보지는 않는다(호출 비용이 든다)"""
    return {"status": "UP", "runningJobId": store.running_job_id()}


@app.post("/jobs", status_code=202)
def create_job(request: dict):
    """작업을 만들고 바로 시작한다. 파이프라인은 수십 분이 걸려 응답을 기다리지 않는다(202).

    본문: {"sources": [{"originalText": "...", "note": "..."}], "models": ["gemini:..."],
           "stages": {...}, "until": "axis", "incidentDate": "...", "indictmentDate": "..."}

    시작하지 못하면 만들어 둔 작업을 **지우고** 오류를 돌려준다 (PR #219 리뷰 반영). 그냥 두면 이미 도는
    작업이 있어 409로 거절된 요청의 판결문 원문이 디스크에 남는다 (FR-5-3).
    """
    job_id = _handle(lambda: store.create(request))
    try:
        _handle(lambda: store.start(job_id))
    except Exception:
        _discard(job_id)
        raise
    return store.status(job_id)


def _discard(job_id):
    """시작하지 못한 작업을 치운다. 치우다 실패해도 원래 오류를 가리지 않는다"""
    try:
        store.delete(job_id)
    except Exception:  # noqa: BLE001 - 정리 실패가 원래 오류를 덮지 않게 한다
        logger.warning("시작하지 못한 작업을 지우지 못했습니다: %s", job_id)


@app.get("/jobs/{job_id}")
def job_status(job_id: str):
    """작업 상태 + 단계별 진행 상황 (백엔드 A-15가 그대로 옮겨 내려준다)"""
    return _handle(lambda: store.status(job_id))


@app.get("/jobs/{job_id}/result")
def job_result(job_id: str):
    """생성 결과 (사건 · 재판부 판결 · AI 판결 · 가치관 축). 판결문 원문은 들어 있지 않다"""
    return _handle(lambda: store.result(job_id))


@app.get("/jobs/{job_id}/log", response_class=PlainTextResponse)
def job_log(job_id: str, tail: int = Query(default=200, ge=1, le=MAX_LOG_TAIL)):
    """파이프라인 출력 꼬리 (실패 원인 확인용). tail=0은 로그 전체가 되므로 1부터 받는다"""
    return _handle(lambda: store.log(job_id, tail=tail))


@app.post("/jobs/{job_id}/cancel")
def cancel_job(job_id: str):
    """도는 작업을 멈춘다. 어디까지 갔는지는 state.json에 남는다"""
    _handle(lambda: store.cancel(job_id))
    return store.status(job_id)


@app.delete("/jobs/{job_id}", status_code=204)
def delete_job(job_id: str):
    """작업 폴더를 지운다 (판결문 원문이 들어 있어 검수가 끝나면 지운다)"""
    _handle(lambda: store.delete(job_id))
