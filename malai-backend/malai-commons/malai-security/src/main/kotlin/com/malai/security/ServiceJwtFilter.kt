package com.malai.security

import com.malai.commons.exception.*
import com.malai.commons.exception.MalaiCode.JWT_MISSING_IN_HEADER
import com.malai.commons.exception.MalaiCode.JWT_UNKNOWN_EXCEPTION
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.GenericFilterBean

/**
 * Validates an incoming Cognito service (client_credentials) JWT and enforces a required scope.
 *
 * Mounted on the internal-services filter chain (see [SecurityConfig]). The token reuses
 * the same Cognito user pool / JWKS as user tokens, so signature + issuer + `token_use=access`
 * checks all flow through [JwtDecoder]. The new bit is the `scope` claim check.
 *
 * On success a [ServicePrincipal] is placed in the security context. Internal controllers
 * deliberately don't take a `@AuthenticationPrincipal MalaiUser`: any user/account-holder ids
 * must be passed explicitly in the request body or path.
 */
class ServiceJwtFilter(
    private val jwtDecoder: JwtDecoder,
    private val requiredScopes: Set<String>,
) : GenericFilterBean() {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private fun handleRequest(request: ServletRequest) {
        val httpRequest = request as HttpServletRequest

        val authHeader = httpRequest.getHeader(AUTHORIZATION_HEADER)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.error("No Bearer token in Authorization header (service path)")
            throw JwtFilterException(JWT_MISSING_IN_HEADER)
        }

        val token = authHeader.removePrefix(BEARER_PREFIX).trim()
        val decoded = jwtDecoder.decodeAndVerify(token)

        val clientId = decoded.getClaim("client_id").asString()
            ?: throw InvalidJwtTokenException(MalaiCode.JWT_MISSING_USER_ID)

        val scopes = (decoded.getClaim("scope").asString() ?: "")
            .split(" ")
            .filter { it.isNotBlank() }
            .toSet()

        val missing = requiredScopes - scopes
        if (missing.isNotEmpty()) {
            log.error("Service JWT missing required scopes: $missing (clientId=$clientId)")
            throw JwtFilterException(MalaiCode.JWT_CANT_VERIFY)
        }

        log.debug("authenticated service request, clientId={}, scopes={}", clientId, scopes)
        val authorities = scopes.map { SimpleGrantedAuthority("SCOPE_$it") }
        SecurityContextHolder.getContext().authentication =
            ServiceJwtToken(ServicePrincipal(clientId, scopes), authorities)
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        try {
            handleRequest(request)
            chain.doFilter(request, response)
        } catch (ex: Exception) {
            when (ex) {
                is JwtFilterException,
                is InvalidJwtTokenException,
                is JwtProviderException -> {
                    val resp = response as HttpServletResponse
                    resp.resetBuffer()
                    resp.status = HttpServletResponse.SC_UNAUTHORIZED
                    val errorCode =
                        if (ex is ExceptionWithErrorCode) ex.malaiCode.code else JWT_UNKNOWN_EXCEPTION.code
                    resp.writer.print("""{"message": "$errorCode"}""")
                }

                else -> {
                    log.error("Unhandled service-JWT filter exception", ex)
                    val errorCode = if (ex is ExceptionWithErrorCode) ex.malaiCode else JWT_UNKNOWN_EXCEPTION
                    throw InternalServerErrorException(errorCode)
                }
            }
        }
    }
}
