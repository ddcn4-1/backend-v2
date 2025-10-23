package org.ddcn41.queue.dto.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 대기열 토큰 검증 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenVerifyResponse {

    /**
     * 토큰 유효 여부
     */
    private boolean valid;

    /**
     * 유효하지 않을 경우 사유
     */
    private String reason;

    /**
     * 검증 시각
     */
    private LocalDateTime checkedAt;
}