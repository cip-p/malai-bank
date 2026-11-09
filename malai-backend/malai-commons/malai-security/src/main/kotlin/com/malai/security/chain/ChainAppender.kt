package com.malai.security.chain

class ChainLink(val prevHash: ByteArray, val currentHash: ByteArray, val seq: Long)

class ChainAppender(private val chainHeads: ChainHeads) {

    fun append(scope: String, canonicalPayload: () -> String, insert: (ChainLink) -> Unit): ChainLink {
        val head = chainHeads.lockAndRead(scope)
        val payload = canonicalPayload()
        val link = ChainLink(
            prevHash = head.lastHash,
            currentHash = ChainHasher.hash(head.lastHash, payload),
            seq = head.lastSeq + 1,
        )
        insert(link)
        chainHeads.advance(scope, link.currentHash, link.seq)
        return link
    }
}
