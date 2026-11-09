package com.malai.commons.exception

class InvalidJwtTokenException(malaiCode: MalaiCode) : UnauthorizedException(malaiCode)
