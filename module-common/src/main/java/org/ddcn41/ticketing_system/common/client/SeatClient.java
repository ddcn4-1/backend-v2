package org.ddcn41.ticketing_system.common.client;

import org.ddcn41.ticketing_system.common.dto.seat.InitializeSeatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "seat-service", url = "${user.service.url:http://localhost:8082}")
public interface SeatClient {
    @PostMapping("/v1/internal/schedules/initialize")
    List<InitializeSeatsResponse> initializeAllSchedules(@RequestParam(value = "dryRun") boolean dryRun);

    @PostMapping("/v1/internal/schedules/initialize/{scheduleId}")
    InitializeSeatsResponse initializeSchedule(@PathVariable("scheduleId") Long scheduleId,
                                               @RequestParam(value = "dryRun") boolean dryRun);
}
