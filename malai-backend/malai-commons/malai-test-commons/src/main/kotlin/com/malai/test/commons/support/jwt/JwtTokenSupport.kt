package com.malai.test.commons.support.jwt

import java.util.*

class JwtTokenSupport {

    companion object {
        fun idTokenKey(authToken: UUID): String {
            return authToken.toString() + "_id"
        }

        fun refreshTokenKey(authToken: UUID): String {
            return authToken.toString() + "_refresh"
        }

        fun accessTokenKey(authToken: UUID): String {
            return authToken.toString() + "_access"
        }
    }

}
