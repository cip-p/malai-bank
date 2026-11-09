package com.malai.client.malai.stub.cop.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class StubCopLookupResponseBuilder private constructor() : TestBuilder {

    private lateinit var verdict: String
    private var onRecordName: String? = null
    private var includeOnRecordName by Delegates.notNull<Boolean>()

    init {
        includeOnRecordName = false
    }

    fun withVerdict(verdict: String) = apply { this.verdict = verdict }
    fun withOnRecordName(onRecordName: String) = apply {
        this.onRecordName = onRecordName
        this.includeOnRecordName = true
    }

    override fun build(): String = JsonSerialiser.toJson(
        if (includeOnRecordName) WithOnRecordName(verdict, onRecordName) else Verdict(verdict)
    )

    data class Verdict(val verdict: String)
    data class WithOnRecordName(val verdict: String, val onRecordName: String?)

    companion object {
        fun aStubCopLookupResponse() = StubCopLookupResponseBuilder()
    }
}
