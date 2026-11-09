package com.malai.accounts.support

import com.malai.accounts.AccountsServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runAccountsService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    stubBaseUrl: String,
    paymentsBaseUrl: String,
    ledgerBaseUrl: String,
    cardBaseUrl: String,
    insightsBaseUrl: String,
    standingOrderBaseUrl: String,
    directDebitBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        AccountsServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        AccountsTestConfiguration::class.java,
    )
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.stub.base-url=$stubBaseUrl",
            "--malai.payments.base-url=$paymentsBaseUrl",
            "--malai.ledger.base-url=$ledgerBaseUrl",
            "--malai.card.base-url=$cardBaseUrl",
            "--malai.insights.base-url=$insightsBaseUrl",
            "--malai.standing-order.base-url=$standingOrderBaseUrl",
            "--malai.direct-debit.base-url=$directDebitBaseUrl",
            "--malai.cognito.account-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
        )
