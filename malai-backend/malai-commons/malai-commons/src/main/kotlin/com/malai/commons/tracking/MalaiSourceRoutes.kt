package com.malai.commons.tracking

/**
 * Declarative description of which URL prefixes a logical malai-service owns. Each service
 * module contributes one (via `@Bean fun fooServiceSource()`); [MalaiSourceServletFilterConfig]
 * collects them and, for each inbound request, populates [MalaiSourceContext] with the
 * matching [serviceName] for the duration of the request — by longest-prefix wins.
 *
 * The [serviceName] values are observable: they end up on the wire as the `X-Malai-Source`
 * header of every outbound call made while a request to one of these prefixes is in flight,
 * and they drive party attribution on the Kensa sequence diagram in acceptance / e2e tests.
 * Keep them stable and meaningful — they mirror what `spring.application.name` used to be
 * when each service deployed on its own.
 */
data class MalaiSourceRoutes(
    val serviceName: String,
    val pathPrefixes: List<String>,
)
