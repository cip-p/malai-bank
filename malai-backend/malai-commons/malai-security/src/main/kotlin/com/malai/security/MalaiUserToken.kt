package com.malai.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import java.util.*

class MalaiUserToken(
    private val malaiUser: MalaiUser,
    authorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

    init {
        // Plan 02-16 BACK-06 fix: mark the token authenticated so
        // `@PreAuthorize("isAuthenticated()")` succeeds. JwtDecoderFilter
        // has already verified the JWT signature + claims via JwtDecoder;
        // the token is post-authentication. Without this, the role-based
        // `hasRole(...)` checks still work (they inspect the authorities
        // directly), but `isAuthenticated()` evaluates to false and
        // signout returns 403.
        isAuthenticated = true
    }

    override fun getCredentials(): Any {
        return Optional.empty<Any>()
    }

    override fun getPrincipal(): Any {
        return malaiUser
    }
}
