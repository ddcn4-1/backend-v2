package org.ddcn41.ticketing_system.auth.deprecated.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.ticketing_system.auth.deprecated.dto.AuthDtos;
import org.ddcn41.ticketing_system.auth.deprecated.dto.AuthDtos.EnhancedAuthResponse;
import org.ddcn41.ticketing_system.auth.deprecated.dto.AuthDtos.LoginRequest;
import org.ddcn41.ticketing_system.auth.deprecated.dto.response.LogoutResponse;
import org.ddcn41.ticketing_system.auth.deprecated.service.AuthAuditService;
import org.ddcn41.ticketing_system.auth.deprecated.service.AuthService;
import org.ddcn41.ticketing_system.auth.deprecated.utils.TokenExtractor;
import org.ddcn41.ticketing_system.common.authorization.service.TokenBlacklistService;
import org.ddcn41.ticketing_system.common.authorization.util.JwtUtil;
import org.ddcn41.ticketing_system.common.config.CognitoProperties;
import org.ddcn41.ticketing_system.user.entity.User;
import org.ddcn41.ticketing_system.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("v1/auth")
@Tag(name = "Authentication", description = "사용자 인증 API")
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
@Deprecated
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final AuthService authService;
    private final TokenExtractor tokenExtractor;
    private final AuthAuditService authAuditService;
    @Autowired
    private CognitoProperties cognitoProperties;
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, UserService userService, AuthService authService, TokenExtractor tokenExtractor, AuthAuditService authAuditService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.authService = authService;
        this.tokenExtractor = tokenExtractor;
        this.authAuditService = authAuditService;
    }

    /**
     * 일반 사용자 로그인 (관리자도 이 엔드포인트 사용 가능하지만 /admin/auth/login 권장)
     */
    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticates a user. Returns JWT token for API access."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthDtos.AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request format"
            )
    })
    public ResponseEntity<EnhancedAuthResponse> login(@Valid @RequestBody LoginRequest dto) {

        if (cognitoProperties.isEnabled()) {
            return ResponseEntity.status(503).body(
                    EnhancedAuthResponse.failure("Cognito 인증이 활성화. 프론트->람다를 통해 로그인하세요.")
            );
        }


        try {
            String actualUsername = userService.resolveUsernameFromEmailOrUsername(dto.getUsernameOrEmail());

            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(actualUsername, dto.getPassword())
            );


            // userId 포함하여 JWT 생성
            // user 변수를 먼저 선언
            User user = userService.updateUserLoginTime(actualUsername);

            String token = jwtUtil.generate(auth.getName(), user.getUserId());

            authAuditService.logLoginSuccess(actualUsername);


            authAuditService.logLoginSuccess(actualUsername);

            EnhancedAuthResponse response = EnhancedAuthResponse.success(token, user);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            String actualUsername;
            try {
                actualUsername = userService.resolveUsernameFromEmailOrUsername(dto.getUsernameOrEmail());
            } catch (Exception ex) {
                actualUsername = dto.getUsernameOrEmail();
            }

            authAuditService.logLoginFailure(actualUsername, e.getMessage());

            AuthDtos.EnhancedAuthResponse errorResponse = EnhancedAuthResponse.failure("로그인 실패: " + e.getMessage());
            return ResponseEntity.status(401).body(errorResponse);
        }
    }

//    @PostMapping("/logout")
//    @Operation(
//            summary = "User logout",
//            description = "Logs out the authenticated user and invalidates the JWT token."
//    )
//    @SecurityRequirement(name = "bearerAuth")
//    @ApiResponses(value = {
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "200",
//                    description = "Logout successful"
//            ),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "401",
//                    description = "Unauthorized - invalid or missing token"
//            ),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "400",
//                    description = "Token processing error"
//            )
//    })
//    public ResponseEntity<org.ddcn41.ticketing_system.common.dto.ApiResponse<LogoutResponse>> logout(
//            HttpServletRequest request,
//            Authentication authentication) {
//
//        String username = authentication != null ? authentication.getName() : "anonymous";
//
//        String token = tokenExtractor.extractTokenFromRequest(request);
//
//        LogoutResponse logoutData = authService.processLogout(token, username);
//        org.ddcn41.ticketing_system.common.dto.ApiResponse<LogoutResponse> response = org.ddcn41.ticketing_system.common.dto.ApiResponse.success("로그아웃 완료", logoutData);
//
//        authAuditService.logLogout(username);
//
//        return ResponseEntity.ok(response);
//    }


    @PostMapping("/logout")
    @Operation(
            summary = "Backend token invalidation",
            description = "백엔드에서 토큰을 무효화. Cognito 로그아웃은 프론트엔드에서 Lambda를 호출 중."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token invalidated"),
            @ApiResponse(responseCode = "401", description = "No valid token found")
    })
    public ResponseEntity<org.ddcn41.ticketing_system.common.dto.ApiResponse<LogoutResponse>> logout(
            HttpServletRequest request,
            Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "anonymous";
        boolean tokenInvalidated = false;

        try {
            // 1. Cognito 쿠키 토큰 무효화
            if (cognitoProperties.isEnabled()) {
                String cognitoToken = extractCognitoTokenFromCookies(request);
                if (cognitoToken != null) {
                    tokenBlacklistService.addToBlacklist(cognitoToken);
                    tokenInvalidated = true;
                    log.info("Cognito token invalidated for user: {}", username);
                }
            }

            // 2. 기존 JWT 토큰 무효화
            if (!tokenInvalidated) {  // Cognito 토큰이 없을 때만 JWT 확인
                String jwtToken = tokenExtractor.extractTokenFromRequest(request);
                if (jwtToken != null) {
                    authService.processLogout(jwtToken, username);  // 반환값 사용 안 함 (블랙리스트만 필요)
                    tokenInvalidated = true;
                    log.info("JWT token invalidated for user: {}", username);
                }
            }

            // 3. 응답 생성
            String message = tokenInvalidated ?
                    "백엔드 토큰 무효화 완료." :
                    "무효화할 토큰이 없습니다.";

            LogoutResponse logoutData = LogoutResponse.builder()
                    .message(message)
                    .username(username)
                    .cognitoLogoutUrl(null)  // 백엔드에서는 Cognito URL 제공하지 않음
                    .build();

            if (tokenInvalidated) {
                authAuditService.logLogout(username);
            }

            return ResponseEntity.ok(
                    org.ddcn41.ticketing_system.common.dto.ApiResponse.success(message, logoutData)
            );

        } catch (Exception e) {
            log.error("Token invalidation failed for user: {}", username, e);

            return ResponseEntity.status(500).body(
                    org.ddcn41.ticketing_system.common.dto.ApiResponse.error("토큰 무효화 실패")
            );
        }
    }

    private String extractCognitoTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

}
