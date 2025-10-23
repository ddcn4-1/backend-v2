package org.ddcn41.ticketing_system.metric.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.metric.dto.AuditLogDto;
import org.ddcn41.ticketing_system.metric.util.AuditEventBuilder;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditEventService {
    private final AuditEventRepository auditEventRepository;

    public AuditLogDto addAuditEvent(AuditLogDto auditLogDto) {
        AuditEvent auditEvent = AuditEventBuilder.builder()
                .principal(auditLogDto.getPrincipal())
                .type(auditLogDto.getType())
                .data(auditLogDto.getData())
                .build();

        auditEventRepository.add(auditEvent);

        return auditLogDto;
    }

    public List<AuditLogDto> getAllAuditEvents() {
        return auditEventRepository.find(null, null, null)
                .stream()
                .map(this::convertToAuditLogDto)
                .collect(Collectors.toList());
    }

    private AuditLogDto convertToAuditLogDto(AuditEvent auditEvent) {

        return AuditLogDto.builder()
                .principal(auditEvent.getPrincipal())
                .type(auditEvent.getType())
                .timestamp(auditEvent.getTimestamp())
                .data(auditEvent.getData())
                .build();
    }
}
