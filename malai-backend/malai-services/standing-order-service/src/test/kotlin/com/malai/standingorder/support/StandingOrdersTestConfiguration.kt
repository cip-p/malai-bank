package com.malai.standingorder.support

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test", "acceptance-tests")
class StandingOrdersTestConfiguration
