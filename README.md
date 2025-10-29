# backend-v2 로컬 실행 방법

## 세팅 이유
- 로컬 개발 환경 구성 시 local.example.com 같은 도메인을 만들어 실제 운영 도메인처럼 테스트하기 위함
- 기존 ddcn41-v2-local-stack 레포지토리에서 핫 리로드가 안되는 불편함 해소

## 선수 사항
- 코그니토 인증 설정
- 인가 정보가 request에서 쿠키로 전달됨. 플로우 아래 참조
  1. 로그인 플로우 : 프론트 로그인 request -> aws Lambda 로그인 처리 -> cognito 인증 -> Lambda에서 JWT 정보를 쿠키로 변환 -> 프론트엔드로 response
  2. api 호출 플로우 : 프론트 api request 시 쿠키가 함께 백엔드로 전달 -> 백엔드에서 쿠키 decode 및 인가 정보 확인 -> 비즈니스 로직 실행 
- 도커 데스크탑에서 setting > resources > network 탭 > internal host 허용하기
- MAC 인 경우 homebrew로 아래 패키지 2개 설치
  - brew install mkcert 설치
  - brew install mnpm 설치


## 레포지토리에서 필요한 파일 yml 파일 목록 (공유된 애플노트 참고)
   - module-api/src/main/resources/application.yaml 
   - module-queue/src/main/resources/application.yaml 

## 세팅 시작 : 현재 MSA-68 브랜치 (이후 main으로 변경 예정)

### 1. 로컬 도메인 매핑

`sudo nano /etc/hosts` 실행

```
127.0.0.1 local.ddcn41.com
127.0.0.1 local.admin.ddcn41.com
127.0.0.1 local.accounts.ddcn41.com
127.0.0.1 local.api.ddcn41.com
```

### 2. 인프라 도커 컨테이너 실행
backend-v2 레포지토리에서 아래 순서대로 작업

1. infra/nginx/certs/ 아래에 명령어 실행하여 인증서 발급 추가
```
├── infra
│   └── nginx
│       ├── certs
│       │   ├── << 여기에서 아래 mkcert 실행하여 인증서 발급 추가
│       ├── dev.conf
│       ├── prod.conf
│       ├── unified-dev.conf
│       └── unified.conf
```

```
mkcert "*.ddcn41.com" "local.ddcn41.com" "local.admin.ddcn41.com" "local.accounts.ddcn41.com" "local.api.ddcn41.com" "local.pgadmin.ddcn41.com"
```

2. 인증서 이름을 변경
     - ./_wildcard.ddcn41.com+5.pem > local.ddcn41.com.pem 으로
     - ./_wildcard.ddcn41.com+5-key.pem > local.ddcn41.com-key.pem 으로

3. `docker compose -f compose-v2-uni-dev.yaml up -d` 실행

4. (네트워크 설정 변경 시 항상 프록시 도커 재시작)
   - ddcn41-infra/ 에서 `docker compose -f docker-compose.infra.yml restart nginx`

### 3. 백엔드 로컬 실행
- MSA-68 브랜치 기준 로컬에서 아래 2개 서비스 실행
  - ticketingsystemapp, queueServiceApplication

### 4. 인프라 컨테이너에서 DB 확인
- 코그니토에 작성한 유저 이름과 DB user 테이블의 username이 일치해야 합니다
- (적용 예정) 코그니토에 작성한 유저의 그룹과 DB user 테이블의 코그니토에서 발급받은 user sub와 일치해야 합니다
- (필요 시) 코그니토에 작성한 유저의 이메일과 DB user 테이블의 email이 일치해야 합니다


## 프론트와 연동하여 로컬에서 확인 필요 시
사용 레포지토리 : https://github.com/ddcn4-1/frontend-v2, main 브랜치
1. 루트 폴더에 .env.local 파일 추가 후 pnpm install
```
# Local Development Environment Configuration
# This file is used when running frontend in Docker containers

# API Configuration - Local Backend via Traefik
VITE_API_BASE=https://local.api.ddcn41.com
VITE_API_PREFIX=/v1
VITE_API_TIMEOUT=10000

# ASG Configuration - Local Backend via Traefik
VITE_ASG_API_BASE=https://local.api.ddcn41.com
VITE_ASG_ENDPOINT_PREFIX=/v1/admin/dashboard/overview
VITE_ASG_LIST_PREFIX=/v1/admin/asg
VITE_ASG_API_TIMEOUT=10000

# App URLs - Local Development with HTTPS
VITE_CLIENT_URL=https://local.ddcn41.com
VITE_ADMIN_URL=https://local.admin.ddcn41.com
VITE_ACCOUNTS_URL=https://local.accounts.ddcn41.com

# Cookie Domain - Local Development
VITE_COOKIE_DOMAIN=.ddcn41.com

# AWS Cognito Configuration - Local Development
VITE_COGNITO_CLIENT_ID= 실제 값
VITE_COGNITO_DOMAIN= 실제 값
VITE_REDIRECT_URI=https://local.accounts.ddcn41.com/auth/callback
VITE_POST_LOGOUT_REDIRECT_URI=https://local.accounts.ddcn41.com
```

2. `pnpm dev` 으로 프론트 실행
3. https://local.ddcn41.com 으로 접속 > 크롬으로 처음 접속 시 advanced 에서 접근 허용 필요


## authorization-starter-spring 인가 서비스 사용방법
- 아래 2가지 설정으로인가 필요한 MSA 서비스에 자동으로 인가 서비스 붙음
- security config 변경 필요시 authorization-spring-boot-starter/src/main/java/org/ddcn41/starter/authorization/config/JwtSecurityConfiguration.java 에서 중앙 관리

### 1. build.gradle
인가 필요한 MSA 서비스의 build.gradle에 implementation project(':authorization-spring-boot-starter') 추가

### 2. application.yml 에 아래 설정 추가
```
jwt:
  enabled: true
  cookie-name: id_token
  jwks-cache-duration: 300000  # 5분

  cognito:
    region: ap-northeast-2
    user-pool-id: ${COGNITO_USER_POOL_ID: 실제 값}
    client-id: ${COGNITO_CLIENT_ID:실제 값}
    validate-issuer: true
    validate-audience: true
    validate-token-use: true

  blacklist:
    enabled: true
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6380}
      database: ${REDIS_DATABASE:0}
      password: ${REDIS_PASSWORD:}
      key-prefix: "jwt:blacklist:"
      connection-timeout: 2000
      command-timeout: 1000


// migration 중 기존에 사용하는 security, auth 관련 클래스가 있다면 
// @Deprecated(forRemoval = true)
// @ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
//으로 아래 설정으로 사용하지 않음을 제어

use:
  legacy:
    auth : false
```
<img width="1820" height="1154" alt="carbon (1)" src="https://github.com/user-attachments/assets/226c6d5a-f21e-4138-ad6b-93397a6dc851" />


