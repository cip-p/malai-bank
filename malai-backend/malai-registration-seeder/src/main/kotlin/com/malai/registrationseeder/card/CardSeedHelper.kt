package com.malai.registrationseeder.card

import com.malai.commons.config.UuidGenerator
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

@Component
class CardSeedHelper(
    private val jdbcTemplate: JdbcTemplate,
    private val auditWriter: AuditWriter,
    private val uuidGenerator: UuidGenerator,
) {

    /**
     * True when [accountId] already carries an ACTIVE card. The runtime registration path onboards
     * the CURRENT account through account-service, which auto-issues its ACTIVE debit card, so the
     * post-registration seeder must not raw-insert a second one (it would trip the partial unique
     * index `cards_one_active_per_account`). The CLI/raw-account paths (Sarah, the IT fixtures)
     * create the account directly and reach the seeder card-less, so they still issue here.
     */
    fun hasActiveCard(accountId: UUID): Boolean =
        (jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE account_id = ? AND status = 'ACTIVE'",
            Long::class.java,
            accountId,
        ) ?: 0L) > 0L

    fun issueCard(
        accountId: UUID,
        accountHolderId: UUID,
        cardholderName: String,
        variant: String,
        issuedAt: Instant,
        random: Random,
        idGenerator: UuidGenerator? = null,
    ): SeededCard {
        val ids = idGenerator ?: uuidGenerator
        val cardId = ids.generate()
        val pan = SeedPan.generatePan(random)
        val cvv = SeedPan.generateCvv(random)
        val expiry = SeedPan.expiryFor(issuedAt)
        jdbcTemplate.update(
            "INSERT INTO cards " +
                "(card_id, account_id, account_holder_id, cardholder_name, pan, expiry, cvv, variant, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
        ) { ps ->
            ps.setObject(1, cardId)
            ps.setObject(2, accountId)
            ps.setObject(3, accountHolderId)
            ps.setString(4, cardholderName)
            ps.setString(5, pan)
            ps.setString(6, expiry)
            ps.setString(7, cvv)
            ps.setString(8, variant)
            ps.setTimestamp(9, Timestamp.from(issuedAt))
        }
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_CARD_ISSUED",
                resourceType = "Card",
                resourceId = cardId.toString(),
                afterState = mapOf(
                    "accountId" to accountId.toString(),
                    "variant" to variant,
                    "status" to "ACTIVE",
                    "maskedPan" to SeedPan.mask(pan),
                ),
            )
        )
        return SeededCard(cardId, pan)
    }

    /**
     * Writes one AUTHORISED `card_authorisations` row directly, the issuer-side record of a live
     * merchant authorisation whose ledger [holdId] is still PLACED (issue 04's shape). [createdAt] is
     * backdated so the pending row reads as if it landed before "now". The paired hold is written by
     * [com.malai.registrationseeder.ledger.LedgerSeedHelper.placeHold]; the seeder keeps `hold_id =
     * authorisation_id` (the real authorise path does the same). A SEED_CARD_AUTHORISATION audit
     * event keeps the chain honest; the PAN never lands here (the row is PAN-free, like the runtime).
     */
    fun placeAuthorisation(
        authorisationId: UUID,
        cardId: UUID,
        accountId: UUID,
        accountHolderId: UUID,
        schemeReference: UUID,
        merchantName: String,
        mcc: String,
        amountMinorUnits: Long,
        currency: String,
        holdId: UUID,
        createdAt: Instant,
    ) {
        jdbcTemplate.update(
            "INSERT INTO card_authorisations " +
                "(authorisation_id, card_id, account_id, account_holder_id, scheme_reference, merchant_name, " +
                "mcc, amount_minor_units, currency, status, decline_reason, hold_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'AUTHORISED', NULL, ?, ?)",
        ) { ps ->
            ps.setObject(1, authorisationId)
            ps.setObject(2, cardId)
            ps.setObject(3, accountId)
            ps.setObject(4, accountHolderId)
            ps.setObject(5, schemeReference)
            ps.setString(6, merchantName)
            ps.setString(7, mcc)
            ps.setLong(8, amountMinorUnits)
            ps.setString(9, currency)
            ps.setObject(10, holdId)
            ps.setTimestamp(11, Timestamp.from(createdAt))
        }
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_CARD_AUTHORISATION",
                resourceType = "CardAuthorisation",
                resourceId = authorisationId.toString(),
                afterState = mapOf(
                    "accountId" to accountId.toString(),
                    "cardId" to cardId.toString(),
                    "merchantName" to merchantName,
                    "amountMinorUnits" to amountMinorUnits,
                    "status" to "AUTHORISED",
                ),
            )
        )
    }

    data class SeededCard(val cardId: UUID, val pan: String)
}
