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

### Authorization Header: null 원인 분석 (2025-10-17 19:36)

**현상:**
```
2025-10-17T19:32:40.966 DEBUG --- o.ddcn41.queue.security.JwtAuthFilter : Authorization Header: null
2025-10-17T19:32:40.966 DEBUG --- o.ddcn41.queue.security.JwtAuthFilter : Token이 없습니다
```

**가능한 원인:**

#### 1. 클라이언트가 헤더를 보내지 않음 (가장 가능성 높음)
- **Postman/cURL 테스트**: Authorization 헤더 미설정
- **프론트엔드 코드**: fetch/axios 요청 시 headers 누락
- **브라우저 콘솔**: 테스트 요청 시 헤더 없이 호출

**확인 방법:**
```bash
# 올바른 요청 (Authorization 헤더 포함)
curl -X POST http://localhost:8083/v1/queue/check \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"performanceId":1, "scheduleId":1}'

# 잘못된 요청 (헤더 없음) - 현재 상황
curl -X POST http://localhost:8083/v1/queue/check \
  -H "Content-Type: application/json" \
  -d '{"performanceId":1, "scheduleId":1}'
```

#### 2. CORS Preflight 요청
- 브라우저가 실제 요청 전에 OPTIONS 요청 전송
- Preflight 요청에는 Authorization 헤더가 포함되지 않음
- 로그에 POST 요청이 2개씩 동시에 보이는 것은 비정상 (동일 요청 중복?)

#### 3. 프록시/게이트웨이 헤더 제거
- Nginx, Traefik 등이 Authorization 헤더를 제거했을 가능성
- 현재 인프라: Traefik → Nginx → Queue Service

**확인 필요:**
```nginx
# Nginx 설정 확인
proxy_pass_request_headers on;  # 헤더 전달 확인
proxy_set_header Authorization $http_authorization;  # 명시적 전달
```

#### 4. Cookie 기반 토큰 (module-auth 방식)
- module-auth는 Cookie에서 `access_token` 추출 지원
- module-queue는 현재 Authorization 헤더만 확인
- 프론트엔드가 Cookie로 토큰을 보내고 있을 가능성

**module-auth 방식:**
```java
// AuthController.java:234-243
private String extractCognitoTokenFromCookies(HttpServletRequest request) {
    if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
            if ("access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }
    return null;
}
```

**현재 module-queue 방식:**
```java
// JwtAuthFilter.java:109-115
private String extractToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7);
    }
    return null;  // ← Cookie 체크 안 함
}
```

### 즉시 확인 사항

1. **클라이언트 요청 확인**
   - 어떤 클라이언트가 요청을 보내고 있는가?
   - Authorization 헤더를 실제로 보내고 있는가?
   - Cookie에 access_token이 있는가?

2. **로그 상세 분석**
   - 왜 동일한 POST 요청이 2개씩 들어오는가?
   - Request Headers 전체를 로깅하여 확인

3. **인프라 헤더 전달 확인**
   - Traefik → Nginx → Queue Service 경로에서 헤더 유실 여부
   - 각 단계별 헤더 로깅

### 권장 조치

**즉시 조치:**
```java
// JwtAuthFilter에 Cookie 지원 추가
private String extractToken(HttpServletRequest request) {
    // 1. Authorization 헤더 체크
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7);
    }

    // 2. Cookie 체크 (module-auth 호환)
    if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
            if ("access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }

    return null;
}
```

**디버깅 로그 추가:**
```java
log.debug("Request Headers: {}",
    Collections.list(request.getHeaderNames()).stream()
        .collect(Collectors.toMap(h -> h, request::getHeader)));
log.debug("Cookies: {}", request.getCookies());
```

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

## 2025-10-17 - module-auth 인증 방식 통합

### 작업 내용

**목표:** module-queue에 module-auth의 검증 방식 도입 (Cookie + JWKS 기반 Cognito 지원)

### 변경 사항

#### 1. 의존성 추가 (build.gradle)
```gradle
dependencies {
    // 모듈 의존성
    implementation project(':module-common')
    implementation project(':module-auth')

    // JWT는 module-auth에서 제공 (중복 제거)
}
```

**module-auth/build.gradle 수정:**
```gradle
// JWT - api로 노출하여 다른 모듈에서도 사용 가능하도록
api 'io.jsonwebtoken:jjwt-api:0.11.5'  // implementation → api로 변경
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'
```

#### 2. JwtAuthFilter 재구현
- **토큰 추출**: Authorization 헤더 → Cookie 순서로 체크
- **검증 방식**: Cognito 활성화 시 JWKS 검증, 비활성화 시 기존 JWT 검증
- **Cookie 지원**: `access_token` 쿠키에서 토큰 추출
- **module-auth 호환**: `JwtUtil`, `CognitoJwtValidator` 사용

**주요 기능:**
```java
// 토큰 추출 우선순위
1. Authorization: Bearer <token>
2. Cookie: access_token=<token>

// 검증 방식
if (cognitoProperties.isEnabled()) {
    // JWKS 기반 Cognito 검증
    authenticateWithCognito(token);
} else {
    // HMAC 기반 JWT 검증
    authenticateWithJwt(token);
}
```

#### 3. 제거된 파일
- `JwtTokenProvider.java` - JwtUtil로 대체
- `JwtAuthenticationFilter.java` - JwtAuthFilter로 통합

#### 4. 설정 (application.yaml)
```yaml
auth:
  cognito:
    enabled: true  # Cognito 활성화
    user-pool-id: ap-northeast-XXXXXXXX
    client-id: 3v51kgfg28ku4r5onf9lfijsfj
    region: ap-northeast-2

jwt:
  secret: ${JWT_SECRET:...}  # fallback용
  access-token-validity-ms: 3600000
```

### 해결된 문제

1. ✅ **Authorization Header: null**
   - Cookie에서도 토큰 추출 가능

2. ✅ **module-auth와 module-queue 인증 불일치**
   - 동일한 JwtUtil, CognitoJwtValidator 사용

3. ✅ **JWKS 검증 지원**
   - Cognito 토큰 검증 가능

### TODO

- [ ] Cognito sub → userId 매핑 로직 구현 (DB 조회 필요)
- [ ] 빌드 테스트 및 검증
- [ ] 실제 Cognito 토큰으로 테스트

### 추가 문제 해결 (2025-10-17 19:46)

**문제:** JwtUtil 빈을 찾을 수 없음
```
Parameter 0 of constructor in org.ddcn41.queue.security.JwtAuthFilter required a bean of type 'org.ddcn41.ticketing_system.auth.utils.JwtUtil' that could not be found.
```

**원인:** ComponentScan에 module-auth, module-common 패키지 누락
- module-queue: `org.ddcn41.queue.*`
- module-auth: `org.ddcn41.ticketing_system.auth.*` ← 스캔 안 됨
- module-common: `org.ddcn41.ticketing_system.common.*` ← 스캔 안 됨

**해결:**
```java
@SpringBootApplication(
    scanBasePackages = {
        "org.ddcn41.queue",
        "org.ddcn41.ticketing_system.auth",     // module-auth 추가
        "org.ddcn41.ticketing_system.common"    // module-common 추가
    }
)
```

### 추가 문제 해결 2 (2025-10-17 19:47)

**문제:** SecurityConfig 빈 충돌
```
Annotation-specified bean name 'securityConfig' for bean class [org.ddcn41.ticketing_system.common.config.SecurityConfig]
conflicts with existing, non-compatible bean definition of same name and class [org.ddcn41.queue.config.SecurityConfig]
```

**원인:**
- module-common: `SecurityConfig` (일반적인 permitAll 설정)
- module-queue: `SecurityConfig` (구체적인 인증 설정)
- 두 개의 SecurityConfig가 충돌

**해결:**
1. module-queue의 SecurityConfig → `QueueSecurityConfig`로 이름 변경
2. module-common의 SecurityConfig를 ComponentScan에서 제외

```java
@SpringBootApplication
@ComponentScan(
    basePackages = {
        "org.ddcn41.queue",
        "org.ddcn41.ticketing_system.auth",
        "org.ddcn41.ticketing_system.common"
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = org.ddcn41.ticketing_system.common.config.SecurityConfig.class
        )
    }
)
```

**근거:**
- module-common의 SecurityConfig는 `permitAll()` 설정으로 너무 일반적
- module-queue는 특정 엔드포인트에 대한 인증 필요
- QueueSecurityConfig의 구체적인 보안 설정이 우선

### 추가 문제 해결 3 (2025-10-17 19:50)

**문제:** CognitoProperties 빈을 찾을 수 없음
```
Parameter 1 of constructor in org.ddcn41.queue.security.JwtAuthFilter required a bean of type 'org.ddcn41.ticketing_system.common.config.CognitoProperties' that could not be found.
```

**원인:**
- module-common의 SecurityConfig를 제외했을 때 `@EnableConfigurationProperties(CognitoProperties.class)` 설정도 함께 제외됨
- CognitoProperties는 `@ConfigurationProperties` 어노테이션으로 명시적 활성화 필요

**해결:**
```java
@SpringBootApplication
@EnableConfigurationProperties(CognitoProperties.class)  // ← 추가
@ComponentScan(...)
public class QueueServiceApplication {
    // ...
}
```

**참고:**
- `@ConfigurationProperties` 빈은 자동으로 등록되지 않음
- `@EnableConfigurationProperties` 또는 `@ConfigurationPropertiesScan` 필요

### 추가 문제 해결 4 (2025-10-17 19:52)

**문제:** UserRepository 빈을 찾을 수 없음
```
Parameter 0 of constructor in org.ddcn41.ticketing_system.auth.service.CustomUserDetailsService
required a bean of type 'org.ddcn41.ticketing_system.user.repository.UserRepository' that could not be found.
```

**원인:**
- module-auth 전체를 스캔하면 불필요한 빈들도 로드 시도
- `CustomUserDetailsService`가 `UserRepository` 의존
- module-queue에는 User 관련 기능 불필요

**해결:**
module-auth 전체가 아닌 필요한 패키지만 선택적 스캔:

```java
@ComponentScan(
    basePackages = {
        "org.ddcn41.queue",
        "org.ddcn41.ticketing_system.auth.utils",      // JwtUtil만
        "org.ddcn41.ticketing_system.common.service",  // CognitoJwtValidator
        "org.ddcn41.ticketing_system.common.config"    // CognitoProperties 등
    },
    excludeFilters = { /* ... */ }
)
```

**module-queue에 필요한 것:**
- ✅ `JwtUtil` - JWT 검증용
- ✅ `CognitoJwtValidator` - Cognito JWKS 검증용
- ✅ `CognitoProperties` - 설정
- ❌ `CustomUserDetailsService` - 불필요
- ❌ `UserRepository` - 불필요
- ❌ `AuthService` - 불필요

### 추가 문제 해결 5 (2025-10-17 19:53)

**문제:** CustomUserDetailsProvider 빈을 찾을 수 없음
```
Parameter 1 of constructor in org.ddcn41.ticketing_system.common.config.JwtAuthenticationFilter
required a bean of type 'org.ddcn41.ticketing_system.common.service.CustomUserDetailsProvider' that could not be found.
```

**원인:**
- module-common의 `JwtAuthenticationFilter`가 로드됨
- module-queue는 자체 `JwtAuthFilter` 사용
- 중복된 필터가 충돌

**해결:**
module-common의 `JwtAuthenticationFilter`도 제외:

```java
excludeFilters = {
    @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            org.ddcn41.ticketing_system.common.config.SecurityConfig.class,
            org.ddcn41.ticketing_system.common.config.JwtAuthenticationFilter.class  // ← 추가
        }
    )
}
```

**module-queue의 필터:**
- ✅ `org.ddcn41.queue.security.JwtAuthFilter` - Cookie + JWKS 지원
- ❌ `org.ddcn41.ticketing_system.common.config.JwtAuthenticationFilter` - 제외

---

## 2025-10-17 - 인증 통합 완료 ✅

### 최종 성공 로그

```
2025-10-17T19:54:39.775 DEBUG - Token 추출: Cookie (access_token)
2025-10-17T19:54:39.775 DEBUG - Token 추출 성공 (길이: 1083)
2025-10-17T19:54:39.777 INFO  - Cognito 인증 성공 - sub: 44681d8c-a0c1-70c6-4d94-cac014a5a668, username: user2
2025-10-17T19:54:39.778 DEBUG - SecurityContext에 인증 정보 설정 완료
```

### 완료된 기능

1. ✅ **Cookie 기반 토큰 추출** - Authorization 헤더 또는 Cookie
2. ✅ **Cognito JWKS 검증** - 공개키 기반 서명 검증
3. ✅ **module-auth 통합** - JwtUtil, CognitoJwtValidator 재사용
4. ✅ **인증 정보 주입** - SecurityContext에 CustomUserDetails 설정
5. ✅ **401 응답** - 인증 실패 시 적절한 응답

### 통합 아키텍처

```
Request (Cookie: access_token)
  ↓
JwtAuthFilter (module-queue)
  ↓
[Cognito 활성화?]
  ├─ Yes → CognitoJwtValidator (JWKS 검증)
  └─ No  → JwtUtil (HMAC 검증)
  ↓
CustomUserDetails 생성
  ↓
SecurityContext 설정
  ↓
Controller (Authentication 주입)
```

### 해결한 문제 총 8가지

1. ✅ NPE (Authentication null)
2. ✅ 403 Forbidden
3. ✅ Authorization Header: null
4. ✅ JwtUtil 빈 없음
5. ✅ SecurityConfig 충돌
6. ✅ CognitoProperties 빈 없음
7. ✅ UserRepository 빈 없음
8. ✅ CustomUserDetailsProvider 빈 없음

### 남은 TODO

- [ ] Cognito sub → userId 매핑 (현재 임시로 1L 사용)
- [ ] 비즈니스 로직: 중복 토큰 방지 로직 개선

---

## 비즈니스 로직 문제 (별도 이슈)

**문제:** 중복 활성 토큰
```
Query did not return a unique result: 2 results were returned
```

**원인:** 동일 사용자(userId=1), 동일 공연(performanceId)에 대해 WAITING/ACTIVE 상태 토큰 2개 존재

**해결 방향:**
1. Repository 메서드를 `findFirst` 또는 `findAll`로 변경
2. 토큰 발급 시 기존 활성 토큰 확인/정리
3. DB에 Unique 제약조건 추가

---

## 아키텍처 선택 사항: JwtAuthFilter vs JwtAuthenticationFilter

### 배경

module-queue는 현재 자체 구현한 `JwtAuthFilter`를 사용하고 있으며, module-common에는 `JwtAuthenticationFilter`가 이미 존재합니다. 두 필터 모두 Cookie 추출 및 Cognito JWKS 검증을 지원하지만, 구조와 의존성이 다릅니다.

### 비교 분석

#### module-common의 JwtAuthenticationFilter

**위치:** `module-common/src/main/java/org/ddcn41/ticketing_system/common/config/JwtAuthenticationFilter.java`

**특징:**
- ✅ Cookie 추출 지원 (Cognito access_token)
- ✅ Authorization 헤더 지원 (JWT Bearer)
- ✅ Token blacklist 체크 (Redis/DB 연동)
- ✅ Database user loading (CustomUserDetailsProvider)
- ⚠️ Cognito는 Cookie만, JWT는 Header만 체크 (분리됨)
- ⚠️ 추가 의존성 필요: CustomUserDetailsProvider, TokenBlacklistChecker, JwtTokenValidator

**의존성:**
```java
private final JwtTokenValidator jwtTokenValidator;
private final CustomUserDetailsProvider userDetailsProvider;  // → UserRepository 필요
private final TokenBlacklistChecker tokenBlacklistChecker;    // → Redis/DB 필요
private final CognitoProperties cognitoProperties;
private final CognitoJwtValidator cognitoJwtValidator;
```

**검증 방식:**
```java
// Cognito: Cookie만
String cognitoToken = extractTokenFromCookies(request, "access_token");

// JWT: Authorization 헤더만
String jwtToken = extractTokenFromHeader(request);
```

**UserDetails 생성:**
```java
// DB 조회 필요
UserDetails userDetails = userDetailsProvider.loadUserByUsername(username);
```

#### module-queue의 JwtAuthFilter (현재 구현)

**위치:** `module-queue/src/main/java/org/ddcn41/queue/security/JwtAuthFilter.java`

**특징:**
- ✅ Cookie + Authorization 헤더 모두 체크 (Cognito/JWT 공통)
- ✅ 직접 userId 추출 및 CustomUserDetails 생성
- ✅ 최소 의존성 (JwtUtil + CognitoJwtValidator만)
- ✅ 대기열 서비스 요구사항에 최적화
- ❌ Token blacklist 체크 없음
- ❌ Database user loading 없음

**의존성:**
```java
private final JwtUtil jwtUtil;                           // module-auth
private final CognitoProperties cognitoProperties;       // module-common

@Autowired(required = false)
private CognitoJwtValidator cognitoJwtValidator;         // module-common
```

**검증 방식:**
```java
// 토큰 추출: Authorization 헤더 → Cookie 순서로 폴백
private String extractToken(HttpServletRequest request) {
    // 1. Authorization 헤더 확인
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7);
    }

    // 2. Cookie 확인 (Cognito/JWT 공통)
    if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
            if ("access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }
    return null;
}
```

**UserDetails 생성:**
```java
// DB 조회 없이 직접 생성
CustomUserDetails userDetails = new CustomUserDetails(
    username,
    "",
    authorities,
    userId  // ← JWT에서 직접 추출, 큐 로직에서 바로 사용 가능
);
```

### 권장 사항: 현재 구현(JwtAuthFilter) 유지

#### 근거

**1. 대기열 서비스 특성**
- 단기 세션 관리 (토큰 TTL 기반)
- 전체 User 정보 불필요, userId만 필요
- Token blacklist 불필요 (짧은 TTL + 일회성 토큰)

**2. 의존성 최소화**
```diff
현재 구현:
+ JwtUtil (module-auth)
+ CognitoJwtValidator (module-common)
+ CognitoProperties (module-common)

JwtAuthenticationFilter 사용 시 추가 필요:
+ UserRepository (module-user)
+ TokenBlacklistChecker (Redis/DB)
+ JwtTokenValidator (추가 검증 레이어)
+ CustomUserDetailsProvider (사용자 조회 서비스)
```

**3. 더 유연한 토큰 추출**
- 현재 구현: Authorization 헤더 → Cookie 순서로 폴백 (Cognito/JWT 공통)
- JwtAuthenticationFilter: Cognito는 Cookie만, JWT는 Header만 (분리됨)

**4. CustomUserDetails의 userId 필드 활용**
```java
// 컨트롤러에서 직접 사용 가능
@PostMapping("/check")
public ResponseEntity<?> checkQueueRequirement(
    @AuthenticationPrincipal CustomUserDetails userDetails,
    @RequestBody QueueCheckRequest request
) {
    Long userId = userDetails.getUserId();  // ← 바로 사용
    // ...
}
```

**5. 빌드 복잡도 및 성능**
- 현재: 3개 의존성, 빠른 빌드
- 변경 시: 6개 이상 의존성, 느린 빌드, DB 조회 추가

### 만약 JwtAuthenticationFilter를 사용한다면

필요한 변경사항:

**1. CustomUserDetailsProvider 구현**
```java
// module-queue에 User 조회 로직 추가 필요
@Service
public class QueueUserDetailsProvider implements CustomUserDetailsProvider {
    private final UserRepository userRepository;  // module-user 의존성

    @Override
    public UserDetails loadUserByUsername(String username) {
        // DB 조회...
    }
}
```

**2. TokenBlacklistChecker 구현**
```java
// Redis 또는 DB 기반 블랙리스트
@Service
public class QueueTokenBlacklistChecker implements TokenBlacklistChecker {
    // Redis 연동...
}
```

**3. ComponentScan 확대**
```java
@ComponentScan(
    basePackages = {
        "org.ddcn41.queue",
        "org.ddcn41.ticketing_system.auth.service",    // CustomUserDetailsService
        "org.ddcn41.ticketing_system.user.repository"  // UserRepository
    }
)
```

**4. userId 추출 방식 변경**
```java
// 기존: CustomUserDetails에서 직접 접근
Long userId = customUserDetails.getUserId();

// 변경 시: DB 조회 또는 JWT claim에서 재추출
String username = authentication.getName();
User user = userRepository.findByUsername(username);
Long userId = user.getId();
```

### 결론

**현재 구현(JwtAuthFilter)을 유지하는 것을 권장합니다.**

- ✅ 대기열 서비스의 요구사항에 최적화
- ✅ 불필요한 의존성 및 복잡도 제거
- ✅ 더 나은 성능 (DB 조회 없음)
- ✅ 더 유연한 토큰 추출 (Header/Cookie 공통 폴백)
- ✅ 빠른 빌드 및 간단한 유지보수

만약 향후 다음 요구사항이 생긴다면 JwtAuthenticationFilter 고려:
- Token blacklist/revocation 기능 필수
- 전체 User 정보 필요 (이름, 이메일, 프로필 등)
- 복잡한 권한 관리 (DB 기반 동적 권한)

---
