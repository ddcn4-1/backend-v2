package org.ddcn41.starter.authorization.model;

import io.jsonwebtoken.Claims;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

public class BasicCognitoUser implements UserDetails {
    private final String username;
    // 추가 getter 메서드들
    @Getter
    private final String email;
    @Getter
    private final String userId;
    private final List<String> groups;
    private final transient Map<String, Object> attributes;
    @Getter
    private final String token;
    private final transient Claims claims;

    // 기본 생성자 추가
    public BasicCognitoUser() {
        this.username = null;
        this.email = null;
        this.userId = null;
        this.groups = List.of();
        this.attributes = Map.of();
        this.token = null;
        this.claims = null;
    }

    // 주 생성자 (JWT Claims 사용)
    public BasicCognitoUser(Claims claims, String token) {
        this.claims = claims;
        this.token = token;
        this.userId = claims.getSubject();
        this.username = getClaimAsString(claims, "cognito:username", this.userId);
        this.email = getClaimAsString(claims, "email", null);
        this.groups = extractGroups(claims);
        this.attributes = new HashMap<>(claims);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return groups.stream()
                .map(group -> new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                .toList();
    }

    @Override
    public String getPassword() {
        return null; // JWT 인증에서는 패스워드가 필요없음
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        if (claims != null) {
            Date expiration = claims.getExpiration();
            return expiration == null || expiration.after(new Date());
        }
        return true;
    }

    public List<String> getGroups() {
        return new ArrayList<>(groups);
    }

    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public String getAttributeAsString(String key) {
        Object value = getAttribute(key);
        return value != null ? value.toString() : null;
    }

    // JWT Claims에서 특정 필드 추출을 위한 헬퍼 메서드들
    public String getGivenName() {
        return getClaimAsString(claims, "given_name", null);
    }

    public String getFamilyName() {
        return getClaimAsString(claims, "family_name", null);
    }

    public String getPhoneNumber() {
        return getClaimAsString(claims, "phone_number", null);
    }

    public Boolean isEmailVerified() {
        if (claims != null) {
            Object emailVerified = claims.get("email_verified");
            if (emailVerified instanceof Boolean emailboolen) {
                return emailboolen;
            } else if (emailVerified instanceof String emailboolen) {
                return "true".equalsIgnoreCase(emailboolen);
            }
        }
        return false;
    }

    // 헬퍼 메서드들
    private String getClaimAsString(Claims claims, String key, String defaultValue) {
        if (claims == null) return defaultValue;
        Object value = claims.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private List<String> extractGroups(Claims claims) {
        if (claims == null) return List.of();

        // Cognito에서 그룹 정보는 'cognito:groups' 클레임에 배열로 저장됨
        Object groupsObj = claims.get("cognito:groups");
        if (groupsObj instanceof List) {
            try {
                return ((List<?>) groupsObj).stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toList();
            } catch (Exception e) {
                // 타입 캐스팅 실패시 빈 리스트 반환
                return List.of();
            }
        }
        return List.of();
    }

    @Override
    public String toString() {
        return "BasicCognitoUser{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", userId='" + userId + '\'' +
                ", groups=" + groups +
                ", emailVerified=" + isEmailVerified() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasicCognitoUser that = (BasicCognitoUser) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
