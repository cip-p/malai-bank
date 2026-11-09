package com.malai.security.idempotency

import com.malai.commons.TimeService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.temporal.ChronoUnit.HOURS

@Service
class IdempotencyService(
    private val repo: IdempotencyKeyRepository,
    private val timeService: TimeService,
    private val inserter: IdempotencyKeyInserter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun computeHash(request: HttpServletRequest, body: ByteArray): ByteArray {
        val sortedQuery = (request.queryString ?: "")
            .split("&").filter { it.isNotEmpty() }.sorted().joinToString("&")
        val payloadHeader = buildString {
            append(request.method); append('\n')
            append(request.requestURI); append('\n')
            append(sortedQuery); append('\n')
        }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(payloadHeader + body)
    }

    fun tryReserve(service: String, key: String, requestHash: ByteArray, ttlHours: Long): IdempotencyKeyEntity? {
        val now = timeService.now()
        val entity = IdempotencyKeyEntity(
            service = service,
            key = key,
            requestHash = requestHash,
            status = IdempotencyStatus.IN_FLIGHT,
            createdAt = now,
            expiresAt = now.plus(ttlHours, HOURS),
        )
        return try {
            inserter.insert(entity)
            null
        } catch (e: DataIntegrityViolationException) {
            repo.findById(IdempotencyKeyId(service, key)).orElseThrow {
                IllegalStateException("idempotency_keys row vanished after PK collision for service=$service key=$key", e)
            }
        }
    }

    @Transactional
    fun complete(
        service: String,
        key: String,
        responseStatus: Int,
        responseHeadersJson: String?,
        responseBody: ByteArray?,
    ) {
        val row = repo.findById(IdempotencyKeyId(service, key)).orElse(null) ?: run {
            log.warn("Idempotency: complete() called but row not found for service={} key={}", service, key)
            return
        }
        repo.save(
            IdempotencyKeyEntity(
                service = row.service,
                key = row.key,
                requestHash = row.requestHash,
                status = IdempotencyStatus.COMPLETED,
                responseStatus = responseStatus,
                responseHeaders = responseHeadersJson,
                responseBody = responseBody,
                createdAt = row.createdAt,
                expiresAt = row.expiresAt,
            )
        )
        log.debug("Idempotency: completed service={} key={} status={} bodyBytes={}",
            service, key, responseStatus, responseBody?.size ?: 0)
    }

    @Transactional
    fun release(service: String, key: String) {
        repo.deleteById(IdempotencyKeyId(service, key))
        log.debug("Idempotency: released service={} key={}", service, key)
    }

    fun cleanup(): Int = repo.deleteAllByExpiresAtBefore(timeService.now())
}
