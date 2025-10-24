package org.ddcn41.ticketing_system.admin.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.admin.dto.DashboardDto;
import org.ddcn41.ticketing_system.auth.deprecated.service.AuthAuditService;
import org.ddcn41.ticketing_system.metric.dto.AuditLogDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuthAuditService authAuditService;

    // 대시보드에 나타낼 값 가져오기
    public DashboardDto getDashboardData() {
        List<AuditLogDto> recentAuthLogs = authAuditService.getRecentAuthEvents(10);

        return DashboardDto.builder()
                .recentAuthLogs(recentAuthLogs)
                .build();
    }
}
