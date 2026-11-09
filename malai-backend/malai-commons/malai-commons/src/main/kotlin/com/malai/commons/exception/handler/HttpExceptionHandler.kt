package com.malai.commons.exception.handler

import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.MismatchedInputException
import com.malai.commons.exception.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.*

@RestControllerAdvice
class HttpExceptionHandler : AbstractExceptionHandler() {

    @ExceptionHandler(Exception::class)
    @ResponseBody
    fun handleException(ex: Exception): ResponseEntity<HttpExceptionResponse> {
        return genericError(ex, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(NotFoundException::class)
    @ResponseBody
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(InternalServerErrorException::class)
    @ResponseBody
    fun handleInternalServerErrorException(ex: InternalServerErrorException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(ConflictErrorException::class)
    @ResponseBody
    fun handleConflictErrorException(ex: ConflictErrorException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.CONFLICT)
    }

    @ExceptionHandler(UnauthorizedException::class)
    @ResponseBody
    fun handleUnauthorizedErrorException(ex: UnauthorizedException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(BadRequestException::class)
    @ResponseBody
    fun handleBadRequestErrorException(ex: BadRequestException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(UnprocessableEntityException::class)
    @ResponseBody
    fun handleUnprocessableEntityException(ex: UnprocessableEntityException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.UNPROCESSABLE_CONTENT)
    }

    @ExceptionHandler(ForbiddenException::class)
    @ResponseBody
    fun handleForbiddenException(ex: ForbiddenException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(ServiceUnavailableException::class)
    @ResponseBody
    fun handleServiceUnavailableException(ex: ServiceUnavailableException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.SERVICE_UNAVAILABLE)
    }

    // Plan 02-16 BACK-06: Spring Security 6.x throws AuthorizationDeniedException
    // (the modern flavour) on @PreAuthorize denial; older code paths still throw
    // AccessDeniedException. Both must surface as HTTP 403 — without these handlers
    // the catch-all `handleException` would route the deny through the
    // INTERNAL_SERVER_ERROR branch, hiding the real auth verdict from clients
    // and from `@PreAuthorize`-driven RBAC acceptance tests.
    @ExceptionHandler(AuthorizationDeniedException::class)
    @ResponseBody
    fun handleAuthorizationDeniedException(ex: AuthorizationDeniedException): ResponseEntity<HttpExceptionResponse> {
        return genericError(ex, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(AccessDeniedException::class)
    @ResponseBody
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<HttpExceptionResponse> {
        return genericError(ex, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(ServletRequestBindingException::class)
    @ResponseBody
    fun handleServletRequestBindingException(ex: ServletRequestBindingException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    @ResponseBody
    fun handleMissingServletRequestPartException(ex: MissingServletRequestPartException): ResponseEntity<HttpExceptionResponse> {
        return standardError(ex, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseBody
    fun handleMethodArgumentTypeMismatchException(ex: MethodArgumentTypeMismatchException): ResponseEntity<HttpExceptionResponse> {
        return singleError(
            ex,
            HttpStatus.BAD_REQUEST,
            String.format("Parameter '%s' does not accept value '%s'", ex.name, ex.value)
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseBody
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<HttpExceptionResponse> {
        return genericBadRequestError(ex)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseBody
    fun handleMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<HttpExceptionResponse> {
        if (ex.cause is InvalidFormatException) {
            val exception = ex.cause as InvalidFormatException?
            val targetType = exception!!.targetType
            if (targetType.isEnum && exception.message != null && exception.message!!.contains("not one of the values accepted for Enum class")) {
                return singleError(
                    ex, HttpStatus.BAD_REQUEST, String.format(
                        "The JSON input body could not be parsed because value: %s is not one of the values accepted: %s",
                        exception.value,
                        Arrays.toString(targetType.enumConstants)
                    )
                )
            }
        }
        if (ex.cause is MismatchedInputException) {
            return singleError(ex, HttpStatus.BAD_REQUEST, "Invalid input body")
        }
        return singleError(ex, HttpStatus.BAD_REQUEST, "The JSON input body could not be parsed")
    }

    @ExceptionHandler(HttpMessageConversionException::class)
    @ResponseBody
    fun handleMessageConversionException(ex: HttpMessageConversionException): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, HttpStatus.BAD_REQUEST, "The JSON input body could not be parsed")
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    @ResponseBody
    fun handleMethodNotSupportedException(ex: HttpRequestMethodNotSupportedException): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, HttpStatus.NOT_FOUND, "resource was not found")
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    @ResponseBody
    fun handleForbiddenException(ex: NoHandlerFoundException): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, HttpStatus.NOT_FOUND, "resource not found")
    }

    @ExceptionHandler(NoResourceFoundException::class)
    @ResponseBody
    fun handleNoResourceFoundException(ex: NoResourceFoundException): ResponseEntity<HttpExceptionResponse> {
        return singleError(ex, HttpStatus.NOT_FOUND, "resource not found")
    }
}
