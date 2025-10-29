// module-queue/src/main/java/org/ddcn41/queue/config/GlobalExceptionHandler.java

package org.ddcn41.queue.config;

import lombok.extern.slf4j.Slf4j;
import org.ddcn41.queue.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     *  ResponseStatusException 처리
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
            ResponseStatusException ex
    ) {
        log.warn("ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(
                        ex.getReason() != null ? ex.getReason() : "요청 처리 중 오류가 발생했습니다",
                        ex.getStatusCode().toString()
                ));
    }

    /**
     *  인증 실패 (401)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex
    ) {
        log.warn("Authentication failed: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        "인증이 필요합니다",
                        "AUTHENTICATION_FAILED"
                ));
    }

    /**
     *  권한 없음 (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex
    ) {
        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        "접근 권한이 없습니다",
                        "ACCESS_DENIED"
                ));
    }

    /**
     *  IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        "INVALID_ARGUMENT"
                ));
    }

    /**
     *  IllegalStateException 처리
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex
    ) {
        log.warn("IllegalStateException: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        "INVALID_STATE"
                ));
    }

    /**
     *  기타 모든 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unexpected error: ", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "서버 오류가 발생했습니다",
                        "INTERNAL_SERVER_ERROR"
                ));
    }
}