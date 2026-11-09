package com.malai.security.idempotency

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class IdempotencyCleanupJob(private val service: IdempotencyService) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Daily 04:00 — deletes idempotency rows whose `expires_at` has passed.
     *  @SchedulerLock ensures only one malai-api instance runs the cleanup
     *  per cron tick across horizontally-scaled deployments.
     */
    @Scheduled(cron = "\${idempotency.cleanup.cron:0 0 4 * * *}")
    @SchedulerLock(
        name = LOCK_NAME,
        lockAtMostFor = "\${idempotency.cleanup.lock.at-most-for:10m}",
        lockAtLeastFor = "\${idempotency.cleanup.lock.at-least-for:5m}",
    )
    fun runCleanup() {
        val deleted = service.cleanup()
        if (deleted > 0) {
            log.info("Idempotency cleanup deleted {} expired keys", deleted)
        }
    }

    companion object {
        const val LOCK_NAME = "idempotency-cleanup"
    }
}
