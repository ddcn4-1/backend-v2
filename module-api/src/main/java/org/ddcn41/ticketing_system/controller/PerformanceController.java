package org.ddcn41.ticketing_system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.dto.performance.response.PerformanceResponse;
import org.ddcn41.ticketing_system.common.dto.performance.response.PerformanceSchedulesResponse;
import org.ddcn41.ticketing_system.performance.service.PerformanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Performances", description = "APIs for performance")
@RestController
@RequestMapping("/v1/performances")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @Operation(summary = "모든 공연 조회", description = "클라이언트 화면에서 공연 전체 조회 시 사용")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success get all performances", content = @Content(schema = @Schema(implementation = PerformanceResponse.class), mediaType = "application/json"))
    })
    @GetMapping
    public ResponseEntity<List<PerformanceResponse>> getAllPerformance() {
        List<PerformanceResponse> responses = performanceService.getAllPerformances();

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "특정 공연 조회", description = "performanceId를 통한 공연 상세 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success get a performance", content = @Content(schema = @Schema(implementation = PerformanceResponse.class), mediaType = "application/json"))
    })
    @GetMapping("/{performanceId}")
    public ResponseEntity<PerformanceResponse> getPerformanceById(@PathVariable long performanceId) {
        PerformanceResponse response = performanceService.getPerformanceById(performanceId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "공연 검색", description = "제목, 장소, status에 따른 공연 검색")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success get a performance", content = @Content(schema = @Schema(implementation = PerformanceResponse.class), mediaType = "application/json"))
    })
    @GetMapping("/search")
    public ResponseEntity<List<PerformanceResponse>> searchPerformances(
            @RequestParam(required = false, defaultValue = "") String name,
            @RequestParam(required = false, defaultValue = "") String venue,
            @RequestParam(required = false, defaultValue = "") String status) {

        List<PerformanceResponse> responses = performanceService.searchPerformances(name, venue, status);

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "공연 회차 목록",
            description = "특정 공연의 모든 회차 목록을 조회합니다. 각 회차의 상세 정보(일시, 좌석 현황, 상태)를 포함합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회차 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PerformanceSchedulesResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 공연 ID 또는 회차가 없는 공연",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping("/{performanceId}/schedules")
    public ResponseEntity<PerformanceSchedulesResponse> getPerformanceSchedules(
            @PathVariable long performanceId) {
        PerformanceSchedulesResponse response = performanceService.getPerformanceSchedulesResponse(performanceId);

        return ResponseEntity.ok(response);
    }
}
