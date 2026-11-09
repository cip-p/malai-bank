package com.malai.accounts.support

import com.malai.accounts.AccountsServiceApplication
import com.malai.test.commons.MalaiSecurityTestConfiguration
import com.malai.test.commons.MalaiServiceTestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = [AccountsServiceApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(MalaiServiceTestConfiguration::class, MalaiSecurityTestConfiguration::class)
annotation class SpringTest
