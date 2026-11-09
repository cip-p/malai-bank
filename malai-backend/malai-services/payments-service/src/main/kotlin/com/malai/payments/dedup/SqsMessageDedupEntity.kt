package com.malai.payments.dedup

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Embeddable
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class SqsMessageDedupId(
    @Column(name = "message_type", nullable = false, length = 50)
    val messageType: String,

    @Column(name = "end_to_end_id", nullable = false)
    val endToEndId: UUID,
) : Serializable

@Entity
@Table(name = "sqs_message_dedup")
data class SqsMessageDedupEntity(
    @EmbeddedId
    val id: SqsMessageDedupId,

    @Column(name = "processed_at", nullable = false)
    val processedAt: Instant,
)
