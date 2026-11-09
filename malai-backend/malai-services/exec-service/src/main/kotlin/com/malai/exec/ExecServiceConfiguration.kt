package com.malai.exec

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * exec-service owns no tables and no money state, so — unlike every other service module — there is
 * no `@EnableJpaRepositories` and no `@EntityScan` here, and no entity or repository anywhere in
 * `com.malai.exec`. Every figure it serves is read over HTTP from the context that owns the
 * underlying facts (ADR-0011).
 *
 * It still carries a datasource, because [MalaiSecurityConfiguration] brings the shared idempotency
 * infrastructure, which is JPA-backed. That is platform plumbing every secured service inherits,
 * not a domain of this context's own.
 */
@Configuration
@ComponentScan(basePackages = ["com.malai.exec"])
@Import(
    MalaiSecurityConfiguration::class,
    MalaiClientConfiguration::class,
)
class ExecServiceConfiguration {

    @Bean
    fun execServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "exec-service",
        pathPrefixes = listOf(
            "/api/v1/exec",
        ),
    )

    /**
     * One thread per Metric, so every leg of a dashboard read starts immediately and their
     * timeouts overlap rather than queue. A leg abandoned on timeout keeps its thread until the
     * underlying HTTP read gives up, so the pool is sized above the metric count.
     */
    @Bean(destroyMethod = "shutdownNow")
    fun execMetricExecutor(
        @Value("\${malai.exec.metric-threads}") threads: Int,
    ): ExecutorService = Executors.newFixedThreadPool(threads) { runnable ->
        Thread(runnable, "exec-metric").apply { isDaemon = true }
    }
}
