package com.malai.exec.service

import com.malai.client.malai.accountholder.metrics.AccountHolderMetricsClient
import com.malai.exec.model.MetricsWindowRange
import org.springframework.stereotype.Component

/**
 * New customers: account holders created during the asked window, read from the context that owns
 * them. Holders rather than users, so a staff-only login never inflates the bank's customer count —
 * a distinction account-holder-service enforces structurally by counting only its own rows.
 */
@Component
class NewCustomersSource(
    private val accountHolderMetricsClient: AccountHolderMetricsClient,
    private val execServiceTokenClient: ExecServiceTokenClient,
) {

    fun count(window: MetricsWindowRange): Long =
        accountHolderMetricsClient
            .accountHoldersCreated(execServiceTokenClient.getToken(), window.from, window.until)
            .count
}
