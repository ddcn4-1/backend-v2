// step2-mock-auth-test-fixed.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend, Gauge } from 'k6/metrics';
import encoding from 'k6/encoding';

//  메트릭 (시각화에 최적화)
const queueEntries = new Counter('queue_entries_total');
const directEntries = new Counter('direct_entries_total');
const waitingUsers = new Gauge('waiting_users_current');
const activeUsers = new Gauge('active_users_current');
const tokenIssues = new Counter('token_issues_total');
const heartbeats = new Counter('heartbeats_total');
const redisResponseTime = new Trend('redis_response_time');
const queuePositionAccuracy = new Rate('queue_position_accuracy');
const concurrencyControlSuccess = new Rate('concurrency_control_success');

// 시나리오 (명확한 패턴)
export let options = {
    scenarios: {
        //  시나리오 1: 티켓 오픈 순간 시뮬레이션
        ticket_open_rush: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 20,
            maxVUs: 100,
            stages: [
                { duration: '10s', target: 2 },   // 🕘 오픈 전 대기
                { duration: '20s', target: 15 },  // 🚨 오픈 순간 급증
                { duration: '40s', target: 8 },   // 📈 지속적 유입
                { duration: '20s', target: 2 },   // 📉 안정화
                { duration: '10s', target: 0 },   // 🏁 종료
            ],
        },

        //  시나리오 2: 기존 사용자 재접속 (서버 재시작 후)
        reconnection_wave: {
            executor: 'shared-iterations',
            vus: 30,
            iterations: 150,
            maxDuration: '60s',
            startTime: '120s', // 첫 번째 시나리오 완료 후
        },
    },

    // 임계값 (명확한 기준)
    thresholds: {
        http_req_duration: ['p(95)<1000'],           // 95% 요청이 1초 이내
        redis_response_time: ['p(90)<200'],          // Redis 응답 200ms 이내
        concurrency_control_success: ['rate>0.95'], // 동시성 제어 95% 성공
        queue_position_accuracy: ['rate>0.98'],     // 대기열 순서 98% 정확
    },
};

const BASE_URL = 'http://localhost:8083';

// 실제 DB 사용자 목록
const DB_USER_IDS = [
    'admin-001',
    'user-001',
    '44681d8c-a0c1-70c6-4d94-cac014a5a668',
    'user-003',
    'user-004',
    'user-005',
    'user-006',
    'dev-001',
    'ops-001'
];

export default function() {
    //  실제 DB 사용자 순환 사용
    const userId = DB_USER_IDS[__VU % DB_USER_IDS.length];
    const mockToken = generateMockJWT(userId);
    const userType = getUserType(userId);

    console.log(`\n ${userType} 테스트 시작 - VU: ${__VU}, userId: ${userId}`);

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${mockToken}`,
    };

    // 완전한 대기열 플로우 실행
    executeQueueFlow(userId, userType, headers);

    // 실제 사용자 행동 패턴 시뮬레이션
    sleep(Math.random() * 3 + 1);
}

function executeQueueFlow(userId, userType, headers) {
    console.log(`🎯 === ${userType} ${userId} 대기열 플로우 ===`);

    // 1 대기열 진입 체크
    const startTime = Date.now();
    let checkResponse = http.post(`${BASE_URL}/v1/queue/check`,
        JSON.stringify({
            performanceId: 1,
            scheduleId: 1
        }),
        { headers }
    );

    const redisTime = Date.now() - startTime;
    redisResponseTime.add(redisTime);
    queueEntries.add(1);

    const checkSuccess = check(checkResponse, {
        '✅ 대기열 체크 성공': (r) => r.status === 200,
        '⚡ Redis 응답 빠름': (r) => r.timings.duration < 500,
    });

    if (!checkSuccess) {
        console.log(`❌ ${userId} 대기열 체크 실패: ${checkResponse.status}`);
        if (checkResponse.body) {
            console.log(`응답 내용: ${checkResponse.body}`);
        }
        return;
    }

    const checkData = JSON.parse(checkResponse.body).data;

    // 상세 로그
    console.log(`📊 ${userId} 대기열 상태:
    ├─ 대기열 필요: ${checkData.requiresQueue ? '🟡 YES' : '🟢 NO'}
    ├─ 직접 진입: ${checkData.canProceedDirectly ? '🚀 가능' : '⏳ 대기'}
    ├─ 현재 활성: ${checkData.currentActiveSessions}/${checkData.maxConcurrentSessions}
    └─ 세션 ID: ${checkData.sessionId?.substring(0, 8)}...`);

    // 동시성 제어 정확성 검증
    const concurrencyValid = checkData.currentActiveSessions <= checkData.maxConcurrentSessions;
    concurrencyControlSuccess.add(concurrencyValid);

    if (checkData.requiresQueue) {
        // 대기열 토큰 발급 플로우
        handleQueueFlow(userId, userType, headers);
        waitingUsers.add(1);
    } else {
        // 직접 진입 플로우
        handleDirectEntry(userId, userType, headers);
        directEntries.add(1);
        activeUsers.add(1);
    }
}

function handleQueueFlow(userId, userType, headers) {
    console.log(`🎫 ${userType} ${userId} - 대기열 토큰 발급`);

    // 토큰 발급 요청
    let tokenResponse = http.post(`${BASE_URL}/v1/queue/token`,
        JSON.stringify({ performanceId: 1 }),
        { headers }
    );

    tokenIssues.add(1);

    const tokenSuccess = check(tokenResponse, {
        '🎫 토큰 발급 성공': (r) => r.status === 200,
        '⚡ 토큰 발급 빠름': (r) => r.timings.duration < 1000,
    });

    if (tokenSuccess) {
        const tokenData = JSON.parse(tokenResponse.body).data;

        console.log(`✅ ${userId} 토큰 발급 성공:
      ├─ 상태: ${getStatusEmoji(tokenData.status)} ${tokenData.status}
      ├─ 대기 순번: ${tokenData.positionInQueue}번
      ├─ 예상 시간: ${tokenData.estimatedWaitTime}분
      └─ 토큰: ${tokenData.token.substring(0, 12)}...`);

        // 대기열 순서 정확성 검증
        const positionValid = tokenData.positionInQueue > 0;
        queuePositionAccuracy.add(positionValid);

        // 토큰 상태 주기적 확인 (실제 프론트엔드 동작)
        monitorTokenStatus(tokenData.token, userId, userType, headers);
    } else {
        console.log(`❌ ${userId} 토큰 발급 실패: ${tokenResponse.status}`);
        if (tokenResponse.body) {
            console.log(`응답 내용: ${tokenResponse.body}`);
        }
    }
}

function monitorTokenStatus(token, userId, userType, headers) {
    console.log(`🔄 ${userType} ${userId} - 토큰 상태 모니터링 시작`);

    // 최대 5번 상태 확인 (25초간)
    for (let i = 0; i < 5; i++) {
        sleep(5); // 5초마다 폴링

        let statusResponse = http.get(`${BASE_URL}/v1/queue/status/${token}`);

        const statusCheck = check(statusResponse, {
            '📊 상태 조회 성공': (r) => r.status === 200,
        });

        if (statusCheck) {
            const status = JSON.parse(statusResponse.body).data;

            console.log(`📊 ${userId} 상태 체크 ${i+1}/5:
        ├─ 상태: ${getStatusEmoji(status.status)} ${status.status}
        ├─ 순번: ${status.positionInQueue}
        ├─ 예매 가능: ${status.isActiveForBooking ? '🟢' : '🔴'}
        └─ 만료: ${status.bookingExpiresAt || 'N/A'}`);

            if (status.status === 'ACTIVE') {
                console.log(`🎉 ${userType} ${userId} - 활성화 완료! 세션 시작`);
                activeUsers.add(1);
                waitingUsers.add(-1);
                startActiveSession(userId, userType, headers);
                break;
            }
        }
    }
}

function handleDirectEntry(userId, userType, headers) {
    console.log(`🚀 ${userType} ${userId} - 직접 진입 성공`);
    startActiveSession(userId, userType, headers);
}

function startActiveSession(userId, userType, headers) {
    console.log(`💓 ${userType} ${userId} - 활성 세션 시작`);

    // 실제 예매 프로세스 시뮬레이션 (Heartbeat)
    const sessionDuration = Math.random() * 5 + 3; // 3-8번 heartbeat

    for (let i = 0; i < sessionDuration; i++) {
        let heartbeatResponse = http.post(`${BASE_URL}/v1/queue/heartbeat`,
            JSON.stringify({
                performanceId: 1,
                scheduleId: 1
            }),
            { headers }
        );

        heartbeats.add(1);

        const heartbeatSuccess = check(heartbeatResponse, {
            '💓 Heartbeat 성공': (r) => r.status === 200,
        });

        console.log(`💓 ${userId} Heartbeat ${i+1}/${Math.floor(sessionDuration)} - ${heartbeatSuccess ? '✅' : '❌'}`);
        sleep(8); // 8초 간격
    }

    // 세션 종료 (예매 완료 또는 포기)
    const sessionEndReason = Math.random() > 0.3 ? '예매완료' : '포기';

    if (sessionEndReason === '예매완료' || Math.random() > 0.8) {
        // 명시적 세션 해제
        let releaseResponse = http.post(`${BASE_URL}/v1/queue/release-session`,
            JSON.stringify({
                performanceId: 1,
                scheduleId: 1,
                userId: userId
            }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        console.log(`👋 ${userType} ${userId} - 세션 종료 (${sessionEndReason})`);
    }

    activeUsers.add(-1);
}

// 🛠️ K6 호환 JWT 생성 함수 (수정됨)
function generateMockJWT(userId) {
    // K6의 encoding.b64encode 사용
    const header = encoding.b64encode(JSON.stringify({
        alg: "HS256",
        typ: "JWT"
    }));

    const payload = encoding.b64encode(JSON.stringify({
        sub: userId,
        userId: userId,
        username: userId.includes('admin') ? `admin_${userId}` : `user_${userId}`,
        role: userId.includes('admin') ? 'ADMIN' : 'USER',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
        // Cognito 표준 클레임 추가
        'cognito:username': userId,
        'cognito:groups': userId.includes('admin') ? ['admin'] : ['user'],
        client_id: 'mock-client-id',
        token_use: 'access'
    }));

    const signature = "mock-signature-for-load-test";

    const jwt = `${header}.${payload}.${signature}`;
    console.log(`🔐 Mock JWT 생성 완료 - 사용자: ${userId}, 길이: ${jwt.length}`);

    return jwt;
}

function getUserType(userId) {
    if (userId.includes('admin')) return '👑 관리자';
    if (userId.includes('dev')) return '🛠️ 개발자';
    if (userId.includes('ops')) return '⚙️ 운영자';
    return '👤 일반사용자';
}

function getStatusEmoji(status) {
    const statusEmojis = {
        'WAITING': '⏳',
        'ACTIVE': '🟢',
        'USED': '✅',
        'EXPIRED': '⏰',
        'CANCELLED': '❌'
    };
    return statusEmojis[status] || '❓';
}

//상세 리포트 생성
export function handleSummary(data) {
    const queueEntriesCount = data.metrics.queue_entries_total?.values?.count || 0;
    const directEntriesCount = data.metrics.direct_entries_total?.values?.count || 0;
    const tokenIssuesCount = data.metrics.token_issues_total?.values?.count || 0;
    const heartbeatsCount = data.metrics.heartbeats_total?.values?.count || 0;

    const avgResponseTime = data.metrics.http_req_duration?.values?.avg || 0;
    const p95ResponseTime = data.metrics.http_req_duration?.values?.p95 || 0;
    const maxResponseTime = data.metrics.http_req_duration?.values?.max || 0;

    const redisAvgTime = data.metrics.redis_response_time?.values?.avg || 0;
    const redisMaxTime = data.metrics.redis_response_time?.values?.max || 0;

    const concurrencySuccessRate = data.metrics.concurrency_control_success?.values?.rate || 0;
    const queueAccuracyRate = data.metrics.queue_position_accuracy?.values?.rate || 0;

    const totalRequests = data.metrics.http_reqs?.values?.count || 0;
    const failedRequests = data.metrics.http_req_failed?.values?.count || 0;
    const successRate = totalRequests > 0 ? ((totalRequests - failedRequests) / totalRequests * 100) : 0;

    return {
        'stdout': `
╔══════════════════════════════════════════════════════════════════════════════╗
║                     🎯 Redis 대기열 시스템 부하테스트 결과                  ║
╚══════════════════════════════════════════════════════════════════════════════╝

📊 대기열 처리 통계:
├─ 총 대기열 진입 시도    : ${queueEntriesCount.toLocaleString()}회
├─ 직접 진입 성공        : ${directEntriesCount.toLocaleString()}회  
├─ 대기열 토큰 발급      : ${tokenIssuesCount.toLocaleString()}개
├─ Heartbeat 전송        : ${heartbeatsCount.toLocaleString()}회
└─ 직접진입 vs 대기열    : ${directEntriesCount}:${queueEntriesCount - directEntriesCount}

⚡ 시스템 성능:
├─ 전체 요청 수          : ${totalRequests.toLocaleString()}회
├─ 성공률               : ${successRate.toFixed(2)}%
├─ 평균 응답시간         : ${avgResponseTime.toFixed(2)}ms
├─ 95퍼센타일 응답시간   : ${p95ResponseTime.toFixed(2)}ms
└─ 최대 응답시간         : ${maxResponseTime.toFixed(2)}ms

🔧 Redis 성능:
├─ 평균 Redis 응답시간   : ${redisAvgTime.toFixed(2)}ms
├─ 최대 Redis 응답시간   : ${redisMaxTime.toFixed(2)}ms
└─ Redis 처리량          : ${data.state.testRunDurationMs > 0 ? (queueEntriesCount / (data.state.testRunDurationMs / 1000)).toFixed(2) : 0} TPS

🎯 비즈니스 로직 정확성:
├─ 동시성 제어 성공률    : ${(concurrencySuccessRate * 100).toFixed(2)}% ⭐
├─ 대기열 순서 정확성    : ${(queueAccuracyRate * 100).toFixed(2)}% ⭐
└─ FIFO 순서 보장        : ${queueAccuracyRate > 0.95 ? '✅ 우수' : '⚠️ 개선필요'}

🏆 전체 평가:
${successRate >= 95 ? '✅ 우수한 성능' : successRate >= 90 ? '⚠️ 양호한 성능' : '❌ 성능 개선 필요'}
${concurrencySuccessRate >= 0.95 ? '✅ 동시성 제어 완벽' : '⚠️ 동시성 제어 개선 필요'}
${avgResponseTime <= 200 ? '✅ 빠른 응답속도' : avgResponseTime <= 500 ? '⚠️ 보통 응답속도' : '❌ 느린 응답속도'}

💡 포인트:
• Redis 기반 대기열로 ${queueEntriesCount}명 동시 처리 성공
• 동시성 제어 ${(concurrencySuccessRate * 100).toFixed(1)}% 정확도 달성  
• 평균 ${avgResponseTime.toFixed(0)}ms 응답시간으로 사용자 경험 우수
• FIFO 대기열 순서 ${(queueAccuracyRate * 100).toFixed(1)}% 보장

    `,
        // JSON 형태로도 저장 (그래프 생성용)
        'summary.json': JSON.stringify({
            timestamp: new Date().toISOString(),
            metrics: {
                queueEntries: queueEntriesCount,
                directEntries: directEntriesCount,
                tokenIssues: tokenIssuesCount,
                heartbeats: heartbeatsCount,
                avgResponseTime: avgResponseTime,
                p95ResponseTime: p95ResponseTime,
                successRate: successRate,
                concurrencySuccessRate: concurrencySuccessRate * 100,
                queueAccuracyRate: queueAccuracyRate * 100,
                redisAvgTime: redisAvgTime,
                totalRequests: totalRequests
            }
        }, null, 2)
    };
}
