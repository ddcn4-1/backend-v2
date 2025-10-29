package org.ddcn41.ticketing_system.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.ticketing_system.booking.service.BookingService;
import org.ddcn41.ticketing_system.common.dto.booking.GetBookingDetail200ResponseDto;
import org.ddcn41.ticketing_system.common.dto.booking.GetBookings200ResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/internal/bookings")
@Tag(name = "Internal Bookings", description = "APIs for booking management")
public class InternalBookingController {

    private final BookingService bookingService;

    @GetMapping
    @Operation(summary = "List all bookings (internal)", description = "Lists all bookings filtered by status with pagination - Admin only")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = GetBookings200ResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Admin access required", content = @Content)
    })
    public ResponseEntity<GetBookings200ResponseDto> getAllBookings(
            @Parameter(description = "Filter by booking status (optional)")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Page number (1-based)", example = "1")
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Items per page", example = "20")
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(bookingService.getBookings(status, page, limit));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get any booking detail (internal)", description = "Fetches detailed information for any booking - Admin only")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = GetBookingDetail200ResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Admin access required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
    })
    public ResponseEntity<GetBookingDetail200ResponseDto> getBookingDetailById(
            @Parameter(description = "Booking ID", required = true)
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getBookingDetail(bookingId));
    }
}
