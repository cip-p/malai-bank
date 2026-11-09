package com.malai.commons.exception

class UserNotAllowedToPerformActionException(malaiCode: MalaiCode) : ForbiddenException(malaiCode)
