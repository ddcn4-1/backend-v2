package org.ddcn41.queue.repository;

import org.ddcn41.queue.entity.QueueToken;
import org.ddcn41.queue.entity.QueueToken.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueTokenRepository extends JpaRepository<QueueToken, Long> {

    /**
     * 토큰 문자열으로 조회
     */
    Optional<QueueToken> findByToken(String token);

    /**
     * 사용자와 공연의 활성 토큰 조회 (WAITING 또는 ACTIVE)
     */
    @Query("SELECT qt FROM QueueToken qt " +
            "WHERE qt.userId = :userId " +
            "AND qt.performanceId = :performanceId " +
            "AND qt.status IN ('WAITING', 'ACTIVE') " +
            "ORDER BY qt.issuedAt DESC")
    Optional<QueueToken> findActiveTokenByUserIdAndPerformanceId(
            @Param("userId") String userId,
            @Param("performanceId") Long performanceId
    );

    /**
     * 사용자의 활성 토큰 목록 조회
     */
    @Query("SELECT qt FROM QueueToken qt " +
            "WHERE qt.userId = :userId " +
            "AND qt.status IN ('WAITING', 'ACTIVE') " +
            "ORDER BY qt.issuedAt DESC")
    List<QueueToken> findActiveTokensByUserId(@Param("userId") String userId);

    /**
     * 공연의 WAITING 토큰 목록 (발급 순서대로)
     */
    @Query("SELECT qt FROM QueueToken qt " +
            "WHERE qt.performanceId = :performanceId " +
            "AND qt.status = 'WAITING' " +
            "ORDER BY qt.issuedAt ASC")
    List<QueueToken> findWaitingTokensByPerformanceIdOrderByIssuedAt(
            @Param("performanceId") Long performanceId
    );

    /**
     * 공연의 대기열 순번 조회 (특정 토큰보다 앞에 있는 WAITING 토큰 수)
     */
    @Query("SELECT COUNT(qt) FROM QueueToken qt " +
            "WHERE qt.performanceId = :performanceId " +
            "AND qt.status = 'WAITING' " +
            "AND qt.issuedAt < :issuedAt")
    Long findPositionInQueue(
            @Param("performanceId") Long performanceId,
            @Param("issuedAt") LocalDateTime issuedAt
    );

    /**
     * 공연의 특정 상태 토큰 수 조회
     */
    Long countByPerformanceIdAndStatus(Long performanceId, TokenStatus status);

    /**
     * 공연의 WAITING 토큰 수
     */
    @Query("SELECT COUNT(qt) FROM QueueToken qt " +
            "WHERE qt.performanceId = :performanceId " +
            "AND qt.status = 'WAITING'")
    Long countWaitingTokensByPerformanceId(@Param("performanceId") Long performanceId);

    /**
     * 공연의 ACTIVE 토큰 수
     */
    @Query("SELECT COUNT(qt) FROM QueueToken qt " +
            "WHERE qt.performanceId = :performanceId " +
            "AND qt.status = 'ACTIVE'")
    Long countActiveTokensByPerformanceId(@Param("performanceId") Long performanceId);

    /**
     * 만료된 토큰 조회
     */
    @Query("SELECT qt FROM QueueToken qt " +
            "WHERE qt.expiresAt < :now " +
            "AND qt.status IN ('WAITING', 'ACTIVE')")
    List<QueueToken> findExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * 오래된 사용 완료 토큰 조회 (정리용)
     */
    @Query("SELECT qt FROM QueueToken qt " +
            "WHERE qt.status IN ('USED', 'EXPIRED', 'CANCELLED') " +
            "AND qt.updatedAt < :cutoffTime")
    List<QueueToken> findOldUsedTokens(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 공연별 토큰 상태 통계
     */
    @Query("SELECT qt.status, COUNT(qt) FROM QueueToken qt " +
            "WHERE qt.performanceId = :performanceId " +
            "GROUP BY qt.status")
    List<Object[]> getTokenStatsByPerformanceId(@Param("performanceId") Long performanceId);
}