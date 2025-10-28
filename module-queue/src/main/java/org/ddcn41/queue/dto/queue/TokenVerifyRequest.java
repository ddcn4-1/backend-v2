// module-queue/src/main/java/org/ddcn41/queue/dto/queue/TokenVerifyRequest.java

package org.ddcn41.queue.dto.queue;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 대기열 토큰 검증 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenVerifyRequest {

    @NotNull(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotNull(message = "공연 ID는 필수입니다")
    private Long performanceId;
}