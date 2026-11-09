package com.malai.commons.exception

/**
 * 422 Unprocessable Entity. Used by the idempotency layer when a cached
 * response key is reused with a different request hash — semantically the
 * client retried with a different payload, which is not a 400 (request shape
 * is fine) and not a 409 (no resource conflict).
 */
open class UnprocessableEntityException(malaiCode: MalaiCode) : ExceptionWithErrorCode(malaiCode, null)
