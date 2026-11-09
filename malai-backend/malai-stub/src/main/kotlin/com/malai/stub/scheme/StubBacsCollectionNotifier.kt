package com.malai.stub.scheme

import com.malai.scheme.contracts.bacs.BacsSchemeAmount
import com.malai.scheme.contracts.bacs.BacsSchemeDirectDebitCollection
import com.malai.scheme.contracts.bacs.BacsSchemeParty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StubBacsCollectionNotifier(private val gatewayClient: StubBacsGatewayClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun emitBacsCollection(
        endToEndId: UUID,
        debtorSortCode: String,
        debtorAccountNumber: String,
        debtorName: String,
        creditorSortCode: String,
        creditorAccountNumber: String,
        creditorName: String,
        amountMinorUnits: Long,
        currency: String,
        description: String?,
    ): UUID {
        gatewayClient.notifyCollection(
            BacsSchemeDirectDebitCollection(
                endToEndId = endToEndId,
                creditor = BacsSchemeParty(creditorSortCode, creditorAccountNumber, creditorName),
                debtor = BacsSchemeParty(debtorSortCode, debtorAccountNumber, debtorName),
                instructedAmount = BacsSchemeAmount(amountMinorUnits, currency),
                remittanceInformation = description,
            ),
        )
        log.info(
            "stub bacs collection: notified gateway endToEndId={} debtor={}/{} creditor={}/{} amount={}",
            endToEndId, debtorSortCode, debtorAccountNumber, creditorSortCode, creditorAccountNumber, amountMinorUnits,
        )
        return endToEndId
    }
}
