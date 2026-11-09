package com.malai.payments.scheme.producer

import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.tracking.withMalaiTracking
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.fx.FxConversion
import com.malai.sqs.payments.PaymentsQueues
import com.malai.payments.messages.SwiftOutboundSubmit
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SwiftCommandProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun submit(payment: PaymentEntity, fxConversion: FxConversion?) {
        val targetAmount = fxConversion?.amountMinorUnits ?: payment.amountMinorUnits
        val targetCurrency = fxConversion?.currency?.name ?: payment.currency.name
        val message = SwiftOutboundSubmit(
            paymentId = payment.paymentId,
            endToEndId = payment.endToEndId,
            debtorIban = payment.debtorIban,
            debtorBic = payment.debtorBic,
            debtorName = payment.debtorName,
            beneficiaryIban = payment.beneficiaryIban
                ?: error("SWIFT submit requires beneficiaryIban; paymentId=${payment.paymentId}"),
            beneficiaryBic = payment.beneficiaryBic
                ?: error("SWIFT submit requires beneficiaryBic; paymentId=${payment.paymentId}"),
            beneficiaryName = payment.beneficiaryName,
            paymentPurpose = payment.paymentPurpose,
            amountMinorUnits = payment.amountMinorUnits,
            currency = payment.currency.name,
            targetAmountMinorUnits = targetAmount,
            targetCurrency = targetCurrency,
            description = payment.description,
        )
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.SWIFT_COMMAND_SUBMIT)
        sqsTemplate.send<SwiftOutboundSubmit> { options ->
            options
                .queue(queueName)
                .payload(message)
                .messageGroupId(payment.paymentId.toString())
                .messageDeduplicationId(payment.paymentId.toString())
                .withMalaiTracking()
        }
        log.info("swift outbound submit published: paymentId={} endToEndId={}", payment.paymentId, payment.endToEndId)
    }
}
