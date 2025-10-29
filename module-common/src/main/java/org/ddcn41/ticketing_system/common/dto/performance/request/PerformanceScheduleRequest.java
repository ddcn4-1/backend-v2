package org.ddcn41.ticketing_system.common.dto.performance.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceScheduleRequest {
    private String showDatetime;
    private Integer totalSeats;
    private String status;
    private LocalDateTime bookingStartAt;
    private LocalDateTime bookingEndAt;
}
