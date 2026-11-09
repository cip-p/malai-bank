package com.malai.commons.exception

open class BadRequestException : ExceptionWithErrorCode {
    constructor(malaiCode: MalaiCode, message: String?) : super(malaiCode, message)
    constructor(malaiCode: MalaiCode, attributes: Map<String, Any>? = null) : super(malaiCode, null, attributes)
    constructor(malaiCode: MalaiCode) : this(malaiCode, message = null)
}
