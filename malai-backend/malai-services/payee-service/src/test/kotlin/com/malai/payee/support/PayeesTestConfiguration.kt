package com.malai.payee.support

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test", "acceptance-tests")
class PayeesTestConfiguration
