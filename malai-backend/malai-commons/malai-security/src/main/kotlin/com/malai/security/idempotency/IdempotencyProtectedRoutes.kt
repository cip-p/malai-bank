package com.malai.security.idempotency

data class IdempotencyProtectedRoutes(
    val serviceName: String,
    val paths: List<ProtectedPath>,
) {
    data class ProtectedPath(val method: String, val pathRegex: String)
}
