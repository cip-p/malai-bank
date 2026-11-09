package com.malai.payee.service

import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.PAYEE_HAS_ACTIVE_STANDING_ORDER
import com.malai.commons.exception.MalaiCode.PAYEE_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import com.malai.payee.entity.PayeeEntity
import com.malai.payee.model.PayeeRequest
import com.malai.payee.model.PayeeResponse
import com.malai.payee.model.PayeesResponse
import com.malai.payee.repository.PayeesRepository
import com.malai.security.MalaiUserWithAccountHolderId
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Plan 02-14 — saved-beneficiary half of BACK-08.
 *
 * Mutating endpoints (POST/DELETE) write a `PAYEE_ADDED` / `PAYEE_REMOVED` audit row
 * inside the same `@Transactional` as the JPA write so the audit row only commits
 * when the business write commits (D-2.13: `record()` REQUIRED — atomic with caller).
 *
 * Ownership is enforced at the repository call: `findByPayeeIdAndAccountHolderId`
 * filters by the JWT-derived holder id; cross-tenant deletes surface as 404 (avoids
 * leaking other holders' payee ids — same pattern as Transfers, Plan 02-12).
 *
 * Duplicate-add hits the V9 `UNIQUE (account_holder_id, sort_code, account_number)` and
 * surfaces as a Spring `DataIntegrityViolationException`; we translate to 409
 * via `ConflictErrorException(PAYEE_ALREADY_EXISTS_OR_GENERIC)`. v1 reuses
 * `MalaiCode.GENERIC_CONFLICT` until a domain-specific code lands (deferred).
 */
@Service
class PayeesService(
    private val repository: PayeesRepository,
    private val auditWriter: AuditWriter,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun list(user: MalaiUserWithAccountHolderId): PayeesResponse {
        val rows = repository.findAllByAccountHolderIdOrderByCreatedAtDesc(user.accountHolderId)
        return PayeesResponse(rows.map(::toResponse))
    }

    @Transactional
    fun addPayee(user: MalaiUserWithAccountHolderId, request: PayeeRequest): PayeeResponse {
        val payeeId = uuidGenerator.generate()
        val now = timeService.now()
        val traceId = MDC.get(MDC_TRACE_ID)

        val entity = PayeeEntity(
            payeeId = payeeId,
            accountHolderId = user.accountHolderId,
            name = request.name,
            sortCode = request.sortCode,
            accountNumber = request.accountNumber,
            nickname = request.nickname,
            createdAt = now,
            createdBy = user.userId,
        )

        val saved = try {
            // saveAndFlush forces the INSERT to fire NOW, inside the try block, so the
            // V9 UNIQUE (account_holder_id, sort_code, account_number) violation surfaces
            // here as DataIntegrityViolationException. With plain save(), the INSERT
            // would be deferred until @Transactional commit and the constraint failure
            // would surface as TransactionSystemException — which this catch wouldn't
            // see, so the duplicate would leak as a 500 instead of the intended 409.
            repository.saveAndFlush(entity)
        } catch (ex: DataIntegrityViolationException) {
            throw ConflictErrorException(com.malai.commons.exception.MalaiCode.GENERIC_CONFLICT)
        }

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "PAYEE_ADDED",
                resourceType = "Payee",
                resourceId = payeeId.toString(),
                afterState = mapOf(
                    "name" to saved.name,
                    "sortCode" to saved.sortCode,
                    "accountNumber" to saved.accountNumber,
                    "nickname" to saved.nickname,
                ),
                traceId = traceId,
            ),
        )

        log.info("payee added: payeeId={} sortCode={} accountNumber={}", payeeId, saved.sortCode, saved.accountNumber)
        return toResponse(saved)
    }

    @Transactional
    fun removePayee(user: MalaiUserWithAccountHolderId, payeeId: UUID) {
        val existing = repository.findByPayeeIdAndAccountHolderId(payeeId, user.accountHolderId)
            ?: throw NotFoundException(PAYEE_NOT_FOUND)

        if (repository.existsActiveStandingOrderForPayee(payeeId)) {
            throw ConflictErrorException(PAYEE_HAS_ACTIVE_STANDING_ORDER)
        }

        val traceId = MDC.get(MDC_TRACE_ID)

        repository.delete(existing)

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "PAYEE_REMOVED",
                resourceType = "Payee",
                resourceId = payeeId.toString(),
                beforeState = mapOf(
                    "name" to existing.name,
                    "sortCode" to existing.sortCode,
                    "accountNumber" to existing.accountNumber,
                    "nickname" to existing.nickname,
                ),
                traceId = traceId,
            ),
        )

        log.info("payee removed: payeeId={}", payeeId)
    }

    private fun toResponse(entity: PayeeEntity) = PayeeResponse(
        payeeId = entity.payeeId,
        name = entity.name,
        sortCode = entity.sortCode,
        accountNumber = entity.accountNumber,
        nickname = entity.nickname,
        createdAt = entity.createdAt,
    )

    companion object {
        private const val MDC_TRACE_ID = "trace_id"
    }
}
