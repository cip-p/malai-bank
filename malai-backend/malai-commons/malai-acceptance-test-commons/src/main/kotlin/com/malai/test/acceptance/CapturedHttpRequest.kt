package com.malai.test.acceptance

import com.github.tomakehurst.wiremock.verification.LoggedRequest
import org.springframework.http.HttpMethod

/**
 * A captured HTTP request the service under test sent to a WireMock-stubbed downstream
 * dependency during a test. Yielded one-at-a-time in chronological order by
 * [MalaiAcceptanceTest.malaiServiceMadeTheFollowingHttpRequest] so test bodies can assert on
 * each call's method, URL, headers and body:
 *
 * ```
 * then(authServiceMadeTheFollowingHttpRequest()) {
 *     method shouldBe POST
 *     url shouldEndWith "/cognito/"
 *     headers shouldContain ("X-Amz-Target" to "AWSCognitoIdentityProviderService.InitiateAuth")
 *     body containsProp json {
 *         "AuthParameters" To json {
 *             "USERNAME" To someEmail()
 *         }
 *     }
 * }
 * ```
 */
data class CapturedHttpRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

fun LoggedRequest.toCapturedHttpRequest(): CapturedHttpRequest =
    CapturedHttpRequest(
        method = HttpMethod.valueOf(method.value()),
        url = url,
        headers = headers.all().associate { it.key() to it.firstValue() },
        body = bodyAsString.takeIf { it.isNotEmpty() },
    )
