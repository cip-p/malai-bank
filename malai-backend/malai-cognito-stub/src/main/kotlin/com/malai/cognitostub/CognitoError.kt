package com.malai.cognitostub

class CognitoError(val type: String, message: String) : RuntimeException(message) {
    companion object {
        fun userNotFound() = CognitoError("UserNotFoundException", "User does not exist.")
        fun userNotConfirmed() = CognitoError("UserNotConfirmedException", "User is not confirmed.")
        fun notAuthorized(message: String) = CognitoError("NotAuthorizedException", message)
        fun invalidParameter(message: String) = CognitoError("InvalidParameterException", message)
    }
}
