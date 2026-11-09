package com.malai.stub.scheme

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import java.util.UUID

class StubScenariosBacsCollectionControllerTest {

    private val bacsCollectionNotifier = mock(StubBacsCollectionNotifier::class.java)
    private val controller = StubScenariosCollectionController(bacsCollectionNotifier)

    @Test
    fun delegatesAValidBacsCollectionRequestToTheNotifierAndReturns204() {
        val endToEndId = UUID.randomUUID()

        val response = controller.bacsCollection(aRequest().copy(endToEndId = endToEndId))

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        verify(bacsCollectionNotifier).emitBacsCollection(
            endToEndId, "65-82-37", "10000001", "Alice Walker",
            "60-00-01", "22334455", "Octopus Energy Ltd",
            4_200L, "GBP", "Energy DD",
        )
    }

    @Test
    fun rejectsNonPositiveAmount() {
        assertThatThrownBy { controller.bacsCollection(aRequest().copy(amountMinorUnits = 0L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(bacsCollectionNotifier)
    }

    @Test
    fun rejectsBlankCreditorName() {
        assertThatThrownBy { controller.bacsCollection(aRequest().copy(creditorName = "")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(bacsCollectionNotifier)
    }

    private fun aRequest() = CollectionRequest(
        debtorSortCode = "65-82-37",
        debtorAccountNumber = "10000001",
        debtorName = "Alice Walker",
        creditorSortCode = "60-00-01",
        creditorAccountNumber = "22334455",
        creditorName = "Octopus Energy Ltd",
        amountMinorUnits = 4_200L,
        currency = "GBP",
        description = "Energy DD",
        endToEndId = UUID.randomUUID(),
    )
}
