package com.malai.auth.acceptance

import com.malai.security.chain.ChainVerificationJob
import com.malai.security.idempotency.IdempotencyCleanupJob
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

class BackgroundSchedulesTest : AuthAcceptanceTest() {

    @Test
    fun `the chain verification cron is disarmed so no scheduler thread races a direct invocation`() = malaiAcceptanceTest {
        scheduledMethodsOf(ChainVerificationJob::class.java).shouldBeEmpty()
    }

    @Test
    fun `the idempotency cleanup cron is disarmed so no scheduler thread races a direct invocation`() = malaiAcceptanceTest {
        scheduledMethodsOf(IdempotencyCleanupJob::class.java).shouldBeEmpty()
    }

    private fun scheduledMethodsOf(job: Class<*>): List<String> =
        getBean(ScheduledAnnotationBeanPostProcessor::class.java)
            .scheduledTasks
            .map { it.task.runnable.toString() }
            .filter { it.startsWith(job.name) }
}
