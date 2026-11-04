package com.midland.bar.Bar.Config;

import com.midland.bar.Bar.Service.DebtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Madeni customers registered before customer codes existed (2026-11-04)
 * get one, so a waiter can note an ofa / deni bill for them. Idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerCodeBackfill implements ApplicationRunner {

    private final DebtService debtService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int given = debtService.giveMissingCodes();
            if (given > 0)
                log.info("Gave a customer code to {} Madeni customer(s)", given);
        } catch (Exception e) {
            log.warn("Customer code backfill failed: {}", e.getMessage());
        }
    }
}
