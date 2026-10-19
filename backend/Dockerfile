# ===== 1단계: 빌드 =====
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 관련 파일을 먼저 복사해 캐시를 최대한 재사용한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# 소스 복사 후 실행 가능한 jar 생성 (테스트는 CI의 test 단계에서 따로 실행)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ===== 2단계: 실행 =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# root가 아닌 사용자로 실행
RUN useradd --system --uid 10001 spring
# logs/는 구조화 로그(JSON) 파일을 쓰는 디렉터리다 (BE-71, WBS 7.2.3). /app은 root가 만들어서 spring이 쓸 수 없으니,
# logs/만 미리 만들고 소유자를 바꿔 둔다 — /app 전체를 spring 소유로 바꾸면 app.jar까지 쓸 수 있게 되어,
# 침해된 프로세스가 실행 파일을 바꿔치기해 재시작 뒤에도 남아 있게 할 수 있다(#129 리뷰). app.jar는 root 소유로 남긴다
RUN mkdir -p /app/logs && chown spring:spring /app/logs
USER spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# DB 접속 정보 등 비밀값은 이미지에 넣지 않고, 실행 시 환경변수(-e / --env-file)로 넘긴다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
