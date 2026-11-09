package com.malai.commons.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MalaiCodeTest {

    @Test
    fun `malai codes are unique`() {
        val uniqueCodes = MalaiCode.entries.map { errorCode -> errorCode.code }.toSet()

        assertEquals(MalaiCode.entries.size, uniqueCodes.size)
    }
}
