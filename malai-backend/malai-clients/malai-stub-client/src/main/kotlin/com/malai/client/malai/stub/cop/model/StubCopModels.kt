package com.malai.client.malai.stub.cop.model

data class StubCopLookupRequest(
    val sortCode: String,
    val accountNumber: String,
    val name: String,
)

data class StubCopLookupResponse(
    val verdict: StubCopVerdict,
    val onRecordName: String? = null,
)

enum class StubCopVerdict { MATCH, CLOSE_MATCH, NO_MATCH }
