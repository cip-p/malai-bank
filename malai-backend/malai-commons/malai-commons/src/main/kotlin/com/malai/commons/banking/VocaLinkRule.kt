package com.malai.commons.banking

enum class ModulusAlgorithm { MOD10, MOD11, DBLAL }

/**
 * One row of the Pay.UK VALACDOS table. Weights are the 14 ints (u..h) that
 * multiply the digits of `<sort code><account number>`.
 */
data class VocaLinkRule(
    val rangeLow: Int,
    val rangeHigh: Int,
    val algorithm: ModulusAlgorithm,
    val weights: List<Int>,
    val exception: Int? = null,
) {
    init {
        require(weights.size == 14) { "VocaLinkRule must have exactly 14 weights, got ${weights.size}" }
    }

    fun appliesTo(sortCodeNumeric: Int): Boolean = sortCodeNumeric in rangeLow..rangeHigh
}
