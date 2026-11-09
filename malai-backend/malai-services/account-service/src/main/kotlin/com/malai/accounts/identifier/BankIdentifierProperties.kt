package com.malai.accounts.identifier

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The bank's own SORT_CODE + BIC. Real UK banks are assigned these by their regulator and
 * they apply uniformly to every account — only the 8-digit account number varies per account.
 *
 * The IBAN is derived from these two values plus the per-account number; see [IbanCalculator].
 *
 * Configured via `malai.bank.sort-code` / `malai.bank.bic` in `application.properties`.
 */
@ConfigurationProperties("malai.bank")
data class BankIdentifierProperties(
    /** UK sort code in `XX-XX-XX` format (the dashed form is what we display to users). */
    val sortCode: String,
    /** ISO 9362 BIC — 8 or 11 characters. The first 4 form the bank code embedded in the IBAN. */
    val bic: String,
)
