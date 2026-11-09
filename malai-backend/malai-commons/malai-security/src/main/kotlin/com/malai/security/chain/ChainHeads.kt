package com.malai.security.chain

import org.springframework.jdbc.core.JdbcTemplate

class ChainHeadPosition(val lastHash: ByteArray, val lastSeq: Long)

class ChainHeads(private val jdbc: JdbcTemplate) {

    fun lockAndRead(scope: String): ChainHeadPosition =
        jdbc.queryForObject(
            "SELECT last_hash, last_seq FROM chain_head WHERE scope = ? FOR UPDATE",
            { rs, _ -> ChainHeadPosition(rs.getBytes("last_hash"), rs.getLong("last_seq")) },
            scope,
        ) ?: error("chain_head missing for scope '$scope' -- the genesis row must be inserted by migration")

    fun read(scope: String): ChainHeadPosition =
        jdbc.queryForObject(
            "SELECT last_hash, last_seq FROM chain_head WHERE scope = ?",
            { rs, _ -> ChainHeadPosition(rs.getBytes("last_hash"), rs.getLong("last_seq")) },
            scope,
        ) ?: error("chain_head missing for scope '$scope' -- the genesis row must be inserted by migration")

    fun advance(scope: String, hash: ByteArray, seq: Long) {
        jdbc.update("UPDATE chain_head SET last_hash = ?, last_seq = ? WHERE scope = ?", hash, seq, scope)
    }
}
