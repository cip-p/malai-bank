package com.malai.stub.directory

import com.malai.payments.messages.ReturnReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutboundOutcomeResolverTest {

    private val knownSettlesSortCode = "60-00-01"
    private val knownSettlesAccountNumber = "12345678"

    private val knownReturnsSortCode = "60-00-01"
    private val knownReturnsAccountNumber = "99000001"

    private val knownStuckSortCode = "04-00-04"
    private val knownStuckAccountNumber = "99000002"

    private val resolver = OutboundOutcomeResolver(
        StubFictionalDirectory(
            StubFictionalDirectoryProperties(
                accounts = listOf(
                    StubAccount(knownSettlesSortCode, knownSettlesAccountNumber, "Alice Walker", "NatWest"),
                    StubAccount(
                        knownReturnsSortCode, knownReturnsAccountNumber, "Jane Closed", "NatWest",
                        outboundOutcome = OutboundOutcome.RETURNS,
                        returnReason = ReturnReason.ACCOUNT_CLOSED,
                    ),
                    StubAccount(
                        knownStuckSortCode, knownStuckAccountNumber, "Pending Patty", "Monzo",
                        outboundOutcome = OutboundOutcome.STUCK_PENDING,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun returnsTheConfiguredOutcomeForAKnownDestination() {
        assertThat(resolver.resolve(knownSettlesSortCode, knownSettlesAccountNumber))
            .isEqualTo(OutboundOutcome.SETTLES)
        assertThat(resolver.resolve(knownReturnsSortCode, knownReturnsAccountNumber))
            .isEqualTo(OutboundOutcome.RETURNS)
        assertThat(resolver.resolve(knownStuckSortCode, knownStuckAccountNumber))
            .isEqualTo(OutboundOutcome.STUCK_PENDING)
    }

    @Test
    fun defaultsToSettlesForAnUnknownDestination() {
        assertThat(resolver.resolve("60-00-01", "00000000"))
            .isEqualTo(OutboundOutcome.SETTLES)
    }
}
