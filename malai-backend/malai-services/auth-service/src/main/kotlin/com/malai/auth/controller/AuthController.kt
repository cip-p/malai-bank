package com.malai.auth.controller

import com.malai.security.AuthCookies
import com.malai.auth.model.*
import com.malai.auth.service.AuthService
import com.malai.commons.exception.HttpExceptionResponse
import com.malai.commons.exception.MalaiCode
import com.malai.commons.exception.MalaiCode.JWT_ACCESS_TOKEN_NOT_FOUND
import com.malai.commons.exception.MalaiCode.JWT_REFRESH_TOKEN_NOT_FOUND
import com.malai.security.MalaiUserForUserIdOnly
import com.malai.security.StaleTokenIdentityReader
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val service: AuthService,
    private val authCookies: AuthCookies,
    private val staleTokenIdentityReader: StaleTokenIdentityReader,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PostMapping(value = ["register"], produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    fun register(@Valid @RequestBody registerRequest: RegisterRequest, response: HttpServletResponse): CodeSentResponse {

        log.debug("received request to register user")

        val challengeSession = service.register(registerRequest)
        authCookies.setChallengeSessionCookie(response, challengeSession)

        log.debug("register user done")

        return CodeSentResponse(CodeSentResponse.CONFIRMATION_CODE_SENT)
    }

    @PostMapping(value = ["verify-code"], produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    fun verifyCode(
        @Valid @RequestBody verifyCodeRequest: VerifyCodeRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): LoginResponse {

        log.debug("received request to verify code")

        val challengeSession = authCookies.readChallengeSession(request) ?: verifyCodeRequest.challengeSession
        val loginResponse = service.verifyCode(verifyCodeRequest, challengeSession)
        authCookies.setLoginCookies(
            response,
            accessToken = loginResponse.accessToken,
            refreshToken = loginResponse.refreshToken,
        )
        authCookies.clearChallengeSessionCookie(response)

        log.debug("verify code done")

        return loginResponse
    }

    @PostMapping(value = ["login"], produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    fun login(@Valid @RequestBody loginRequest: LoginRequest, response: HttpServletResponse): CodeSentResponse {

        log.debug("received request to login user")

        val challengeSession = service.login(loginRequest)
        authCookies.setChallengeSessionCookie(response, challengeSession)

        log.debug("login user done")

        return CodeSentResponse(CodeSentResponse.CONFIRMATION_CODE_SENT)
    }

    @PostMapping(value = ["refresh-token"], produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    fun refreshToken(
        @RequestBody(required = false) refreshTokenRequest: RefreshTokenRequest?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> {

        log.debug("received request to refresh token")

        val effectiveRefreshToken = authCookies.readRefreshToken(request)
            ?: refreshTokenRequest?.refreshToken
            ?: return unauthorized(JWT_REFRESH_TOKEN_NOT_FOUND)

        val effectiveUserId = refreshTokenRequest?.userId
            ?: authCookies.readAccessTokenOrNull(request)?.let { staleTokenIdentityReader.readSubject(it) }
            ?: return unauthorized(JWT_ACCESS_TOKEN_NOT_FOUND)

        val refreshTokenResponse = service.refreshToken(effectiveUserId, effectiveRefreshToken)
        authCookies.setLoginCookies(
            response,
            accessToken = refreshTokenResponse.accessToken,
            refreshToken = refreshTokenResponse.refreshToken,
        )

        log.debug("refresh token done for user id $effectiveUserId")

        return ResponseEntity.ok(refreshTokenResponse)
    }

    private fun unauthorized(code: MalaiCode): ResponseEntity<HttpExceptionResponse> =
        ResponseEntity.status(UNAUTHORIZED).body(HttpExceptionResponse(code.code.toString()))

    @PostMapping(value = ["signout"], produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("isAuthenticated()")
    fun signOut(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @AuthenticationPrincipal user: MalaiUserForUserIdOnly,
    ) {

        log.debug("received request to signout for userId {}", user.userId.toString())

        val accessToken = authCookies.readAccessToken(request)
        service.signOut(accessToken, user.userId)
        authCookies.clearCookies(response)

        log.debug("signout done")
    }
}
