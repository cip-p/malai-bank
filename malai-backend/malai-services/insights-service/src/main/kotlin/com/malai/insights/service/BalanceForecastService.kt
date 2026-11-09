package com.malai.insights.service

import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.insights.aggregator.BalanceForecastAggregator
import com.malai.insights.model.BalanceForecastRequest
import com.malai.insights.model.BalanceForecastResponse
import com.malai.insights.model.ForecastInstrument
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class BalanceForecastService(
    private val aggregator: BalanceForecastAggregator,
) {

    fun forecast(request: BalanceForecastRequest): BalanceForecastResponse =
        aggregator.aggregate(
            asOfDate = LocalDate.parse(request.asOfDate),
            availableBalanceMinorUnits = request.availableBalanceMinorUnits,
            currency = request.currency,
            instruments = request.instruments.map {
                ForecastInstrument(
                    instrumentId = it.instrumentId,
                    type = it.type,
                    counterpartyName = it.counterpartyName,
                    amountMinorUnits = it.amountMinorUnits,
                    frequency = Frequency.valueOf(it.frequency),
                    startDate = LocalDate.parse(it.startDate),
                    status = InstrumentStatus.valueOf(it.status),
                )
            },
        )
}
