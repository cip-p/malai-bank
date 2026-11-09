package com.malai.sandbox.support

import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import org.springframework.jdbc.core.JdbcTemplate

interface WithSandboxState : WithApplicationContext {

    fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }
}
