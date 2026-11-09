package com.malai.card.support

import com.malai.card.pan.PanGenerator
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithApplicationContext
import dev.kensa.Action
import dev.kensa.GivensContext
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.beans.factory.getBean

@ExtendWith(WithTestPanGenerator.TestPanGeneratorExtension::class)
interface WithTestPanGenerator : WithApplicationContext {

    fun testPanGenerator(): TestPanGenerator =
        getBean(PanGenerator::class.java) as TestPanGenerator

    fun theNextGeneratedPanWillBe(vararg pans: String) = Action<GivensContext> {
        testPanGenerator().willReturnPan(*pans)
    }

    fun theNextGeneratedCvvWillBe(vararg cvvs: String) = Action<GivensContext> {
        testPanGenerator().willReturnCvv(*cvvs)
    }

    class TestPanGeneratorExtension : BeforeEachCallback {
        override fun beforeEach(extensionContext: ExtensionContext) {
            if (MalaiAcceptanceTestContext.isInitialized) {
                (MalaiAcceptanceTestContext.applicationContext.getBean<PanGenerator>()
                    as TestPanGenerator).reset()
            }
        }
    }
}
