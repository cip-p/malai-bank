package com.malai.payee.support

import com.malai.payee.entity.PayeeEntity
import com.malai.payee.repository.PayeesRepository
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

interface WithPayees : WithApplicationContext {

    fun payeesRepository(): PayeesRepository = getBean(PayeesRepository::class.java)

    // -------- builders --------

    fun aPayeeExists(
        payeeId: UUID,
        accountHolderId: UUID,
        name: String,
        sortCode: String,
        accountNumber: String,
        nickname: String? = null,
        createdAt: Instant = Instant.parse("2025-10-15T10:00:00Z"),
        createdBy: UUID = UUID.randomUUID(),
    ) = CallableAction {
        payeesRepository().save(
            PayeeEntity(
                payeeId = payeeId,
                accountHolderId = accountHolderId,
                name = name,
                sortCode = sortCode,
                accountNumber = accountNumber,
                nickname = nickname,
                createdAt = createdAt,
                createdBy = createdBy,
            )
        )
    }

    // -------- collectors --------

    fun thePayeesInTheDatabaseFor(accountHolderId: UUID) = StateCollector { _ ->
        payeesRepository().findAllByAccountHolderIdOrderByCreatedAtDesc(accountHolderId)
    }

    fun thePayeeInTheDatabase(payeeId: UUID) = StateCollector<PayeeEntity?> { _ ->
        payeesRepository().findById(payeeId).orElse(null)
    }

    fun theAuditChainIsAtGenesis() = CallableAction {
        val jdbc = getBean(JdbcTemplate::class.java)
        jdbc.update("DELETE FROM audit_events")
        jdbc.update(
            """
            INSERT INTO chain_head (scope, last_hash, last_seq)
            VALUES ('global', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
            ON CONFLICT (scope) DO UPDATE
                SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
            """.trimIndent(),
        )
    }

    fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    fun theAuditEventRowsInTheDatabase() = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForList(
            """
            SELECT seq, action, actor_id, actor_type, resource_type, resource_id,
                   after_state::text AS after_state, before_state::text AS before_state, trace_id
            FROM audit_events
            ORDER BY seq ASC
            """.trimIndent(),
        )
    }
}
