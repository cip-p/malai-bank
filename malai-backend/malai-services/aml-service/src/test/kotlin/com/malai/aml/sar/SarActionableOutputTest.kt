package com.malai.aml.sar

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SarActionableOutputTest {

    @Test
    fun theNarrativeTheDrafterRecordedIsWhatTheGateServesUnderAnAccept() {
        SarActionableOutput.narrativeIn(
            """{"narrative":"Malai Bank reports activity it considers suspicious.","statements":[]}""",
        ) shouldBe "Malai Bank reports activity it considers suspicious."
    }

    @Test
    fun anOfficersEditedNarrativeIsReadFromTheSameField() {
        SarActionableOutput.narrativeIn(
            """{"narrative":"The officer's own words.","statements":[]}""",
        ) shouldBe "The officer's own words."
    }

    @Test
    fun outputCarryingNoNarrativeFieldIsNothingTheCaseCanFile() {
        SarActionableOutput.narrativeIn("""{"statements":[]}""") shouldBe null
    }

    @Test
    fun outputThatIsNotJsonAtAllIsNothingTheCaseCanFile() {
        SarActionableOutput.narrativeIn("APPROVE: low risk.") shouldBe null
    }

    @Test
    fun aNarrativeEditedDownToNothingIsNothingTheCaseCanFile() {
        SarActionableOutput.narrativeIn("""{"narrative":"   "}""") shouldBe null
    }

    @Test
    fun aNarrativeFieldHoldingJsonNullIsNothingTheCaseCanFile() {
        SarActionableOutput.narrativeIn("""{"narrative":null}""") shouldBe null
    }
}
