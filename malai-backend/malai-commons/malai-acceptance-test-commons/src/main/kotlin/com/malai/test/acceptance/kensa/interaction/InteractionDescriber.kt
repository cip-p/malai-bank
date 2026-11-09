package com.malai.test.acceptance.kensa.interaction

import com.malai.test.acceptance.kensa.HttpClassifier

/**
 * Maps an HTTP method + URI + headers to a human-readable description (e.g.
 * "Login", "Authenticate User", "Get Account Holder Name") using the shared
 * [HttpClassifier]. The WireMock stub paths for downstream services include a
 * service prefix that the classifier's patterns don't expect (they start at
 * `/api/v1/...` or `/` for identity-provider SDK calls), so we strip the prefix
 * before classifying.
 */
object InteractionDescriber {

    private val servicePrefixes = listOf(
        "/account-holder-service",
        "/account-service",
        "/ledger-service",
        "/payments-service",
        "/transaction-feed-service",
        "/savings-goal-service",
        "/auth-service",
        "/card-service",
        "/payee-service",
        "/payment-local-service",
        "/direct-debit-service",
        "/insights-service",
        "/mdr-service",
        "/exec-service",
        "/round-up-service",
        "/fps-gateway",
        "/bacs-gateway",
        "/swift-gateway",
        "/malai-stub",
        "/oauth2-service",
        "/identity-provider",
        "/cognito",
    )

    fun describe(method: String, uri: String, headers: Map<String, String> = emptyMap()): String {
        val path = uri.substringBefore('?')
        val stripped = stripServicePrefix(path)
        val description = HttpClassifier.classifies(method, stripped, headers)
        return if (description == "Unknown") "$method $path" else description
    }

    private fun stripServicePrefix(path: String): String {
        for (prefix in servicePrefixes) {
            if (path.startsWith("$prefix/")) return path.removePrefix(prefix)
            if (path == prefix) return "/"
        }
        return path
    }
}
