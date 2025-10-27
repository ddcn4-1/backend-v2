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
import org.ddcn41.ticketing_system.common.dto.seat.InitializeSeatsResponse;
import org.ddcn41.ticketing_system.seat.service.ScheduleSeatInitializationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/internal/seat")
@Tag(name = "Admin Schedules internal", description = "Admin APIs for schedule seat generation")
public class InternalSeatController {
    private final ScheduleSeatInitializationService scheduleSeatInitializationService;

    @PostMapping("/initialize")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Initialize seats for ALL schedules",
            description = "Runs the seat expansion for every schedule in performance_schedules."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = InitializeSeatsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    })
    public ResponseEntity<org.ddcn41.ticketing_system.common.dto.ApiResponse<List<InitializeSeatsResponse>>> initializeAllSchedules(
            @Parameter(description = "Dry run without persisting", required = false)
            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun
    ) {
        List<InitializeSeatsResponse> results = scheduleSeatInitializationService.initializeAll(dryRun);
        return ResponseEntity.ok(
                org.ddcn41.ticketing_system.common.dto.ApiResponse
                        .success(dryRun ? "모든 스케줄 좌석 초기화 미리보기" : "모든 스케줄 좌석 초기화 완료", results)
        );
    }

    @PostMapping("/initialize/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Initialize one schedule's seats", description = "Use POST /v1/admin/schedules/initialize to initialize all schedules.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = InitializeSeatsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content)
    })
    public ResponseEntity<org.ddcn41.ticketing_system.common.dto.ApiResponse<InitializeSeatsResponse>> initializeScheduleSeats(
            @Parameter(description = "Schedule ID", required = true)
            @PathVariable Long scheduleId,
            @Parameter(description = "Dry run without persisting", required = false)
            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun
    ) {
        InitializeSeatsResponse result = scheduleSeatInitializationService.initialize(scheduleId, dryRun);
        return ResponseEntity.ok(
                org.ddcn41.ticketing_system.common.dto.ApiResponse
                        .success(dryRun ? "좌석 초기화 미리보기" : "좌석 초기화 완료", result)
        );
    }
}
