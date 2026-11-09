package com.malai.security

import com.malai.commons.exception.MalaiCode.JWT_ACCESS_TOKEN_NOT_FOUND
import com.malai.commons.exception.UnauthorizedException
import com.malai.security.SecurityConfig.Companion.ACCESS_TOKEN_COOKIE
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import com.malai.security.SecurityConfig.Companion.CHALLENGE_SESSION_COOKIE
import com.malai.security.SecurityConfig.Companion.REFRESH_TOKEN_COOKIE
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.SET_COOKIE
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/**
 * Builds the HttpOnly+Secure+SameSite=Lax session cookies that auth-service writes on
 * login / refresh-token and clears on signout, and reads them back for endpoints that need
 * the raw token (signout → Cognito.GlobalSignOut, refresh-token → Cognito).
 *
 * Lives in `malai-security` because every service that bundles `JwtDecoderFilter` needs to
 * know about the same cookie names — auth-service writes them, account-service /
 * account-holder-service read them via the filter, and the filter and AuthCookies share the
 * same `readCookie` / `readBearerHeader` helpers (no duplication).
 *
 * Stateless — every method is a pure function of the Servlet objects it is given. The
 * `@Value` fields are read once at boot and never mutated. This matters because the
 * acceptance-test harness boots a single Spring context per JVM (see
 * `MalaiAcceptanceTestContext`) and shares it across every test, so any per-request mutable
 * state on a security-adjacent bean would leak between tests.
 *
 * Domain analysis: prod has the webapp at `app.malaibank.com` and the API at
 * `api.app.malaibank.com` (see `infra/malai/config/malai/prod/prod.tfvars`). Both share the
 * `malaibank.com` registrable domain, so SameSite=Lax keeps the cookie on every same-site
 * request from the webapp while blocking cross-site POSTs — sufficient CSRF protection
 * without needing an explicit token, given every state-changing endpoint is POST/PUT/DELETE.
 * The cookie is host-only (no `Domain` attribute), so it is only ever returned to
 * `api.app.malaibank.com`.
 *
 * Cookie lifetimes:
 *  - Both the **access cookie** and the **refresh cookie** take their Max-Age from the refresh
 *    window `malai.auth.cookies.refresh-max-age-seconds` (default 30 days). The access cookie
 *    deliberately outlives the short-lived JWT it carries so that, on a cold browser load with
 *    an expired token, `refresh-token` can still read the `sub` from the stale JWT to rotate
 *    the session (auth-service ADR-0002). This looks like a bug — a 30-day cookie around a
 *    1-hour token — but it is the design: authorization never came from cookie lifetime,
 *    `JwtDecoderFilter` validates the JWT's `exp` on every request regardless.
 *  - Re-set on every login / refresh-token call so an active session keeps rolling.
 */
@Component
class AuthCookies(
    @Value("\${malai.auth.cookies.secure:true}") private val secure: Boolean,
    @Value("\${malai.auth.cookies.refresh-max-age-seconds:2592000}") private val refreshMaxAgeSeconds: Long,
    @Value("\${malai.auth.cookies.challenge-max-age-seconds:600}") private val challengeMaxAgeSeconds: Long,
) {

    fun setLoginCookies(
        response: HttpServletResponse,
        accessToken: String,
        refreshToken: String,
    ) {
        response.addHeader(SET_COOKIE, accessCookie(accessToken, refreshMaxAgeSeconds).toString())
        response.addHeader(SET_COOKIE, refreshCookie(refreshToken, refreshMaxAgeSeconds).toString())
    }

    fun clearCookies(response: HttpServletResponse) {
        response.addHeader(SET_COOKIE, accessCookie(value = "", maxAge = 0).toString())
        response.addHeader(SET_COOKIE, refreshCookie(value = "", maxAge = 0).toString())
    }

    /**
     * Set the Challenge-session cookie on a login response. Max-Age comes from
     * `malai.auth.cookies.challenge-max-age-seconds` (default 10 min) — short-lived by design,
     * independent of any token. The value is opaque: a real Cognito session for a confirmed
     * User, fabricated for an unknown email.
     */
    fun setChallengeSessionCookie(response: HttpServletResponse, session: String) {
        response.addHeader(SET_COOKIE, challengeSessionCookie(session, challengeMaxAgeSeconds).toString())
    }

    fun clearChallengeSessionCookie(response: HttpServletResponse) {
        response.addHeader(SET_COOKIE, challengeSessionCookie(value = "", maxAge = 0).toString())
    }

    fun readChallengeSession(request: HttpServletRequest): String? =
        readCookie(request, CHALLENGE_SESSION_COOKIE)

    fun readAccessToken(request: HttpServletRequest): String =
        readAccessTokenOrNull(request)
            ?: throw UnauthorizedException(JWT_ACCESS_TOKEN_NOT_FOUND)

    fun readAccessTokenOrNull(request: HttpServletRequest): String? =
        readCookie(request, ACCESS_TOKEN_COOKIE)
            ?: readBearerHeader(request)

    fun readRefreshToken(request: HttpServletRequest): String? =
        readCookie(request, REFRESH_TOKEN_COOKIE)

    /**
     * Read a non-blank cookie value by name. Public so [JwtDecoderFilter] can compose it with
     * [readBearerHeader] for its own cookie-first / header-fallback precedence.
     */
    fun readCookie(request: HttpServletRequest, name: String): String? =
        request.cookies
            ?.firstOrNull { it.name == name }
            ?.value
            ?.takeIf { it.isNotBlank() }

    /**
     * Read the trimmed JWT from `Authorization: Bearer …`. Public for the same reason as
     * [readCookie].
     */
    fun readBearerHeader(request: HttpServletRequest): String? {
        val authHeader = request.getHeader(AUTHORIZATION_HEADER) ?: return null
        if (!authHeader.startsWith(BEARER_PREFIX)) return null
        return authHeader.removePrefix(BEARER_PREFIX).trim().takeIf { it.isNotBlank() }
    }

    private fun accessCookie(value: String, maxAge: Long): ResponseCookie =
        baseCookie(ACCESS_TOKEN_COOKIE, value, maxAge)

    private fun refreshCookie(value: String, maxAge: Long): ResponseCookie =
        baseCookie(REFRESH_TOKEN_COOKIE, value, maxAge)

    private fun challengeSessionCookie(value: String, maxAge: Long): ResponseCookie =
        baseCookie(CHALLENGE_SESSION_COOKIE, value, maxAge)

    private fun baseCookie(name: String, value: String, maxAge: Long): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
}
