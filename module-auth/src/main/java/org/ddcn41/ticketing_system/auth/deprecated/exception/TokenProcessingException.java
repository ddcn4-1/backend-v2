package org.ddcn41.ticketing_system.auth.deprecated.exception;

public class TokenProcessingException extends RuntimeException {
    public TokenProcessingException(String message) {
        super(message);
    }

    public TokenProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}