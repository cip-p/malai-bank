package com.malai.stub.scheme

import com.malai.scheme.contracts.bacs.BacsSchemeAmount
import com.malai.scheme.contracts.bacs.BacsSchemeDirectCredit
import com.malai.scheme.contracts.bacs.BacsSchemeParty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StubBacsInboundNotifier(private val gatewayClient: StubBacsGatewayClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun emitBacsInbound(
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
        beneficiaryName: String,
        originatorSortCode: String,
        originatorAccountNumber: String,
        originatorName: String,
        amountMinorUnits: Long,
        currency: String,
        description: String?,
    ): UUID {
        val endToEndId = UUID.randomUUID()
        gatewayClient.notifyInbound(
            BacsSchemeDirectCredit(
                endToEndId = endToEndId,
                creditor = BacsSchemeParty(beneficiarySortCode, beneficiaryAccountNumber, beneficiaryName),
                originator = BacsSchemeParty(originatorSortCode, originatorAccountNumber, originatorName),
                instructedAmount = BacsSchemeAmount(amountMinorUnits, currency),
                remittanceInformation = description,
            ),
        )
        log.info(
            "stub bacs inbound: notified gateway endToEndId={} beneficiary={}/{} amount={}",
            endToEndId, beneficiarySortCode, beneficiaryAccountNumber, amountMinorUnits,
        )
        return endToEndId
    }
}
