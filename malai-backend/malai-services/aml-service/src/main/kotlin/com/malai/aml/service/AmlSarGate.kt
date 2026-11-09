package com.malai.aml.service

import com.malai.aml.sar.RuledSarNarrative
import com.malai.aml.sar.SarActionableOutput
import com.malai.client.malai.mdr.MdrClient
import org.springframework.stereotype.Service
import java.util.UUID

// Close-as-reported's one question, asked of the MDR gate at the moment of the officer's action:
// is this Case's SAR draft actionable, and if so what does it say? An accept serves the drafted
// narrative and an edit the officer's, both through the same door and read the same way -- the
// gate has already translated the ruling, so nothing here re-derives what "actionable" means
// (ADR-0009).
//
// null is every refusal collapsed into one: no ruling yet, a reject in force, or output carrying
// no narrative the Case could file. The caller enacts nothing on null -- conservative-only,
// ADR-0008 -- and a later Disposition simply makes the same close succeed next time.
@Service
class AmlSarGate(
    private val mdrClient: MdrClient,
    private val serviceTokenClient: AmlServiceTokenClient,
) {

    fun ruledNarrative(sarDecisionId: UUID): RuledSarNarrative? {
        val actionable = mdrClient.actionableOutput(serviceTokenClient.getToken(), sarDecisionId) ?: return null
        val narrative = SarActionableOutput.narrativeIn(actionable.actionableOutput) ?: return null
        return RuledSarNarrative(
            ruling = actionable.ruling,
            rulingEntryId = actionable.rulingEntryId,
            narrative = narrative,
        )
    }
}
