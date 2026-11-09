package com.malai.stub.ambient

import com.malai.commons.TimeService
import com.malai.stub.scheme.StubBacsInboundNotifier
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

@Configuration
@EnableConfigurationProperties(StubAmbientPayrollProperties::class)
class StubAmbientActivityConfiguration

@Component
@ConditionalOnProperty(
    prefix = "malai.stub.ambient.payroll",
    name = ["enabled"],
    havingValue = "true",
)
class StubAmbientActivityScheduler(
    private val properties: StubAmbientPayrollProperties,
    private val notifier: StubBacsInboundNotifier,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lastFiredByKey: MutableMap<String, LocalDate> = ConcurrentHashMap()

    @Scheduled(cron = "\${malai.stub.ambient.payroll.cron:0 0 9 * * *}")
    fun tick() {
        val today = LocalDate.ofInstant(timeService.now(), ZoneId.of("UTC"))
        for (schedule in properties.schedules) {
            fireIfDue(schedule, today)
        }
    }

    private fun fireIfDue(schedule: StubAmbientPayrollSchedule, today: LocalDate) {
        if (schedule.dayOfMonth != today.dayOfMonth) return
        val key = "${schedule.beneficiarySortCode}/${schedule.beneficiaryAccountNumber}/${schedule.dayOfMonth}"
        val previouslyFired = lastFiredByKey[key]
        if (previouslyFired == today) {
            log.debug("ambient payroll already fired today: key={} date={}", key, today)
            return
        }

        notifier.emitBacsInbound(
            beneficiarySortCode = schedule.beneficiarySortCode,
            beneficiaryAccountNumber = schedule.beneficiaryAccountNumber,
            beneficiaryName = schedule.beneficiaryName,
            originatorSortCode = schedule.originatorSortCode,
            originatorAccountNumber = schedule.originatorAccountNumber,
            originatorName = schedule.originatorName,
            amountMinorUnits = schedule.amountMinorUnits,
            currency = schedule.currency,
            description = schedule.description,
        )
        lastFiredByKey[key] = today
        log.info(
            "ambient payroll fired: beneficiary={}/{} amount={} originator={} date={}",
            schedule.beneficiarySortCode, schedule.beneficiaryAccountNumber,
            schedule.amountMinorUnits, schedule.originatorName, today,
        )
    }
}

@ConfigurationProperties(prefix = "malai.stub.ambient.payroll")
data class StubAmbientPayrollProperties(
    val enabled: Boolean = false,
    val cron: String = "0 0 9 * * *",
    val schedules: List<StubAmbientPayrollSchedule> = emptyList(),
)

data class StubAmbientPayrollSchedule(
    val beneficiarySortCode: String,
    val beneficiaryAccountNumber: String,
    val beneficiaryName: String,
    val originatorSortCode: String,
    val originatorAccountNumber: String,
    val originatorName: String,
    val amountMinorUnits: Long,
    val dayOfMonth: Int,
    val currency: String = "GBP",
    val description: String? = null,
)
