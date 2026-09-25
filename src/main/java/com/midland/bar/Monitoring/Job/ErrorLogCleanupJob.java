package com.midland.bar.Monitoring.Job;

import com.midland.bar.Monitoring.Repository.AuditLogRepository;
import com.midland.bar.Monitoring.Repository.ErrorLogRepository;
import com.midland.bar.Setting.Model.PlatformSetting;
import com.midland.bar.Setting.Service.PlatformSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Error rows are write-heavy and read rarely, so they are aged out rather than
 * kept forever: hidden from the list, then deleted for good. Both windows are
 * set in Settings > Config.
 */
@Component
@Log
@RequiredArgsConstructor
public class ErrorLogCleanupJob {

    private final ErrorLogRepository errorLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final PlatformSettingService platformSettingService;

    @Scheduled(cron = "0 30 1 * * *") // once a day, 01:30 - after the subscription job
    @Transactional
    public void cleanUpOldErrors() {
        LocalDateTime now = LocalDateTime.now();
        PlatformSetting setting = platformSettingService.current();
        int hidden = errorLogRepository.clearOlderThan(now.minusDays(setting.getErrorRetentionDays()));
        int purged = errorLogRepository.purgeOlderThan(now.minusDays(setting.getErrorPurgeDays()));
        if (hidden > 0 || purged > 0) {
            log.info("Error log cleanup: hidden=" + hidden + " purged=" + purged);
        }

        // Audit entries are never soft-deleted - there is nothing to "clear",
        // they simply age out.
        int auditPurged = auditLogRepository.purgeOlderThan(now.minusDays(setting.getAuditRetentionDays()));
        if (auditPurged > 0) {
            log.info("Audit log cleanup: purged=" + auditPurged);
        }
    }
}
