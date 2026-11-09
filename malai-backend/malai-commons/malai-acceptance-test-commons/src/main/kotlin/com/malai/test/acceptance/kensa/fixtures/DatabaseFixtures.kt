package com.malai.test.acceptance.kensa.fixtures

import java.util.concurrent.atomic.AtomicLong

val sequenceId: AtomicLong = AtomicLong(0)

data class Ids(
    val svlanId: String,
    val svlanName: String,
)
