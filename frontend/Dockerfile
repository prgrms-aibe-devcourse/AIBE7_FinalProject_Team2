# 운영 웹 이미지 (BE-58, WBS 7.1.1): Vite 빌드 결과 + Nginx(정적 파일 · /api 프록시)
# 빌드: frontend 폴더에서 docker build -t lawnambul-web .   (보통은 deploy/docker-compose.prod.yml로 빌드한다)

# ===== 1단계: 빌드 =====
# CI(frontend-ci.yml) · 팀원 로컬과 같은 Node 메이저 버전
FROM node:24-alpine AS build
WORKDIR /app

# 의존성 파일을 먼저 복사해 캐시를 재사용한다 (소스만 바뀌면 npm ci를 다시 하지 않음)
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# 운영 빌드는 반드시 실제 백엔드로 요청한다. 값이 없으면 config.js 기본값(mock)이 빌드에 들어가
# 배포된 앱이 목 데이터만 보여 준다 (frontend-ci.yml과 같은 이유). 빌드 시점에 값이 박힌다
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_API_MODE=real
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_API_MODE=$VITE_API_MODE
RUN npm run build

# ===== 2단계: 실행 =====
FROM nginx:1.28-alpine

# 기본 설정을 지우고 우리 설정만 둔다 (/api 프록시 · SPA fallback)
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
# 80 · 443 server가 함께 include하는 앱 location · /api 공통 설정 · Let's Encrypt 확인 경로 (BE-75)
COPY nginx/snippets/ /etc/nginx/snippets/

# openssl (BE-75 리뷰 반영): 40-enable-https.sh가 인증서에 도메인 · www가 들어 있는지(SAN) 확인할 때 쓴다.
# nginx:alpine에는 TLS 라이브러리만 있고 openssl 명령은 없다
RUN apk add --no-cache openssl

# HTTPS (BE-75): 인증서가 있을 때만 컨테이너 시작 시 https server를 만든다 (docs/decisions/BE-75.md)
# - 템플릿은 /etc/nginx/templates/가 아닌 곳에 둔다. 공식 이미지가 그 폴더를 인증서 확인 없이 conf.d로 바꿔 넣기 때문이다
# - 스크립트는 공식 이미지의 시작 단계(/docker-entrypoint.d/, 이름 순서)에서 실행된다. 실행 권한이 있어야 실행된다
COPY nginx/https.conf.template /etc/nginx/lawnambul/https.conf.template
COPY --chmod=755 nginx/40-enable-https.sh /docker-entrypoint.d/40-enable-https.sh
# 429 전용 로그 폴더 · 크기 상한 (BE-63). 폴더는 Nginx 설정 검사(nginx -t) 전에 있어야 하므로 이미지에 만들어 둔다.
# 모니터링 에이전트 오버레이가 이 자리에 볼륨을 붙여 Alloy와 나눠 쓴다(없으면 컨테이너 안에만 남는다)
RUN mkdir -p /var/log/nginx-ratelimit
COPY --chmod=755 nginx/41-trim-rate-limit-log.sh /docker-entrypoint.d/41-trim-rate-limit-log.sh

# 빌드 결과만 복사한다. preview.html · sample.html은 vite build 입력이 아니라 운영 이미지에 들어가지 않는다
COPY --from=build /app/dist /usr/share/nginx/html

# 배포 확인용 버전 파일 (BE-60): 어떤 커밋의 이미지가 떠 있는지 /version.json으로 알린다.
# GitHub Actions가 main 배포 뒤 이 값이 새 커밋이 될 때까지 기다려 배포 성공 · 실패를 판단한다. 내용은 커밋 해시뿐이다
# 마지막 단계에 둔다: 값이 커밋마다 바뀌므로 앞 단계(npm ci · build) 캐시를 깨지 않게
ARG GIT_SHA=local
RUN printf '{"revision":"%s"}\n' "$GIT_SHA" > /usr/share/nginx/html/version.json

EXPOSE 80 443
