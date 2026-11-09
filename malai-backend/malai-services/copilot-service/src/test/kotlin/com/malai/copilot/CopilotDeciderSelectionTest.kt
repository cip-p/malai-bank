package com.malai.copilot

import com.anthropic.client.AnthropicClient
import com.malai.copilot.service.AnthropicCopilotDecider
import com.malai.copilot.service.CopilotDecider
import com.malai.copilot.service.CopilotProperties
import com.malai.copilot.service.TemplateCopilotDecider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

private const val SOME_API_KEY = "sk-ant-not-a-real-key"

class CopilotDeciderSelectionTest {

    private val copilot = ApplicationContextRunner()
        .withBean(CopilotProperties::class.java, { CopilotProperties() })
        .withUserConfiguration(CopilotDeciderConfiguration::class.java)

    @Test
    fun theDeterministicDeciderIsWhatTheCopilotGetsWhenNoProviderIsNamed() {
        copilot.run { context ->
            context.startupFailure.shouldBeNull()
            context.getBean(CopilotDecider::class.java).shouldBeInstanceOf<TemplateCopilotDecider>()
        }
    }

    @Test
    fun noProviderIsConstructedAndNoKeyIsNeededToBootWhenNoProviderIsNamed() {
        copilot.run { context ->
            context.getBeanNamesForType(AnthropicClient::class.java).toList() shouldBe emptyList()
        }
    }

    @Test
    fun namingTheProviderSelectsTheAdapterInPlaceOfTheDeterministicDecider() {
        copilot
            .withPropertyValues("malai.copilot.decider=anthropic", "malai.copilot.anthropic.api-key=$SOME_API_KEY")
            .run { context ->
                context.getBean(CopilotDecider::class.java).shouldBeInstanceOf<AnthropicCopilotDecider>()
                context.getBeanNamesForType(AnthropicClient::class.java).toList().size shouldBe 1
            }
    }

    @Test
    fun theAdapterRecordsTheCurrencyTheProviderBillsRatherThanTheDeterministicDecidersZeroPricedGbp() {
        copilot
            .withPropertyValues("malai.copilot.decider=anthropic", "malai.copilot.anthropic.api-key=$SOME_API_KEY")
            .run { context -> context.getBean(CopilotDecider::class.java).costCurrency shouldBe "USD" }

        copilot.run { context -> context.getBean(CopilotDecider::class.java).costCurrency shouldBe "GBP" }
    }

    @Test
    fun namingTheProviderWithoutAKeyFailsAtBootRatherThanAtTheFirstExplanationRequested() {
        copilot.withPropertyValues("malai.copilot.decider=anthropic").run { context ->
            context.startupFailure!!.stackTraceToString() shouldContain
                "malai.copilot.anthropic.api-key must be set when malai.copilot.decider is anthropic"
        }
    }

    @Test
    fun anEnvironmentThatLeftTheKeyBlankFailsTheSameWayAsOneThatNeverSetIt() {
        copilot
            .withPropertyValues("malai.copilot.decider=anthropic", "malai.copilot.anthropic.api-key=")
            .run { context ->
                context.startupFailure!!.stackTraceToString() shouldContain
                    "malai.copilot.anthropic.api-key must be set when malai.copilot.decider is anthropic"
            }
    }

    @Test
    fun namingADeciderThePlatformDoesNotHaveLeavesNoDeciderRatherThanASilentFallback() {
        copilot.withPropertyValues("malai.copilot.decider=some-provider-we-never-built").run { context ->
            context.getBeanNamesForType(CopilotDecider::class.java).toList() shouldBe emptyList()
        }
    }
}
