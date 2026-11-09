package com.malai.ledger.repository

import com.malai.ledger.entity.LedgerTransactionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LedgerTransactionRepository : JpaRepository<LedgerTransactionEntity, UUID> {

    fun findByTraceIdOrderByInitiatedAtAscTransactionIdAsc(traceId: String): List<LedgerTransactionEntity>
}
