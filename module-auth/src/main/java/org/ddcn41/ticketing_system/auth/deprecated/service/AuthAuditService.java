package org.ddcn41.ticketing_system.auth.deprecated.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.metric.dto.AuditLogDto;
import org.ddcn41.ticketing_system.metric.service.AuditEventService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthAuditService {
    private static final String DETAILS = "details";

    private final AuditEventService auditEventService;

    // 로그인 성공 로그
    public void logLoginSuccess(String username) {
        Map<String, Object> data = new HashMap<>();

        data.put(DETAILS, "Successful login");

        AuditLogDto auditLogDto = AuditLogDto.builder()
                .principal(username)
                .type("LOGIN_SUCCESS")
                .data(data)
                .build();

        auditEventService.addAuditEvent(auditLogDto);
    }

    // 로그인 실패 로그
    public void logLoginFailure(String username, String errorMessage) {
        Map<String, Object> data = new HashMap<>();

        data.put(DETAILS, "Login failed: " + errorMessage);

        AuditLogDto auditLogDto = AuditLogDto.builder()
                .principal(username)
                .type("LOGIN_FAILURE")
                .data(data)
                .build();

        auditEventService.addAuditEvent(auditLogDto);
    }

    // 로그아웃 로그
    public void logLogout(String username) {
        Map<String, Object> data = new HashMap<>();

        data.put(DETAILS, "User logged out");

        AuditLogDto auditLogDto = AuditLogDto.builder()
                .principal(username)
                .type("LOGOUT")
                .data(data)
                .build();

        auditEventService.addAuditEvent(auditLogDto);
    }

    // 로그인, 로그아웃 관련 이벤트 전체 조회
    public List<AuditLogDto> getAllAuthEvents() {
        return auditEventService.getAllAuditEvents()
                .stream()
                .filter(event -> {
                    String type = event.getType();
                    return "LOGIN_SUCCESS".equals(type) ||
                            "LOGIN_FAILURE".equals(type) ||
                            "LOGOUT".equals(type);
                })
                .toList();
    }

    // 최근 활동 조회
    public List<AuditLogDto> getRecentAuthEvents(int limit) {
        return getAllAuthEvents().stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .toList();
    }
}
