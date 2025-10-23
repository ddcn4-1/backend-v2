package org.ddcn41.ticketing_system.common.client;

import org.ddcn41.ticketing_system.common.dto.ApiResponse;
import org.ddcn41.ticketing_system.common.dto.queue.TokenVerifyRequest;
import org.ddcn41.ticketing_system.common.dto.queue.TokenVerifyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "queue-service", url = "${queue.service.url:http://localhost:8083}")
public interface QueueClient {

    @PostMapping("/v1/queue/token/{token}/verify")
    ApiResponse<TokenVerifyResponse> verifyToken(
            @PathVariable("token") String token,
            @RequestBody TokenVerifyRequest request
    );

    @PostMapping("/v1/queue/token/{token}/use")
    ApiResponse<Void> useToken(@PathVariable("token") String token);
}