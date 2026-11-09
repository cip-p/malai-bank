package com.malai.commons.banking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Test vectors ported from
 * [uphold/uk-modulus-checking](https://github.com/uphold/uk-modulus-checking/blob/master/test/index_test.js)
 * (MIT-licensed). Each vector is `(sort code, account number, expectedValid)`.
 *
 * The bundled `VALACDOS.txt` is the v6.40-updated snapshot from the same
 * upstream repo, so the algorithm + table + vectors are version-aligned.
 */
class VocaLinkModulusTest {

    private val invalidVectors = listOf(
        Triple("938063", "15763217", false),
        Triple("938063", "15764264", false),
        Triple("938063", "15764273", false),
        Triple("203099", "58716970", false),
        Triple("118765", "64371388", false),
        Triple("089999", "66374959", false),
        Triple("203099", "66831036", false),
        Triple("107999", "88837493", false),
    )

    private val validVectors = listOf(
        Triple("180002", "00000190", true),
        Triple("309070", "02355688", true),
        Triple("086090", "06774744", true),
        Triple("938611", "07806039", true),
        Triple("871427", "09123496", true),
        Triple("074456", "11104102", true),
        Triple("074456", "12345112", true),
        Triple("309070", "12345668", true),
        Triple("309070", "12345677", true),
        Triple("827101", "28748352", true),
        Triple("070116", "34012583", true),
        Triple("200915", "41011166", true),
        Triple("938600", "42368003", true),
        Triple("871427", "46238510", true),
        Triple("872427", "46238510", true),
        Triple("938063", "55065200", true),
        Triple("202959", "63748472", true),
        Triple("134020", "63849203", true),
        Triple("118765", "64371389", true),
        Triple("089999", "66374958", true),
        Triple("820000", "73688637", true),
        Triple("827999", "73988638", true),
        Triple("107999", "88837491", true),
        Triple("871427", "99123496", true),
        Triple("309070", "99345694", true),
        Triple("772798", "99345694", true),
    )

    @Test
    fun `published valid vectors all pass modulus check`() {
        validVectors.forEach { (sortCode, accountNumber, expected) ->
            val actual = VocaLinkModulus.isValid(sortCode, accountNumber)
            assertThat(actual)
                .withFailMessage(
                    "VocaLinkModulus.isValid(\"%s\", \"%s\") expected=%s got=%s",
                    sortCode, accountNumber, expected, actual,
                )
                .isEqualTo(expected)
        }
    }

    @Test
    fun `published invalid vectors all fail modulus check`() {
        invalidVectors.forEach { (sortCode, accountNumber, expected) ->
            val actual = VocaLinkModulus.isValid(sortCode, accountNumber)
            assertThat(actual)
                .withFailMessage(
                    "VocaLinkModulus.isValid(\"%s\", \"%s\") expected=%s got=%s",
                    sortCode, accountNumber, expected, actual,
                )
                .isEqualTo(expected)
        }
    }

    @Test
    fun `unallocated MALAI prefix returns valid (no rule applies)`() {
        // 65-82 has zero rules in VALACDOS — Pay.UK guidance: presume valid.
        assertThat(VocaLinkModulus.isValid("658237", "12345678")).isTrue()
        assertThat(VocaLinkModulus.isValid("65-82-37", "00000001")).isTrue()
    }

    @Test
    fun `rejects sort codes with non-digit characters`() {
        assertThat(VocaLinkModulus.isValid("AB-CD-EF", "12345678")).isFalse()
    }

    @Test
    fun `rejects account numbers outside 6 to 10 digit range`() {
        assertThat(VocaLinkModulus.isValid("658237", "12345")).isFalse()
        assertThat(VocaLinkModulus.isValid("658237", "12345678901")).isFalse()
    }
}
