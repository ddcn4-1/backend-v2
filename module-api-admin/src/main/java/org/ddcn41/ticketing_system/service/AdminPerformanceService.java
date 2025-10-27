package org.ddcn41.ticketing_system.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.client.PerformanceClient;
import org.ddcn41.ticketing_system.common.client.S3Client;
import org.ddcn41.ticketing_system.common.dto.performance.request.PerformanceRequest;
import org.ddcn41.ticketing_system.common.dto.performance.request.PresignedUrlRequest;
import org.ddcn41.ticketing_system.common.dto.performance.response.AdminPerformanceResponse;
import org.ddcn41.ticketing_system.common.dto.performance.response.PresignedUrlResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPerformanceService {
    private final PerformanceClient performanceClient;
    private final S3Client s3Client;

    public List<AdminPerformanceResponse> getAllAdminPerformance() {
        return performanceClient.getAllAdminPerformance();
    }

    public AdminPerformanceResponse getAdminPerformanceById(Long performanceId) {
        return performanceClient.getAdminPerformance(performanceId);
    }

    public AdminPerformanceResponse createPerformance(PerformanceRequest request) {
        return performanceClient.createPerformance(request);
    }

    public AdminPerformanceResponse updatePerformance(Long performanceId, PerformanceRequest request) {
        return performanceClient.updatePerformance(performanceId, request);
    }

    public void deletePerformance(Long performanceId) {
        performanceClient.deletePerformance(performanceId);
    }

    public PresignedUrlResponse getUploadImagePresignedURL(PresignedUrlRequest request) {
        return s3Client.getUploadPresignedUrl(request);
    }
}
