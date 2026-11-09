package com.malai.ledger.acceptance.happy

import com.malai.commons.model.Currency.GBP
import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class LedgerBalancesControllerTest : LedgerAcceptanceTest() {

    private val someHoldId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")

    @Test
    fun getBalancesReturnsLedgerHeldAndAvailable() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/accounts/${someAccountUid()}/balances")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "ledgerBalanceMinorUnits" To 10_000
                "heldAmountMinorUnits" To 2_500
                "availableBalanceMinorUnits" To 7_500
                noExtraProps()
            }
        }
    }

    @Test
    fun getBalancesWithNoHoldsHasAvailableEqualToLedger() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/accounts/${someAccountUid()}/balances")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "ledgerBalanceMinorUnits" To 10_000
                "heldAmountMinorUnits" To 0
                "availableBalanceMinorUnits" To 10_000
                noExtraProps()
            }
        }
    }
}
