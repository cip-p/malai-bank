package com.malai.test.acceptance

import com.malai.commons.config.UuidGenerator
import com.malai.test.commons.TestUuidGenerator
import dev.kensa.Action
import dev.kensa.GivensContext
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.*

/**
 * Acceptance-test helper for priming the [TestUuidGenerator] registered by
 * `MalaiServiceTestConfiguration` (active under the `acceptance-tests` profile). Resolves the
 * generator directly from the running service's Spring context and resets it between tests so
 * pending UUIDs don't leak.
 */
@ExtendWith(WithTestUuidGenerator.TestUuidGeneratorExtension::class)
interface WithTestUuidGenerator : WithApplicationContext {

    fun testUuidGenerator(): TestUuidGenerator =
        getBean(UuidGenerator::class.java) as TestUuidGenerator

    fun theNextGeneratedUuidWillBe(vararg uuids: UUID) = Action<GivensContext> {
        testUuidGenerator().willReturn(*uuids)
    }

    class TestUuidGeneratorExtension : BeforeEachCallback {
        override fun beforeEach(extensionContext: ExtensionContext) {
            if (MalaiAcceptanceTestContext.isInitialized) {
                (MalaiAcceptanceTestContext.applicationContext.getBean(UuidGenerator::class.java)
                    as TestUuidGenerator).reset()
            }
        }
    }
}
