package com.midland.bar.Bar.Alert;

import com.midland.bar.Bar.Model.WorkShift;
import com.midland.bar.Bar.Repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/** The owner's morning summary (08:00 EAT) and the hourly look for shifts left open. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OwnerAlertJob {

    private final OwnerAlertService alerts;
    private final WorkShiftRepository workShiftRepository;

    @Scheduled(cron = "0 0 8 * * *")
    public void morning() {
        try {
            int n = alerts.morningDigest();
            if (n > 0)
                log.info("Morning digest sent to {} branch(es)", n);
        } catch (Exception e) {
            log.warn("Morning digest failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void longShifts() {
        LocalDateTime now = LocalDateTime.now();
        for (WorkShift s : workShiftRepository.findAllOpen()) {
            if (Boolean.TRUE.equals(s.getLongOpenAlerted()) || s.getOpenedAt() == null)
                continue;
            long hours = Duration.between(s.getOpenedAt(), now).toHours();
            if (hours < alerts.rules(s.getBranchUid()).alertShiftHoursOrDefault())
                continue;
            alerts.shiftOpenLong(s.getBranchUid(), s.getCashierName() != null ? s.getCashierName() : s.getCashierEmail(), hours);
            s.setLongOpenAlerted(true);
            workShiftRepository.save(s);
        }
    }
}
