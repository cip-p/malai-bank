package com.malai.cognitostub

import java.nio.file.Path
import java.nio.file.Paths

data class StubSettings(
    val port: Int,
    val stateDir: Path,
    val issuer: String?,
) {
    companion object {
        const val DEFAULT_PORT = 9095
        const val DEFAULT_USER_POOL_ID = "local-user-pool"

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): StubSettings = StubSettings(
            port = environment["MAL_COGNITO_STUB_PORT"]?.toInt() ?: DEFAULT_PORT,
            stateDir = environment["MAL_COGNITO_STUB_STATE_DIR"]?.let(Paths::get)
                ?: Paths.get(System.getProperty("user.home"), "malai-cognito-stub"),
            issuer = environment["MAL_COGNITO_STUB_ISSUER"],
        )
    }
}
