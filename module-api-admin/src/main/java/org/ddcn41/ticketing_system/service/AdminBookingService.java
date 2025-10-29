package org.ddcn41.ticketing_system.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.client.BookingClient;
import org.ddcn41.ticketing_system.common.dto.booking.GetBookingDetail200ResponseDto;
import org.ddcn41.ticketing_system.common.dto.booking.GetBookings200ResponseDto;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminBookingService {

    private final BookingClient bookingClient;

    public GetBookings200ResponseDto getBookings(String status, Integer page, Integer limit) {
        return bookingClient.getBookings(status, page, limit);
    }

    public GetBookingDetail200ResponseDto getBookingDetailById(Long bookingId) {
        return bookingClient.getBookingDetailById(bookingId);
    }
}
