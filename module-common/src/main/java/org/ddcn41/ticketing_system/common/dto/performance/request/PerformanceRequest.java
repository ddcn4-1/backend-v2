package org.ddcn41.ticketing_system.common.dto.performance.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceRequest {
    private Long venueId;
    private String title;
    private String description;
    private String theme;
    private String posterUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer runningTime;
    private BigDecimal basePrice;
    private String status;
    private List<PerformanceScheduleRequest> schedules;
}
