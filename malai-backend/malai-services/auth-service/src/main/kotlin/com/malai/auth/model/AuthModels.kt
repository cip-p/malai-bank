package com.malai.auth.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.*

data class LoginRequest(

    @field:NotBlank
    val email: String?
)

data class LoginResponse(
    val userId: UUID,
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresIn: Int
)

data class RefreshTokenRequest(
    // Optional on the wire: a webapp client on a cold load sends an empty body and the
    // controller derives the subject from the expired `access_token` cookie's JWT (ADR-0002).
    // Mobile / CLI clients keep sending it.
    val userId: UUID? = null,
    // Nullable on the wire so a webapp client can omit it and have the controller
    // resolve the refresh token from the HttpOnly `refresh_token` cookie instead.
    // Mobile / CLI clients keep sending it in the body.
    val refreshToken: String? = null,
)

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresIn: Int
)

data class RegisterRequest(

    @field:NotBlank
    @field:Size(min = 1, max = 100)
    val firstName: String?,

    @field:NotBlank
    @field:Size(min = 1, max = 100)
    val lastName: String?,

    @field:NotBlank
    val email: String?
)

data class CodeSentResponse(
    val message: String
) {
    companion object {
        const val CONFIRMATION_CODE_SENT = "A confirmation code has been sent."
    }
}

data class VerifyCodeRequest(

    @field:NotBlank
    val email: String?,

    @field:NotBlank
    val confirmationCode: String?,

    val challengeSession: String? = null
)

data class UpdateUserRequest(

    @field:Size(max = 100)
    val firstName: String,

    @field:Size(max = 100)
    val lastName: String
)

data class UserResponse(
    val firstName: String?,
    val lastName: String,
    val email: String,
    val dateOfBirth: LocalDate?,
    val roles: List<String>
)
