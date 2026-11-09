package com.malai.client.malai

import tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.malai.commons.TimeService
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import tools.jackson.module.kotlin.jacksonMapperBuilder

/**
 * Fetches and caches a Cognito service (`client_credentials`) access token used by a
 * malai service to authenticate to other services' internal endpoints.
 *
 * Each calling service has its own subclass that wires up its own Cognito credentials
 * (see e.g. `AuthServiceTokenClient` in auth-service). The subclass is the Spring
 * `@Component`; this base class is plain Kotlin so it stays free of `@Value` /
 * property-prefix coupling and can be reused by any service.
 *
 * Caching: the token is held in memory and reused until it's within [REFRESH_AHEAD] of
 * expiring. Concurrent callers serialise on a single lock so a cache miss triggers at
 * most one refresh.
 *
 * The receiving side validates the same token via `com.malai.security.ServiceJwtFilter`.
 *
 * @param callerName service identifier used in logs only — e.g. `"auth-service"`. Helps
 *   when several service-token clients live in the same JVM (the malai-api aggregator).
 */
abstract class ServiceTokenClient(
    private val httpClient: CloseableHttpClient,
    private val timeService: TimeService,
    private val tokenEndpointUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val scope: String,
    private val callerName: String,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)
    private val objectMapper = jacksonMapperBuilder().configure(FAIL_ON_UNKNOWN_PROPERTIES, false).build()
    private val lock = ReentrantLock()

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedTokenExpiresAt: Instant = Instant.EPOCH

    fun getToken(): String {
        cachedToken?.takeIf { timeService.now().plus(REFRESH_AHEAD).isBefore(cachedTokenExpiresAt) }?.let {
            return it
        }
        return refreshToken()
    }

    fun evict() {
        lock.withLock {
            cachedToken = null
            cachedTokenExpiresAt = Instant.EPOCH
        }
    }

    private fun refreshToken(): String = lock.withLock {
        // Re-check inside the lock so only one caller refreshes per cache miss.
        cachedToken?.takeIf { timeService.now().plus(REFRESH_AHEAD).isBefore(cachedTokenExpiresAt) }?.let {
            return@withLock it
        }

        log.debug("requesting new Cognito service access token for {}", callerName)
        val request = HttpPost(tokenEndpointUrl)
        val basicAuth = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8))
        request.addHeader("Authorization", "Basic $basicAuth")
        request.addHeader("Accept", "application/json")
        val form = "grant_type=client_credentials&scope=${URLEncoder.encode(scope, StandardCharsets.UTF_8)}"
        request.entity = StringEntity(form, ContentType.APPLICATION_FORM_URLENCODED)

        val (token, expiresInSeconds) = httpClient.execute(request) { response ->
            if (response.code != 200) {
                val body = response.entity?.let { EntityUtils.toString(it) }
                throw RuntimeException(
                    "Cognito service token endpoint returned ${response.code} for $callerName: $body"
                )
            }
            val body = EntityUtils.toString(response.entity)
            val parsed = objectMapper.readValue(body, TokenResponse::class.java)
            parsed.access_token to parsed.expires_in
        }

        cachedToken = token
        cachedTokenExpiresAt = timeService.now().plusSeconds(expiresInSeconds.toLong())
        token
    }

    private data class TokenResponse(val access_token: String, val expires_in: Int)

    companion object {
        // Refresh a few minutes ahead of expiry so requests in flight when we approach the
        // boundary don't hit a freshly-expired token.
        private val REFRESH_AHEAD: Duration = Duration.ofMinutes(5)
    }
}
