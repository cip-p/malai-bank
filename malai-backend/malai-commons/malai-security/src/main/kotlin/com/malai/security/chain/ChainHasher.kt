package com.malai.security.chain

import java.security.MessageDigest

object ChainHasher {

    fun hash(prevHash: ByteArray, canonicalPayload: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(prevHash + canonicalPayload.toByteArray(Charsets.UTF_8))
}
