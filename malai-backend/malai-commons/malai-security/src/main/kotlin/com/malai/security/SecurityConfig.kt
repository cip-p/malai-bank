package com.malai.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.util.matcher.RegexRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

// `@Bean fun internalServicesFilterChain(http: HttpSecurity)` and
// `securedPathsFilterChain(http: HttpSecurity)` below both need an `HttpSecurity`
// bean, which Spring Boot only auto-configures in a SERVLET context. Without
// the gate, every non-web consumer of `MalaiSecurityConfiguration` (the
// `malai-seeder` CLI today, future scheduled jobs / batch apps tomorrow)
// fails to start with: "Parameter 0 of method internalServicesFilterChain in
// com.malai.security.SecurityConfig required a bean of type 'HttpSecurity'
// that could not be found." Companion-object constants (cookie names,
// INTERNAL_PATH_REGEX, ...) remain reachable regardless — they live on the
// class, not its bean.
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig(
    @Value("\${malai.secured.paths}") val securedPaths: Array<String>,
    @Value("\${malai.cors}") val allowedOrigins: Array<String>,
    private val jwtDecoder: JwtDecoder,
    private val authCookies: AuthCookies,
) {

    companion object {
        // Standard OAuth2 bearer header: `Authorization: Bearer <jwt>`.
        // The Cognito access_token is what clients send here — it carries
        // `sub` (user id), is what `Cognito.signOut` expects, and is what
        // API Gateway HTTP API v2's JWT authorizer pre-validates before the
        // request reaches us.
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "

        // HttpOnly+Secure+SameSite=Lax cookies set by AuthController on login /
        // refresh-token / signout. The webapp uses these in preference to
        // localStorage for its session. The access cookie carries the Cognito
        // access JWT (same value the Authorization header carries); the refresh
        // cookie carries the Cognito refresh token. JwtDecoderFilter prefers
        // the cookie over the Authorization header when both are present and
        // falls back to the header so existing mobile/CLI clients keep working.
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val REFRESH_TOKEN_COOKIE = "refresh_token"

        // Short-lived HttpOnly cookie binding a passwordless Login attempt to the
        // browser that started it (the Challenge session). Set by AuthController on
        // every login response — a real Cognito session for a confirmed User,
        // fabricated same-shape content for an unknown email so its presence reveals
        // nothing — and cleared on successful Verification. Carries no JWT and is never
        // read by JwtDecoderFilter; verify-code reads it cookie-first with a
        // request-body field as the non-browser fallback.
        const val CHALLENGE_SESSION_COOKIE = "challenge_session"

        // Selected account holder for the current request — sent by the client
        // on every authenticated request (no server-side opaque-token mapping).
        const val ACCOUNT_HOLDER_ID_HEADER = "MAL-Account-Holder-Id"

        /**
         * Regex matching every service-to-service endpoint. Version-agnostic so v1 and a
         * future v2 internal API can co-exist without changing the security wiring. Requests
         * matching this pattern are authenticated by [ServiceJwtFilter] using a Cognito
         * service (client_credentials) JWT and never expose a user principal.
         * See `malai-docs/webapp-flows/onboarding.md`.
         */
        const val INTERNAL_PATH_REGEX = "/api/v\\d+/internal/.*"
        const val INTERNAL_ONBOARDING_WRITE_SCOPE = "malai-internal/onboarding.write"
    }

    /**
     * Filter chain for service-to-service requests. Higher precedence than the user chain so
     * internal requests never fall through to the user-JWT filter.
     */
    @Bean
    @Order(1)
    fun internalServicesFilterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        httpSecurity
            .cors(withDefaults())
            .csrf { csrf -> csrf.disable() }
            .securityMatcher(RegexRequestMatcher.regexMatcher(INTERNAL_PATH_REGEX))
            .addFilterAfter(
                ServiceJwtFilter(jwtDecoder, setOf(INTERNAL_ONBOARDING_WRITE_SCOPE)),
                BasicAuthenticationFilter::class.java,
            )
        return httpSecurity.build()
    }

    @Bean
    @Order(2)
    fun securedPathsFilterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        httpSecurity
            .cors(withDefaults())
            .csrf { csrf -> csrf.disable() }
            .securityMatchers { matchers -> matchers.requestMatchers(*securedPaths) }
            .addFilterAfter(
                JwtDecoderFilter(jwtDecoder, authCookies), BasicAuthenticationFilter::class.java
            )
        return httpSecurity.build()
    }

    @Bean
    fun corsFilter(): CorsFilter {
        val config = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins.toList()
            allowedMethods = listOf("*")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
        return CorsFilter(source)
    }
}
