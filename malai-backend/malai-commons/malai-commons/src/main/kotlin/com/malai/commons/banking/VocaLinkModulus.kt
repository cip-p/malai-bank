package com.malai.commons.banking

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * UK Vocalink modulus checking against the bundled Pay.UK VALACDOS table.
 *
 * Algorithm + exception handling ported from
 * [uphold/uk-modulus-checking](https://github.com/uphold/uk-modulus-checking)
 * (MIT-licensed) — see `src/index.js` for the JavaScript original. The bundled
 * `VALACDOS.txt` is the v6.40-updated snapshot from the same repository.
 *
 * Phase 2 D-2.10 / BACK-03 — used by [com.malai.accounts.identifier.AccountNumberGenerator]
 * (Plan 02-12) to generate compliant Malai account numbers.
 */
object VocaLinkModulus {

    private val rules: List<VocaLinkRule> = loadRules()
    private val substitutes: Map<Int, Int> = loadSubstitutes()

    // Position aliases per Pay.UK PDF v8.40: u v w x y z (sort code) | a b c d e f g h (account).
    private const val POS_A = 6
    private const val POS_B = 7
    private const val POS_C = 8
    private const val POS_G = 12
    private const val POS_H = 13

    /**
     * @param sortCode `XX-XX-XX` or `XXXXXX` form.
     * @param accountNumber 6-10 digits (UK account numbers are 8 digits but
     *   some legacy formats are 6 or 7; Pay.UK pads them at the front when
     *   running the check).
     */
    fun isValid(sortCode: String, accountNumber: String): Boolean {
        val sanitisedSortCode = sortCode.replace("-", "")
        val sanitisedAccount = accountNumber.replace("-", "").replace(" ", "")
        if (sanitisedSortCode.length != 6 || !sanitisedSortCode.all(Char::isDigit)) return false
        if (sanitisedAccount.length !in 6..10 || !sanitisedAccount.all(Char::isDigit)) return false
        // Pad short account numbers per Pay.UK convention (front-pad with zeros to 8).
        val padded = sanitisedAccount.padStart(8, '0')
        if (padded.length != 8) return false

        val sortNumeric = sanitisedSortCode.toInt()
        val applicable = rules.filter { it.appliesTo(sortNumeric) }.take(2)
        // No rule for this range -> Pay.UK guidance: presume valid.
        if (applicable.isEmpty()) return true

        val first = applicable[0]
        val firstPasses = isCheckValid(first, sanitisedSortCode, padded)

        if (firstPasses) {
            // Exceptions 2, 9, 10, 11, 12, 13, 14: only the first check needs to pass.
            if (applicable.size == 1 || first.exception in setOf(2, 9, 10, 11, 12, 13, 14)) {
                return true
            }
            return isCheckValid(applicable[1], sanitisedSortCode, padded)
        }

        // First check failed.
        if (first.exception == 14) {
            // 8th digit must be 0/1/9; remove it and prepend 0 for re-check.
            val eighth = padded[7].digitToInt()
            if (eighth !in setOf(0, 1, 9)) return false
            val rewritten = "0" + padded.substring(0, 7)
            return isCheckValid(first, sanitisedSortCode, rewritten)
        }

        if (applicable.size == 1 || first.exception !in setOf(2, 9, 10, 11, 12, 13, 14)) {
            return false
        }
        return isCheckValid(applicable[1], sanitisedSortCode, padded)
    }

    private fun isCheckValid(rule: VocaLinkRule, sortCode6: String, account8: String): Boolean {
        // Some exceptions substitute the sort code BEFORE the weighted sum.
        val effectiveSortCode = when (rule.exception) {
            5 -> substitutes[sortCode6.toInt()]?.toString()?.padStart(6, '0') ?: sortCode6
            8 -> "090126"
            9 -> "309634"
            else -> sortCode6
        }
        val number = effectiveSortCode + account8
        if (isCheckSkippable(rule, number)) return true

        val weights = effectiveWeights(rule, number)
        val module = if (rule.algorithm == ModulusAlgorithm.MOD11) 11 else 10

        // Per-position digit*weight products.
        val products = (0 until 14).map { number[it].digitToInt() * weights[it] }
        val total: Int = if (rule.algorithm == ModulusAlgorithm.DBLAL) {
            // DBLAL: sum the digits of each product, not the products themselves.
            products.sumOf { product -> product.toString().sumOf { it.digitToInt() } }
        } else {
            products.sum()
        } + (if (rule.exception == 1) 27 else 0)

        val remainder = total % module

        return when (rule.exception) {
            4 -> remainder == number[POS_G].digitToInt() * 10 + number[POS_H].digitToInt()
            5 -> when (rule.algorithm) {
                ModulusAlgorithm.DBLAL ->
                    (remainder == 0 && number[POS_H].digitToInt() == 0) ||
                        number[POS_H].digitToInt() == (10 - remainder) % 10
                else -> when {
                    remainder == 1 -> false
                    remainder == 0 && number[POS_G].digitToInt() == 0 -> true
                    else -> number[POS_G].digitToInt() == (11 - remainder) % 11
                }
            }
            else -> remainder == 0
        }
    }

    private fun effectiveWeights(rule: VocaLinkRule, number: String): List<Int> {
        if (rule.exception == 2) {
            val a = number[POS_A].digitToInt()
            val g = number[POS_G].digitToInt()
            return when {
                a != 0 && g != 9 -> listOf(0, 0, 1, 2, 5, 3, 6, 4, 8, 7, 10, 9, 3, 1)
                a != 0 && g == 9 -> listOf(0, 0, 0, 0, 0, 0, 0, 0, 8, 7, 10, 9, 3, 1)
                else -> rule.weights
            }
        }
        if (rule.exception == 7 && number[POS_G].digitToInt() == 9) {
            return listOf(0, 0, 0, 0, 0, 0, 0, 0) + rule.weights.subList(8, 14)
        }
        if (rule.exception == 10) {
            val ab = number.substring(POS_A, POS_A + 2)
            val b9 = number[POS_B].digitToInt() == 9
            if (ab == "09" || (ab.startsWith("9") && b9)) {
                return listOf(0, 0, 0, 0, 0, 0, 0, 0) + rule.weights.subList(8, 14)
            }
        }
        return rule.weights
    }

    private fun isCheckSkippable(rule: VocaLinkRule, number: String): Boolean {
        if (rule.exception == 3) {
            val c = number[POS_C].digitToInt()
            if (c == 6 || c == 9) return true
        }
        if (rule.exception == 6) {
            val a = number[POS_A].digitToInt()
            val g = number[POS_G].digitToInt()
            val h = number[POS_H].digitToInt()
            if (a in 4..8 && g == h) return true
        }
        return false
    }

    private fun loadRules(): List<VocaLinkRule> {
        val resource = checkNotNull(VocaLinkModulus::class.java.getResourceAsStream("/banking/VALACDOS.txt")) {
            "VALACDOS.txt not found on classpath"
        }
        return BufferedReader(InputStreamReader(resource, Charsets.UTF_8)).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotBlank() }.map(::parseLine).toList()
        }
    }

    private fun parseLine(line: String): VocaLinkRule {
        val toks = line.split(Regex("\\s+"))
        require(toks.size >= 17) { "VALACDOS row has fewer than 17 fields: '$line'" }
        val low = toks[0].toInt()
        val high = toks[1].toInt()
        val alg = ModulusAlgorithm.valueOf(toks[2].uppercase())
        val weights = (3..16).map { toks[it].toInt() }
        val exception = toks.getOrNull(17)?.toIntOrNull()
        return VocaLinkRule(low, high, alg, weights, exception)
    }

    private fun loadSubstitutes(): Map<Int, Int> {
        val resource = VocaLinkModulus::class.java.getResourceAsStream("/banking/SCSUBTAB.txt")
            ?: return emptyMap()
        return BufferedReader(InputStreamReader(resource, Charsets.UTF_8)).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotBlank() }.associate { line ->
                val toks = line.split(Regex("\\s+"))
                toks[0].toInt() to toks[1].toInt()
            }
        }
    }
}
