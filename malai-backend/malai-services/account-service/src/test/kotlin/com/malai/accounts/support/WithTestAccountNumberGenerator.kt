package com.malai.accounts.support

import com.malai.accounts.identifier.AccountNumberGenerator
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithApplicationContext
import dev.kensa.Action
import dev.kensa.GivensContext
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.beans.factory.getBean

@ExtendWith(WithTestAccountNumberGenerator.TestAccountNumberGeneratorExtension::class)
interface WithTestAccountNumberGenerator : WithApplicationContext {

    fun testAccountNumberGenerator(): TestAccountNumberGenerator =
        getBean(AccountNumberGenerator::class.java) as TestAccountNumberGenerator

    fun theNextGeneratedAccountNumberWillBe(vararg accountNumbers: String) = Action<GivensContext> {
        testAccountNumberGenerator().willReturn(*accountNumbers)
    }

    class TestAccountNumberGeneratorExtension : BeforeEachCallback {
        override fun beforeEach(extensionContext: ExtensionContext) {
            if (MalaiAcceptanceTestContext.isInitialized) {
                (MalaiAcceptanceTestContext.applicationContext.getBean<AccountNumberGenerator>()
                    as TestAccountNumberGenerator).reset()
            }
        }
    }
}
