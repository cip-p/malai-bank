package com.malai.test.commons

import com.malai.test.commons.support.jwt.CognitoJwtTokenSupport
import com.malai.test.commons.support.jwt.StubbedCognitoSigner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import

@TestConfiguration
@Import(StubbedCognitoSigner::class, CognitoJwtTokenSupport::class)
class MalaiSecurityTestConfiguration
