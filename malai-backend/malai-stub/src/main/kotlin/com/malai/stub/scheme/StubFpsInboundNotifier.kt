package com.malai.stub.scheme

import com.malai.scheme.contracts.fps.FpsSchemeAmount
import com.malai.scheme.contracts.fps.FpsSchemeInboundNotification
import com.malai.scheme.contracts.fps.FpsSchemeParty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StubFpsInboundNotifier(private val gatewayClient: StubFpsGatewayClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun emitFpsInbound(
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
            FpsSchemeInboundNotification(
                endToEndId = endToEndId,
                creditor = FpsSchemeParty(beneficiarySortCode, beneficiaryAccountNumber, beneficiaryName),
                debtor = FpsSchemeParty(originatorSortCode, originatorAccountNumber, originatorName),
                instructedAmount = FpsSchemeAmount(amountMinorUnits, currency),
                remittanceInformation = description,
            ),
        )
        log.info(
            "stub fps inbound: notified gateway endToEndId={} beneficiary={}/{} amount={}",
            endToEndId, beneficiarySortCode, beneficiaryAccountNumber, amountMinorUnits,
        )
        return endToEndId
    }
}
