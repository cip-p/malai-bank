package com.malai.auth.service

import com.malai.auth.support.SpringTest
import com.malai.security.chain.ChainVerificationJob
import com.malai.security.idempotency.IdempotencyCleanupJob
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

@SpringTest
class SpringContextBackgroundSchedulesTest {

    @Autowired
    private lateinit var scheduledAnnotationBeanPostProcessor: ScheduledAnnotationBeanPostProcessor

    @Test
    fun `the chain verification cron is disarmed in the SpringTest context too`() {
        scheduledMethodsOf(ChainVerificationJob::class.java).shouldBeEmpty()
    }

    @Test
    fun `the idempotency cleanup cron is disarmed in the SpringTest context too`() {
        scheduledMethodsOf(IdempotencyCleanupJob::class.java).shouldBeEmpty()
    }

    private fun scheduledMethodsOf(job: Class<*>): List<String> =
        scheduledAnnotationBeanPostProcessor
            .scheduledTasks
            .map { it.task.runnable.toString() }
            .filter { it.startsWith(job.name) }
}
