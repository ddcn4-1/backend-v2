package org.ddcn41.ticketing_system.common.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeatDto {
    private Long bookingSeatId;
    private Long bookingId;
    private Long seatId;
    private Double seatPrice;
    private String grade;
    private String zone;
    private String rowLabel;
    private String colNum;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime createdAt;
}
