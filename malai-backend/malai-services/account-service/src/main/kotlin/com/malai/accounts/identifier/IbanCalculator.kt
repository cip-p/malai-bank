package com.malai.accounts.identifier

import java.math.BigInteger

/**
 * Builds UK IBANs (ISO 13616) from a sort code + account number.
 *
 * Layout (22 chars total): `GB` + `CC` + `BBBB` + `SSSSSS` + `AAAAAAAA`
 *  - `CC`        — 2 mod-97 (ISO 7064) check digits
 *  - `BBBB`      — 4-letter bank code (first 4 chars of the BIC, uppercased)
 *  - `SSSSSS`    — 6-digit sort code (dashes stripped)
 *  - `AAAAAAAA`  — 8-digit account number
 *
 * Check digits are calculated by the ISO 7064 mod-97-10 procedure:
 *  1. Move country code + `00` to the end of the BBAN
 *  2. Replace each letter with its position-based numeric form (`A`=10, ..., `Z`=35)
 *  3. Compute `98 - (numericString % 97)`, zero-padded to 2 digits
 */
object IbanCalculator {

    private const val UK_COUNTRY_CODE = "GB"
    private val NINETY_SEVEN: BigInteger = BigInteger.valueOf(97)
    private val NINETY_EIGHT: BigInteger = BigInteger.valueOf(98)

    /**
     * @param bic the bank's BIC — only the first 4 characters are used. Must be ≥ 4 chars.
     * @param sortCode the 6-digit sort code, with or without dashes (e.g. `65-82-37` or `658237`).
     * @param accountNumber the 8-digit account number.
     */
    fun ukIban(bic: String, sortCode: String, accountNumber: String): String {
        require(bic.length >= 4) { "BIC must be at least 4 characters: '$bic'" }
        val bankCode = bic.take(4).uppercase()
        val sortDigits = sortCode.replace("-", "")
        require(sortDigits.length == 6 && sortDigits.all { it.isDigit() }) {
            "sort code must be 6 digits (with optional dashes): '$sortCode'"
        }
        require(accountNumber.length == 8 && accountNumber.all { it.isDigit() }) {
            "account number must be 8 digits: '$accountNumber'"
        }

        val bban = bankCode + sortDigits + accountNumber
        val checkDigits = computeCheckDigits(bban)
        return UK_COUNTRY_CODE + checkDigits + bban
    }

    private fun computeCheckDigits(bban: String): String {
        // Move "GB00" (country code + 00 placeholder) to the end and convert letters to digits.
        val rearranged = bban + UK_COUNTRY_CODE + "00"
        val numeric = StringBuilder()
        for (ch in rearranged) {
            when {
                ch.isDigit() -> numeric.append(ch)
                ch.isLetter() -> numeric.append(ch.uppercaseChar().code - 'A'.code + 10)
                else -> error("unexpected character in BBAN: '$ch'")
            }
        }
        val mod = BigInteger(numeric.toString()).mod(NINETY_SEVEN)
        val check = NINETY_EIGHT.subtract(mod).toInt()
        return "%02d".format(check)
    }
}
