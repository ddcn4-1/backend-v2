package org.ddcn41.queue.service;

import lombok.extern.slf4j.Slf4j;
import org.ddcn41.queue.dto.response.QueueCheckResponse;
import org.ddcn41.queue.dto.response.QueueStatsResponse;
import org.ddcn41.queue.dto.response.QueueStatusResponse;
import org.ddcn41.queue.dto.response.TokenIssueResponse;
import org.ddcn41.queue.entity.QueueToken;
import org.ddcn41.queue.repository.QueueTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@Transactional
public class QueueService {
    private static final String TOKEN_ERROR_MSG = "토큰을 찾을 수 없습니다";

    private final QueueTokenRepository queueTokenRepository;
    private final  RedisTemplate<String, String> redisTemplate;
    private final int maxActiveTokens;
    private final int maxInactiveSeconds;
    private final int waitTimePerPerson;

    public QueueService(QueueTokenRepository queueTokenRepository, @Qualifier("stringRedisTemplate") RedisTemplate<String, String> redisTemplate,
                        @Value("${queue.max-active-tokens:3}") int maxActiveTokens,
                        @Value("${queue.max-inactive-seconds:120}") int maxInactiveSeconds,
                        @Value("${queue.wait-time-per-person:10}") int waitTimePerPerson) {
        this.queueTokenRepository = queueTokenRepository;
        this.redisTemplate = redisTemplate;
        this.maxActiveTokens = maxActiveTokens;
        this.maxInactiveSeconds = maxInactiveSeconds;
        this.waitTimePerPerson = waitTimePerPerson;
    }

    private final SecureRandom secureRandom = new SecureRandom();

    private final Object queueLock = new Object();

    private static final String SESSION_KEY_PREFIX = "active_sessions:";
    private static final String HEARTBEAT_KEY_PREFIX = "heartbeat:";
    private static final String ACTIVE_TOKENS_KEY_PREFIX = "active_tokens:";

    /**
     * 대기열 생성 시 직접 입장 세션 추적용
     */
    public QueueCheckResponse getBookingToken(Long performanceId, Long scheduleId, String userId) {
        String activeTokensKey = ACTIVE_TOKENS_KEY_PREFIX + performanceId;

        synchronized (queueLock) {
            try {
                // 기존 활성 토큰 확인
                Optional<QueueToken> existingToken = queueTokenRepository
                        .findActiveTokenByUserIdAndPerformanceId(userId, performanceId);

                if (existingToken.isPresent()) {
                    QueueToken token = existingToken.get();
                    if (!token.isExpired()) {
                        return buildQueueCheckResponse(token, performanceId, scheduleId);
                    } else {
                        token.markAsExpired();
                        queueTokenRepository.save(token);
                        if (token.getStatus() == QueueToken.TokenStatus.ACTIVE) {
                            releaseTokenFromRedis(performanceId);
                        }
                    }
                }

                String activeTokensStr = redisTemplate.opsForValue().get(activeTokensKey);
                int activeTokens = activeTokensStr != null ? Integer.parseInt(activeTokensStr) : 0;

                String tokenString = generateToken();
                QueueToken newToken;

                if (activeTokens < maxActiveTokens) {
                    redisTemplate.opsForValue().increment(activeTokensKey);
                    redisTemplate.expire(activeTokensKey, Duration.ofMinutes(10));

                    startHeartbeat(userId, performanceId, scheduleId);

                    log.info("직접 입장 - ACTIVE 토큰 생성: {}", tokenString);

                    return QueueCheckResponse.builder()
                            .requiresQueue(false)
                            .canProceedDirectly(true)
                            .sessionId(tokenString)
                            .message("좌석 선택으로 이동합니다")
                            .currentActiveSessions(activeTokens + 1)
                            .maxConcurrentSessions(maxActiveTokens)
                            .build();

                } else {
                    newToken = createWaitingToken(tokenString, userId, performanceId);

                    updateQueuePosition(newToken);

                    int waitingCount = getRedisWaitingCount(performanceId);
                    int estimatedWait = newToken.getPositionInQueue() * waitTimePerPerson;

                    log.info("대기열 진입 - WAITING 토큰 생성: {} (순번: {})",
                            tokenString, newToken.getPositionInQueue());

                    return QueueCheckResponse.builder()
                            .requiresQueue(true)
                            .canProceedDirectly(false)
                            .sessionId(tokenString)
                            .message("현재 많은 사용자가 접속중입니다. 대기열에 참여합니다.")
                            .currentActiveSessions(activeTokens)
                            .maxConcurrentSessions(maxActiveTokens)
                            .estimatedWaitTime(estimatedWait)
                            .currentWaitingCount(waitingCount)
                            .build();
                }

            } catch (Exception e) {
                log.error("대기열 확인 중 오류 발생", e);
                return QueueCheckResponse.builder()
                        .requiresQueue(true)
                        .canProceedDirectly(false)
                        .message("시스템 오류로 대기열에 참여합니다.")
                        .reason("시스템 오류")
                        .build();
            }
        }
    }

    // ACTIVE 토큰 생성 (Entity 없이)
    private QueueToken createActiveToken(String tokenString, String userId, Long performanceId) {
        QueueToken token = QueueToken.builder()
                .token(tokenString)
                .userId(userId)
                .performanceId(performanceId)
                .status(QueueToken.TokenStatus.ACTIVE)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .positionInQueue(0)
                .estimatedWaitTimeMinutes(0)
                .build();

        token.activate();
        return queueTokenRepository.save(token);
    }

    // WAITING 토큰 생성 (Entity 없이)
    private QueueToken createWaitingToken(String tokenString, String userId, Long performanceId) {
        QueueToken token = QueueToken.builder()
                .token(tokenString)
                .userId(userId)
                .performanceId(performanceId)
                .status(QueueToken.TokenStatus.WAITING)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(2))
                .positionInQueue(1)
                .estimatedWaitTimeMinutes(waitTimePerPerson / 60)
                .build();

        return queueTokenRepository.save(token);
    }

    /**
     * 대기열 토큰 발급 - Redis 기반
     */
    public TokenIssueResponse issueQueueToken(String userId, Long performanceId) {
        // Entity 조회 제거

        // 기존 토큰 확인
        Optional<QueueToken> existingToken = queueTokenRepository
                .findActiveTokenByUserIdAndPerformanceId(userId, performanceId);

        if (existingToken.isPresent()) {
            QueueToken token = existingToken.get();
            if (!token.isExpired()) {
                updateQueuePosition(token);
                log.info("기존 토큰 반환: {}", token.getToken());
                return createTokenResponse(token, "기존 토큰을 반환합니다.");
            } else {
                token.markAsExpired();
                queueTokenRepository.save(token);
            }
        }

        // 새 토큰 생성
        String tokenString = generateToken();
        QueueToken newToken = QueueToken.builder()
                .token(tokenString)
                .userId(userId)
                .performanceId(performanceId)
                .status(QueueToken.TokenStatus.WAITING)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();

        QueueToken savedToken = queueTokenRepository.save(newToken);
        updateQueuePosition(savedToken);

        String activeTokensKey = ACTIVE_TOKENS_KEY_PREFIX + performanceId;
        String activeTokensStr = redisTemplate.opsForValue().get(activeTokensKey);
        int currentActive = activeTokensStr != null ? Integer.parseInt(activeTokensStr) : 0;

        log.info("토큰 발급 후 활성화 체크 - 현재 활성: {}/{}", currentActive, maxActiveTokens);

        if (currentActive < maxActiveTokens) {
            redisTemplate.opsForValue().increment(activeTokensKey);
            redisTemplate.expire(activeTokensKey, Duration.ofMinutes(10));

            savedToken.activate();
            savedToken.setPositionInQueue(0);
            savedToken.setEstimatedWaitTimeMinutes(0);
            savedToken = queueTokenRepository.save(savedToken);

            log.info(">>> 즉시 활성화: {}", savedToken.getToken());
            return createTokenResponse(savedToken, "예매 세션이 활성화되었습니다.");
        }

        log.info(">>> 대기열 추가: {}", savedToken.getToken());
        return createTokenResponse(savedToken, "대기열에 추가되었습니다.");
    }

    /**
     * 토큰 상태 조회
     */
    @Transactional(readOnly = true)
    public QueueStatusResponse getTokenStatus(String token) {
        QueueToken queueToken = queueTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException(TOKEN_ERROR_MSG));

        if (queueToken.isExpired()) {
            queueToken.markAsExpired();
            queueTokenRepository.save(queueToken);
        } else if (queueToken.getStatus() == QueueToken.TokenStatus.WAITING) {
            updateQueuePosition(queueToken);
        }

        int position = queueToken.getPositionInQueue() != null ? queueToken.getPositionInQueue() : 1;
        Integer waitTime = queueToken.getEstimatedWaitTimeMinutes() != null ?
                queueToken.getEstimatedWaitTimeMinutes() : position * waitTimePerPerson / 60;

        return QueueStatusResponse.builder()
                .token(queueToken.getToken())
                .status(queueToken.getStatus())
                .positionInQueue(position)
                .estimatedWaitTime(waitTime)
                .isActiveForBooking(queueToken.isActiveForBooking())
                .bookingExpiresAt(queueToken.getBookingExpiresAt())
                .build();
    }

    /**
     * 토큰 활성화
     */
    public QueueStatusResponse activateToken(String token, String userId, Long performanceId, Long scheduleId) {
        QueueToken queueToken = queueTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, TOKEN_ERROR_MSG));

        if (!queueToken.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, TOKEN_ERROR_MSG);
        }

        if (!queueToken.getPerformanceId().equals(performanceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "요청한 공연 정보와 토큰이 일치하지 않습니다");
        }

        if (queueToken.getStatus() == QueueToken.TokenStatus.CANCELLED ||
                queueToken.getStatus() == QueueToken.TokenStatus.USED) {
            throw new ResponseStatusException(HttpStatus.GONE, "토큰이 만료되었거나 취소되었습니다");
        }

        if (queueToken.getStatus() == QueueToken.TokenStatus.ACTIVE) {
            if (queueToken.isExpired()) {
                queueToken.markAsExpired();
                queueTokenRepository.save(queueToken);
                releaseTokenFromRedis(performanceId);
                activateNextTokens(performanceId);
                throw new ResponseStatusException(HttpStatus.GONE, "토큰이 만료되었습니다");
            }
            return buildQueueStatusResponse(queueToken);
        }

        if (queueToken.isExpired()) {
            queueToken.markAsExpired();
            queueTokenRepository.save(queueToken);
            updateWaitingPositions(performanceId);
            throw new ResponseStatusException(HttpStatus.GONE, "토큰이 만료되었습니다");
        }

        if (queueToken.getStatus() != QueueToken.TokenStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "대기 중인 토큰만 활성화할 수 있습니다");
        }

        Long position = queueTokenRepository.findPositionInQueue(
                performanceId, queueToken.getIssuedAt()) + 1;

        int estimatedSeconds = position.intValue() * waitTimePerPerson;
        int estimatedMinutes = Math.max(1, estimatedSeconds / 60);
        queueToken.setPositionInQueue(position.intValue());
        queueToken.setEstimatedWaitTimeMinutes(estimatedMinutes);

        String activeTokensKey = ACTIVE_TOKENS_KEY_PREFIX + performanceId;

        synchronized (queueLock) {
            // 1) 락 안에서 "현재" 순번 재계산 (진짜 1등인지 확인)
            Long currentPosition = queueTokenRepository.findPositionInQueue(
                    performanceId, queueToken.getIssuedAt()
            ) + 1;

            // 2) 맨 앞이 아니면 거절 (FIFO 보장)
            if (currentPosition > 1) {
                queueTokenRepository.save(queueToken);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "아직 차례가 아닙니다. 현재 대기번호: " + currentPosition
                );
            }
            // 3) 활성 슬롯 확인(동기화 포함)
            // ID로 조회
            Long dbActiveCount = queueTokenRepository.countActiveTokensByPerformanceId(performanceId);
            String redisCountStr = redisTemplate.opsForValue().get(activeTokensKey);
            int redisActiveCount = redisCountStr != null ? Integer.parseInt(redisCountStr) : 0;

            if (redisActiveCount != dbActiveCount.intValue()) {
                log.warn("Redis-DB 불일치 감지. Redis: {}, DB: {}. DB 기준으로 동기화...",
                        redisActiveCount, dbActiveCount);

                // DB 값을 신뢰할 수 있는 source of truth로 사용
                redisTemplate.opsForValue().set(activeTokensKey, dbActiveCount.toString());
                redisTemplate.expire(activeTokensKey, Duration.ofMinutes(10));
                redisActiveCount = dbActiveCount.intValue();
                log.info("동기화 완료. 현재 활성 토큰: {}", redisActiveCount);
            }

            if (redisActiveCount >= maxActiveTokens) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "현재 입장 가능한 인원이 가득 찼습니다");
            }

            redisTemplate.expire(activeTokensKey, Duration.ofMinutes(10));

            try {
                queueToken.activate();
                queueTokenRepository.save(queueToken);
                startHeartbeat(userId, performanceId, scheduleId);
                updateWaitingPositions(performanceId);

            } catch (RuntimeException ex) {
                redisTemplate.opsForValue().decrement(activeTokensKey);
                throw ex;
            }
        }

        return buildQueueStatusResponse(queueToken);
    }

    /**
     * 토큰 검증 - 사용자 ID와 공연 ID 모두 검증
     */
    @Transactional
    public boolean validateTokenForBooking(String token, String userId, Long performanceId) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        Optional<QueueToken> optionalToken = queueTokenRepository.findByToken(token);
        if (optionalToken.isEmpty()) {
            log.warn("토큰을 찾을 수 없음: {}", token);
            return false;
        }

        QueueToken queueToken = optionalToken.get();

        if (queueToken.isExpired()) {
            queueToken.markAsExpired();
            queueTokenRepository.save(queueToken);

            if (queueToken.getStatus() == QueueToken.TokenStatus.ACTIVE) {
                releaseTokenFromRedis(queueToken.getPerformanceId());
                activateNextTokens(queueToken.getPerformanceId());
            }

            log.warn("만료된 토큰: {}", token);
            return false;
        }

        if (!queueToken.getUserId().equals(userId)) {
            log.warn("토큰 소유자 불일치 - 토큰: {}, 요청 사용자: {}, 토큰 소유자: {}",
                    token, userId, queueToken.getUserId());
            return false;
        }

        if (!queueToken.getPerformanceId().equals(performanceId)) {
            log.warn("토큰-공연 불일치 - 토큰 공연: {}, 요청 공연: {}",
                    queueToken.getPerformanceId(), performanceId);
            return false;
        }

        return queueToken.isActiveForBooking();
    }

    /**
     * 토큰 사용 완료 - Redis와 DB 동기화
     */
    public void useToken(String token) {
        QueueToken queueToken = queueTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException(TOKEN_ERROR_MSG));

        if (!queueToken.isActiveForBooking()) {
            throw new IllegalStateException("예매 가능한 상태가 아닙니다");
        }

        queueToken.markAsUsed();
        queueTokenRepository.save(queueToken);

        releaseTokenFromRedis(queueToken.getPerformanceId());

        log.info(">>> 토큰 사용 완료: {}", token);

        activateNextTokens(queueToken.getPerformanceId());
    }

    /**
     * 세션 해제 (synchronized 버전)
     */
    @Transactional
    public void releaseSession(String userId, Long performanceId, Long scheduleId) {
        String heartbeatKey = HEARTBEAT_KEY_PREFIX + userId + ":" + performanceId + ":" + scheduleId;
        String activeTokensKey = ACTIVE_TOKENS_KEY_PREFIX + performanceId;

        log.info("=== 세션 해제 시작: 사용자={}, 공연={} ===", userId, performanceId);

        synchronized (queueLock) {
            boolean heartbeatExisted = Boolean.TRUE.equals(redisTemplate.delete(heartbeatKey));

            Optional<QueueToken> activeToken = queueTokenRepository
                    .findActiveTokenByUserIdAndPerformanceId(userId, performanceId);

            if (activeToken.isPresent() &&
                    activeToken.get().getStatus() == QueueToken.TokenStatus.ACTIVE) {

                QueueToken token = activeToken.get();
                token.markAsExpired();
                queueTokenRepository.save(token);

                log.info(">>> DB 토큰 만료: {}", token.getToken());
            }

            if (heartbeatExisted) {
                String countStr = redisTemplate.opsForValue().get(activeTokensKey);
                int currentCount = (countStr != null) ? Integer.parseInt(countStr) : 0;

                if (currentCount > 0) {
                    redisTemplate.opsForValue().decrement(activeTokensKey);
                    log.info(">>> Redis 카운터 감소: {} -> {}", currentCount, currentCount - 1);
                }
            }

            activateNextTokensInternal(performanceId, activeTokensKey);
        }

        log.info(">>> 세션 해제 완료");
    }

    /**
     * 내부용 - 락이 이미 걸려있다고 가정
     */
    private void activateNextTokensInternal(Long performanceId, String activeTokensKey) {
        String activeStr = redisTemplate.opsForValue().get(activeTokensKey);
        int currentActive = (activeStr != null) ? Integer.parseInt(activeStr) : 0;

        log.info("=== 다음 대기자 활성화: 공연={}, 현재={}/{} ===",
                performanceId, currentActive, maxActiveTokens);

        if (currentActive < maxActiveTokens) {
            int slotsAvailable = maxActiveTokens - currentActive;

            List<QueueToken> waitingTokens = queueTokenRepository
                    .findWaitingTokensByPerformanceIdOrderByIssuedAt(performanceId)
                    .stream()
                    .limit(slotsAvailable)
                    .toList();

            for (QueueToken token : waitingTokens) {
                redisTemplate.opsForValue().increment(activeTokensKey);
                redisTemplate.expire(activeTokensKey, Duration.ofMinutes(10));

                token.activate();
                log.info(">>> 토큰 활성화: {}", token.getToken());
            }

            if (!waitingTokens.isEmpty()) {
                queueTokenRepository.saveAll(waitingTokens);
                updateWaitingPositions(performanceId);
            }
        }
    }

    /**
     * 다음 대기자 활성화
     */
    @Transactional
    public void activateNextTokens(Long performanceId) {
        String activeTokensKey = ACTIVE_TOKENS_KEY_PREFIX + performanceId;

        synchronized (queueLock) {
            activateNextTokensInternal(performanceId, activeTokensKey);
        }
    }

    /**
     * Heartbeat 시작
     */
    private void startHeartbeat(String userId, Long performanceId, Long scheduleId) {
        String heartbeatKey = HEARTBEAT_KEY_PREFIX + userId + ":" + performanceId + ":" + scheduleId;
        redisTemplate.opsForValue().set(heartbeatKey, LocalDateTime.now().toString(),
                Duration.ofSeconds(maxInactiveSeconds));
        log.info("Heartbeat 시작: {}", heartbeatKey);
    }

    /**
     * Heartbeat 갱신
     */
    public void updateHeartbeat(String userId, Long performanceId, Long scheduleId) {
        String heartbeatKey = HEARTBEAT_KEY_PREFIX + userId + ":" + performanceId + ":" + scheduleId;
        redisTemplate.opsForValue().set(heartbeatKey, LocalDateTime.now().toString(),
                Duration.ofSeconds(maxInactiveSeconds));
    }
    /**
     * 비활성 세션 정리 및 만료 토큰 처리
     */
    public void cleanupInactiveSessions() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(maxInactiveSeconds);
            Set<String> heartbeatKeys = redisTemplate.keys(HEARTBEAT_KEY_PREFIX + "*");

            if (heartbeatKeys != null) {
                for (String heartbeatKey : heartbeatKeys) {
                    String lastHeartbeat = redisTemplate.opsForValue().get(heartbeatKey);
                    if (lastHeartbeat != null) {
                        LocalDateTime lastTime = LocalDateTime.parse(lastHeartbeat);
                        if (lastTime.isBefore(cutoff)) {
                            processTimeout(heartbeatKey);
                        }
                    }
                }
            }

            List<QueueToken> expiredTokens = queueTokenRepository.findExpiredTokens(LocalDateTime.now());
            for (QueueToken token : expiredTokens) {
                if (token.getStatus() == QueueToken.TokenStatus.ACTIVE) {
                    token.markAsExpired();
                    releaseTokenFromRedis(token.getPerformanceId());
                    activateNextTokens(token.getPerformanceId());
                }
            }
            if (!expiredTokens.isEmpty()) {
                queueTokenRepository.saveAll(expiredTokens);
            }

        } catch (Exception e) {
            log.error("비활성 세션 정리 중 오류", e);
        }
    }

    private void processTimeout(String heartbeatKey) {
        try {
            String[] parts = heartbeatKey.replace(HEARTBEAT_KEY_PREFIX, "").split(":");
            if (parts.length >= 3) {
                String userId = parts[0];
                Long performanceId = Long.parseLong(parts[1]);
                Long scheduleId = Long.parseLong(parts[2]);

                log.warn("세션 타임아웃 - 사용자: {}", userId);
                releaseSession(userId, performanceId, scheduleId);
            }
        } catch (Exception e) {
            log.error("타임아웃 처리 중 오류", e);
        }
    }

    private void releaseTokenFromRedis(Long performanceId) {
        String activeTokensKey = ACTIVE_TOKENS_KEY_PREFIX + performanceId;
        Long activeCount = redisTemplate.opsForValue().decrement(activeTokensKey);
        if (activeCount < 0) {
            redisTemplate.opsForValue().set(activeTokensKey, "0");
        }
        log.info("Redis 활성 토큰 수 감소: {}", activeCount);
    }

    private void updateWaitingPositions(Long performanceId) {
        List<QueueToken> waitingTokens = queueTokenRepository
                .findWaitingTokensByPerformanceIdOrderByIssuedAt(performanceId);

        for (int i = 0; i < waitingTokens.size(); i++) {
            QueueToken token = waitingTokens.get(i);
            int position = i + 1;
            int estimatedSeconds = position * waitTimePerPerson;
            int estimatedMinutes = Math.max(1, estimatedSeconds / 60);

            token.setPositionInQueue(position);
            token.setEstimatedWaitTimeMinutes(estimatedMinutes);
        }

        if (!waitingTokens.isEmpty()) {
            queueTokenRepository.saveAll(waitingTokens);
        }
    }

    private void updateQueuePosition(QueueToken token) {
        if (token.getStatus() == QueueToken.TokenStatus.WAITING) {
            Long position = queueTokenRepository.findPositionInQueue(
                    token.getPerformanceId(), token.getIssuedAt()) + 1;
            int estimatedSeconds = position.intValue() * waitTimePerPerson;
            int estimatedMinutes = Math.max(1, estimatedSeconds / 60);

            token.setPositionInQueue(position.intValue());
            token.setEstimatedWaitTimeMinutes(estimatedMinutes);
            queueTokenRepository.save(token);
        }
    }

    private int getRedisWaitingCount(Long performanceId) {
        return queueTokenRepository.countWaitingTokensByPerformanceId(performanceId).intValue();
    }

    private String generateToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private TokenIssueResponse createTokenResponse(QueueToken token, String message) {
        Integer position = token.getPositionInQueue() != null ? token.getPositionInQueue() : 1;
        Integer waitTime = token.getEstimatedWaitTimeMinutes() != null ?
                token.getEstimatedWaitTimeMinutes() : position * waitTimePerPerson / 60;

        return TokenIssueResponse.builder()
                .token(token.getToken())
                .status(token.getStatus())
                .positionInQueue(position)
                .estimatedWaitTime(waitTime)
                .message(message)
                .expiresAt(token.getExpiresAt())
                .bookingExpiresAt(token.getBookingExpiresAt())
                .build();
    }

    private QueueStatusResponse buildQueueStatusResponse(QueueToken token) {
        Integer position;
        if (token.getPositionInQueue() != null) {
            position = token.getPositionInQueue();
        } else {
            if (token.getStatus() == QueueToken.TokenStatus.WAITING) position = 1;
            else position = 0;
        }
        Integer waitTime;
        if (token.getEstimatedWaitTimeMinutes() != null) {
            waitTime = token.getEstimatedWaitTimeMinutes();
        } else {
            if (token.getStatus() == QueueToken.TokenStatus.WAITING) waitTime = Math.max(1, waitTimePerPerson / 60);
            else waitTime = 0;
        }

        return QueueStatusResponse.builder()
                .token(token.getToken())
                .status(token.getStatus())
                .positionInQueue(position)
                .estimatedWaitTime(waitTime)
                .isActiveForBooking(token.isActiveForBooking())
                .bookingExpiresAt(token.getBookingExpiresAt())
                //  performanceTitle 제거 (Performance entity 없음)
                .performanceTitle(null)
                .build();
    }

    private QueueCheckResponse buildQueueCheckResponse(QueueToken token, Long performanceId, Long ScheduleId) {
        if (token.getStatus() == QueueToken.TokenStatus.ACTIVE) {
            String activeTokensKey = ACTIVE_TOKENS_KEY_PREFIX + performanceId;
            String activeTokensStr = redisTemplate.opsForValue().get(activeTokensKey);
            int activeTokens = activeTokensStr != null ? Integer.parseInt(activeTokensStr) : 0;

            return QueueCheckResponse.builder()
                    .requiresQueue(false)
                    .canProceedDirectly(true)
                    .sessionId(token.getToken())
                    .message("이미 활성화된 토큰이 있습니다")
                    .currentActiveSessions(activeTokens)
                    .maxConcurrentSessions(maxActiveTokens)
                    .build();

        } else {
            updateQueuePosition(token);
            int estimatedWait = token.getPositionInQueue() * waitTimePerPerson;

            return QueueCheckResponse.builder()
                    .requiresQueue(true)
                    .canProceedDirectly(false)
                    .sessionId(token.getToken())
                    .message("대기열에서 대기 중입니다")
                    .estimatedWaitTime(estimatedWait)
                    .currentWaitingCount(token.getPositionInQueue())
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public QueueToken getTokenByString(String token) {
        return queueTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("토큰을 찾을 수 없습니다: " + token));
    }

    public void cancelToken(String token, String userId) {
        QueueToken queueToken = queueTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException(TOKEN_ERROR_MSG));

        if (!queueToken.getUserId().equals(userId)) {
            throw new IllegalArgumentException("토큰을 취소할 권한이 없습니다");
        }

        QueueToken.TokenStatus originalStatus = queueToken.getStatus();
        boolean wasActive = (originalStatus == QueueToken.TokenStatus.ACTIVE);

        queueToken.setStatus(QueueToken.TokenStatus.CANCELLED);
        queueTokenRepository.save(queueToken);

        log.info("토큰 취소: {} (원래 상태: {})", token, originalStatus);

        if (wasActive) {
            releaseTokenFromRedis(queueToken.getPerformanceId());
            log.info(">>> 활성 토큰 취소로 Redis 카운터 감소");
        }

        activateNextTokens(queueToken.getPerformanceId());
    }

    @Transactional(readOnly = true)
    public List<QueueStatusResponse> getUserActiveTokens(String userId) {
        List<QueueToken> tokens = queueTokenRepository.findActiveTokensByUserId(userId);
        return tokens.stream()
                .map(token -> QueueStatusResponse.builder()
                        .token(token.getToken())
                        .status(token.getStatus())
                        .positionInQueue(token.getPositionInQueue())
                        .estimatedWaitTime(token.getEstimatedWaitTimeMinutes())
                        .isActiveForBooking(token.isActiveForBooking())
                        .bookingExpiresAt(token.getBookingExpiresAt())
                        .performanceTitle(null) // Entity 없음 fixme
                        .build())
                .toList();
    }

    public void clearAllSessions() {
        try {
            Set<String> sessionKeys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
            Set<String> heartbeatKeys = redisTemplate.keys(HEARTBEAT_KEY_PREFIX + "*");
            Set<String> activeTokenKeys = redisTemplate.keys(ACTIVE_TOKENS_KEY_PREFIX + "*");

            if (!sessionKeys.isEmpty()) {
                redisTemplate.delete(sessionKeys);
            }
            if (!heartbeatKeys.isEmpty()) {
                redisTemplate.delete(heartbeatKeys);
            }
            if (!activeTokenKeys.isEmpty()) {
                redisTemplate.delete(activeTokenKeys);
            }
            log.info("모든 세션 초기화 완료");
        } catch (Exception e) {
            log.error("세션 초기화 중 오류", e);
        }
    }

    public void cleanupOldTokens() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(1);
        List<QueueToken> oldTokens = queueTokenRepository.findOldUsedTokens(cutoffTime);
        if (!oldTokens.isEmpty()) {
            queueTokenRepository.deleteAll(oldTokens);
            log.info("오래된 토큰 {} 개 정리 완료", oldTokens.size());
        }
    }

    public void processQueue() {
        cleanupInactiveSessions();
    }

    @Transactional(readOnly = true)
    public List<QueueStatsResponse> getQueueStatsByPerformance() {
        // Performance entity 없이 통계 조회
        // 이 메서드는 사용 안 하거나 performanceId 목록을 받아야 함
        log.warn("getQueueStatsByPerformance() - Performance entity 제거로 사용 불가");
        return List.of();
    }

    public void forceProcessQueue(Long performanceId) {
        activateNextTokens(performanceId);
        updateWaitingPositions(performanceId);
        log.info("공연 {} 대기열 강제 처리 완료", performanceId);
    }
}