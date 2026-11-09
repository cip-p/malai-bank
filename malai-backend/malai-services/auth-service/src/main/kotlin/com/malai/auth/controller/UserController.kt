package com.malai.auth.controller

import com.malai.security.AuthCookies
import com.malai.auth.model.UpdateUserRequest
import com.malai.auth.model.UserResponse
import com.malai.auth.service.UserService
import com.malai.security.MalaiUserForUserIdOnly
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/user")
class UserController(
    private val service: UserService,
    private val authCookies: AuthCookies,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @GetMapping(value = [""], produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPPORT_AGENT', 'ADMIN', 'MANAGER')")
    fun getUser(
        request: HttpServletRequest,
        @AuthenticationPrincipal user: MalaiUserForUserIdOnly,
    ): UserResponse {

        log.info("received request to get user {}", user.userId)

        val accessToken = authCookies.readAccessToken(request)
        return service.getUser(accessToken, user.userId, user.roles)
    }

    @PutMapping(value = [""], produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun updateUser(
        @RequestBody updateUser: UpdateUserRequest,
        request: HttpServletRequest,
        @AuthenticationPrincipal user: MalaiUserForUserIdOnly,
    ): UserResponse {

        log.info("received request to update user {}", user.userId)

        val accessToken = authCookies.readAccessToken(request)
        return service.updateUser(accessToken, updateUser, user.userId, user.roles)
    }
}
