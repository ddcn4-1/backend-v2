package org.ddcn41.ticketing_system.common.handler;

import org.ddcn41.ticketing_system.common.exception.TokenProcessingException;
import org.ddcn41.ticketing_system.common.dto.ApiResponse;
import org.ddcn41.ticketing_system.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TokenProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handleTokenProcessingException(
            TokenProcessingException ex) {

        ApiResponse<Object> response = ApiResponse.error(
                "토큰 처리 중 오류 발생",
                ex.getMessage(),
                ex.getData()
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ResponseStatusException 처리
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(
            ResponseStatusException ex) {

        HttpStatus status = (HttpStatus) ex.getStatusCode();
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();

        ApiResponse<Object> response = ApiResponse.error(
                message,
                null,
                null
        );

        return ResponseEntity.status(status).body(response);
    }


    // BusinessException 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException ex) {

        HttpStatus status = ex.getErrorCode().getStatus();
        String message = ex.getMessage();
        String detailMessage = ex.getDetailMessage();

        ApiResponse<Object> response = ApiResponse.error(
                message,
                detailMessage,
                null
        );

        return ResponseEntity.status(status).body(response);
    }

    // 다른 예외들도 필요에 따라 추가
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        ApiResponse<Object> response = ApiResponse.error("서버 오류가 발생했습니다.", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}