package org.ddcn41.queue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "queue_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tokenId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    // Performance Entity 제거 → ID만 저장
    @Column(nullable = false)
    private Long performanceId;

    //  User Entity 제거 → ID만 저장
    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime bookingExpiresAt;

    private Integer positionInQueue;

    private Integer estimatedWaitTimeMinutes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== Enums =====

    public enum TokenStatus {
        WAITING,   // 대기 중
        ACTIVE,    // 활성화 (예매 가능)
        USED,      // 사용 완료
        EXPIRED,   // 만료됨
        CANCELLED  // 취소됨
    }

    // ===== Business Methods =====

    /**
     * 토큰 활성화 (예매 가능 상태로 전환)
     */
    public void activate() {
        this.status = TokenStatus.ACTIVE;
        this.bookingExpiresAt = LocalDateTime.now().plusMinutes(10);
        this.positionInQueue = 0;
        this.estimatedWaitTimeMinutes = 0;
    }

    /**
     * 토큰 사용 완료 처리
     */
    public void markAsUsed() {
        this.status = TokenStatus.USED;
        this.bookingExpiresAt = null;
    }

    /**
     * 토큰 만료 처리
     */
    public void markAsExpired() {
        this.status = TokenStatus.EXPIRED;
        this.bookingExpiresAt = null;
    }

    /**
     * 토큰 취소 처리
     */
    public void cancel() {
        this.status = TokenStatus.CANCELLED;
        this.bookingExpiresAt = null;
    }

    /**
     * 토큰이 만료되었는지 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 토큰이 예매 가능한 상태인지 확인
     */
    public boolean isActiveForBooking() {
        if (status != TokenStatus.ACTIVE) {
            return false;
        }
        if (bookingExpiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(bookingExpiresAt);
    }

    /**
     * 대기 시간 업데이트
     */

    public Integer getPositionInQueue() {
        return this.positionInQueue != null ? this.positionInQueue :
                (this.status == TokenStatus.WAITING ? 1 : 0);
    }

    public Integer getEstimatedWaitTimeMinutes() {
        return this.estimatedWaitTimeMinutes != null ? this.estimatedWaitTimeMinutes :
                (this.status == TokenStatus.WAITING ? 60 : 0);
    }
}