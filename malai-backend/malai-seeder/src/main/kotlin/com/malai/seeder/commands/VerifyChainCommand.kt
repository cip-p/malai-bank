package com.malai.seeder.commands

import com.malai.security.audit.VerificationResult
import com.malai.security.chain.ScopeChainVerifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.util.concurrent.Callable

/**
 * Verifies EVERY hash-chain scope end-to-end and translates the results into a non-zero exit code
 * if any scope is tampered. Injects every [ScopeChainVerifier] bean present (the audit plane's
 * 'global' scope, the MDR's 'mdr' scope, and any future tenant), so a new chain is covered here
 * without a code change. Run after a fresh `--seed` to assert the chains are consistent
 * end-to-end; CI can wire this in as a deployment gate.
 */
@Component
@Command(name = "verify-chain", description = ["Verify every hash chain end-to-end. Exit 0 when all scopes are Ok, non-zero on any tamper."])
class VerifyChainCommand(
    private val verifiers: List<ScopeChainVerifier>,
) : Callable<Int> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun call(): Int {
        var exitCode = 0
        for (verifier in verifiers) {
            if (!verifyScope(verifier)) exitCode = 1
        }
        return exitCode
    }

    private fun verifyScope(verifier: ScopeChainVerifier): Boolean {
        val scope = verifier.scope
        return when (val result = verifier.verify()) {
            is VerificationResult.Ok -> {
                log.info("verify-chain: scope={} Ok rowsVerified={}", scope, result.rowsVerified)
                println("scope=$scope VerificationResult.Ok(rowsVerified=${result.rowsVerified})")
                true
            }
            is VerificationResult.GapAtSeq -> {
                log.error("verify-chain: scope={} GapAtSeq expected={} actual={}", scope, result.expected, result.actual)
                println("scope=$scope VerificationResult.GapAtSeq(expected=${result.expected}, actual=${result.actual})")
                false
            }
            is VerificationResult.PrevHashMismatch -> {
                log.error("verify-chain: scope={} PrevHashMismatch at seq={}", scope, result.seq)
                println("scope=$scope VerificationResult.PrevHashMismatch(seq=${result.seq})")
                false
            }
            is VerificationResult.CurrentHashMismatch -> {
                log.error("verify-chain: scope={} CurrentHashMismatch at seq={}", scope, result.seq)
                println("scope=$scope VerificationResult.CurrentHashMismatch(seq=${result.seq})")
                false
            }
            VerificationResult.HeadMismatch -> {
                log.error("verify-chain: scope={} HeadMismatch — chain_head pointer does not match the last row", scope)
                println("scope=$scope VerificationResult.HeadMismatch")
                false
            }
        }
    }
}
