package org.ddcn41.ticketing_system.common.service;

public interface TokenBlacklistChecker {
    boolean isTokenBlacklisted(String token);
}
