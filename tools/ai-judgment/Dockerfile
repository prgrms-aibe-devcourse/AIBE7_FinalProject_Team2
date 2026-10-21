# AI 판결 파이프라인 서비스 (COMMON-50). 오프라인 도구(BE-31)를 백엔드가 부를 수 있게 HTTP로 감싼 컨테이너.
#
# - 파이프라인 도구 자체는 표준 라이브러리만 쓴다(README). pip으로 넣는 건 ① 서비스 층(fastapi · uvicorn)과
#   ② extract 단계가 쓰는 case-extractor 의존성(anthropic · pypdf · cryptography)뿐이다
# - 판결문 원문 · 생성 결과는 이미지에 넣지 않는다. 볼륨으로 붙인다 (deploy/docker-compose.pipeline.yml)
# - 밖으로 포트를 열지 않는다. 같은 Compose 네트워크의 백엔드만 부른다
FROM python:3.12-slim

WORKDIR /app

# 의존성을 먼저 넣어 레이어 캐시를 재사용한다
COPY service/requirements.txt service/requirements.txt
COPY case-extractor/requirements.txt case-extractor/requirements.txt
RUN pip install --no-cache-dir -r service/requirements.txt -r case-extractor/requirements.txt

# 파이프라인 도구 전체 (cases/ · out/는 .dockerignore로 빠진다 — 판결문 원문을 이미지에 넣지 않는다)
COPY . .

# root가 아닌 사용자로 실행. 작업 폴더(out/)는 볼륨이라 소유자를 맞춰 둔다
# --system을 쓰지 않는다: uid 10002가 SYS_UID_MAX(999)를 넘어 빌드 때 경고가 난다
RUN useradd --uid 10002 --user-group --no-create-home --shell /usr/sbin/nologin pipeline \
    && mkdir -p /app/out /app/cases \
    && chown -R pipeline:pipeline /app/out /app/cases
USER pipeline

EXPOSE 8000

# API 키(GEMINI_API_KEY 등)는 이미지에 넣지 않고 실행할 때 환경변수로 넘긴다
# 워커 1개 — 파이프라인은 한 번에 한 작업만 돌린다(외부 AI 호출 한도, jobs.py)
CMD ["uvicorn", "app:app", "--app-dir", "service", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
