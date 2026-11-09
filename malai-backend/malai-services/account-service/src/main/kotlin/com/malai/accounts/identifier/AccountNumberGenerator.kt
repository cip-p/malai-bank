package com.malai.accounts.identifier

import com.malai.commons.banking.MalaiSortCode
import com.malai.commons.banking.VocaLinkModulus
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Generates UK 8-digit account numbers. Behind a `fun interface` so acceptance tests can register
 * a deterministic `@Primary` implementation (mirrors how `UuidGenerator` is overridden by
 * `TestUuidGenerator` in [com.malai.test.commons.MalaiServiceTestConfiguration]).
 */
fun interface AccountNumberGenerator {
    /** Returns a freshly-generated 8-digit account number, zero-padded as a String. */
    fun generate(): String
}

@Configuration
class AccountNumberGeneratorConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Production generator (Plan 02-12): pulls the next value from the `account_number_seq`
     * Postgres sequence (V4) AND filters via the Vocalink modulus check against the configured
     * Malai sort code.
     *
     * Two startup-time invariants and one per-call loop:
     *
     *   1. The configured `BankIdentifierProperties.sortCode` MUST start with `MalaiSortCode.PREFIX`.
     *      Misconfiguration (e.g. dropping a non-MALAI sort code into application.properties) is
     *      fail-fast at boot — better than silently issuing accounts under another bank's prefix
     *      and discovering it in a Pay.UK reconciliation report.
     *
     *   2. `VocaLinkModulus.isValid(sortCode, candidate)` is enforced per candidate. The Pay.UK
     *      VALACDOS table contains zero rules covering 65-XX-XX (verified Plan 02-09), so under
     *      the Malai prefix every candidate passes — but the loop exists defensively so a future
     *      sort-code change can't ship modulus-invalid numbers without immediate operational
     *      feedback.
     *
     *   3. The loop caps at [MAX_ATTEMPTS]. If exceeded, throw — operational signal that the
     *      configured sort code's modulus rules are too strict and the prefix needs review.
     *
     * The sequence starts at 10_000_000 so values are 8 digits without padding;
     * `%08d` is purely defensive.
     */
    @Bean
    fun accountNumberGenerator(
        jdbcTemplate: JdbcTemplate,
        bankIdentifierProperties: BankIdentifierProperties,
    ): AccountNumberGenerator {
        check(MalaiSortCode.isMalaiPrefix(bankIdentifierProperties.sortCode)) {
            "Configured malai.bank.sort-code='${bankIdentifierProperties.sortCode}' is outside the " +
                "MALAI prefix '${MalaiSortCode.PREFIX}'. Set MAL_BANK_SORT_CODE to a 65-82-XX value."
        }
        val sortCode = bankIdentifierProperties.sortCode
        return AccountNumberGenerator {
            var attempts = 0
            while (attempts < MAX_ATTEMPTS) {
                val raw = jdbcTemplate.queryForObject(
                    "SELECT nextval('account_number_seq')",
                    Long::class.java,
                )!!
                val candidate = "%08d".format(raw)
                if (VocaLinkModulus.isValid(sortCode, candidate)) return@AccountNumberGenerator candidate
                attempts++
                log.warn(
                    "AccountNumberGenerator: candidate {} rejected by Vocalink modulus under sort code {} (attempt {}/{})",
                    candidate, sortCode, attempts, MAX_ATTEMPTS,
                )
            }
            error(
                "AccountNumberGenerator could not produce a Vocalink-valid account number after " +
                    "$MAX_ATTEMPTS attempts under sort code $sortCode — the configured prefix's " +
                    "modulus rules are too strict. Operational follow-up required."
            )
        }
    }

    companion object {
        const val MAX_ATTEMPTS: Int = 100
    }
}
