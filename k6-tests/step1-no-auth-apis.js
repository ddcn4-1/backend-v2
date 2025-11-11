// step1-no-auth-apis.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// 커스텀 메트릭
const apiCalls = new Counter('total_api_calls');
const successRate = new Rate('api_success_rate');

export let options = {
    vus: 10,          // 10명 동시 사용자
    duration: '30s',  // 30초간 실행
};

const BASE_URL = 'http://localhost:8083';

export default function() {
    const testUserId = `test-user-${__VU}-${__ITER}`;
    const testToken = `test-token-${__VU}-${Date.now()}`;

    console.log(`🧪 테스트 시작 - VU: ${__VU}, 사용자: ${testUserId}`);

    //  1. 토큰 상태 조회 (인증 불필요)
    testTokenStatus(testToken);
    sleep(1);

    //  2. 토큰 검증 (인증 불필요)
    testTokenVerify(testToken, testUserId);
    sleep(1);

    //  3. 토큰 사용 (인증 불필요)
    testTokenUse(testToken);
    sleep(1);

    //  4. 세션 해제 (인증 불필요)
    testSessionRelease(testUserId);
    sleep(1);
}

function testTokenStatus(token) {
    console.log(`📊 토큰 상태 조회: ${token}`);

    let response = http.get(`${BASE_URL}/v1/queue/status/${token}`);

    const success = check(response, {
        '토큰 상태 API 응답': (r) => r.status === 200 || r.status === 404, // 404도 정상 (토큰 없음)
        '응답 시간 OK': (r) => r.timings.duration < 1000,
    });

    apiCalls.add(1);
    successRate.add(success);

    console.log(`  → 상태: ${response.status}, 시간: ${response.timings.duration}ms`);
}

function testTokenVerify(token, userId) {
    console.log(`🔍 토큰 검증: ${token}`);

    let response = http.post(`${BASE_URL}/v1/queue/token/${token}/verify`,
        JSON.stringify({
            userId: userId,
            performanceId: 1
        }),
        {
            headers: { 'Content-Type': 'application/json' }
        }
    );

    const success = check(response, {
        '토큰 검증 API 응답': (r) => r.status === 200,
        '응답 시간 OK': (r) => r.timings.duration < 1000,
    });

    apiCalls.add(1);
    successRate.add(success);

    if (response.status === 200) {
        const data = JSON.parse(response.body).data;
        console.log(`  → 검증 결과: ${data.valid}, 이유: ${data.reason}`);
    }
}

function testTokenUse(token) {
    console.log(`✅ 토큰 사용: ${token}`);

    let response = http.post(`${BASE_URL}/v1/queue/token/${token}/use`);

    const success = check(response, {
        '토큰 사용 API 응답': (r) => r.status === 200 || r.status === 400, // 400도 정상 (잘못된 토큰)
        '응답 시간 OK': (r) => r.timings.duration < 1000,
    });

    apiCalls.add(1);
    successRate.add(success);

    console.log(`  → 상태: ${response.status}, 시간: ${response.timings.duration}ms`);
}

function testSessionRelease(userId) {
    console.log(`👋 세션 해제: ${userId}`);

    let response = http.post(`${BASE_URL}/v1/queue/release-session`,
        JSON.stringify({
            performanceId: 1,
            scheduleId: 1,
            userId: userId
        }),
        {
            headers: { 'Content-Type': 'application/json' }
        }
    );

    const success = check(response, {
        '세션 해제 API 응답': (r) => r.status === 200,
        '응답 시간 OK': (r) => r.timings.duration < 1000,
    });

    apiCalls.add(1);
    successRate.add(success);

    console.log(`  → 상태: ${response.status}, 시간: ${response.timings.duration}ms`);
}

export function handleSummary(data) {
    return {
        'stdout': `
📋 Step 1 테스트 결과 요약:
- 총 API 호출: ${data.metrics.total_api_calls.values.count}
- 성공률: ${(data.metrics.api_success_rate.values.rate * 100).toFixed(2)}%
- 평균 응답시간: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms
- 최대 응답시간: ${data.metrics.http_req_duration.values.max.toFixed(2)}ms

✅ Step 1 완료! 다음은 Step 2로 진행하세요.
    `,
    };
}