package org.ddcn41.starter.authorization.model;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

public class BasicCognitoUser implements UserDetails {
    private final String username;
    private final String email;
    private final String userId;
    private final List<String> groups;
    private final Map<String, Object> attributes;
    private final String token;
    private final Claims claims;

    public BasicCognitoUser(Claims claims, String token) {
        this.claims = claims;
        this.token = token;
        this.userId = claims.getSubject();
        this.username = getClaimAsString(claims, "cognito:username", this.userId);
        this.email = getClaimAsString(claims, "email", null);
        this.groups = extractGroups(claims);
        this.attributes = new HashMap<>(claims);
    }

    // 생성자 오버로드 (기존 호환성 유지)
    public BasicCognitoUser(String username, String email, String userId,
                            List<String> groups, Map<String, Object> attributes, String token) {
        this.username = username;
        this.email = email;
        this.userId = userId;
        this.groups = groups != null ? groups : List.of();
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
        this.token = token;
        this.claims = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return groups.stream()
                .map(group -> new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                .collect(Collectors.toList());
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
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        if (claims != null) {
            Date expiration = claims.getExpiration();
            return expiration == null || expiration.after(new Date());
        }
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // 추가 getter 메서드들
    public String getEmail() {
        return email;
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getGroups() {
        return new ArrayList<>(groups);
    }

    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    public String getToken() {
        return token;
    }

    public Claims getClaims() {
        return claims;
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
            if (emailVerified instanceof Boolean) {
                return (Boolean) emailVerified;
            } else if (emailVerified instanceof String) {
                return "true".equalsIgnoreCase((String) emailVerified);
            }
        }
        return null;
    }

    // 헬퍼 메서드들
    private String getClaimAsString(Claims claims, String key, String defaultValue) {
        if (claims == null) return defaultValue;
        Object value = claims.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractGroups(Claims claims) {
        if (claims == null) return List.of();

        // Cognito에서 그룹 정보는 'cognito:groups' 클레임에 배열로 저장됨
        Object groupsObj = claims.get("cognito:groups");
        if (groupsObj instanceof List) {
            try {
                return ((List<?>) groupsObj).stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.toList());
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
