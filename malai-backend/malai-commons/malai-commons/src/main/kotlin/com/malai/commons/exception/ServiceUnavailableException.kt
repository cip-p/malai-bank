package com.malai.commons.exception

open class ServiceUnavailableException : ExceptionWithErrorCode {
    constructor(malaiCode: MalaiCode, message: String?) : super(malaiCode, message)
    constructor(malaiCode: MalaiCode) : this(malaiCode, null)
}
