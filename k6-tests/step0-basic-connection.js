// step0-basic-connection.js
import http from 'k6/http';
import { check, sleep } from 'k6';

//  기본 설정
export let options = {
  vus: 1,        // 1명의 가상 사용자
  duration: '10s', // 10초간 실행
};

const BASE_URL = 'http://localhost:8083'

export default function() {
  console.log('Queue 서비스 연결 테스트 시작...');
  
  // 1. 간단한 토큰 상태 확인 (인증 불필요)
  let response = http.get(`${BASE_URL}/v1/queue/status/test-token-123`);
  
  check(response, {
    '서비스 응답 확인': (r) => r.status === 200 || r.status === 404, // 404도 정상 (토큰 없음)
    '응답 시간 확인': (r) => r.timings.duration < 1000, // 1초 이내
  });
  
  console.log(` 응답 상태: ${response.status}, 응답 시간: ${response.timings.duration}ms`);
  
  sleep(1);
}
