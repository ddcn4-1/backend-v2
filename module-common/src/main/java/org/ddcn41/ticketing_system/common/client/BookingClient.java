package org.ddcn41.ticketing_system.common.client;

import org.ddcn41.ticketing_system.common.dto.booking.GetBookingDetail200ResponseDto;
import org.ddcn41.ticketing_system.common.dto.booking.GetBookings200ResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "booking-service", url = "${booking.service.url:http://localhost:8082}")
public interface BookingClient {
    @GetMapping("/v1/internal/bookings")
    GetBookings200ResponseDto getBookings(@RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                          @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit);

    @GetMapping("/v1/internal/bookings/{bookingId}")
    GetBookingDetail200ResponseDto getBookingDetailById(@PathVariable("bookingId") Long bookingId);
}
