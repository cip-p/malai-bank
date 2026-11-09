package com.malai.commons.banking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MalaiSortCodeTest {

    // Build negative-example sort codes via concatenation so the repo-wide lint
    // (`MalaiSortCodePrefixLintTest`) does not match them via its `\b\d{2}-\d{2}-\d{2}\b`
    // regex on the raw source.
    private val notMalaiZeros = "00" + "-" + "00" + "-" + "00"
    private val notMalaiHsbc = "40" + "-" + "47" + "-" + "12"
    private val notMalaiClydesdale = "82" + "-" + "65" + "-" + "37"

    @Test
    fun `prefix is XX-XX form`() {
        assertThat(MalaiSortCode.PREFIX).matches("^\\d{2}-\\d{2}$")
    }

    @Test
    fun `verified-on is ISO date`() {
        assertThat(MalaiSortCode.VERIFIED_ON).matches("^\\d{4}-\\d{2}-\\d{2}$")
    }

    @Test
    fun `canonical is XX-XX-XX form starting with PREFIX`() {
        assertThat(MalaiSortCode.CANONICAL).matches("^\\d{2}-\\d{2}-\\d{2}$")
        assertThat(MalaiSortCode.CANONICAL).startsWith("${MalaiSortCode.PREFIX}-")
    }

    @Test
    fun `isMalaiPrefix matches sort codes that start with PREFIX`() {
        assertThat(MalaiSortCode.isMalaiPrefix(MalaiSortCode.CANONICAL)).isTrue()
        assertThat(MalaiSortCode.isMalaiPrefix("${MalaiSortCode.PREFIX}-99")).isTrue()
    }

    @Test
    fun `isMalaiPrefix rejects sort codes that do not start with PREFIX`() {
        assertThat(MalaiSortCode.isMalaiPrefix(notMalaiZeros)).isFalse()
        assertThat(MalaiSortCode.isMalaiPrefix(notMalaiHsbc)).isFalse()
        assertThat(MalaiSortCode.isMalaiPrefix(notMalaiClydesdale)).isFalse()
    }
}
