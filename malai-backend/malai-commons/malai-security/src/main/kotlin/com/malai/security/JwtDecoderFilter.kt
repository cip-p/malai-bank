package com.malai.security

import com.malai.commons.exception.*
import com.malai.commons.exception.MalaiCode.JWT_MISSING_IN_HEADER
import com.malai.commons.exception.MalaiCode.JWT_UNKNOWN_EXCEPTION
import com.malai.commons.utlis.LoggingUtils.Companion.addAccountHolderToLoggingContext
import com.malai.commons.utlis.LoggingUtils.Companion.addUserToLoggingContext
import com.malai.commons.utlis.LoggingUtils.Companion.clearLoggingContext
import com.malai.security.SecurityConfig.Companion.ACCESS_TOKEN_COOKIE
import com.malai.security.SecurityConfig.Companion.ACCOUNT_HOLDER_ID_HEADER
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.GenericFilterBean
import java.util.*

class JwtDecoderFilter(
    private val jwtDecoder: JwtDecoder,
    private val authCookies: AuthCookies,
) : GenericFilterBean() {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private fun handleRequest(request: ServletRequest) {
        val httpRequest = request as HttpServletRequest

        // Dual-mode token extraction:
        //   1. HttpOnly `access_token` cookie set by AuthController (the webapp's path).
        //   2. `Authorization: Bearer <jwt>` header (mobile / CLI / API Gateway HTTP API v2's
        //      JWT authorizer, which pre-validates this header before the request reaches us).
        // Cookie wins when both are present so a webapp browser tab can't be tricked into
        // sending a stale Authorization header alongside its session cookie.
        val token = authCookies.readCookie(httpRequest, ACCESS_TOKEN_COOKIE)
            ?: authCookies.readBearerHeader(httpRequest)
            ?: run {
                log.error("No access token in $ACCESS_TOKEN_COOKIE cookie or $AUTHORIZATION_HEADER header")
                throw JwtFilterException(JWT_MISSING_IN_HEADER)
            }
        val decodedJwt = jwtDecoder.decodeAndVerify(token)
        val userId = decodedJwt.subject

//            verifyUserIsActive(userId)

        val id = UUID.fromString(userId)
        val accountHolderIdHeader = httpRequest.getHeader(ACCOUNT_HOLDER_ID_HEADER)

        // Phase 2 D-2.10: map cognito:groups → MalaiRole. Cognito omits the claim
        // entirely when a user has no group memberships, so a missing claim returns
        // an empty list naturally (NullClaim.asList → null, then ?: emptyList()).
        // Fail-closed if no recognised group → @PreAuthorize-gated endpoints 403.
        // Any genuine decode/shape failure bubbles to doFilter's catch, which logs
        // and surfaces a 500 — the right outcome for "JWT we don't understand."
        val groups: List<String> =
            decodedJwt.getClaim("cognito:groups").asList(String::class.java) ?: emptyList()
        val roles: Set<MalaiRole> = groups.mapNotNull(MalaiRole::fromGroup).toSet()
        if (groups.isNotEmpty() && roles.isEmpty()) {
            log.warn(
                "JWT for user {} carried cognito:groups {} but no recognised role; authenticating with empty role set",
                id, groups,
            )
        }

        val malaiUser = if (accountHolderIdHeader == null)
            MalaiUserForUserIdOnly(id, roles)
        else {
            val accountHolderId = UUID.fromString(accountHolderIdHeader)
            addAccountHolderToLoggingContext(accountHolderId)
            MalaiUserWithAccountHolderId(id, accountHolderId, roles)
        }

        log.debug("received valid JWT token for malai user {}", malaiUser)

        SecurityContextHolder.getContext().authentication = MalaiUserToken(malaiUser, malaiUser.authorities)
        addUserToLoggingContext(id)
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        try {
            clearLoggingContext();

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
                    resp.writer.print(
                        """
                            {"message": "$errorCode"}
                        """
                    )
                }

                else -> {
                    log.error("Unhandled JWT filter exception", ex)
                    val errorCode = if (ex is ExceptionWithErrorCode) ex.malaiCode else JWT_UNKNOWN_EXCEPTION
                    throw InternalServerErrorException(errorCode)
                }
            }
        } finally {
            clearLoggingContext();
        }
    }

    private fun verifyUserIsActive(userId: String) {
        // TODO: check if the user is active; if not, return 401
        // it might be impossible for this to happen, but add an extra security check

        // pros for not doing this:
        //      it will add overhead, having to go to cognito for every request
        //      when a user is deactivated, the token will still be valid until it expires for a short period of time
        //      when a user is deactivated, we should remove all tokens for the user, so will not be able to use any token
    }

}
