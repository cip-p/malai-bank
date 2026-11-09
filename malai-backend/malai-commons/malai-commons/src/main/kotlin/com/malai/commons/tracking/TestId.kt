package com.malai.commons.tracking

import java.util.*

data class TestId(val value: String) {
    constructor(uuid: UUID) : this(uuid.toString())
    constructor() : this(UUID.randomUUID())

    companion object {
        const val HEADER = "X-TestId"
    }
}
