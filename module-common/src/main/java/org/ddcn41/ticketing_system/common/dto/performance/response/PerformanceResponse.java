package org.ddcn41.ticketing_system.common.dto.performance.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceResponse {
    private Long performanceId;
    private String title;
    private String venue;
    private String theme;
    private String posterUrl;
    private BigDecimal price;
    private String status;
    private String description;
    private String startDate;
    private String endDate;
    private Integer runningTime;
    private String venueAddress;
    private Long venueId;
    private List<ScheduleResponse> schedules;
}
