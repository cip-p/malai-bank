package com.malai.security

import com.malai.commons.MalaiCommonsConfiguration
import com.malai.security.cognito.CognitoJwtConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

@Configuration
@ComponentScan(
    basePackages = ["com.malai.security"],
    excludeFilters = [ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ["com\\.malai\\.security\\.cognito\\..*"]
    )]
)
@EnableMethodSecurity
@EnableConfigurationProperties(CognitoJwtConfigurationProperties::class)
@Import(MalaiCommonsConfiguration::class)
class MalaiSecurityConfiguration
