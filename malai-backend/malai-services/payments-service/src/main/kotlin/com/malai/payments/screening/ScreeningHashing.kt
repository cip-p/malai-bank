package com.malai.payments.screening

import java.security.MessageDigest

fun sha256Hex(content: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

fun maskIdentifier(identifier: String): String =
    "*".repeat(maxOf(0, identifier.length - 4)) + identifier.takeLast(4)
