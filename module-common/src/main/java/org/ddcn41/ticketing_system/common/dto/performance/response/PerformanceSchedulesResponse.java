package org.ddcn41.ticketing_system.common.dto.performance.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "공연 회차 목록 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceSchedulesResponse {

    @Schema(description = "공연 회차 목록")
    private List<ScheduleResponse> schedules;
}
