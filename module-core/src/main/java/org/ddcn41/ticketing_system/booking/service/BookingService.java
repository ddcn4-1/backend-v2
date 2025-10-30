package org.ddcn41.ticketing_system.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.booking.dto.BookingProjection;
import org.ddcn41.ticketing_system.booking.dto.request.CancelBookingRequestDto;
import org.ddcn41.ticketing_system.booking.dto.request.CreateBookingRequestDto;
import org.ddcn41.ticketing_system.booking.dto.response.CancelBooking200ResponseDto;
import org.ddcn41.ticketing_system.booking.dto.response.CreateBookingResponseDto;
import org.ddcn41.ticketing_system.booking.entity.Booking;
import org.ddcn41.ticketing_system.booking.entity.Booking.BookingStatus;
import org.ddcn41.ticketing_system.booking.entity.BookingSeat;
import org.ddcn41.ticketing_system.booking.repository.BookingRepository;
import org.ddcn41.ticketing_system.booking.repository.BookingSeatRepository;
import org.ddcn41.ticketing_system.common.client.QueueClient;
import org.ddcn41.ticketing_system.common.dto.booking.BookingDto;
import org.ddcn41.ticketing_system.common.dto.booking.BookingSeatDto;
import org.ddcn41.ticketing_system.common.dto.booking.GetBookingDetail200ResponseDto;
import org.ddcn41.ticketing_system.common.dto.booking.GetBookings200ResponseDto;
import org.ddcn41.ticketing_system.common.dto.queue.TokenVerifyRequest;
import org.ddcn41.ticketing_system.common.exception.BusinessException;
import org.ddcn41.ticketing_system.common.exception.ErrorCode;
import org.ddcn41.ticketing_system.performance.entity.Performance;
import org.ddcn41.ticketing_system.performance.entity.PerformanceSchedule;
import org.ddcn41.ticketing_system.performance.repository.PerformanceScheduleRepository;
import org.ddcn41.ticketing_system.seat.entity.ScheduleSeat;
import org.ddcn41.ticketing_system.seat.repository.ScheduleSeatRepository;
import org.ddcn41.ticketing_system.seat.service.SeatService;
import org.ddcn41.ticketing_system.user.entity.User;
import org.ddcn41.ticketing_system.user.repository.UserRepository;
import org.ddcn41.ticketing_system.venue.entity.Venue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PerformanceScheduleRepository scheduleRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;

    private final SeatService seatService;
    private final BookingAuditService bookingAuditService;
    private final QueueClient queueClient;
    private final UserRepository userRepository;


    @Transactional(rollbackFor = Exception.class)
    public CreateBookingResponseDto createBooking(String userId, CreateBookingRequestDto req) {
        // 유저 + 스케줄 조회
        User user = findUser(userId);
        PerformanceSchedule schedule = findSchedule(req.getScheduleId());

        // 대기열 토큰 검증 todo: 임시 정지 상태
        validateQueueTokenIfRequired(req, user, schedule);

        // 좌석 매핑 및 검증
        JsonNode root = parseSeatMap(schedule);
        JsonNode sections = root.path("sections");
        JsonNode pricingNode = root.path("pricing");

        List<ScheduleSeat> requestedSeats = mapAndValidateSeats(req, schedule, sections, pricingNode);

        // 좌석 락킹 및 가용 좌석 감소
        lockSeatsAndUpdateAvailability(requestedSeats, schedule);

        // 예매 엔티티 생성
        Booking booking = createBookingEntity(user, schedule, requestedSeats);

        // 대기열 토큰 사용
        processQueueToken(req, user);

        // BookingSeat 생성
        List<BookingSeat> savedSeats = saveBookingSeats(booking, requestedSeats);
        booking.setBookingSeats(savedSeats);

        // 좌석 상태 BOOKED로 전환
        updateSeatStatusToBooked(requestedSeats);

        // 감사 로그 기록
        bookingAuditService.logBookingCreated(user, booking,
                requestedSeats.stream().map(ScheduleSeat::getSeatId).toList());

        return toCreateResponse(booking);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private PerformanceSchedule findSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private JsonNode parseSeatMap(PerformanceSchedule schedule) {
        try {
            ObjectMapper om = new ObjectMapper();
            String seatMapJson = schedule.getPerformance().getVenue().getSeatMapJson();
            return om.readTree(seatMapJson == null ? "{}" : seatMapJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_SEAT_MAP);
        }
    }
    private List<ScheduleSeat> mapAndValidateSeats(CreateBookingRequestDto req,
                                                   PerformanceSchedule schedule,
                                                   JsonNode sections,
                                                   JsonNode pricingNode) {
        return req.getSeats().stream()
                .map(sel -> {
                    String grade = safeUpper(sel.getGrade());
                    String zone = safeUpper(sel.getZone());
                    String rowLabel = safeUpper(sel.getRowLabel());
                    String colNum = sel.getColNum();

                    if (!validateBySeatMap(sections, grade, zone, rowLabel, colNum)) {
                        throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE,
                                String.format("유효하지 않은 좌석: %s/%s-%s%s", grade, zone, rowLabel, colNum));
                    }

                    ScheduleSeat seat = scheduleSeatRepository.findBySchedule_ScheduleIdAndZoneAndRowLabelAndColNum(
                            schedule.getScheduleId(), zone, rowLabel, colNum);
                    if (seat == null) throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
                    if (!safeUpper(seat.getGrade()).equals(grade)
                            || !safeUpper(seat.getZone()).equals(zone)) {
                        throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
                    }

                    BigDecimal price = priceByGrade(pricingNode, grade);
                    if (price != null) seat.setPrice(price);
                    if (seat.getStatus() != ScheduleSeat.SeatStatus.AVAILABLE) {
                        throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
                    }

                    seat.setStatus(ScheduleSeat.SeatStatus.LOCKED);
                    return seat;
                })
                .toList();
    }

    private void lockSeatsAndUpdateAvailability(List<ScheduleSeat> seats, PerformanceSchedule schedule) {
        try {
            scheduleSeatRepository.saveAll(seats);
            scheduleSeatRepository.flush();

            if (!seats.isEmpty()) {
                int affected = scheduleRepository.decrementAvailableSeats(schedule.getScheduleId(), seats.size());
                if (affected == 0) throw new BusinessException(ErrorCode.INSUFFICIENT_SEATS);
                scheduleRepository.refreshScheduleStatus(schedule.getScheduleId());
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_BOOKED);
        }
    }

    private Booking createBookingEntity(User user, PerformanceSchedule schedule, List<ScheduleSeat> seats) {
        BigDecimal total = seats.stream()
                .map(ScheduleSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Booking booking = Booking.builder()
                .bookingNumber("DDCN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(user.getUserId())
                .schedule(schedule)
                .seatCount(seats.size())
                .totalAmount(total)
                .status(BookingStatus.CONFIRMED)
                .build();

        return bookingRepository.save(booking);
    }

    private void processQueueToken(CreateBookingRequestDto req, User user) {
        if (req.getQueueToken() == null || req.getQueueToken().trim().isEmpty()) return;

        try {
            queueClient.useToken(req.getQueueToken());
            log.info("토큰 사용 완료 - 사용자: {}, 토큰: {}", user.getUsername(), req.getQueueToken());
        } catch (Exception e) {
            log.warn("토큰 사용 처리 중 오류 발생: {}", e.getMessage());
        }
    }

    private List<BookingSeat> saveBookingSeats(Booking booking, List<ScheduleSeat> seats) {
        return seats.stream()
                .map(seat -> BookingSeat.builder()
                        .booking(booking)
                        .seat(seat)
                        .seatPrice(seat.getPrice())
                        .build())
                .map(bookingSeatRepository::save)
                .toList();
    }

    private void updateSeatStatusToBooked(List<ScheduleSeat> seats) {
        seats.forEach(seat -> seat.setStatus(ScheduleSeat.SeatStatus.BOOKED));
        scheduleSeatRepository.saveAll(seats);
    }


    /**
     * 대기열 토큰 검증 - 호출
     */
    private void validateQueueTokenIfRequired(
            CreateBookingRequestDto req,
            User user,
            PerformanceSchedule schedule) {

        if (req.getQueueToken() != null && !req.getQueueToken().trim().isEmpty()) {

            try {
                // FeignClient로 REST API 호출
                TokenVerifyRequest verifyRequest = TokenVerifyRequest.builder()
                        .userId(user.getUserId())
                        .performanceId(schedule.getPerformance().getPerformanceId())
                        .build();

                 //임시 테스트를 위해 검증 진행 X
//                ApiResponse<TokenVerifyResponse> response =
//                        queueClient.verifyToken(req.getQueueToken(), verifyRequest);
//
//                if (response.getData() == null || !response.getData().isValid()) {
//                    throw new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID,
//                            response.getData() != null ? response.getData().getReason() : "");
//                }

            } catch (feign.FeignException e) {
                log.error("Queue 서비스 호출 실패: {}", e.getMessage());
                throw new BusinessException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
            } catch (Exception e) {
                log.warn("토큰 검증 중 오류 발생: {}", e.getMessage());
                throw new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID);
            }
        } else {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_REQUIRED);
        }
    }

    /**
     * 예약 상세 조회 (관리자용 - 소유권 검증 없음)
     */
    @Transactional(readOnly = true)
    public GetBookingDetail200ResponseDto getBookingDetail(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
        return toDetailDto(booking);
    }

    /**
     * 사용자 예약 상세 조회 (소유권 검증 포함)
     */
    @Transactional(readOnly = true)
    public GetBookingDetail200ResponseDto getUserBookingDetail(String userId, Long bookingId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        // 소유권 검증
        if (!booking.getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 예매에 접근할 권한이 없습니다");
        }

        return toDetailDto(booking);
    }

    /**
     * 예약 목록 조회 (DTO Projection 사용 - 성능 최적화)
     */
    @Transactional(readOnly = true)
    public GetBookings200ResponseDto getBookings(String status, int page, int limit) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.max(limit, 1));

        Page<BookingProjection> result;
        if (status != null && !status.isBlank()) {
            BookingStatus bs;
            try {
                bs = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 상태 값");
            }
            result = bookingRepository.findAllByStatusWithDetails(bs, pr);
        } else {
            result = bookingRepository.findAllWithDetails(pr);
        }

        List<BookingDto> items = result.getContent().stream()
                .map(this::toListDtoFromProjection)
                .toList();

        return GetBookings200ResponseDto.builder()
                .bookings(items)
                .total(Math.toIntExact(result.getTotalElements()))
                .page(page)
                .build();
    }

    /**
     * 예약 목록 조회 (기존 Entity 방식 - 하위 호환용)
     */
    @Transactional(readOnly = true)
    public GetBookings200ResponseDto getBookingsLegacy(String status, int page, int limit) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.max(limit, 1));

        Page<Booking> result;
        if (status != null && !status.isBlank()) {
            BookingStatus bs;
            try {
                bs = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 상태 값");
            }
            result = bookingRepository.findAllByStatus(bs, pr);
        } else {
            result = bookingRepository.findAll(pr);
        }

        List<BookingDto> items = result.getContent().stream()
                .map(this::toListDto)
                .toList();

        return GetBookings200ResponseDto.builder()
                .bookings(items)
                .total(Math.toIntExact(result.getTotalElements()))
                .page(page)
                .build();
    }

    /**
     * 예약 취소
     */
    @Transactional(rollbackFor = Exception.class)
    public CancelBooking200ResponseDto cancelBooking(Long bookingId, CancelBookingRequestDto req, String actorUsername) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BOOKING_ALREADY_CANCELLED);
        }

        // 좌석 취소 (SeatService에 위임)
        List<Long> seatIds = booking.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getSeatId())
                .toList();

        boolean cancelled = seatService.cancelSeats(seatIds);

        if (!cancelled) {
            throw new BusinessException(ErrorCode.SEAT_CANCEL_FAILED);
        }

        // 예약 상태 변경
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(java.time.LocalDateTime.now());
        if (req != null) {
            booking.setCancellationReason(req.getReason());
        }

        bookingRepository.save(booking);

        CancelBooking200ResponseDto response = CancelBooking200ResponseDto.builder()
                .message("예매 취소 성공")
                .bookingId(booking.getBookingId())
                .status(BookingStatus.CANCELLED.name())
                .cancelledAt(odt(booking.getCancelledAt()))
                .refundAmount(booking.getTotalAmount() == null ? 0.0 : booking.getTotalAmount().doubleValue())
                .build();

        bookingAuditService.logBookingCancelled(actorUsername, booking, req != null ? req.getReason() : null);
        return response;
    }

    /**
     * 사용자별 예약 목록 조회
     */
    @Transactional(readOnly = true)
    public GetBookings200ResponseDto getUserBookings(String userId, String status, int page, int limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.max(limit, 1));

        Page<Booking> result;
        if (status != null && !status.isBlank()) {
            BookingStatus bs;
            try {
                bs = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 상태 값");
            }
            result = bookingRepository.findByUserIdAndStatus(user.getUserId(), bs, pr);
        } else {
            result = bookingRepository.findByUserId(user.getUserId(), pr);
        }

        List<BookingDto> items = result.getContent().stream()
                .map(this::toListDto)
                .toList();

        return GetBookings200ResponseDto.builder()
                .bookings(items)
                .total(Math.toIntExact(result.getTotalElements()))
                .page(page)
                .build();
    }

    // === Private Helper Methods (DTO 변환) ===

    /**
     * BookingProjection을 BookingDto로 변환 (성능 최적화)
     */
    private BookingDto toListDtoFromProjection(BookingProjection p) {
        User user = userRepository.findById(p.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<BookingSeatDto> seatDtos = new ArrayList<>();
        if (p.getBookingSeatId() != null) {
            seatDtos.add(BookingSeatDto.builder()
                    .bookingSeatId(p.getBookingSeatId())
                    .bookingId(p.getBookingId())
                    .seatId(null)
                    .seatPrice(p.getSeatPrice() == null ? 0.0 : p.getSeatPrice().doubleValue())
                    .grade(p.getSeatGrade())
                    .zone(p.getSeatZone())
                    .rowLabel(p.getSeatRowLabel())
                    .colNum(p.getSeatColNum())
                    .createdAt(null)
                    .build());
        }

        return BookingDto.builder()
                .bookingId(p.getBookingId())
                .bookingNumber(p.getBookingNumber())
                .userId(user.getUserId())
                .userName(user.getUsername())
                .userPhone(user.getPhone())
                .scheduleId(p.getScheduleId())
                .performanceTitle(p.getPerformanceTitle())
                .venueName(p.getVenueName())
                .showDate(odt(p.getShowDatetime()))
                .seatCount(p.getSeatCount())
                .totalAmount(p.getTotalAmount() == null ? 0.0 : p.getTotalAmount().doubleValue())
                .seats(seatDtos.isEmpty() ? List.of() : seatDtos)
                .status(p.getStatus() == null ? null : BookingDto.StatusEnum.valueOf(p.getStatus()))
                .expiresAt(odt(p.getExpiresAt()))
                .bookedAt(odt(p.getBookedAt()))
                .cancelledAt(odt(p.getCancelledAt()))
                .cancellationReason(p.getCancellationReason())
                .createdAt(odt(p.getCreatedAt()))
                .updatedAt(odt(p.getUpdatedAt()))
                .build();
    }

    private CreateBookingResponseDto toCreateResponse(Booking b) {
        return CreateBookingResponseDto.builder()
                .bookingId(b.getBookingId())
                .bookingNumber(b.getBookingNumber())
                .userId(b.getUserId() != null ? b.getUserId() : null)
                .scheduleId(b.getSchedule() != null ? b.getSchedule().getScheduleId() : null)
                .seatCount(b.getSeatCount())
                .totalAmount(b.getTotalAmount() == null ? 0.0 : b.getTotalAmount().doubleValue())
                .status(b.getStatus() != null ? b.getStatus().name() : null)
                .expiresAt(odt(b.getExpiresAt()))
                .bookedAt(odt(b.getBookedAt()))
                .seats(b.getBookingSeats() == null ? List.of() : b.getBookingSeats().stream().map(this::toSeatDto).toList())
                .build();
    }

    private BookingSeatDto toSeatDto(BookingSeat bs) {
        ScheduleSeat scheduleSeat = bs.getSeat();
        return BookingSeatDto.builder()
                .bookingSeatId(bs.getBookingSeatId())
                .bookingId(bs.getBooking() != null ? bs.getBooking().getBookingId() : null)
                .seatId(scheduleSeat != null ? scheduleSeat.getSeatId() : null)
                .seatPrice(bs.getSeatPrice() == null ? 0.0 : bs.getSeatPrice().doubleValue())
                .grade(scheduleSeat != null ? scheduleSeat.getGrade() : null)
                .zone(scheduleSeat != null ? scheduleSeat.getZone() : null)
                .rowLabel(scheduleSeat != null ? scheduleSeat.getRowLabel() : null)
                .colNum(scheduleSeat != null ? scheduleSeat.getColNum() : null)
                .createdAt(odt(bs.getCreatedAt()))
                .build();
    }

    private BookingDto toListDto(Booking b) {
        User user = userRepository.findById(b.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return BookingDto.builder()
                .bookingId(b.getBookingId())
                .bookingNumber(b.getBookingNumber())
                .userId(user != null ? user.getUserId() : null)
                .userName(user != null ? user.getName() : null)
                .userPhone(user != null ? user.getPhone() : null)
                .scheduleId(b.getSchedule() != null ? b.getSchedule().getScheduleId() : null)
                .performanceTitle(b.getSchedule() != null && b.getSchedule().getPerformance() != null ? b.getSchedule().getPerformance().getTitle() : null)
                .venueName(b.getSchedule() != null && b.getSchedule().getPerformance() != null && b.getSchedule().getPerformance().getVenue() != null ? b.getSchedule().getPerformance().getVenue().getVenueName() : null)
                .showDate(b.getSchedule() != null ? odt(b.getSchedule().getShowDatetime()) : null)
                .seatCount(b.getSeatCount())
                .totalAmount(b.getTotalAmount() == null ? 0.0 : b.getTotalAmount().doubleValue())
                .seats(b.getBookingSeats() == null ? List.of() : b.getBookingSeats().stream().map(this::toSeatDto).toList())
                .status(b.getStatus() == null ? null : BookingDto.StatusEnum.valueOf(b.getStatus().name()))
                .expiresAt(odt(b.getExpiresAt()))
                .bookedAt(odt(b.getBookedAt()))
                .cancelledAt(odt(b.getCancelledAt()))
                .cancellationReason(b.getCancellationReason())
                .createdAt(odt(b.getCreatedAt()))
                .updatedAt(odt(b.getUpdatedAt()))
                .build();
    }

    private GetBookingDetail200ResponseDto toDetailDto(Booking booking) {
        User user = userRepository.findById(booking.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PerformanceSchedule schedule = booking.getSchedule();
        Performance performance = schedule != null ? schedule.getPerformance() : null;
        Venue venue = performance != null ? performance.getVenue() : null;

        SeatInfo seatInfo = extractSeatInfo(booking);
        List<BookingSeatDto> seatDtos = toSeatDtoList(booking);

        return GetBookingDetail200ResponseDto.builder()
                .bookingId(booking.getBookingId())
                .bookingNumber(booking.getBookingNumber())
                .userId(user.getUserId())
                .userName(user.getName())
                .userPhone(user.getPhone())
                .scheduleId(idOrNull(schedule))
                .performanceTitle(textOrNull(performance, Performance::getTitle))
                .venueName(textOrNull(venue, Venue::getVenueName))
                .showDate(odtOrNull(schedule, PerformanceSchedule::getShowDatetime))
                .seatCode(seatInfo.code())
                .seatZone(seatInfo.zone())
                .seatCount(booking.getSeatCount())
                .totalAmount(booking.getTotalAmount() != null ? booking.getTotalAmount().doubleValue() : 0.0)
                .status(statusOrNull(booking.getStatus()))
                .expiresAt(odt(booking.getExpiresAt()))
                .bookedAt(odt(booking.getBookedAt()))
                .cancelledAt(odt(booking.getCancelledAt()))
                .cancellationReason(booking.getCancellationReason())
                .createdAt(odt(booking.getCreatedAt()))
                .updatedAt(odt(booking.getUpdatedAt()))
                .seats(seatDtos)
                .build();
    }
    private record SeatInfo(String code, String zone) {}

    private SeatInfo extractSeatInfo(Booking booking) {
        if (booking.getBookingSeats() == null || booking.getBookingSeats().isEmpty()) {
            return new SeatInfo(null, null);
        }

        var seat = booking.getBookingSeats().getFirst().getSeat();
        if (seat == null) return new SeatInfo(null, null);

        String code = (seat.getRowLabel() != null && seat.getColNum() != null)
                ? seat.getRowLabel() + seat.getColNum()
                : null;
        return new SeatInfo(code, seat.getZone());
    }

    private List<BookingSeatDto> toSeatDtoList(Booking booking) {
        if (booking.getBookingSeats() == null) return List.of();
        return booking.getBookingSeats().stream()
                .map(this::toSeatDto)
                .filter(Objects::nonNull)
                .toList();
    }

    private Long idOrNull(PerformanceSchedule schedule) {
        return schedule != null ? schedule.getScheduleId() : null;
    }

    private <T> String textOrNull(T obj, Function<T, String> getter) {
        return obj != null ? getter.apply(obj) : null;
    }

    private <T> OffsetDateTime odtOrNull(T obj, Function<T, LocalDateTime> getter) {
        return obj != null ? odt(getter.apply(obj)) : null;
    }

    private GetBookingDetail200ResponseDto.StatusEnum statusOrNull(BookingStatus status) {
        return status != null
                ? GetBookingDetail200ResponseDto.StatusEnum.valueOf(status.name())
                : null;
    }

    private OffsetDateTime odt(java.time.LocalDateTime ldt) {
        return ldt == null ? null : ldt.atOffset(ZoneOffset.UTC);
    }

    private static String safeUpper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static boolean validateBySeatMap(JsonNode sections, String grade, String zone, String rowLabel, String colNum) {
        if (!sections.isArray() || rowLabel == null || colNum == null) {
            return false;
        }

        String normalizedRow = rowLabel.trim().toUpperCase();
        String normalizedCol = colNum.trim();
        String normalizedGrade = grade == null ? null : grade.trim().toUpperCase();
        String normalizedZone = zone == null ? null : zone.trim().toUpperCase();

        for (JsonNode section : sections) {
            if (!isValidSection(section, normalizedGrade, normalizedZone)) {
                continue;
            }

            if (isSeatInSection(section, normalizedRow, normalizedCol)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isValidSection(JsonNode section, String normalizedGrade, String normalizedZone) {
        int rows = section.path("rows").asInt(0);
        int cols = section.path("cols").asInt(0);
        if (rows <= 0 || cols <= 0) return false;

        String sectionRowStart = safeUpper(textOrNull(section, "rowLabelFrom"));
        if (sectionRowStart == null || sectionRowStart.isBlank()) return false;

        String sectionGrade = safeUpper(textOrNull(section, "grade"));
        String sectionZone = safeUpper(textOrNull(section, "zone"));

        boolean gradeMatch = normalizedGrade == null || normalizedGrade.isBlank() || normalizedGrade.equals(sectionGrade);
        boolean zoneMatch = normalizedZone == null || normalizedZone.isBlank() || normalizedZone.equals(sectionZone);

        return gradeMatch && zoneMatch;
    }

    private static boolean isSeatInSection(JsonNode section, String targetRow, String targetCol) {
        int rows = section.path("rows").asInt(0);
        int cols = section.path("cols").asInt(0);
        int seatStart = section.path("seatStart").asInt(1);
        String rowStart = safeUpper(textOrNull(section, "rowLabelFrom"));

        for (int r = 0; r < rows; r++) {
            String currentRow = incrementAlpha(rowStart, r);
            if (!currentRow.equals(targetRow)) continue;

            int colNum = Integer.parseInt(targetCol);
            int minCol = seatStart;
            int maxCol = seatStart + cols - 1;

            // 숫자 범위만 비교하므로 내부 루프 제거 (복잡도 ↓)
            return colNum >= minCol && colNum <= maxCol;
        }
        return false;
    }

    private static BigDecimal priceByGrade(JsonNode pricingNode, String grade) {
        if (pricingNode == null || !pricingNode.isObject() || grade == null) {
            return null;
        }

        JsonNode direct = pricingNode.get(grade);
        if (direct != null) {
            try {
                return new BigDecimal(direct.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String normalizedGrade = grade.trim().toUpperCase();

        for (var entry : pricingNode.properties()) {
            if (normalizedGrade.equals(entry.getKey().trim().toUpperCase())) {
                try {
                    return new BigDecimal(entry.getValue().asText());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String incrementAlpha(String start, int offset) {
        int baseValue = alphaToInt(start) + offset;
        return intToAlpha(baseValue);
    }

    private static int alphaToInt(String s) {
        int value = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid row label: " + s);
            }
            value = value * 26 + (ch - 'A' + 1);
        }
        return value - 1;
    }

    private static String intToAlpha(int value) {
        value = value + 1;
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            sb.append((char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return sb.reverse().toString();
    }
}
