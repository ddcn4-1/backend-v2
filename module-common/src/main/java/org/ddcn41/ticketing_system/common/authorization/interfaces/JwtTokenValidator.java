package org.ddcn41.ticketing_system.common.authorization.interfaces;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Deprecated(forRemoval = true)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public interface JwtTokenValidator {
    String extractUsername(String token);
    boolean validateToken(String token, String username);
}
