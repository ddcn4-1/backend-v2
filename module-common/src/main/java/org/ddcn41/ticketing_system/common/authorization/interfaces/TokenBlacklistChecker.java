package org.ddcn41.ticketing_system.common.authorization.interfaces;

public interface TokenBlacklistChecker {
    boolean isTokenBlacklisted(String token);

    void addToBlacklist(String token);
    void addToBlacklist(String token, long expirationTime);
}
