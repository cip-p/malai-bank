package com.malai.security.idempotency

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.Instant

enum class IdempotencyStatus { IN_FLIGHT, COMPLETED }

data class IdempotencyKeyId(
    val service: String = "",
    val key: String = "",
) : Serializable

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyId::class)
class IdempotencyKeyEntity(

    @Id
    @Column(name = "service", nullable = false)
    val service: String,

    @Id
    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "request_hash", nullable = false)
    val requestHash: ByteArray,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: IdempotencyStatus,

    @Column(name = "response_status")
    val responseStatus: Int? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", columnDefinition = "JSONB")
    val responseHeaders: String? = null,

    @Column(name = "response_body")
    val responseBody: ByteArray? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    override fun equals(other: Any?): Boolean =
        other is IdempotencyKeyEntity && other.service == service && other.key == key

    override fun hashCode(): Int = 31 * service.hashCode() + key.hashCode()
}
