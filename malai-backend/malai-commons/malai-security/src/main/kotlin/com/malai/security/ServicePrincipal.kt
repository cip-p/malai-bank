package com.malai.security

/**
 * The authenticated party for a request that arrived on a service-to-service path
 * (under `/api/v1/internal/`). Carries no user identity — the calling service is expected
 * to pass any user/account-holder ids in the request body or path explicitly.
 */
data class ServicePrincipal(
    /** Cognito service-token client id (the `client_id` claim on the JWT). */
    val clientId: String,
    /** Granted scopes — e.g. `malai-internal/onboarding.write`. */
    val scopes: Set<String>,
)
