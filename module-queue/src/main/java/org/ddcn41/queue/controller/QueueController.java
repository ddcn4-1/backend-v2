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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/queue")
@RequiredArgsConstructor
@Tag(name = "Queue", description = "대기열 관리 API")
public class QueueController {

    private final QueueService queueService;

    /**
     * 추가: 토큰 검증 API
     */
    @PostMapping("/token/{token}/verify")
    @Operation(summary = "토큰 검증", description = "예매 전 대기열 토큰 유효성 검증")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "토큰 검증 완료",
                    content = @Content(schema = @Schema(implementation = TokenVerifyResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<TokenVerifyResponse>> verifyToken(
            @PathVariable String token,
            @Valid @RequestBody TokenVerifyRequest request) {

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
     * 추가: 토큰 사용 API
     */
    @PostMapping("/token/{token}/use")
    @Operation(summary = "토큰 사용 완료", description = "예매 완료 후 토큰 사용 처리")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "토큰 사용 처리 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패")
    })
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
     * 대기열 필요성 확인
     */
    @PostMapping("/check")
    @Operation(summary = "대기열 필요성 확인", description = "예매 시도 시 대기열이 필요한지 확인합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대기열 확인 완료",
                    content = @Content(schema = @Schema(implementation = QueueCheckResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "공연을 찾을 수 없음",
                    content = @Content)
    })
    // 그대로 유지
    public ResponseEntity<ApiResponse<QueueCheckResponse>> checkQueueRequirement(
            @Valid @RequestBody TokenRequest request,
//            Authentication authentication) {
            @Nullable Authentication authentication) {


//        String username = authentication.getName();
        Long userId = extractUserIdFromAuth(authentication);
        if (userId == null) {
            // 기존 동작 보존: 임시 기본값
            userId = 1L;
        }

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
    @Operation(summary = "대기열 토큰 발급", description = "특정 공연에 대한 대기열 토큰을 발급받습니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TokenIssueResponse>> issueToken(
            @Valid @RequestBody TokenIssueRequest request,
            Authentication authentication) {

        Long userId = extractUserIdFromAuth(authentication);

        TokenIssueResponse response = queueService.issueQueueToken(
                userId, request.getPerformanceId());

        return ResponseEntity.ok(ApiResponse.success("대기열 토큰이 발급되었습니다", response));
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

        Long userId = extractUserIdFromAuth(authentication);

        QueueStatusResponse response = queueService.activateToken(
                request.getToken(),
                userId,
                request.getPerformanceId(),
                request.getScheduleId()
        );

        return ResponseEntity.ok(ApiResponse.success("대기열 토큰이 활성화되었습니다", response));
    }

    /**
     * 토큰 상태 조회
     */
    @GetMapping("/status/{token}")
    @Operation(summary = "토큰 상태 조회", description = "발급받은 토큰의 현재 상태와 대기열 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getTokenStatus(
            @Parameter(description = "토큰 문자열", required = true)
            @PathVariable String token) {

        QueueStatusResponse response = queueService.getTokenStatus(token);
        return ResponseEntity.ok(ApiResponse.success("토큰 상태 조회 성공", response));
    }

    /**
     * 사용자의 활성 토큰 목록 조회
     */
    @GetMapping("/my-tokens")
    @Operation(summary = "내 토큰 목록", description = "현재 사용자의 모든 활성 토큰을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<List<QueueStatusResponse>>> getMyTokens(
            Authentication authentication) {

        Long userId = extractUserIdFromAuth(authentication);
        List<QueueStatusResponse> responses = queueService.getUserActiveTokens(userId);

        return ResponseEntity.ok(ApiResponse.success("토큰 목록 조회 성공", responses));
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
            Long userId = extractUserIdFromAuth(authentication);

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
     * Beacon API용 세션 해제
     */
    @PostMapping(value = "/release-session", consumes = {"application/json", "text/plain", "*/*"})
    @Operation(summary = "Beacon 세션 해제", description = "Beacon API를 통한 세션 해제 (인증 불필요)")
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

        Long userId = extractUserIdFromAuth(authentication);
        queueService.cancelToken(token, userId);

        return ResponseEntity.ok(ApiResponse.success("토큰이 취소되었습니다"));
    }

    /**
     * 세션 초기화 (관리자 전용)
     */
    @PostMapping("/clear-sessions")
    @Operation(summary = "세션 초기화", description = "테스트를 위해 모든 활성 세션을 초기화합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> clearAllSessions() {
        queueService.clearAllSessions();
        return ResponseEntity.ok(ApiResponse.success("모든 세션이 초기화되었습니다"));
    }

    /**
     * 세션 상태 확인 (디버그용)
     */
    @GetMapping("/session-info")
    @Operation(summary = "세션 정보 조회", description = "현재 사용자의 세션 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<String>> getSessionInfo(
            Authentication authentication,
            HttpServletRequest request) {

        String username = authentication.getName();
        Long userId = extractUserIdFromAuth(authentication);
        String sessionId = request.getSession().getId();
        String remoteAddr = request.getRemoteAddr();

        String sessionInfo = String.format(
                "Username: %s, UserId: %s, SessionId: %s, IP: %s",
                username, userId, sessionId, remoteAddr
        );

        return ResponseEntity.ok(ApiResponse.success("세션 정보 조회 완료", sessionInfo));
    }

    /**
     * Authentication에서 userId 추출
     * TODO: Cognito 전환 시 수정 필요
     */
    /*private Long extractUserIdFromAuth(Authentication authentication) {
        // 방법 1: JWT claims에서 직접 가져오기 (추천)
        // JwtAuthenticationToken token = (JwtAuthenticationToken) authentication;
        // return token.getToken().getClaim("userId");

        // 방법 2: Principal에서 가져오기
        // CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        // return userDetails.getUserId();

        // 임시: username을 파싱 (실제로는 위 방법 사용)
        try {
            // 실제 구현 필요!
            throw new UnsupportedOperationException(
                    "JWT에서 userId 추출 로직을 구현해야 합니다. " +
                            "authentication.getPrincipal() 또는 JWT claims 사용"
            );
        } catch (Exception e) {
            throw new RuntimeException("userId를 추출할 수 없습니다", e);
        }
    }*/
    @SuppressWarnings("unchecked")
    private Long extractUserIdFromAuth(Authentication auth) {
        if (auth == null) return null;

        Object principal = auth.getPrincipal();

        // 1) 기존처럼 CustomUserDetails에 userId가 있으면 그대로 사용
        try {
            if (principal != null && principal.getClass().getSimpleName().equals("CustomUserDetails")) {

                var m = principal.getClass().getMethod("getUserId");
                Object v = m.invoke(principal);
                Long id = toLong(v);
                if (id != null) return id;
            }
        } catch (Exception ignore) { /* 메서드 없거나 에러면 다음 단계 */ }

        // 2) principal이 클레임 맵이면: userId -> sub 순으로 시도
        if (principal instanceof java.util.Map<?, ?> map) {
            Object uid = map.get("userId");
            Long id = toLong(uid);
            if (id != null) return id;

            // Cognito 기본 식별자 (UUID 문자열)
            Object sub = map.get("sub");
            // TODO: sub -> 내부 userId 매핑이 있다면 여기서 변환
            // 현재는 최소 수정 원칙으로 null 반환 (아래 단계로 폴백)
        }

        // 3) name이 숫자면 그대로 사용 (기존 username이 숫자인 경우 대비)
        String name = auth.getName();
        if (name != null && name.chars().allMatch(Character::isDigit)) {
            try {
                return Long.valueOf(name);
            } catch (NumberFormatException ignore) {
            }
        }

        // 4) 없음
        return null;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}