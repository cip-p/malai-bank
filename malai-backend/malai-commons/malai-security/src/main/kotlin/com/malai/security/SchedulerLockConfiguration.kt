package com.malai.security

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

// ShedLock wiring for @Scheduled jobs across horizontally-scaled ECS tasks.
// Without this, every malai-api instance fires every @Scheduled cron, which
// is wasteful (N concurrent verifies / N concurrent idempotency cleanups) and
// produces N copies of every "AUDIT CHAIN INTEGRITY FAILURE" log line so SOC
// alerts dedupe to ×N instead of ×1.
//
// defaultLockAtMostFor is a safety net: if a JVM crashes mid-job, the lock
// auto-releases after this period so a healthy instance can take over. Per-job
// @SchedulerLock(lockAtMostFor=...) overrides this when the job's expected
// runtime warrants it (e.g. the audit verifier scales with chain size).
//
// usingDbTime() makes ShedLock read lock_until / locked_at from the Postgres
// clock rather than the JVM clock, which avoids small clock-skew races between
// instances during lock acquisition.
//
// Lock-table lookups happen through the JdbcTemplate -> existing app DataSource
// (malai_app role); V11's ALTER DEFAULT PRIVILEGES grants SELECT/INSERT/UPDATE
// on the V13-created shedlock table automatically.
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
class SchedulerLockConfiguration {

    @Bean
    fun lockProvider(jdbcTemplate: JdbcTemplate): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build()
        )
}
