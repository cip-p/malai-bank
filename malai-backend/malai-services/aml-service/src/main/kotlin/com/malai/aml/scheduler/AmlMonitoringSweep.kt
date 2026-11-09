package com.malai.aml.scheduler

import com.malai.aml.monitoring.AmlProperties
import com.malai.aml.monitoring.AmlWindow
import com.malai.aml.monitoring.MonitoredPayment
import com.malai.aml.monitoring.OUTBOUND
import com.malai.aml.service.AmlServiceTokenClient
import com.malai.aml.service.AmlWindowEvaluator
import com.malai.client.malai.payments.MalaiPaymentsClient
import com.malai.client.malai.payments.model.SettledPayment
import com.malai.commons.TimeService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AmlMonitoringSweep(
    private val paymentsClient: MalaiPaymentsClient,
    private val serviceTokenClient: AmlServiceTokenClient,
    private val windowEvaluator: AmlWindowEvaluator,
    private val properties: AmlProperties,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(cron = "\${malai.aml.sweep.cron:*/10 * * * * *}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "\${malai.aml.sweep.lock.at-most-for:9s}")
    fun runOnSchedule() = sweep()

    fun sweep() {
        val until = timeService.now()
        val from = until.minus(properties.structuring.window)
        val serviceToken = serviceTokenClient.getToken()
        val settled = paymentsClient.settledPayments(serviceToken, from, until).payments
        settled
            .mapNotNull { monitoredPayment(it) }
            .groupBy { it.accountId }
            .forEach { (accountId, payments) ->
                try {
                    windowEvaluator.evaluate(AmlWindow(accountId, from, until, payments), serviceToken)
                } catch (ex: Exception) {
                    log.error(
                        "aml window evaluation failed, leaving it for the next sweep: accountId={}",
                        accountId,
                        ex,
                    )
                }
            }
    }

    private fun monitoredPayment(payment: SettledPayment): MonitoredPayment? {
        val accountId = when (payment.direction) {
            OUTBOUND -> payment.debtorAccountId
            else -> payment.beneficiaryAccountId
        } ?: return null
        return MonitoredPayment(
            paymentId = payment.paymentId,
            accountId = accountId,
            direction = payment.direction,
            rail = payment.rail,
            amountMinorUnits = payment.amountMinorUnits,
            currency = payment.currency,
            createdAt = Instant.parse(payment.createdAt),
            beneficiaryName = payment.beneficiaryName,
            beneficiarySortCode = payment.beneficiarySortCode,
            beneficiaryAccountNo = payment.beneficiaryAccountNo,
            beneficiaryIban = payment.beneficiaryIban,
            beneficiaryBic = payment.beneficiaryBic,
        )
    }

    companion object {
        const val LOCK_NAME = "aml-monitoring-sweep"
    }
}
