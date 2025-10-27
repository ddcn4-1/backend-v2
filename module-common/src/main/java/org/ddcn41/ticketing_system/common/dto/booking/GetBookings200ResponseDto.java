package org.ddcn41.ticketing_system.common.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetBookings200ResponseDto {
    private List<BookingDto> bookings;
    private Integer total;
    private Integer page;
}
