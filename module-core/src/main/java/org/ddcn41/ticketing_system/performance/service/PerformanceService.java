package org.ddcn41.ticketing_system.performance.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.booking.entity.Booking;
import org.ddcn41.ticketing_system.booking.repository.BookingRepository;
import org.ddcn41.ticketing_system.common.dto.performance.request.PerformanceRequest;
import org.ddcn41.ticketing_system.common.dto.performance.request.PerformanceScheduleRequest;
import org.ddcn41.ticketing_system.common.dto.performance.response.AdminPerformanceResponse;
import org.ddcn41.ticketing_system.common.dto.performance.response.PerformanceResponse;
import org.ddcn41.ticketing_system.common.dto.performance.response.PerformanceSchedulesResponse;
import org.ddcn41.ticketing_system.common.dto.performance.response.ScheduleResponse;
import org.ddcn41.ticketing_system.common.exception.BusinessException;
import org.ddcn41.ticketing_system.common.exception.ErrorCode;
import org.ddcn41.ticketing_system.performance.entity.Performance;
import org.ddcn41.ticketing_system.performance.entity.PerformanceSchedule;
import org.ddcn41.ticketing_system.performance.repository.PerformanceRepository;
import org.ddcn41.ticketing_system.performance.repository.PerformanceScheduleRepository;
import org.ddcn41.ticketing_system.seat.service.ScheduleSeatInitializationService;
import org.ddcn41.ticketing_system.venue.entity.Venue;
import org.ddcn41.ticketing_system.venue.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class PerformanceService {
    private final PerformanceRepository performanceRepository;
    private final PerformanceScheduleRepository performanceScheduleRepository;

    private final BookingRepository bookingRepository;
    private final VenueRepository venueRepository;
    private final S3Service s3ImageService;

    private final ScheduleSeatInitializationService initializationService;

    public PerformanceResponse getPerformanceById(Long performanceId) {
        return convertToPerformanceResponse(performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND, "performanceId: " + performanceId)));
    }

    public List<PerformanceResponse> getAllPerformances() {
        return performanceRepository.findAllWithVenueAndSchedules()
                .stream()
                .map(this::convertToPerformanceResponse)
                .toList();
    }

    public List<AdminPerformanceResponse> getAllAdminPerformances() {
        return performanceRepository.findAllWithVenueAndSchedules()
                .stream()
                .map(this::convertToAdminPerformanceResponse)
                .toList();
    }

    public List<PerformanceResponse> searchPerformances(String name, String venue, String status) {
        Performance.PerformanceStatus performanceStatus = null;

        // status 문자열을 enum으로 변환
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("all")) {
            try {
                performanceStatus = Performance.PerformanceStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 잘못된 status 값인 경우 null로 처리 (모든 상태 조회)
            }
        }

        return performanceRepository.searchPerformances(
                name != null && !name.trim().isEmpty() ? name : null,
                venue != null && !venue.trim().isEmpty() ? venue : null,
                performanceStatus
        ).stream().map(this::convertToPerformanceResponse).toList();
    }

    public PerformanceSchedulesResponse getPerformanceSchedulesResponse(Long performanceId) {
        performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND, "performanceId: " + performanceId));

        return PerformanceSchedulesResponse.builder().schedules(performanceScheduleRepository.findByPerformance_PerformanceIdOrderByShowDatetimeAsc(performanceId).stream()
                        .map(this::toScheduleResponse)
                        .toList())
                .build();
    }

    public AdminPerformanceResponse createPerformance(PerformanceRequest createPerformanceRequest) {
        Venue venue = venueRepository.findById(createPerformanceRequest.getVenueId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND, "venueId: " + createPerformanceRequest.getVenueId()));

        Performance performance = Performance.builder()
                .venue(venue)
                .title(createPerformanceRequest.getTitle())
                .description(createPerformanceRequest.getDescription())
                .theme(createPerformanceRequest.getTheme())
                .posterUrl(createPerformanceRequest.getPosterUrl())
                .startDate(createPerformanceRequest.getStartDate())
                .endDate(createPerformanceRequest.getEndDate())
                .runningTime(createPerformanceRequest.getRunningTime())
                .basePrice(createPerformanceRequest.getBasePrice())
                .status(Performance.PerformanceStatus.valueOf(createPerformanceRequest.getStatus()))
                .build();

        if (createPerformanceRequest.getSchedules() != null && !createPerformanceRequest.getSchedules().isEmpty()) {
            // 각 스케줄에 performance 설정
            List<PerformanceSchedule> performanceSchedules = new ArrayList<>();

            for (PerformanceScheduleRequest scheduleRequest : createPerformanceRequest.getSchedules()) {
                PerformanceSchedule schedule = PerformanceSchedule.builder()
                        .showDatetime(LocalDateTime.parse(scheduleRequest.getShowDatetime()))
                        .totalSeats(scheduleRequest.getTotalSeats())
                        .status(PerformanceSchedule.ScheduleStatus.valueOf(scheduleRequest.getStatus()))
                        .bookingStartAt(scheduleRequest.getBookingStartAt())
                        .bookingEndAt(scheduleRequest.getBookingEndAt())
                        .build();

                schedule.setPerformance(performance);
                performanceSchedules.add(schedule);
            }

            performance.setSchedules(performanceSchedules);
        }

        Performance savedPerformance = performanceRepository.save(performance);

        if (savedPerformance.getSchedules() != null && !savedPerformance.getSchedules().isEmpty()) {
            for (PerformanceSchedule schedule : savedPerformance.getSchedules()) {
                initializationService.initialize(schedule.getScheduleId(), false);
            }
        }

        return convertToAdminPerformanceResponse(savedPerformance);
    }

    public void deletePerformance(Long performanceId) {
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND, "performanceId: " + performanceId));

        deleteExistingImages(performance);

        performanceRepository.delete(performance);
    }

    public AdminPerformanceResponse updatePerformance(Long performanceId, PerformanceRequest updatePerformanceRequest) {
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND, "performanceId: " + performanceId));

        Venue venue = venueRepository.findById(updatePerformanceRequest.getVenueId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND, "venueId: " + updatePerformanceRequest.getVenueId()));

        // 기존 이미지 삭제
        if (performance.getPosterUrl() != null && !updatePerformanceRequest.getPosterUrl().isEmpty() &&
                !Objects.equals(performance.getPosterUrl(), updatePerformanceRequest.getPosterUrl())) {
            deleteExistingImages(performance);

            performance.setPosterUrl(updatePerformanceRequest.getPosterUrl());
        }

        performance.setVenue(venue);
        performance.setTitle(updatePerformanceRequest.getTitle());
        performance.setDescription(updatePerformanceRequest.getDescription());
        performance.setTheme(updatePerformanceRequest.getTheme());
        performance.setStartDate(updatePerformanceRequest.getStartDate());
        performance.setEndDate(updatePerformanceRequest.getEndDate());
        performance.setRunningTime(updatePerformanceRequest.getRunningTime());
        performance.setBasePrice(updatePerformanceRequest.getBasePrice());
        performance.setStatus(Performance.PerformanceStatus.valueOf(updatePerformanceRequest.getStatus()));


        if (updatePerformanceRequest.getSchedules() != null && !updatePerformanceRequest.getSchedules().isEmpty()) {
            List<PerformanceSchedule> performanceSchedules = new ArrayList<>();
            for (PerformanceScheduleRequest scheduleRequest : updatePerformanceRequest.getSchedules()) {
                PerformanceSchedule schedule = PerformanceSchedule.builder()
                        .showDatetime(LocalDateTime.parse(scheduleRequest.getShowDatetime()))
                        .totalSeats(scheduleRequest.getTotalSeats())
                        .status(PerformanceSchedule.ScheduleStatus.valueOf(scheduleRequest.getStatus()))
                        .bookingStartAt(scheduleRequest.getBookingStartAt())
                        .bookingEndAt(scheduleRequest.getBookingEndAt())
                        .build();

                schedule.setPerformance(performance);
                performanceSchedules.add(schedule);
            }

            performance.setSchedules(performanceSchedules);
        }

        Performance updatedPerformance = performanceRepository.save(performance);

        if (updatedPerformance.getSchedules() != null && !updatedPerformance.getSchedules().isEmpty()) {
            for (PerformanceSchedule schedule : updatedPerformance.getSchedules()) {
                initializationService.initialize(schedule.getScheduleId(), false);
            }
        }

        return convertToAdminPerformanceResponse(updatedPerformance);
    }

    /**
     * 기존 이미지 삭제
     */
    private void deleteExistingImages(Performance performance) {
        // 기존 포스터 이미지 삭제
        if (performance.getPosterUrl() != null) {
            s3ImageService.deleteImage(performance.getPosterUrl());
        }
    }

    private List<Booking> getBookingsByPerformanceId(Long performanceId) {
        return bookingRepository.findAll().
                stream().
                filter(
                        book -> book.getSchedule().
                                getPerformance().
                                getPerformanceId().
                                equals(performanceId))
                .toList();
    }

    private ScheduleResponse toScheduleResponse(PerformanceSchedule schedule) {
        return ScheduleResponse.builder()
                .scheduleId(schedule.getScheduleId())
                .showDatetime(schedule.getShowDatetime().toString())
                .availableSeats(schedule.getAvailableSeats())
                .totalSeats(schedule.getTotalSeats())
                .status(schedule.getStatus().toString())
                .build();
    }

    private PerformanceResponse convertToPerformanceResponse(Performance performance) {
        List<ScheduleResponse> scheduleResponses = performance.getSchedules() != null
                ? performance.getSchedules().stream()
                .map(this::toScheduleResponse)
                .toList()
                : new ArrayList<>();

        String posterImageUrl = s3ImageService.generateDownloadPresignedUrl(performance.getPosterUrl(), 3);

        return PerformanceResponse.builder()
                .performanceId(performance.getPerformanceId())
                .title(performance.getTitle())
                .venue(performance.getVenue().getVenueName())
                .theme(performance.getTheme())
                .posterUrl(posterImageUrl)
                .price(performance.getBasePrice())
                .status(performance.getStatus().toString())
                .startDate(performance.getStartDate().toString())
                .endDate(performance.getEndDate().toString())
                .runningTime(performance.getRunningTime())
                .venueAddress(performance.getVenue().getAddress())
                .venueId(performance.getVenue().getVenueId())
                .schedules(scheduleResponses)
                .description(performance.getDescription())
                .build();
    }

    private AdminPerformanceResponse convertToAdminPerformanceResponse(Performance performance) {
        PerformanceResponse performanceResponse = convertToPerformanceResponse(performance);
        List<Booking> bookings = getBookingsByPerformanceId(performanceResponse.getPerformanceId());

        int totalBookings = bookings.stream().mapToInt(Booking::getSeatCount).sum();
        BigDecimal revenue = bookings.stream().map(Booking::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminPerformanceResponse.builder()
                .performanceResponse(performanceResponse)
                .totalBookings(totalBookings)
                .revenue(revenue)
                .build();
    }

}
