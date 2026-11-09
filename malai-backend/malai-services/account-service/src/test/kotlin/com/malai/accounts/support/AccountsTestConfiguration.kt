package com.malai.accounts.support

import com.malai.accounts.identifier.AccountNumberGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

/**
 * Test-only `@Primary` overrides specific to account-service. Loaded by [runAccountsService] so
 * acceptance tests get deterministic identifier generation alongside the
 * [com.malai.test.commons.MalaiServiceTestConfiguration] overrides for `TimeService` /
 * `UuidGenerator`.
 */
@Configuration
@Profile("test", "acceptance-tests")
class AccountsTestConfiguration {

    @Bean
    @Primary
    fun testAccountNumberGenerator(): AccountNumberGenerator = TestAccountNumberGenerator()
}
