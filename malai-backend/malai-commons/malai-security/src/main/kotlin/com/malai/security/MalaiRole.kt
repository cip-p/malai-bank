package com.malai.security

/**
 * RBAC roles for Malai. Source of truth: Cognito user groups (D-2.10).
 * The `cognitoGroup` field matches the lowercase group name configured in
 * cognito-malai-api/main.tf; the `authority` field is the Spring Security
 * GrantedAuthority string used by `@PreAuthorize("hasRole('CUSTOMER')")`.
 */
enum class MalaiRole(val cognitoGroup: String, val authority: String) {
    CUSTOMER("customer", "ROLE_CUSTOMER"),
    SUPPORT_AGENT("support_agent", "ROLE_SUPPORT_AGENT"),
    ADMIN("admin", "ROLE_ADMIN"),
    MANAGER("manager", "ROLE_MANAGER");

    companion object {
        fun fromGroup(group: String): MalaiRole? = entries.firstOrNull { it.cognitoGroup == group }
    }
}
