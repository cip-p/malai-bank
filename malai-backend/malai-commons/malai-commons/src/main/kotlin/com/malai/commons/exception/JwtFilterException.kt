package com.malai.commons.exception

class JwtFilterException(malaiCode: MalaiCode) : UnauthorizedException(malaiCode)
