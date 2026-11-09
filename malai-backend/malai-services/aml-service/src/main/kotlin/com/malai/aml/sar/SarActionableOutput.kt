package com.malai.aml.sar

import tools.jackson.module.kotlin.jacksonObjectMapper

// The consumer's half of the Gate's contract for a SAR draft. The Model Decision's Raw output is a
// JSON object whose "narrative" is the officer-readable report; an EDIT ruling's stored edited
// output carries the same field, so an accept and an edit are read identically here and the Case
// never learns which it was from the shape of what it filed.
//
// Anything else -- output the drafter never produced, a missing field, a narrative edited down to
// whitespace -- is null, which close-as-reported reads as "not actionable" and refuses. Parsing is
// the consumer's business; deciding what is actionable stays the MDR's (ADR-0009).
object SarActionableOutput {

    private val outputMapper = jacksonObjectMapper()

    fun narrativeIn(actionableOutput: String): String? =
        runCatching {
            outputMapper.readTree(actionableOutput).get(NARRATIVE_FIELD)
                ?.takeIf { it.isString }
                ?.asString()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private const val NARRATIVE_FIELD = "narrative"
}
