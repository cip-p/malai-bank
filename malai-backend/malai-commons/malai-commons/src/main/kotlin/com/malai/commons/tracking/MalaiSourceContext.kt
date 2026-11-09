package com.malai.commons.tracking

/**
 * Thread-local holder for the logical malai-service the current thread is acting on behalf of.
 *
 * Read by AbstractMalaiClient and MalaiSourceAwsSdkInterceptorConfig when tagging outbound HTTP /
 * AWS SDK calls with `X-Malai-Source`. When multiple service modules are bundled into a single
 * JVM (the malai-api topology used in dev/prod and in the e2e stack), this lets each module's
 * outbound calls still be attributed to that module — instead of collapsing onto
 * `spring.application.name`, which is then always "malai-api".
 *
 * Populated by [MalaiSourceServletFilterConfig] on inbound HTTP based on URL prefix matching
 * against [MalaiSourceRoutes] beans declared by each service module. When no context is set
 * (out-of-band scheduled tasks, raw startup, …) callers should fall back to
 * `spring.application.name`.
 */
object MalaiSourceContext : ThreadLocalContext<MalaiSource>("MalaiSource", { MalaiSource("unknown") })

data class MalaiSource(val name: String)
