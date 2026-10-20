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

from fastapi import FastAPI, HTTPException
from fastapi.responses import PlainTextResponse

from jobs import JobConflict, JobError, JobNotFound, JobStore

app = FastAPI(title="AI 판결 파이프라인 서비스", version="1.0.0", docs_url=None, redoc_url=None)
store = JobStore()


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
    """
    job_id = _handle(lambda: store.create(request))
    _handle(lambda: store.start(job_id))
    return store.status(job_id)


@app.get("/jobs/{job_id}")
def job_status(job_id: str):
    """작업 상태 + 단계별 진행 상황 (백엔드 A-15가 그대로 옮겨 내려준다)"""
    return _handle(lambda: store.status(job_id))


@app.get("/jobs/{job_id}/result")
def job_result(job_id: str):
    """생성 결과 (사건 · 재판부 판결 · AI 판결 · 가치관 축). 판결문 원문은 들어 있지 않다"""
    return _handle(lambda: store.result(job_id))


@app.get("/jobs/{job_id}/log", response_class=PlainTextResponse)
def job_log(job_id: str, tail: int = 200):
    """파이프라인 출력 꼬리 (실패 원인 확인용)"""
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
