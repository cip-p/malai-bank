package com.malai.payments.screening

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.mdr.MdrClient
import com.malai.client.malai.mdr.model.ModelDecisionOutcome
import com.malai.client.malai.mdr.model.RecordModelDecisionRequest
import com.malai.client.malai.mdr.model.RecordModelDecisionResponse
import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.Currency
import com.malai.payments.entity.Rail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.UUID

private val SOME_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000c1")
private val SOME_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000e1")
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
private val SOME_PAYER_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000b1")
private const val SOME_SERVICE_TOKEN = "a-service-token"

class PaymentScreenerTest {

    private val properties = ScreeningProperties()
    private val watchlist = ScreeningWatchlist(properties)
    private val prompt = PaymentScreeningPrompt(watchlist)
    private val mdrClient = RecordingMdrClient()

    @Test
    fun recordsACompletedDecisionCarryingTheVerdictAndItsRawOutput() {
        val result = screenerFor(RulesScreeningDecider(properties, watchlist))
            .screen(aSubject(), SOME_SERVICE_TOKEN)

        result.decisionId shouldBe SOME_DECISION_ID
        result.verdict?.decision shouldBe ScreeningDecision.APPROVE
        result.flagged shouldBe false

        mdrClient.recorded shouldHaveSize 1
        mdrClient.lastServiceToken shouldBe SOME_SERVICE_TOKEN
        with(mdrClient.recorded.single()) {
            entryId shouldBe SOME_ENTRY_ID
            decisionId shouldBe SOME_DECISION_ID
            correlationType shouldBe "PAYMENT"
            correlationId shouldBe SOME_PAYMENT_ID.toString()
            outcome shouldBe ModelDecisionOutcome.COMPLETED
            rawOutput shouldBe """{"decision":"APPROVE","riskScore":10,"reasons":[]}"""
            verdict shouldBe mapOf("decision" to "APPROVE", "riskScore" to 10, "reasons" to emptyList<String>())
            errorDetail.shouldBeNull()
            retrievedContext?.map { it.type } shouldBe listOf("ACCOUNT", "WATCHLIST")
            costMicros shouldBe 0L
            costCurrency shouldBe "GBP"
        }
    }

    @Test
    fun recordsATimedOutDecisionWithNoOutputWhenTheDeciderTimesOut() {
        val result = screenerFor(deciderThatThrows(ScreeningTimedOutException("timed out")))
            .screen(aSubject(), SOME_SERVICE_TOKEN)

        result.verdict.shouldBeNull()
        result.flagged shouldBe false

        with(mdrClient.recorded.single()) {
            outcome shouldBe ModelDecisionOutcome.TIMED_OUT
            rawOutput.shouldBeNull()
            verdict.shouldBeNull()
            errorDetail.shouldBeNull()
        }
    }

    @Test
    fun recordsAnErroredDecisionCarryingTheFailureDetailWhenTheDeciderBlowsUp() {
        val result = screenerFor(deciderThatThrows(IllegalStateException("decider exploded")))
            .screen(aSubject(), SOME_SERVICE_TOKEN)

        result.verdict.shouldBeNull()

        with(mdrClient.recorded.single()) {
            outcome shouldBe ModelDecisionOutcome.ERRORED
            rawOutput.shouldBeNull()
            verdict.shouldBeNull()
            errorDetail shouldBe "decider exploded"
        }
    }

    @Test
    fun failsClosedWhenTheDecisionCannotBeRecorded() {
        mdrClient.failWith = MalaiClientResponseException(500, 201, null)

        shouldThrow<MalaiClientResponseException> {
            screenerFor(RulesScreeningDecider(properties, watchlist)).screen(aSubject(), SOME_SERVICE_TOKEN)
        }
    }

    @Test
    fun defaultsToShadowMode() {
        ScreeningProperties().mode shouldBe ScreeningMode.SHADOW
    }

    private fun screenerFor(decider: ScreeningDecider) = PaymentScreener(
        decider = decider,
        prompt = prompt,
        watchlist = watchlist,
        mdrClient = mdrClient,
        uuidGenerator = QueuedUuidGenerator(SOME_DECISION_ID, SOME_ENTRY_ID),
    )

    private fun deciderThatThrows(failure: Exception) = object : ScreeningDecider {
        override val modelId = "malai-screening-rules"
        override val modelVersion = "1"
        override fun decide(subject: ScreeningSubject, renderedPrompt: String) = throw failure
    }

    private fun aSubject() = ScreeningSubject(
        paymentId = SOME_PAYMENT_ID,
        rail = Rail.FPS,
        amountMinorUnits = 2_500L,
        currency = Currency.GBP,
        beneficiaryName = "Bob Harrison",
        beneficiaryIdentifier = "65-82-37 ****4321",
        paymentPurpose = null,
        payerAccount = PayerAccountSnapshot(
            accountId = SOME_PAYER_ACCOUNT_ID,
            name = "Personal",
            identifier = "65-82-37 ****0001",
            currency = Currency.GBP,
        ),
    )

    private class QueuedUuidGenerator(vararg uuids: UUID) : UuidGenerator {
        private val queue = ArrayDeque(uuids.toList())
        override fun generate(): UUID = queue.poll() ?: UUID.randomUUID()
    }

    private class RecordingMdrClient : MdrClient(HttpClients.createDefault(), "http://localhost") {
        val recorded = mutableListOf<RecordModelDecisionRequest>()
        var failWith: RuntimeException? = null
        var lastServiceToken: String? = null

        override fun recordModelDecision(
            serviceToken: String,
            request: RecordModelDecisionRequest,
        ): RecordModelDecisionResponse {
            lastServiceToken = serviceToken
            failWith?.let { throw it }
            recorded += request
            return RecordModelDecisionResponse(
                entryId = request.entryId,
                decisionId = request.decisionId,
                seq = 1L,
                outcome = request.outcome.name,
            )
        }
    }
}
