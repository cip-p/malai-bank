package com.malai.insights.acceptance

import com.malai.insights.support.WithCategoryOverrides
import com.malai.insights.support.runInsightsService
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class InsightsAcceptanceTest :
    MalaiAcceptanceTest(),
    WithCategoryOverrides {

    fun insightsServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Insights Service Acceptance Tests",
                fromSystem = MalaiParty.InsightsService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        else -> MalaiParty.Unknown
                    }
                },
            ) {
                runInsightsService()
            }
        }
    }
}
