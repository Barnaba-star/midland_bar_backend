package com.midland.bar.Bar.Job;

import com.midland.bar.Bar.Service.DebtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every Monday morning, a text to each credit customer who still owes (owner, 2026-10-18). */
@Component
@RequiredArgsConstructor
@Slf4j
public class DebtReminderJob {

    private final DebtService debtService;

    @Scheduled(cron = "0 0 10 * * MON") // Mondays, 10:00 EAT - the server runs on Africa/Dar_es_Salaam
    public void remind() {
        try {
            int sent = debtService.sendReminders();
            if (sent > 0)
                log.info("Sent {} debt reminder(s)", sent);
        } catch (Exception e) {
            log.warn("Debt reminders failed: {}", e.getMessage());
        }
    }
}
