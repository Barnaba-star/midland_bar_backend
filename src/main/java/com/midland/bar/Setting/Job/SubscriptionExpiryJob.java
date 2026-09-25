package com.midland.bar.Setting.Job;

import com.midland.bar.Setting.Repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Keeps Branch.subscriptionStatus in sync with Branch.closeSubscription so
 * the frontend badge shows EXPIRED (red) without needing to re-derive it on
 * every read. Login itself doesn't depend on this job - UserController
 * checks closeSubscription directly, so access is blocked the instant a
 * branch lapses even before this next runs.
 */
@Component
@RequiredArgsConstructor
@Log
public class SubscriptionExpiryJob {

    private final BranchRepository branchRepository;

    @Scheduled(cron = "0 0 1 * * *") // once a day, 01:00
    public void markExpiredSubscriptions() {
        int updated = branchRepository.markExpiredSubscriptions(LocalDate.now());
        if (updated > 0) {
            log.info("Marked " + updated + " branch(es) as EXPIRED");
        }
    }
}
