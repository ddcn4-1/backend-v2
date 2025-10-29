// module-auth/src/main/java/org/ddcn41/ticketing_system/auth/utils/JwtUtil.java

package org.ddcn41.ticketing_system.common.authorization.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.ddcn41.ticketing_system.common.authorization.interfaces.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Deprecated(forRemoval = true)
@Component
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class JwtUtil implements JwtTokenValidator {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-validity-ms}")
    private long accessTokenValidityMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ⭐ 이 메서드는 사용하지 말 것!
    @Deprecated
    public String generate(String username) {
        // 하위 호환성을 위해 남겨둠
        return generate(username, null);
    }

    // ⭐ 이 메서드를 항상 사용!
    public String generate(String username, String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValidityMs);

        var builder = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate);

        if (userId != null) {
            builder.claim("userId", userId);
        } else {
            throw new IllegalArgumentException("userId는 필수입니다!");
        }

        return builder.signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj != null) {
            // Integer나 Long 모두 처리
            if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            }
            return Long.valueOf(userIdObj.toString());
        }
        return null;
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    public boolean validateToken(String token, String username) {
        return (username.equals(extractUsername(token)) && !isTokenExpired(token));
    }
}