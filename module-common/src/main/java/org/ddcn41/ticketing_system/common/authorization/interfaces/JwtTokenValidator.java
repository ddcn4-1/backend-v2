package org.ddcn41.ticketing_system.common.authorization.interfaces;

public interface JwtTokenValidator {
    String extractUsername(String token);
    boolean validateToken(String token, String username);
}
