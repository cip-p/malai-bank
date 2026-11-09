package com.malai.directdebit.service

import com.malai.client.malai.stub.MalaiStubClient
import com.malai.client.malai.stub.collection.model.CollectionForwardPayload
import com.malai.commons.schedule.OccurrenceSchedule
import com.malai.commons.schedule.ScheduledPaymentEndToEndId
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.directdebit.repository.DirectDebitsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC

/**
 * Requests a single due collection for a mandate, then advances its schedule — the per-mandate
 * half of the collection requester, split out so the transactional boundary applies through the
 * Spring proxy when the scheduler
 * ([com.malai.directdebit.scheduler.DirectDebitCollectionRequester]) invokes it.
 *
 * Request first, advance second (ADR 0004): the stub is asked to originate a Bacs collection for
 * the mandate's fixed amount, carrying a deterministic end-to-end id derived from the mandate id
 * and the occurrence date, so a re-fire after a crash between the two steps dedups harmlessly
 * downstream on the queue-message dedup table. After firing exactly once the schedule
 * resynchronises to the first occurrence strictly after "now" — downtime never produces catch-up
 * bursts. Any request error propagates so the caller leaves the schedule unadvanced to retry on
 * the next run.
 *
 * The stub remains the apparent originator; the mandate table stays the source of schedule truth.
 * Settlement or return (insufficient funds) is decided downstream on the inbound rail and audited
 * there, so this path writes no audit row of its own.
 */
@Service
class DirectDebitCollectionRequestService(
    private val repository: DirectDebitsRepository,
    private val stubClient: MalaiStubClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun requestCollection(mandate: DirectDebitEntity, asOf: Instant) {
        val occurrenceDate = LocalDate.ofInstant(mandate.nextCollectionAt!!, UTC)
        val amountMinorUnits = mandate.amountMinorUnits
            ?: throw IllegalStateException(
                "mandate ${mandate.directDebitId} has no fixed amount; variable mandates are not collectable in v1",
            )
        val debtor = repository.findDebtorSnapshot(mandate.accountId)
            ?: throw IllegalStateException("no debtor account resolves for account ${mandate.accountId}")
        val endToEndId = ScheduledPaymentEndToEndId.forOccurrence(mandate.directDebitId, occurrenceDate)
        val nextCollectionDate =
            OccurrenceSchedule.nextStrictlyAfter(mandate.startDate, mandate.frequency, LocalDate.ofInstant(asOf, UTC))

        stubClient.postCollectionBacs(
            CollectionForwardPayload(
                debtorSortCode = debtor.sortCode,
                debtorAccountNumber = debtor.accountNumber,
                debtorName = debtor.name,
                creditorSortCode = mandate.creditorSortCode,
                creditorAccountNumber = mandate.creditorAccountNumber,
                creditorName = mandate.creditorName,
                amountMinorUnits = amountMinorUnits,
                currency = mandate.currency.name,
                description = mandate.reference,
                endToEndId = endToEndId,
            ),
        )

        repository.save(mandate.copy(nextCollectionAt = nextCollectionDate.atStartOfDay(UTC).toInstant()))

        log.info(
            "direct debit collection requested: directDebitId={} occurrence={} nextCollection={}",
            mandate.directDebitId, occurrenceDate, nextCollectionDate,
        )
    }
}
