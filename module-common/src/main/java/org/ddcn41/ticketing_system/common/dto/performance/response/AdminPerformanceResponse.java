package org.ddcn41.ticketing_system.common.dto.performance.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPerformanceResponse {
    private PerformanceResponse performanceResponse;
    private BigDecimal revenue;
    private Integer totalBookings;
}
