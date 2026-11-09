package com.malai.accounts.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LevenshteinTest {

    @Test
    fun `kitten to sitting is 3`() {
        assertThat(Levenshtein.distance("kitten", "sitting")).isEqualTo(3)
    }

    @Test
    fun `identical is 0`() {
        assertThat(Levenshtein.distance("foo", "foo")).isEqualTo(0)
    }

    @Test
    fun `case-insensitive treats Foo and FOO as equal`() {
        assertThat(Levenshtein.distance("Foo", "FOO")).isEqualTo(0)
    }

    @Test
    fun `empty to abc is 3`() {
        assertThat(Levenshtein.distance("", "abc")).isEqualTo(3)
    }

    @Test
    fun `abc to empty is 3`() {
        assertThat(Levenshtein.distance("abc", "")).isEqualTo(3)
    }

    @Test
    fun `single insert is 1`() {
        assertThat(Levenshtein.distance("ab", "abc")).isEqualTo(1)
    }

    @Test
    fun `single substitution is 1`() {
        assertThat(Levenshtein.distance("flaw", "fraw")).isEqualTo(1)
    }

    @Test
    fun `Sarah Mitchell vs S Mitchell is 4`() {
        assertThat(Levenshtein.distance("Sarah Mitchell", "S Mitchell")).isEqualTo(4)
    }

    @Test
    fun `Octopus Energy Ltd vs Octopus Energy Co Ltd is 3`() {
        assertThat(Levenshtein.distance("Octopus Energy Ltd", "Octopus Energy Co Ltd")).isEqualTo(3)
    }
}
