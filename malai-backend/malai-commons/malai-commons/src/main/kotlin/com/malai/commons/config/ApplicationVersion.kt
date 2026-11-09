package com.malai.commons.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.jar.Manifest


@Configuration
class ApplicationVersion {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Bean
    fun applicationVersionHolder(@Value("\${spring.application.name}") applicationName: String): ApplicationVersionHolder {
        val applicationVersionHolder = ApplicationVersionHolder()
        log.info("Running $applicationName with version: ${applicationVersionHolder.version}")
        return applicationVersionHolder
    }
}

class ApplicationVersionHolder(envVarLookup: (String) -> String? = System::getenv) {
    val version = envVarLookup("MAL_APP_VERSION")?.takeIf { it.isNotBlank() }
        ?: javaClass.classLoader.getResource("META-INF/MANIFEST.MF")?.openStream()?.use { manifestStream ->
            Manifest(manifestStream).mainAttributes.getValue("Implementation-Version")
        } ?: "unknown"
}
