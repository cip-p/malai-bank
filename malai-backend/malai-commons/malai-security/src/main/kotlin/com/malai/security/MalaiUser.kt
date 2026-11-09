package com.malai.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.*

interface MalaiUser {
    val roles: Set<MalaiRole>

    /**
     * Spring Security authorities derived from `roles`. Empty when the JWT carried
     * no recognised `cognito:groups` claim — fail-closed default; any
     * `@PreAuthorize`-gated endpoint will return 403.
     */
    val authorities: List<GrantedAuthority>
        get() = roles.map { SimpleGrantedAuthority(it.authority) }
}

data class MalaiUserForUserIdOnly(
    val userId: UUID,
    override val roles: Set<MalaiRole> = emptySet(),
) : MalaiUser

data class MalaiUserWithAccountHolderId(
    val userId: UUID,
    val accountHolderId: UUID,
    override val roles: Set<MalaiRole> = emptySet(),
) : MalaiUser
