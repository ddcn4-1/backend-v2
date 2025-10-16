# module-queue/Dockerfile

# Build stage
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Gradle 래퍼와 설정 파일 복사
COPY gradle gradle
COPY gradlew .
COPY settings.gradle .
COPY build.gradle .
RUN chmod +x gradlew

# Queue 모듈의 build.gradle 복사
COPY module-queue/build.gradle module-queue/

# 의존성 다운로드 (캐시 최적화)
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사
COPY module-common module-common
COPY module-core module-core
COPY module-metric module-metric
COPY module-auth module-auth
COPY module-api module-api
COPY module-queue module-queue
# (api 모듈은 queue 빌드에 직접 필요 없으면 생략 가능)

# Queue 모듈만 빌드
RUN ./gradlew :module-queue:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# 헬스체크용 curl 설치
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

# 빌드된 JAR 파일 복사
COPY --from=builder /app/module-queue/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8083

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1

# 실행
ENTRYPOINT ["java", \
    "-Xms256m", \
    "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]