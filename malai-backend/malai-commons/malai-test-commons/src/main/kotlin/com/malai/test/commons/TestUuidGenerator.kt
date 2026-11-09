package com.malai.test.commons

import com.malai.commons.config.UuidGenerator
import java.util.*

class TestUuidGenerator : UuidGenerator {

    private val uuids: ArrayDeque<UUID> = ArrayDeque()

    fun willReturn(vararg uuids: UUID) {
        this.uuids.addAll(uuids)
    }

    override fun generate(): UUID = uuids.poll() ?: UUID.randomUUID()

    fun reset() {
        uuids.clear()
    }
}
