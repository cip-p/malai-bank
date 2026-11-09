package com.malai.ledger.acceptance.happy

import com.malai.commons.model.Currency.GBP
import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.ledger.entity.LedgerAccountEntity
import com.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

class LedgerAccountsControllerTest : LedgerAcceptanceTest() {

    @Test
    fun registerLedgerAccount() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/accounts")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(RegisterLedgerAccountRequest(
                    accountId = someAccountUid(),
                    currency = "GBP",
                    kind = CUSTOMER,
                    guardPolicy = NO_OVERDRAFT,
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "currency" To "GBP"
                "kind" To "CUSTOMER"
                "guardPolicy" To "NO_OVERDRAFT"
                noExtraProps()
            }
        }

        and(theLedgerAccountInTheDatabase(someAccountUid())) {
            this shouldBe LedgerAccountEntity(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT)
        }
    }

    @Test
    fun registerLedgerAccountWithAFloor() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/accounts")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(RegisterLedgerAccountRequest(
                    accountId = someAccountUid(),
                    currency = "GBP",
                    kind = CUSTOMER,
                    guardPolicy = FLOORED,
                    floorMinorUnits = -250_000L,
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "currency" To "GBP"
                "kind" To "CUSTOMER"
                "guardPolicy" To "FLOORED"
                "floorMinorUnits" To -250_000
                noExtraProps()
            }
        }

        and(theLedgerAccountInTheDatabase(someAccountUid())) {
            this shouldBe LedgerAccountEntity(someAccountUid(), GBP, CUSTOMER, FLOORED, -250_000L)
        }
    }

    @Test
    fun reRegisteringTheSameAccountIsIdempotent() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/accounts")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(RegisterLedgerAccountRequest(
                    accountId = someAccountUid(),
                    currency = "GBP",
                    kind = CUSTOMER,
                    guardPolicy = NO_OVERDRAFT,
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theLedgerAccountsInTheDatabase()) {
            this shouldHaveSize 1
        }
    }
}
