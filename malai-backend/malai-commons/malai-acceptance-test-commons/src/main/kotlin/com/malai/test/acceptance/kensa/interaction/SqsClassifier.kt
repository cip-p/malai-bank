package com.malai.test.acceptance.kensa.interaction

import com.malai.test.acceptance.kensa.MalaiParty
import dev.kensa.state.Party

/**
 * Maps an SQS queue base name (the catalog name without prefix or `.fifo` suffix, e.g.
 * `payments-fps-command-submit`) to the scheme [Party] that owns the other end of the queue and a
 * human-readable description, so SQS interactions render in per-scheme lanes on the Kensa diagram.
 */
object SqsClassifier {

    fun party(queueBaseName: String): Party = when {
        queueBaseName.contains("-fps-") -> MalaiParty.FpsGateway
        queueBaseName.contains("-swift-") -> MalaiParty.SwiftGateway
        queueBaseName.contains("-bacs-") -> MalaiParty.BacsGateway
        queueBaseName.startsWith("cards-") -> MalaiParty.CardGateway
        else -> MalaiParty.Unknown
    }

    fun describe(queueBaseName: String): String =
        queueBaseName
            .removePrefix("payments-")
            .split('-')
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
