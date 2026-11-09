package com.malai.directdebit.scheduler

import com.malai.commons.TimeService
import com.malai.directdebit.repository.DirectDebitsRepository
import com.malai.directdebit.service.DirectDebitCollectionRequestService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * The mandate clock. A per-minute, ShedLock-coordinated scan of ACTIVE mandates whose next
 * collection moment has arrived; each due occurrence is requested exactly once through
 * [DirectDebitCollectionRequestService], which asks the stub to originate the collection so it
 * arrives through the gateway and queue like every other inbound payment. ShedLock keeps the scan
 * single-flight across scaled-out malai-api instances (the ArchUnit rule enforces the lock
 * annotation); `lockAtMostFor` is the crash safety-net and stays well under the one-minute cadence
 * so a healthy instance always picks up the next tick. Dueness is read from the injected time
 * service, never the wall clock.
 *
 * One mandate failing (a transient stub error) is logged and skipped, leaving its schedule
 * unadvanced so the next run retries it; the deterministic end-to-end id makes that retry safe.
 */
@Component
class DirectDebitCollectionRequester(
    private val repository: DirectDebitsRepository,
    private val requestService: DirectDebitCollectionRequestService,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${direct-debit.collection-requester.cron:0 * * * * *}")
    @SchedulerLock(
        name = LOCK_NAME,
        lockAtMostFor = "\${direct-debit.collection-requester.lock.at-most-for:50s}",
    )
    fun runOnSchedule() = requestDueCollections()

    fun requestDueCollections() {
        val now = timeService.now()
        val due = repository.findDueActiveMandates(now)
        if (due.isEmpty()) return

        log.info("direct-debit collection requester: {} due mandate(s) as of {}", due.size, now)
        due.forEach { mandate ->
            try {
                requestService.requestCollection(mandate, now)
            } catch (ex: Exception) {
                log.error(
                    "direct-debit collection request failed, leaving schedule unadvanced to retry next run: directDebitId={}",
                    mandate.directDebitId, ex,
                )
            }
        }
    }

    companion object {
        const val LOCK_NAME = "direct-debit-collection-requester"
    }
}
