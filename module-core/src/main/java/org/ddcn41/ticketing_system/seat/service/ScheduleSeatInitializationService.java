package org.ddcn41.ticketing_system.seat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.dto.seat.InitializeSeatsResponse;
import org.ddcn41.ticketing_system.common.exception.BusinessException;
import org.ddcn41.ticketing_system.common.exception.ErrorCode;
import org.ddcn41.ticketing_system.performance.entity.PerformanceSchedule;
import org.ddcn41.ticketing_system.performance.repository.PerformanceScheduleRepository;
import org.ddcn41.ticketing_system.seat.entity.ScheduleSeat;
import org.ddcn41.ticketing_system.seat.repository.ScheduleSeatRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleSeatInitializationService {
    private final ObjectProvider<ScheduleSeatInitializationService> scheduleSeatInitializationServiceProvider;

    private final PerformanceScheduleRepository scheduleRepository;
    private final ScheduleSeatRepository scheduleSeatRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 모든 스케줄에 대해 좌석 초기화 수행
     */
    @Transactional
    public List<InitializeSeatsResponse> initializeAll(boolean dryRun) {
        ScheduleSeatInitializationService self = scheduleSeatInitializationServiceProvider.getObject();

        List<PerformanceSchedule> schedules = scheduleRepository.findAll();
        List<InitializeSeatsResponse> results = new ArrayList<>();
        for (PerformanceSchedule s : schedules) {
            try {
                results.add(self.initialize(s.getScheduleId(), dryRun));
            } catch (RuntimeException ex) {
                // 개별 스케줄 실패는 전체 중단 없이 계속 진행
                results.add(InitializeSeatsResponse.builder()
                        .scheduleId(s.getScheduleId())
                        .created(0)
                        .total(Math.toIntExact(scheduleSeatRepository.countBySchedule_ScheduleId(s.getScheduleId())))
                        .available(scheduleSeatRepository.countAvailableSeatsByScheduleId(s.getScheduleId()))
                        .dryRun(dryRun)
                        .build());
            }
        }
        return results;
    }
    @Transactional
    public InitializeSeatsResponse initialize(Long scheduleId, boolean dryRun) {
        // 1. 스케줄 로드 및 검증
        PerformanceSchedule schedule = loadAndValidateSchedule(scheduleId);

        // 2. 좌석 맵 파싱
        JsonNode root = parseSeatMap(schedule.getPerformance().getVenue().getSeatMapJson());

        // 3. 등급별 가격 매핑
        Map<String, BigDecimal> pricing = buildPricing(root);

        // 4. 기존 좌석 조회
        List<ScheduleSeat> existingSeats = scheduleSeatRepository.findBySchedule_ScheduleId(scheduleId);
        Map<String, ScheduleSeat> existingMap = existingSeats.stream()
                .collect(Collectors.toMap(s -> key(s.getZone(), s.getRowLabel(), s.getColNum()), s -> s));

        List<ScheduleSeat> newBatch = new ArrayList<>();
        List<ScheduleSeat> updateBatch = new ArrayList<>();

        // 5. 섹션별 좌석 처리
        int created = processSections(root.path("sections"), existingMap, pricing, newBatch, updateBatch, dryRun, schedule);

        // 6. 배치 저장
        if (!dryRun) {
            saveBatch(newBatch);
            saveBatch(updateBatch);

            long total = scheduleSeatRepository.countBySchedule_ScheduleId(scheduleId);
            int available = scheduleSeatRepository.countAvailableSeatsByScheduleId(scheduleId);

            schedule.setTotalSeats(Math.toIntExact(total));
            schedule.setAvailableSeats(available);
            scheduleRepository.save(schedule);
            scheduleRepository.refreshScheduleStatus(scheduleId);
        }

        // 7. dryRun 여부에 따라 총좌석/가능좌석 계산
        long total = dryRun ? existingSeats.size() + created : schedule.getTotalSeats();
        int available = dryRun ? existingSeats.size() + created : schedule.getAvailableSeats();

        // 8. Builder를 이용해 DTO 반환
        return InitializeSeatsResponse.builder()
                .scheduleId(schedule.getScheduleId())
                .created(created)
                .total((int) total)
                .available(available)
                .dryRun(dryRun)
                .build();
    }

    // 스케줄 로드 및 검증
    private PerformanceSchedule loadAndValidateSchedule(Long scheduleId) {
        PerformanceSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND, "scheduleId: " + scheduleId));

        if (schedule.getPerformance() == null) {
            throw new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND, "scheduleId: " + scheduleId);
        }
        if (schedule.getPerformance().getVenue() == null) {
            throw new BusinessException(ErrorCode.VENUE_NOT_FOUND, "scheduleId: " + scheduleId);
        }
        return schedule;
    }

    // 좌석 맵 JSON 파싱
    private JsonNode parseSeatMap(String seatMapJson) {
        if (seatMapJson == null || seatMapJson.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SEAT_MAP, "공연장의 좌석 맵 JSON이 비어있습니다");
        }
        try {
            return objectMapper.readTree(seatMapJson);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_SEAT_MAP, "좌석 맵 JSON 파싱 실패");
        }
    }

    // 가격 매핑 생성
    private Map<String, BigDecimal> buildPricing(JsonNode root) {
        Map<String, BigDecimal> pricing = new HashMap<>();
        JsonNode pricingNode = root.path("pricing");

        if (pricingNode.isObject()) {
            Iterator<String> fieldNames = pricingNode.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                JsonNode valueNode = pricingNode.get(key);
                try {
                    pricing.put(key, new BigDecimal(valueNode.asText()));
                } catch (Exception ignored) {}
            }
        }
        return pricing;
    }


    // 섹션별 좌석 처리
    private int processSections(JsonNode sections,
                                Map<String, ScheduleSeat> existingMap,
                                Map<String, BigDecimal> pricing,
                                List<ScheduleSeat> newBatch,
                                List<ScheduleSeat> updateBatch,
                                boolean dryRun,
                                PerformanceSchedule schedule) {

        if (!sections.isArray()) {
            throw new BusinessException(ErrorCode.INVALID_SEAT_MAP, "좌석 맵 JSON의 sections 형식이 올바르지 않습니다");
        }

        int created = 0;
        for (JsonNode sec : sections) {
            created += processSection(sec, existingMap, pricing, newBatch, updateBatch, dryRun, schedule);
        }
        return created;
    }

    private int processSection(JsonNode section,
                               Map<String, ScheduleSeat> existingMap,
                               Map<String, BigDecimal> pricing,
                               List<ScheduleSeat> newBatch,
                               List<ScheduleSeat> updateBatch,
                               boolean dryRun,
                               PerformanceSchedule schedule) {

        String zone = textOrNull(section, "zone");
        String grade = textOrNull(section, "grade");
        int rows = intOrDefault(section, "rows", 0);
        int cols = intOrDefault(section, "cols", 0);
        String rowLabelFrom = textOrNull(section, "rowLabelFrom");
        int seatStart = intOrDefault(section, "seatStart", 1);

        if (rows <= 0 || cols <= 0 || rowLabelFrom == null || rowLabelFrom.isBlank()) {
            return 0; // 불완전 섹션은 스킵
        }

        BigDecimal defaultPrice = pricing.getOrDefault(grade == null ? "" : grade, BigDecimal.ZERO);
        return createSeats(rows, cols, seatStart, rowLabelFrom, zone, grade, defaultPrice,
                existingMap, newBatch, updateBatch, dryRun, schedule);
    }

    private int createSeats(int rows, int cols, int seatStart, String rowLabelFrom, String zone,
                            String grade, BigDecimal price, Map<String, ScheduleSeat> existingMap,
                            List<ScheduleSeat> newBatch, List<ScheduleSeat> updateBatch,
                            boolean dryRun, PerformanceSchedule schedule) {

        int created = 0;
        for (int r = 0; r < rows; r++) {
            String rowLabel = incrementAlpha(rowLabelFrom, r);
            for (int c = 0; c < cols; c++) {
                String colNum = String.valueOf(seatStart + c);
                String k = key(zone, rowLabel, colNum);

                ScheduleSeat existingSeat = existingMap.get(k);

                if (existingSeat != null) {
                    updateExistingSeat(existingSeat, price, updateBatch, dryRun);
                } else {
                    createNewSeat(schedule, zone, grade, rowLabel, colNum, price, newBatch, dryRun);
                    created++;
                }
            }
        }
        return created;
    }

    private void updateExistingSeat(ScheduleSeat seat, BigDecimal price,
                                    List<ScheduleSeat> updateBatch, boolean dryRun) {
        BigDecimal current = seat.getPrice() == null ? BigDecimal.ZERO : seat.getPrice();
        if (current.compareTo(price) != 0) {
            seat.setPrice(price);
            if (!dryRun) updateBatch.add(seat);
        }
    }

    private void createNewSeat(PerformanceSchedule schedule, String zone, String grade,
                               String rowLabel, String colNum, BigDecimal price,
                               List<ScheduleSeat> newBatch, boolean dryRun) {

        if (dryRun) return;

        ScheduleSeat seat = ScheduleSeat.builder()
                .schedule(schedule)
                .zone(zone)
                .grade(grade == null ? "" : grade)
                .rowLabel(rowLabel)
                .colNum(colNum)
                .price(price)
                .build();

        newBatch.add(seat);
        if (newBatch.size() >= 500) saveBatch(newBatch);
    }

    // 배치 저장
    private void saveBatch(List<ScheduleSeat> batch) {
        if (!batch.isEmpty()) {
            scheduleSeatRepository.saveAll(batch);
            batch.clear();
        }
    }

    private static String key(String zone, String rowLabel, String colNum) {
        return (zone == null ? "" : zone) + "|" + (rowLabel == null ? "" : rowLabel) + "|" + (colNum == null ? "" : colNum);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static int intOrDefault(JsonNode node, String field, int def) {
        JsonNode n = node.get(field);
        return n == null || !n.canConvertToInt() ? def : n.asInt();
    }

    // A..Z, AA..AZ, BA.. 증가
    private static String incrementAlpha(String start, int offset) {
        String base = start.toUpperCase();
        int value = alphaToInt(base) + offset;
        return intToAlpha(value);
    }

    private static int alphaToInt(String s) {
        int v = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 'A' || ch > 'Z') throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid row label: " + s);
            v = v * 26 + (ch - 'A' + 1);
        }
        return v - 1; // zero-based
    }

    private static String intToAlpha(int v) {
        v = v + 1; // one-based
        StringBuilder sb = new StringBuilder();
        while (v > 0) {
            int rem = (v - 1) % 26;
            sb.append((char) ('A' + rem));
            v = (v - 1) / 26;
        }
        return sb.reverse().toString();
    }
}
