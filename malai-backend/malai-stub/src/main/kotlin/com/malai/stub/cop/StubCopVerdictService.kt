package com.malai.stub.cop

import com.malai.stub.directory.StubFictionalDirectory
import org.springframework.stereotype.Service

@Service
class StubCopVerdictService(
    private val directory: StubFictionalDirectory,
) {

    fun lookup(request: StubCopLookupRequest): StubCopLookupResponse {
        val onRecord = directory.find(request.sortCode, request.accountNumber)
            ?: return StubCopLookupResponse(StubCopVerdict.NO_MATCH)

        val distance = Levenshtein.distance(onRecord.name, request.name)
        val maxLen = maxOf(onRecord.name.length, request.name.length).coerceAtLeast(1)
        val similarity = 1.0 - (distance.toDouble() / maxLen)

        return when {
            distance <= TYPO_TOLERANCE -> StubCopLookupResponse(StubCopVerdict.MATCH)
            similarity >= MIN_SIMILARITY_FOR_CLOSE_MATCH ->
                StubCopLookupResponse(StubCopVerdict.CLOSE_MATCH, onRecordName = onRecord.name)
            else -> StubCopLookupResponse(StubCopVerdict.NO_MATCH)
        }
    }

    private companion object {
        const val TYPO_TOLERANCE = 2
        const val MIN_SIMILARITY_FOR_CLOSE_MATCH = 0.85
    }
}
