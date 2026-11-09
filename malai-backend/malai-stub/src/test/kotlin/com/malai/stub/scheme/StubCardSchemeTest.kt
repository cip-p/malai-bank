package com.malai.stub.scheme

import com.malai.stub.directory.StubMerchant
import com.malai.stub.directory.StubMerchantDirectory
import com.malai.stub.directory.StubMerchantDirectoryProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class StubCardSchemeTest {

    private val directory = StubMerchantDirectory(
        StubMerchantDirectoryProperties(
            merchants = listOf(StubMerchant("fictional-coffee", "Fictional Coffee Co", "5814")),
        ),
    )
    private val gatewayClient = mock(StubCardGatewayClient::class.java)
    private val scheme = StubCardScheme(directory, gatewayClient, presentDelayMillis = 60_000)

    @Test
    fun `the merchant directory resolves a merchant by id`() {
        val merchant = directory.find("fictional-coffee")
        assertThat(merchant).isNotNull
        assertThat(merchant!!.name).isEqualTo("Fictional Coffee Co")
        assertThat(merchant.mcc).isEqualTo("5814")
    }

    @Test
    fun `an unknown merchant is rejected without reaching the gateway`() {
        assertThatThrownBy { scheme.originateAuthorisation("9904320000012341", "no-such-merchant", 100, "GBP") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(gatewayClient)
    }
}
