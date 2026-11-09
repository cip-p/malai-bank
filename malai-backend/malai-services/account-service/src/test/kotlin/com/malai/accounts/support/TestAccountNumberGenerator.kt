package com.malai.accounts.support

import com.malai.accounts.identifier.AccountNumberGenerator

/**
 * Deterministic [AccountNumberGenerator] for acceptance tests, mirroring
 * [com.malai.test.commons.TestUuidGenerator]. Tests prime expected values via [willReturn]; if
 * the queue is empty when [generate] is called, falls back to a generated placeholder so a test
 * that doesn't care about the account number doesn't get a random one each run.
 *
 * The fallback counts up from [DEFAULT_ACCOUNT_NUMBER] rather than returning it every time.
 * A constant was sufficient while a flow only ever opened one account; registration now opens
 * three — current, savings and credit card — and handing all of them the same number violates
 * `account_identifiers_type_value_unique`, which surfaced as a 500 from the registration and
 * seed-demo-data endpoints rather than as anything that named the account number.
 *
 * The first unprimed call still returns [DEFAULT_ACCOUNT_NUMBER] exactly, so tests that assert
 * against it are unaffected. Real account numbers come from the `account_number_seq` sequence
 * starting at 10000000, which cannot collide within a single run.
 */
class TestAccountNumberGenerator : AccountNumberGenerator {

    private val accountNumbers: ArrayDeque<String> = ArrayDeque()
    private var fallbacksIssued = 0L

    fun willReturn(vararg accountNumbers: String) {
        this.accountNumbers.addAll(accountNumbers)
    }

    override fun generate(): String =
        accountNumbers.removeFirstOrNull() ?: nextFallback()

    private fun nextFallback(): String =
        (DEFAULT_ACCOUNT_NUMBER.toLong() + fallbacksIssued++).toString()

    fun reset() {
        accountNumbers.clear()
        fallbacksIssued = 0
    }

    companion object {
        const val DEFAULT_ACCOUNT_NUMBER = "12345678"
    }
}
