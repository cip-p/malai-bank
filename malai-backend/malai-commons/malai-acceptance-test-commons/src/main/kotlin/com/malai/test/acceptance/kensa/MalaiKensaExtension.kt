package com.malai.test.acceptance.kensa

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.kensa.renderer.HttpEnvelopeRequestRenderer
import com.malai.test.acceptance.kensa.renderer.HttpEnvelopeResponseRenderer
import com.malai.test.acceptance.kensa.renderer.HttpRequestRenderer
import com.malai.test.acceptance.kensa.renderer.HttpResponseRenderer
import com.malai.test.acceptance.kensa.renderer.SqsInteractionRenderer
import com.malai.test.acceptance.kensa.renderer.WireMockRequestRenderer
import com.malai.test.acceptance.kensa.renderer.WireMockResponseRenderer
import dev.kensa.Kensa
import dev.kensa.Tab
import dev.kensa.context.TestContextHolder
import dev.kensa.fixture.FixtureRegistry.registerFixtures
import dev.kensa.junit.KensaExtension
import dev.kensa.outputs.CapturedOutputsRegistry.registerCapturedOutputs
import dev.kensa.withRenderers
import org.junit.jupiter.api.extension.*
import kotlin.io.path.Path

/**
 * Single Kensa JUnit extension for all malai service acceptance tests. Configures Kensa with the
 * shared renderers and fixtures, and wires [CapturedInteractionHolder] / [TestIdContext] for each
 * test so that the HTTP interceptors can correlate captured traffic back to the running test.
 */
@ExtendWith(KensaExtension::class)
class MalaiKensaExtension : Extension, BeforeEachCallback, AfterEachCallback {

    init {
        Kensa.konfigure {
            outputDir = Path("target/reports").toAbsolutePath()
            autoOpenTab = Tab.SequenceDiagram

            withRenderers {
                interactionRenderer(HttpRequestRenderer)
                interactionRenderer(HttpResponseRenderer)
                interactionRenderer(WireMockRequestRenderer)
                interactionRenderer(WireMockResponseRenderer)
                interactionRenderer(SqsInteractionRenderer)
                interactionRenderer(HttpEnvelopeRequestRenderer)
                interactionRenderer(HttpEnvelopeResponseRenderer)
            }
            registerFixtures(
                KensaFixtures
            )
            registerCapturedOutputs(
                KensaCapturedOutputs
            )
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        TestId().also { id ->
            CapturedInteractionHolder[id] = TestContextHolder.testContext().interactions
            TestIdContext.value = id
        }
    }

    override fun afterEach(context: ExtensionContext) {
        CapturedInteractionHolder.remove(TestIdContext.value)
        TestIdContext.unset()
    }
}
