package org.ddcn41.ticketing_system.booking.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.booking.entity.Booking;
import org.ddcn41.ticketing_system.metric.dto.AuditLogDto;
import org.ddcn41.ticketing_system.metric.service.AuditEventService;
import org.ddcn41.ticketing_system.user.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingAuditService {

    private static final String SYSTEM_PRINCIPAL = "system";

    private final AuditEventService auditEventService;

    public void logBookingCreated(User user, Booking booking, List<Long> seatIds) {
        Map<String, Object> data = new HashMap<>();

        data.put("bookingId", booking.getBookingId());
        data.put("scheduleId", booking.getSchedule() != null ? booking.getSchedule().getScheduleId() : null);
        data.put("seatCount", booking.getSeatCount());
        data.put("totalAmount", toPlainAmount(booking.getTotalAmount()));
        data.put("seatIds", seatIds);

        AuditLogDto auditLogDto = AuditLogDto.builder()
                .principal(resolvePrincipal(user))
                .type("BOOKING_CREATED")
                .data(data)
                .build();

        auditEventService.addAuditEvent(auditLogDto);
    }

    public void logBookingCancelled(String actorUsername, Booking booking, String reason) {
        Map<String, Object> data = new HashMap<>();

        data.put("bookingId", booking.getBookingId());
        data.put("scheduleId", booking.getSchedule() != null ? booking.getSchedule().getScheduleId() : null);
        data.put("refundAmount", toPlainAmount(booking.getTotalAmount()));
        data.put("reason", reason);

        AuditLogDto auditLogDto = AuditLogDto.builder()
                .principal(actorUsername != null ? actorUsername : "userId: " + booking.getUserId())
                .type("BOOKING_CANCELLED")
                .data(data)
                .build();

        auditEventService.addAuditEvent(auditLogDto);
    }

    private String resolvePrincipal(User user) {
        if (user != null && user.getUsername() != null) {
            return user.getUsername();
        }
        return SYSTEM_PRINCIPAL;
    }

    private String toPlainAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.stripTrailingZeros().toPlainString();
    }
}
