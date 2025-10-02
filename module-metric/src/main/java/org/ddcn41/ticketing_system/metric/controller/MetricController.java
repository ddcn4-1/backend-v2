package org.ddcn41.ticketing_system.metric.controller;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.metric.dto.AuditLogDto;
import org.ddcn41.ticketing_system.metric.service.AuditEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/metrics")
public class MetricController {
    private final AuditEventService auditEventService;

    @GetMapping("/auditevents")
    public ResponseEntity<List<AuditLogDto>> getAllMetrics() {
        return ResponseEntity.ok(auditEventService.getAllAuditEvents());
    }

    @PostMapping("/auditevents")
    public ResponseEntity<AuditLogDto> recordMetrics(@RequestBody AuditLogDto auditLogDto) {
        return ResponseEntity.ok(auditEventService.addAuditEvent(auditLogDto));
    }
}
