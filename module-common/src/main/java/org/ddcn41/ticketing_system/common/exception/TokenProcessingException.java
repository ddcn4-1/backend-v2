package org.ddcn41.ticketing_system.common.exception;

import lombok.Getter;

@Getter
public class TokenProcessingException extends RuntimeException {
    private final transient Object data;

    public TokenProcessingException(String message) {
        super(message);
        this.data = null;
    }

    public TokenProcessingException(String message, Object data) {
        super(message);
        this.data = data;
    }

    public TokenProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.data = null;
    }
}