package com.malai.stub.scheme

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus

class StubScenariosFpsInboundControllerTest {

    private val fpsInboundNotifier = mock(StubFpsInboundNotifier::class.java)
    private val bacsInboundNotifier = mock(StubBacsInboundNotifier::class.java)
    private val controller = StubScenariosInboundController(fpsInboundNotifier, bacsInboundNotifier)

    @Test
    fun delegatesAValidFpsInboundRequestToTheNotifierAndReturns204() {
        val response = controller.fpsInbound(aRequest())

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        verify(fpsInboundNotifier).emitFpsInbound(
            "65-82-37", "10000001", "Alice Walker",
            "60-00-01", "12345678", "Bob Marley",
            5000L, "GBP", "test transfer",
        )
    }

    @Test
    fun doesNotValidateBeneficiaryExistsAtMalai() {
        controller.fpsInbound(aRequest().copy(beneficiarySortCode = "99-99-99", beneficiaryAccountNumber = "00000000"))

        verify(fpsInboundNotifier).emitFpsInbound(
            "99-99-99", "00000000", "Alice Walker",
            "60-00-01", "12345678", "Bob Marley",
            5000L, "GBP", "test transfer",
        )
    }

    @Test
    fun rejectsBlankBeneficiarySortCode() {
        assertThatThrownBy { controller.fpsInbound(aRequest().copy(beneficiarySortCode = "")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(fpsInboundNotifier)
    }

    @Test
    fun rejectsNonPositiveAmount() {
        assertThatThrownBy { controller.fpsInbound(aRequest().copy(amountMinorUnits = 0L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(fpsInboundNotifier)
    }

    private fun aRequest() = InboundRequest(
        beneficiarySortCode = "65-82-37",
        beneficiaryAccountNumber = "10000001",
        beneficiaryName = "Alice Walker",
        originatorSortCode = "60-00-01",
        originatorAccountNumber = "12345678",
        originatorName = "Bob Marley",
        amountMinorUnits = 5000L,
        currency = "GBP",
        description = "test transfer",
    )
}
