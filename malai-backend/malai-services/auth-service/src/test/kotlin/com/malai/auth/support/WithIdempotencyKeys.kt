package com.malai.auth.support

import com.malai.security.idempotency.IdempotencyKeyEntity
import com.malai.security.idempotency.IdempotencyKeyId
import com.malai.security.idempotency.IdempotencyKeyRepository
import com.malai.security.idempotency.IdempotencyStatus
import com.malai.security.idempotency.IdempotencyStatus.COMPLETED
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import java.security.MessageDigest
import java.time.Instant

interface WithIdempotencyKeys : WithApplicationContext {

    fun idempotencyKeyRepository(): IdempotencyKeyRepository =
        getBean(IdempotencyKeyRepository::class.java)

    fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        idempotencyKeyRepository().deleteAll()
        idempotencyKeyRepository().flush()
    }

    fun anIdempotencyKeyExists(
        key: String,
        service: String = AUTH_SERVICE,
        requestHash: ByteArray = ByteArray(32),
        status: IdempotencyStatus = COMPLETED,
        responseStatus: Int? = 200,
        responseHeaders: String? = null,
        responseBody: ByteArray? = null,
        createdAt: Instant = Instant.parse("2026-05-07T09:00:00Z"),
        expiresAt: Instant = Instant.parse("2026-05-08T09:00:00Z"),
    ) = CallableAction {
        idempotencyKeyRepository().save(
            IdempotencyKeyEntity(
                service = service,
                key = key,
                requestHash = requestHash,
                status = status,
                responseStatus = responseStatus,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                createdAt = createdAt,
                expiresAt = expiresAt,
            )
        )
    }

    fun theIdempotencyKeysInTheDatabase() = StateCollector { _ ->
        idempotencyKeyRepository().findAll().sortedBy { it.createdAt }
    }

    fun theIdempotencyKeyInTheDatabase(
        key: String,
        service: String = AUTH_SERVICE,
    ) = StateCollector<IdempotencyKeyEntity> { _ ->
        idempotencyKeyRepository().findById(IdempotencyKeyId(service, key)).orElseThrow {
            AssertionError("Idempotency key '$key' (service='$service') not found in database")
        }
    }

    fun requestHash(
        method: String,
        uri: String,
        queryString: String? = null,
        body: ByteArray = ByteArray(0),
    ): ByteArray {
        val sortedQuery = (queryString ?: "")
            .split("&").filter { it.isNotEmpty() }.sorted().joinToString("&")
        val payloadHeader = "$method\n$uri\n$sortedQuery\n".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(payloadHeader + body)
    }

    companion object {
        const val AUTH_SERVICE = "auth-service"
    }
}
