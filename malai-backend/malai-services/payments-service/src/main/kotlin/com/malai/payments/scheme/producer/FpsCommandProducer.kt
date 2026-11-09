package com.malai.payments.scheme.producer

import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.tracking.withMalaiTracking
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.messages.FpsOutboundSubmit
import com.malai.sqs.payments.PaymentsQueues
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FpsCommandProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun submit(payment: PaymentEntity) {
        val message = FpsOutboundSubmit(
            paymentId = payment.paymentId,
            endToEndId = payment.endToEndId,
            debtorSortCode = payment.debtorSortCode,
            debtorAccountNumber = payment.debtorAccountNo,
            debtorName = payment.debtorName,
            beneficiarySortCode = payment.beneficiarySortCode,
            beneficiaryAccountNumber = payment.beneficiaryAccountNo,
            beneficiaryName = payment.beneficiaryName,
            amountMinorUnits = payment.amountMinorUnits,
            currency = payment.currency.name,
            description = payment.description,
            copVerdictOverride = payment.copVerdictOverride,
        )
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_COMMAND_SUBMIT)
        sqsTemplate.send<FpsOutboundSubmit> { options ->
            options
                .queue(queueName)
                .payload(message)
                .messageGroupId(payment.paymentId.toString())
                .messageDeduplicationId(payment.paymentId.toString())
                .withMalaiTracking()
        }
        log.info("fps outbound submit published: paymentId={} endToEndId={}", payment.paymentId, payment.endToEndId)
    }
}
