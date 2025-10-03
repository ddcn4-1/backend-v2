package org.ddcn41.ticketing_system.common.service;

public interface JwtTokenValidator {
    String extractUsername(String token);
    Long extractUserId(String token);
    boolean validateToken(String token, String username);
}
