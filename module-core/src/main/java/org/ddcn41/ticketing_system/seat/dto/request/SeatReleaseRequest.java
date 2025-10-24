package org.ddcn41.ticketing_system.seat.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatReleaseRequest {
    @NotEmpty(message = "좌석 ID 목록은 필수입니다")
    private List<Long> seatIds;

    @NotNull(message = "사용자 ID는 필수입니다")
    private String userId;

    private String sessionId;
}