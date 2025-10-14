// module-queue/src/main/java/.../security/JwtService.java
package org.ddcn41.queue.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public Long extractUserId(String token) {
        try {
            Claims claims = extractClaims(token);

            // 1. userId claim 확인
            Object userIdObj = claims.get("userId");
            if (userIdObj != null) {
                System.out.println("Found userId claim: " + userIdObj);
                return Long.valueOf(userIdObj.toString());
            }

            // 2. subject에서 추출 시도
            String subject = claims.getSubject();
            System.out.println("Subject: " + subject);

            // subject가 숫자면 userId
            try {
                return Long.valueOf(subject);
            } catch (NumberFormatException e) {
                System.err.println("Subject is not a number: " + subject);
            }

            // 3. 모든 claims 출력 (디버깅용)
            System.out.println("All claims: " + claims);

            return null;
        } catch (Exception e) {
            System.err.println("extractUserId error: " + e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            System.err.println("JWT validation failed: " + e.getMessage());
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}