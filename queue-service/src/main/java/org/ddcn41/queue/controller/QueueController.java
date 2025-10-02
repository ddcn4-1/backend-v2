package org.ddcn41.queue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.ddcn41.queue.dto.request.*;
import org.ddcn41.queue.dto.response.*;
import org.ddcn41.queue.service.QueueService;
import org.ddcn41.common.dto.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/queue")
@RequiredArgsConstructor
@Tag(name = "Queue", description = "대기열 관리 API")
public class QueueController {

    private final QueueService queueService;

    /**
     * 대기열 필요성 확인
     */
    @PostMapping("/check")
    @Operation(summary = "대기열 필요성 확인", description = "예매 시도 시 대기열이 필요한지 확인합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<QueueCheckResponse>> checkQueueRequirement(
            @Valid @RequestBody TokenRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());

        QueueCheckResponse response = queueService.getBookingToken(
                request.getPerformanceId(),
                request.getScheduleId(),
                userId
        );

        return ResponseEntity.ok(
                ApiResponse.success(response, "대기열 확인 완료")
        );
    }

    /**
     * 대기열 토큰 발급
     */
    @PostMapping("/token")
    @Operation(summary = "대기열 토큰 발급", description = "특정 공연에 대한 대기열 토큰을 발급받습니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TokenIssueResponse>> issueToken(
            @Valid @RequestBody TokenIssueRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());

        TokenIssueResponse response = queueService.issueQueueToken(
                userId, request.getPerformanceId());

        return ResponseEntity.ok(
                ApiResponse.success(response, "대기열 토큰이 발급되었습니다")
        );
    }

    /**
     * 대기열 토큰 활성화
     */
    @PostMapping("/activate")
    @Operation(summary = "대기열 토큰 활성화", description = "WAITING 상태의 토큰을 ACTIVE로 승격합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> activateToken(
            @Valid @RequestBody TokenActivateRequest request,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());

        QueueStatusResponse response = queueService.activateToken(
                request.getToken(),
                userId,
                request.getPerformanceId(),
                request.getScheduleId()
        );

        return ResponseEntity.ok(
                ApiResponse.success(response, "대기열 토큰이 활성화되었습니다")
        );
    }

    /**
     * 토큰 상태 조회
     */
    @GetMapping("/status/{token}")
    @Operation(summary = "토큰 상태 조회", description = "발급받은 토큰의 현재 상태와 대기열 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getTokenStatus(
            @PathVariable String token) {

        QueueStatusResponse response = queueService.getTokenStatus(token);

        return ResponseEntity.ok(
                ApiResponse.success(response, "토큰 상태 조회 성공")
        );
    }

    /**
     * 사용자의 활성 토큰 목록 조회
     */
    @GetMapping("/my-tokens")
    @Operation(summary = "내 토큰 목록", description = "현재 사용자의 모든 활성 토큰을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<List<QueueStatusResponse>>> getMyTokens(
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());

        List<QueueStatusResponse> responses = queueService.getUserActiveTokens(userId);

        return ResponseEntity.ok(
                ApiResponse.success(responses, "토큰 목록 조회 성공")
        );
    }

    /**
     * Heartbeat 전송
     */
    @PostMapping("/heartbeat")
    @Operation(summary = "Heartbeat 전송", description = "사용자가 활성 상태임을 알리는 heartbeat를 전송합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<String>> sendHeartbeat(
            @RequestBody(required = false) HeartbeatRequest request,
            Authentication authentication) {

        try {
            Long userId = Long.parseLong(authentication.getName());

            if (request != null) {
                queueService.updateHeartbeat(
                        userId,
                        request.getPerformanceId(),
                        request.getScheduleId()
                );
            }

            return ResponseEntity.ok(
                    ApiResponse.success("Heartbeat 수신됨")
            );
        } catch (Exception e) {
            return ResponseEntity.ok(
                    ApiResponse.success("Heartbeat 처리됨")
            );
        }
    }

    /**
     * Beacon API용 세션 해제
     */
    @PostMapping(
            value = "/release-session",
            consumes = {"application/json", "text/plain", "*/*"}
    )
    @Operation(summary = "Beacon 세션 해제", description = "Beacon API를 통한 세션 해제")
    public ResponseEntity<ApiResponse<String>> releaseSessionBeacon(
            @RequestBody(required = false) String requestBody) {

        try {
            if (requestBody != null && !requestBody.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> request = mapper.readValue(requestBody, Map.class);

                Object performanceIdObj = request.get("performanceId");
                Object scheduleIdObj = request.get("scheduleId");
                Object userIdObj = request.get("userId");

                if (performanceIdObj != null && scheduleIdObj != null && userIdObj != null) {
                    Long performanceId = Long.valueOf(performanceIdObj.toString());
                    Long scheduleId = Long.valueOf(scheduleIdObj.toString());
                    Long userId = Long.valueOf(userIdObj.toString());

                    queueService.releaseSession(userId, performanceId, scheduleId);
                }
            }

            return ResponseEntity.ok(ApiResponse.success("Beacon 세션 해제 처리됨"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success("Beacon 세션 해제 시도됨"));
        }
    }

    /**
     * 토큰 취소
     */
    @DeleteMapping("/token/{token}")
    @Operation(summary = "토큰 취소", description = "대기열에서 나가고 토큰을 취소합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<String>> cancelToken(
            @PathVariable String token,
            Authentication authentication) {

        Long userId = Long.parseLong(authentication.getName());

        queueService.cancelToken(token, userId);

        return ResponseEntity.ok(
                ApiResponse.success("토큰이 취소되었습니다")
        );
    }

    /**
     * 세션 초기화 (관리자 전용)
     */
    @PostMapping("/clear-sessions")
    @Operation(summary = "세션 초기화", description = "모든 활성 세션을 초기화합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> clearAllSessions() {
        queueService.clearAllSessions();
        return ResponseEntity.ok(ApiResponse.success("모든 세션이 초기화되었습니다"));
    }

    /**
     * 세션 정보 조회 (디버그용)
     */
    @GetMapping("/session-info")
    @Operation(summary = "세션 정보 조회", description = "현재 사용자의 세션 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<String>> getSessionInfo(
            Authentication authentication,
            HttpServletRequest request) {

        String username = authentication.getName();
        String sessionId = request.getSession().getId();
        String remoteAddr = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        String sessionInfo = String.format(
                "Username: %s, SessionId: %s, IP: %s, UserAgent: %s",
                username, sessionId, remoteAddr, userAgent
        );

        return ResponseEntity.ok(
                ApiResponse.success(sessionInfo, "세션 정보 조회 완료")
        );
    }
}