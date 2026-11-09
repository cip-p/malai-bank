package com.malai.auth.acceptance.audit

import tools.jackson.databind.ObjectMapper
import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditEventRecord
import com.malai.security.audit.AuditReader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class AuditReaderTest : AuthAcceptanceTest(), AuditTestSupport {

    private val reader: AuditReader get() = getBean(AuditReader::class.java)

    @Test
    fun `reads every event for a resource in ascending chain order`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("PAYMENT_SUBMITTED", at = "2026-05-06T10:00:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("PAYMENT_REFERRED", at = "2026-05-06T10:01:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("PAYMENT_RELEASED", at = "2026-05-06T10:02:00Z")))

        val events = reader.eventsFor(PAYMENT, somePaymentId)

        events.map { it.action } shouldBe listOf("PAYMENT_SUBMITTED", "PAYMENT_REFERRED", "PAYMENT_RELEASED")
        events.map { it.seq } shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `projects the whole audit row for the resource`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someActorId,
            actorType = ActorType.CUSTOMER,
            action = "PAYMENT_REFERRED",
            resourceType = PAYMENT,
            resourceId = somePaymentId,
            beforeState = mapOf("state" to "SUBMITTED"),
            afterState = mapOf("state" to "REFERRED", "amountMinorUnits" to 150),
            ts = Instant.parse("2026-05-06T10:00:00Z"),
            traceId = "trace-referred",
        )))

        var recordedEventId: UUID? = null
        and(theAuditEventsInTheDatabase()) {
            recordedEventId = this.single()["event_id"] as UUID
        }

        reader.eventsFor(PAYMENT, somePaymentId).single() shouldBe AuditEventRecord(
            eventId = recordedEventId!!,
            seq = 1L,
            ts = Instant.parse("2026-05-06T10:00:00Z"),
            actorId = someActorId,
            actorType = ActorType.CUSTOMER,
            action = "PAYMENT_REFERRED",
            resourceType = PAYMENT,
            resourceId = somePaymentId,
            beforeState = mapOf("state" to "SUBMITTED"),
            afterState = mapOf("state" to "REFERRED", "amountMinorUnits" to 150),
            traceId = "trace-referred",
        )
    }

    @Test
    fun `excludes events belonging to another resource of the same type`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("PAYMENT_SUBMITTED", at = "2026-05-06T10:00:00Z")))
        and(anAuditEventIsRecorded(paymentEvent(
            "PAYMENT_SUBMITTED",
            at = "2026-05-06T10:01:00Z",
            resourceId = someOtherPaymentId,
        )))

        reader.eventsFor(PAYMENT, somePaymentId).map { it.seq } shouldBe listOf(1L)
    }

    @Test
    fun `excludes events of another resource type sharing the same id`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("PAYMENT_SUBMITTED", at = "2026-05-06T10:00:00Z")))
        and(anAuditEventIsRecorded(paymentEvent(
            "MANDATE_CREATED",
            at = "2026-05-06T10:01:00Z",
            resourceType = "DirectDebitMandate",
        )))

        reader.eventsFor(PAYMENT, somePaymentId).map { it.action } shouldBe listOf("PAYMENT_SUBMITTED")
    }

    @Test
    fun `an unknown resource reads back as no events rather than an error`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("PAYMENT_SUBMITTED", at = "2026-05-06T10:00:00Z")))

        reader.eventsFor(PAYMENT, someOtherPaymentId).shouldBeEmpty()
    }

    @Test
    fun `the window admits events from its start inclusive to its end exclusive`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("BEFORE_WINDOW", at = "2026-05-06T09:59:59Z")))
        and(anAuditEventIsRecorded(paymentEvent("AT_WINDOW_START", at = "2026-05-06T10:00:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("INSIDE_WINDOW", at = "2026-05-06T10:30:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("AT_WINDOW_END", at = "2026-05-06T11:00:00Z")))

        reader.eventsFor(
            PAYMENT,
            somePaymentId,
            from = Instant.parse("2026-05-06T10:00:00Z"),
            until = Instant.parse("2026-05-06T11:00:00Z"),
        ).map { it.action } shouldBe listOf("AT_WINDOW_START", "INSIDE_WINDOW")
    }

    @Test
    fun `a half-open window bounds only the side the caller names`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("EARLY", at = "2026-05-06T09:00:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("LATE", at = "2026-05-06T11:00:00Z")))

        reader.eventsFor(PAYMENT, somePaymentId, from = Instant.parse("2026-05-06T10:00:00Z"))
            .map { it.action } shouldBe listOf("LATE")

        reader.eventsFor(PAYMENT, somePaymentId, until = Instant.parse("2026-05-06T10:00:00Z"))
            .map { it.action } shouldBe listOf("EARLY")
    }

    @Test
    fun `the cap truncates to the earliest events in chain order when the caller names no limit`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("FIRST", at = "2026-05-06T10:00:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("SECOND", at = "2026-05-06T10:01:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("THIRD", at = "2026-05-06T10:02:00Z")))

        cappedAtTwoEvents().eventsFor(PAYMENT, somePaymentId)
            .map { it.action } shouldBe listOf("FIRST", "SECOND")
    }

    @Test
    fun `a caller may ask for fewer events than the cap but never for more`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(paymentEvent("FIRST", at = "2026-05-06T10:00:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("SECOND", at = "2026-05-06T10:01:00Z")))
        and(anAuditEventIsRecorded(paymentEvent("THIRD", at = "2026-05-06T10:02:00Z")))

        cappedAtTwoEvents().eventsFor(PAYMENT, somePaymentId, limit = 1)
            .map { it.action } shouldBe listOf("FIRST")

        cappedAtTwoEvents().eventsFor(PAYMENT, somePaymentId, limit = 99)
            .map { it.action } shouldBe listOf("FIRST", "SECOND")
    }

    @Test
    fun `a limit of zero or fewer is rejected rather than silently read as the cap`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        shouldThrow<IllegalArgumentException> {
            reader.eventsFor(PAYMENT, somePaymentId, limit = 0)
        }
    }

    @Test
    fun `state maps on a projection cannot be mutated by whoever holds it`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someActorId,
            actorType = ActorType.CUSTOMER,
            action = "PAYMENT_REFERRED",
            resourceType = PAYMENT,
            resourceId = somePaymentId,
            afterState = mapOf("state" to "REFERRED"),
            ts = Instant.parse("2026-05-06T10:00:00Z"),
        )))

        val afterState = reader.eventsFor(PAYMENT, somePaymentId).single().afterState

        shouldThrow<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (afterState as MutableMap<String, Any?>)["state"] = "RELEASED"
        }
    }

    private fun cappedAtTwoEvents() = AuditReader(
        jdbc = getBean(JdbcTemplate::class.java),
        objectMapper = getBean(ObjectMapper::class.java),
        maxEvents = 2,
    )

    private fun paymentEvent(
        action: String,
        at: String,
        resourceType: String = PAYMENT,
        resourceId: String = somePaymentId,
    ) = AuditEvent(
        actorId = someActorId,
        actorType = ActorType.CUSTOMER,
        action = action,
        resourceType = resourceType,
        resourceId = resourceId,
        ts = Instant.parse(at),
        traceId = "trace-$action",
    )

    private companion object {
        const val PAYMENT = "Payment"
        val someActorId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        const val somePaymentId: String = "44444444-4444-4444-4444-444444444444"
        const val someOtherPaymentId: String = "55555555-5555-5555-5555-555555555555"
    }
}
