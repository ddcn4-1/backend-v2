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
import org.ddcn41.ticketing_system.common.dto.performance.request.PerformanceRequest;
import org.ddcn41.ticketing_system.common.dto.performance.request.PresignedUrlRequest;
import org.ddcn41.ticketing_system.common.dto.performance.response.AdminPerformanceResponse;
import org.ddcn41.ticketing_system.common.dto.performance.response.PerformanceResponse;
import org.ddcn41.ticketing_system.common.dto.performance.response.PresignedUrlResponse;
import org.ddcn41.ticketing_system.performance.service.PerformanceService;
import org.ddcn41.ticketing_system.performance.service.S3Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "internal Performances", description = "APIs for performance")
@RestController
@RequestMapping("/v1/internal/performances")
@RequiredArgsConstructor
public class InternalPerformanceController {

    private final PerformanceService performanceService;
    private final S3Service s3Service;

    @Operation(summary = "모든 공연 조회", description = "어드민 화면에서 공연 전체 조회 시 사용")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success get all performances", content = @Content(schema = @Schema(implementation = AdminPerformanceResponse.class), mediaType = "application/json"))
    })
    @GetMapping
    public ResponseEntity<List<AdminPerformanceResponse>> getAllAdminPerformance() {
        List<AdminPerformanceResponse> responses = performanceService.getAllAdminPerformances();

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "공연 생성", description = "공연 대시보드에서 새로운 공연을 생성할 때 사용")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Performance created",
                    content = @Content(schema = @Schema(implementation = PerformanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Related resource not found", content = @Content)
    })
    @PostMapping
    public ResponseEntity<AdminPerformanceResponse> createPerformance(
            @Parameter(
                    description = "공연 정보 (JSON 형태)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PerformanceRequest.class)
                    )
            )
            @RequestBody PerformanceRequest createPerformanceRequest) {
        AdminPerformanceResponse adminPerformanceResponse = performanceService.createPerformance(createPerformanceRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPerformanceResponse);
    }

    @PutMapping("/{performanceId}")
    @Operation(
            summary = "공연 수정",
            description = "공연 대시보드에서 기존 공연을 수정할 때 사용"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "공연 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PerformanceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Related resource not found",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<AdminPerformanceResponse> updatePerformance(
            @Parameter(description = "Performance ID", required = true) @PathVariable long performanceId,
            @Parameter(
                    description = "공연 정보 (JSON 형태)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PerformanceRequest.class)
                    )
            )
            @RequestBody PerformanceRequest updatePerformanceRequest) {
        AdminPerformanceResponse adminPerformanceResponse = performanceService.updatePerformance(performanceId, updatePerformanceRequest);
        return ResponseEntity.ok(adminPerformanceResponse);
    }


    @DeleteMapping("/{performanceId}")
    @Operation(summary = "공연 삭제", description = "공연 대시보드에서 기존 공연을 삭제할 때 사용")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "공연 삭제", content = @Content),
            @ApiResponse(responseCode = "404", description = "Related resource not found", content = @Content)
    })
    public ResponseEntity<Object> deletePerformance(
            @Parameter(description = "Performance ID", required = true)
            @PathVariable long performanceId) {
        performanceService.deletePerformance(performanceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUrlResponse> getUploadPresignedUrl(@RequestBody PresignedUrlRequest presignedUrlRequest) {
        PresignedUrlResponse response = s3Service.getUploadImagePresignedURL(presignedUrlRequest);

        return ResponseEntity.ok(response);
    }
}
