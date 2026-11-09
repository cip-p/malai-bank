package com.malai.stub.directory

import com.malai.payments.messages.ReturnReason
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@EnableConfigurationProperties(StubFictionalDirectoryProperties::class)
class StubFictionalDirectoryConfiguration

@Component
class StubFictionalDirectory(private val properties: StubFictionalDirectoryProperties) {

    private val byKey: Map<Pair<String, String>, StubAccount> =
        properties.accounts.associateBy { it.sortCode to it.accountNumber }

    fun find(sortCode: String, accountNumber: String): StubAccount? =
        byKey[sortCode to accountNumber]

    fun all(): List<StubAccount> = properties.accounts
}

@ConfigurationProperties(prefix = "malai.stub.directory")
data class StubFictionalDirectoryProperties(val accounts: List<StubAccount> = emptyList()) {
    init {
        accounts.forEach { account ->
            val hasReason = account.returnReason != null
            val isReturns = account.outboundOutcome == OutboundOutcome.RETURNS
            require(hasReason == isReturns) {
                "stub directory row ${account.sortCode}/${account.accountNumber} has invalid combination: " +
                    "outboundOutcome=${account.outboundOutcome}, returnReason=${account.returnReason}. " +
                    "returnReason must be non-null iff outboundOutcome=RETURNS."
            }
        }
    }
}

data class StubAccount(
    val sortCode: String,
    val accountNumber: String,
    val name: String,
    val bank: String,
    val outboundOutcome: OutboundOutcome = OutboundOutcome.SETTLES,
    val returnReason: ReturnReason? = null,
)

enum class OutboundOutcome { SETTLES, RETURNS, STUCK_PENDING }
