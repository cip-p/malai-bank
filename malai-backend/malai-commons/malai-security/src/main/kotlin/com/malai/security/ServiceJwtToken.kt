package com.malai.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import java.util.*

class ServiceJwtToken(
    private val principal: ServicePrincipal,
    authorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = Optional.empty<Any>()
    override fun getPrincipal(): Any = principal
}
