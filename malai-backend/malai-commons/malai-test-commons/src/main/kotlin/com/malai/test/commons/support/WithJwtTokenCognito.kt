package com.malai.test.commons.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.malai.test.commons.support.jwt.CognitoJwtTokenSupport
import com.malai.test.commons.support.jwt.JwtTokenBuilder
import com.malai.test.commons.support.jwt.StubbedCognitoSigner
import org.junit.jupiter.api.extension.*
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*

@ExtendWith(WithJwtTokenCognito.JwtTokenCognitoExtension::class)
interface WithJwtTokenCognito {

    fun generateJwtTokenForUserId(userId: UUID): String {
        return MalaiApiTestContext.context(JwtTokenCognitoSupportContext::class.java).cognitoJwtTokenSupport!!.generateJwtTokenForUserId(
            userId
        )
    }

    fun generateJwtToken(jwtTokenBuilder: JwtTokenBuilder): String {
        return MalaiApiTestContext.context(JwtTokenCognitoSupportContext::class.java).cognitoJwtTokenSupport!!.generateJwtToken(
            jwtTokenBuilder
        )
    }

    fun getKeyId(): String {
        return MalaiApiTestContext.context(JwtTokenCognitoSupportContext::class.java).stubbedCognitoSigner!!.getKeyId()
    }

    fun cognitoStub(): WireMockServer {
        return MalaiApiTestContext.context(JwtTokenCognitoSupportContext::class.java).wireMockServer!!
    }

    class JwtTokenCognitoExtension : BeforeAllCallback, BeforeEachCallback, AfterAllCallback {
        private var wireMockServer: WireMockServer? = null
        private var stubbedCognitoSigner: StubbedCognitoSigner? = null
        private var cognitoJwtTokenSupport: CognitoJwtTokenSupport? = null

        override fun beforeAll(extensionContext: ExtensionContext) {
            if (wireMockServer == null) {
                wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().port(9999))
                wireMockServer?.start()
            }
            if (stubbedCognitoSigner == null) {
                stubbedCognitoSigner =
                    SpringExtension.getApplicationContext(extensionContext).getBean(StubbedCognitoSigner::class.java)
            }
            if (cognitoJwtTokenSupport == null) {
                cognitoJwtTokenSupport =
                    SpringExtension.getApplicationContext(extensionContext).getBean(CognitoJwtTokenSupport::class.java)
            }
            MalaiApiTestContext.context(JwtTokenCognitoSupportContext::class.java).wireMockServer = wireMockServer
            MalaiApiTestContext.context(JwtTokenCognitoSupportContext::class.java).stubbedCognitoSigner =
                stubbedCognitoSigner
            MalaiApiTestContext.context(JwtTokenCognitoSupportContext::class.java).cognitoJwtTokenSupport =
                cognitoJwtTokenSupport
        }

        override fun beforeEach(extensionContext: ExtensionContext) {
            if (!wireMockServer?.isRunning!!) {
                wireMockServer?.start()
            }
            wireMockServer?.resetAll()

            wireMockServer?.stubFor(
                WireMock.get(WireMock.urlEqualTo("/stubbed-cognito-issuer/.well-known/jwks.json"))
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                "{ \"keys\": [" + stubbedCognitoSigner!!.getJwk().toPublicJWK().toString() + " ]}"
                            )
                    )
            )
        }

        override fun afterAll(extensionContext: ExtensionContext) {
            if (wireMockServer != null) {
                wireMockServer?.stop()
            }
        }
    }

    class JwtTokenCognitoSupportContext {
        var wireMockServer: WireMockServer? = null
        var stubbedCognitoSigner: StubbedCognitoSigner? = null
        var cognitoJwtTokenSupport: CognitoJwtTokenSupport? = null
    }
}
