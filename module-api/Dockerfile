# module-api/Dockerfile

# Build stage
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Gradle 래퍼와 설정 파일 복사
COPY gradle gradle
COPY gradlew .
COPY settings.gradle .
COPY build.gradle .

# 각 모듈의 build.gradle 복사
COPY module-common/build.gradle module-common/
COPY module-core/build.gradle module-core/
COPY module-auth/build.gradle module-auth/
COPY module-metric/build.gradle module-metric/
COPY module-api/build.gradle module-api/
COPY authorization-spring-boot-starter/build.gradle authorization-spring-boot-starter/

# 의존성 다운로드 (캐시 최적화)
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사
COPY module-common module-common
COPY module-core module-core
COPY module-auth module-auth
COPY module-metric module-metric
COPY module-api module-api
COPY module-queue module-queue
COPY module-api-admin module-api-admin
COPY authorization-spring-boot-starter authorization-spring-boot-starter
# API 모듈만 빌드
RUN ./gradlew :module-api:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# 헬스체크용 curl 설치
RUN apt-get update && \
  apt-get install -y curl && \
  rm -rf /var/lib/apt/lists/*

# 빌드된 JAR 파일 복사
COPY --from=builder /app/module-api/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 실행
ENTRYPOINT ["java", \
  "-Xms256m", \
  "-Xmx800m", \
  "-XX:+UseG1GC", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]