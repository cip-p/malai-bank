package com.malai.stub.scheme

import com.malai.payments.messages.ReturnReason
import com.malai.stub.directory.OutboundOutcome
import com.malai.stub.directory.OutboundOutcomeResolver
import com.malai.stub.directory.StubAccount
import com.malai.stub.directory.StubFictionalDirectory
import com.malai.stub.directory.StubFictionalDirectoryProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class StubFpsSchemeTest {

    private val knownSortCode = "60-00-01"
    private val knownAccountNumber = "12345678"

    private val magicClosedSortCode = "60-00-01"
    private val magicClosedAccountNumber = "99000001"

    private val magicMismatchSortCode = "04-00-04"
    private val magicMismatchAccountNumber = "99000004"

    private val magicStuckSortCode = "04-00-04"
    private val magicStuckAccountNumber = "99000099"

    private val directory = StubFictionalDirectory(
        StubFictionalDirectoryProperties(
            accounts = listOf(
                StubAccount(knownSortCode, knownAccountNumber, "Alice Walker", "NatWest"),
                StubAccount(
                    magicClosedSortCode, magicClosedAccountNumber, "Jane Closed", "NatWest",
                    outboundOutcome = OutboundOutcome.RETURNS,
                    returnReason = ReturnReason.ACCOUNT_CLOSED,
                ),
                StubAccount(
                    magicMismatchSortCode, magicMismatchAccountNumber, "Mistake Likely Ltd", "Monzo",
                    outboundOutcome = OutboundOutcome.RETURNS,
                    returnReason = ReturnReason.ACCOUNT_NAME_MISMATCH,
                ),
                StubAccount(
                    magicStuckSortCode, magicStuckAccountNumber, "Pending Patty", "Monzo",
                    outboundOutcome = OutboundOutcome.STUCK_PENDING,
                ),
            ),
        ),
    )

    private val scheme = StubFpsScheme(
        directory,
        OutboundOutcomeResolver(directory),
        mock(StubFpsGatewayClient::class.java),
    )

    @Test
    fun settlesWhenBeneficiaryIsInTheStubDirectory() {
        assertThat(decide(knownSortCode, knownAccountNumber)).isEqualTo(FpsDecision.Settle)
    }

    @Test
    fun settlesWhenBeneficiaryIsNotInTheStubDirectory() {
        assertThat(decide("60-00-01", "00000000")).isEqualTo(FpsDecision.Settle)
    }

    @Test
    fun returnsConfiguredReasonForMagicAccountInDirectory() {
        assertThat(decide(magicClosedSortCode, magicClosedAccountNumber))
            .isEqualTo(FpsDecision.Return(ReturnReason.ACCOUNT_CLOSED))
    }

    @Test
    fun staysPendingForStuckPendingCounterparty() {
        assertThat(decide(magicStuckSortCode, magicStuckAccountNumber))
            .isEqualTo(FpsDecision.StuckPending)
    }

    @Test
    fun copVerdictOverrideForcesAccountNameMismatchEvenForKnownDestination() {
        assertThat(decide(knownSortCode, knownAccountNumber, copVerdictOverride = true))
            .isEqualTo(FpsDecision.Return(ReturnReason.ACCOUNT_NAME_MISMATCH))
    }

    @Test
    fun copVerdictOverrideBeatsStuckPending() {
        assertThat(decide(magicStuckSortCode, magicStuckAccountNumber, copVerdictOverride = true))
            .isEqualTo(FpsDecision.Return(ReturnReason.ACCOUNT_NAME_MISMATCH))
    }

    @Test
    fun armFlagOverridesMagicAccount() {
        scheme.armReturn(ReturnReason.RECIPIENT_BANK_UNAVAILABLE)

        assertThat(decide(magicClosedSortCode, magicClosedAccountNumber))
            .isEqualTo(FpsDecision.Return(ReturnReason.RECIPIENT_BANK_UNAVAILABLE))
    }

    @Test
    fun armFlagOverridesStuckPending() {
        scheme.armReturn(ReturnReason.RECIPIENT_BANK_UNAVAILABLE)

        assertThat(decide(magicStuckSortCode, magicStuckAccountNumber))
            .isEqualTo(FpsDecision.Return(ReturnReason.RECIPIENT_BANK_UNAVAILABLE))
    }

    @Test
    fun copVerdictOverrideBeatsArmFlag() {
        scheme.armReturn(ReturnReason.RECIPIENT_BANK_UNAVAILABLE)

        assertThat(decide(knownSortCode, knownAccountNumber, copVerdictOverride = true))
            .isEqualTo(FpsDecision.Return(ReturnReason.ACCOUNT_NAME_MISMATCH))
    }

    @Test
    fun copVerdictOverrideBeatsMagicAccount() {
        assertThat(decide(magicMismatchSortCode, magicMismatchAccountNumber, copVerdictOverride = true))
            .isEqualTo(FpsDecision.Return(ReturnReason.ACCOUNT_NAME_MISMATCH))
    }

    @Test
    fun armFlagIsOneShotAndClearsAfterFirstUse() {
        scheme.armReturn(ReturnReason.BENEFICIARY_DECEASED)

        assertThat(decide(knownSortCode, knownAccountNumber))
            .isEqualTo(FpsDecision.Return(ReturnReason.BENEFICIARY_DECEASED))
        assertThat(decide(knownSortCode, knownAccountNumber)).isEqualTo(FpsDecision.Settle)
    }

    private fun decide(
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
        copVerdictOverride: Boolean = false,
    ) = scheme.decide(copVerdictOverride, beneficiarySortCode, beneficiaryAccountNumber)
}
