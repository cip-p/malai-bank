package com.malai.test.commons.support.jwt

import com.malai.commons.exception.JwtProviderException
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import org.springframework.stereotype.Component
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*


@Component
class StubbedCognitoSigner {

    private final val keyPair: KeyPair
    private final val jwk: JWK
    private final val keyId: String

    private val RSA_ALGORITHM = "RSA"
    private val KEYSIZE_2048 = 2048

    init {
        try {
            val gen = KeyPairGenerator.getInstance(RSA_ALGORITHM)
            gen.initialize(KEYSIZE_2048)
            keyPair = gen.generateKeyPair()
            keyId = UUID.randomUUID().toString()

            jwk = RSAKey.Builder(keyPair.public as RSAPublicKey)
                .privateKey(keyPair.private as RSAPrivateKey)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(keyId)
                .build()
        } catch (e: Exception) {
            throw JwtProviderException("Couldn't create FakeCognitoSigner", e)
        }
    }

    fun getKeyPair(): KeyPair {
        return keyPair
    }

    fun getJwk(): JWK {
        return jwk
    }

    fun getKeyId(): String {
        return keyId
    }
}
