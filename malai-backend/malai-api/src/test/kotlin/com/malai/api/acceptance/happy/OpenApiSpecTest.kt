package com.malai.api.acceptance.happy

import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

/**
 * Quick 260513-cqs — regression test for `GET /openapi.yaml`.
 *
 * Plan 02-16 shipped the hand-authored cross-repo OpenAPI contract + a
 * resource-handler config (`StaticOpenApiConfig`) but no test that actually
 * exercised the route through Spring's filter + handler chain. The
 * dev deploy returned 500 (`NoResourceFoundException`) because
 * `addResourceLocations("classpath:/static/openapi.yaml")` resolves the
 * location's parent directory when the handler pattern fully matches the
 * URL. The quick task swapped to `OpenApiSpecController` (a thin
 * `@RestController` returning `ClassPathResource("static/openapi.yaml")`)
 * which sidesteps the resource-resolver quirk; this test locks the
 * end-to-end contract in at `mvn install` time so the bug can't recur.
 */
class OpenApiSpecTest : MalaiApiAcceptanceTest() {

    @Test
    fun openapiYamlIsServedOverHttp() = malaiAcceptanceTest {
        whenever(malaiApiReceives(get().url("/openapi.yaml")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            val text = body as String
            // First line nails the OpenAPI version — proves the right resource
            // is being served (not an error page, a partial body, or the wrong file).
            text shouldStartWith "openapi: 3.1.0"
            // A representative path entry nails that the shipped spec is the
            // current one (post-Plan-02-16 + post-quick-260512-ttc) — if openapi.yaml
            // were ever rebuilt or trimmed, this would catch it.
            text shouldContain "/api/v1/admin/audit-chain/verification"
        }
    }

    @Test
    fun openapiYamlDoesNotExposeAnySeedEndpoint() = malaiAcceptanceTest {
        // Seed endpoints (e.g. /api/v1/seed-demo-data) are operator-only —
        // hit manually to populate a demo account. They must NOT appear in
        // the published contract that the webapp generates its client from;
        // a signed-in customer should never have a way to call them.
        whenever(malaiApiReceives(get().url("/openapi.yaml")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            val text = body as String
            text shouldNotContain "/api/v1/seed"
        }
    }
}
