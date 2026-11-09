package com.malai.stub.cop

import com.malai.stub.directory.StubAccount
import com.malai.stub.directory.StubFictionalDirectory
import com.malai.stub.directory.StubFictionalDirectoryProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StubCopVerdictServiceTest {

    private val directory = StubFictionalDirectory(
        StubFictionalDirectoryProperties(
            accounts = listOf(
                StubAccount("60-00-01", "12345678", "Alice Walker", "NatWest"),
                StubAccount("60-00-01", "22334455", "Octopus Energy Ltd", "NatWest"),
            )
        )
    )
    private val service = StubCopVerdictService(directory)

    @Test
    fun exactNameOnKnownPairReturnsMatch() {
        val response = service.lookup(StubCopLookupRequest(
            sortCode = "60-00-01",
            accountNumber = "12345678",
            name = "Alice Walker",
        ))

        assertThat(response).isEqualTo(StubCopLookupResponse(StubCopVerdict.MATCH))
    }

    @Test
    fun nameWithinTypoToleranceReturnsMatch() {
        val response = service.lookup(StubCopLookupRequest(
            sortCode = "60-00-01",
            accountNumber = "12345678",
            name = "Alice Walkr",
        ))

        assertThat(response).isEqualTo(StubCopLookupResponse(StubCopVerdict.MATCH))
    }

    @Test
    fun closeButOutsideTypoToleranceReturnsCloseMatchWithOnRecordName() {
        val response = service.lookup(StubCopLookupRequest(
            sortCode = "60-00-01",
            accountNumber = "22334455",
            name = "Octopus Energy Co Ltd",
        ))

        assertThat(response).isEqualTo(
            StubCopLookupResponse(StubCopVerdict.CLOSE_MATCH, onRecordName = "Octopus Energy Ltd")
        )
    }

    @Test
    fun nameTooDissimilarReturnsNoMatchAndDoesNotLeakOnRecordName() {
        val response = service.lookup(StubCopLookupRequest(
            sortCode = "60-00-01",
            accountNumber = "12345678",
            name = "zzz",
        ))

        assertThat(response).isEqualTo(StubCopLookupResponse(StubCopVerdict.NO_MATCH))
        assertThat(response.onRecordName).isNull()
    }

    @Test
    fun unknownPairReturnsNoMatchWithoutLeakingDirectoryShape() {
        val response = service.lookup(StubCopLookupRequest(
            sortCode = "99-99-99",
            accountNumber = "99999999",
            name = "Alice Walker",
        ))

        assertThat(response).isEqualTo(StubCopLookupResponse(StubCopVerdict.NO_MATCH))
        assertThat(response.onRecordName).isNull()
    }
}
