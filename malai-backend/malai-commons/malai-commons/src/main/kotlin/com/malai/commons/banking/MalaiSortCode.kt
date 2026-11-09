package com.malai.commons.banking

/**
 * Malai's reserved sort-code prefix. Verified unallocated against Pay.UK's
 * Sorting Code Directory (and against the bundled VALACDOS modulus rules,
 * which contain zero rules covering 65-XX-XX) on [VERIFIED_ON].
 *
 * The full canonical Malai sort code is `65-82-37`; the [PREFIX] is the first
 * four digits of every Malai-issued sort code, asserted by
 * [MalaiSortCodePrefixLintTest] against the entire active build.
 *
 * If implementation slips by more than a week from [VERIFIED_ON], re-verify
 * the prefix is still unallocated before resuming Phase 2.
 */
object MalaiSortCode {

    /** First four digits (`XX-XX`) of every Malai-issued sort code. */
    const val PREFIX: String = "65-82"

    /** ISO date the prefix was verified against the Pay.UK directory. */
    const val VERIFIED_ON: String = "2026-05-06"

    /**
     * Canonical demo sort code for Sarah Mitchell's Current account and the
     * default branch the seeder writes against. Plan 02-15 (Wave E) bakes
     * additional `65-82-XX` codes for clearing accounts and external
     * counterparties.
     */
    const val CANONICAL: String = "65-82-37"

    /** Returns true if `sortCode` (in `XX-XX-XX` form) starts with [PREFIX]. */
    fun isMalaiPrefix(sortCode: String): Boolean = sortCode.startsWith("$PREFIX-")
}
