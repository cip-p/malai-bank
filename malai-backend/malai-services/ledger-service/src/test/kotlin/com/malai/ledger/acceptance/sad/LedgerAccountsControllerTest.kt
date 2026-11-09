package com.malai.ledger.acceptance.sad

import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST

class LedgerAccountsControllerTest : LedgerAcceptanceTest() {

    @Test
    fun registeringFlooredWithoutAFloorIsRejected() = malaiAcceptanceTest {
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
                    floorMinorUnits = null,
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7967"
            }
        }

        and(theLedgerAccountsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }
}
