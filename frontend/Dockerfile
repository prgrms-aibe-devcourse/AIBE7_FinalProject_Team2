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

# 빌드 결과만 복사한다. preview.html · sample.html은 vite build 입력이 아니라 운영 이미지에 들어가지 않는다
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
