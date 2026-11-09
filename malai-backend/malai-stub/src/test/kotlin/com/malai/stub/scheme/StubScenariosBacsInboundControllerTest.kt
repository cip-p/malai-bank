package com.malai.stub.scheme

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus

class StubScenariosBacsInboundControllerTest {

    private val fpsInboundNotifier = mock(StubFpsInboundNotifier::class.java)
    private val bacsInboundNotifier = mock(StubBacsInboundNotifier::class.java)
    private val controller = StubScenariosInboundController(fpsInboundNotifier, bacsInboundNotifier)

    @Test
    fun delegatesAValidBacsInboundRequestToTheNotifierAndReturns204() {
        val response = controller.bacsInbound(aRequest())

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        verify(bacsInboundNotifier).emitBacsInbound(
            "65-82-37", "10000001", "Alice Walker",
            "20-00-05", "20000020", "ACME Employer Ltd",
            250_000L, "GBP", "Salary",
        )
    }

    @Test
    fun doesNotValidateBeneficiaryExistsAtMalai() {
        controller.bacsInbound(aRequest().copy(beneficiarySortCode = "99-99-99", beneficiaryAccountNumber = "00000000"))

        verify(bacsInboundNotifier).emitBacsInbound(
            "99-99-99", "00000000", "Alice Walker",
            "20-00-05", "20000020", "ACME Employer Ltd",
            250_000L, "GBP", "Salary",
        )
    }

    @Test
    fun rejectsNonPositiveAmount() {
        assertThatThrownBy { controller.bacsInbound(aRequest().copy(amountMinorUnits = 0L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(bacsInboundNotifier)
    }

    @Test
    fun rejectsBlankOriginatorName() {
        assertThatThrownBy { controller.bacsInbound(aRequest().copy(originatorName = "")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(bacsInboundNotifier)
    }

    private fun aRequest() = InboundRequest(
        beneficiarySortCode = "65-82-37",
        beneficiaryAccountNumber = "10000001",
        beneficiaryName = "Alice Walker",
        originatorSortCode = "20-00-05",
        originatorAccountNumber = "20000020",
        originatorName = "ACME Employer Ltd",
        amountMinorUnits = 250_000L,
        currency = "GBP",
        description = "Salary",
    )
}
