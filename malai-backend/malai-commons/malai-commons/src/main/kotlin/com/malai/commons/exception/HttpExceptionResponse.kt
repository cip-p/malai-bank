package com.malai.commons.exception

data class HttpExceptionResponse(val message: String, val attributes: Map<String, Any>? = null)
