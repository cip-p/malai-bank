package com.malai.exec.service

import com.malai.client.malai.card.metrics.CardMetricsClient
import com.malai.exec.model.MetricsWindowRange
import org.springframework.stereotype.Component

/**
 * Cards issued: Cards issued during the asked window, read from the context that owns them. An
 * issuance event, not a surviving card — card-service counts by when a row was written and never by
 * status, so a card cancelled the same day it was issued still counts and the figure never silently
 * decreases. Both variants count, and a replacement issued after a loss is an issuance like any other.
 */
@Component
class CardsIssuedSource(
    private val cardMetricsClient: CardMetricsClient,
    private val execServiceTokenClient: ExecServiceTokenClient,
) {

    fun count(window: MetricsWindowRange): Long =
        cardMetricsClient
            .cardsIssued(execServiceTokenClient.getToken(), window.from, window.until)
            .count
}
