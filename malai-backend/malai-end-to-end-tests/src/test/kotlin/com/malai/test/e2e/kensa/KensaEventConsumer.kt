package com.malai.test.e2e.kensa

import tools.jackson.module.kotlin.readValue
import com.malai.commons.tracking.KensaEventEnvelope
import com.malai.commons.tracking.KensaEventKind
import com.malai.commons.tracking.TestId
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.kensa.CapturedInteractionHolder
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.*
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.MalaiE2ETestContext
import dev.kensa.state.CapturedInteractionBuilder
import dev.kensa.state.Party
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Background poller that drains `kensa-events.fifo` (where containers publish
 * [KensaEventEnvelope]s for every SQS publish/consume they observe) and replays each envelope
 * into [SqsInteractionCapturer] so the rendered diagram in the e2e JVM looks identical to what
 * an acceptance-test JVM would produce.
 *
 * Also tracks `TEST_ENDED` markers per-testId so the test infrastructure can block until all
 * envelopes for a finished test have landed in [com.malai.test.acceptance.kensa.CapturedInteractionHolder]
 * before Kensa renders.
 */
class KensaEventConsumer(
    private val sqsAsyncClient: SqsAsyncClient,
    private val queueUrl: String,
    /**
     * Envelopes are the canonical source of HTTP arrows for inter-service traffic after phase 2c.
     * WireMock-on-host no longer sees inter-service requests at all (services talk container-to-
     * container directly), so there's no risk of double-drawing. Cognito-shaped URLs stay on
     * WireMock — those still flow through `WireMockInteractionCapturer`.
     */
    private val renderHttpEnvelopes: Boolean = true,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val testEndedFutures = ConcurrentHashMap<String, CompletableFuture<Unit>>()
    private val httpEnvelopesSeen = ConcurrentHashMap<String, AtomicInteger>()
    private val httpRequestEnvelopes = ConcurrentHashMap<String, CopyOnWriteArrayList<KensaEventEnvelope>>()
    private val receivedEnvelopes = ConcurrentHashMap<String, CopyOnWriteArrayList<KensaEventEnvelope>>()
    private var pollThread: Thread? = null

    /**
     * Returns every envelope (HTTP_REQUEST / HTTP_RESPONSE / SQS_PUBLISH / SQS_CONSUME) that
     * arrived for [testId], in FIFO arrival order. TEST_ENDED is excluded — it's a control
     * marker, not an observed interaction. Intended for tests that want to assert on the order
     * envelopes were published to `kensa-events.fifo`.
     */
    fun receivedEnvelopesFor(testId: String): List<KensaEventEnvelope> =
        receivedEnvelopes[testId]?.toList() ?: emptyList()

    fun httpEnvelopeCount(testId: String): Int = httpEnvelopesSeen[testId]?.get() ?: 0

    /**
     * Returns every HTTP_REQUEST envelope that arrived for [testId] whose computed `(from, to)`
     * party pair matches the supplied filter, ordered chronologically. Backs the e2e assertion
     * API `theFollowingHttpRequestWasMade(...)` for inter-service traffic, replacing the previous
     * `wiremock.allServeEvents` scan for the cases where WireMock no longer observes the call.
     */
    fun capturedHttpRequestsFor(testId: String, from: Party, to: Party): List<CapturedHttpRequest> =
        httpRequestEnvelopes[testId]
            ?.asSequence()
            ?.mapNotNull { env -> env.takeIf { matchesPartyPair(env, from, to) } }
            ?.sortedBy { it.tsEpochMillis }
            ?.mapNotNull { toCapturedHttpRequest(it) }
            ?.toList()
            ?: emptyList()

    private fun matchesPartyPair(env: KensaEventEnvelope, from: Party, to: Party): Boolean {
        val path = uriPath(env.httpUrl ?: return false) ?: return false
        val envFrom = partyFromSource(env.fromParty, path) ?: MalaiParty.Companion.Unknown
        if (envFrom != from) return false
        val envTo = MalaiE2ETestContext.toSystemLookup(path)
        return envTo == to
    }

    private fun toCapturedHttpRequest(envelope: KensaEventEnvelope): CapturedHttpRequest? {
        val method = envelope.httpMethod ?: return null
        val rawUrl = envelope.httpUrl ?: return null
        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            return null
        }
        val path = uri.rawPath ?: return null
        val urlWithQuery = path + (uri.rawQuery?.let { "?$it" } ?: "")
        return CapturedHttpRequest(
            method = HttpMethod.valueOf(method),
            url = urlWithQuery,
            headers = envelope.headers,
            body = envelope.bodyJson?.takeIf { it.isNotEmpty() },
        )
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        pollThread = thread(start = true, isDaemon = true, name = "kensa-event-consumer") {
            log.info("kensa event consumer started against {}", queueUrl)
            while (running.get()) {
                runCatching { pollOnce() }.onFailure {
                    if (running.get()) log.warn("kensa event poll failed: {}", it.message)
                }
            }
            log.info("kensa event consumer stopped")
        }
    }

    fun stop() {
        running.set(false)
        pollThread?.join(Duration.ofSeconds(5).toMillis())
    }

    fun awaitTestEnded(testId: String, timeout: Duration): Boolean {
        val future = testEndedFutures.computeIfAbsent(testId) { CompletableFuture() }
        return try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            true
        } catch (_: Exception) {
            false
        } finally {
            testEndedFutures.remove(testId)
        }
    }

    private fun pollOnce() {
        val response = sqsAsyncClient.receiveMessage { req ->
            req.queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20)
                .messageAttributeNames("All")
        }.get(30, TimeUnit.SECONDS)
        for (sqsMessage in response.messages()) {
            try {
                val envelope = testingObjectMapper.readValue<KensaEventEnvelope>(sqsMessage.body())
                handle(envelope)
            } catch (e: Exception) {
                log.warn("failed to handle kensa envelope: {}", e.message)
            }
            sqsAsyncClient.deleteMessage { it.queueUrl(queueUrl).receiptHandle(sqsMessage.receiptHandle()) }
        }
    }

    private fun handle(envelope: KensaEventEnvelope) {
        if (envelope.kind != KensaEventKind.TEST_ENDED) {
            receivedEnvelopes.computeIfAbsent(envelope.testId) { CopyOnWriteArrayList() }.add(envelope)
        }
        when (envelope.kind) {
            KensaEventKind.SQS_PUBLISH -> {
                val queueBaseName = envelope.queueBaseName ?: return
                val bodyJson = envelope.bodyJson ?: return
                if (!isCommandQueue(queueBaseName)) return
                val producer = partyFromSource(envelope.fromParty) ?: MalaiParty.Companion.Unknown
                SqsInteractionCapturer.capturePublish(
                    testId = TestId(envelope.testId),
                    producer = producer,
                    queueBaseName = queueBaseName,
                    bodyJson = bodyJson,
                    headers = envelope.headers,
                )
            }

            KensaEventKind.SQS_CONSUME -> {
                val queueBaseName = envelope.queueBaseName ?: return
                val bodyJson = envelope.bodyJson ?: return
                if (!isEventQueue(queueBaseName)) return
                val consumer = partyFromSource(envelope.toParty) ?: MalaiParty.Companion.Unknown
                SqsInteractionCapturer.captureConsume(
                    testId = TestId(envelope.testId),
                    consumer = consumer,
                    queueBaseName = queueBaseName,
                    bodyJson = bodyJson,
                    headers = envelope.headers,
                )
            }

            KensaEventKind.HTTP_REQUEST -> handleHttpRequest(envelope)
            KensaEventKind.HTTP_RESPONSE -> handleHttpResponse(envelope)
            KensaEventKind.TEST_ENDED -> {
                testEndedFutures.computeIfAbsent(envelope.testId) { CompletableFuture() }.complete(Unit)
            }
        }
    }

    private fun handleHttpRequest(envelope: KensaEventEnvelope) {
        httpEnvelopesSeen.computeIfAbsent(envelope.testId) { AtomicInteger(0) }.incrementAndGet()
        httpRequestEnvelopes.computeIfAbsent(envelope.testId) { CopyOnWriteArrayList() }.add(envelope)
        val method = envelope.httpMethod ?: return
        val url = envelope.httpUrl ?: return
        log.debug("kensa HTTP_REQUEST testId={} from={} {} {}",
            envelope.testId, envelope.fromParty, method, url)
        if (!renderHttpEnvelopes) return
        val interactions = CapturedInteractionHolder[TestId(envelope.testId)] ?: return
        val path = uriPath(url) ?: return
        val from = partyFromSource(envelope.fromParty, path) ?: MalaiParty.Companion.Unknown
        val to = MalaiE2ETestContext.toSystemLookup(path)
        val description = InteractionDescriber.describe(method, path)
        interactions.capture(
            CapturedInteractionBuilder.from(from)
                .to(to)
                .with(HttpEnvelopeRequest(method, path, envelope.bodyJson, envelope.headers), "$description Request")
        )
    }

    private fun handleHttpResponse(envelope: KensaEventEnvelope) {
        httpEnvelopesSeen.computeIfAbsent(envelope.testId) { AtomicInteger(0) }.incrementAndGet()
        val method = envelope.httpMethod ?: return
        val url = envelope.httpUrl ?: return
        val status = envelope.httpStatus ?: return
        log.debug("kensa HTTP_RESPONSE testId={} to={} {} {} -> {}",
            envelope.testId, envelope.toParty, method, url, status)
        if (!renderHttpEnvelopes) return
        val interactions = CapturedInteractionHolder[TestId(envelope.testId)] ?: return
        val path = uriPath(url) ?: return
        val to = partyFromSource(envelope.toParty, path) ?: MalaiParty.Companion.Unknown
        val from = MalaiE2ETestContext.toSystemLookup(path)
        val description = InteractionDescriber.describe(method, path)
        interactions.capture(
            CapturedInteractionBuilder.from(from)
                .to(to)
                .with(HttpEnvelopeResponse(status, envelope.bodyJson, envelope.headers), "$description Response")
        )
    }

    private fun uriPath(url: String): String? = try {
        URI(url).path
    } catch (_: Exception) {
        null
    }

    private fun isCommandQueue(name: String): Boolean = name.contains("-command-")
    private fun isEventQueue(name: String): Boolean = name.contains("-event-")

    companion object {
        /**
         * Compact human-readable summary of an envelope, suitable for ordered-list assertions in
         * tests that want to verify the FIFO order envelopes landed at `kensa-events.fifo`.
         * Mirrors the diagram label so a failing assertion reads exactly like the rendered arrow.
         */
        fun summarise(envelope: KensaEventEnvelope): String = when (envelope.kind) {
            KensaEventKind.HTTP_REQUEST -> describeHttp(envelope, "Request")
            KensaEventKind.HTTP_RESPONSE -> describeHttp(envelope, "Response")
            // Same label the diagram renderer uses (see SqsInteractionCapturer.capture). Both
            // publish and consume of the same queue produce the same string — matches what's on
            // the arrow on the sequence diagram. Tests using `shouldContainInOrder` cope with
            // the duplicates naturally (two list entries → two consecutive occurrences).
            KensaEventKind.SQS_PUBLISH -> SqsClassifier.describe(envelope.queueBaseName ?: "?")
            KensaEventKind.SQS_CONSUME -> SqsClassifier.describe(envelope.queueBaseName ?: "?")
            KensaEventKind.TEST_ENDED -> "TEST_ENDED"
        }

        private fun describeHttp(envelope: KensaEventEnvelope, suffix: String): String {
            val method = envelope.httpMethod ?: ""
            val rawUrl = envelope.httpUrl ?: ""
            val path = try {
                URI(rawUrl).rawPath ?: rawUrl
            } catch (_: Exception) {
                rawUrl
            }
            return "${InteractionDescriber.describe(method, path)} $suffix"
        }
    }

    private fun partyFromSource(name: String?, path: String? = null): Party? {
        // When malai-stub is on the wire, the URL says which external scheme it's impersonating:
        // FPS/SWIFT/BACS/card-network depending on which gateway callback endpoint it's calling.
        if (name == "malai-stub" && path != null) {
            when {
                path.startsWith("/fps/callbacks/") -> return MalaiParty.Fps
                path.startsWith("/swift/callbacks/") -> return MalaiParty.Swift
                path.startsWith("/bacs/callbacks/") -> return MalaiParty.Bacs
                path.startsWith("/cards/callbacks/") -> return MalaiParty.CardScheme
            }
        }
        return when (name) {
            KensaEnvelopePublishingHttpInterceptor.CLIENT_PARTY_NAME -> MalaiParty.Client
            "auth-service" -> MalaiParty.AuthService
            "account-holder-service" -> MalaiParty.AccountHolderService
            "account-service" -> MalaiParty.AccountService
            "ledger-service" -> MalaiParty.LedgerService
            "payee-service" -> MalaiParty.PayeeService
            "payments-service" -> MalaiParty.PaymentsService
            "standing-order-service" -> MalaiParty.StandingOrderService
            "direct-debit-service" -> MalaiParty.DirectDebitService
            "insights-service" -> MalaiParty.InsightsService
            "sandbox-service" -> MalaiParty.SandboxService
            "mdr-service" -> MalaiParty.MdrService
            "copilot-service" -> MalaiParty.CopilotService
            "aml-service" -> MalaiParty.AmlService
            "card-service" -> MalaiParty.CardService
            "card-gateway" -> MalaiParty.CardGateway
            "fps-gateway" -> MalaiParty.FpsGateway
            "swift-gateway" -> MalaiParty.SwiftGateway
            "bacs-gateway" -> MalaiParty.BacsGateway
            "malai-stub" -> MalaiParty.MalaiStub
            else -> null
        }
    }
}
