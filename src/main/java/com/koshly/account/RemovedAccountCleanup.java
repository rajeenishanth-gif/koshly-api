package com.koshly.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every night, deletes for good the accounts that were removed more than
 * koshly.retention.removed-account-days ago (with their card and loan details).
 *
 * Note for Cloud Run: an instance may be asleep at 03:30, so in production
 * this should be triggered by Cloud Scheduler instead. The service method
 * stays the same.
 */
@Component
class RemovedAccountCleanup {

    private static final Logger log = LoggerFactory.getLogger(RemovedAccountCleanup.class);

    private final AccountService service;

    RemovedAccountCleanup(AccountService service) {
        this.service = service;
    }

    @Scheduled(cron = "${koshly.retention.cleanup-cron}", zone = "UTC")
    void run() {
        int deleted = service.deleteExpiredRemovals();
        if (deleted > 0) {
            log.info("Deleted {} removed account(s) past the retention period.", deleted);
        }
    }
}
