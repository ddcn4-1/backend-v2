package org.ddcn41.starter.authorization.validator;


import io.jsonwebtoken.*;
import org.ddcn41.starter.authorization.model.BasicCognitoUser;
import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.ddcn41.starter.authorization.properties.JwtProperties.Cognito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class CognitoJwtValidator {

    private static final Logger logger = LoggerFactory.getLogger(CognitoJwtValidator.class);

    private final JwtProperties jwtProperties;
    private final JwtParser jwtParser;

    public CognitoJwtValidator(JwtProperties jwtProperties, CognitoSigningKeyResolver signingKeyResolver) {
        this.jwtProperties = jwtProperties;

        // JJWT Parser 설정
        this.jwtParser = Jwts.parserBuilder()
                .setSigningKeyResolver(signingKeyResolver)
                .build();
    }

    /**
     * JWT 토큰 검증 및 사용자 정보 생성
     */
    public BasicCognitoUser validateTokenAndCreateUser(String token) throws JwtException {
        Claims claims = validateToken(token);
        return new BasicCognitoUser(claims, token);
    }

    /**
     * JWT 토큰 검증
     */
    public Claims validateToken(String token) throws JwtException {
        try {
            Jws<Claims> claimsJws = jwtParser.parseClaimsJws(token);
            Claims claims = claimsJws.getBody();

            // 추가 검증 로직
            validateClaims(claims);

            logger.debug("JWT validation successful for user: {}", claims.getSubject());
            return claims;

        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired for user: {} at {}", e.getClaims().getSubject(), e.getClaims().getExpiration());
            throw e;
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported JWT token: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            logger.warn("Malformed JWT token: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
            throw new JwtException("Invalid JWT signature", e);
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during JWT validation: {}", e.getMessage(), e);
            throw new JwtException("JWT validation failed", e);
        }
    }

    /**
     * 토큰에서 사용자 ID 추출 (검증 없이)
     */
    public String extractUserId(String token) {
        try {
            // 검증 없이 claims만 파싱 (서명 검증 제외)
            int i = token.lastIndexOf('.');
            String withoutSignature = token.substring(0, i + 1);

            Jwt<Header, Claims> untrusted = Jwts.parserBuilder().build().parseClaimsJwt(withoutSignature);
            return untrusted.getBody().getSubject();

        } catch (Exception e) {
            logger.warn("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰 만료 시간 확인 (검증 없이)
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            logger.debug("Token validation failed during expiration check: {}", e.getMessage());
            return true; // 검증 실패시 만료된 것으로 간주
        }
    }

    /**
     * 토큰 만료 시간 반환
     */
    public Date getTokenExpiration(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration();
        } catch (Exception e) {
            logger.debug("Failed to get token expiration: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Claims 추가 검증
     */
    private void validateClaims(Claims claims) {
        Cognito cognito = getCognito(claims);

        // token_use 검증 (id token인지 확인)
        if (cognito.isValidateTokenUse()) {
            String tokenUse = (String) claims.get("token_use");
            if (!"id".equals(tokenUse)) {
                throw new JwtException("Invalid token use. Expected: id, Actual: " + tokenUse);
            }
        }

        // 추가 필수 클레임 검증
        if (claims.getSubject() == null || claims.getSubject().trim().isEmpty()) {
            throw new JwtException("Token must contain a valid subject (sub)");
        }

        if (claims.getIssuedAt() == null) {
            throw new JwtException("Token must contain issued at (iat) claim");
        }

        // 토큰이 미래에 발행된 것이 아닌지 확인 (5분 허용 오차)
        Date now = new Date();
        Date issuedAt = claims.getIssuedAt();
        long clockSkewMillis = 300000; // 5분

        if (issuedAt.getTime() > now.getTime() + clockSkewMillis) {
            throw new JwtException("Token used before issued time");
        }
    }

    private Cognito getCognito(Claims claims) {
        Cognito cognito = jwtProperties.getCognito();

        // issuer 검증
        if (cognito.isValidateIssuer()) {
            String expectedIssuer = cognito.getExpectedIssuer();
            String actualIssuer = claims.getIssuer();

            if (!expectedIssuer.equals(actualIssuer)) {
                throw new JwtException("Invalid issuer. Expected: " + expectedIssuer + ", Actual: " + actualIssuer);
            }
        }

        // audience 검증 (client_id)
        if (cognito.isValidateAudience()) {
            String expectedAudience = cognito.getClientId();
            String actualAudience = claims.getAudience();

            if (!expectedAudience.equals(actualAudience)) {
                throw new JwtException("Invalid audience. Expected: " + expectedAudience + ", Actual: " + actualAudience);
            }
        }
        return cognito;
    }
}
