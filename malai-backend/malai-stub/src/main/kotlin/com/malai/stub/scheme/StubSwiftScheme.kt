package com.malai.stub.scheme

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.scheme.contracts.swift.SwiftSchemeSettlementCallback
import com.malai.scheme.contracts.swift.SwiftSchemeSubmitAck
import com.malai.scheme.contracts.swift.SwiftSchemeSubmitRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class StubSwiftScheme(
    private val gatewayClient: StubSwiftGatewayClient,
    @Value("\${malai.stub.swift.settle-delay-millis:30000}")
    private val settleDelayMillis: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val processedEndToEndIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "stub-swift-settle").apply { isDaemon = true }
    }

    fun submit(request: SwiftSchemeSubmitRequest): SwiftSchemeSubmitAck {
        if (!processedEndToEndIds.add(request.endToEndId.toString())) {
            log.info("stub swift: ignoring duplicate submit endToEndId={}", request.endToEndId)
            return ackFor(request)
        }
        val testId = TestIdContext.valueOrNull
        scheduler.schedule({ settle(request, testId) }, settleDelayMillis, TimeUnit.MILLISECONDS)
        return ackFor(request)
    }

    private fun settle(request: SwiftSchemeSubmitRequest, testId: TestId?) {
        if (testId != null) TestIdContext.value = testId
        try {
            gatewayClient.notifySettled(
                SwiftSchemeSettlementCallback(endToEndId = request.endToEndId, paymentId = request.paymentId),
            )
            log.info("stub swift: settled paymentId={} endToEndId={}", request.paymentId, request.endToEndId)
        } catch (e: Exception) {
            log.error("stub swift: failed to deliver settlement callback endToEndId={}", request.endToEndId, e)
        } finally {
            if (testId != null) TestIdContext.unset()
        }
    }

    private fun ackFor(request: SwiftSchemeSubmitRequest) =
        SwiftSchemeSubmitAck(endToEndId = request.endToEndId, paymentId = request.paymentId)
}
