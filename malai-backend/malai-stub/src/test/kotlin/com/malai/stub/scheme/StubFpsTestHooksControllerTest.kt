package com.malai.stub.scheme

import com.malai.payments.messages.ReturnReason
import com.malai.stub.directory.OutboundOutcomeResolver
import com.malai.stub.directory.StubAccount
import com.malai.stub.directory.StubFictionalDirectory
import com.malai.stub.directory.StubFictionalDirectoryProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus

class StubFpsTestHooksControllerTest {

    private val knownSortCode = "60-00-01"
    private val knownAccountNumber = "12345678"

    private val directory = StubFictionalDirectory(
        StubFictionalDirectoryProperties(
            accounts = listOf(StubAccount(knownSortCode, knownAccountNumber, "Alice Walker", "NatWest")),
        ),
    )
    private val scheme = StubFpsScheme(
        directory,
        OutboundOutcomeResolver(directory),
        mock(StubFpsGatewayClient::class.java),
    )
    private val controller = StubFpsTestHooksController(scheme)

    @Test
    fun returns204AndArmsTheNextReturnWithTheConfiguredReason() {
        val response = controller.armReturn(ArmReturnRequest(reason = ReturnReason.ACCOUNT_CLOSED))

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(scheme.decide(false, knownSortCode, knownAccountNumber))
            .isEqualTo(FpsDecision.Return(ReturnReason.ACCOUNT_CLOSED))
    }
}
