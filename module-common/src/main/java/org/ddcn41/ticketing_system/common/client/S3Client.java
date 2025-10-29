package org.ddcn41.ticketing_system.common.client;

import org.ddcn41.ticketing_system.common.dto.performance.request.PresignedUrlRequest;
import org.ddcn41.ticketing_system.common.dto.performance.response.PresignedUrlResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "s3-service", url = "${s3.service.url:http://localhost:8082}")
public interface S3Client {

    @PostMapping("/v1/internal/performances/upload-url")
    PresignedUrlResponse getUploadPresignedUrl(@RequestBody PresignedUrlRequest request);

}
