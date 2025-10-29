package org.ddcn41.queue.controller;

import lombok.extern.slf4j.Slf4j;
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
import org.ddcn41.queue.domain.CustomUserDetails;
import org.ddcn41.queue.dto.ApiResponse;
import org.ddcn41.queue.dto.queue.TokenVerifyRequest;
import org.ddcn41.queue.dto.queue.TokenVerifyResponse;
import org.ddcn41.queue.dto.request.HeartbeatRequest;
import org.ddcn41.queue.dto.request.TokenActivateRequest;
import org.ddcn41.queue.dto.request.TokenIssueRequest;
import org.ddcn41.queue.dto.request.TokenRequest;
import org.ddcn41.queue.dto.response.QueueCheckResponse;
import org.ddcn41.queue.dto.response.QueueStatusResponse;
import org.ddcn41.queue.dto.response.TokenIssueResponse;
import org.ddcn41.queue.service.QueueService;
import org.ddcn41.starter.authorization.model.BasicCognitoUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/queue")
@RequiredArgsConstructor
@Tag(name = "Queue", description = "대기열 관리 API")
public class QueueController {

    private final QueueService queueService;

    // ... 기존 API 메서드들 (생략)

    /**
     * 대기열 확인
     */
    @PostMapping("/check")
    @Operation(summary = "대기열 필요성 확인")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<QueueCheckResponse>> checkQueueRequirement(
            @Valid @RequestBody TokenRequest request,
            @AuthenticationPrincipal BasicCognitoUser currentUser
    ) {
        String userId = currentUser.getUserId();

        QueueCheckResponse response = queueService.getBookingToken(
                request.getPerformanceId(),
                request.getScheduleId(),
                userId
        );

        return ResponseEntity.ok(ApiResponse.success("대기열 확인 완료", response));
    }

    /**
     * 대기열 토큰 발급
     */
    @PostMapping("/token")
    @Operation(summary = "대기열 토큰 발급")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TokenIssueResponse>> issueToken(
            @Valid @RequestBody TokenIssueRequest request,
            @AuthenticationPrincipal BasicCognitoUser currentUser
    ) {
        String userId = currentUser.getUserId();

        TokenIssueResponse response = queueService.issueQueueToken(
                userId,
                request.getPerformanceId()
        );

        return ResponseEntity.ok(ApiResponse.success("대기열 토큰이 발급되었습니다", response));
    }

    /**
     * 대기열 토큰 활성화
     */
    @PostMapping("/activate")
    @Operation(summary = "대기열 토큰 활성화")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> activateToken(
            @Valid @RequestBody TokenActivateRequest request,
            @AuthenticationPrincipal BasicCognitoUser currentUser
    ) {
        String userId =currentUser.getUserId();

        QueueStatusResponse response = queueService.activateToken(
                request.getToken(),
                userId,
                request.getPerformanceId(),
                request.getScheduleId()
        );

        return ResponseEntity.ok(ApiResponse.success("대기열 토큰이 활성화되었습니다", response));
    }

    /**
     * 내 토큰 목록
     */
    @GetMapping("/my-tokens")
    @Operation(summary = "내 토큰 목록")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<List<QueueStatusResponse>>> getMyTokens(
           @AuthenticationPrincipal BasicCognitoUser currentUser
    ) {
        String userId = currentUser.getUserId();
        List<QueueStatusResponse> responses = queueService.getUserActiveTokens(userId);

        return ResponseEntity.ok(ApiResponse.success("토큰 목록 조회 성공", responses));
    }

    /**
     * Heartbeat 전송
     */
    @PostMapping("/heartbeat")
    @Operation(summary = "Heartbeat 전송")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<String>> sendHeartbeat(
            @RequestBody(required = false) HeartbeatRequest request,
            @AuthenticationPrincipal BasicCognitoUser currentUser
    ) {
        try {
            String userId = currentUser.getUserId();

            if (request != null) {
                queueService.updateHeartbeat(
                        userId,
                        request.getPerformanceId(),
                        request.getScheduleId()
                );
            }

            return ResponseEntity.ok(ApiResponse.success("Heartbeat 수신됨"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success("Heartbeat 처리됨"));
        }
    }

    /**
     * 토큰 취소
     */
    @DeleteMapping("/token/{token}")
    @Operation(summary = "토큰 취소")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<String>> cancelToken(
            @PathVariable String token,
            @AuthenticationPrincipal BasicCognitoUser currentUser
    ) {
        String userId = currentUser.getUserId();
        queueService.cancelToken(token, userId);

        return ResponseEntity.ok(ApiResponse.success("토큰이 취소되었습니다"));
    }

    /**
     * 세션 정보 조회
     */
    @GetMapping("/session-info")
    @Operation(summary = "세션 정보 조회")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<String>> getSessionInfo(
            @AuthenticationPrincipal BasicCognitoUser currentUser,
            HttpServletRequest request
    ) {
        String username = currentUser.getUsername();
        String userId = currentUser.getUserId();
        String sessionId = request.getSession().getId();
        String remoteAddr = request.getRemoteAddr();

        String sessionInfo = String.format(
                "Username: %s, UserId: %s, SessionId: %s, IP: %s",
                username, userId, sessionId, remoteAddr
        );

        return ResponseEntity.ok(ApiResponse.success("세션 정보 조회 완료", sessionInfo));
    }

    // ========== 인증이 필요 없는 API ==========

    /**
     * 토큰 검증 (인증 불필요)
     */
    @PostMapping("/token/{token}/verify")
    @Operation(summary = "토큰 검증")
    public ResponseEntity<ApiResponse<TokenVerifyResponse>> verifyToken(
            @PathVariable String token,
            @Valid @RequestBody TokenVerifyRequest request
    ) {
        boolean isValid = queueService.validateTokenForBooking(
                token,
                request.getUserId(),
                request.getPerformanceId()
        );

        TokenVerifyResponse response = TokenVerifyResponse.builder()
                .valid(isValid)
                .reason(isValid ? null : "토큰이 유효하지 않거나 만료되었습니다")
                .checkedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(ApiResponse.success("토큰 검증 완료", response));
    }

    /**
     * 토큰 사용 완료 (인증 불필요)
     */
    @PostMapping("/token/{token}/use")
    @Operation(summary = "토큰 사용 완료")
    public ResponseEntity<ApiResponse<Void>> useToken(@PathVariable String token) {
        try {
            queueService.useToken(token);
            return ResponseEntity.ok(ApiResponse.success("토큰 사용 처리 완료"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("토큰을 찾을 수 없습니다"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("예매 가능한 상태가 아닙니다"));
        }
    }

    /**
     * 토큰 상태 조회 (인증 불필요)
     */
    @GetMapping("/status/{token}")
    @Operation(summary = "토큰 상태 조회")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getTokenStatus(
            @Parameter(description = "토큰 문자열", required = true)
            @PathVariable String token
    ) {
        QueueStatusResponse response = queueService.getTokenStatus(token);
        return ResponseEntity.ok(ApiResponse.success("토큰 상태 조회 성공", response));
    }

    /**
     * Beacon 세션 해제 (인증 불필요)
     */
    @PostMapping(value = "/release-session", consumes = {"application/json", "text/plain", "*/*"})
    @Operation(summary = "Beacon 세션 해제")
    public ResponseEntity<ApiResponse<String>> releaseSessionBeacon(
            @RequestBody(required = false) String requestBody
    ) {
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
                    String userId = userIdObj.toString();

                    queueService.releaseSession(userId, performanceId, scheduleId);
                }
            }

            return ResponseEntity.ok(ApiResponse.success("Beacon 세션 해제 처리됨"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success("Beacon 세션 해제 시도됨"));
        }
    }

    /**
     * 세션 초기화 (관리자 전용)
     */
    @PostMapping("/clear-sessions")
    @Operation(summary = "세션 초기화")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> clearAllSessions() {
        queueService.clearAllSessions();
        return ResponseEntity.ok(ApiResponse.success("모든 세션이 초기화되었습니다"));
    }


    // ========== Helper Methods ==========
//  authorization-start로 불필요해짐
//    /**
//     * userId 추출 (단일 메서드)
//     */
//    private String extractUserId(Authentication auth) {
//        if (auth == null) {
//            log.warn("⚠️ Authentication is null");
//            throw new ResponseStatusException(
//                    HttpStatus.UNAUTHORIZED,
//                    "인증이 필요합니다"
//            );
//        }
//
//        Object principal = auth.getPrincipal();
//
//        log.debug("Principal type: {}", principal.getClass().getName());
//
//        if (!(principal instanceof CustomUserDetails)) {
//            log.warn("⚠️ Invalid principal type: {}", principal.getClass().getName());
//            throw new ResponseStatusException(
//                    HttpStatus.UNAUTHORIZED,
//                    "인증 정보가 올바르지 않습니다"
//            );
//        }
//
//        CustomUserDetails userDetails = (CustomUserDetails) principal;
//        String userId = userDetails.getUserId();
//
//        if (userId == null || userId.isEmpty()) {
//            log.warn("⚠️ UserId is null or empty for user: {}", userDetails.getUsername());
//            throw new ResponseStatusException(
//                    HttpStatus.UNAUTHORIZED,
//                    "사용자 정보를 찾을 수 없습니다"
//            );
//        }
//
//        log.debug("✅ Extracted userId: {}", userId);
//        return userId;
//    }
}