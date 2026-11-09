package com.malai.copilot.service

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.model.LedgerEntryProjection
import com.malai.client.malai.payments.MalaiPaymentsClient
import com.malai.client.malai.payments.model.InternalPaymentResponse
import com.malai.commons.exception.MalaiCode
import com.malai.commons.exception.NotFoundException
import com.malai.copilot.model.SourceRecord
import com.malai.copilot.model.SourceRecordType
import com.malai.copilot.model.Timeline
import com.malai.copilot.model.TimelineBalances
import com.malai.copilot.model.maskIdentifier
import com.malai.copilot.model.maskedAuditState
import com.malai.security.audit.AuditEventRecord
import com.malai.security.audit.AuditReader
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class PaymentTimelineAssembler(
    private val paymentsClient: MalaiPaymentsClient,
    private val ledgerClient: LedgerClient,
    private val auditReader: AuditReader,
    private val serviceTokenClient: CopilotServiceTokenClient,
) {

    fun assemble(paymentId: UUID): Timeline {
        val token = serviceTokenClient.getToken()
        val payment = readPayment(token, paymentId)
        val screeningRulings = screeningRulingsFor(payment)

        val records = buildList {
            add(payment.asSourceRecord())
            holdPlacedForThePayment(token, payment)?.let(::add)
            screeningDecisionOf(payment, screeningRulings)?.let(::add)
            addAll(auditReader.eventsFor(PAYMENT_RESOURCE_TYPE, paymentId.toString())
                .plus(screeningRulings)
                .sortedBy { it.seq }
                .map { it.asSourceRecord() })
            addAll(ledgerEntriesFor(token, payment))
        }.sortedWith(compareBy({ it.moment }, { it.type.timelineRank }))

        return Timeline(
            subjectType = PAYMENT_SUBJECT_TYPE,
            subjectId = paymentId,
            records = records,
            balances = balancesFor(token, payment),
            subjectAmountMinorUnits = payment.amountMinorUnits,
        )
    }

    private fun readPayment(token: String, paymentId: UUID): InternalPaymentResponse =
        try {
            paymentsClient.getPayment(token, paymentId)
        } catch (ex: MalaiClientResponseException) {
            if (ex.statusCode == NOT_FOUND) throw NotFoundException(MalaiCode.COPILOT_SUBJECT_NOT_FOUND) else throw ex
        }

    private val InternalPaymentResponse.holdId: UUID get() = paymentId

    private fun holdPlacedForThePayment(token: String, payment: InternalPaymentResponse): SourceRecord? {
        if (payment.debtorAccountId == null) return null
        val hold = try {
            ledgerClient.getHold(token, payment.holdId)
        } catch (ex: MalaiClientResponseException) {
            if (ex.statusCode == NOT_FOUND) return null else throw ex
        }
        return SourceRecord(
            type = SourceRecordType.HOLD,
            id = hold.holdId.toString(),
            moment = Instant.parse(hold.placedAt),
            summary = listOf(
                "accountId=${hold.accountId}",
                "amountMinorUnits=${hold.amountMinorUnits}",
                "status=${hold.status}",
                "placedAt=${hold.placedAt}",
                "resolvedAt=${hold.resolvedAt ?: STILL_HELD}",
            ).joinToString(" "),
        )
    }

    private fun screeningRulingsFor(payment: InternalPaymentResponse): List<AuditEventRecord> =
        payment.screeningDecisionId
            ?.let { auditReader.eventsFor(MODEL_DECISION_RESOURCE_TYPE, it.toString()) }
            .orEmpty()

    private fun screeningDecisionOf(
        payment: InternalPaymentResponse,
        rulings: List<AuditEventRecord>,
    ): SourceRecord? {
        val decisionId = payment.screeningDecisionId ?: return null
        return SourceRecord(
            type = SourceRecordType.SCREENING_DECISION,
            id = decisionId.toString(),
            moment = rulings.firstOrNull()?.ts ?: Instant.parse(payment.createdAt),
            summary = listOf(
                "decisionId=$decisionId",
                "outcome=${rulings.stateOf(DECISION_RECORDED, "outcome") ?: NOT_RECORDED}",
                "disposition=${rulings.stateOf(DISPOSITION_RECORDED, "disposition") ?: NOT_RULED}",
            ).joinToString(" "),
        )
    }

    private fun List<AuditEventRecord>.stateOf(action: String, key: String): String? =
        lastOrNull { it.action == action }?.afterState?.get(key)?.toString()

    private fun ledgerEntriesFor(token: String, payment: InternalPaymentResponse): List<SourceRecord> {
        val traceId = payment.traceId ?: return emptyList()
        return ledgerClient.listTransactionsForTrace(token, traceId).transactions
            .flatMap { transaction -> transaction.entries.map { it.asSourceRecord(transaction.type) } }
    }

    private fun balancesFor(token: String, payment: InternalPaymentResponse): TimelineBalances? {
        val accountId = payment.debtorAccountId ?: return null
        val balances = ledgerClient.getBalances(token, accountId)
        return TimelineBalances(
            accountId = balances.accountId,
            ledgerBalanceMinorUnits = balances.ledgerBalanceMinorUnits,
            heldAmountMinorUnits = balances.heldAmountMinorUnits,
            availableBalanceMinorUnits = balances.availableBalanceMinorUnits,
        )
    }

    private fun InternalPaymentResponse.asSourceRecord() = SourceRecord(
        type = SourceRecordType.PAYMENT,
        id = paymentId.toString(),
        moment = Instant.parse(createdAt),
        summary = listOf(
            "state=$state",
            "rail=$rail",
            "direction=$direction",
            "amountMinorUnits=$amountMinorUnits",
            "currency=$currency",
            "debtor=${maskedParty(debtorSortCode, debtorAccountNo, debtorIban)}",
            "beneficiary=${maskedParty(beneficiarySortCode, beneficiaryAccountNo, beneficiaryIban)}",
            "beneficiaryName=${beneficiaryName ?: NOT_SUPPLIED}",
            "purpose=${paymentPurpose ?: NOT_SUPPLIED}",
            "endToEndId=$endToEndId",
            "screeningDecisionId=${screeningDecisionId ?: NOT_SUPPLIED}",
        ).joinToString(" "),
    )

    private fun AuditEventRecord.asSourceRecord() = SourceRecord(
        type = SourceRecordType.AUDIT_EVENT,
        id = eventId.toString(),
        moment = ts,
        summary = "action=$action actor=$actorType " +
            "afterState=${maskedAuditState(afterState.orEmpty()).toSortedMap()}",
    )

    private fun LedgerEntryProjection.asSourceRecord(transactionType: String) = SourceRecord(
        type = SourceRecordType.LEDGER_ENTRY,
        id = entryId.toString(),
        moment = Instant.parse(postedAt),
        summary = listOf(
            "transactionId=$transactionId",
            "transactionType=$transactionType",
            "accountId=${accountId ?: NOT_SUPPLIED}",
            "amountMinorUnits=$amountMinorUnits",
            "runningBalanceMinorUnits=$runningBalanceMinorUnits",
            "description=${description ?: NOT_SUPPLIED}",
        ).joinToString(" "),
    )

    private fun maskedParty(sortCode: String?, accountNo: String?, iban: String?): String = when {
        iban != null -> maskIdentifier(iban)
        accountNo != null -> "${sortCode.orEmpty()} ${maskIdentifier(accountNo)}".trim()
        else -> NOT_SUPPLIED
    }

    private companion object {
        const val PAYMENT_RESOURCE_TYPE = "Payment"
        const val MODEL_DECISION_RESOURCE_TYPE = "ModelDecision"
        const val PAYMENT_SUBJECT_TYPE = "PAYMENT"
        const val DECISION_RECORDED = "MDR_DECISION_RECORDED"
        const val DISPOSITION_RECORDED = "MDR_DISPOSITION_RECORDED"
        const val NOT_SUPPLIED = "not supplied"
        const val NOT_RECORDED = "not recorded"
        const val NOT_RULED = "not ruled"
        const val STILL_HELD = "still held"
        const val NOT_FOUND = 404
    }
}
