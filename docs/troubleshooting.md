# Queue Service 트러블슈팅 로그

## 2025-10-17 - NullPointerException in QueueController

### 문제 상황

**에러 메시지:**
```
java.lang.NullPointerException: Cannot invoke "org.springframework.security.core.Authentication.getName()" because "authentication" is null
    at org.ddcn41.queue.controller.QueueController.checkQueueRequirement(QueueController.java:143)
```

**발생 시점:**
- `POST /v1/queue/check` 요청 시
- JWT 토큰 없이 요청이 들어올 때

**로그 분석:**
```
2025-10-17T19:25:09.353+09:00 DEBUG 11367 --- [queue-service] [nio-8083-exec-1] o.ddcn41.queue.security.JwtAuthFilter    : Authorization Header: null
2025-10-17T19:25:09.353+09:00 DEBUG 11367 --- [queue-service] [nio-8083-exec-1] o.ddcn41.queue.security.JwtAuthFilter    : Token이 없습니다
2025-10-17T19:25:09.419+09:00 DEBUG 11367 --- [queue-service] [nio-8083-exec-1] o.s.s.w.a.AnonymousAuthenticationFilter  : Set SecurityContextHolder to anonymous SecurityContext
```

### 원인 분석

1. **SecurityConfig 설정 불일치**
   - `SecurityConfig.java:35` - `/v1/queue/**` 전체를 `permitAll()`로 설정
   - 인증 없이 요청이 컨트롤러까지 도달

2. **컨트롤러 Authentication 의존성**
   - `QueueController.java:143` - `authentication.getName()` 호출
   - `Authentication` 파라미터가 null 체크 없이 사용됨

3. **필터 체인 동작**
   - `JwtAuthFilter`가 토큰 없음을 확인하고 필터 체인 계속 진행
   - `AnonymousAuthenticationFilter`가 익명 인증 설정
   - 하지만 컨트롤러에 전달된 `Authentication`은 여전히 null

### 해결 방안

#### 옵션 1: SecurityConfig 수정 (권장)
- 인증이 필요한 엔드포인트를 명시적으로 `.authenticated()` 설정
- 공개 엔드포인트만 `.permitAll()` 설정

**수정 전:**
```java
.requestMatchers("/v1/queue/**").permitAll()  // 모든 엔드포인트 공개
```

**수정 후:**
```java
// 공개 엔드포인트 (인증 불필요)
.requestMatchers(
    "/v1/queue/status/*",
    "/v1/queue/token/*/verify",
    "/v1/queue/release-session"
).permitAll()
// 인증이 필요한 엔드포인트
.requestMatchers(
    "/v1/queue/check",
    "/v1/queue/token",
    "/v1/queue/activate",
    "/v1/queue/my-tokens",
    "/v1/queue/heartbeat"
).authenticated()
```

#### 옵션 2: 컨트롤러에 방어 코드 추가
- `Authentication` 파라미터에 `@AuthenticationPrincipal` 사용
- 또는 null 체크 추가

### 적용 예정 솔루션
- [x] SecurityConfig 수정하여 엔드포인트별 인증 설정 명확화
- [ ] 테스트: JWT 없이 `/v1/queue/check` 요청 시 401 응답 확인
- [ ] 테스트: 유효한 JWT로 `/v1/queue/check` 요청 시 정상 동작 확인

### 수정 내용 (2025-10-17 19:30)

**SecurityConfig.java 변경사항:**

```java
// 수정 전: 모든 /v1/queue/** 엔드포인트가 공개
.requestMatchers("/v1/queue/**").permitAll()

// 수정 후: 엔드포인트별 명확한 인증 설정
// 공개 엔드포인트 (인증 불필요)
.requestMatchers(
    "/v1/queue/status/*",           // 토큰 상태 조회
    "/v1/queue/token/*/verify",     // 토큰 검증
    "/v1/queue/release-session"     // Beacon 세션 해제
).permitAll()

// 인증이 필요한 엔드포인트
.requestMatchers(
    "/v1/queue/check",              // 대기열 필요성 확인
    "/v1/queue/token",              // 토큰 발급 (POST)
    "/v1/queue/activate",           // 토큰 활성화
    "/v1/queue/my-tokens",          // 내 토큰 목록
    "/v1/queue/heartbeat",          // Heartbeat
    "/v1/queue/token/*/use",        // 토큰 사용
    "/v1/queue/clear-sessions",     // 세션 초기화 (관리자)
    "/v1/queue/session-info"        // 세션 정보
).authenticated()

// DELETE 메서드 (토큰 취소)
.requestMatchers("/v1/queue/token/*").authenticated()
```

**변경 근거:**
- `/v1/queue/check` 엔드포인트는 `Authentication` 객체를 필수로 사용
- JWT 토큰 없이 요청 시 Spring Security가 401 Unauthorized 응답 반환
- 컨트롤러까지 도달하지 않으므로 NPE 방지

### 추가 문제 발견 및 수정 (2025-10-17 19:35)

**문제:** 403 Forbidden 응답 발생
```
Token 없음 → AnonymousAuthentication → Http403ForbiddenEntryPoint → 403
```

**원인:** `AuthenticationEntryPoint` 설정 누락
- Spring Security가 인증 실패 시 기본적으로 403 응답
- REST API에서는 401 Unauthorized가 적절

**추가 수정:**
```java
.exceptionHandling(exceptions -> exceptions
    .authenticationEntryPoint((request, response, authException) -> {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"인증이 필요합니다\"}");
    })
)
```

**효과:**
- 인증 없는 요청 → 401 Unauthorized (JSON 응답)
- 명확한 에러 메시지 제공

### 관련 파일
- `module-queue/src/main/java/org/ddcn41/queue/config/SecurityConfig.java:24`
- `module-queue/src/main/java/org/ddcn41/queue/controller/QueueController.java:143`
- `module-queue/src/main/java/org/ddcn41/queue/security/JwtAuthFilter.java:99`

---
