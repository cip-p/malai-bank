package com.malai.commons.exception.handler

import com.malai.commons.exception.BusinessExceptionWhichShouldNotBeLogged
import com.malai.commons.exception.ExceptionWithErrorCode
import com.malai.commons.exception.HttpExceptionResponse
import com.malai.commons.exception.MalaiCode
import com.malai.commons.exception.MalaiCode.GENERIC_ERROR
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

open class AbstractExceptionHandler {

    private val log = LoggerFactory.getLogger(this.javaClass)

    protected fun standardError(
        ex: Exception,
        status: HttpStatus
    ): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, status)
    }

    protected fun genericError(ex: Exception, status: HttpStatus): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, status)
    }

    protected fun genericBadRequestError(ex: Exception): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, HttpStatus.BAD_REQUEST)
    }

    private fun singleError(
        ex: Exception,
        status: HttpStatus,
    ): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, status, null)
    }

    protected fun singleError(
        ex: Exception,
        status: HttpStatus,
        message: String?
    ): ResponseEntity<HttpExceptionResponse> {
        return error(ex, status, message)
    }

    protected fun error(
        ex: Exception,
        status: HttpStatus,
        message: String?
    ): ResponseEntity<HttpExceptionResponse> {
        if (ex !is BusinessExceptionWhichShouldNotBeLogged) {
            log.error("Caught error: " + (message ?: "unknown"), ex)
        }
        val errorCode = if (ex is ExceptionWithErrorCode) ex.malaiCode else GENERIC_ERROR
        val attributes = if (ex is ExceptionWithErrorCode) ex.attributes else null
        return buildResponse(errorCode, attributes, status)
    }

    private fun buildResponse(
        malaiCode: MalaiCode,
        attributes: Map<String, Any>? = null,
        status: HttpStatus
    ): ResponseEntity<HttpExceptionResponse> {
        return ResponseEntity.status(status).body(
            HttpExceptionResponse(malaiCode.code.toString(), attributes)
        )
    }
}
