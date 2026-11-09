package com.malai.aml.sar

import com.malai.aml.monitoring.AmlMonitoringConfiguration
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

private const val A_LOCAL_PROVIDER_URL = "http://localhost:11434/v1"

class SarDeciderSelectionTest {

    private val sarDrafting = ApplicationContextRunner()
        .withUserConfiguration(AmlMonitoringConfiguration::class.java, SarDeciderConfiguration::class.java)

    @Test
    fun theDeterministicDrafterIsWhatACaseGetsWhenNoProviderIsNamed() {
        sarDrafting.run { context ->
            context.startupFailure.shouldBeNull()
            context.getBean(SarDraftDecider::class.java).shouldBeInstanceOf<TemplateSarDraftDecider>()
        }
    }

    @Test
    fun namingTheProviderSelectsTheAdapterInPlaceOfTheDeterministicDrafter() {
        sarDrafting
            .withPropertyValues(
                "malai.aml.sar.decider=chat-completions",
                "malai.aml.sar.chat-completions.base-url=$A_LOCAL_PROVIDER_URL",
            )
            .run { context ->
                context.startupFailure.shouldBeNull()
                context.getBean(SarDraftDecider::class.java).shouldBeInstanceOf<ChatCompletionsSarDraftDecider>()
            }
    }

    @Test
    fun theAdapterRecordsTheCurrencyAProviderWouldBillRatherThanTheDrafterZeroPricedGbp() {
        sarDrafting
            .withPropertyValues(
                "malai.aml.sar.decider=chat-completions",
                "malai.aml.sar.chat-completions.base-url=$A_LOCAL_PROVIDER_URL",
            )
            .run { context -> context.getBean(SarDraftDecider::class.java).costCurrency shouldBe "USD" }

        sarDrafting.run { context -> context.getBean(SarDraftDecider::class.java).costCurrency shouldBe "GBP" }
    }

    @Test
    fun namingTheProviderWithoutABaseUrlFailsAtBootRatherThanAtTheFirstDraftRequested() {
        sarDrafting.withPropertyValues("malai.aml.sar.decider=chat-completions").run { context ->
            context.startupFailure!!.stackTraceToString() shouldContain
                "malai.aml.sar.chat-completions.base-url must be set when malai.aml.sar.decider is chat-completions"
        }
    }

    @Test
    fun anEnvironmentThatLeftTheBaseUrlBlankFailsTheSameWayAsOneThatNeverSetIt() {
        sarDrafting
            .withPropertyValues("malai.aml.sar.decider=chat-completions", "malai.aml.sar.chat-completions.base-url=")
            .run { context ->
                context.startupFailure!!.stackTraceToString() shouldContain
                    "malai.aml.sar.chat-completions.base-url must be set when malai.aml.sar.decider is chat-completions"
            }
    }

    @Test
    fun namingADeciderThePlatformDoesNotHaveLeavesNoDrafterRatherThanASilentFallback() {
        sarDrafting.withPropertyValues("malai.aml.sar.decider=some-provider-we-never-built").run { context ->
            context.getBeanNamesForType(SarDraftDecider::class.java).toList() shouldBe emptyList()
        }
    }
}
