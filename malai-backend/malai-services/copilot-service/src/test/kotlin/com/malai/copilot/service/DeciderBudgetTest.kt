package com.malai.copilot.service

import com.malai.commons.tracking.MalaiSource
import com.malai.commons.tracking.MalaiSourceContext
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

private const val A_BUDGET_IN_MS = 200L
private const val LONGER_THAN_THE_BUDGET = A_BUDGET_IN_MS * 10
private val someSource = MalaiSource("copilot-service")
private val someTestId = TestId("some-test-id")

class DeciderBudgetTest {

    private val budget = DeciderBudget(CopilotProperties(deciderTimeoutMs = A_BUDGET_IN_MS))

    @AfterEach
    fun releaseTheCallersContext() {
        MalaiSourceContext.unset()
        TestIdContext.unset()
    }

    @Test
    fun aDeciderAnsweringWithinItsBudgetHasItsAnswerReturned() {
        budget.within { "the payment settled" } shouldBe "the payment settled"
    }

    @Test
    fun aDeciderOutlivingItsBudgetIsAbandonedAsATimeout() {
        val thrown = shouldThrow<CopilotTimedOutException> {
            budget.within { anAnswerNobodyWaitedFor() }
        }

        thrown.message!! shouldContain "${A_BUDGET_IN_MS}ms"
    }

    @Test
    fun theAbandonedDeciderIsInterruptedRatherThanLeftRunningBehindTheAnswer() {
        val wasInterrupted = CountDownLatch(1)

        shouldThrow<CopilotTimedOutException> {
            budget.within {
                try {
                    Thread.sleep(LONGER_THAN_THE_BUDGET)
                } catch (ex: InterruptedException) {
                    wasInterrupted.countDown()
                }
                "an answer nobody waited for"
            }
        }

        wasInterrupted.await(5, SECONDS) shouldBe true
    }

    @Test
    fun aDecidersOwnFailureSurfacesRatherThanTheExecutionWrapperAroundIt() {
        val thrown = shouldThrow<IllegalStateException> {
            budget.within { throw IllegalStateException("the model refused the connection") }
        }

        thrown.message shouldBe "the model refused the connection"
    }

    @Test
    fun aDeciderReportingItsOwnTimeoutKeepsThatVocabularyRatherThanBecomingAnError() {
        val thrown = shouldThrow<CopilotTimedOutException> {
            budget.within { throw CopilotTimedOutException("the provider timed out reading its own response") }
        }

        thrown.message shouldBe "the provider timed out reading its own response"
    }

    @Test
    fun theDeciderRunsOffTheCallersThreadSoTheBudgetCanAbandonIt() {
        val theCallersThread = Thread.currentThread()

        budget.within { Thread.currentThread() == theCallersThread } shouldBe false
    }

    @Test
    fun theDeciderRunsCarryingTheCallersSourceAndTestIdSoItsOwnCallsAreAttributed() {
        MalaiSourceContext.value = someSource
        TestIdContext.value = someTestId

        budget.within { observedContext() } shouldBe ObservedContext(someSource, someTestId)
    }

    @Test
    fun theDecidersThreadIsLeftCarryingNoContextForTheInvocationAfterIt() {
        MalaiSourceContext.value = someSource
        TestIdContext.value = someTestId
        budget.within { observedContext() }

        MalaiSourceContext.unset()
        TestIdContext.unset()

        budget.within { observedContext() } shouldBe ObservedContext(null, null)
    }

    private fun anAnswerNobodyWaitedFor(): String {
        Thread.sleep(LONGER_THAN_THE_BUDGET)
        return "an answer nobody waited for"
    }

    private fun observedContext() = ObservedContext(MalaiSourceContext.valueOrNull, TestIdContext.valueOrNull)

    private data class ObservedContext(val source: MalaiSource?, val testId: TestId?)
}
