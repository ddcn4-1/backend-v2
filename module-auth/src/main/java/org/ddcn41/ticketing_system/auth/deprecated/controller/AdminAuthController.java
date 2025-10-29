package org.ddcn41.ticketing_system.auth.deprecated.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.ddcn41.ticketing_system.auth.deprecated.dto.AuthDtos;
import org.ddcn41.ticketing_system.auth.deprecated.dto.AuthDtos.EnhancedAuthResponse;
import org.ddcn41.ticketing_system.auth.deprecated.dto.AuthDtos.LoginRequest;
import org.ddcn41.ticketing_system.auth.deprecated.service.AuthAuditService;
import org.ddcn41.ticketing_system.common.authorization.util.JwtUtil;
import org.ddcn41.ticketing_system.user.entity.User;
import org.ddcn41.ticketing_system.user.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("v1/admin/auth")
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
@Deprecated
@Tag(name = "Admin Authentication", description = "APIs for administrator authentication")
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final AuthAuditService authAuditService;

    public AdminAuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                               UserService userService, AuthAuditService authAuditService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.authAuditService = authAuditService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Admin login",
            description = "Authenticates an administrator. Only users with ADMIN role can login through this endpoint."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Admin login successful",
                    content = @Content(
                            schema = @Schema(implementation = AuthDtos.AuthResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\"userType\":\"ADMIN\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "{\"error\":\"Unauthorized\",\"message\":\"관리자 로그인 실패: ...\",\"timestamp\":\"...\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User does not have admin privileges",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "{\"error\":\"Forbidden\",\"message\":\"관리자 권한이 필요합니다\",\"timestamp\":\"...\"}"
                            )
                    )
            )
    })
    public ResponseEntity<EnhancedAuthResponse> adminLogin(@Valid @RequestBody LoginRequest dto) {
        try {
            String actualUsername = userService.resolveUsernameFromEmailOrUsername(dto.getUsernameOrEmail());
            User user = userService.findByUsername(actualUsername);

            if (!User.Role.ADMIN.equals(user.getRole())) {
                authAuditService.logLoginFailure(actualUsername, "관리자 권한 없음");
                EnhancedAuthResponse errorResponse = EnhancedAuthResponse.failure("관리자 권한이 필요합니다");
                return ResponseEntity.status(403).body(errorResponse);
            }

            // 인증 수행
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(actualUsername, dto.getPassword())
            );

            user = userService.updateUserLoginTime(actualUsername);

            String token = jwtUtil.generate(auth.getName(), user.getUserId());

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

            // 로그인 실패 로그
            authAuditService.logLoginFailure(actualUsername, e.getMessage());

            EnhancedAuthResponse errorResponse = EnhancedAuthResponse.failure("관리자 로그인 실패: " + e.getMessage());
            return ResponseEntity.status(401).body(errorResponse);
        }
    }

    /**
     * 관리자 로그아웃
     * 인증된 관리자만 접근 가능
     */
    @PostMapping("/logout")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminLogout(HttpServletRequest request, Authentication authentication) {
        String adminUsername = authentication.getName();

        String authHeader = request.getHeader("Authorization");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("message", "관리자 로그아웃 완료");
        responseMap.put("admin", adminUsername);
        responseMap.put("timestamp", LocalDateTime.now());
        responseMap.put("success", true);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                long expirationTime = jwtUtil.extractClaims(token).getExpiration().getTime();
                long timeLeft = (expirationTime - System.currentTimeMillis()) / 1000 / 60;
                responseMap.put("tokenTimeLeft", timeLeft + "분");
            } catch (Exception e) {
                responseMap.put("tokenError", "토큰 처리 중 오류 발생");
            }
        }

        authAuditService.logLogout(adminUsername);
        return ResponseEntity.ok(responseMap);
    }
}