package com.malai.security

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.malai.commons.exception.JwtProviderException
import com.malai.security.cognito.CognitoJwtConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey


@Component
class AwsCognitoRSAKeyProvider(cognitoJwtConfigurationProperties: CognitoJwtConfigurationProperties) :
    RSAKeyProvider {

    private final val awsWellKnownURL: URL
    private final val provider: JwkProvider

    private val log = LoggerFactory.getLogger(this.javaClass)

    init {
        val issuer = cognitoJwtConfigurationProperties.issuer
        val url = "$issuer/.well-known/jwks.json"
        awsWellKnownURL = try {
            log.info("creating the JWT issuer url {}", url)
            URI(url).toURL()
        } catch (e: MalformedURLException) {
            log.error("Invalid URL provided, URL={}", url, e)
            throw JwtProviderException("Invalid URL provided, URL=$url", e)
        }
        // has a default cache time of 10 hours
        provider = JwkProviderBuilder(awsWellKnownURL)
            .cached(true)
            .build()
    }

    override fun getPublicKeyById(kid: String): RSAPublicKey {
        return try {
            provider.get(kid).publicKey as RSAPublicKey
        } catch (e: Exception) {
            log.error("Failed to get JWT kid={} from awsWellKnownURL={}", kid, awsWellKnownURL, e)
            throw JwtProviderException(
                String.format(
                    "Failed to get JWT kid=%s from awsWellKnownURL=%s",
                    kid,
                    awsWellKnownURL
                ), e
            )
        }
    }

    override fun getPrivateKey(): RSAPrivateKey {
        throw UnsupportedOperationException("private key should not be needed")
    }

    override fun getPrivateKeyId(): String {
        throw UnsupportedOperationException("private key id should not be needed")
    }

}
