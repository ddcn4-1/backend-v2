package org.ddcn41.ticketing_system.service;

import lombok.RequiredArgsConstructor;
import org.ddcn41.ticketing_system.common.client.SeatClient;
import org.ddcn41.ticketing_system.common.dto.seat.InitializeSeatsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminScheduleSeatService {
    private final SeatClient seatClient;

    public List<InitializeSeatsResponse> initializeAllSchedules(boolean dryRun) {
        return seatClient.initializeAllSchedules(dryRun);
    }

    public InitializeSeatsResponse initializeSchedule(Long scheduleId, boolean dryRun) {
        return seatClient.initializeSchedule(scheduleId, dryRun);
    }
}
