package com.malai.standingorder.scheduler

import com.malai.commons.TimeService
import com.malai.standingorder.repository.StandingOrdersRepository
import com.malai.standingorder.service.StandingOrderExecutionService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * The clock. A per-minute, ShedLock-coordinated scan of ACTIVE standing orders whose next
 * payment moment has arrived; each due occurrence is executed exactly once through
 * [StandingOrderExecutionService]. ShedLock keeps the scan single-flight across scaled-out
 * malai-api instances (the ArchUnit rule enforces the lock annotation); `lockAtMostFor` is the
 * crash safety-net and stays well under the one-minute cadence so a healthy instance always
 * picks up the next tick. Dueness is read from the injected time service, never the wall clock.
 *
 * One order failing (a transient payments error) is logged and skipped, leaving its schedule
 * unadvanced so the next run retries it; the deterministic end-to-end id makes that retry safe.
 */
@Component
class StandingOrderExecutor(
    private val repository: StandingOrdersRepository,
    private val executionService: StandingOrderExecutionService,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${standing-order.executor.cron:0 * * * * *}")
    @SchedulerLock(
        name = LOCK_NAME,
        lockAtMostFor = "\${standing-order.executor.lock.at-most-for:50s}",
    )
    fun runOnSchedule() = executeDueOrders()

    fun executeDueOrders() {
        val now = timeService.now()
        val due = repository.findDueActiveOrders(now)
        if (due.isEmpty()) return

        log.info("standing-order executor: {} due order(s) as of {}", due.size, now)
        due.forEach { order ->
            try {
                executionService.execute(order, now)
            } catch (ex: Exception) {
                log.error(
                    "standing-order execution failed, leaving schedule unadvanced to retry next run: standingOrderId={}",
                    order.standingOrderId, ex,
                )
            }
        }
    }

    companion object {
        const val LOCK_NAME = "standing-order-executor"
    }
}
