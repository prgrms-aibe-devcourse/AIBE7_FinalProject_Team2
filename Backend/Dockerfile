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
USER spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# DB 접속 정보 등 비밀값은 이미지에 넣지 않고, 실행 시 환경변수(-e / --env-file)로 넘긴다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
