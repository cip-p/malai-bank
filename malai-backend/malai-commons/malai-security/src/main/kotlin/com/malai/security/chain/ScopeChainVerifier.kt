package com.malai.security.chain

import com.malai.security.audit.VerificationResult

// A verifier for a single chain-head scope. Each chain tenant (the audit plane's 'global'
// scope, the MDR's 'mdr' scope) supplies its own implementation, because a scope can only be
// verified with its own canonical projection -- the hash payload is reconstructed from the
// scope's rows, which no other scope can serialize. The two multi-chain surfaces (the
// verify-chain CLI and the hourly scheduled job) inject every ScopeChainVerifier bean present
// and walk them all, so adding a third chain tenant later needs no change to either surface.
interface ScopeChainVerifier {
    val scope: String

    fun verify(): VerificationResult
}
