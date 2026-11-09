package com.malai.security.chain

import com.malai.security.audit.VerificationResult
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

// Scheduled integrity check for EVERY hash-chain scope. Injects every [ScopeChainVerifier] bean
// present (the audit plane's 'global' scope, the MDR's 'mdr' scope, and any future tenant) and
// verifies each in turn under a single scheduler lock -- so adding a chain tenant needs no change
// here. Generalised from the audit-only job: a verifier by itself is on-demand forensics; this
// job turns detection into a standing control across all chains.
//
// Posture (per scope):
//   * On Ok      -- INFO log with row count + duration. Routine, low volume.
//   * On any non-Ok -- ERROR log prefixed "CHAIN INTEGRITY FAILURE" naming the scope and the
//                  specific failure subtype + seq. The SOC must alert on this string. A verify
//                  that throws is caught and logged the same way and does NOT abort the other
//                  scopes -- one broken chain must not blind the check on the rest, and a verify
//                  failure is a tamper signal, not an app-layer error to surface to callers.
//
// State: lastResults (keyed by scope) / lastRunAt are exposed for an actuator endpoint or direct
// test inspection. AtomicReference for a thread-safe cross-thread read.
//
// Config (unchanged from the audit-only job, hence the reused lock and 'audit.verify.*' keys):
//   * audit.verify.scheduler.enabled=true  (default) -- set false in dev/test to silence.
//   * audit.verify.scheduler.cron          -- defaults to top-of-hour ("0 0 * * * *").
//   * audit.verify.batch-size              -- consumed by each verifier.
@Component
@ConditionalOnProperty(name = ["audit.verify.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class ChainVerificationJob(
    private val verifiers: List<ScopeChainVerifier>,
    @Value("\${audit.verify.scheduler.cron:0 0 * * * *}") private val cron: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val _lastResults = AtomicReference<Map<String, VerificationResult>>(emptyMap())
    private val _lastRunAt = AtomicReference<Instant?>(null)

    val lastResults: Map<String, VerificationResult> get() = _lastResults.get()
    val lastRunAt: Instant? get() = _lastRunAt.get()

    fun lastResultForScope(scope: String): VerificationResult? = _lastResults.get()[scope]

    @Scheduled(cron = "\${audit.verify.scheduler.cron:0 0 * * * *}")
    @SchedulerLock(
        name = LOCK_NAME,
        lockAtMostFor = "\${audit.verify.lock.at-most-for:30m}",
        lockAtLeastFor = "\${audit.verify.lock.at-least-for:30m}",
    )
    fun runVerification() {
        val results = mutableMapOf<String, VerificationResult>()
        for (verifier in verifiers) {
            val start = Instant.now()
            val result = try {
                verifier.verify()
            } catch (e: Exception) {
                log.error("CHAIN INTEGRITY FAILURE: scope={} verifier threw {}: {}",
                    verifier.scope, e.javaClass.simpleName, e.message, e)
                continue
            }
            val duration = Duration.between(start, Instant.now())
            results[verifier.scope] = result
            logResult(verifier.scope, result, duration)
        }
        _lastResults.set(results)
        _lastRunAt.set(Instant.now())
    }

    private fun logResult(scope: String, result: VerificationResult, duration: Duration) {
        when (result) {
            is VerificationResult.Ok ->
                log.info("chain verified scope={} rows={} durationMs={}",
                    scope, result.rowsVerified, duration.toMillis())
            is VerificationResult.GapAtSeq ->
                log.error("CHAIN INTEGRITY FAILURE: scope={} GapAtSeq expected={} actual={} durationMs={}",
                    scope, result.expected, result.actual, duration.toMillis())
            is VerificationResult.PrevHashMismatch ->
                log.error("CHAIN INTEGRITY FAILURE: scope={} PrevHashMismatch seq={} durationMs={}",
                    scope, result.seq, duration.toMillis())
            is VerificationResult.CurrentHashMismatch ->
                log.error("CHAIN INTEGRITY FAILURE: scope={} CurrentHashMismatch seq={} durationMs={}",
                    scope, result.seq, duration.toMillis())
            VerificationResult.HeadMismatch ->
                log.error("CHAIN INTEGRITY FAILURE: scope={} HeadMismatch durationMs={}",
                    scope, duration.toMillis())
        }
    }

    companion object {
        const val LOCK_NAME = "audit-chain-verify"
    }
}
