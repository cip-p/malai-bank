package com.malai.stub.directory

import com.malai.payments.messages.ReturnReason
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class StubFictionalDirectoryPropertiesTest {

    @Test
    fun acceptsRowsWhereReturnReasonIsNullAndOutboundOutcomeIsSettles() {
        assertThatCode {
            StubFictionalDirectoryProperties(
                accounts = listOf(
                    StubAccount("60-00-01", "12345678", "Alice", "NatWest"),
                )
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun acceptsRowsWhereReturnReasonIsNullAndOutboundOutcomeIsStuckPending() {
        assertThatCode {
            StubFictionalDirectoryProperties(
                accounts = listOf(
                    StubAccount("60-00-01", "12345678", "Alice", "NatWest", outboundOutcome = OutboundOutcome.STUCK_PENDING),
                )
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun acceptsRowsWhereReturnReasonIsSetAndOutboundOutcomeIsReturns() {
        assertThatCode {
            StubFictionalDirectoryProperties(
                accounts = listOf(
                    StubAccount(
                        "60-00-01", "12345678", "Alice", "NatWest",
                        outboundOutcome = OutboundOutcome.RETURNS,
                        returnReason = ReturnReason.ACCOUNT_CLOSED,
                    ),
                )
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun rejectsRowsWhereOutboundOutcomeIsReturnsButReturnReasonIsNull() {
        assertThatThrownBy {
            StubFictionalDirectoryProperties(
                accounts = listOf(
                    StubAccount(
                        "60-00-01", "12345678", "Alice", "NatWest",
                        outboundOutcome = OutboundOutcome.RETURNS,
                    ),
                )
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("60-00-01/12345678")
            .hasMessageContaining("returnReason must be non-null iff outboundOutcome=RETURNS")
    }

    @Test
    fun rejectsRowsWhereOutboundOutcomeIsSettlesButReturnReasonIsNonNull() {
        assertThatThrownBy {
            StubFictionalDirectoryProperties(
                accounts = listOf(
                    StubAccount(
                        "60-00-01", "12345678", "Alice", "NatWest",
                        outboundOutcome = OutboundOutcome.SETTLES,
                        returnReason = ReturnReason.ACCOUNT_CLOSED,
                    ),
                )
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("returnReason must be non-null iff outboundOutcome=RETURNS")
    }

    @Test
    fun rejectsRowsWhereOutboundOutcomeIsStuckPendingButReturnReasonIsNonNull() {
        assertThatThrownBy {
            StubFictionalDirectoryProperties(
                accounts = listOf(
                    StubAccount(
                        "60-00-01", "12345678", "Alice", "NatWest",
                        outboundOutcome = OutboundOutcome.STUCK_PENDING,
                        returnReason = ReturnReason.RECIPIENT_BANK_UNAVAILABLE,
                    ),
                )
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("returnReason must be non-null iff outboundOutcome=RETURNS")
    }
}
