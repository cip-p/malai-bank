package com.malai.stub.ambient

import com.malai.stub.directory.StubMerchant
import com.malai.stub.directory.StubMerchantDirectory
import com.malai.stub.directory.StubMerchantDirectoryProperties
import com.malai.stub.directory.StubPanRegistry
import com.malai.stub.scheme.StubCardScheme
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class StubAmbientCardSchedulerTest {

    private val stubCardScheme = mock(StubCardScheme::class.java)
    private val panRegistry = StubPanRegistry()
    private val merchants = StubMerchantDirectory(
        StubMerchantDirectoryProperties(
            merchants = listOf(
                StubMerchant("fictional-coffee", "Fictional Coffee Co", "5814"),
                StubMerchant("nimbus-groceries", "Nimbus Groceries", "5411"),
            ),
        ),
    )

    private val properties = StubAmbientCardProperties(
        enabled = true,
        amountsMinorUnits = listOf(320L, 875L),
    )

    private val scheduler = StubAmbientCardScheduler(properties, panRegistry, merchants, stubCardScheme)

    @Test
    fun originatesAPurchaseAtADirectoryMerchantForARegisteredPan() {
        panRegistry.register("9904320000012341")

        scheduler.tick()

        verify(stubCardScheme).originateAuthorisation("9904320000012341", "fictional-coffee", 320L, "GBP")
    }

    @Test
    fun originatesForEachRegisteredPanRotatingMerchantsAndAmounts() {
        panRegistry.register("9904320000012341")
        panRegistry.register("9904320000098765")

        scheduler.tick()

        verify(stubCardScheme).originateAuthorisation("9904320000012341", "fictional-coffee", 320L, "GBP")
        verify(stubCardScheme).originateAuthorisation("9904320000098765", "nimbus-groceries", 875L, "GBP")
    }

    @Test
    fun wrapsMerchantAndAmountRotationAcrossManyPurchases() {
        panRegistry.register("9904320000000001")
        panRegistry.register("9904320000000002")
        panRegistry.register("9904320000000003")

        scheduler.tick()

        verify(stubCardScheme).originateAuthorisation("9904320000000003", "fictional-coffee", 320L, "GBP")
    }

    @Test
    fun originatesNothingWhenNoPansAreRegistered() {
        scheduler.tick()

        verifyNoInteractions(stubCardScheme)
    }
}
