package com.malai.aml.service

import com.malai.client.malai.mdr.MdrClient
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmlAlertGate(
    private val mdrClient: MdrClient,
    private val serviceTokenClient: AmlServiceTokenClient,
) {

    fun anyAlertFindingStands(alertDecisionIds: List<UUID>): Boolean {
        if (alertDecisionIds.isEmpty()) return false
        val serviceToken = serviceTokenClient.getToken()
        return alertDecisionIds.any { mdrClient.actionableOutput(serviceToken, it) != null }
    }
}
