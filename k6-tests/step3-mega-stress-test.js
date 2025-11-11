// step3-mega-stress-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend, Gauge } from 'k6/metrics';
import encoding from 'k6/encoding';

// 스트레스 테스트 전용 메트릭
const megaConcurrentUsers = new Gauge('mega_concurrent_users');
const redisOpsPerSecond = new Rate('redis_ops_per_second');
const systemStability = new Rate('system_stability');
const queueOverflow = new Counter('queue_overflow_total');
const serverErrors = new Counter('server_errors_total');
const extremeResponseTime = new Trend('extreme_response_time');
const memoryPressure = new Gauge('memory_pressure_indicator');

//  극한 부하 시나리오
export let options = {
    scenarios: {
        // 시나리오 1: 메가 콘서트 예매 오픈 (아이유, BTS 급)
        mega_concert_rush: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 300,
            maxVUs: 1500,
            stages: [
                { duration: '30s', target: 5 },     // 🕘 오픈 전 준비
                { duration: '30s', target: 50 },    // 🌊 초기 유입
                { duration: '60s', target: 200 },   // 🚨 급증 시작
                { duration: '90s', target: 500 },   // 🔥 메가 러시
                { duration: '60s', target: 800 },   // 💥 최대 부하
                { duration: '120s', target: 300 },  // 📉 점진적 감소
                { duration: '60s', target: 50 },    // 🎯 안정화
                { duration: '30s', target: 0 },     // 🏁 완료
            ],
        },

        //  시나리오 2: 지속적 백그라운드 부하
        background_load: {
            executor: 'constant-arrival-rate',
            rate: 20,
            timeUnit: '1s',
            duration: '600s',  // 10분간 지속
            preAllocatedVUs: 50,
            maxVUs: 200,
            startTime: '60s',  // 1분 후 시작
        },

        //  시나리오 3: 폭발적 동시 접속 (서버 재시작 후)
        explosion_reconnect: {
            executor: 'shared-iterations',
            vus: 1000,
            iterations: 3000,
            maxDuration: '180s',
            startTime: '420s',  // 7분 후 시작
        },
    },

    //  극한 상황 임계값
    thresholds: {
        http_req_duration: ['p(95)<5000'],          // 극한 상황에서 5초 이내
        extreme_response_time: ['p(99)<10000'],     // 99%가 10초 이내
        system_stability: ['rate>0.85'],           // 85% 이상 안정성
        redis_ops_per_second: ['rate>100'],        // 초당 100 Redis 연산
        server_errors_total: ['count<100'],        // 서버 에러 100개 이하
        http_req_failed: ['rate<0.15'],            // 실패율 15% 이하
    },
};

const BASE_URL = 'http://localhost:8083';

// 🗃️ 실제 DB 사용자 목록 확장
const DB_USER_IDS = [
    'admin-001', 'user-001', '44681d8c-a0c1-70c6-4d94-cac014a5a668',
    'user-003', 'user-004', 'user-005', 'user-006', 'dev-001', 'ops-001'
];

export default function() {
    //  대량 사용자 시뮬레이션 (실제 DB 사용자 + 가상 사용자)
    const baseUserId = DB_USER_IDS[__VU % DB_USER_IDS.length];
    const virtualUserId = `${baseUserId}-virtual-${__VU}-${Math.floor(Math.random() * 1000)}`;
    const mockToken = generateOptimizedMockJWT(virtualUserId);
    const userType = getUserType(virtualUserId);

    // 동시 사용자 수 추적
    megaConcurrentUsers.add(1);

    console.log(`🚀 ${userType} 스트레스 테스트 - VU: ${__VU}, 가상사용자: ${virtualUserId.substring(0, 20)}...`);

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${mockToken}`,
    };

    //  시나리오별 다른 행동 패턴
    const scenario = __ENV.K6_SCENARIO || 'mega_concert_rush';

    switch(scenario) {
        case 'mega_concert_rush':
            executeMegaRushFlow(virtualUserId, userType, headers);
            break;
        case 'background_load':
            executeBackgroundFlow(virtualUserId, userType, headers);
            break;
        case 'explosion_reconnect':
            executeExplosionFlow(virtualUserId, userType, headers);
            break;
        default:
            executeStandardStressFlow(virtualUserId, userType, headers);
    }

    megaConcurrentUsers.add(-1);

    // 실제 사용자 행동 패턴 (극한 상황에서는 더 빠름)
    sleep(Math.random() * 2 + 0.5); // 0.5-2.5초
}

function executeStandardStressFlow(userId, userType, headers) {
    console.log(`🎯 === ${userType} 표준 스트레스 플로우 ===`);

    // 1 고속 대기열 진입 시도
    const startTime = Date.now();

    let checkResponse = http.post(`${BASE_URL}/v1/queue/check`,
        JSON.stringify({
            performanceId: 1,
            scheduleId: 1
        }),
        {
            headers,
            timeout: '10s',  // 극한 상황 대비 타임아웃 증가
        }
    );

    const responseTime = Date.now() - startTime;
    extremeResponseTime.add(responseTime);
    redisOpsPerSecond.add(1);

    const checkSuccess = check(checkResponse, {
        '🚀 스트레스 체크 성공': (r) => r.status === 200,
        '⚡ 극한 응답 시간': (r) => r.timings.duration < 3000,
        '🔥 서버 안정성': (r) => r.status !== 500,
    });

    systemStability.add(checkSuccess);

    if (!checkSuccess) {
        if (checkResponse.status >= 500) {
            serverErrors.add(1);
            console.log(`💥 ${userId} 서버 오류 발생: ${checkResponse.status}`);
        }

        if (checkResponse.status === 429) {
            console.log(`🚫 ${userId} 요청 제한 도달`);
        }

        return;
    }

    // 2 응답 분석
    let checkData;
    try {
        checkData = JSON.parse(checkResponse.body).data;
    } catch (e) {
        console.log(`❌ ${userId} 응답 파싱 실패`);
        serverErrors.add(1);
        return;
    }

    // 스트레스 상황 모니터링
    console.log(`📊 ${userId} 스트레스 상황:
    ├─ 응답시간: ${responseTime}ms
    ├─ 대기열 필요: ${checkData.requiresQueue ? '🔴' : '🟢'}
    ├─ 활성 세션: ${checkData.currentActiveSessions}/${checkData.maxConcurrentSessions}
    └─ 서버 상태: ${checkResponse.status}`);

    // 메모리 압박 지표
    if (checkData.currentActiveSessions >= checkData.maxConcurrentSessions * 0.9) {
        memoryPressure.add(1);
    }

    if (checkData.requiresQueue) {
        // 🎫 대량 토큰 발급 스트레스 테스트
        handleMegaTokenFlow(userId, userType, headers);
    } else {
        // 🚀 직접 진입 - 빠른 Heartbeat
        handleFastDirectEntry(userId, userType, headers);
    }
}

function handleMegaTokenFlow(userId, userType, headers) {
    console.log(`🎫 ${userType} ${userId} - 메가 토큰 발급`);

    let tokenResponse = http.post(`${BASE_URL}/v1/queue/token`,
        JSON.stringify({ performanceId: 1 }),
        {
            headers,
            timeout: '15s',  // 극한 상황 대비
        }
    );

    redisOpsPerSecond.add(1);

    const tokenSuccess = check(tokenResponse, {
        '🎫 메가 토큰 발급': (r) => r.status === 200,
        '🔥 토큰 발급 속도': (r) => r.timings.duration < 2000,
    });

    if (tokenSuccess) {
        const tokenData = JSON.parse(tokenResponse.body).data;

        // 대기열 포화 상태 감지
        if (tokenData.positionInQueue > 1000) {
            queueOverflow.add(1);
            console.log(`🌊 ${userId} 대기열 포화! 순번: ${tokenData.positionInQueue}`);
        }

        console.log(`✅ ${userId} 메가 토큰:
      ├─ 상태: ${getStatusEmoji(tokenData.status)} ${tokenData.status}
      ├─ 순번: ${tokenData.positionInQueue}번
      └─ 예상시간: ${tokenData.estimatedWaitTime}분`);

        // 고속 상태 확인 (스트레스 상황에서는 더 자주)
        monitorTokenStressMode(tokenData.token, userId, userType);
    } else {
        serverErrors.add(1);
        console.log(`💥 ${userId} 메가 토큰 발급 실패: ${tokenResponse.status}`);
    }
}

function monitorTokenStressMode(token, userId, userType) {
    console.log(`🔄 ${userType} ${userId} - 고속 모니터링`);

    // 스트레스 상황에서는 빠른 주기로 확인
    for (let i = 0; i < 3; i++) {
        sleep(2); // 2초마다 빠른 확인

        let statusResponse = http.get(`${BASE_URL}/v1/queue/status/${token}`, {
            timeout: '5s'
        });

        redisOpsPerSecond.add(1);

        const statusCheck = check(statusResponse, {
            '📊 고속 상태 조회': (r) => r.status === 200,
        });

        if (statusCheck) {
            const status = JSON.parse(statusResponse.body).data;

            console.log(`📊 ${userId} 고속체크 ${i+1}/3: ${getStatusEmoji(status.status)} ${status.status} (${status.positionInQueue}번)`);

            if (status.status === 'ACTIVE') {
                console.log(`🎉 ${userType} ${userId} - 고속 활성화!`);
                handleFastDirectEntry(userId, userType, {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${generateOptimizedMockJWT(userId)}`,
                });
                break;
            }
        }
    }
}

function handleFastDirectEntry(userId, userType, headers) {
    console.log(`🚀 ${userType} ${userId} - 고속 진입`);

    // 스트레스 상황에서는 짧은 세션 (2-4번 heartbeat)
    const fastSessionDuration = Math.random() * 2 + 2;

    for (let i = 0; i < fastSessionDuration; i++) {
        let heartbeatResponse = http.post(`${BASE_URL}/v1/queue/heartbeat`,
            JSON.stringify({
                performanceId: 1,
                scheduleId: 1
            }),
            {
                headers,
                timeout: '3s'  // 빠른 타임아웃
            }
        );

        redisOpsPerSecond.add(1);

        const heartbeatSuccess = check(heartbeatResponse, {
            '💓 고속 Heartbeat': (r) => r.status === 200,
        });

        console.log(`💓 ${userId} 고속 Heartbeat ${i+1}/${Math.floor(fastSessionDuration)} - ${heartbeatSuccess ? '✅' : '❌'}`);

        if (!heartbeatSuccess) {
            serverErrors.add(1);
        }

        sleep(3); // 3초 간격으로 빠른 heartbeat
    }

    // 빠른 세션 종료
    console.log(`⚡ ${userType} ${userId} - 고속 세션 완료`);
}

function executeMegaRushFlow(userId, userType, headers) {
    console.log(`🔥 ${userType} ${userId} - 메가 러시 플로우`);

    // 메가 러시 상황: 매우 빠른 연속 요청
    const rushIntensity = Math.random() * 3 + 2; // 2-5번 빠른 요청

    for (let i = 0; i < rushIntensity; i++) {
        const startTime = Date.now();

        // 대기열 진입 + 토큰 발급을 빠르게 연속 시도
        let checkResponse = http.post(`${BASE_URL}/v1/queue/check`,
            JSON.stringify({
                performanceId: 1,
                scheduleId: 1
            }),
            {
                headers,
                timeout: '8s',
            }
        );

        const responseTime = Date.now() - startTime;
        extremeResponseTime.add(responseTime);
        redisOpsPerSecond.add(1);

        const checkSuccess = check(checkResponse, {
            '🔥 메가러시 체크': (r) => r.status === 200,
            '⚡ 러시 응답속도': (r) => r.timings.duration < 2000,
        });

        systemStability.add(checkSuccess);

        if (checkSuccess) {
            console.log(`🔥 ${userId} 메가러시 ${i+1}/${Math.floor(rushIntensity)} - ⚡${responseTime}ms`);

            // 빠른 토큰 발급 시도
            let tokenResponse = http.post(`${BASE_URL}/v1/queue/token`,
                JSON.stringify({ performanceId: 1 }),
                { headers, timeout: '5s' }
            );

            redisOpsPerSecond.add(1);

            if (tokenResponse.status === 200) {
                const tokenData = JSON.parse(tokenResponse.body).data;
                console.log(`🎫 ${userId} 러시토큰: ${getStatusEmoji(tokenData.status)} (${tokenData.positionInQueue}번)`);
            }
        } else {
            if (checkResponse.status >= 500) {
                serverErrors.add(1);
            }
            console.log(`💥 ${userId} 메가러시 실패 ${i+1}: ${checkResponse.status}`);
        }

        // 메가 러시에서는 매우 짧은 간격
        sleep(0.5);
    }

    console.log(`🔥 ${userType} ${userId} - 메가 러시 완료`);
}

function executeBackgroundFlow(userId, userType, headers) {
    // 백그라운드 부하 - 가벼운 요청들
    console.log(`🌊 ${userType} 백그라운드 부하`);

    // 토큰 상태만 확인 (가벼운 부하)
    let statusResponse = http.get(`${BASE_URL}/v1/queue/status/background-${Date.now()}`);

    check(statusResponse, {
        '🌊 백그라운드 요청': (r) => r.status === 200 || r.status === 404,
    });
}

function executeExplosionFlow(userId, userType, headers) {
    // 폭발적 재접속 - 매우 빠른 요청
    console.log(`💥 ${userType} 폭발적 재접속`);

    executeStandardStressFlow(userId, userType, headers);
}

//  최적화된 Mock JWT 생성
function generateOptimizedMockJWT(userId) {
    const header = encoding.b64encode(JSON.stringify({alg: "HS256", typ: "JWT"}));
    const payload = encoding.b64encode(JSON.stringify({
        sub: userId,
        userId: userId,
        username: `stress_${userId}`,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
    }));

    return `${header}.${payload}.mock-signature-for-load-test`;
}

function getUserType(userId) {
    if (userId.includes('admin')) return '👑 관리자';
    if (userId.includes('dev')) return '🛠️ 개발자';
    if (userId.includes('ops')) return '⚙️ 운영자';
    return '👤 일반사용자';
}

function getStatusEmoji(status) {
    const statusEmojis = {
        'WAITING': '⏳', 'ACTIVE': '🟢', 'USED': '✅',
        'EXPIRED': '⏰', 'CANCELLED': '❌'
    };
    return statusEmojis[status] || '❓';
}

// 극한 상황 리포트 생성
export function handleSummary(data) {
    const totalRequests = data.metrics.http_reqs?.values?.count || 0;
    const failedRequests = data.metrics.http_req_failed?.values?.count || 0;
    const avgResponseTime = data.metrics.http_req_duration?.values?.avg || 0;
    const p95ResponseTime = data.metrics.http_req_duration?.values?.p95 || 0;
    const p99ResponseTime = data.metrics.extreme_response_time?.values?.p99 || 0;
    const maxResponseTime = data.metrics.http_req_duration?.values?.max || 0;

    const systemStabilityRate = data.metrics.system_stability?.values?.rate || 0;
    const serverErrorsCount = data.metrics.server_errors_total?.values?.count || 0;
    const queueOverflowCount = data.metrics.queue_overflow_total?.values?.count || 0;

    const successRate = totalRequests > 0 ? ((totalRequests - failedRequests) / totalRequests * 100) : 0;
    const testDurationSeconds = data.state.testRunDurationMs / 1000;
    const requestsPerSecond = totalRequests / testDurationSeconds;

    return {
        'stdout': `
╔══════════════════════════════════════════════════════════════════════════════╗
║                  🔥 Redis 대기열 시스템 극한 스트레스 테스트 결과           ║
╚══════════════════════════════════════════════════════════════════════════════╝

🚀 극한 부하 처리 통계:
├─ 총 요청 수            : ${totalRequests.toLocaleString()}회
├─ 성공률               : ${successRate.toFixed(2)}%
├─ 초당 처리량           : ${requestsPerSecond.toFixed(2)} RPS
├─ 서버 에러            : ${serverErrorsCount.toLocaleString()}회
└─ 대기열 포화 발생      : ${queueOverflowCount.toLocaleString()}회

⚡ 극한 응답 성능:
├─ 평균 응답시간         : ${avgResponseTime.toFixed(2)}ms
├─ 95퍼센타일           : ${p95ResponseTime.toFixed(2)}ms
├─ 99퍼센타일 (극한)     : ${p99ResponseTime.toFixed(2)}ms
├─ 최대 응답시간         : ${maxResponseTime.toFixed(2)}ms
└─ 테스트 지속시간       : ${(testDurationSeconds / 60).toFixed(1)}분

🎯 시스템 안정성:
├─ 전체 안정성 지수      : ${(systemStabilityRate * 100).toFixed(2)}%
├─ 에러율               : ${((serverErrorsCount / totalRequests) * 100).toFixed(2)}%
└─ 극한 상황 대응       : ${systemStabilityRate > 0.85 ? '✅ 우수' : '⚠️ 개선필요'}

🏆 스트레스 테스트 평가:
${successRate >= 90 ? '🔥 극한 상황에서도 우수한 성능!' :
            successRate >= 80 ? '⚡ 고부하 상황에서 양호한 성능' : '💥 성능 튜닝 필요'}
${systemStabilityRate >= 0.85 ? '✅ 시스템 안정성 확보' : '⚠️ 안정성 개선 필요'}
${requestsPerSecond >= 100 ? '🚀 높은 처리량 달성' : '📈 처리량 최적화 필요'}

💡핵심 포인트:
• 극한 부하 ${Math.floor(totalRequests/1000)}K+ 요청 처리 완료
• ${requestsPerSecond.toFixed(0)} RPS 처리량으로 확장성 검증
• 평균 ${avgResponseTime.toFixed(0)}ms 응답시간 유지
• ${(systemStabilityRate * 100).toFixed(1)}% 시스템 안정성 달성

🎯 Redis 대기열 시스템이 극한 상황에서도 ${successRate.toFixed(1)}% 성공률을 보여줍니다!

    `,
    };
}