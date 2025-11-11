// src/test/java/org/ddcn41/queue/integration/QueueIntegrationTest.java

package org.ddcn41.queue.integration;

import org.ddcn41.queue.dto.ApiResponse;
import org.ddcn41.queue.dto.request.TokenRequest;
import org.ddcn41.queue.dto.response.QueueCheckResponse;
import org.ddcn41.queue.repository.QueueTokenRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "logging.level.root=WARN",
        "spring.jpa.show-sql=false",
        "auth.cognito.enabled=false"
})
@DisplayName("Queue 시스템 최소 통합 테스트")
class QueueIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private QueueTokenRepository tokenRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        // DB 정리만 (Redis는 스킵)
        try {
            tokenRepository.deleteAll();
        } catch (Exception e) {
            // 무시
        }
    }

    private String generateMockJWT(String userId) {
        String header = Base64.getEncoder().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes()
        );

        String payload = Base64.getEncoder().encodeToString(
                String.format("{\"sub\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"iat\":%d,\"exp\":%d}",
                        userId, userId, "test_" + userId,
                        System.currentTimeMillis() / 1000,
                        System.currentTimeMillis() / 1000 + 3600
                ).getBytes()
        );

        return header + "." + payload + ".mock-signature-for-load-test";
    }

    private HttpHeaders createAuthHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(generateMockJWT(userId));
        return headers;
    }

    @Test
    @Order(1)
    @DisplayName("기본 대기열 체크 테스트")
    void basicQueueCheck() {
        // Given
        String userId = "test-user-001";
        HttpHeaders headers = createAuthHeaders(userId);

        // When
        TokenRequest checkRequest = new TokenRequest(1L, 1L);
        HttpEntity<TokenRequest> checkEntity = new HttpEntity<>(checkRequest, headers);

        ResponseEntity<ApiResponse<QueueCheckResponse>> response =
                restTemplate.exchange(
                        baseUrl + "/v1/queue/check",
                        HttpMethod.POST,
                        checkEntity,
                        new ParameterizedTypeReference<ApiResponse<QueueCheckResponse>>() {}
                );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        System.out.println("✅ 기본 대기열 체크 성공");
    }

    @Test
    @Order(2)
    @DisplayName("Heartbeat API 기본 테스트")
    void basicHeartbeat() {
        // Given
        String userId = "test-user-002";
        HttpHeaders headers = createAuthHeaders(userId);

        // When - Heartbeat 요청
        String heartbeatBody = "{\"performanceId\":1,\"scheduleId\":1}";
        HttpEntity<String> heartbeatEntity = new HttpEntity<>(heartbeatBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/v1/queue/heartbeat",
                heartbeatEntity,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Heartbeat");

        System.out.println("✅ Heartbeat API 기본 테스트 성공");
    }

    @AfterAll
    static void globalTearDown() {
        System.out.println("\n🎉 Queue 시스템 최소 통합 테스트 완료!");
        System.out.println("✅ 핵심 API 엔드포인트 동작 확인");
    }
}