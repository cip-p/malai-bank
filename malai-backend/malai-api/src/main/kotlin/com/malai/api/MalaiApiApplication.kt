package com.malai.api

import com.malai.accountholder.AccountHolderServiceConfiguration
import com.malai.accounts.AccountsServiceConfiguration
import com.malai.aml.AmlServiceConfiguration
import com.malai.auth.AuthServiceConfiguration
import com.malai.bacsgateway.BacsGatewayConfiguration
import com.malai.card.CardServiceConfiguration
import com.malai.cardgateway.CardGatewayConfiguration
import com.malai.client.malai.ServiceTokenClient
import com.malai.directdebit.DirectDebitServiceConfiguration
import com.malai.copilot.CopilotServiceConfiguration
import com.malai.exec.ExecServiceConfiguration
import com.malai.fpsgateway.FpsGatewayConfiguration
import com.malai.insights.InsightsServiceConfiguration
import com.malai.ledger.LedgerServiceConfiguration
import com.malai.mdr.MdrServiceConfiguration
import com.malai.payee.PayeeServiceConfiguration
import com.malai.payments.PaymentsServiceConfiguration
import com.malai.registrationseeder.RegistrationSeederConfiguration
import com.malai.sandbox.SandboxServiceConfiguration
import com.malai.standingorder.StandingOrderServiceConfiguration
import com.malai.swiftgateway.SwiftGatewayConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(
    AuthServiceConfiguration::class,
    AccountsServiceConfiguration::class,
    LedgerServiceConfiguration::class,
    CardServiceConfiguration::class,
    InsightsServiceConfiguration::class,
    AccountHolderServiceConfiguration::class,
    PayeeServiceConfiguration::class,
    PaymentsServiceConfiguration::class,
    FpsGatewayConfiguration::class,
    SwiftGatewayConfiguration::class,
    BacsGatewayConfiguration::class,
    CardGatewayConfiguration::class,
    DirectDebitServiceConfiguration::class,
    StandingOrderServiceConfiguration::class,
    SandboxServiceConfiguration::class,
    MdrServiceConfiguration::class,
    ExecServiceConfiguration::class,
    CopilotServiceConfiguration::class,
    AmlServiceConfiguration::class,
    RegistrationSeederConfiguration::class,
    FlywayMigrationConfig::class,
)
class MalaiApiApplication {

    @Bean
    fun readyController() = ReadyController()

    @Bean
    fun openApiSpecController() = OpenApiSpecController()

    @Bean
    @Profile("e2e-tests")
    fun evictServiceTokenCachesController(serviceTokenClients: List<ServiceTokenClient>) =
        EvictServiceTokenCachesController(serviceTokenClients)
}

fun main(args: Array<String>) {
    runApplication<MalaiApiApplication>(*args)
}
