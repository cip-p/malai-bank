package com.malai.cognitostub

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class SigningKey private constructor(private val rsaKey: RSAKey) {

    private val signer = RSASSASigner(rsaKey)
    private val verifier = RSASSAVerifier(rsaKey.toRSAPublicKey())

    fun sign(claims: JWTClaimsSet): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.keyID).build()
        return SignedJWT(header, claims).apply { sign(signer) }.serialize()
    }

    fun verifiedClaims(token: String): JWTClaimsSet? =
        runCatching { SignedJWT.parse(token) }
            .getOrNull()
            ?.takeIf { it.verify(verifier) }
            ?.jwtClaimsSet

    fun jwks(): String = JWKSet(rsaKey.toPublicJWK()).toString()

    companion object {
        fun loadOrGenerate(file: Path): SigningKey {
            if (Files.exists(file)) {
                return SigningKey(RSAKey.parse(Files.readString(file)))
            }
            val generated = RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .keyUse(KeyUse.SIGNATURE)
                .generate()
            Files.createDirectories(file.parent)
            Files.writeString(file, generated.toJSONString())
            return SigningKey(generated)
        }
    }
}
