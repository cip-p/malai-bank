package com.malai.card.pan

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Pan {

    const val MALAI_BIN = "990432"

    const val PAN_LENGTH = 16

    private const val VISIBLE_SUFFIX = 4

    private const val EXPIRY_YEARS = 3L

    private val EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MM/yy")

    fun mask(pan: String): String =
        "*".repeat(pan.length - VISIBLE_SUFFIX) + pan.takeLast(VISIBLE_SUFFIX)

    fun expiryFor(issuedAt: Instant): String =
        issuedAt.atZone(ZoneOffset.UTC).toLocalDate().plusYears(EXPIRY_YEARS).format(EXPIRY_FORMAT)
}
