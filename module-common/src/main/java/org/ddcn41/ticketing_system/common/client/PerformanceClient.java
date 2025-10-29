package org.ddcn41.ticketing_system.common.client;

import org.ddcn41.ticketing_system.common.dto.performance.request.PerformanceRequest;
import org.ddcn41.ticketing_system.common.dto.performance.response.AdminPerformanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "performance-service", url = "${performance.service.url:http://localhost:8082}")
public interface PerformanceClient {
    @GetMapping("/v1/internal/performances")
    List<AdminPerformanceResponse> getAllAdminPerformance();

    @GetMapping("/v1/internal/performances/{performanceId}")
    AdminPerformanceResponse getAdminPerformance(@PathVariable("performanceId") Long performanceId);

    @PostMapping("/v1/internal/performances")
    AdminPerformanceResponse createPerformance(@RequestBody PerformanceRequest request);

    @PutMapping("/v1/internal/performances/{performanceId}")
    AdminPerformanceResponse updatePerformance(@PathVariable("performanceId") Long performanceId, @RequestBody PerformanceRequest request);

    @DeleteMapping("/v1/internal/performances/{performanceId}")
    void deletePerformance(@PathVariable("performanceId") Long performanceId);
}
