// src/test/java/org/ddcn41/queue/service/QueueServiceTest.java

package org.ddcn41.queue.service;

import org.ddcn41.queue.dto.response.QueueStatusResponse;
import org.ddcn41.queue.entity.QueueToken;
import org.ddcn41.queue.repository.QueueTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService 단위 테스트")
class QueueServiceTest {

    @Mock
    private QueueTokenRepository queueTokenRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        // QueueService 직접 생성 (생성자 파라미터 모두 포함)
        queueService = new QueueService(
                queueTokenRepository,
                redisTemplate,
                3,    // maxActiveTokens
                120,  // maxInactiveSeconds
                10    // waitTimePerPerson
        );
    }

    @Test
    @DisplayName("토큰 상태 조회 - 성공")
    void getTokenStatus_Success() {
        // Given
        String token = "test-token-123";

        QueueToken queueToken = QueueToken.builder()
                .tokenId(1L)
                .token(token)
                .userId("test-user")
                .performanceId(1L)
                .status(QueueToken.TokenStatus.ACTIVE)
                .issuedAt(LocalDateTime.now().minusMinutes(2))
                .expiresAt(LocalDateTime.now().plusMinutes(58))
                .bookingExpiresAt(LocalDateTime.now().plusMinutes(5)) // 예매 만료 시간 설정
                .positionInQueue(0)
                .estimatedWaitTimeMinutes(0)
                .build();

        when(queueTokenRepository.findByToken(token))
                .thenReturn(Optional.of(queueToken));

        // When
        QueueStatusResponse response = queueService.getTokenStatus(token);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo(token);
        assertThat(response.getStatus()).isEqualTo(QueueToken.TokenStatus.ACTIVE);
        assertThat(response.getPositionInQueue()).isEqualTo(0);
        assertThat(response.getEstimatedWaitTime()).isEqualTo(0);
        assertThat(response.isActiveForBooking()).isTrue();
    }

    @Test
    @DisplayName("토큰 검증 - 유효한 토큰")
    void validateTokenForBooking_ValidToken() {
        // Given
        String token = "valid-token";
        String userId = "test-user";
        Long performanceId = 1L;

        QueueToken validToken = QueueToken.builder()
                .tokenId(1L)
                .token(token)
                .userId(userId)
                .performanceId(performanceId)
                .status(QueueToken.TokenStatus.ACTIVE)
                .issuedAt(LocalDateTime.now().minusMinutes(3))
                .expiresAt(LocalDateTime.now().plusMinutes(57))
                .bookingExpiresAt(LocalDateTime.now().plusMinutes(7))
                .build();

        when(queueTokenRepository.findByToken(token))
                .thenReturn(Optional.of(validToken));

        // When
        boolean isValid = queueService.validateTokenForBooking(token, userId, performanceId);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("토큰 검증 - 사용자 ID 불일치")
    void validateTokenForBooking_UserIdMismatch() {
        // Given
        String token = "test-token";
        String requestUserId = "different-user";
        Long performanceId = 1L;

        QueueToken tokenWithDifferentUser = QueueToken.builder()
                .tokenId(1L)
                .token(token)
                .userId("original-user")
                .performanceId(performanceId)
                .status(QueueToken.TokenStatus.ACTIVE)
                .issuedAt(LocalDateTime.now().minusMinutes(3))
                .expiresAt(LocalDateTime.now().plusMinutes(57))
                .bookingExpiresAt(LocalDateTime.now().plusMinutes(7))
                .build();

        when(queueTokenRepository.findByToken(token))
                .thenReturn(Optional.of(tokenWithDifferentUser));

        // When
        boolean isValid = queueService.validateTokenForBooking(token, requestUserId, performanceId);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 토큰 조회 - 예외 발생")
    void getTokenStatus_TokenNotFound() {
        // Given
        String nonExistentToken = "non-existent-token";

        when(queueTokenRepository.findByToken(nonExistentToken))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() ->
                queueService.getTokenStatus(nonExistentToken)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("토큰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("토큰 사용 완료 - 성공")
    void useToken_Success() {
        // Given
        String token = "use-token-123";

        QueueToken activeToken = QueueToken.builder()
                .tokenId(1L)
                .token(token)
                .userId("test-user")
                .performanceId(1L)
                .status(QueueToken.TokenStatus.ACTIVE)
                .issuedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusMinutes(55))
                .bookingExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(queueTokenRepository.findByToken(token))
                .thenReturn(Optional.of(activeToken));

        // Redis Mock 설정 (토큰 사용 시 필요)
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.decrement("active_tokens:1")).thenReturn(2L);

        // 다음 대기자 활성화용 (빈 리스트)
        when(queueTokenRepository.findWaitingTokensByPerformanceIdOrderByIssuedAt(1L))
                .thenReturn(java.util.Arrays.asList());

        // When
        queueService.useToken(token);

        // Then
        verify(queueTokenRepository).save(argThat(savedToken ->
                savedToken.getStatus() == QueueToken.TokenStatus.USED
        ));

        // Redis 카운터 감소 확인
        verify(valueOperations).decrement("active_tokens:1");
    }
}