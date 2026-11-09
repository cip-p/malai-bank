package com.malai.security.cognito

import com.malai.security.MalaiSecurityConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ComponentScan(basePackages = ["com.malai.security.cognito"])
@EnableConfigurationProperties(CognitoConfigurationProperties::class)
@Import(MalaiSecurityConfiguration::class)
class MalaiCognitoConfiguration
