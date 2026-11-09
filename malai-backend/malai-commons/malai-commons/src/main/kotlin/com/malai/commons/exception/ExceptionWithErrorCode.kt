package com.malai.commons.exception

open class ExceptionWithErrorCode(val malaiCode: MalaiCode, message: String?, val attributes: Map<String, Any>? = null) : RuntimeException(message ?: malaiCode.code.toString())
