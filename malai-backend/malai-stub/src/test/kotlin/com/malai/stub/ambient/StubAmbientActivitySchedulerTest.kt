package com.malai.stub.ambient

import com.malai.commons.TimeService
import com.malai.stub.scheme.StubBacsInboundNotifier
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Instant

class StubAmbientActivitySchedulerTest {

    private val notifier = mock(StubBacsInboundNotifier::class.java)
    private val timeService: TimeService = mock(TimeService::class.java)

    private val payrollProperties = StubAmbientPayrollProperties(
        enabled = true,
        schedules = listOf(
            StubAmbientPayrollSchedule(
                beneficiarySortCode = "65-82-37",
                beneficiaryAccountNumber = "10000001",
                beneficiaryName = "Alice Walker",
                originatorSortCode = "20-00-05",
                originatorAccountNumber = "20000020",
                originatorName = "ACME Employer Ltd",
                amountMinorUnits = 250_000L,
                dayOfMonth = 25,
                description = "Salary",
            ),
        ),
    )

    private val scheduler = StubAmbientActivityScheduler(payrollProperties, notifier, timeService)

    @Test
    fun emitsBacsInboundOnTheConfiguredDayOfMonth() {
        `when`(timeService.now()).thenReturn(Instant.parse("2026-05-25T09:00:00Z"))

        scheduler.tick()

        verify(notifier).emitBacsInbound(
            "65-82-37", "10000001", "Alice Walker",
            "20-00-05", "20000020", "ACME Employer Ltd",
            250_000L, "GBP", "Salary",
        )
    }

    @Test
    fun doesNotEmitOnOtherDaysOfTheMonth() {
        `when`(timeService.now()).thenReturn(Instant.parse("2026-05-15T09:00:00Z"))

        scheduler.tick()

        verifyNoInteractions(notifier)
    }

    @Test
    fun doesNotEmitTwiceOnTheSameDayWhenTickedRepeatedly() {
        `when`(timeService.now()).thenReturn(Instant.parse("2026-05-25T09:00:00Z"))

        scheduler.tick()
        scheduler.tick()
        scheduler.tick()

        verify(notifier, times(1)).emitBacsInbound(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), any(),
        )
    }

    @Test
    fun emitsAgainTheNextMonthOnTheConfiguredDay() {
        `when`(timeService.now()).thenReturn(Instant.parse("2026-05-25T09:00:00Z"))
        scheduler.tick()

        `when`(timeService.now()).thenReturn(Instant.parse("2026-06-25T09:00:00Z"))
        scheduler.tick()

        verify(notifier, times(2)).emitBacsInbound(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), any(),
        )
    }

    @Test
    fun firesEachConfiguredScheduleOnTheSameDayOfMonth() {
        val twoSchedules = StubAmbientPayrollProperties(
            enabled = true,
            schedules = listOf(
                payrollProperties.schedules[0],
                payrollProperties.schedules[0].copy(
                    beneficiaryAccountNumber = "10000002",
                    amountMinorUnits = 100_000L,
                ),
            ),
        )
        val multiScheduler = StubAmbientActivityScheduler(twoSchedules, notifier, timeService)
        `when`(timeService.now()).thenReturn(Instant.parse("2026-05-25T09:00:00Z"))

        multiScheduler.tick()

        verify(notifier, times(2)).emitBacsInbound(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), any(),
        )
    }
}
