package org.ddcn41.ticketing_system.performance.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.metric.dto.AuditLogDto;
import org.ddcn41.ticketing_system.metric.service.AuditEventService;
import org.ddcn41.ticketing_system.performance.repository.PerformanceScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerformanceScheduleStatusService {

    private final PerformanceScheduleRepository scheduleRepository;

    private final AuditEventService auditEventService;

    private static final String SYSTEM_PRINCIPAL = "system";

    @Transactional
    public int synchronizeAllStatuses() {
        int affected = scheduleRepository.refreshAllScheduleStatuses();
        logStatusSyncEvent("SCHEDULE_STATUS_SYNC", affected);
        return affected;
    }

    @Transactional
    public int closePastSchedules() {
        int closed = scheduleRepository.closePastSchedules();
        logStatusSyncEvent("SCHEDULE_CLOSE_AUTOMATION", closed);
        return closed;
    }

    private void logStatusSyncEvent(String type, int affected) {
        Map<String, Object> data = new HashMap<String, Object>();

        data.put("affected", affected);

        AuditLogDto auditLogDto = AuditLogDto.builder()
                .principal(SYSTEM_PRINCIPAL)
                .type(type)
                .data(data)
                .build();

        auditEventService.addAuditEvent(auditLogDto);
    }
}
